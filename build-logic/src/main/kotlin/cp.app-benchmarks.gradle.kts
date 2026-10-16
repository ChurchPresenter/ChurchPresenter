import org.churchpresenter.buildlogic.InjectedExec
import java.time.Duration

// :composeApp's measurements: the render, GPU, isolation and startup benchmarks, the UI monkey and
// the soak test. Each is its own task and none is part of `jvmTest` or `check`. Applied after the
// app's Test settings, so each task's own forks, heap and limits are the ones that stick.

// The render benchmark, the soak test and the UI monkey: each run by its own task
// (`renderBenchmark`, `soakTest`, `monkeyTest`) and kept out of `jvmTest`.
val renderBenchmarkClasses = "*.benchmark.RenderBenchmark"
val soakTestClasses = "*.benchmark.ServiceSoak"
val monkeyTestClasses = "*.benchmark.UiMonkey"

// The render benchmark, the soak test and the monkey have their own tasks and never run in the
// parallel pass.
tasks.named<Test>("jvmTest") {
    filter {
        excludeTestsMatching(renderBenchmarkClasses)
        excludeTestsMatching(soakTestClasses)
        excludeTestsMatching(monkeyTestClasses)
    }
}

// ── Render benchmark ──────────────────────────────────────────────────────────
// How long each content type takes on an off-screen output, at 1080p and 4K -- see RenderBenchmark.
//   ./gradlew :composeApp:renderBenchmark                         # report to build/reports/render-benchmark/
//   ./gradlew :composeApp:renderBenchmark -PrecordRenderBaseline  # also overwrite composeApp/benchmarks/
//   ./gradlew :composeApp:renderBenchmark -PenforceRenderBudget   # fail past one frame at p99
//   ./gradlew :composeApp:renderBenchmark -PrecordCiRenderBaseline  # write composeApp/benchmarks/ci/ instead
//   ./gradlew :composeApp:renderBenchmark -PcheckRenderRegression   # fail on a row slower than the CI baseline
// The two baselines are different machines and are never compared with each other: the one in
// benchmarks/ is the reference Mac, against the absolute budget; benchmarks/ci/ is recorded BY the
// hosted CI runner (~10x slower), and is what render-benchmark.yml holds main against every week.
// `-PcheckRenderRegression=<file>` names another baseline (relative to the repository root). A row
// regresses when its typical frame (render p50 + readback p50) passes baseline x (1 + margin) +
// slack: `-PrenderRegressionMargin` (default 0.5) and `-PrenderRegressionSlackMs` (default 1.0).
// A missing baseline fails the check -- it never passes for want of something to compare with.
// Alone in one JVM and never part of `jvmTest` or `check`: timings taken beside other forks measure
// the machine, not the code.

