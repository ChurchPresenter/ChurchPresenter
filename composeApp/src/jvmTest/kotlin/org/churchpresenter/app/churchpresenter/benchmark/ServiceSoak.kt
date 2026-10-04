package org.churchpresenter.app.churchpresenter.benchmark

import androidx.compose.runtime.mutableIntStateOf
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.locks.LockSupport
import kotlin.test.Test
import kotlin.test.assertTrue

private const val NANOS_PER_SECOND = 1_000_000_000L
private const val SECONDS_PER_MINUTE = 60.0
private const val NANOS_PER_MILLI = 1_000_000.0
private const val BYTES_PER_MB = 1024.0 * 1024.0
private const val KB_PER_MB = 1024.0

/**
 * A service run on one off-screen output for hours: every content type in turn, a cue at a time,
 * on a single long-lived scene — as a live NDI or Browser Source output runs through a Sunday.
 *
 * Not part of `jvmTest`: it runs as `./gradlew :composeApp:soakTest` (`-PsoakMinutes`, default 240),
 * alone in its JVM, paced at the output's frame rate in real time. Every minute it collects garbage
 * and samples the heap, the process's resident memory and that minute's frame times, then writes
 * `soak.csv`, `soak.md` and `soak.svg` to `build/reports/soak/`.
 *
 * It fails when a frame stalls past [SoakLimits.stallMs], or when the heap or resident memory kept
 * growing from the first quarter of the run to the last -- see [judge].
 */
class ServiceSoak {

    private val minutes = System.getProperty("soak.minutes")?.toDoubleOrNull() ?: DEFAULT_MINUTES
    private val fps = System.getProperty("soak.fps")?.toIntOrNull() ?: DEFAULT_FPS
    private val cueSeconds = System.getProperty("soak.cueSeconds")?.toLongOrNull() ?: DEFAULT_CUE_SECONDS
    private val sampleSeconds = System.getProperty("soak.sampleSeconds")?.toLongOrNull() ?: DEFAULT_SAMPLE_SECONDS

    @Test
    fun `a long service neither leaks nor stalls`() {
        val photo = BenchmarkScenarios.photo()
        val scenarios = BenchmarkScenarios.all(photo)
        val cue = mutableIntStateOf(0)
        var cuesShown = 0
        val run = OffscreenOutput(WIDTH, HEIGHT) { frame -> scenarios[cue.intValue].second(frame) }.use { output ->
            run(
                output,
                content = { scenarios[cue.intValue].first },
                warmingUp = { cuesShown < scenarios.size },
            ) {
                cuesShown++
                cue.intValue = cuesShown % scenarios.size
            }
        }
        photo.parentFile.deleteRecursively()

        val limits = SoakLimits()
        val verdict = judge(run.samples, limits)
        val description = "${scenarios.size} content types in turn, ${cueSeconds}s a cue, on one " +
            "${WIDTH}x$HEIGHT off-screen output at $fps fps for $minutes minutes; sampled every " +
            "${sampleSeconds}s. The first pass through every content type is warm-up."
        System.getProperty("soak.reportDir")?.let { dir ->
            val out = File(dir).apply { mkdirs() }
            File(out, "soak.csv").writeText(soakCsv(run.samples))
            File(out, "soak.md").writeText(soakMarkdown(description, verdict, limits, run.worstByContent))
            File(out, "soak.svg").writeText(soakChart(run.samples, frameBudgetMs(WIDTH, HEIGHT)))
        }
        assertTrue(verdict.passed, verdict.failures.joinToString("; "))
    }

    /** What a run recorded: its samples, and the worst frame each content type drew after warm-up. */
    private class Run(val samples: List<SoakSample>, val worstByContent: Map<String, Double>)

    /**
     * Renders at [fps] in real time until [minutes] have passed, moving to the next cue every
     * [cueSeconds]. A window is warm-up if any of its frames was drawn while [warmingUp] said so;
     * [content] names what is on screen, so the worst frame can be put down to it.
     */
    private fun run(
        output: OffscreenOutput,
        content: () -> String,
        warmingUp: () -> Boolean,
        onCue: () -> Unit,
    ): Run {
        val frameInterval = NANOS_PER_SECOND / fps
        val start = System.nanoTime()
        val end = start + (minutes * SECONDS_PER_MINUTE * NANOS_PER_SECOND).toLong()
        var nextCue = start + cueSeconds * NANOS_PER_SECOND
        var nextSample = start + sampleSeconds * NANOS_PER_SECOND
        var deadline = start
        val window = ArrayList<Long>()
        var late = 0
        var windowWarming = false
        val samples = mutableListOf<SoakSample>()
        val worst = mutableMapOf<String, Double>()
        while (System.nanoTime() < end) {
            val now = System.nanoTime()
            if (now < deadline) {
                // Pacing, not waiting on a condition: an output renders on a clock.
                LockSupport.parkNanos(deadline - now)
            } else if (now - deadline > frameInterval) {
                late++
            }
            deadline = maxOf(deadline + frameInterval, System.nanoTime())
            val warming = warmingUp()
            val showing = content()
            val cost = output.step().totalNanos
            window += cost
            windowWarming = windowWarming || warming
            if (!warming) worst.merge(showing, cost / NANOS_PER_MILLI, ::maxOf)
            if (System.nanoTime() >= nextCue) {
                onCue()
                nextCue += cueSeconds * NANOS_PER_SECOND
            }
            if (System.nanoTime() >= nextSample) {
                samples += sample(minutesSince(start), window, late, windowWarming)
                window.clear()
                late = 0
                windowWarming = false
                nextSample += sampleSeconds * NANOS_PER_SECOND
            }
        }
        if (window.isNotEmpty()) samples += sample(minutesSince(start), window, late, windowWarming)
        return Run(samples, worst)
    }

    private fun minutesSince(start: Long) =
        (System.nanoTime() - start).toDouble() / NANOS_PER_SECOND / SECONDS_PER_MINUTE

    private fun sample(minute: Double, frameNanos: List<Long>, late: Int, warmup: Boolean): SoakSample {
        val stats = FrameStats.of(frameNanos.toLongArray())
        @Suppress("ExplicitGarbageCollectionCall") // the sample is of what survives a collection
        System.gc()
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used / BYTES_PER_MB
        return SoakSample(
            minute, heap, residentMb(), frameNanos.size, late, stats.p50Ms, stats.p99Ms, stats.maxMs, warmup,
        )
    }

    /** VmRSS from `/proc/self/status`, where there is one (Linux, which is where the CI run is). */
    private fun residentMb(): Double? = runCatching {
        File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmRSS:") }
            ?.split(Regex("\\s+"))?.getOrNull(1)?.toDouble()?.div(KB_PER_MB)
    }.getOrNull()

    private companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
        const val DEFAULT_MINUTES = 240.0
        const val DEFAULT_FPS = 30
        const val DEFAULT_CUE_SECONDS = 20L
        const val DEFAULT_SAMPLE_SECONDS = 60L
    }
}
