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
    // KeyboardShortcutSettings stores KeyChord, AppSettings stores SongTuning and the Companion
    // surface placement — all three live in :core-models, which also owns TimerModes, aliased by
    // Constants.
    implementation(projects.coreModels)
    // Not for composables — this module has no Compose compiler plugin and must not need one.
    // KeyChord's own signature speaks Compose's Key/KeyEvent, so the classes must resolve when a
    // settings class names it.
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
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
