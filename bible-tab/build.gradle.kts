plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    id("cp.compose")
    // Committed, beside the module, as :composeApp's, :shared-ui's, :slides', :media's and :songs' are.
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
    `java-test-fixtures`
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
    implementation(projects.bible)
    implementation(projects.diagnostics)
    implementation(projects.sttEngine)
    implementation(projects.bibleFormats)
    // BibleEngineClient: the speech engine it starts, and the WebSocket it reads the engine on.
    implementation(projects.bibleEngine)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.org.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // The tab-driving harness and its fixtures, shared with :composeApp's suites.
    testFixturesImplementation(projects.theme)
    testFixturesImplementation(projects.settings)
    testFixturesImplementation(projects.coreModels)
    testFixturesImplementation(projects.sharedUi)
    testFixturesImplementation(projects.bible)
    testFixturesImplementation(projects.sttEngine)
    testFixturesImplementation(testFixtures(projects.bible))
    testFixturesImplementation(testFixtures(projects.sharedUi))
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.material3)
    testFixturesImplementation(libs.compose.uiTest)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.bible))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.sttEngine))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.roborazzi.composeDesktop)
    // BibleEngineClientLinkTest stands up a real engine socket to talk to.
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.websockets)
}

