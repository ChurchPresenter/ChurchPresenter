plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.detekt)
    // SpbFixture writes real .spb files. It lives here because this module owns that format, and
    // the app's Bible tab, view-model and server suites all build their fixtures with it.
    `java-test-fixtures`
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
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

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
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
