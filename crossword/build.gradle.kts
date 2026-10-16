import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("cp.jvm-module")
    id("cp.compose")
}

// Coverage for the crossword data layer. The UI package is Compose desktop composables that need
// a real display, so it is excluded rather than counted as permanently uncovered.
extra["coverageExcludes"] = listOf("**/ui/**", "**/MainKt*", "**/ComposableSingletons*")

dependencies {
    implementation(projects.theme)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
}

tasks.test {
    systemProperty("java.awt.headless", "true")
}

compose.desktop {
    application {
        mainClass = "org.churchpresenter.crossword.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "churchpresenter-cross"
            packageVersion = "1.0.0"
            description = "ChurchPresenter Crossword Admin"
        }
    }
}

