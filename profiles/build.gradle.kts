plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    id("cp.compose")
    // Committed, beside the module, as :composeApp's and the other tab modules' are.
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
    `java-test-fixtures`
}

// The strings and icons come from :strings and :icons. This module's own resources are the bundled
// stock backgrounds the local library offers.
compose.resources {
    packageOfResClass = "org.churchpresenter.profiles.generated.resources"
    publicResClass = false
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.presenter)
    implementation(projects.canvasEngine)
    implementation(projects.media)
    implementation(projects.mediaEngine)
    implementation(projects.slidesEngine)
    implementation(projects.bible)
    implementation(projects.bibleTab)
    implementation(projects.lottieGenerator)
    implementation(projects.atem)
    implementation(projects.dictionary)
    implementation(projects.qa)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compottie)
    implementation(libs.compottie.dot)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // The colour-field helpers the settings suites and :composeApp's dialog suites share.
    testFixturesImplementation(testFixtures(projects.sharedUi))
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.uiTest)
    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(projects.settings)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.coreModels))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.presenter))
    testImplementation(testFixtures(projects.bible))
    testImplementation(testFixtures(projects.atem))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.roborazzi.composeDesktop)
}

