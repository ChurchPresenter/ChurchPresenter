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
    // ScheduleItem.LowerThirdItem — what a saved lower third looks like in a service plan.
    api(projects.coreModels)
    // LowerThirdSequencer and LottieRenderCache drive playback; isLottieFile and
    // LottieFrameRenderer are the wire the Browser Source overlay renders through.
    api(projects.companionServer)
    // The switcher: querying the media pool, uploading a clip, and reading its frame rate.
    api(projects.atem)
    // SlideFontRegistry — the fonts a Lottie design names have to resolve somewhere.
    implementation(projects.presentationEngine)
    // A frame that fails to rasterise is reported, not swallowed.
    implementation(projects.diagnostics)
    implementation(projects.uiComponents)
    implementation(projects.theme)
    api(projects.resources)
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compottie)
    implementation(libs.compottie.dot)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.roborazzi.composeDesktop)
    testImplementation(testFixtures(projects.uiComponents))
    // LottieFontSpec has an `internal constructor`, so the compottie library is the only thing that
    // can build one — the two `font(spec)` tests have no other way in. Everything else in
    // LottieFontsTest calls the internal helpers directly.
    testImplementation(libs.mockk)
    // The ATEM upload tests drive a loopback fake switcher built from a capture of real hardware,
    // so the media-pool upload is exercised without a device.
    testImplementation(testFixtures(projects.atem))
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
