plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    id("cp.compose")
    // Committed, beside the module, as :composeApp's and the other modules' are.
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(projects.server)
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.calendar)
    implementation(projects.profiles)
    implementation(projects.lowerThirdEngine)
    // The connection and enrolment QR codes.
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.profiles))
    testImplementation(projects.diagnostics)
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.roborazzi.composeDesktop)
}

