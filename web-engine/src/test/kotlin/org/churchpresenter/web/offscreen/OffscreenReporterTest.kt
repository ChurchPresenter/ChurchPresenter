package org.churchpresenter.web.offscreen

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What a page tells Sentry, on a clock of the test's own: the low-frame-rate warning (only when
 * Chromium paints fast and the busiest output falls behind for long enough, once), the session
 * summary (only for a session long enough, a few a run), and a thrown paint (once). Also the
 * counts the reports are made of.
 */
class OffscreenReporterTest {
    private var now = 0L
    private val sent = mutableListOf<OffscreenReport>()
    private val runSessions = AtomicInteger()
    private val reporter = OffscreenReporter(
        tags = { mapOf(OffscreenReporter.TAG_SIZE to "1920x1080") },
        clock = { now },
        send = { sent += it },
        sessionsSent = runSessions,
    )

    /** One second of [paints] paints and [draws] draws of the busiest output. */
    private fun second(paints: Long, draws: Long) {
        now += 1_000
        val sample = Sample(
            paints = paints,
            copyNs = paints * 1_000_000,
            copyMaxNs = 2_000_000,
            draws = draws,
            latencyNs = 0,
            skipped = 0,
        )
        reporter.tick(sample, 1.5)
    }

    @Test
    fun `an output that falls behind fast paints for long enough is reported once`() {
        repeat(29) { second(paints = 60, draws = 40) }
        assertTrue(sent.isEmpty(), "29 seconds behind is not yet long enough")

        second(paints = 60, draws = 40)
        val report = assertIs<OffscreenReport.LowFrameRate>(sent.single())
        assertEquals("60.0", report.extras["paints_per_s"])
        assertEquals("40.0", report.extras["draws_per_s"])
        assertEquals("1920x1080", report.tags[OffscreenReporter.TAG_SIZE])

        repeat(60) { second(paints = 60, draws = 40) }
        assertEquals(1, sent.size, "a page sends it once")
    }

    @Test
    fun `a second that keeps up starts the count again`() {
        repeat(20) { second(paints = 60, draws = 40) }
        second(paints = 60, draws = 60)
        repeat(20) { second(paints = 60, draws = 40) }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a page that paints slowly is never behind, however little is drawn`() {
        repeat(60) { second(paints = 30, draws = 0) }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a session of at least thirty seconds sends one summary`() {
        repeat(30) { second(paints = 60, draws = 60) }
        reporter.finish(copyP95Ms = 1.25)

        val report = assertIs<OffscreenReport.Session>(sent.single())
        assertEquals("30", report.extras["duration_s"])
        assertEquals("60.0", report.extras["draws_per_s"])
        assertEquals("1.25", report.extras["copy_p95_ms"])
        assertEquals("1920x1080", report.extras["largest_size"])
    }

    @Test
    fun `a short session sends nothing`() {
        repeat(29) { second(paints = 60, draws = 60) }
        reporter.finish(copyP95Ms = 1.0)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a run sends at most three summaries`() {
        runSessions.set(OffscreenReporter.MAX_SESSIONS)
        now += OffscreenReporter.SESSION_MIN_MS
        reporter.finish(copyP95Ms = 1.0)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a paint that throws is reported once`() {
        reporter.paintThrew(IllegalStateException("one"))
        reporter.paintThrew(IllegalStateException("two"))
        assertEquals("one", assertIs<OffscreenReport.PaintThrew>(sent.single()).failure.message)
    }

    @Test
    fun `the counts drain from the busiest output and start again`() {
        val stats = OffscreenStats()
        val quiet = Any()
        val busy = Any()
        repeat(3) { stats.paint(1_000_000) }
        stats.draw(quiet, latencyNs = 5, gap = 0)
        repeat(3) { stats.draw(busy, latencyNs = 10, gap = 1) }

        val sample = stats.drain()
        val wanted = Sample(
            paints = 3,
            copyNs = 3_000_000,
            copyMaxNs = 1_000_000,
            draws = 3,
            latencyNs = 30,
            skipped = 3,
        )
        assertEquals(wanted, sample)
        assertEquals(Sample(0, 0, 0, 0, 0, 0), stats.drain())

        stats.forget(busy)
        stats.draw(quiet, latencyNs = 1, gap = 0)
        assertEquals(1, stats.drain().draws)
    }

    @Test
    fun `the copy p95 is the bin that reaches ninety five percent`() {
        val stats = OffscreenStats()
        assertEquals(0.0, stats.copyP95Ms())
        repeat(95) { stats.paint(100_000) }
        repeat(5) { stats.paint(10_000_000) }
        assertEquals(0.25, stats.copyP95Ms())
    }
}
