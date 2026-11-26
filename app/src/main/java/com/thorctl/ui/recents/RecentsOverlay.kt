package com.thorctl.ui.recents

import com.thorctl.domain.model.DaemonStatus
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
    private val context: Context,
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
    private var homeSequence = 0
    private var frameShown = false
    private var homeGoPending = false

    fun start() {
        scope.launch {
            daemon.events.collect { event ->
                when (event) {
                    is DaemonEvent.RecentsOpened -> openRecents(event)
                    is DaemonEvent.HomeAnimation -> openHomePress(event)
                    is DaemonEvent.RecentsThumb -> updateThumb(event.taskId, event.thumb)
                    is DaemonEvent.RecentsKey -> keys.tryEmit(event.key)
                    DaemonEvent.RecentsClosed -> removeNow()
                    else -> {}
                }
            }
        }
    }

    // ---- Opening ------------------------------------------------------------------------------

    private fun openRecents(event: DaemonEvent.RecentsOpened) {
        removeNow()
        val sessionId = ++session
        val display = windows.display(event.displayId) ?: run {
            event.closeImages()
            daemon.send(DaemonCommand.RecentsClose)
            return
        }
        val bottomId = when (val status = daemon.status.value) {
            DaemonStatus.Disconnected -> -1
            is DaemonStatus.Connected -> status.state.value.bottomDisplayId
        }
        scope.launch {
            val built = withContext(Dispatchers.Default) {
                try {
                    buildCards(event, bottomId)
                } finally {
                    event.closeImages() // whatever wasn't read (a failure partway): closing twice is harmless
                }
            }
            if (sessionId != session) return@launch
            cards.value = built
            val intro = if (event.fromHome) Intro.FROM_HOME else Intro.FROM_APP
            if (!show(display, Session(sessionId, intro, startIndex(built, event.fromHome, event.originTask), event.originTask))) {
                daemon.send(DaemonCommand.RecentsClose)
            }
        }
    }

    /** Decodes every image of [e] (each closes its pipe) into the stage's cards: apps oldest first, home last. */
    private fun buildCards(event: DaemonEvent.RecentsOpened, bottomId: Int): List<Card> {
        val hero = decode(event.hero)
        val appCards = event.tasks.map { task ->
            val thumb = decode(task.thumb)
            Card(
                key = task.id,
                packageName = task.packageName,
                label = apps.label(task.packageName),
                icon = icons.icon(task.packageName)?.asImageBitmap(),
                image = if (task.id == event.originTask && hero != null) hero else thumb,
                onBottom = bottomId >= 0 && task.displayId == bottomId,
                running = task.running,
            )
        }
        return appCards.reversed() + Card(Card.HOME_KEY, "", "Home", image = decode(event.home), isHome = true)
    }

    private fun openHomePress(event: DaemonEvent.HomeAnimation) {
        removeNow()
        val sessionId = ++session
        homeSequence = event.seq
        val display = windows.display(event.displayId) ?: run {
            event.closeImages()
            return
        }
        scope.launch {
            val built = withContext(Dispatchers.Default) {
                listOf(
                    Card(Card.PRESS_APP_KEY, "", "", image = decode(event.appScreen)),
                    Card(Card.HOME_KEY, "", "Home", image = decode(event.homeScreen), isHome = true),
                )
            }
            if (sessionId != session) return@launch
            if (built.any { it.image == null }) return@launch // the daemon sends Home itself shortly
            cards.value = built
            show(display, Session(sessionId, Intro.HOME_PRESS, 0, Card.PRESS_APP_KEY))
        }
    }

    private fun updateThumb(id: Int, jpeg: Jpeg) {
        val sessionId = session
        scope.launch {
            val bitmap = withContext(Dispatchers.Default) { decode(jpeg) } ?: return@launch
            if (sessionId != session) return@launch
            cards.value = cards.value.map { if (it.key == id) it.copy(image = bitmap) else it }
        }
    }

    /** Screens are opaque: RGB_565 halves the memory. Full resolution, they fill the screen. Always closes [jpeg]. */
    private fun decode(jpeg: Jpeg?): ImageBitmap? {
        if (jpeg == null) return null
        return try {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            jpeg.open().use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
        } catch (error: Exception) {
            Timber.w(error, "decode failed")
            null
        } finally {
            jpeg.close()
        }
    }

    // ---- Window -------------------------------------------------------------------------------

    private fun show(display: Display, stageSession: Session): Boolean {
        val overlayContext = windows.contextFor(display) ?: run {
            Timber.w("no way to show an overlay")
            return false
        }
        displayId = display.displayId
        val recentsDialog = Dialog(overlayContext.context, R.style.Theme_ThorControl_Menu)
        val window = recentsDialog.window ?: return false
        window.setType(overlayContext.type)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // Never focusable: the app underneath keeps focus (and keeps running); buttons come from the daemon.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )
        window.attributes = window.attributes.apply {
            title = "ThorControlRecents"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            windowAnimations = 0
        }
        frameShown = false
        homeGoPending = false

        val overlayOwner = OverlayOwner()
        val composeView = ComposeView(overlayContext.context)
        overlayOwner.attach(composeView)
        composeView.setContent {
            ThorTheme {
                RecentsStage(session = stageSession, cards = cards, keys = keys, actions = Actions(stageSession.id))
            }
        }
        // The first frame is on screen: only now may the app underneath change.
        composeView.viewTreeObserver.registerFrameCommitCallback {
            main.postDelayed({
                frameShown = true
                if (homeGoPending) sendHomeGo()
            }, 24)
        }
        recentsDialog.setContentView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        recentsDialog.setCancelable(false)
        return try {
            recentsDialog.show()
            overlayOwner.resume()
            dialog = recentsDialog
            view = composeView
            owner = overlayOwner
            true
        } catch (error: Exception) {
            Timber.w(error, "show failed")
            overlayOwner.destroy()
            false
        }
    }

    private fun sendHomeGo() {
        homeGoPending = false
        daemon.send(DaemonCommand.HomeGo(homeSequence))
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
            val shownView = view ?: return@postDelayed
            shownView.animate().alpha(0f).setDuration(140).withEndAction { if (sessionId == session) removeNow() }.start()
        }, settleMs)
    }

    private fun removeNow() {
        val shownDialog = dialog ?: return
        dialog = null
        view = null
        try {
            shownDialog.dismiss()
        } catch (error: Exception) {
            Timber.w(error, "dismiss")
        }
        owner?.destroy()
        owner = null
        cards.value = emptyList()
        session++
        daemon.send(DaemonCommand.OverlayDone)
    }

    /** Starts an app whose task is no longer running, like a launcher would, on the same screen. */
    private fun launch(card: Card) {
        val intent = context.packageManager.getLaunchIntentForPackage(card.packageName) ?: run {
            Timber.w("no launch intent for ${card.packageName}")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val options = ActivityOptions.makeBasic()
        if (displayId >= 0) options.launchDisplayId = displayId
        try {
            context.startActivity(intent, options.toBundle())
        } catch (error: Exception) {
            Timber.w(error, "launch ${card.packageName} failed")
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
