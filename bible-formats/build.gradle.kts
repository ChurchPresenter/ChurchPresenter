plugins {
    id("cp.jvm-module")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    // Beblia's catalogue is JSON, decoded with @Serializable DTOs.
    implementation(libs.kotlinx.serialization.json)

    // The catalogue sources fetch over HTTP and stream module downloads to disk.
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    // Constants only — the app data directory the installer writes modules into.
    implementation(projects.settings)
    // CrashReporter.reportWarning on a failed fetch or a malformed catalogue entry.
    implementation(projects.diagnostics)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.netty)
}