tasks.register<Test>("renderBenchmark") {
    group = "verification"
    description = "Times every content type on an off-screen output, at 1080p and 4K."
    val parallel = tasks.named<Test>("jvmTest").get()
    testClassesDirs = parallel.testClassesDirs
    classpath = parallel.classpath
    maxParallelForks = 1
    maxHeapSize = "2g"
    filter { includeTestsMatching(renderBenchmarkClasses) }
    extensions.getByType<JacocoTaskExtension>().isEnabled = false
    // A measurement, so never up to date and never cached.
    outputs.upToDateWhen { false }
    val reportDir = layout.buildDirectory.dir("reports/render-benchmark").get().asFile
    systemProperty("renderBenchmark.reportDir", reportDir.absolutePath)
    val recordReference = project.hasProperty("recordRenderBaseline")
    val recordCi = project.hasProperty("recordCiRenderBaseline")
    val baselineDir = layout.projectDirectory.dir(if (recordCi) "benchmarks/ci" else "benchmarks").asFile
    systemProperty("renderBenchmark.baselineDir", baselineDir.absolutePath)
    systemProperty("renderBenchmark.record", (recordReference || recordCi).toString())
    systemProperty("renderBenchmark.enforce", project.hasProperty("enforceRenderBudget").toString())
    providers.gradleProperty("renderBenchmarkFrames").orNull?.let { systemProperty("renderBenchmark.frames", it) }
    // `-PcheckRenderRegression` alone has the value "" (or "true" from a properties file): the default.
    val regressionBaselineName = providers.gradleProperty("checkRenderRegression").orNull
        ?.let { if (it.isBlank() || it == "true") "composeApp/benchmarks/ci/results.json" else it }
    val regressionBaseline = regressionBaselineName?.let { rootProject.layout.projectDirectory.file(it).asFile }
    regressionBaseline?.let { systemProperty("renderBenchmark.regressionBaseline", it.absolutePath) }
    // As given, so the report and the failure name the repository's file, not the runner's checkout path.
    regressionBaselineName?.let { systemProperty("renderBenchmark.regressionBaselineName", it) }
    providers.gradleProperty("renderRegressionMargin").orNull
        ?.let { systemProperty("renderBenchmark.regressionMargin", it) }
    providers.gradleProperty("renderRegressionSlackMs").orNull
        ?.let { systemProperty("renderBenchmark.regressionSlackMs", it) }
    doFirst {
        if (recordReference && recordCi) {
            throw GradleException(
                "-PrecordRenderBaseline and -PrecordCiRenderBaseline name different machines' baselines; pass one.",
            )
        }
        // Before minutes of measuring, not after them. RenderBenchmark checks again for a direct run.
        if (regressionBaseline != null && !regressionBaseline.isFile) {
            throw GradleException(
                "No render baseline at $regressionBaseline to check against. Record one on the machine " +
                    "this run is compared on with -PrecordCiRenderBaseline (on CI: dispatch render-benchmark.yml " +
                    "with record_baseline and commit its ci-render-baseline artifact to composeApp/benchmarks/ci/).",
            )
        }
    }
    doLast {
        val report = reportDir.resolve("results.md")
        if (report.exists()) logger.lifecycle(report.readText())
    }
}

// ── GPU output benchmark ──────────────────────────────────────────────────────
// How each content type runs on an on-screen output window, drawn on the GPU -- see GpuOutputBenchmark.
//   ./gradlew :composeApp:gpuBenchmark                      # report to build/reports/gpu-benchmark/
//   ./gradlew :composeApp:gpuBenchmark -PrecordGpuBaseline  # also overwrite composeApp/benchmarks/gpu/
//   ./gradlew :composeApp:gpuBenchmark -PgpuBenchmarkWatchdog  # with the UI watchdog pinging at its live budget
// Needs a display, so it runs on the reference Mac, never on CI. It opens one window per content type
// and size over the main screen. Its `main` lives with the render benchmark in jvmTest, so this runs
// jvmTest's classpath -- not headless, and with its own home so nothing reads the operator's settings.
tasks.register<JavaExec>("gpuBenchmark") {
    group = "verification"
    description = "Times every content type on an on-screen output window, drawn on the GPU."
    val parallel = tasks.named<Test>("jvmTest").get()
    classpath = parallel.classpath
    mainClass.set("org.churchpresenter.app.churchpresenter.benchmark.GpuOutputBenchmarkKt")
    maxHeapSize = "2g"
    outputs.upToDateWhen { false }
    val home = layout.buildDirectory.dir("gpu-benchmark-home").get().asFile
    val reportDir = layout.buildDirectory.dir("reports/gpu-benchmark").get().asFile
    systemProperty("user.home", home.absolutePath)
    systemProperty("gpuBenchmark.reportDir", reportDir.absolutePath)
    systemProperty("gpuBenchmark.baselineDir", layout.projectDirectory.dir("benchmarks/gpu").asFile.absolutePath)
    systemProperty("gpuBenchmark.record", project.hasProperty("recordGpuBaseline").toString())
    providers.gradleProperty("gpuBenchmarkFrames").orNull?.let { systemProperty("gpuBenchmark.frames", it) }
    systemProperty("gpuBenchmark.watchdog", project.hasProperty("gpuBenchmarkWatchdog").toString())
    doFirst { home.mkdirs() }
    doLast {
        val report = reportDir.resolve("results.md")
        if (report.exists()) logger.lifecycle(report.readText())
    }
}

