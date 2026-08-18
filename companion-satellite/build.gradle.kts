plugins {
    alias(libs.plugins.kotlinJvm)
    jacoco
}

group = "companionsatellite"

extra["coverageFloors"] = mapOf(
    "BRANCH" to "0.75",
    "COMPLEXITY" to "0.70",
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

