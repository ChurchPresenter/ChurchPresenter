plugins {
    id("cp.jvm-module")
    // CrashReportSweep is published from here because it exists for CrashReporter's own design:
    // the report directory is resolved once per JVM, so a test that reports an exception really
    // writes into the developer's own ~/.churchpresenter/crash-reports and has to put it back.
    // HungTestReporter is too: the root build puts these fixtures on every module's test runtime,
    // and their service file registers it, so a hung fork anywhere leaves a thread dump.
    `java-test-fixtures`
}

dependencies {
    // `api`, not `implementation`: CrashReporter.breadcrumb takes a SentryLevel, so the type is
    // part of this module's public surface and has to resolve at every call site in the app.
    api(libs.sentry)

    // The listener API HungTestReporter implements. Compile-only: every module that runs it is a
    // JUnit Platform suite and already has the launcher at run time.
    testFixturesCompileOnly(libs.junit.platform.launcher)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.platform.launcher)
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
    // Neither of the two above actually stops CrashReporter: they disable the SDK's own external
    // configuration, while initSentry reads sentry.properties off the classpath and hands the DSN
    // to Sentry.init directly. This is the switch initSentry itself honours, and it is what keeps
    // a suite that calls CrashReporter.initialize out of the production project.
    systemProperty("churchpresenter.telemetry.disabled", "true")
}

