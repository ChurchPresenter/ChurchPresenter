plugins {
    id("cp.jvm-module")
    // CrashReporter resolves its file under `user.home`; the suite gets a home of its own under build/
    // so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    id("cp.compose")
    id("cp.screenshots")
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.diagnostics)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    // The fake OBS the client and the settings page are driven against.
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.websockets)
    testImplementation(libs.roborazzi.composeDesktop)
}

