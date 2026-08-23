plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

// Every consumer reaches these through `org.churchpresenter.resources.generated.resources.Res` —
// ONE accessor for icons, strings, fonts and files alike, which is the reason they share a module.
// Split into two, a file using both `Res.drawable` and `Res.string` would see two classes called
// `Res` and need an import alias; 46 files do exactly that. `publicResClass` is what makes the
// accessor visible outside this module at all.
compose.resources {
    packageOfResClass = "org.churchpresenter.resources.generated.resources"
    publicResClass = true
}

// No detekt, no jacoco, no test source set, and that is deliberate: this module holds assets and
// the accessor generated from them, and no Kotlin of its own. There is nothing here for a linter
// to read or a test to exercise -- the root build only wires those in for a module that applies
// the `detekt` and `jacoco` plugins, so leaving them off is what keeps the gates honest rather
// than green-by-vacuum.
dependencies {
    implementation(compose.runtime)
    implementation(compose.components.resources)
}

// ── Crossword puzzle sync ─────────────────────────────────────────────────────
// Copies encrypted .xwp files from the :crossword module in beside the other assets, so they are
// bundled with the app. Edit the puzzles in that module's `encoded/` directory, then rebuild.
// It lives here rather than in :composeApp because this is the module that owns `files/` now.
val syncCrosswordFiles by tasks.registering(Copy::class) {
    from(rootProject.file("crossword/encoded"))
    include("*.xwp")
    into(layout.projectDirectory.file("src/main/composeResources/files/crossword"))
    doFirst {
        destinationDir.mkdirs()
    }
}
// Every task that reads `composeResources` has to wait for the copy above, or Gradle fails the
// build for an undeclared dependency on the directory it writes into. The compose plugin spreads
// that reading across several tasks (`copyNonXmlValueResourcesForMain`,
// `prepareComposeResourcesTaskForMain`, `generateResourceAccessorsForMain`, …), so match on the
// word rather than naming them — the set changes between plugin versions.
tasks.matching {
    it.name != syncCrosswordFiles.name && it.name.contains("Resources", ignoreCase = true)
}.configureEach {
    dependsOn(syncCrosswordFiles)
}
