plugins {
    id("cp.jvm-module")
    // The render cache keeps its frames under `user.home`; the suite gets a home of its own under
    // build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    // The bundled lottie fonts are plain classpath resources under src/main/resources/fonts, loaded by
    // LottieFonts (and by LottieGen's FontRegistry) -- not Compose resources.
    id("cp.compose")
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.settings)
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
    implementation(libs.compose.material3)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.atem))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
