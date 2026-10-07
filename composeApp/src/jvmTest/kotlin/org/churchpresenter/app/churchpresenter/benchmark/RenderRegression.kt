package org.churchpresenter.app.churchpresenter.benchmark

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.util.Locale

/** How much slower than its baseline a row may get, as a fraction of it, before it regresses. */
const val DEFAULT_REGRESSION_MARGIN = 0.5

/** Added to every row's limit, so a sub-millisecond row is not failed by timer noise alone. */
const val DEFAULT_REGRESSION_SLACK_MS = 1.0

/** One scenario at one output size: what a baseline and a run are matched on. */
data class RowKey(val scenario: String, val width: Int, val height: Int) {
    override fun toString() = "$scenario ${width}x$height"
}

/**
 * A typical frame: render p50 plus readback p50. The median rather than p99, because a CI gate
 * compares two runs on shared hardware, and the tail is where a noisy neighbour shows.
 */
fun typicalFrameMs(result: ScenarioResult): Double = result.render.p50Ms + result.readback.p50Ms

/** A recorded run read back: where it was taken, and each row's typical frame in milliseconds. */
data class RenderBaseline(val machine: String, val typicalMs: Map<RowKey, Double>)

/** The most a row's typical frame may take before it counts as a regression. */
fun regressionLimitMs(baselineMs: Double, margin: Double, slackMs: Double): Double =
    baselineMs * (1 + margin) + slackMs

/** One row in both runs. */
data class RowComparison(val key: RowKey, val baselineMs: Double, val currentMs: Double, val limitMs: Double) {
    val regressed: Boolean get() = currentMs > limitMs
}

/** A run held against a baseline: every shared row, and the rows only one side has. */
data class RegressionReport(
    val compared: List<RowComparison>,
    /** In this run but not the baseline: a new scenario, which has nothing to be compared with yet. */
    val onlyInCurrent: List<RowKey>,
    /** In the baseline but not this run: a scenario removed or renamed since it was recorded. */
    val onlyInBaseline: List<RowKey>,
    val margin: Double,
    val slackMs: Double,
) {
    val regressions: List<RowComparison> get() = compared.filter { it.regressed }
}

/** Compares each row both runs have; rows only one side has are listed, never failed. */
fun compareWithBaseline(
    baseline: RenderBaseline,
    current: List<ScenarioResult>,
    margin: Double = DEFAULT_REGRESSION_MARGIN,
    slackMs: Double = DEFAULT_REGRESSION_SLACK_MS,
): RegressionReport {
    val now = current.associate { RowKey(it.scenario, it.width, it.height) to typicalFrameMs(it) }
    val compared = now.mapNotNull { (key, ms) ->
        baseline.typicalMs[key]?.let { RowComparison(key, it, ms, regressionLimitMs(it, margin, slackMs)) }
    }
    return RegressionReport(
        compared = compared,
        onlyInCurrent = now.keys.filter { it !in baseline.typicalMs },
        onlyInBaseline = baseline.typicalMs.keys.filter { it !in now },
        margin = margin,
        slackMs = slackMs,
    )
}

/**
 * Reads back what [toJson] wrote. Throws [IllegalArgumentException] naming [source] when the text
 * is not such a file, so a broken baseline fails the gate rather than passing it.
 */
