plugins {
    id("cp.jvm-module")
    id("cp.compose")
    id("cp.screenshots")
}

dependencies {
    // Colors come from the app theme; type, shapes, elevation and state alphas from :hosanna-theme.
    implementation(projects.theme)
    implementation(projects.hosannaTheme)
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
}

tasks.test {
    systemProperty("java.awt.headless", "true")
}
