package org.churchpresenter.app.churchpresenter.benchmark

import java.util.Locale

private const val NANOS_PER_SECOND = 1_000_000_000.0

/** A frame more than this many refresh intervals after the one before it was a dropped frame. */
private const val DROPPED_AFTER_INTERVALS = 1.5

/**
 * One content type on an on-screen output window: the time between consecutive frames it drew,
 * and how many of those gaps were long enough to have missed a refresh.
 */
data class GpuResult(
    val scenario: String,
    /** The window's size in device pixels. */
    val width: Int,
    val height: Int,
    val refreshHz: Int,
    val interval: FrameStats,
    val frames: Int,
    val dropped: Int,
)

/** [nanos], the gaps between consecutive frames, judged against a [refreshHz] display. */
fun gpuResult(scenario: String, width: Int, height: Int, refreshHz: Int, nanos: LongArray): GpuResult {
    val refreshNanos = NANOS_PER_SECOND / refreshHz
    return GpuResult(
        scenario = scenario,
        width = width,
        height = height,
        refreshHz = refreshHz,
        interval = FrameStats.of(nanos),
        frames = nanos.size,
        dropped = nanos.count { it > refreshNanos * DROPPED_AFTER_INTERVALS },
    )
}

private fun ms(value: Double) = String.format(Locale.ROOT, "%.2f", value)

private fun String.json() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** The run as JSON: one object per scenario and size, every interval in milliseconds. */
fun gpuJson(info: RunInfo, renderApi: String, results: List<GpuResult>): String = buildString {
    appendLine("{")
    appendLine("  \"run\": {")
    appendLine("    \"os\": ${info.os.json()},")
    appendLine("    \"arch\": ${info.arch.json()},")
    appendLine("    \"cpus\": ${info.cpus},")
    appendLine("    \"jvm\": ${info.jvm.json()},")
    appendLine("    \"renderApi\": ${renderApi.json()},")
    appendLine("    \"warmupFrames\": ${info.warmupFrames},")
    appendLine("    \"measuredFrames\": ${info.measuredFrames}")
    appendLine("  },")
    appendLine("  \"results\": [")
    results.forEachIndexed { i, r ->
        val s = r.interval
        append("    {\"scenario\": ${r.scenario.json()}, \"width\": ${r.width}, \"height\": ${r.height}, ")
        append("\"refreshHz\": ${r.refreshHz}, ")
        append("\"intervalMs\": {\"p50\": ${ms(s.p50Ms)}, \"p95\": ${ms(s.p95Ms)}, \"p99\": ${ms(s.p99Ms)}, ")
        append("\"max\": ${ms(s.maxMs)}}, \"frames\": ${r.frames}, \"dropped\": ${r.dropped}}")
        appendLine(if (i < results.lastIndex) "," else "")
    }
    appendLine("  ]")
    appendLine("}")
}

/** The run as a Markdown table, the rows that dropped frames marked, for a reviewer to read. */
fun gpuMarkdown(info: RunInfo, renderApi: String, results: List<GpuResult>): String = buildString {
    appendLine("# GPU output benchmark")
    appendLine()
    appendLine("On-screen output windows (the projector path), drawn by Skia on the GPU ($renderApi).")
    appendLine("${info.os}, ${info.arch}, ${info.cpus} CPUs, ${info.jvm}.")
    appendLine("${info.warmupFrames} warm-up frames, ${info.measuredFrames} measured. Frame-to-frame intervals in ms;")
    appendLine("a frame more than ${DROPPED_AFTER_INTERVALS}x the refresh interval after the last is counted dropped.")
    appendLine()
    appendLine("| Scenario | Size | Refresh | Interval p50 | Interval p99 | Worst | Dropped | |")
    appendLine("|---|---|---:|---:|---:|---:|---:|---|")
    results.forEach { r ->
        val flag = if (r.dropped > 0) "drops" else ""
        appendLine(
            "| ${r.scenario} | ${r.width}x${r.height} | ${r.refreshHz} Hz | ${ms(r.interval.p50Ms)} | " +
                "${ms(r.interval.p99Ms)} | ${ms(r.interval.maxMs)} | ${r.dropped} of ${r.frames} | $flag |",
        )
    }
}
