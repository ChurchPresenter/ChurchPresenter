import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
}

// Detect current OS classifier for JavaFX native binaries
fun currentOsClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("mac") -> if (arch.contains("aarch64")) "mac-aarch64" else "mac"
        os.contains("win") -> "win"
        else -> "linux"
    }
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.sqlite.jdbc)
            implementation(libs.kotlinx.serialization.json)
            implementation("io.github.alexzhirkevich:compottie:2.0.0-rc01")
            implementation("io.github.alexzhirkevich:compottie-dot:2.0.0-rc01")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
            implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
            implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.10.1")
            // Apache PDFBox for PDF slide extraction
            implementation("org.apache.pdfbox:pdfbox:2.0.30")
            // Apache POI for PowerPoint slide extraction
            implementation("org.apache.poi:poi:5.2.5")
            implementation("org.apache.poi:poi-ooxml:5.2.5")
            implementation("org.apache.poi:poi-scratchpad:5.2.5")
            // JavaFX for video playback (platform-native binaries)
            val jfxClassifier = currentOsClassifier()
            implementation("org.openjfx:javafx-base:21:$jfxClassifier")
            implementation("org.openjfx:javafx-graphics:21:$jfxClassifier")
            implementation("org.openjfx:javafx-media:21:$jfxClassifier")
            implementation("org.openjfx:javafx-swing:21:$jfxClassifier")
            implementation("org.openjfx:javafx-controls:21:$jfxClassifier")
        }
    }
}


compose.desktop {
    application {
        mainClass = "org.churchpresenter.app.churchpresenter.MainKt"

        jvmArgs(
            "-Xms512m",
            "-Xmx1536m",
            "-XX:+UseG1GC",
            "-XX:G1NewSizePercent=20",
            "-XX:G1ReservePercent=20",
            "-XX:MaxGCPauseMillis=50",
            "-XX:+UseStringDeduplication",
            "-Dskiko.renderApi=OPENGL",         // use OpenGL on Windows for smoother rendering
            "-Dawt.useSystemAAFontSettings=on",
            "-Dswing.aatext=true"
        )

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "ChurchPresenter"
            packageVersion = "1.0.0"
            description = "Church Presenter - Presentation software for worship services"
            copyright = "© 2025 Church Presenter. All rights reserved."
            vendor = "Church Presenter"

            macOS {
                bundleID = "org.churchpresenter.app"
                // Uncomment when you have an icon file
                // iconFile.set(project.file("src/jvmMain/resources/icon.icns"))
            }

            windows {
                // Uncomment when you have an icon file
                // iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
                menuGroup = "ChurchPresenter"
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "A1B2C3D4-E5F6-4789-A012-3456789ABCDE"
            }

            linux {
                // Uncomment when you have an icon file
                // iconFile.set(project.file("src/jvmMain/resources/icon.png"))
            }
        }
    }
}

// Workaround: avoid Gradle incremental state tracking on Compose resource generation tasks
// These tasks sometimes fail snapshotting when build outputs are OneDrive placeholders. This
// marks them as untracked (preferred) or falls back to disabling up-to-date checking.

val problematicTasks = setOf(
    "generateResourceAccessorsForJvmMain",
    "generateComposeResClass",
    "generateExpectResourceCollectorsForCommonMain"
)

tasks.matching { it.name in problematicTasks }.configureEach {
    // Mark the task as untracked so Gradle does not snapshot inputs/outputs
    // Workaround for OneDrive placeholder snapshot errors
    doNotTrackState("Temporary workaround: OneDrive placeholder snapshot errors")
}
