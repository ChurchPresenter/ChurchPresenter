plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
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
