package org.churchpresenter.app.churchpresenter.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How a soak run is judged and reported: memory that kept growing and frames that stalled fail it,
 * warm-up and one noisy sample do not, and the CSV, summary and chart say what was found.
 */
class SoakAnalysisTest {

    private fun sample(
        minute: Int,
        heap: Double,
        rss: Double? = 500.0,
        maxMs: Double = 20.0,
        warmup: Boolean = false,
        uiStalls: Int = 0,
        maxUiStallMs: Double = 40.0,
    ) = SoakSample(
        minute.toDouble(), heap, rss, frames = 1800, lateFrames = 0, p50Ms = 10.0, p99Ms = 15.0,
        maxMs = maxMs, warmup = warmup, uiStalls = uiStalls, maxUiStallMs = maxUiStallMs,
    )

    private fun steady(count: Int = 12) = (1..count).map { sample(it, heap = 200.0) }

    @Test
    fun `growth compares the settled start with the end, as medians`() {
        assertEquals(0.0, growth(listOf(50.0)))
        // The first value is warm-up and is ignored; one spike in the last quarter is outvoted.
        assertEquals(0.0, growth(listOf(900.0) + List(11) { 100.0 } + 400.0))
        assertEquals(60.0, growth(listOf(0.0) + (1..8).map { it * 10.0 + 10.0 }))
    }

    @Test
    fun `a steady run passes`() {
        val verdict = judge(steady())
        assertTrue(verdict.passed, verdict.failures.toString())
        assertTrue(verdict.judgedGrowth)
        assertEquals(12 * 1800, verdict.totalFrames)
    }

    @Test
    fun `a heap that keeps growing fails the run`() {
        val leaking = (1..12).map { sample(it, heap = 200.0 + it * 20) }
        val verdict = judge(leaking)
        assertFalse(verdict.passed)
        assertTrue(verdict.failures.single().startsWith("the heap grew"), verdict.failures.toString())
    }

    @Test
    fun `resident memory that keeps growing fails the run, as native memory`() {
        val leaking = (1..12).map { sample(it, heap = 200.0, rss = 500.0 + it * 60) }
        val verdict = judge(leaking)
        assertTrue(verdict.failures.single().startsWith("resident memory grew"), verdict.failures.toString())
    }

    @Test
    fun `one stalled frame fails the run however short it is`() {
        val verdict = judge(listOf(sample(1, 200.0), sample(2, 200.0, maxMs = 400.0)))
        assertFalse(verdict.judgedGrowth)
        assertEquals(400.0, verdict.worstFrameMs)
        assertTrue(verdict.failures.single().startsWith("a frame took 400.0 ms"))
    }

    @Test
    fun `the UI thread stalling past its budget fails the run, and says how often and how long`() {
        val verdict = judge(steady(4) + sample(5, 200.0, uiStalls = 2, maxUiStallMs = 640.0))
        assertEquals(2, verdict.uiStalls)
        assertEquals(640.0, verdict.worstUiStallMs)
        assertEquals("the UI thread stalled 2 time(s), the longest 640.0 ms, past 250.0 ms", verdict.failures.single())
        val markdown = soakMarkdown("run", verdict, SoakLimits())
        assertTrue("| UI-thread stalls | 2, longest 640.0 ms (budget 250.0) |" in markdown)
    }

    @Test
    fun `a UI stall during warm-up is not judged`() {
        val verdict = judge(listOf(sample(0, 200.0, warmup = true, uiStalls = 3, maxUiStallMs = 900.0)) + steady())
        assertTrue(verdict.passed, verdict.failures.toString())
        assertEquals(0, verdict.uiStalls)
    }

