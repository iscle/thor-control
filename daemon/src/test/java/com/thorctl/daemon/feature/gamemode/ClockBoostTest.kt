package com.thorctl.daemon.feature.gamemode

import com.thorctl.daemon.FakeSys
import com.thorctl.protocol.model.GameOptions
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClockBoostTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val files by lazy { GameModeFiles(folder.root) }
    private val sys = FakeSys().apply {
        this["$POLICY0/cpuinfo_min_freq"] = "300000"
        this["$POLICY0/cpuinfo_max_freq"] = "1800000"
        this["$POLICY0/scaling_min_freq"] = "300000"
        this["$POLICY4/cpuinfo_min_freq"] = "400000"
        this["$POLICY4/cpuinfo_max_freq"] = "2400000"
        // Reads back the perf HAL's temporary boost; the vendor script's floor is what to restore.
        this["$POLICY4/scaling_min_freq"] = "1200000"
        this["/vendor/bin/init.kernel.post_boot-pineapple.sh"] = "echo 556800 > $POLICY4/scaling_min_freq\n"
        this["/vendor/bin/other.sh"] = "echo 1 > $POLICY0/scaling_min_freq\n"
        this["$GPU/min_pwrlevel"] = "8"
        this["$GPU/max_pwrlevel"] = "0"
    }
    private val boost by lazy { ClockBoost(sys, files) }

    @Test
    fun findsTheKnobs() {
        assertEquals(2, boost.cpuKnobCount)
        assertEquals(1, boost.gpuKnobCount)
    }

    @Test
    fun savesOriginalsBeforeRaisingTheFloors() {
        boost.apply(GameOptions())
        assertTrue(boost.boosted)
        assertEquals(
            mapOf("$POLICY0/scaling_min_freq" to "300000", "$POLICY4/scaling_min_freq" to "556800", "$GPU/min_pwrlevel" to "8"),
            files.loadOriginals(),
        )
        assertEquals(
            listOf("$POLICY0/scaling_min_freq" to "1800000", "$POLICY4/scaling_min_freq" to "2400000", "$GPU/min_pwrlevel" to "0"),
            sys.writes,
        )
    }

    @Test
    fun boostsOnlyWhatIsAskedFor() {
        boost.apply(GameOptions(cpu = false))
        assertEquals(listOf("$GPU/min_pwrlevel" to "0"), sys.writes)
        assertEquals(setOf("$GPU/min_pwrlevel"), files.loadOriginals().keys)
    }

    @Test
    fun releasePutsTheOriginalsBack() {
        boost.apply(GameOptions())
        sys.writes.clear()
        boost.release()
        assertFalse(boost.boosted)
        assertFalse(files.hasOriginals())
        assertEquals(
            listOf("$POLICY0/scaling_min_freq" to "300000", "$POLICY4/scaling_min_freq" to "556800", "$GPU/min_pwrlevel" to "8"),
            sys.writes,
        )
    }

    @Test
    fun keepsTheFirstSavedOriginal() {
        files.saveOriginals(mapOf("$GPU/min_pwrlevel" to "6"))
        boost.apply(GameOptions(cpu = false))
        assertEquals(mapOf("$GPU/min_pwrlevel" to "6"), files.loadOriginals())
    }

    @Test
    fun restoresWhatADeadDaemonLeftBoosted() {
        // A daemon that died while boosting left its originals behind, and the floors raised.
        ClockBoost(sys, files).apply(GameOptions())
        sys.writes.clear()

        val restarted = ClockBoost(sys, GameModeFiles(folder.root))
        restarted.restoreSaved()

        assertEquals("300000", sys["$POLICY0/scaling_min_freq"])
        assertEquals("556800", sys["$POLICY4/scaling_min_freq"])
        assertEquals("8", sys["$GPU/min_pwrlevel"])
        assertEquals(3, sys.writes.size)
        assertFalse(files.hasOriginals())
    }

    @Test
    fun releaseWithoutBoostStillRestoresLeftovers() {
        files.saveOriginals(mapOf("$GPU/min_pwrlevel" to "8"))
        sys["$GPU/min_pwrlevel"] = "0"
        boost.release()
        assertEquals("8", sys["$GPU/min_pwrlevel"])
        assertFalse(files.hasOriginals())
    }

    @Test
    fun releaseWithNothingToDoWritesNothing() {
        boost.release()
        assertTrue(sys.writes.isEmpty())
    }

    @Test
    fun reassertPutsBackALoweredGpuFloorButNotCpuFloors() {
        boost.apply(GameOptions())
        sys["$GPU/min_pwrlevel"] = "3"
        sys["$POLICY0/scaling_min_freq"] = "1500000" // thermal limits lower the effective floor
        sys.writes.clear()
        boost.reassert()
        assertEquals(listOf("$GPU/min_pwrlevel" to "0"), sys.writes)
    }

    private companion object {
        const val POLICY0 = "/sys/devices/system/cpu/cpufreq/policy0"
        const val POLICY4 = "/sys/devices/system/cpu/cpufreq/policy4"
        const val GPU = "/sys/class/kgsl/kgsl-3d0"
    }
}
