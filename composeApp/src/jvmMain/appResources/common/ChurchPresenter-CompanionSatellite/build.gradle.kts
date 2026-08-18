plugins {
    kotlin("jvm") version "2.3.10"
    jacoco
}

group = "companionsatellite"
version = "1.0.0"

// Kept in sync with ChurchPresenter (gradle/libs.versions.toml) so the standalone build matches
// the version this source is compiled against when included directly into the main app.
val coroutinesVersion = "1.10.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
}
