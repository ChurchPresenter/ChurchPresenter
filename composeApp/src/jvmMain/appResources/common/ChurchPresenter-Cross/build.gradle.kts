import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    jacoco
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.churchpresenter.cross.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "churchpresenter-cross"
            packageVersion = "1.0.0"
            description = "ChurchPresenter Crossword Admin"
        }
    }
}

// Coverage for the crossword data layer. The UI package is Compose desktop composables that need
// a real display, so it is excluded rather than counted as permanently uncovered.
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("jvmTest")
    executionData.setFrom(layout.buildDirectory.file("jacoco/jvmTest.exec"))
    sourceDirectories.setFrom(files("src/jvmMain/kotlin"))
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/jvm/main")) {
            exclude("**/ui/**", "**/MainKt*")
        }
    )
    reports { xml.required.set(true); html.required.set(true) }
}
