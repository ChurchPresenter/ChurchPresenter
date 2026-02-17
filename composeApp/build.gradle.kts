import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}


compose.desktop {
    application {
        mainClass = "org.churchpresenter.app.churchpresenter.MainKt"

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
