package org.churchpresenter.app.churchpresenter.benchmark

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The CI render gate's comparison: reading a recorded run back, matching rows, and which count as
 * regressed. Pure arithmetic over made-up numbers -- nothing here is timed.
 */
class RenderRegressionTest {

    private val info = RunInfo("Linux 6.8", "amd64", 4, "OpenJDK 21", warmupFrames = 30, measuredFrames = 120)

    private fun result(name: String, w: Int, h: Int, renderP50: Double, readbackP50: Double) = ScenarioResult(
        scenario = name,
        width = w,
        height = h,
        render = FrameStats(renderP50, renderP50 * 2, renderP50 * 3, renderP50 * 4),
        readback = FrameStats(readbackP50, readbackP50 + 1, readbackP50 + 2, readbackP50 + 3),
        drawnPixels = 1,
    )

    private fun baselineOf(vararg results: ScenarioResult) = parseBaseline(toJson(info, results.toList()))

    @Test
    fun `a recorded run reads back as each row's typical frame`() {
        val recorded = listOf(
            result("song verse", 1920, 1080, renderP50 = 1.25, readbackP50 = 2.5),
            result("say \"hi\"", 3840, 2160, renderP50 = 4.0, readbackP50 = 5.5),
        )
        val baseline = parseBaseline(toJson(info, recorded))
        assertEquals(
            mapOf(RowKey("song verse", 1920, 1080) to 3.75, RowKey("say \"hi\"", 3840, 2160) to 9.5),
            baseline.typicalMs,
        )
        assertEquals("Linux 6.8, amd64, 4 CPUs, OpenJDK 21", baseline.machine)
        assertEquals(recorded.map(::typicalFrameMs), baseline.typicalMs.values.toList())
    }

    @Test
    fun `a row past baseline times the margin plus the slack regresses`() {
        val baseline = baselineOf(result("picture", 1920, 1080, renderP50 = 1.0, readbackP50 = 2.1))
        // 3.10 x 1.5 + 1.0 = 5.65
        val report = compareWithBaseline(baseline, listOf(result("picture", 1920, 1080, 3.0, 3.2)))
        val row = report.regressions.single()
        assertEquals(3.1, row.baselineMs, 1e-9)
        assertEquals(6.2, row.currentMs, 1e-9)
        assertEquals(5.65, row.limitMs, 1e-9)
        val message = report.failureMessage("ci/results.json")
        assertTrue("picture 1920x1080: 3.10 → 6.20 ms (limit 5.65)" in message, message)
        assertTrue("1 render benchmark row(s) slower than ci/results.json allows" in message, message)
    }

    @Test
    fun `a row slower but inside the margin passes`() {
        val baseline = baselineOf(result("canvas scene", 3840, 2160, renderP50 = 10.0, readbackP50 = 10.0))
        // limit 20 x 1.5 + 1 = 31; 30 is half again as slow and still inside it.
        val report = compareWithBaseline(baseline, listOf(result("canvas scene", 3840, 2160, 15.0, 15.0)))
        assertTrue(report.regressions.isEmpty())
        assertEquals(1, report.compared.size)
    }

    @Test
    fun `the slack keeps a tiny row from failing on noise alone`() {
        val baseline = baselineOf(result("captions", 1920, 1080, renderP50 = 0.1, readbackP50 = 0.1))
        // Three times slower, but 0.6 ms is under 0.2 x 1.5 + 1.0 = 1.3.
        val noisy = compareWithBaseline(baseline, listOf(result("captions", 1920, 1080, 0.3, 0.3)))
        assertTrue(noisy.regressions.isEmpty())
        val noSlack = compareWithBaseline(baseline, listOf(result("captions", 1920, 1080, 0.3, 0.3)), slackMs = 0.0)
        assertEquals(1, noSlack.regressions.size)
    }

    @Test
    fun `the margin and slack are the caller's to set`() {
        assertEquals(5.65, regressionLimitMs(3.1, DEFAULT_REGRESSION_MARGIN, DEFAULT_REGRESSION_SLACK_MS), 1e-9)
        assertEquals(4.0, regressionLimitMs(2.0, margin = 1.0, slackMs = 0.0), 1e-9)
        val baseline = baselineOf(result("question", 1920, 1080, renderP50 = 1.0, readbackP50 = 1.0))
        val tight = compareWithBaseline(baseline, listOf(result("question", 1920, 1080, 1.1, 1.0)), 0.0, 0.0)
        assertEquals(listOf(RowKey("question", 1920, 1080)), tight.regressions.map { it.key })
        assertEquals(0.0, tight.margin)
    }

