plugins {
    id("cp.jvm-module")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // The loaded module the saved corrections are laid over.
    implementation(projects.bible)
    implementation(projects.diagnostics)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.bible))
}
