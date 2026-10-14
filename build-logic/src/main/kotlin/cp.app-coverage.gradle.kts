import java.io.File

// :composeApp's coverage: the report and the six-counter floor over the app's own classes, from
// the jvmTest and jvmTestSerial suites, and the link printed after it. The modules' floor is
// cp.test; this app is Kotlin Multiplatform, with no conventional test/main pair to attach to.

// ── Test coverage (JaCoCo) ────────────────────────────────────────────────────
// Run: ./gradlew :composeApp:jacocoTestReport
// HTML: composeApp/build/reports/jacoco/jacocoTestReport/html/index.html
//
// The jacoco plugin instruments every Test task automatically, so `jvmTest` needs no extra
// wiring — but the report itself does, because this is a Kotlin Multiplatform build: there is no
// conventional `test`/`main` pair for the plugin's default report to attach to. Hence an explicit
// task pointing at the jvm compilation's own output.
extensions.configure<JacocoPluginExtension> {
    toolVersion = "0.8.15" // 0.8.12+ is required for JDK 21 class files
}

// The classes the app's coverage is measured over -- by the report (and so the CI summary and the
// pull-request table, which read its XML) and by the floor alike, so the number printed is the number
// enforced. Restricted to this app's package root: every module is a real Gradle module now and is
// measured by its own build.
//
// The app-entry wiring at the end used to be excluded from the gate only, with the report kept
// all-inclusive. That made the report and the table show the floor breached while the gate passed --
// 4,918 lines of window/menu construction that only runs under a real display are 8.7% of the total.
// One list, one number.
fun coverableAppClasses() = fileTree(layout.buildDirectory.dir("classes/kotlin/jvm/main")) {
            include("org/churchpresenter/**")
            // Generated code -- no value in measuring, and it would inflate the denominator.
            exclude("**/BuildConfig*")
            // Compose emits synthetic ComposableSingletons holder classes per file.
            exclude("**/ComposableSingletons*")
            // Compose + AWT startup side-effect (delayed window activation) with no headless-testable
            // body; only its os gate is unit-tested (see MacWindowActivationTest).
            exclude("**/MacWindowActivationKt*")
            // Whole file is a DialogWindow-wrapped fun easter egg with an infinite
            // `while (true) { withFrameNanos { ... } }` confetti animation loop — can't be composed
            // headless and has no terminating state to assert on.
            exclude("**/KonamiEasterEggDialogKt*")
            // A hidden easter egg (unlocked via the same Konami-style arrow sequence, MainDesktop.kt)
            // rather than a documented feature in FEATURES.md — out of scope for the app's coverage
            // metrics regardless of how well tested it happens to be.
            exclude("**/CrosswordTabKt*")

            // Three further "generated code" exclusions have been proposed and are deliberately NOT
            // here, each measured against the report of 2026-08-08 (76.2% branch, 21,443/28,137):
            //
            //  - `**/*$$serializer*` and `**/*WhenMappings*` — JaCoCo already filters both. All 132
            //    serializer classes and 49 WhenMappings appear in the XML as EMPTY self-closing
            //    <class/> elements carrying zero instructions and zero branches. Adding the globs
            //    moves the number by 0.0 points; they would be config that looks load-bearing and
            //    is not.
            //  - DI-generated classes — there is no DI framework in this build. Zero matches for
            //    *_Factory*, *Hilt*, *Dagger*, *_Impl*.
            //  - the UI packages (`tabs/`, `dialogs/`, `composables/`, `ui/`, `presenter/`), on the
            //    theory that composables dilute branch coverage. They do not: those packages are at
            //    76.1% branch against 76.2% for the whole, so excluding all of them moves branch
            //    coverage from 76.2% to 76.4%. They are also hand-written, screenshot-tested UI, not
            //    generated code. The single largest drag is main.kt at 0.3% branch (711 of 28,137
            //    missed branches, 10.6% of the total), and the gate below already excludes it.

            // The app entry point: `main` itself, which opens real windows and binds real ports and
            // so only runs under a display. MainKt's ~200 synthetic lambda classes come along via
            // the `$` globs.
            //
            // Two files have left this list rather than sitting on it out of habit.
            // NavigationTopBar.kt is covered in full by NavigationTopBarTest. MainDesktop.kt is at
            // ~70% via MainDesktopComposeTest and the extracted MainDesktopLogic — excluding it
            // claimed a demonstrably testable file could not be tested, and kept ~960 covered lines
            // out of the enforced number. Counting it costs the gate about half a point against a
            // ~14 point margin. This list is only worth reading if every entry on it is still true.
            exclude("org/churchpresenter/app/churchpresenter/MainKt*")
            // The rest of main.kt, split out of it into its own files: the same window, menu and
            // dialog construction, which only runs under a display. Its decisions are pulled out
            // into CalendarLogic.kt and MainWindowLogic.kt, which are counted and tested. Temporary:
            // it comes off this list once display-driven tests cover these files.
            exclude("org/churchpresenter/app/churchpresenter/AppRootState.class")
            exclude("org/churchpresenter/app/churchpresenter/AppRootState$*")
            exclude("org/churchpresenter/app/churchpresenter/AppRootStateKt*")
            exclude("org/churchpresenter/app/churchpresenter/AppWiringKt*")
            exclude("org/churchpresenter/app/churchpresenter/CalendarAutomationKt*")
            exclude("org/churchpresenter/app/churchpresenter/CalendarManagerWindowKt*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowKt*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowChromeKt*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowDialogsKt*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowScope*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowState*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowWiringKt*")
            exclude("org/churchpresenter/app/churchpresenter/RemoteRequestWiringKt*")
            exclude("org/churchpresenter/app/churchpresenter/VirtualOutputsKt*")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates a coverage report for the app's own code from the jvmTest suite."
    dependsOn("jvmTest", "jvmTestSerial")
    // Point at the agent's .exec output explicitly. The `executionData(task)` overload resolves
    // to the task's own binary-results directory here, not the JacocoTaskExtension destination,
    // and fails with "Unable to read execution data file .../test-results/jvmTest/binary".
    // BOTH exec files: the loopback-UDP suites run in the separate serial task, and leaving their
    // data out would drop real coverage from the number and from the floor below.
    executionData.setFrom(
        layout.buildDirectory.file("jacoco/jvmTest.exec"),
        layout.buildDirectory.file("jacoco/jvmTestSerial.exec"),
    )

    classDirectories.setFrom(coverableAppClasses())
    sourceDirectories.setFrom(files("src/jvmMain/kotlin", "src/commonMain/kotlin"))

    onlyIf {
        gradle.startParameter.taskRequests.none { request -> request.args.any { it == "--tests" } }
    }

    reports {
        html.required.set(true)
        xml.required.set(true)  // for CI/coverage services
        csv.required.set(false)
    }

    // Wipe the HTML tree before writing it. JaCoCo only ever writes the files it is reporting on and
    // never removes the ones it isn't, so a page for a class that has since been excluded above --
    // or renamed, or deleted -- stays on disk indefinitely, unreachable from index.html but sitting
    // there at 0% for anyone who opens it directly or lands on it from a stale link or a search.
    // CrosswordTabKt was doing exactly that: excluded from the data, still browsable as a file.
    // The XML is a single file and is overwritten in place, so only the HTML tree needs this.
    val htmlReportDir = layout.buildDirectory.dir("reports/jacoco/jacocoTestReport/html")
    doFirst { delete(htmlReportDir) }

    finalizedBy("printCoverageLink")
}