    @Test
    fun `rows only one side has are listed, not failed`() {
        val baseline = baselineOf(
            result("song verse", 1920, 1080, 1.0, 1.0),
            result("retired", 1920, 1080, 1.0, 1.0),
        )
        val report = compareWithBaseline(
            baseline,
            listOf(result("song verse", 1920, 1080, 1.0, 1.0), result("brand new", 1920, 1080, 50.0, 50.0)),
        )
        assertTrue(report.regressions.isEmpty())
        assertEquals(listOf(RowKey("brand new", 1920, 1080)), report.onlyInCurrent)
        assertEquals(listOf(RowKey("retired", 1920, 1080)), report.onlyInBaseline)
        // Matched on size as well as name.
        val other = compareWithBaseline(baseline, listOf(result("song verse", 3840, 2160, 9.0, 9.0)))
        assertEquals(listOf(RowKey("song verse", 3840, 2160)), other.onlyInCurrent)
        assertTrue(other.compared.isEmpty())
    }

    @Test
    fun `the report section names the baseline, every row and the rows it could not compare`() {
        val baseline = baselineOf(result("picture", 1920, 1080, 1.0, 2.1), result("retired", 1920, 1080, 1.0, 1.0))
        val report = compareWithBaseline(
            baseline,
            listOf(result("picture", 1920, 1080, 3.0, 3.2), result("brand new", 3840, 2160, 1.0, 1.0)),
        )
        val md = report.comparisonMarkdown("composeApp/benchmarks/ci/results.json", baseline)
        assertTrue("## Compared with composeApp/benchmarks/ci/results.json" in md, md)
        assertTrue("Baseline recorded on Linux 6.8, amd64, 4 CPUs, OpenJDK 21." in md, md)
        assertTrue("| picture | 1920x1080 | 3.10 | 6.20 | 5.65 | +100% | regressed |" in md, md)
        assertTrue("**1 regressed.**" in md, md)
        assertTrue("Not in the baseline yet, so not compared" in md && "- brand new 3840x2160" in md, md)
        assertTrue("- retired 1920x1080" in md, md)

        val clean = compareWithBaseline(baseline, listOf(result("picture", 1920, 1080, 1.0, 2.1)))
        val cleanMd = clean.comparisonMarkdown("b.json", baseline)
        assertTrue("No regressions in 1 rows." in cleanMd, cleanMd)
        assertTrue("| picture | 1920x1080 | 3.10 | 3.10 | 5.65 | +0% |  |" in cleanMd, cleanMd)
    }

    @Test
    fun `a malformed baseline fails clearly instead of passing`() {
        fun reason(json: String) = assertFailsWith<IllegalArgumentException> { parseBaseline(json, "b.json") }.message!!
        assertTrue("b.json is not a render benchmark results.json" in reason("{ not json"))
        assertTrue("top level is not an object" in reason("[]"))
        assertTrue("no \"results\" array" in reason("{\"run\": {}}"))
        assertTrue("result 0 has no \"width\"" in reason("{\"results\": [{\"scenario\": \"x\"}]}"))
        assertTrue("result 0 has no renderMs.p50" in reason(row("\"renderMs\": {\"p95\": 1}")))
        assertTrue("result 0 has no \"scenario\" name" in reason("{\"results\": [{\"scenario\": 3}]}"))
        assertTrue("result 0 has no \"scenario\"" in reason("{\"results\": [7]}"))
        // A baseline with no run block still reads; it just cannot say where it came from.
        assertEquals("an unrecorded machine", parseBaseline(row("\"renderMs\": {\"p50\": 1}")).machine)
    }

    private fun row(render: String) =
        "{\"results\": [{\"scenario\": \"x\", \"width\": 1, \"height\": 1, $render, \"readbackMs\": {\"p50\": 1}}]}"

    @Test
    fun `a missing baseline file says how to record one`() {
        val dir = Files.createTempDirectory("render-baseline").toFile()
        try {
            val missing = dir.resolve("results.json")
            val error = assertFailsWith<IllegalArgumentException> { readBaseline(missing) }
            assertTrue("No render baseline at ${missing.path}" in error.message!!, error.message)
            assertTrue("-PrecordCiRenderBaseline" in error.message!!, error.message)

            missing.writeText(toJson(info, listOf(result("song verse", 1920, 1080, 1.0, 1.0))))
            assertEquals(2.0, readBaseline(missing).typicalMs.getValue(RowKey("song verse", 1920, 1080)))
        } finally {
            dir.deleteRecursively()
        }
    }
}
