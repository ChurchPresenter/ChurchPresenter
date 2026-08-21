plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
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

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin")
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}
