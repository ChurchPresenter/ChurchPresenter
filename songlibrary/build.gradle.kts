import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The song, the `.song` format and the library folder -- the same ones the app uses.
    implementation(projects.coreModels)

    implementation(compose.desktop.currentOs)
    // Compose artefacts come from the version catalogue rather than the `compose.*` accessors, so
    // this module and :composeApp resolve the SAME material3 and icon versions -- composeApp
    // depends on this module, and a second version line here would silently upgrade the app's.
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
}

// Same rules as the app's gate, on this module's own sources, and no baseline: what is analysed
// here has to come out clean. `ui/**` is Compose desktop that needs a display; the library it edits
// is the part with the logic, and that is analysed and covered.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin/songlibrary/ui/SongLibraryState.kt", "src/test/kotlin")
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

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// Coverage for the library layer. `ui/**` is excluded: it is Compose desktop composables that need
// a real display, the same carve-out :converter and the app's own report make.
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
            exclude("songlibrary/ui/**", "songlibrary/MainKt*", "ComposableSingletons*")
        }
    )
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
