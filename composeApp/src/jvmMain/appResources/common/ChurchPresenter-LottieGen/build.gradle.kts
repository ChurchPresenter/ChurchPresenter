import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.10"
    id("org.jetbrains.compose") version "1.10.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
    jacoco
}

group = "org.churchpresenter"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    @Suppress("DEPRECATION")
    implementation(compose.material3)
    @Suppress("DEPRECATION")
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("io.github.alexzhirkevich:compottie:2.0.0-rc01")
    implementation("io.github.alexzhirkevich:compottie-dot:2.0.0-rc01")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
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

// Coverage for this module's own logic. UI composables and the CLI diagnostic entry points are
// excluded: the first need a real display, the second exist to print to stdout.
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
            exclude("**/ui/**", "**/MainKt*", "**/*Dump*", "**/MakeSampleDeck*")
        }
    )
}