    @Test
    fun `warm-up is reported but neither its stalls nor its memory are judged`() {
        val run = listOf(
            sample(1, heap = 50.0, rss = 300.0, maxMs = 800.0, warmup = true),
            sample(2, heap = 120.0, rss = 600.0, maxMs = 300.0, warmup = true),
        ) + steady()
        val verdict = judge(run)
        assertTrue(verdict.passed, verdict.failures.toString())
        assertEquals(800.0, verdict.warmupWorstFrameMs)
        assertEquals(20.0, verdict.worstFrameMs)
        assertEquals(12 * 1800, verdict.totalFrames, "warm-up frames are not counted")
        assertTrue("800.00 ms — first appearance, not judged" in soakMarkdown("run", verdict, SoakLimits()))
    }

    @Test
    fun `a run too short to judge reports growth without failing on it`() {
        val verdict = judge((1..4).map { sample(it, heap = 100.0 * it) })
        assertFalse(verdict.judgedGrowth)
        assertTrue(verdict.passed)
        assertTrue(verdict.heapGrowthMb > 0)
    }

    @Test
    fun `resident memory is left unjudged where the OS does not report it`() {
        val verdict = judge((1..12).map { sample(it, heap = 200.0, rss = null) })
        assertNull(verdict.rssGrowthMb)
        assertTrue(verdict.passed)
        assertTrue("not reported by this OS" in soakMarkdown("run", verdict, SoakLimits()))
    }

    @Test
    fun `the CSV has a row per sample and leaves a missing reading empty`() {
        val csv = soakCsv(listOf(sample(1, 200.0), sample(2, 210.5, rss = null))).trim().lines()
        assertEquals(
            "minute,heap_mb,rss_mb,frames,late_frames,p50_ms,p99_ms,max_ms,warmup,ui_stalls,max_ui_stall_ms",
            csv[0],
        )
        assertEquals("1.00,200.0,500.0,1800,0,10.00,15.00,20.00,false,0,40.0", csv[1])
        assertEquals("2.00,210.5,,1800,0,10.00,15.00,20.00,false,0,40.0", csv[2])
    }

    @Test
    fun `the summary says whether it passed and why not`() {
        val limits = SoakLimits()
        val failed = judge(listOf(sample(1, 200.0, maxMs = 300.0)), limits)
        val markdown = soakMarkdown("A test run.", failed, limits)
        assertTrue("**Failed**" in markdown)
        assertTrue("- a frame took 300.0 ms" in markdown)
        assertTrue("too short to judge" in markdown)
        assertTrue("**Passed**" in soakMarkdown("A test run.", judge(steady(), limits), limits))
        val worst = mapOf("song" to 12.0, "picture" to 180.5)
        val byContent = soakMarkdown("A test run.", judge(steady(), limits), limits, worst)
        assertTrue(byContent.indexOf("| picture | 180.50 ms |") < byContent.indexOf("| song | 12.00 ms |"), byContent)
    }

    @Test
    fun `the chart draws both panels, the budget line, and skips missing readings`() {
        val svg = soakChart(listOf(sample(0, 200.0, rss = null), sample(1, 210.0), sample(2, 220.0)), budgetMs = 16.7)
        assertTrue(svg.startsWith("<svg") && svg.endsWith("</svg>"))
        assertEquals(4, Regex("<polyline").findAll(svg).count(), "heap, resident, p99 and worst")
        assertEquals(1, Regex("stroke-dasharray").findAll(svg).count(), "one budget line, on the frame panel")
        val resident = Regex("stroke='#805ad5'[^>]*points='([^']*)'").find(svg)!!.groupValues[1]
        assertEquals(2, resident.split(" ").size, "the sample with no reading is left out of its line")
    }

    @Test
    fun `the chart shades warm-up and scales to the run after it`() {
        val svg = soakChart(
            listOf(sample(0, 200.0, maxMs = 900.0, warmup = true), sample(1, 210.0), sample(2, 220.0)),
            budgetMs = 16.7,
        )
        assertTrue("warm-up" in svg)
        assertTrue("Frame time, ms (max 20.0)" in svg, "the 900 ms warm-up frame does not set the scale: $svg")
    }

    @Test
    fun `an empty run still produces a chart`() {
        assertTrue(soakChart(emptyList(), budgetMs = 16.7).contains("minutes 0 to 1.0"))
    }
}
