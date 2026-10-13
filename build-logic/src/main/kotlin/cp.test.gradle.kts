// A module's own suite: JUnit Platform, the hung-test watchdog, and JaCoCo held to the six-counter
// floor. `:composeApp` (Kotlin Multiplatform) registers its own coverage tasks and does not apply
// this. See AGENT.md, "JaCoCo lives in build-logic".
plugins {
    jacoco
}

val defaultCoverageFloors = mapOf(
    "INSTRUCTION" to "0.85",
    "BRANCH" to "0.85",
    "LINE" to "0.85",
    "COMPLEXITY" to "0.85",
    "METHOD" to "0.85",
    "CLASS" to "0.85",
)

val isFilteredTestRun = gradle.startParameter.taskRequests.any { request ->
    request.args.any { it == "--tests" }
}

// A module's `extra["coverageExcludes"]` and `extra["coverageFloors"]` are read when the coverage
// tasks are configured, not now: the module sets them in its own script, after this has applied.
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
    // A watchdog on the harness, not a wait inside a test: nothing asserts on it, and every module
    // suite here runs in seconds to a couple of minutes. It exists so a hung suite ends as a failure
    // with the log intact rather than running until the CI step gives up -- the same reason
    // :composeApp has one, applied to the modules that were left unbounded when it was added.
    timeout.set(java.time.Duration.ofMinutes(10))
    // HungTestReporter (`:diagnostics` test fixtures, added below) halts a fork stuck on one test
    // with a thread dump, well inside that timeout. `-PhangThresholdMs=` tightens it; the dump lands
    // in test-results so CI uploads it with the results.
    providers.gradleProperty("hangThresholdMs").orNull?.let {
        systemProperty("churchpresenter.test.hangThresholdMs", it)
    }
    systemProperty(
        "churchpresenter.test.hangDumpDir",
        layout.buildDirectory.dir("test-results/" + name).get().asFile.absolutePath,
    )
}

// Registers HungTestReporter through the fixtures' service file. `:diagnostics` has its own fixtures
// on its test classpath already.
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
