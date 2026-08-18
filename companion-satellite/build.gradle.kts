plugins {
    alias(libs.plugins.kotlinJvm)
    jacoco
}

group = "companionsatellite"
version = "1.0.0"

// A module of this build rather than a standalone one, so the Kotlin and coroutines versions come
// from gradle/libs.versions.toml. They used to be a literal `kotlin("jvm") version "2.3.10"` and a
// `val coroutinesVersion = "1.10.2"` carrying a comment asking whoever bumped the catalog to
// remember this file too. Nobody has to remember anything now.
dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
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
