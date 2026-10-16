plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.settings)
    implementation(projects.coreModels)
    implementation(projects.diagnostics)
    implementation(projects.profiles)
    // What a ping counts and the device report lists: chord charts, calendar and Song Library use,
    // conversions, and the cameras, DeckLink, VLC, NDI/OMT and Chromium the machine has.
    implementation(projects.songChords)
    implementation(projects.calendar)
    implementation(projects.converter)
    implementation(projects.songlibrary)
    implementation(projects.canvasEngine)
    implementation(projects.mediaEngine)
    implementation(projects.webEngine)
    implementation(projects.liveOutput)
    implementation(projects.ndi)
    implementation(projects.omt)
    // GpuInfo's Windows display-adapter enumeration.
    implementation(libs.jna)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.diagnostics))
}

