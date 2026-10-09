package org.churchpresenter.app.churchpresenter.benchmark

import java.util.Locale

/**
 * One window of a soak run: when it ended, what memory looked like after a collection, and how the
 * frames in it went.
 */
data class SoakSample(
    val minute: Double,
    /** Java heap in use after a full collection, in MB. */
    val heapMb: Double,
    /** The process's resident set, in MB, or null where the OS does not say. Native (Skia) memory shows here. */
    val rssMb: Double?,
    val frames: Int,
    /** Frames that started after their deadline because the one before ran long. */
    val lateFrames: Int,
    val p50Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
    /**
     * Whether the window fell in the first pass through every content type. Each one's first
     * appearance loads fonts, images and code, so these windows are reported but not judged.
     */
    val warmup: Boolean = false,
    /** Times the UI thread left `UiWatchdog`'s ping unanswered past [SoakLimits.uiStallMs]. */
    val uiStalls: Int = 0,
    /** The longest the UI thread took to answer a ping in this window, in ms. */
    val maxUiStallMs: Double = 0.0,
)

/** How much memory may grow, and how long one frame may take, before a soak run fails. */
data class SoakLimits(
    val heapGrowthMb: Double = 64.0,
    val rssGrowthMb: Double = 256.0,
    val stallMs: Double = 250.0,
    /** How long the UI thread may leave `UiWatchdog`'s ping unanswered: its own budget. */
    val uiStallMs: Double = 250.0,
    /** Fewer samples than this cannot tell growth from warm-up, so growth is reported but not judged. */
    val minSamplesToJudge: Int = 8,
)

/** What a soak run found, and whether it passes. */
data class SoakVerdict(
    val heapGrowthMb: Double,
    val rssGrowthMb: Double?,
    val worstFrameMs: Double,
    /** The worst frame during warm-up: what the first appearance of some content costs. */
    val warmupWorstFrameMs: Double,
    val lateFrames: Int,
    val totalFrames: Int,
    val judgedGrowth: Boolean,
    /** UI-thread stalls after warm-up, and the longest. */
    val uiStalls: Int = 0,
    val worstUiStallMs: Double = 0.0,
    val failures: List<String>,
) {
    val passed: Boolean get() = failures.isEmpty()
}

private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

/**
 * Growth from the start of a run to its end, as medians so one noisy sample cannot decide it: the
 * first quarter after the first sample (which still holds warm-up) against the last quarter.
 */
internal fun growth(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    val settled = values.drop(1)
    val quarter = (settled.size / 4).coerceAtLeast(1)
    return median(settled.takeLast(quarter)) - median(settled.take(quarter))
}

/**
 * Judges a run after its warm-up: any frame past the stall limit fails it, so does any time the UI
 * thread stopped answering past its budget, and so does memory that kept growing.
 */
fun judge(all: List<SoakSample>, limits: SoakLimits = SoakLimits()): SoakVerdict {
    val samples = all.filterNot { it.warmup }
    val heapGrowth = growth(samples.map { it.heapMb })
    val rss = samples.mapNotNull { it.rssMb }
    val rssGrowth = if (rss.size == samples.size && rss.isNotEmpty()) growth(rss) else null
    val worst = samples.maxOfOrNull { it.maxMs } ?: 0.0
    val judged = samples.size >= limits.minSamplesToJudge
    val uiStalls = samples.sumOf { it.uiStalls }
    val worstUi = samples.maxOfOrNull { it.maxUiStallMs } ?: 0.0
    val failures = buildList {
        if (worst > limits.stallMs) add("a frame took ${fmt(worst)} ms, past the ${fmt(limits.stallMs)} ms stall limit")
        if (uiStalls > 0) {
            add(
                "the UI thread stalled $uiStalls time(s), the longest ${fmt(worstUi)} ms, " +
                    "past ${fmt(limits.uiStallMs)} ms",
            )
        }
        if (judged && heapGrowth > limits.heapGrowthMb) {
            add("the heap grew ${fmt(heapGrowth)} MB, past ${fmt(limits.heapGrowthMb)} MB")
        }
        if (judged && rssGrowth != null && rssGrowth > limits.rssGrowthMb) {
            add("resident memory grew ${fmt(rssGrowth)} MB, past ${fmt(limits.rssGrowthMb)} MB")
        }
    }
    return SoakVerdict(
        heapGrowthMb = heapGrowth,
        rssGrowthMb = rssGrowth,
        worstFrameMs = worst,
        warmupWorstFrameMs = all.filter { it.warmup }.maxOfOrNull { it.maxMs } ?: 0.0,
        lateFrames = samples.sumOf { it.lateFrames },
        totalFrames = samples.sumOf { it.frames },
        judgedGrowth = judged,
        uiStalls = uiStalls,
        worstUiStallMs = worstUi,
        failures = failures,
    )
}

