package org.churchpresenter.web.offscreen

import org.churchpresenter.diagnostics.CrashReporter
import java.util.concurrent.atomic.AtomicInteger

/** What a page tells Sentry; see [OffscreenReporter]. */
internal sealed interface OffscreenReport {
    val tags: Map<String, String>

    data class PageFailed(val failure: Throwable, override val tags: Map<String, String>) : OffscreenReport
    data class PaintThrew(val failure: Throwable, override val tags: Map<String, String>) : OffscreenReport
    data class LowFrameRate(override val tags: Map<String, String>, val extras: Map<String, String>) : OffscreenReport
    data class Session(override val tags: Map<String, String>, val extras: Map<String, String>) : OffscreenReport
}

/**
 * How one off-screen page is doing, for Sentry.
 *
 * - A paint that throws is sent once per page ([paintThrew]).
 * - **Low frame rate**: once a second [tick] gets the page's counts. While Chromium paints at least
 *   [LOW_RATE_MIN_PAINTS] a second but the busiest output draws fewer than [LOW_RATE_SHARE] of them,
 *   for [LOW_RATE_HOLD_MS] running, one warning goes for the page. A page that does not change paints
 *   nothing, so it never counts.
 * - **Session summary**: [finish] sends one event for a page that was open at least [SESSION_MIN_MS],
 *   at most [MAX_SESSIONS] in a run ([sessionsSent] is the run's count).
 *
 * [tags] says where it ran (OS, render API, size, scale, pixel order) and is read as each report goes,
 * so a resize is in it.
 */
internal class OffscreenReporter(
    private val tags: () -> Map<String, String>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val send: (OffscreenReport) -> Unit = ::sendToSentry,
    private val sessionsSent: AtomicInteger = SESSIONS_SENT,
) {
    private val openedAt = clock()
    private var lastTick = openedAt
    private var paintThrewSent = false
    private var lowSince = NONE
    private var lowSent = false
    private var paints = 0L
    private var draws = 0L
    private var skipped = 0L
    private var copyNs = 0L
    private var latencyNs = 0L
    private var largest = ""

    fun paintThrew(failure: Throwable) {
        if (paintThrewSent) return
        paintThrewSent = true
        send(OffscreenReport.PaintThrew(failure, tags()))
    }

    /** The counts since the last tick, with [copyP95Ms] the page's copy-time p95 so far. */
    fun tick(sample: Sample, copyP95Ms: Double) {
        val now = clock()
        val from = lastTick
        val seconds = (now - from).coerceAtLeast(1) / MS_PER_SECOND
        lastTick = now
        paints += sample.paints
        draws += sample.draws
        skipped += sample.skipped
        copyNs += sample.copyNs
        latencyNs += sample.latencyNs
        tags()[TAG_SIZE]?.let { largest = it }
        val paintRate = sample.paints / seconds
        val drawRate = sample.draws / seconds
        val low = paintRate >= LOW_RATE_MIN_PAINTS && drawRate < paintRate * LOW_RATE_SHARE
        if (!low) {
            lowSince = NONE
            return
        }
        // Behind since the start of the first slow sample, not its end.
        if (lowSince == NONE) lowSince = from
        if (lowSent || now - lowSince < LOW_RATE_HOLD_MS) return
        lowSent = true
        send(
            OffscreenReport.LowFrameRate(
                tags(),
                mapOf(
                    "paints_per_s" to "%.1f".format(paintRate),
                    "draws_per_s" to "%.1f".format(drawRate),
                    "skipped_per_s" to "%.1f".format(sample.skipped / seconds),
                    "copy_avg_ms" to avgMs(sample.copyNs, sample.paints),
                    "copy_max_ms" to "%.2f".format(sample.copyMaxNs / NS_PER_MS),
                    "copy_p95_ms" to "%.2f".format(copyP95Ms),
                    "latency_avg_ms" to avgMs(sample.latencyNs, sample.draws),
                ),
            ),
        )
    }

    /** The page closed: sends its summary when it was open long enough and the run has room for one. */
    fun finish(copyP95Ms: Double) {
        val ms = clock() - openedAt
        if (ms < SESSION_MIN_MS || sessionsSent.incrementAndGet() > MAX_SESSIONS) return
        val seconds = ms / MS_PER_SECOND
        send(
            OffscreenReport.Session(
                tags(),
                mapOf(
                    "duration_s" to "%.0f".format(seconds),
                    "paints_per_s" to "%.1f".format(paints / seconds),
                    "draws_per_s" to "%.1f".format(draws / seconds),
                    "skipped" to skipped.toString(),
                    "copy_avg_ms" to avgMs(copyNs, paints),
                    "copy_p95_ms" to "%.2f".format(copyP95Ms),
                    "latency_avg_ms" to avgMs(latencyNs, draws),
                    "largest_size" to largest,
                ),
            ),
        )
    }

    private fun avgMs(totalNs: Long, count: Long) = if (count == 0L) "-" else "%.2f".format(totalNs / count / NS_PER_MS)

    companion object {
        const val TAG_SIZE = "web.offscreen.size"
        const val LOW_RATE_MIN_PAINTS = 50.0
        const val LOW_RATE_SHARE = 0.85
        const val LOW_RATE_HOLD_MS = 30_000L
        const val SESSION_MIN_MS = 30_000L
        const val MAX_SESSIONS = 3
        private const val NONE = -1L
        private const val MS_PER_SECOND = 1000.0
        private val SESSIONS_SENT = AtomicInteger()
    }
}

/** Sends [report] through [CrashReporter], each kind under one constant title. */
internal fun sendToSentry(report: OffscreenReport) {
    when (report) {
        is OffscreenReport.PageFailed ->
            CrashReporter.reportWarning("Off-screen web page failed to start", report.failure, report.tags)
        is OffscreenReport.PaintThrew ->
            CrashReporter.reportWarning("Off-screen web paint threw", report.failure, report.tags)
        is OffscreenReport.LowFrameRate ->
            CrashReporter.reportWarning(
                "Off-screen web output below frame rate",
                tags = report.tags,
                extras = report.extras,
            )
        is OffscreenReport.Session ->
            CrashReporter.reportInfo("Off-screen web session", tags = report.tags, extras = report.extras)
    }
}
