plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    // The strings come from :strings. The sample songs the System page copies into a new library are
    // plain JVM resources (src/main/resources/song_samples), so no resource class is generated.
    id("cp.compose")
    // Committed, beside the module, as :composeApp's and the other modules' are.
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.coreModels)
    implementation(projects.diagnostics)
    implementation(projects.profiles)
    implementation(projects.server)
    implementation(projects.liveOutput)
    implementation(projects.mediaEngine)
    implementation(projects.songs)
    implementation(projects.bibleTab)
    implementation(projects.bibleFormats)
    // AutoStartManager reads whether this is a Store install.
    implementation(projects.updater)
    // AutoStartManager's Windows registry calls.
    implementation(libs.jna)
    implementation(libs.jna.platform)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.profiles))
    testImplementation(testFixtures(projects.bible))
    testImplementation(projects.diagnostics)
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.roborazzi.composeDesktop)
}

