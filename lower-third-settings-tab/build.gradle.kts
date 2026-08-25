import org.jetbrains.compose.resources.ResourcesExtension

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

compose.resources {
    generateResClass = ResourcesExtension.ResourceClassGeneration.Never
}

dependencies {
    api(projects.settings)
    // The preview pane renders the selected animation exactly as the output does -- same compottie
    // configuration, same bundled `LottieFonts` -- and reads the folder with the same `isLottieFile`
    // the tab and the server use. That is a real dependency on the lower third, not an accident of
    // layout, so it is named rather than routed around: `:lower-third-tab` re-exports `:settings`,
    // `:resources` and `:companion-server` as `api`, which is where `isLottieFile` comes from.
    implementation(projects.lowerThirdTab)
    implementation(projects.uiComponents)
    api(projects.resources)
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compottie)
    testImplementation(kotlin("test"))
    testImplementation(projects.theme)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.roborazzi.composeDesktop)
    testImplementation(testFixtures(projects.uiComponents))
    testImplementation(testFixtures(projects.lowerThirdTab))
}

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
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

roborazzi {
    outputDir.set(layout.projectDirectory.dir("screenshots"))
}
