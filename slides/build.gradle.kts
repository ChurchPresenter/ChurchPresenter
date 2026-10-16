plugins {
    id("cp.jvm-module")
    // Several of these classes read and write under `user.home` (recent files, hidden items); the suite
    // gets a home of its own under build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    id("cp.compose")
    // Committed, beside the module, as :composeApp's and :shared-ui's are.
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // The port to the live output this tab declares against.
    api(projects.outputApi)
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.diagnostics)
    api(projects.presentationEngine)
    // The pictures' and presentation's presenters and the picture decoder the tabs preview with.
    api(projects.slidesEngine)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    // tempDir() and solidImage(), shared with the presenters' suites.
    testImplementation(testFixtures(projects.slidesEngine))
    testImplementation(testFixtures(projects.coreModels))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.presentationEngine))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.pdfbox)
    testImplementation(libs.roborazzi.composeDesktop)
}

