plugins {
    id("cp.jvm-module")
    id("cp.pitest")
    `java-test-fixtures`
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // KeyChord is a keyboard binding, so it speaks Compose's Key/KeyEvent/KeyShortcut. No runtime,
    // no composables: this module has no Compose compiler plugin and must not need one.
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation(libs.kotest.property)
    testFixturesImplementation(libs.compose.ui)
}

// main + test only: this module's test fixtures are not scanned.
detekt {
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

