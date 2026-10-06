plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

// The strings and icons come from :strings and :icons; this module ships only the sample songs the
// System page installs.
compose.resources {
    packageOfResClass = "org.churchpresenter.settingspages.generated.resources"
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.diagnostics)
    implementation(projects.server)
    implementation(projects.profiles)
    implementation(projects.liveOutput)
    implementation(projects.bibleFormats)
    implementation(projects.media)
    implementation(projects.canvas)
    implementation(projects.songs)
    implementation(projects.lowerThird)
    implementation(projects.calendar)
    implementation(projects.companionSurface)
    implementation(projects.companionSatellite)
    implementation(projects.ndi)
    implementation(projects.omt)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // JUnit 4 on junit-vintage, as the app's suite was -- the server and storage tests use @get:Rule
    // TemporaryFolder and @BeforeClass, which a JUnit 5 run silently skips.
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.coreModels))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.ndi))
    testImplementation(testFixtures(projects.omt))
    testImplementation(testFixtures(projects.bible))
    testImplementation(testFixtures(projects.profiles))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.roborazzi.composeDesktop)
}

// Keeps `kotlin-test` on its JUnit 4 flavour: both flavours offer the same capability, and with
// useJUnitPlatform() the Kotlin plugin otherwise picks junit5 -- see the same block in
// composeApp/build.gradle.kts.
configurations.configureEach {
    resolutionStrategy.capabilitiesResolution.withCapability(
        "org.jetbrains.kotlin:kotlin-test-framework-impl"
    ) {
        val junit4 = candidates.firstOrNull {
            (it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)
                ?.module == "kotlin-test-junit"
        }
        if (junit4 != null) select(junit4)
    }
}

// The suite gets a home of its own under build/ so a test can never touch the real ~/.churchpresenter.
tasks.withType<Test>().configureEach {
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.mkdirs() }
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("java.awt.headless", "true")
}

// Committed, beside the module, as :composeApp's and the other tab modules' are.
roborazzi {
    outputDir.set(layout.projectDirectory.dir("screenshots"))
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
