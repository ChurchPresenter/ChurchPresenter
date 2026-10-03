package org.churchpresenter.app.churchpresenter.benchmark

import java.util.Locale

/** The frame budget an output size has to render inside: one frame at 60 fps, or at 30 fps for 4K. */
fun frameBudgetMs(width: Int, height: Int): Double =
    if (width.toLong() * height > FULL_HD_PIXELS) BUDGET_4K_MS else BUDGET_1080P_MS

private const val FULL_HD_PIXELS = 1920L * 1080L
private const val BUDGET_1080P_MS = 16.7
private const val BUDGET_4K_MS = 33.3

/** The scenarios whose render and readback together run past their size's frame budget at p99. */
fun overBudget(results: List<ScenarioResult>): List<ScenarioResult> =
    results.filter { it.totalP99Ms > frameBudgetMs(it.width, it.height) }

/** Where and how a run was taken, so two sets of numbers are only compared like for like. */
data class RunInfo(
    val os: String,
    val arch: String,
    val cpus: Int,
    val jvm: String,
    val warmupFrames: Int,
    val measuredFrames: Int,
) {
    companion object {
        fun current(warmupFrames: Int, measuredFrames: Int) = RunInfo(
            os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
            arch = System.getProperty("os.arch"),
            cpus = Runtime.getRuntime().availableProcessors(),
            jvm = "${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}",
            warmupFrames = warmupFrames,
            measuredFrames = measuredFrames,
        )
    }
}

private fun ms(value: Double) = String.format(Locale.ROOT, "%.2f", value)

private fun String.json() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** The run as JSON: one object per scenario and size, every time in milliseconds. */
fun toJson(info: RunInfo, results: List<ScenarioResult>): String = buildString {
    appendLine("{")
    appendLine("  \"run\": {")
    appendLine("    \"os\": ${info.os.json()},")
    appendLine("    \"arch\": ${info.arch.json()},")
    appendLine("    \"cpus\": ${info.cpus},")
    appendLine("    \"jvm\": ${info.jvm.json()},")
    appendLine("    \"warmupFrames\": ${info.warmupFrames},")
    appendLine("    \"measuredFrames\": ${info.measuredFrames}")
    appendLine("  },")
    appendLine("  \"results\": [")
    results.forEachIndexed { i, r ->
        fun stats(s: FrameStats) =
            "{\"p50\": ${ms(s.p50Ms)}, \"p95\": ${ms(s.p95Ms)}, \"p99\": ${ms(s.p99Ms)}, \"max\": ${ms(s.maxMs)}}"
        append("    {\"scenario\": ${r.scenario.json()}, \"width\": ${r.width}, \"height\": ${r.height}, ")
        append("\"renderMs\": ${stats(r.render)}, \"readbackMs\": ${stats(r.readback)}, ")
        append("\"budgetMs\": ${ms(frameBudgetMs(r.width, r.height))}}")
        appendLine(if (i < results.lastIndex) "," else "")
    }
    appendLine("  ]")
    appendLine("}")
}

/** The run as a Markdown table, the over-budget rows marked, for a reviewer to read. */
fun toMarkdown(info: RunInfo, results: List<ScenarioResult>): String = buildString {
    appendLine("# Render benchmark")
    appendLine()
    appendLine("Off-screen render (the NDI, OMT and Browser Source path), CPU raster, density 1.")
    appendLine("${info.os}, ${info.arch}, ${info.cpus} CPUs, ${info.jvm}.")
    appendLine("${info.warmupFrames}+ warm-up frames, ${info.measuredFrames} measured. Times in ms.")
    appendLine()
    appendLine("| Scenario | Size | Render p50 | Render p99 | Readback p99 | Total p99 | Budget | |")
    appendLine("|---|---|---:|---:|---:|---:|---:|---|")
    results.forEach { r ->
        val budget = frameBudgetMs(r.width, r.height)
        val flag = if (r.totalP99Ms > budget) "over" else ""
        appendLine(
            "| ${r.scenario} | ${r.width}x${r.height} | ${ms(r.render.p50Ms)} | ${ms(r.render.p99Ms)} | " +
                "${ms(r.readback.p99Ms)} | ${ms(r.totalP99Ms)} | ${ms(budget)} | $flag |",
        )
    }
}
