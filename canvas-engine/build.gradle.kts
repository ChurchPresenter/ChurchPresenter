plugins {
    id("cp.jvm-module")
    // DeckLinkManager and the capture caches read and write under `user.home`; the suite gets a home
    // of its own under build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    id("cp.compose")
    alias(libs.plugins.kotlinx.serialization)
    `java-test-fixtures`
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.theme)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.diagnostics)
    implementation(projects.ndi)
    implementation(projects.omt)
    implementation(projects.mediaEngine)
    implementation(projects.slidesEngine)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)

    // Cameras through VLC and the platform APIs, and the QR code source.
    implementation(libs.vlcj)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)

    // TestSingletons and the panel helpers, shared with :canvas's suites.
    testFixturesApi(projects.coreModels)
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.uiTest)
    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(projects.sharedUi)
    testFixturesImplementation(testFixtures(projects.sharedUi))

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.ndi))
    testImplementation(testFixtures(projects.omt))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    // The browser source's page-discovery tests stand up a local DevTools endpoint.
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.websockets)
}