fun parseBaseline(json: String, source: String = "baseline"): RenderBaseline {
    fun bad(why: String): Nothing =
        throw IllegalArgumentException("$source is not a render benchmark results.json: $why")
    val root = try {
        Json.parseToJsonElement(json) as? JsonObject ?: bad("the top level is not an object")
    } catch (e: SerializationException) {
        bad(e.message ?: "unreadable JSON")
    }
    val rows = root["results"] as? JsonArray ?: bad("no \"results\" array")
    val typical = rows.mapIndexed { i, row ->
        fun field(name: String) = (row as? JsonObject)?.get(name) ?: bad("result $i has no \"$name\"")
        fun p50(name: String) = ((field(name) as? JsonObject)?.get("p50") as? JsonPrimitive)?.doubleOrNull
            ?: bad("result $i has no $name.p50")
        fun int(name: String) = (field(name) as? JsonPrimitive)?.intOrNull ?: bad("result $i has no whole \"$name\"")
        val scenario = (field("scenario") as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: bad("result $i has no \"scenario\" name")
        RowKey(scenario, int("width"), int("height")) to p50("renderMs") + p50("readbackMs")
    }.toMap()
    return RenderBaseline(machine = describeRun(root["run"] as? JsonObject), typicalMs = typical)
}

private fun describeRun(run: JsonObject?): String {
    fun text(name: String) = (run?.get(name) as? JsonPrimitive)?.content
    return listOfNotNull(text("os"), text("arch"), text("cpus")?.let { "$it CPUs" }, text("jvm"))
        .joinToString(", ")
        .ifEmpty { "an unrecorded machine" }
}

/** [parseBaseline] on a file; a missing one fails with how to record it, never as a pass. */
fun readBaseline(file: File): RenderBaseline {
    require(file.isFile) {
        "No render baseline at ${file.path}. Record one on the machine this run is compared on: " +
            "./gradlew :composeApp:renderBenchmark -PrecordCiRenderBaseline (on CI: dispatch the " +
            "Render benchmark workflow with record_baseline, then commit its ci-render-baseline artifact " +
            "to composeApp/benchmarks/ci/)."
    }
    return parseBaseline(file.readText(), file.path)
}

private fun ms(value: Double) = String.format(Locale.ROOT, "%.2f", value)

private fun RowComparison.line() = "$key: ${ms(baselineMs)} → ${ms(currentMs)} ms (limit ${ms(limitMs)})"

/** Why the gate failed, one regressed row per line. */
fun RegressionReport.failureMessage(baselineName: String): String = buildString {
    appendLine("${regressions.size} render benchmark row(s) slower than $baselineName allows:")
    regressions.forEach { appendLine("  ${it.line()}") }
    append("Typical frame = render p50 + readback p50; limit = baseline × ${ms(1 + margin)} + ${ms(slackMs)} ms.")
}

/** The comparison as a Markdown section, for the end of the run's `results.md`. */
fun RegressionReport.comparisonMarkdown(baselineName: String, baseline: RenderBaseline): String = buildString {
    appendLine()
    appendLine("## Compared with $baselineName")
    appendLine()
    appendLine("Baseline recorded on ${baseline.machine}.")
    appendLine(
        "Typical frame = render p50 + readback p50. A row regresses past baseline × ${ms(1 + margin)} + " +
            "${ms(slackMs)} ms.",
    )
    appendLine()
    appendLine(
        if (regressions.isEmpty()) "No regressions in ${compared.size} rows." else "**${regressions.size} regressed.**",
    )
    appendLine()
    appendLine("| Scenario | Size | Baseline | Now | Limit | Change | |")
    appendLine("|---|---|---:|---:|---:|---:|---|")
    compared.forEach { c ->
        val change = if (c.baselineMs > 0) {
            String.format(Locale.ROOT, "%+.0f%%", (c.currentMs / c.baselineMs - 1) * 100)
        } else {
            ""
        }
        val flag = if (c.regressed) "regressed" else ""
        appendLine(
            "| ${c.key.scenario} | ${c.key.width}x${c.key.height} | ${ms(c.baselineMs)} | ${ms(c.currentMs)} | " +
                "${ms(c.limitMs)} | $change | $flag |",
        )
    }
    if (onlyInCurrent.isNotEmpty()) {
        appendLine()
        appendLine("Not in the baseline yet, so not compared (re-record it to cover them):")
        onlyInCurrent.forEach { appendLine("- $it") }
    }
    if (onlyInBaseline.isNotEmpty()) {
        appendLine()
        appendLine("In the baseline but not measured in this run:")
        onlyInBaseline.forEach { appendLine("- $it") }
    }
}
