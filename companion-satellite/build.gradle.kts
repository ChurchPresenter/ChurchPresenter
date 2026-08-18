plugins {
    alias(libs.plugins.kotlinJvm)
    jacoco
}

group = "companionsatellite"

extra["coverageFloors"] = mapOf(
    "INSTRUCTION" to "0.90",
    "BRANCH" to "0.75",
    "LINE" to "0.90",
    "COMPLEXITY" to "0.70",
    "METHOD" to "0.90",
    "CLASS" to "1.00",
)

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

