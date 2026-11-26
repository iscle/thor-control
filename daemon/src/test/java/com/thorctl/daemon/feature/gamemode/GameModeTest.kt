package com.thorctl.daemon.feature.gamemode

import android.app.ActivityManager.RunningAppProcessInfo
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.ThorDisplays
import com.thorctl.daemon.ipc.DaemonNotifier
import com.thorctl.daemon.system.AppProcess
import com.thorctl.daemon.system.AppTask
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakePackageService
import com.thorctl.daemon.system.FakePowerService
import com.thorctl.daemon.system.FakeTaskService
import com.thorctl.protocol.model.GameHudState
import com.thorctl.protocol.model.GameOptions
import com.thorctl.protocol.model.HudEvent
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameModeTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val sys = FakeSys().apply {
        this["$GPU/min_pwrlevel"] = "8"
        this["$GPU/max_pwrlevel"] = "0"
    }
    private val displayService = FakeDisplayService(listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM))
    private val displays = ThorDisplays.controller(displayService)
    private val taskService = FakeTaskService()
    private val packageService = FakePackageService().apply {
        flags[GAME] = 0
        flags["com.cached"] = 0
    }
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val huds = mutableListOf<HudEvent>()
    private var stateChanges = 0
    private val notifier = object : DaemonNotifier {
        override fun stateChanged() {
            stateChanges++
        }

        override fun hud(event: HudEvent, displayId: Int) {
            huds += event
        }
    }
    private val files by lazy { GameModeFiles(folder.root) }
    private val gameMode by lazy {
        GameMode(
            displays = displays,
            packageService = packageService,
            powerService = FakePowerService(),
            notifier = notifier,
            scheduler = scheduler,
            files = files,
            picker = GamePicker(displays, taskService, packageService),
            closer = AppCloser(displays, packageService, taskService, sys),
            boost = ClockBoost(sys, files),
            bottomScreen = BottomScreenPolicy(displays, taskService, sys),
        )
    }

    @After
    fun stopScheduler() {
        scheduler.shutdownNow()
    }

    private fun playing() {
        packageService.running = listOf(AppProcess(100, 10234, GAME, listOf(GAME), RunningAppProcessInfo.IMPORTANCE_FOREGROUND))
        taskService.topTasks[ThorDisplays.TOP.id] = AppTask(5, GAME)
    }

    private fun turnOn(options: GameOptions = GameOptions(closeApps = false)) {
        gameMode.init(options)
        gameMode.setEnabled(true)
    }

    @Test
    fun picksTheGameAndBoosts() {
        playing()
        turnOn()
        gameMode.tick(interactive = true)

        assertEquals(GAME, gameMode.game)
        assertTrue(gameMode.boosted)
        assertEquals("sport", gameMode.fanOverride)
        assertEquals("0", sys["$GPU/min_pwrlevel"])
        assertEquals(listOf<HudEvent>(HudEvent.Game(GameHudState.STARTED, GAME, 0)), huds)
        assertEquals(1, stateChanges)
    }

    @Test
    fun turnsTheBottomScreenOffOnTheSecondTick() {
        playing()
        turnOn()
        gameMode.tick(interactive = true)
        gameMode.tick(interactive = true)
        assertTrue(displays.bottomForcedOff)
        assertEquals(HudEvent.Game(GameHudState.BOTTOM_OFF), huds.last())
        assertEquals(2, stateChanges)

        gameMode.setEnabled(false)
        assertFalse(displays.bottomForcedOff)
    }

    @Test
    fun dropsTheClocksWhileTheScreenIsOff() {
        playing()
        turnOn()
        gameMode.tick(interactive = true)
        gameMode.tick(interactive = false)
        assertFalse(gameMode.boosted)
        assertEquals("8", sys["$GPU/min_pwrlevel"])
        assertEquals(GAME, gameMode.game)
        gameMode.tick(interactive = true)
        assertTrue(gameMode.boosted)
    }

    @Test
    fun waitsForTheNextGameWhenTheGameCloses() {
        playing()
        turnOn()
        gameMode.tick(interactive = true)
        packageService.running = emptyList()
        taskService.topTasks.clear()
        gameMode.tick(interactive = true)
        assertNull(gameMode.game)
        assertFalse(gameMode.boosted)
        assertEquals("8", sys["$GPU/min_pwrlevel"])
    }

    @Test
    fun turningOffRestoresEverything() {
        playing()
        turnOn()
        gameMode.tick(interactive = true)
        gameMode.setEnabled(false)
        assertFalse(gameMode.enabled)
        assertNull(gameMode.game)
        assertFalse(gameMode.boosted)
        assertFalse(files.isMarkedOn())
        assertFalse(files.hasOriginals())
        assertEquals("8", sys["$GPU/min_pwrlevel"])
    }

    @Test
    fun aRestartedDaemonKeepsGameModeOn() {
        files.markOn(true)
        gameMode.init(GameOptions())
        assertTrue(gameMode.enabled)
    }

    @Test
    fun aRestartedDaemonUndoesLeftoversWhenGameModeIsOff() {
        files.saveOriginals(mapOf("$GPU/min_pwrlevel" to "8"))
        sys["$GPU/min_pwrlevel"] = "0"
        gameMode.init(GameOptions())
        assertFalse(gameMode.enabled)
        assertEquals("8", sys["$GPU/min_pwrlevel"])
        assertFalse(files.hasOriginals())
    }

    @Test
    fun clearsCachedAppsEveryFifthTick() {
        playing()
        turnOn(GameOptions())
        gameMode.tick(interactive = true) // picks the game (and closes the rest)
        packageService.running += AppProcess(101, 10300, "com.cached", listOf("com.cached"), RunningAppProcessInfo.IMPORTANCE_CACHED)
        repeat(4) { gameMode.tick(interactive = true) }
        assertTrue(packageService.killedInBackground.isEmpty())
        gameMode.tick(interactive = true)
        assertEquals(listOf("com.cached"), packageService.killedInBackground)
    }

    @Test
    fun changingTheClockOptionsReappliesTheBoost() {
        playing()
        turnOn()
        gameMode.tick(interactive = true)
        gameMode.setOptions(GameOptions(closeApps = false, gpu = false))
        assertTrue(gameMode.boosted)
        assertEquals("8", sys["$GPU/min_pwrlevel"])
    }

    private companion object {
        const val GAME = "com.game"
        const val GPU = "/sys/class/kgsl/kgsl-3d0"
    }
}
