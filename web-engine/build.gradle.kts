plugins {
    id("cp.jvm-module")
    // CefManager reads and writes under `user.home` (the JCEF install dir); the suite gets a home of its
    // own under build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    id("cp.compose")
    `java-test-fixtures`
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.settings)
    implementation(projects.diagnostics)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // JCEF -- the embedded Chromium the Web tab and the output window both browse with. `api` because
    // the app hands a CefBrowser across WebOutput. The platform's Chromium binaries are the app's
    // runtime dependency, not this module's: they are only ever loaded by a packaged or running app.
    api(libs.jcefmaven)

    implementation(compose.desktop.currentOs)
    // The off-screen page's Windows message pump (SentMessagePump) calls User32 directly.
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.compose.material3)

    // stubOffscreenPage(): a live page for the suites of the modules that draw one.
    testFixturesImplementation(libs.mockk)
    testFixturesImplementation(libs.compose.runtime)
    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

// JcefShutdownHook reads the JDK's shutdown-hook table, which the app opens with
// --add-opens=java.base/java.lang (composeApp's jvmArgs); the tests run it the same way.
tasks.test {
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
