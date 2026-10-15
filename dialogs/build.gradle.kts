plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    id("cp.compose")
    id("cp.screenshots")
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.telemetry)
    implementation(projects.liveShow)
    implementation(projects.liveOutput)
    implementation(projects.controlIn)
    implementation(projects.schedule)
    implementation(projects.helper)
    implementation(projects.server)
    implementation(projects.calendar)
    implementation(projects.profiles)
    implementation(projects.songs)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    // The color-field, number-field and key-event helpers the dialogs' suites drive them with.
    testImplementation(testFixtures(projects.profiles))
    testImplementation(testFixtures(projects.coreModels))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.roborazzi.composeDesktop)
}

