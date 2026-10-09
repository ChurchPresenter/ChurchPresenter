plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.pitest) apply false
}

val gitHooksPath = ".githooks"
if (layout.projectDirectory.file(".git").asFile.exists()) {
    val current = providers.exec {
        commandLine("git", "config", "--get", "core.hooksPath")
        isIgnoreExitValue = true          // exits 1 when the key is simply unset
    }.standardOutput.asText.map { it.trim() }.getOrElse("")

    if (current != gitHooksPath) {
        providers.exec {
            commandLine("git", "config", "core.hooksPath", gitHooksPath)
            isIgnoreExitValue = true      // a missing/!working git must never fail the build
        }.result.get()
        logger.lifecycle("Configured git core.hooksPath = $gitHooksPath")
    }
}

subprojects {
    version = "1.0.0"
}

val isFilteredTestRun = gradle.startParameter.taskRequests.any { request ->
    request.args.any { it == "--tests" }
}

val defaultCoverageFloors = mapOf(
    "INSTRUCTION" to "0.85",
    "BRANCH" to "0.85",
    "LINE" to "0.85",
    "COMPLEXITY" to "0.85",
    "METHOD" to "0.85",
    "CLASS" to "0.85",
)

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        plugins.withId("jacoco") {
            @Suppress("UNCHECKED_CAST")
            fun excludes(): List<String> =
                (findProperty("coverageExcludes") as? List<String>) ?: listOf("**/ComposableSingletons*")

            @Suppress("UNCHECKED_CAST")
            fun floors(): Map<String, String> =
                defaultCoverageFloors + (findProperty("coverageFloors") as? Map<String, String>).orEmpty()

            fun coveredClasses() = fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
                exclude(excludes())
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                finalizedBy("jacocoTestReport")
                // A watchdog on the harness, not a wait inside a test: nothing asserts on it, and
                // every module suite here runs in seconds to a couple of minutes. It exists so a
                // hung suite ends as a failure with the log intact rather than running until the CI
                // step gives up -- the same reason :composeApp has one, applied to the modules that
                // were left unbounded when it was added.
                timeout.set(java.time.Duration.ofMinutes(10))
                // HungTestReporter (`:diagnostics` test fixtures, added below) halts a fork stuck on
                // one test with a thread dump, well inside that timeout. `-PhangThresholdMs=`
                // tightens it; the dump lands in test-results so CI uploads it with the results.
                providers.gradleProperty("hangThresholdMs").orNull?.let {
                    systemProperty("churchpresenter.test.hangThresholdMs", it)
                }
                systemProperty(
                    "churchpresenter.test.hangDumpDir",
                    layout.buildDirectory.dir("test-results/" + name).get().asFile.absolutePath,
                )
            }

            // Registers HungTestReporter through the fixtures' service file. `:diagnostics` has its
            // own fixtures on its test classpath already.
            if (path != ":diagnostics") {
                dependencies.add("testRuntimeOnly", dependencies.testFixtures(project(":diagnostics")))
            }

            tasks.withType<JacocoReport>().configureEach {
                dependsOn("test")
                reports { xml.required.set(true); html.required.set(true) }
                classDirectories.setFrom(coveredClasses())
                onlyIf { !isFilteredTestRun }
            }

            tasks.withType<JacocoCoverageVerification>().configureEach {
                dependsOn("test")
                executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
                sourceDirectories.setFrom(files("src/main/kotlin"))
                classDirectories.setFrom(coveredClasses())
                violationRules {
                    rule {
                        floors().forEach { (counterName, floor) ->
                            limit {
                                counter = counterName
                                value = "COVEREDRATIO"
                                minimum = floor.toBigDecimal()
                            }
                        }
                    }
                }
            }
        }
    }
}

// Mutation testing, configured once for the modules that apply `info.solidsoft.pitest` (the core
// logic: :song-chords, :live-show, :core-models, :schedule). `./gradlew :<module>:pitest` writes
// build/reports/pitest/; mutation-test.yml runs it weekly. Not a gate: no mutationThreshold.
subprojects {
    plugins.withId("info.solidsoft.pitest") {
        extensions.configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
            pitestVersion.set(libs.versions.pitest.get())
            junit5PluginVersion.set(libs.versions.pitestJunit5.get())
            targetClasses.set(listOf("org.churchpresenter.*"))
            excludedClasses.set(listOf("*ComposableSingletons*"))
            // Kotlin's generated null checks: mutating them only proves the compiler inserted them.
            avoidCallsTo.set(listOf("kotlin.jvm.internal.Intrinsics"))
            threads.set(Runtime.getRuntime().availableProcessors())
            outputFormats.set(listOf("HTML", "XML"))
            timestampedReports.set(false)
            jvmArgs.set(listOf("-Djava.awt.headless=true"))
        }
    }
}

// The configured floor of every module, read off the verification tasks themselves so the number
// reported is the number enforced. CSV: FLOOR,<module>,<counter>,<minimum>
tasks.register("coverageFloors") {
    group = "verification"
    description = "Prints each module's configured JaCoCo floors."
    doLast {
        allprojects.forEach { project ->
            project.tasks.withType(JacocoCoverageVerification::class.java).forEach { task ->
                task.violationRules.rules.forEach { rule ->
                    rule.limits.forEach { limit ->
                        println("FLOOR,${project.name},${limit.counter},${limit.minimum}")
                    }
                }
            }
        }
    }
}

// Every module's project dependencies, test fixtures included, one module per line:
// `MODULE <path> <dependency path>...`. CI's change detection (.github/ci/affected_modules.py) reads
// this to decide which suites a change can affect, so the graph is the build's own, not a copy.
tasks.register("moduleGraph") {
    group = "help"
    description = "Prints each module's project dependencies, for CI's change detection."
    val graph = subprojects.sortedBy { it.path }.map { project ->
        val dependencies = project.configurations
            .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
            .map { it.path }
            .filter { it != project.path }
            .toSortedSet()
        "MODULE ${project.path} ${dependencies.joinToString(" ")}".trimEnd()
    }
    doLast { graph.forEach(::println) }
}
