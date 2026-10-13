plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    // The strings and icons come from :strings and :icons. The bundled lottie fonts are plain classpath
    // resources under src/main/resources/fonts, loaded by LottieFonts (and by LottieGen's FontRegistry).
    id("cp.compose")
    id("cp.screenshots")
    `java-test-fixtures`
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.diagnostics)
    implementation(projects.atem)
    implementation(projects.lottieGenerator)
    implementation(projects.presentationEngine)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compottie)
    implementation(libs.compottie.dot)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // The tab-driving helpers, shared with :composeApp's lower-third screenshot suite.
    testFixturesImplementation(projects.theme)
    testFixturesImplementation(projects.settings)
    testFixturesImplementation(projects.sharedUi)
    testFixturesImplementation(testFixtures(projects.sharedUi))
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.material3)
    testFixturesImplementation(libs.compose.uiTest)
    testFixturesImplementation(projects.atem)
    testFixturesImplementation(testFixtures(projects.atem))
    testFixturesImplementation(projects.coreModels)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.atem))
    testImplementation(testFixtures(projects.coreModels))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.roborazzi.composeDesktop)
    // The screenshot suite draws the tab with the Profiles preview-output picker, as the app does.
    testImplementation(projects.profiles)
}

