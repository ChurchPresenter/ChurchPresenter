package org.churchpresenter.app.churchpresenter.benchmark

import androidx.compose.runtime.Composable
import kotlin.math.ceil

private const val NANOS_PER_MILLI = 1_000_000.0

/** What one content type costs to put on one output size. */
data class ScenarioResult(
    val scenario: String,
    val width: Int,
    val height: Int,
    /** Composition, layout and drawing, per frame, in milliseconds. */
    val render: FrameStats,
    /** Reading the frame back into an ARGB array, as every off-screen output does. */
    val readback: FrameStats,
    /** Pixels the last measured frame drew on, so a scenario that showed nothing is caught. */
    val drawnPixels: Int,
) {
    /** Render and readback together: what one off-screen output frame costs. */
    val totalP99Ms: Double get() = render.p99Ms + readback.p99Ms
}

/** Percentiles over a run of frame times. */
data class FrameStats(val p50Ms: Double, val p95Ms: Double, val p99Ms: Double, val maxMs: Double) {
    companion object {
        /** Nearest-rank percentiles over [nanos]; empty input is all zeros. */
        fun of(nanos: LongArray): FrameStats {
            if (nanos.isEmpty()) return FrameStats(0.0, 0.0, 0.0, 0.0)
            val sorted = nanos.sorted()
            fun pct(p: Double): Double {
                val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
                return sorted[rank - 1] / NANOS_PER_MILLI
            }
            return FrameStats(pct(50.0), pct(95.0), pct(99.0), sorted.last() / NANOS_PER_MILLI)
        }
    }
}

/**
 * Times a composable on an [OffscreenOutput]: the path the NDI, OMT and Browser Source outputs take.
 *
 * Warm-up runs until both [warmupFrames] have rendered and [warmupMillis] of wall time have passed:
 * pictures, Lottie fonts and the like load on background threads, and a frame timed before they
 * arrive measures an empty screen.
 */
class FrameTimer(
    val warmupFrames: Int,
    private val warmupMillis: Long,
    val measuredFrames: Int,
    private val clock: () -> Long = System::nanoTime,
) {
    fun measure(
        scenario: String,
        width: Int,
        height: Int,
        content: @Composable (frame: Int) -> Unit,
    ): ScenarioResult = OffscreenOutput(width, height, clock, content).use { output ->
        // Whether the content drew at all, from any frame, warm-up included: a scrolling notice can
        // be between laps for every one of a short measured run, and warm-up runs on a wall clock,
        // so where the scroll stands when measuring starts differs from machine to machine.
        var drawn = 0
        val warmupEnd = clock() + warmupMillis * NANOS_PER_MILLI.toLong()
        var warmed = 0
        while (warmed < warmupFrames || clock() < warmupEnd) {
            output.step()
            if (drawn == 0) drawn = output.drawnPixels()
            warmed++
        }
        val render = LongArray(measuredFrames)
        val readback = LongArray(measuredFrames)
        repeat(measuredFrames) { i ->
            val cost = output.step()
            render[i] = cost.renderNanos
            readback[i] = cost.readbackNanos
            if (drawn == 0) drawn = output.drawnPixels()
        }
        ScenarioResult(scenario, width, height, FrameStats.of(render), FrameStats.of(readback), drawn)
    }
}
