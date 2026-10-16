plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    id("cp.compose")
    // Committed, beside the module, as :composeApp's and the other tab modules' are.
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // The renderer, the capture sources and DeckLink the editor previews with; its API names them.
    api(projects.canvasEngine)
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.ndi)
    implementation(projects.omt)
    implementation(projects.bible)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    // TestSingletons and the panel helpers, shared with the engine's suites.
    testImplementation(testFixtures(projects.canvasEngine))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.ndi))
    testImplementation(testFixtures(projects.omt))
    testImplementation(testFixtures(projects.bible))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.mockk)
    testImplementation(libs.roborazzi.composeDesktop)
}

