plugins {
    id("cp.jvm-module")
    // The strings and icons come from :strings and :icons; the module has no resources of its own.
    id("cp.compose")
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // The loaded translation the editor reads, and the .spb format it never rewrites.
    implementation(projects.bible)
    // The change model and the history file, shared with the Bible tab that applies them.
    implementation(projects.bibleEdits)
    implementation(projects.theme)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.sharedUi)
    implementation(projects.diagnostics)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.bible))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.roborazzi.composeDesktop)
}
