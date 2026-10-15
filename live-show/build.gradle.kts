plugins {
    id("cp.jvm-module")
    id("cp.pitest")
}

dependencies {
    // The cues carry the shared models; the state is Compose snapshot state so the outputs can
    // read it in composition. No composables here, so no Compose compiler plugin.
    api(projects.coreModels)
    api(libs.compose.runtime)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation(libs.kotest.property)
}

