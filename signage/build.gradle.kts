plugins {
    id("cp.jvm-module")
    id("cp.compose")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // The wire types and the hub of paired screens this tab shares with the companion server.
    api(projects.signageCore)
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.theme)
    implementation(projects.diagnostics)

    implementation(libs.kotlinx.coroutines.core)
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
}
