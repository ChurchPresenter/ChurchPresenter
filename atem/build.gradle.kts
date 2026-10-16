plugins {
    id("cp.jvm-module")
    `java-test-fixtures`
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
    // main + test only, as every other module of this build has it. NOT testFixtures: detekt's
    // default excludes cover test source sets by name (`**/jvmTest/**`, `**/test/**`, …) and
    // `testFixtures` is not among them, so scanning it would report 42 MagicNumbers against
    // FakeAtemSwitcher's captured byte layouts that the same file never produced while it lived in
    // `:composeApp`'s jvmTest — findings created by the directory it sits in, not by the code.
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

