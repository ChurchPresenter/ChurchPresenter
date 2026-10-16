plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    id("cp.compose")
    // Committed, beside the module, as :composeApp's and the other tab modules' are.
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // The port to the live outputs this module implements.
    implementation(projects.outputApi)
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.diagnostics)
    implementation(projects.presenter)
    implementation(projects.liveShow)
    implementation(projects.canvasEngine)
    implementation(projects.slidesEngine)
    implementation(projects.mediaEngine)
    implementation(projects.webEngine)
    implementation(projects.presentationEngine)
    implementation(projects.sttEngine)
    implementation(projects.lowerThirdEngine)
    implementation(projects.songChords)
    implementation(projects.lottieGenerator)
    implementation(projects.ndi)
    implementation(projects.omt)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compottie)
    implementation(libs.compottie.dot)
    implementation(libs.jna)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // AnnouncementsTimerControlTest drives the real tab view model against the manager.
    testImplementation(projects.announcements)
    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.coreModels))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.presenter))
    testImplementation(testFixtures(projects.presentationEngine))
    testImplementation(testFixtures(projects.ndi))
    testImplementation(testFixtures(projects.omt))
    // DeckLinkComposeOutputTest drives the output over a fake card.
    testImplementation(testFixtures(projects.canvasEngine))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.pdfbox)
    testImplementation(libs.roborazzi.composeDesktop)
}

