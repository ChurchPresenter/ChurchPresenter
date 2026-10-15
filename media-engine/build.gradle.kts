plugins {
    id("cp.jvm-module")
    // VideoFirstFrame and the player's caches read and write under `user.home`; the suite gets a home
    // of its own under build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    id("cp.compose")
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.settings)
    implementation(projects.diagnostics)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // VLC does the decoding; the app finds the system's libvlc through JNA.
    api(libs.vlcj)
    implementation(libs.jna)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.coreModels))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