private fun fmt(value: Double) = String.format(Locale.ROOT, "%.1f", value)
private fun fmt2(value: Double) = String.format(Locale.ROOT, "%.2f", value)

/** Every sample as CSV, one row per window. */
fun soakCsv(samples: List<SoakSample>): String = buildString {
    appendLine("minute,heap_mb,rss_mb,frames,late_frames,p50_ms,p99_ms,max_ms,warmup,ui_stalls,max_ui_stall_ms")
    samples.forEach { s ->
        appendLine(
            listOf(
                fmt2(s.minute), fmt(s.heapMb), s.rssMb?.let(::fmt).orEmpty(), s.frames, s.lateFrames,
                fmt2(s.p50Ms), fmt2(s.p99Ms), fmt2(s.maxMs), s.warmup, s.uiStalls, fmt(s.maxUiStallMs),
            ).joinToString(","),
        )
    }
}

/**
 * The run's summary and verdict, for a reviewer, with the worst frame each content type drew after
 * warm-up when [worstByContent] has them, worst first.
 */
fun soakMarkdown(
    description: String,
    verdict: SoakVerdict,
    limits: SoakLimits,
    worstByContent: Map<String, Double> = emptyMap(),
): String = buildString {
    appendLine("# Soak test")
    appendLine()
    appendLine(description)
    appendLine()
    appendLine("**${if (verdict.passed) "Passed" else "Failed"}**")
    verdict.failures.forEach { appendLine("- $it") }
    appendLine()
    appendLine("| | |")
    appendLine("|---|---|")
    appendLine("| Frames after warm-up | ${verdict.totalFrames} (${verdict.lateFrames} late) |")
    appendLine("| Worst frame | ${fmt2(verdict.worstFrameMs)} ms (limit ${fmt(limits.stallMs)}) |")
    appendLine("| Worst frame in warm-up | ${fmt2(verdict.warmupWorstFrameMs)} ms — first appearance, not judged |")
    appendLine(
        "| UI-thread stalls | ${verdict.uiStalls}, longest ${fmt(verdict.worstUiStallMs)} ms " +
            "(budget ${fmt(limits.uiStallMs)}) |",
    )
    val judged = if (verdict.judgedGrowth) "" else " — too short to judge"
    appendLine("| Heap growth | ${fmt(verdict.heapGrowthMb)} MB (limit ${fmt(limits.heapGrowthMb)})$judged |")
    val rss = verdict.rssGrowthMb?.let { "${fmt(it)} MB (limit ${fmt(limits.rssGrowthMb)})$judged" }
        ?: "not reported by this OS"
    appendLine("| Resident memory growth | $rss |")
    if (worstByContent.isNotEmpty()) {
        appendLine()
        appendLine("| Content | Worst frame after warm-up |")
        appendLine("|---|---:|")
        worstByContent.entries.sortedByDescending { it.value }.forEach { (name, ms) ->
            appendLine("| $name | ${fmt2(ms)} ms |")
        }
    }
    appendLine()
    appendLine("Per-window samples are in `soak.csv`, charted in `soak.svg`.")
}

private const val CHART_WIDTH = 900
private const val PANEL_HEIGHT = 220
private const val MARGIN = 50
private const val GAP = 40

