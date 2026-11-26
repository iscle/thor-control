package com.thorctl.daemon.feature.gamemode

import android.app.ActivityManager.RunningAppProcessInfo
import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.ThorDisplays
import com.thorctl.daemon.ipc.FakeDaemonNotifier
import com.thorctl.daemon.system.AppProcess
import com.thorctl.daemon.system.AppTask
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakePackageService
import com.thorctl.daemon.system.FakePowerService
import com.thorctl.daemon.system.FakeTaskService
import com.thorctl.protocol.model.GameHudState
import com.thorctl.protocol.model.GameOptions
import com.thorctl.protocol.model.HudEvent
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
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
        this[MEMINFO] = "MemAvailable:    2097152 kB\n"
    }
    private val displayService = FakeDisplayService(listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM))
    private val displays = ThorDisplays.controller(displayService)
    private val taskService = FakeTaskService()
    private val packageService = FakePackageService().apply {
        flags[GAME] = 0
        flags["com.cached"] = 0
        flags["com.other"] = 0
    }
    private val scheduler = FakeScheduledExecutor(FakeClock())
    private val notifier = FakeDaemonNotifier()

    /** Where game mode keeps its files; a test can point it somewhere unwritable before first use. */
    private var filesDirectory: () -> File = { folder.root }
    private val files by lazy { GameModeFiles(filesDirectory()) }
    private val gameMode by lazy {
        GameMode(
            displays = displays,
            packageService = packageService,
            powerService = FakePowerService(),
            notifier = notifier,
            scheduler = scheduler,
            files = files,
            picker = GamePicker(displays, taskService, packageService),
            closer = AppCloser(displays, packageService, taskService, sys, scheduler),
            boost = ClockBoost(sys, files),
            bottomScreen = BottomScreenPolicy(displays, taskService, sys),
        )
    }

    private val huds get() = notifier.huds.map { it.first }
    private val stateChanges get() = notifier.stateChanges.get()

    private fun playing() {
        packageService.running = listOf(AppProcess(100, 10234, GAME, listOf(GAME), RunningAppProcessInfo.IMPORTANCE_FOREGROUND))
        taskService.topTasks[ThorDisplays.TOP.id] = AppTask(5, GAME)
    }

    private fun turnOn(options: GameOptions = GameOptions(closeApps = false)) {
        gameMode.init(options)
        assertTrue(gameMode.setEnabled(true))
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
    fun closesTheOtherAppsAndAnnouncesWhatThatFreedOnceItSettled() {
        playing()
        packageService.running += AppProcess(101, 10300, "com.other", listOf("com.other"), RunningAppProcessInfo.IMPORTANCE_SERVICE)
        turnOn(GameOptions())
        gameMode.tick(interactive = true)

        // Nothing waits for the memory to settle: the game is picked and boosted straight away.
        assertEquals(listOf("com.other"), packageService.forceStopped)
        assertEquals(GAME, gameMode.game)
        assertTrue(gameMode.boosted)
        assertTrue(huds.isEmpty())

        sys[MEMINFO] = "MemAvailable:    3145728 kB\n"
        scheduler.advanceBy(1200)
        assertEquals(listOf<HudEvent>(HudEvent.Game(GameHudState.STARTED, GAME, 1024)), huds)
        assertEquals(1024, gameMode.freedMb)
        assertEquals(2, stateChanges)
    }

    @Test
    fun announcesNothingWhenTurnedOffWhileMemorySettles() {
        playing()
        turnOn(GameOptions())
        gameMode.tick(interactive = true)
        gameMode.setEnabled(false)
        scheduler.advanceBy(1200)
        assertTrue(huds.isEmpty())
        assertEquals(0, gameMode.freedMb)
    }

    @Test
    fun staysOffWhenItCantBeMarkedOn() {
        filesDirectory = { folder.newFile("not-a-directory") }
        playing()
        gameMode.init(GameOptions(closeApps = false))
        assertFalse(gameMode.setEnabled(true))
        assertFalse(gameMode.enabled)
        gameMode.tick(interactive = true)
        assertNull(gameMode.game)
        assertFalse(gameMode.boosted)
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

        assertFalse(gameMode.setEnabled(false))
        assertFalse(displays.bottomForcedOff)
    }

    @Test
    fun turnsTheBottomScreenBackOnOnceItCanEvenAfterTurningOff() {
        playing()
        turnOn()
        gameMode.tick(interactive = true)
        gameMode.tick(interactive = true)
        // The bottom panel can disappear while it is off; then it can't be turned back on yet.
        displayService.displays = listOf(ThorDisplays.TOP)
        displays.refresh()
        gameMode.setEnabled(false)
        assertTrue(displays.bottomForcedOff)

        displayService.displays = listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM)
        displays.refresh()
        val before = stateChanges
        gameMode.tick(interactive = true)
        assertFalse(displays.bottomForcedOff)
        assertEquals(before + 1, stateChanges)
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

    @Test
    fun nudgeTicksSoonOnTheScheduler() {
        playing()
        turnOn()
        gameMode.nudge()
        assertNull(gameMode.game)
        scheduler.advanceBy(500)
        assertEquals(GAME, gameMode.game)
    }

    private companion object {
        const val GAME = "com.game"
        const val GPU = "/sys/class/kgsl/kgsl-3d0"
        const val MEMINFO = "/proc/meminfo"
    }
}
