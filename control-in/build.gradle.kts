plugins {
    id("cp.jvm-module")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // A mapping runs a list of show-control actions.
    api(projects.showControl)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

