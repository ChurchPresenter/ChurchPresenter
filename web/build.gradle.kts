plugins {
    id("cp.jvm-module")
    // CefManager reads and writes under `user.home` (the JCEF install dir); the suite gets a home of its
    // own under build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    id("cp.compose")
}

dependencies {
    // The port to the live output this tab declares against.
    api(projects.outputApi)
    // JCEF and the embedded web view the tab browses with; its API names them.
    api(projects.webEngine)
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)

    implementation(libs.kotlinx.coroutines.core)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.mockk)
}