// ── Output isolation benchmark ────────────────────────────────────────────────
// What a stalled operator UI costs an on-screen output: frame gaps at injected event-thread stalls of
// 0, 100, 500 and 2000 ms -- see docs/SHOW_CONTROL.md, Output isolation.
//   ./gradlew :composeApp:isolationBenchmark                # report to build/reports/isolation/
// Needs a display, so it runs on the reference Mac, never on CI; like gpuBenchmark it runs jvmTest's
// classpath under a home of its own.
tasks.register<JavaExec>("isolationBenchmark") {
    group = "verification"
    description = "Measures an output window's frame gaps while the UI thread is stalled on purpose."
    val parallel = tasks.named<Test>("jvmTest").get()
    classpath = parallel.classpath
    mainClass.set("org.churchpresenter.app.churchpresenter.benchmark.OutputIsolationBenchmarkKt")
    maxHeapSize = "2g"
    outputs.upToDateWhen { false }
    val home = layout.buildDirectory.dir("isolation-benchmark-home").get().asFile
    val reportDir = layout.buildDirectory.dir("reports/isolation").get().asFile
    systemProperty("user.home", home.absolutePath)
    systemProperty("isolationBenchmark.reportDir", reportDir.absolutePath)
    doFirst { home.mkdirs() }
    doLast {
        val report = reportDir.resolve("results.md")
        if (report.exists()) logger.lifecycle(report.readText())
    }
}

