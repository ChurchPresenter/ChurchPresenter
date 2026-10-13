plugins {
    id("cp.jvm-module")
    // CefManager reads and writes under `user.home` (the JCEF install dir); the suite gets a home of its
    // own under build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    id("cp.compose")
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.diagnostics)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // JCEF -- the embedded Chromium the tab and the output window both browse with. `api` because
    // the app hands a CefBrowser across WebOutput. The platform's Chromium binaries are the app's
    // runtime dependency, not this module's: they are only ever loaded by a packaged or running app.
    api("me.friwi:jcefmaven:143.0.14")

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

