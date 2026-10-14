plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    id("cp.compose")
    // Committed, beside the module, as :composeApp's and the other tab modules' are.
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
}

// Below the shared 85% on branches and complexity: the tab is editor UI, and what is left is Compose's
// per-value change checks on its handlers and the editor's pop-out text window. See AGENT.md.
extra["coverageFloors"] = mapOf(
    "BRANCH" to "0.83",
    "COMPLEXITY" to "0.81",
)

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

