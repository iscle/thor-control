package com.thorctl.daemon.feature.gamemode

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.model.GameOptions
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/**
 * Holds the CPU and GPU at their top clocks while a game runs:
 *
 *  - CPU: every cpufreq policy's minimum frequency raised to its maximum. Core parking (core_ctl)
 *    is left alone: the vendor perf HAL saves and rewrites it on its own schedule, so values
 *    written there leak.
 *  - GPU: lowest allowed power level = highest allowed one (level 0 is the top clock).
 *
 * Every value is saved to [GameModeFiles] before it is first overwritten, so it can be put back
 * after the boost, or by restore-kernel.sh if the daemon dies.
 */
@Inject
@SingleIn(DaemonScope::class)
class ClockBoost(private val sys: Sys, private val files: GameModeFiles) {
    private data class Knob(val path: String, val boost: String)

    @Volatile
    var boosted = false
        private set

    private var applied: List<Knob> = emptyList()

    val cpuKnobCount: Int get() = cpuKnobs.size

    val gpuKnobCount: Int get() = gpuKnobs.size

    /** Every cpufreq policy's minimum frequency raised to its maximum. */
    private val cpuKnobs: List<Knob> by lazy {
        sys.list(CPUFREQ)
            .filter { it.substringAfterLast('/').matches(POLICY) }.sortedBy { it.substringAfterLast('/') }
            .mapNotNull { policy -> sys.read("$policy/cpuinfo_max_freq")?.let { Knob("$policy/scaling_min_freq", it) } }
    }

    /** Lowest allowed GPU power level = highest allowed one (level 0 is the top clock). */
    private val gpuKnobs: List<Knob> by lazy {
        if (sys.exists("$GPU/min_pwrlevel")) listOf(Knob("$GPU/min_pwrlevel", sys.read("$GPU/max_pwrlevel") ?: "0")) else emptyList()
    }

    /**
     * scaling_min_freq reads back the effective floor, which includes the vendor perf HAL's own
     * boosts (msm_performance). Saving that would make a temporary boost permanent, so restore the
     * floor the vendor boot script sets instead (or the hardware minimum).
     */
    private val vendorMinFrequencies: Map<String, String> by lazy {
        val floors = HashMap<String, String>()
        sys.list(VENDOR_BIN).filter { it.substringAfterLast('/').startsWith("init.kernel.post_boot") }.sortedBy { it.substringAfterLast('/') }
            .forEach { script -> sys.read(script)?.let { text -> VENDOR_FLOOR.findAll(text).forEach { floors[it.groupValues[2]] = it.groupValues[1] } } }
        floors
    }

    private fun originalOf(path: String): String? =
        if (path.endsWith("/scaling_min_freq")) {
            vendorMinFrequencies[path] ?: sys.read(path.replace("scaling_min_freq", "cpuinfo_min_freq"))
        } else {
            sys.read(path)
        }

    fun apply(options: GameOptions) {
        val knobs = (if (options.cpu) cpuKnobs else emptyList()) + (if (options.gpu) gpuKnobs else emptyList())
        val originals = LinkedHashMap(files.loadOriginals())
        for (knob in knobs) if (knob.path !in originals) originalOf(knob.path)?.let { originals[knob.path] = it }
        files.saveOriginals(originals)
        for (knob in knobs) sys.write(knob.path, knob.boost)
        applied = knobs
        boosted = true
        Timber.i("game mode: boost applied (cpu=${options.cpu} gpu=${options.gpu} fan=${options.fan.ifEmpty { "unchanged" }})")
    }

    fun release() {
        if (!boosted && !files.hasOriginals()) return
        restoreSaved()
        applied = emptyList()
        boosted = false
        Timber.i("game mode: boost released")
    }

    /** Puts back a GPU floor something else lowered while we were boosting. */
    fun reassert() {
        for (knob in applied) {
            val current = sys.read(knob.path) ?: continue
            // scaling_min_freq reads back the effective floor, which thermal limits may lower.
            if (current != knob.boost && !knob.path.endsWith("/scaling_min_freq")) sys.write(knob.path, knob.boost)
        }
    }

    /** Writes back every saved original (e.g. left over from a daemon that died) and forgets them. */
    fun restoreSaved() {
        val saved = files.loadOriginals()
        for ((path, value) in saved) sys.write(path, value)
        files.deleteOriginals()
    }

    private companion object {
        const val CPUFREQ = "/sys/devices/system/cpu/cpufreq"
        const val GPU = "/sys/class/kgsl/kgsl-3d0"
        const val VENDOR_BIN = "/vendor/bin"
        val POLICY = Regex("policy\\d+")
        val VENDOR_FLOOR = Regex("""echo\s+(\d+)\s*>\s*(/sys/devices/system/cpu/cpufreq/policy\d+/scaling_min_freq)""")
    }
}
