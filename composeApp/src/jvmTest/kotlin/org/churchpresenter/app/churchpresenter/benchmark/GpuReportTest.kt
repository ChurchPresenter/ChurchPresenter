package org.churchpresenter.app.churchpresenter.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How an on-screen run's frame gaps are judged and written down. */
class GpuReportTest {

    private val info = RunInfo("macOS 26", "aarch64", 8, "OpenJDK 21", warmupFrames = 60, measuredFrames = 4)

    private fun nanos(vararg ms: Double) = ms.map { (it * 1_000_000).toLong() }.toLongArray()

    @Test
    fun `a gap past one and a half refreshes is a dropped frame`() {
        val result = gpuResult("song", 1920, 1080, refreshHz = 60, nanos = nanos(16.6, 16.7, 25.1, 33.4))
        assertEquals(2, result.dropped, "25.1 ms and 33.4 ms are past 25 ms at 60 Hz")
        assertEquals(4, result.frames)
        assertEquals(33.4, result.interval.maxMs, 0.01)
    }

    @Test
    fun `the table marks a row that dropped frames, and the JSON carries the render API`() {
        val steady = gpuResult("song", 1920, 1080, 60, nanos(16.7, 16.7))
        val dropping = gpuResult("lower third", 3840, 2160, 60, nanos(16.7, 50.0))
        val markdown = gpuMarkdown(info, "METAL", listOf(steady, dropping))
        assertTrue("| song | 1920x1080 | 60 Hz | 16.70 | 16.70 | 16.70 | 0 of 2 |  |" in markdown, markdown)
        assertTrue("| lower third | 3840x2160 | 60 Hz | 16.70 | 50.00 | 50.00 | 1 of 2 | drops |" in markdown, markdown)
        val json = gpuJson(info, "METAL", listOf(steady))
        assertTrue("\"renderApi\": \"METAL\"" in json, json)
        assertTrue("\"dropped\": 0" in json, json)
    }
}
