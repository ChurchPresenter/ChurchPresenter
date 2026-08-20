plugins {
    alias(libs.plugins.kotlinJvm)
    `java-test-fixtures`
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

// The two counters a UDP protocol client cannot reach from a loopback fake. AtemClient's receive
// loop branches on every malformed packet a switcher could send — truncated headers, unknown
// command names, transfer error codes no captured device ever emitted — and FakeAtemSwitcher only
// replays what real hardware sent, by design (see its doc comment). What remains beyond that is
// the keepalive loop (a hard-coded 1.5s delay) and the retransmit path; both are named in
// AGENT.md under "What is not tested here, and why".
//
// Measured: BRANCH 81.3%, COMPLEXITY 79.5% — floors set just under, so a real regression trips
// them but ordinary noise does not. The other four clear the 85% default on their own:
// INSTRUCTION 91.5%, LINE 94.0%, METHOD 96.4%, CLASS 100%.
extra["coverageFloors"] = mapOf(
    "BRANCH" to "0.80",
    "COMPLEXITY" to "0.78",
)

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    // CrashReporter only. Not `api`: no ATEM signature mentions a Sentry or diagnostics type, so
    // the dependency stops at this module's own call sites.
    implementation(projects.diagnostics)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":atem")))

    testFixturesImplementation(libs.kotlinx.coroutines.core)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    // main + test only, as every other module of this build has it. NOT testFixtures: detekt's
    // default excludes cover test source sets by name (`**/jvmTest/**`, `**/test/**`, …) and
    // `testFixtures` is not among them, so scanning it would report 42 MagicNumbers against
    // FakeAtemSwitcher's captured byte layouts that the same file never produced while it lived in
    // `:composeApp`'s jvmTest — findings created by the directory it sits in, not by the code.
    source.setFrom("src/main/kotlin", "src/test/kotlin")
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}
