package org.churchpresenter.app.churchpresenter.benchmark

import java.util.Locale

private const val NANOS_PER_MILLI = 1_000_000.0
private const val DROPPED_AFTER_INTERVALS = 1.5

/** One injected stall level and what the output window showed while it ran. */
data class IsolationResult(
    /** The stall put on the event thread, a second after the last one ended; 0 is the undisturbed run. */
    val stallMs: Long,
    val frames: Int,
    /** The longest the output went without a new frame. */
    val longestGapMs: Double,
    /** Frames that came more than one and a half refreshes after the last. */
    val droppedFrames: Int,
)

/** [gaps] (frame-to-frame, in ns) as an [IsolationResult] at a [refreshHz] display. */
fun isolationResult(stallMs: Long, refreshHz: Int, gaps: LongArray): IsolationResult {
    val refreshNanos = 1_000_000_000.0 / refreshHz
    return IsolationResult(
        stallMs = stallMs,
        frames = gaps.size,
        longestGapMs = (gaps.maxOrNull() ?: 0L) / NANOS_PER_MILLI,
        droppedFrames = gaps.count { it > refreshNanos * DROPPED_AFTER_INTERVALS },
    )
}

/** The table `docs/SHOW_CONTROL.md` quotes. */
fun isolationMarkdown(info: RunInfo, renderApi: String, refreshHz: Int, results: List<IsolationResult>): String =
    buildString {
        appendLine("# Output isolation")
        appendLine()
        appendLine("One on-screen output window (1920x1080, $renderApi, $refreshHz Hz)")
        appendLine("drawing a changing song verse,")
        appendLine("while the operator UI's event thread is blocked for the stall shown, a second after each one ends.")
        appendLine("${info.os}, ${info.arch}, ${info.cpus} CPUs, ${info.jvm}.")
        appendLine()
        appendLine("| Injected stall | Frames | Longest gap (ms) | Dropped frames |")
        appendLine("|---:|---:|---:|---:|")
        results.forEach { r ->
            appendLine(
                "| ${r.stallMs} ms | ${r.frames} | ${String.format(Locale.ROOT, "%.1f", r.longestGapMs)} " +
                    "| ${r.droppedFrames} |",
            )
        }
    }
