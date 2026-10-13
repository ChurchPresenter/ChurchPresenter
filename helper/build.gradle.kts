plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    id("cp.compose")
    // Committed, beside the module, as :composeApp's and :shared-ui's are.
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    // parseReference, for "show John 3:16" -- the light reference parser, not :bible-engine's.
    implementation(projects.calendarCore)
    implementation(projects.diagnostics)
    implementation(projects.settings)
    implementation(projects.theme)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
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
    testImplementation(libs.mockk)
    testImplementation(libs.roborazzi.composeDesktop)
}

// The understanding eval (WickUnderstandingEval) runs the model on every row, past the unit suite's
// one-second budget, so it is its own task: `test` leaves it out, `wickEval` runs only it.
tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("eval", "wickPack") }
}
tasks.register<Test>("wickEval") {
    description = "Measures how well Wick understands reworded requests"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("eval") }
    testLogging.showStandardStreams = true
}

// Regenerates Wick's catalog (src/main/resources/wick/catalog.tsv) from the codebase. WickCatalogTest
// fails on every other run until this has been run after a change it covers.
tasks.register<Test>("updateWickCatalog") {
    description = "Regenerates Wick's catalog from the codebase"
    group = "helper"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("*WickCatalogTest.the committed catalog*") }
    systemProperty("wick.catalog.update", "true")
    outputs.upToDateWhen { false }
    testLogging.showStandardStreams = true
}

// Builds wick-pack/pack.json, the update Wick downloads, from the hand-written wick-pack/source.json:
// checks every target and embeds every phrase with the same model updateWickCatalog uses.
tasks.register<Test>("buildWickPack") {
    description = "Builds Wick's downloadable pack from wick-pack/source.json"
    group = "helper"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("wickPack") }
    outputs.upToDateWhen { false }
    testLogging.showStandardStreams = true
}

