plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.detekt)
    // CrashReportSweep is published from here because it exists for CrashReporter's own design:
    // the report directory is resolved once per JVM, so a test that reports an exception really
    // writes into the developer's own ~/.churchpresenter/crash-reports and has to put it back.
    `java-test-fixtures`
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // `api`, not `implementation`: CrashReporter.breadcrumb takes a SentryLevel, so the type is
    // part of this module's public surface and has to resolve at every call site in the app.
    api(libs.sentry)

    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    // CrashReporterTest DELETES ~/.churchpresenter/crash-reports, .install_id and .crash_count in
    // its @BeforeTest. In :composeApp that was safe because the jvmTest task redirects user.home;
    // without the same redirect here the suite deletes the developer's own crash reports and
    // install id on the first run. This is the whole of that protection — do not remove it.
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.mkdirs() }
    systemProperty("user.home", testHome.absolutePath)

    // Keep tests out of the production Sentry project, the same way :composeApp does. The real DSN
    // lives in composeApp's sentry.properties and is not on this module's classpath, so this is
    // belt and braces — but the SDK auto-initialises from any properties file it finds, and an
    // empty DSN leaves it permanently disabled.
    systemProperty("sentry.dsn", "")
    systemProperty("sentry.enable-external-configuration", "false")
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
