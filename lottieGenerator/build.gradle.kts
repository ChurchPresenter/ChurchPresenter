import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
    jacoco
}

group = "org.churchpresenter"

extra["coverageExcludes"] = listOf("**/ui/**", "**/MainKt*", "**/ComposableSingletons*")

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.compottie)
    implementation(libs.compottie.dot)

    testImplementation(kotlin("test"))
}

tasks.test {
    systemProperty("java.awt.headless", "true")
}

compose.desktop {
    application {
        mainClass = "lottiegen.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ChurchPresenter-LottieGen"
            packageVersion = "1.0.0"

            windows {
                menuGroup = "ChurchPresenter"
                upgradeUuid = "b2c3d4e5-f6a7-8901-bcde-f23456789012"
            }
        }
    }
}
