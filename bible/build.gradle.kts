plugins {
    id("cp.jvm-module")
    // SpbFixture writes real .spb files. It lives here because this module owns that format, and
    // the app's Bible tab, view-model and server suites all build their fixtures with it.
    `java-test-fixtures`
}

dependencies {
    // Bible reports a load failure it cannot recover from, so the reporter is a real dependency
    // rather than a callback the caller has to remember to wire.
    implementation(projects.diagnostics)

    testImplementation(kotlin("test"))
    // CrashReportSweep: these tests exercise paths that really do write a crash report, and the
    // reporter resolves its directory once per JVM, so each has to put the developer's own
    // ~/.churchpresenter/crash-reports back afterwards.
    testImplementation(testFixtures(projects.diagnostics))
}

// main + test only: this module's test fixtures are not scanned.
detekt {
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

