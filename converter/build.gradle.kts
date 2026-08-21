import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

extra["coverageExcludes"] = listOf("ui/**", "MainKt*", "ComposableSingletons*")

extra["coverageFloors"] = mapOf(
    "BRANCH" to "0.80",
    "COMPLEXITY" to "0.75",
)

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdfbox)

    // Exactly one POI schema jar on the classpath — see :presentation-engine and :composeApp.
    implementation(libs.apache.poi.ooxml) {
        exclude(group = "org.apache.poi", module = "poi-ooxml-lite")
    }
    implementation(libs.apache.poi.ooxmlFull)

    testImplementation(kotlin("test"))
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin/converter", "src/test/kotlin")
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

// The converter also ships as a standalone desktop app (.github/workflows/converter-installers.yml
// packages it), separately from the copy the main app opens from its Help menu.
compose.desktop {
    application {
        mainClass = "org.churchpresenter.converter.MainKt"

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
