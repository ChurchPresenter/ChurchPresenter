package org.churchpresenter.app.churchpresenter.benchmark

import androidx.compose.runtime.mutableIntStateOf
import org.churchpresenter.diagnostics.HungTestReporter
import org.churchpresenter.diagnostics.ThreadDump
import org.churchpresenter.diagnostics.UiWatchdog
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale
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
 * `soak.csv`, `soak.md` and `soak.svg` to `build/reports/soak/` -- the CSV after every sample, so a
 * run cut short still leaves its curve.
 *
 * It fails when a frame stalls past [SoakLimits.stallMs], when the UI thread leaves `UiWatchdog`'s
 * ping unanswered past [SoakLimits.uiStallMs] (the output renders on that thread, so a stall there
 * is a frozen screen), or when the heap or resident memory kept growing from the first quarter of
 * the run to the last -- see [judge]. A frame that never finishes
 * at all is caught by [SoakStallWatchdog], which writes every thread's stack to `soak-stall.txt`.
 */
class ServiceSoak {

    private val minutes = System.getProperty("soak.minutes")?.toDoubleOrNull() ?: DEFAULT_MINUTES
    private val fps = System.getProperty("soak.fps")?.toIntOrNull() ?: DEFAULT_FPS
    private val cueSeconds = System.getProperty("soak.cueSeconds")?.toLongOrNull() ?: DEFAULT_CUE_SECONDS
    private val sampleSeconds = System.getProperty("soak.sampleSeconds")?.toLongOrNull() ?: DEFAULT_SAMPLE_SECONDS
    private val stallSeconds = System.getProperty("soak.stallSeconds")?.toLongOrNull() ?: DEFAULT_STALL_SECONDS
    private val uiStalls = UiStallTally(SoakLimits().uiStallMs.toLong())

    // Emptied first: a run cut short must not leave the last run's files looking like its own.
    private val reportDir = System.getProperty("soak.reportDir")?.let { dir ->
        File(dir).apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun `a long service neither leaks nor stalls`() {
        val photo = BenchmarkScenarios.photo()
        val scenarios = BenchmarkScenarios.all(photo)
        val cue = mutableIntStateOf(0)
        var cuesShown = 0
        val limits = SoakLimits()
        UiWatchdog.start(budgetMs = limits.uiStallMs.toLong(), onAnswered = uiStalls::record)
        // Watched like a frame: building the output composes it for the first time, and a soak once
        // hung right there, before the frame loop and its own watchdog had started.
        val built = SoakStallWatchdog(
            limitNanos = stallSeconds * NANOS_PER_SECOND,
            onStall = { stalled -> reportStall(0.0, stalled, "the output being built") },
        ).start().use { watchdog ->
            watchdog.step { OffscreenOutput(WIDTH, HEIGHT) { frame -> scenarios[cue.intValue].second(frame) } }
        }
        val run = built.use { output ->
            run(
                output,
                content = { scenarios[cue.intValue].first },
                warmingUp = { cuesShown < scenarios.size },
            ) {
                cuesShown++
                cue.intValue = cuesShown % scenarios.size
            }
        }
        UiWatchdog.stop()
        photo.parentFile.deleteRecursively()

        val verdict = judge(run.samples, limits)
        val description = "${scenarios.size} content types in turn, ${cueSeconds}s a cue, on one " +
            "${WIDTH}x$HEIGHT off-screen output at $fps fps for $minutes minutes; sampled every " +
            "${sampleSeconds}s. The first pass through every content type is warm-up."
        reportDir?.let { out ->
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
        val watchdog = SoakStallWatchdog(
            limitNanos = stallSeconds * NANOS_PER_SECOND,
            onStall = { stalled -> reportStall(minutesSince(start), stalled, content()) },
        ).start()
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
            val cost = watchdog.step { output.step() }.totalNanos
            window += cost
            windowWarming = windowWarming || warming
            if (!warming) worst.merge(showing, cost / NANOS_PER_MILLI, ::maxOf)
            if (System.nanoTime() >= nextCue) {
                onCue()
                nextCue += cueSeconds * NANOS_PER_SECOND
            }
            if (System.nanoTime() >= nextSample) {
                samples += sample(minutesSince(start), window, late, windowWarming)
                // On disk as it grows, so a run killed part-way still leaves its curve behind.
                reportDir?.let { File(it, "soak.csv").writeText(soakCsv(samples)) }
                window.clear()
                late = 0
                windowWarming = false
                nextSample += sampleSeconds * NANOS_PER_SECOND
            }
        }
        watchdog.close()
        if (window.isNotEmpty()) samples += sample(minutesSince(start), window, late, windowWarming)
        return Run(samples, worst)
    }

    /**
     * One frame has not finished in [stallSeconds]: a stall, which is what this test exists to find.
     * Writes every thread's stack and a summary the workflow shows, then halts -- the stuck frame
     * holds the test thread, so there is no failing it the ordinary way, and waiting only lets the
     * task timeout kill it with nothing written.
     */
    private fun reportStall(minute: Double, stalledNanos: Long, showing: String) {
        val headline = "SOAK STALL: one frame has not finished in ${stalledNanos / NANOS_PER_SECOND}s, " +
            "at minute ${"%.1f".format(Locale.ROOT, minute)}, showing $showing ==="
        val dump = ThreadDump.text(
            "=== $headline\n=== The test thread is inside OffscreenOutput.step(); read it and the event queue.",
            depth = HungTestReporter.STACK_DEPTH,
        )
        System.err.println(dump)
        System.err.flush()
        reportDir?.let { out ->
            runCatching {
                File(out, "soak-stall.txt").writeText(dump)
                File(out, "soak.md").writeText(
                    "## Soak: stalled\n\n$headline\n\nEvery thread's stack is in `soak-stall.txt`, and the " +
                        "samples up to the stall in `soak.csv`, both in the `soak-report` artifact.\n",
                )
            }
        }
        Runtime.getRuntime().halt(HungTestReporter.HUNG_EXIT_CODE)
    }

    private fun minutesSince(start: Long) =
        (System.nanoTime() - start).toDouble() / NANOS_PER_SECOND / SECONDS_PER_MINUTE

    private fun sample(minute: Double, frameNanos: List<Long>, late: Int, warmup: Boolean): SoakSample {
        val stats = FrameStats.of(frameNanos.toLongArray())
        val (stalls, longestUiMs) = uiStalls.take()
        @Suppress("ExplicitGarbageCollectionCall") // the sample is of what survives a collection
        System.gc()
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used / BYTES_PER_MB
        return SoakSample(
            minute, heap, residentMb(), frameNanos.size, late, stats.p50Ms, stats.p99Ms, stats.maxMs, warmup,
            uiStalls = stalls, maxUiStallMs = longestUiMs.toDouble(),
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

        /** A frame costs tens of milliseconds; one still running after a minute is stuck, not slow. */
        const val DEFAULT_STALL_SECONDS = 60L
    }
}