/**
 * Memory and frame time over the run as a self-contained SVG: heap and resident memory above,
 * p99 and worst frame below with the [budgetMs] line. Each panel is scaled to its maximum after
 * warm-up, which is shaded and clipped to it.
 */
fun soakChart(samples: List<SoakSample>, budgetMs: Double): String {
    val height = MARGIN * 2 + PANEL_HEIGHT * 2 + GAP
    val plotWidth = CHART_WIDTH - MARGIN * 2
    val lastMinute = samples.maxOfOrNull { it.minute }?.takeIf { it > 0 } ?: 1.0
    fun x(minute: Double) = MARGIN + minute / lastMinute * plotWidth
    // Scaled to the settled run: a warm-up spike would flatten everything after it, so it is clipped.
    val settled = samples.indices.filterNot { samples[it].warmup }.ifEmpty { samples.indices.toList() }
    val warmupEnd = samples.indexOfLast { it.warmup }.takeIf { it >= 0 }?.let { x(samples[it].minute) }
    fun panel(
        top: Int,
        title: String,
        series: List<Pair<String, List<Double?>>>,
        colors: List<String>,
        guide: Double?,
    ): String {
        val peak = (series.flatMap { (_, values) -> settled.mapNotNull { values[it] } } + listOfNotNull(guide))
            .maxOrNull()?.takeIf { it > 0 } ?: 1.0
        fun y(value: Double) = top + PANEL_HEIGHT - minOf(value, peak) / peak * PANEL_HEIGHT
        return buildString {
            warmupEnd?.let {
                append("<rect x='$MARGIN' y='$top' width='${fmt2(it - MARGIN)}' height='$PANEL_HEIGHT' fill='#eee'/>")
                append("<text x='${MARGIN + 4}' y='${top + 14}' font-size='11' fill='#777'>warm-up</text>")
            }
            append("<rect x='$MARGIN' y='$top' width='$plotWidth' height='$PANEL_HEIGHT' fill='none' stroke='#999'/>")
            append("<text x='$MARGIN' y='${top - 8}' font-size='13'>$title (max ${fmt(peak)})</text>")
            guide?.let {
                val gy = fmt2(y(it))
                append("<line x1='$MARGIN' y1='$gy' x2='${MARGIN + plotWidth}' y2='$gy' ")
                append("stroke='#c33' stroke-dasharray='4 4'/>")
            }
            series.forEachIndexed { i, (name, values) ->
                val points = samples.indices.mapNotNull { idx ->
                    values[idx]?.let { "${fmt2(x(samples[idx].minute))},${fmt2(y(it))}" }
                }
                if (points.isNotEmpty()) {
                    append("<polyline fill='none' stroke='${colors[i]}' stroke-width='1.5' ")
                    append("points='${points.joinToString(" ")}'/>")
                }
                val labelY = top + 16 + i * 16
                append("<text x='${MARGIN + plotWidth - 160}' y='$labelY' font-size='12' ")
                append("fill='${colors[i]}'>$name</text>")
            }
        }
    }
    return buildString {
        append("<svg xmlns='http://www.w3.org/2000/svg' width='$CHART_WIDTH' height='$height' ")
        append("font-family='sans-serif'>")
        append("<rect width='100%' height='100%' fill='white'/>")
        append(
            panel(
                MARGIN, "Memory, MB",
                listOf("heap after GC" to samples.map { it.heapMb }, "resident" to samples.map { it.rssMb }),
                listOf("#2b6cb0", "#805ad5"), null,
            ),
        )
        append(
            panel(
                MARGIN + PANEL_HEIGHT + GAP, "Frame time, ms",
                listOf("p99" to samples.map { it.p99Ms }, "worst" to samples.map { it.maxMs }),
                listOf("#2f855a", "#dd6b20"), budgetMs,
            ),
        )
        append("<text x='$MARGIN' y='${height - 12}' font-size='12'>minutes 0 to ${fmt(lastMinute)}</text>")
        append("</svg>")
    }
}
