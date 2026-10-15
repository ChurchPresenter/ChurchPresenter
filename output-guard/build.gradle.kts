plugins {
    id("cp.jvm-module")
}

// The output guard: a second process of the app, AWT only, that keeps a still of each output under
// its window and shows it if the app dies (docs/SHOW_CONTROL.md, Output isolation). Nothing here
// may load Compose, Skiko or VLC: the guard starts in a fraction of the app's time and memory.
dependencies {
    implementation(projects.diagnostics)

    testImplementation(kotlin("test"))
}
