plugins {
    id("cp.jvm-module")
    // CrashReporter and UsageEvents resolve their files under `user.home`; the suite gets a home of its
    // own under build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    id("cp.compose")
    // Committed, beside the module, as :composeApp's and :shared-ui's are.
    id("cp.screenshots")
}

dependencies {
    // The Satellite protocol client stays free of any UI toolkit; this module is its Compose face.
    api(projects.companionSatellite)
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.diagnostics)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.coreModels))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.roborazzi.composeDesktop)
}

