import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

// Read when the root build realizes the JaCoCo tasks, so both have to be set before anything else
// in this file -- see "JaCoCo lives in the root build" in AGENT.md. `songlibrary/ui/**` is Compose
// desktop that needs a display, the same carve-out :converter makes; `SongLibraryState` is the part
// with the decisions in it and is deliberately NOT under `ui/` so that this exclude can be honest.
extra["coverageExcludes"] = listOf("songlibrary/ui/**", "songlibrary/MainKt*", "**/ComposableSingletons*")

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The song, the `.song` format and the library folder -- the same ones the app uses.
    implementation(projects.coreModels)
    // The app's nine colour schemes and semantic roles. The window is opened inside the app's
    // AppThemeWrapper, so it follows the theme the operator chose rather than painting its own.
    implementation(projects.theme)

    implementation(libs.kotlinx.coroutines.core)

    implementation(compose.desktop.currentOs)
    // Compose artefacts come from the version catalogue rather than the `compose.*` accessors, so
    // this module and :composeApp resolve the SAME material3 and icon versions -- composeApp
    // depends on this module, and a second version line here would silently upgrade the app's.
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
}

// Same rules as the app's gate, on this module's own sources, and no baseline: what is analysed
// here has to come out clean. `ui/**` is Compose desktop that needs a display; the state it draws
// is the part with the logic, and that is analysed and covered.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin/songlibrary/SongLibraryState.kt", "src/test/kotlin")
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

compose.desktop {
    application {
        mainClass = "songlibrary.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ChurchPresenter-SongLibrary"
            packageVersion = "1.0.0"
        }
    }
}
