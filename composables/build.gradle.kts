plugins {
    id("cp.jvm-module")
    id("cp.compose")
    id("cp.screenshots")
}

dependencies {
    implementation(projects.theme)
    implementation(projects.icons)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.roborazzi.composeDesktop)
    testImplementation(testFixtures(projects.sharedUi))
    // Only for ThemeMode, which the shared harness's bothThemes labels its two renders with.
}

tasks.test {
    systemProperty("java.awt.headless", "true")
}
