import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    // Kept in step with the app (gradle/libs.versions.toml): this module's sources are also
    // compiled INTO composeApp via kotlin.srcDir, so a version skew means code that builds in one
    // build fails in the other — which is exactly how ui/App.kt came to be broken here while the
    // app compiled it fine.
    kotlin("jvm") version "2.3.10"
    id("org.jetbrains.compose") version "1.10.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
    jacoco
}

group = "org.churchpresenter"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("org.apache.pdfbox:pdfbox:2.0.33")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// Coverage for this module's own logic. The UI package is excluded: it is Compose desktop
// composables that need a real display, and the app's own report has the same carve-out.
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
            exclude("ui/**", "MainKt*")
        }
    )
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ChurchPresenter-Converter"
            packageVersion = "1.0.0"

            windows {
                menuGroup = "ChurchPresenter"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
        }
    }
}
