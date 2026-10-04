plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
    `java-test-fixtures`
    jacoco
}

group = "org.churchpresenter"

// Below the shared 85% on two counters only: what is left is the dialogs that open real windows
// (the stock and library browsers, the Lottie generator) and Compose's per-value change checks.
// See AGENT.md.
extra["coverageFloors"] = mapOf(
    "BRANCH" to "0.79",
    "COMPLEXITY" to "0.78",
)

kotlin {
    jvmToolchain(21)
}

// The strings and icons come from :strings and :icons. This module's own resources are the bundled
// stock backgrounds the local library offers.
compose.resources {
    packageOfResClass = "org.churchpresenter.profiles.generated.resources"
    publicResClass = false
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.diagnostics)
    implementation(projects.presenter)
    implementation(projects.canvas)
    implementation(projects.media)
    implementation(projects.slides)
    implementation(projects.bible)
    implementation(projects.bibleTab)
    implementation(projects.lottieGenerator)
    implementation(projects.atem)
    implementation(projects.stt)
    implementation(projects.dictionary)
    implementation(projects.qa)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compottie)
    implementation(libs.compottie.dot)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // The colour-field helpers the settings suites and :composeApp's dialog suites share.
    testFixturesImplementation(testFixtures(projects.sharedUi))
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.uiTest)
    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(projects.settings)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.coreModels))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.presenter))
    testImplementation(testFixtures(projects.bible))
    testImplementation(testFixtures(projects.atem))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.roborazzi.composeDesktop)
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
    source.setFrom("src/main/kotlin", "src/test/kotlin", "src/testFixtures/kotlin")
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
