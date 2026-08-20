import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

// Coverage for the crossword data layer. The UI package is Compose desktop composables that need
// a real display, so it is excluded rather than counted as permanently uncovered.
extra["coverageExcludes"] = listOf("**/ui/**", "**/MainKt*", "**/ComposableSingletons*")

kotlin {
    jvmToolchain(21)
}

dependencies {
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
        mainClass = "org.churchpresenter.cross.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "churchpresenter-cross"
            packageVersion = "1.0.0"
            description = "ChurchPresenter Crossword Admin"
        }
    }
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