// ── Startup benchmark ─────────────────────────────────────────────────────────
// How long the app takes to start and what it sits at idle -- see StartupProbe and benchmarks/budgets.md.
//   ./gradlew :composeApp:startupBenchmark                  # 5 launches, report to build/reports/startup/
//   ./gradlew :composeApp:startupBenchmark -PcheckBudgets   # fail past a budget in benchmarks/budgets.md
//   -PstartupLaunches=N, -PstartupIdleSeconds=S (default 30) change the run.
// Needs a display, so it runs on the reference Mac. Each launch is the real app (the `run` task's
// classpath and JVM flags) under its own home, seeded by StartupSeed with the licence accepted and
// setup done, on its own single-instance port, so it neither reads the operator's settings nor
// meets a copy already running.
tasks.register("startupBenchmark") {
    group = "verification"
    description = "Launches the app several times and reports time to the first frame and idle memory."
    // What the launches run on -- the app's jar and its runtime classpath -- never the `run` task
    // itself, which would launch the app as the operator's own.
    dependsOn("jvmTestClasses", "jvmJar", configurations.named("jvmRuntimeClasspath"))
    outputs.upToDateWhen { false }
    val exec = objects.newInstance(InjectedExec::class.java).exec
    val launches = providers.gradleProperty("startupLaunches").orNull?.toIntOrNull() ?: 5
    val idleSeconds = providers.gradleProperty("startupIdleSeconds").orNull ?: "30"
    val check = project.hasProperty("checkBudgets")
    val home = layout.buildDirectory.dir("startup-home").get().asFile
    val reportDir = layout.buildDirectory.dir("reports/startup").get().asFile
    val budgets = layout.projectDirectory.file("benchmarks/budgets.md").asFile
    doLast {
        val run = tasks.named<JavaExec>("run").get()
        val testClasspath = tasks.named<Test>("jvmTest").get().classpath
        home.deleteRecursively()
        reportDir.deleteRecursively()
        home.mkdirs()
        reportDir.mkdirs()
        exec.javaexec {
            classpath = testClasspath
            mainClass.set("org.churchpresenter.app.churchpresenter.benchmark.StartupSeedKt")
            systemProperty("user.home", home.absolutePath)
            systemProperty("java.awt.headless", "true")
        }
        val results = (1..launches).map { i ->
            val out = reportDir.resolve("launch-$i.json")
            exec.javaexec {
                classpath = run.classpath
                mainClass.set(run.mainClass)
                jvmArgs(run.jvmArgs ?: emptyList<String>())
                run.javaLauncher.orNull?.let { executable = it.executablePath.asFile.absolutePath }
                systemProperty("user.home", home.absolutePath)
                systemProperty("churchpresenter.singleInstancePort", "47634")
                systemProperty("churchpresenter.startupProbe", out.absolutePath)
                systemProperty("churchpresenter.startupProbe.idleSeconds", idleSeconds)
            }
            fun field(name: String) = Regex("\"$name\": ([0-9.]+)").find(out.readText())?.groupValues?.get(1)?.toDouble()
            mapOf(
                "toMainMs" to field("toMainMs"),
                "toFirstFrameMs" to field("toFirstFrameMs"),
                "idleHeapMb" to field("idleHeapMb"),
                "idleRssMb" to field("idleRssMb"),
            )
        }
        // A launch that hit StartupProbe's deadline wrote no times: it never drew its window.
        val failed = results.count { it["toFirstFrameMs"] == null }
        // The first launch warms the OS file cache and is reported, not counted.
        val counted = (if (results.size > 1) results.drop(1) else results).filter { it["toFirstFrameMs"] != null }
        fun median(key: String) = counted.mapNotNull { it[key] }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        fun worst(key: String) = counted.mapNotNull { it[key] }.maxOrNull()
        val keys = listOf("toMainMs" to "To main()", "toFirstFrameMs" to "To the main window's first frame",
            "idleHeapMb" to "Idle heap, MB", "idleRssMb" to "Idle resident memory, MB")
        val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}, ${System.getProperty("os.arch")}"
        val report = buildString {
            appendLine("# Startup benchmark")
            appendLine()
            appendLine("$launches launches on $os; the first warms the file cache and is not counted. Idle is ${idleSeconds}s after the first frame.")
            appendLine()
            appendLine("| | Median | Worst |")
            appendLine("|---|---:|---:|")
            keys.forEach { (key, label) -> appendLine("| $label | ${median(key) ?: "n/a"} | ${worst(key) ?: "n/a"} |") }
            if (failed > 0) appendLine("\n$failed launch(es) never drew the main window before the deadline.")
        }
        reportDir.resolve("startup.md").writeText(report)
        logger.lifecycle(report)
        if (check) {
            // budgets.md rows read `| <key> | <limit> | ... |`; a median past its limit fails.
            val limits = budgets.readLines().mapNotNull { line ->
                Regex("^\\|\\s*`(\\w+)`\\s*\\|\\s*([0-9.]+)").find(line)?.let { it.groupValues[1] to it.groupValues[2].toDouble() }
            }.toMap()
            val over = keys.mapNotNull { (key, label) ->
                val limit = limits[key] ?: return@mapNotNull null
                val value = median(key) ?: return@mapNotNull null
                if (value > limit) "$label: $value, past its budget of $limit" else null
            }
            if (failed > 0) throw GradleException("$failed startup launch(es) never drew the main window.")
            if (over.isNotEmpty()) throw GradleException("Startup over budget: " + over.joinToString("; "))
        }
    }
}

