package com.thorctl.ui.recents

import android.app.ActivityOptions
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import com.thorctl.R
import com.thorctl.di.ApplicationScope
import com.thorctl.domain.repository.AppInfoRepository
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.platform.apps.AppIcons
import com.thorctl.platform.haptics.Haptics
import com.thorctl.platform.overlay.OverlayOwner
import com.thorctl.platform.overlay.OverlayWindows
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.Jpeg
import com.thorctl.protocol.model.closeImages
import com.thorctl.ui.theme.ThorTheme
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Hosts the Home button's screens (see [RecentsStage]): recent apps on Home hold, and the zoom
 * to the home screen on a Home press inside an app. The daemon sends the pictures (the screen as
 * it was when Home went down, the home screen, previews of other apps) and, while recents is open,
 * the controller's buttons. Everything is dropped when the overlay goes, so it costs no memory
 * while closed.
 */
@Inject
@SingleIn(AppScope::class)
class RecentsOverlay(
    private val app: Context,
    private val daemon: DaemonRepository,
    private val windows: OverlayWindows,
    private val apps: AppInfoRepository,
    private val icons: AppIcons,
    private val haptics: Haptics,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val cards = MutableStateFlow<List<Card>>(emptyList())
    private val keys = MutableSharedFlow<String>(extraBufferCapacity = 32)

    private val main = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var session = 0
    private var displayId = -1

    // Home press: the switch underneath starts once our first frame is on screen.
    private var homeSeq = 0
    private var frameShown = false
    private var homeGoPending = false

    fun start() {
        scope.launch {
            daemon.events.collect { e ->
                when (e) {
                    is DaemonEvent.RecentsOpened -> openRecents(e)
                    is DaemonEvent.HomeAnimation -> openHomePress(e)
                    is DaemonEvent.RecentsThumb -> updateThumb(e.taskId, e.thumb)
                    is DaemonEvent.RecentsKey -> keys.tryEmit(e.key)
                    DaemonEvent.RecentsClosed -> removeNow()
                    else -> {}
                }
            }
        }
    }

    // ---- Opening ------------------------------------------------------------------------------

    private fun openRecents(e: DaemonEvent.RecentsOpened) {
        removeNow()
        val s = ++session
        val display = windows.display(e.displayId) ?: run {
            e.closeImages()
            daemon.send(DaemonCommand.RecentsClose)
            return
        }
        val bottomId = daemon.state.value?.bottomDisplayId ?: -1
        scope.launch {
            val built = withContext(Dispatchers.Default) {
                try {
                    buildCards(e, bottomId)
                } finally {
                    e.closeImages() // whatever wasn't read (a failure partway): closing twice is harmless
                }
            }
            if (s != session) return@launch
            cards.value = built
            val intro = if (e.fromHome) Intro.FROM_HOME else Intro.FROM_APP
            if (!show(display, Session(s, intro, startIndex(built, e.fromHome, e.originTask), e.originTask))) {
                daemon.send(DaemonCommand.RecentsClose)
            }
        }
    }

    /** Decodes every image of [e] (each closes its pipe) into the stage's cards: apps oldest first, home last. */
    private fun buildCards(e: DaemonEvent.RecentsOpened, bottomId: Int): List<Card> {
        val hero = decode(e.hero)
        val appCards = e.tasks.map { t ->
            val thumb = decode(t.thumb)
            Card(
                key = t.id,
                pkg = t.pkg,
                label = apps.label(t.pkg),
                icon = icons.icon(t.pkg)?.asImageBitmap(),
                image = if (t.id == e.originTask && hero != null) hero else thumb,
                onBottom = bottomId >= 0 && t.displayId == bottomId,
                running = t.running,
            )
        }
        return appCards.reversed() + Card(Card.HOME_KEY, "", "Home", image = decode(e.home), isHome = true)
    }

    private fun openHomePress(e: DaemonEvent.HomeAnimation) {
        removeNow()
        val s = ++session
        homeSeq = e.seq
        val display = windows.display(e.displayId) ?: run {
            e.closeImages()
            return
        }
        scope.launch {
            val built = withContext(Dispatchers.Default) {
                listOf(
                    Card(Card.PRESS_APP_KEY, "", "", image = decode(e.app)),
                    Card(Card.HOME_KEY, "", "Home", image = decode(e.home), isHome = true),
                )
            }
            if (s != session) return@launch
            if (built.any { it.image == null }) return@launch // the daemon sends Home itself shortly
            cards.value = built
            show(display, Session(s, Intro.HOME_PRESS, 0, Card.PRESS_APP_KEY))
        }
    }

    private fun updateThumb(id: Int, jpeg: Jpeg) {
        val s = session
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { decode(jpeg) } ?: return@launch
            if (s != session) return@launch
            cards.value = cards.value.map { if (it.key == id) it.copy(image = bmp) else it }
        }
    }

    /** Screens are opaque: RGB_565 halves the memory. Full resolution, they fill the screen. Always closes [jpeg]. */
    private fun decode(jpeg: Jpeg?): ImageBitmap? {
        if (jpeg == null) return null
        return try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            jpeg.open().use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
        } catch (e: Exception) {
            Timber.w(e, "decode failed")
            null
        } finally {
            jpeg.close()
        }
    }

    // ---- Window -------------------------------------------------------------------------------

    private fun show(display: Display, s: Session): Boolean {
        val oc = windows.contextFor(display) ?: run {
            Timber.w("no way to show an overlay")
            return false
        }
        displayId = display.displayId
        val d = Dialog(oc.context, R.style.Theme_ThorControl_Menu)
        val w = d.window ?: return false
        w.setType(oc.type)
        w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        w.setFormat(PixelFormat.TRANSLUCENT)
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // Never focusable: the app underneath keeps focus (and keeps running); buttons come from the daemon.
        w.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )
        w.attributes = w.attributes.apply {
            title = "ThorControlRecents"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            windowAnimations = 0
        }
        frameShown = false
        homeGoPending = false

        val o = OverlayOwner()
        val v = ComposeView(oc.context)
        o.attach(v)
        v.setContent {
            ThorTheme {
                RecentsStage(session = s, cards = cards, keys = keys, actions = Actions(s.id))
            }
        }
        // The first frame is on screen: only now may the app underneath change.
        v.viewTreeObserver.registerFrameCommitCallback {
            main.postDelayed({
                frameShown = true
                if (homeGoPending) sendHomeGo()
            }, 24)
        }
        d.setContentView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        d.setCancelable(false)
        return try {
            d.show()
            o.resume()
            dialog = d
            view = v
            owner = o
            true
        } catch (e: Exception) {
            Timber.w(e, "show failed")
            o.destroy()
            false
        }
    }

    private fun sendHomeGo() {
        homeGoPending = false
        daemon.send(DaemonCommand.HomeGo(homeSeq))
    }

    /** After the zoom: let the switch underneath settle, fade out, and drop everything. */
    private fun finish(sessionId: Int, settleMs: Long) {
        if (sessionId != session) return
        if (settleMs <= 0) {
            removeNow()
            return
        }
        main.postDelayed({
            if (sessionId != session) return@postDelayed
            val v = view ?: return@postDelayed
            v.animate().alpha(0f).setDuration(140).withEndAction { if (sessionId == session) removeNow() }.start()
        }, settleMs)
    }

    private fun removeNow() {
        val d = dialog ?: return
        dialog = null
        view = null
        try {
            d.dismiss()
        } catch (e: Exception) {
            Timber.w(e, "dismiss")
        }
        owner?.destroy()
        owner = null
        cards.value = emptyList()
        session++
        daemon.send(DaemonCommand.OverlayDone)
    }

    /** Starts an app whose task is no longer running, like a launcher would, on the same screen. */
    private fun launch(c: Card) {
        val intent = app.packageManager.getLaunchIntentForPackage(c.pkg) ?: run {
            Timber.w("no launch intent for ${c.pkg}")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val opts = ActivityOptions.makeBasic()
        if (displayId >= 0) opts.launchDisplayId = displayId
        try {
            app.startActivity(intent, opts.toBundle())
        } catch (e: Exception) {
            Timber.w(e, "launch ${c.pkg} failed")
        }
    }

    private inner class Actions(private val sessionId: Int) : RecentsActions {
        private fun live() = sessionId == session

        override fun openApp(card: Card) {
            if (!live()) return
            if (card.running) {
                daemon.send(DaemonCommand.RecentsOpen(card.key))
            } else {
                launch(card)
                daemon.send(DaemonCommand.RecentsClose)
            }
        }

        override fun openHome(alreadyThere: Boolean) {
            if (!live()) return
            daemon.send(if (alreadyThere) DaemonCommand.RecentsClose else DaemonCommand.RecentsHome)
        }

        override fun back() {
            if (live()) daemon.send(DaemonCommand.RecentsClose)
        }

        override fun remove(card: Card) {
            if (live()) daemon.send(DaemonCommand.RecentsRemove(card.key))
        }

        override fun clearAll(ids: List<Int>) {
            if (live()) daemon.send(DaemonCommand.RecentsClear(ids))
        }

        override fun homeGo() {
            if (!live()) return
            if (frameShown) sendHomeGo() else homeGoPending = true
        }

        override fun finish(settleMs: Long) = finish(sessionId, settleMs)

        override fun tick() = haptics.tick()
    }
}