// Coverage floors for this MVVM app, on all six JaCoCo counters (see violationRules below).
//
// This started as a single 75% LINE floor, on the reasoning that the View layer
// (tabs/dialogs/composables) is a large share of the lines but low-logic, so 75% was the honest
// ceiling. That reasoning is now out of date in one specific way: it was measured against the
// REPORT scope, which includes main.kt and the rest of the permanently-uncoverable app entry. The
// gated scope below excludes those and stands at 90.2% line, so an 85% line floor is a floor, not a
// stretch. What the original note got right still holds for BRANCH, which is the counter the UI
// layer genuinely does cap -- see the table in violationRules.
//
// Wired into `check` (see the bottom of this file) as of 2026-07-30, when the gated scope first
// cleared the floor. Run it on its own with:
//   ./gradlew :composeApp:jacocoTestCoverageVerification
// Same execution, class and source wiring as jacocoTestReport -- see coverableAppClasses() for why
// the app-entry wiring is outside both.
tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    group = "verification"
    description = "Fails the build if coverage of the coverable code is below the line/branch targets."
    dependsOn("jvmTest", "jvmTestSerial")
    // BOTH exec files: the loopback-UDP suites run in the separate serial task, and leaving their
    // data out would drop real coverage from the number and from the floor below.
    executionData.setFrom(
        layout.buildDirectory.file("jacoco/jvmTest.exec"),
        layout.buildDirectory.file("jacoco/jvmTestSerial.exec"),
    )
    classDirectories.setFrom(coverableAppClasses())
    sourceDirectories.setFrom(files("src/jvmMain/kotlin", "src/commonMain/kotlin"))
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.81".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "COMPLEXITY"
                value = "COVEREDRATIO"
                minimum = "0.78".toBigDecimal()
            }
            limit {
                counter = "METHOD"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "CLASS"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

// Prints the headline number and a clickable file:// link so the report doesn't have to be hunted
// for under build/. Deliberately a SEPARATE task rather than a doLast on jacocoTestReport: a
// doLast is skipped when the report is UP-TO-DATE, so re-running `check` would silently print
// nothing. This task declares no outputs, so it never goes up-to-date and always prints.
tasks.register("printCoverageLink") {
    val reportDir = layout.buildDirectory.dir("reports/jacoco/jacocoTestReport")
    val testResultsDir = layout.buildDirectory.dir("test-results/jvmTest")
    // Where to append the run's summary block, resolved at CONFIGURATION time from a property the
    // CI client passes in -- not from this process's environment. `System.getenv` here reads the
    // Gradle DAEMON's environment, which is frozen when the daemon starts, and GITHUB_STEP_SUMMARY
    // is a fresh temp file PER STEP that the runner reads and discards as soon as that step ends.
    // In practice the daemon is started by the Detekt step and still serving 26 minutes later when
    // this task runs, so the block was being appended to a file belonging to a step that finished
    // long before -- written successfully, read by nobody. Observed on run 31944001329: daemon up
    // at 11:19:45 (Detekt), printCoverageLink at 11:45:25, same daemon.
    //
    // A `-P` property travels with each individual invocation, so it is the live path every time.
    // The environment stays as the fallback for anyone running the task by hand.
    val stepSummaryPath = providers.gradleProperty("stepSummary")
        .orElse(providers.environmentVariable("GITHUB_STEP_SUMMARY"))
        .orNull
    doLast {
        val dir = reportDir.get().asFile
        val htmlIndex = dir.resolve("html/index.html")
        if (!htmlIndex.exists()) return@doLast

        // Each per-suite TEST-*.xml carries its own totals on the root <testsuite> tag; sum them
        // for the whole-run figure rather than reading jvmTest's own summary (Gradle doesn't write
        // one -- only the per-suite files land under test-results).
        val testSummary = runCatching {
            val suiteAttrs = Regex("""tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"""")
            val files = testResultsDir.get().asFile.listFiles { f -> f.name.endsWith(".xml") }
            if (files.isNullOrEmpty()) return@runCatching null
            var tests = 0; var skipped = 0; var failures = 0; var errors = 0
            files.forEach { file ->
                val match = suiteAttrs.find(file.readText().lineSequence().take(2).joinToString("\n"))
                    ?: return@forEach
                tests += match.groupValues[1].toInt()
                skipped += match.groupValues[2].toInt()
                failures += match.groupValues[3].toInt()
                errors += match.groupValues[4].toInt()
            }
            "$tests run, ${failures + errors} failed, $skipped skipped"
        }.getOrNull()

        // Regex rather than a DOM parse: the JaCoCo XML declares an external DTD, which a
        // DocumentBuilder tries to resolve over the network. The report-wide totals are the LAST
        // <counter> of each type in the document (they appear per-package/-class first, then once
        // more on the closing </report> element), so the final match per type is the overall figure.
        val lines = runCatching {
            val xml = dir.resolve("jacocoTestReport.xml")
            if (!xml.exists()) return@runCatching null
            val text = xml.readText()
            // Order matches the JaCoCo HTML overview table's own column order.
            val labels = listOf(
                "INSTRUCTION" to "instructions",
                "BRANCH" to "branches",
                "LINE" to "lines",
                "COMPLEXITY" to "complexity",
                "METHOD" to "methods",
                "CLASS" to "classes",
            )
            labels.mapNotNull { (type, label) ->
                val last = Regex("""<counter type="$type" missed="(\d+)" covered="(\d+)"/>""")
                    .findAll(text).lastOrNull() ?: return@mapNotNull null
                val missed = last.groupValues[1].toInt()
                val covered = last.groupValues[2].toInt()
                val total = covered + missed
                if (total == 0) null
                else "%.1f%% of %s (%d/%d)".format(100.0 * covered / total, label, covered, total)
            }
        }.getOrNull()

        val summaryLines = buildList {
            if (testSummary != null) add("Tests:    $testSummary")
            if (lines != null) {
                add("Coverage:")
                lines.forEach { add("  $it") }
            }
        }

        logger.lifecycle("")
        summaryLines.forEach { logger.lifecycle(it) }
        // The HTML tree only exists on the machine that produced it. On a CI runner it is deleted
        // with the workspace when the job ends, so the line is noise there -- a `file://` path
        // pointing at a directory the reader has no way to open. Printed locally, skipped in CI.
        //
        // Three slashes: File.toURI() yields "file:/path", which many terminals refuse to linkify.
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            logger.lifecycle("Report:   file://${htmlIndex.absolutePath}")
        }

        // Also put it on the run's summary page. The job log is ~14 minutes of Gradle output and
        // the numbers land in the middle of it; the step summary is the page a reviewer actually
        // opens from a pull request. Best-effort -- a coverage print must never fail a build.
        stepSummaryPath?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching {
                File(path).appendText(
                    buildString {
                        appendLine("### Unit test coverage")
                        appendLine()
                        appendLine("```")
                        summaryLines.forEach { appendLine(it) }
                        appendLine("```")
                        appendLine()
                    }
                )
            }
        }
    }
}

// `check` runs the tests anyway, so generating the coverage report from the same run is nearly
// free -- and it makes the link appear at the end of the standard pre-commit command. The gate runs
// off the same execution data, so enforcing the floor here costs nothing beyond the check itself.
tasks.named("check") {
    dependsOn("jacocoTestReport")
    dependsOn("jacocoTestCoverageVerification")
}

