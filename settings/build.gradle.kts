plugins {
    id("cp.jvm-module")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // KeyboardShortcutSettings stores KeyChord, AppSettings stores SongTuning and the Companion
    // surface placement — all three live in :core-models, which also owns TimerModes, aliased by
    // Constants.
    implementation(projects.coreModels)
    api(projects.showControl)
    api(projects.controlIn)
    // Not for composables — this module has no Compose compiler plugin and must not need one.
    // KeyChord's own signature speaks Compose's Key/KeyEvent, so the classes must resolve when a
    // settings class names it.
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

