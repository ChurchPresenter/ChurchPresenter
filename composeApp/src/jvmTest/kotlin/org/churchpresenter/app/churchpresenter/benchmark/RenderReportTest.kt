package org.churchpresenter.app.churchpresenter.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The render benchmark's own arithmetic and reporting: percentiles, which size gets which budget,
 * what counts as over it, what the report says, and that the timer measures a real frame.
 */
class RenderReportTest {

    private fun result(name: String, w: Int, h: Int, renderP99: Double, readbackP99: Double) = ScenarioResult(
        scenario = name,
        width = w,
        height = h,
        render = FrameStats(renderP99 / 2, renderP99, renderP99, renderP99),
        readback = FrameStats(readbackP99, readbackP99, readbackP99, readbackP99),
        drawnPixels = 1,
    )

    @Test
    fun `percentiles are nearest-rank over the frames`() {
        val nanos = LongArray(100) { (it + 1) * 1_000_000L }
        val stats = FrameStats.of(nanos)
        assertEquals(50.0, stats.p50Ms)
        assertEquals(95.0, stats.p95Ms)
        assertEquals(99.0, stats.p99Ms)
        assertEquals(100.0, stats.maxMs)
    }

    @Test
    fun `a single frame is every percentile, and no frames are zero`() {
        assertEquals(FrameStats(2.0, 2.0, 2.0, 2.0), FrameStats.of(longArrayOf(2_000_000L)))
        assertEquals(FrameStats(0.0, 0.0, 0.0, 0.0), FrameStats.of(LongArray(0)))
    }

    @Test
    fun `1080p has a 60 fps budget and anything larger a 30 fps one`() {
        assertEquals(16.7, frameBudgetMs(1920, 1080))
        assertEquals(16.7, frameBudgetMs(1280, 720))
        assertEquals(33.3, frameBudgetMs(3840, 2160))
        assertEquals(33.3, frameBudgetMs(2560, 1440))
    }

    @Test
    fun `render and readback together decide whether a scenario is over budget`() {
        val inside = result("inside", 1920, 1080, renderP99 = 8.0, readbackP99 = 8.0)
        val over = result("over", 1920, 1080, renderP99 = 8.0, readbackP99 = 9.0)
        val big = result("4k", 3840, 2160, renderP99 = 20.0, readbackP99 = 10.0)
        assertEquals(listOf(over), overBudget(listOf(inside, over, big)))
        assertEquals(17.0, over.totalP99Ms)
    }

    @Test
    fun `the reports carry the run, every scenario and the over-budget mark`() {
        val info = RunInfo("Linux 6", "amd64", 4, "OpenJDK \"21\"", warmupFrames = 30, measuredFrames = 120)
        val results = listOf(
            result("song verse", 1920, 1080, renderP99 = 4.0, readbackP99 = 2.0),
            result("picture", 3840, 2160, renderP99 = 30.0, readbackP99 = 10.0),
        )
        val json = toJson(info, results)
        assertTrue("\"jvm\": \"OpenJDK \\\"21\\\"\"" in json, "quotes in a value are escaped: $json")
        assertTrue("\"scenario\": \"song verse\", \"width\": 1920, \"height\": 1080" in json, json)
        assertTrue("\"budgetMs\": 33.30" in json, json)
        assertEquals(2, Regex("\"scenario\"").findAll(json).count())

        val markdown = toMarkdown(info, results)
        assertTrue("| song verse | 1920x1080 | 2.00 | 4.00 | 2.00 | 6.00 | 16.70 |  |" in markdown, markdown)
        assertTrue("| picture | 3840x2160 | 15.00 | 30.00 | 10.00 | 40.00 | 33.30 | over |" in markdown, markdown)
        assertTrue("4 CPUs" in markdown)
    }

    @Test
    fun `the timer measures every frame and sees what was drawn`() {
        val timer = FrameTimer(warmupFrames = 1, warmupMillis = 0, measuredFrames = 5)
        val filled = timer.measure("filled", 32, 16) { Box(Modifier.fillMaxSize().background(Color.Red)) }
        assertEquals(32 * 16, filled.drawnPixels)
        assertTrue(filled.render.maxMs >= filled.render.p50Ms && filled.render.p50Ms >= 0.0)
        assertEquals("filled" to (32 to 16), filled.scenario to (filled.width to filled.height))

        val blank = timer.measure("blank", 32, 16) { }
        assertEquals(0, blank.drawnPixels)
    }

    @Test
    fun `the content is told which frame it is drawing`() {
        val seen = mutableListOf<Int>()
        FrameTimer(warmupFrames = 2, warmupMillis = 0, measuredFrames = 3).measure("frames", 8, 8) { frame ->
            seen += frame
        }
        // Frame 0 is the composition the scene makes when it is built, before any render.
        assertEquals((1..5).toList(), seen.filter { it > 0 }.distinct())
    }
}