// ── UI monkey ─────────────────────────────────────────────────────────────────
// Random presses, typing and keys on the main window from a seed; fails on the first exception the
// app throws -- see UiMonkey. The seed and every action go to build/reports/monkey/monkey.log.
//   ./gradlew :composeApp:monkeyTest                                  # the pinned seed, 300 steps
//   ./gradlew :composeApp:monkeyTest -PmonkeySeed=42 -PmonkeySteps=2000
// Run weekly on main (and by hand) by .github/workflows/monkey.yml; never part of `jvmTest` or `check`.
tasks.register<Test>("monkeyTest") {
    group = "verification"
    description = "Presses, types and keys at random on the main window from a seed, failing on any exception."
    val parallel = tasks.named<Test>("jvmTest").get()
    testClassesDirs = parallel.testClassesDirs
    classpath = parallel.classpath
    maxParallelForks = 1
    filter { includeTestsMatching(monkeyTestClasses) }
    extensions.getByType<JacocoTaskExtension>().isEnabled = false
    outputs.upToDateWhen { false }
    val reportDir = layout.buildDirectory.dir("reports/monkey").get().asFile
    systemProperty("monkey.reportDir", reportDir.absolutePath)
    listOf("monkeySeed" to "monkey.seed", "monkeySteps" to "monkey.steps").forEach { (gradle, system) ->
        providers.gradleProperty(gradle).orNull?.let { systemProperty(system, it) }
    }
    // The run's own limit is UiMonkey's monkeyTimeout -- a minute plus 200 ms a step. The hung-test
    // reporter is set a minute past it, so a run that is only long fails on that limit, and one that
    // really hangs still ends with its thread dump.
    val monkeySteps = providers.gradleProperty("monkeySteps").orNull?.toLongOrNull() ?: 300L
    systemProperty("churchpresenter.test.hangThresholdMs", (120_000L + monkeySteps * 200L).toString())
}

// ── Soak test ─────────────────────────────────────────────────────────────────
// A service run for hours on one off-screen output, failing on a leak or a stall -- see ServiceSoak.
//   ./gradlew :composeApp:soakTest                    # four hours, report to build/reports/soak/
//   ./gradlew :composeApp:soakTest -PsoakMinutes=10   # a short local run
// Run weekly on main (and by hand) by .github/workflows/soak.yml; never part of `jvmTest` or `check`.
tasks.register<Test>("soakTest") {
    group = "verification"
    description = "Runs a scripted service for hours on one off-screen output and fails on a leak or a stall."
    val parallel = tasks.named<Test>("jvmTest").get()
    testClassesDirs = parallel.testClassesDirs
    classpath = parallel.classpath
    maxParallelForks = 1
    maxHeapSize = "2g"
    filter { includeTestsMatching(soakTestClasses) }
    extensions.getByType<JacocoTaskExtension>().isEnabled = false
    outputs.upToDateWhen { false }
    // The hung-test reporter's five minutes are for unit tests. This one runs for hours by design, so
    // it is only called hung a quarter of an hour past the length of the run it was asked for --
    // before the task timeout below, so a hung run ends with its thread dump rather than without one.
    val soakMinutes = providers.gradleProperty("soakMinutes").orNull?.toDoubleOrNull() ?: 240.0
    systemProperty("churchpresenter.test.hangThresholdMs", ((soakMinutes + 15) * 60_000).toLong().toString())
    // The task itself gets half an hour past the run: the 30-minute cap every other test task
    // carries would end a four-hour run a quarter of the way in.
    timeout.set(Duration.ofMillis(((soakMinutes + 30) * 60_000).toLong()))
    val reportDir = layout.buildDirectory.dir("reports/soak").get().asFile
    systemProperty("soak.reportDir", reportDir.absolutePath)
    listOf("soakMinutes" to "soak.minutes", "soakFps" to "soak.fps", "soakCueSeconds" to "soak.cueSeconds",
        "soakSampleSeconds" to "soak.sampleSeconds", "soakStallSeconds" to "soak.stallSeconds").forEach { (gradle, system) ->
        providers.gradleProperty(gradle).orNull?.let { systemProperty(system, it) }
    }
    doLast {
        val report = reportDir.resolve("soak.md")
        if (report.exists()) logger.lifecycle(report.readText())
    }
}
