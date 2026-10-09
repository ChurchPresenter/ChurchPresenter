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

// The strings come from :strings. The sample songs the System page copies into a new library are
// plain JVM resources (src/main/resources/song_samples), so no resource class is generated.
compose.resources {
    generateResClass = never
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.coreModels)
    implementation(projects.diagnostics)
    implementation(projects.profiles)
    implementation(projects.server)
    implementation(projects.liveOutput)
    implementation(projects.media)
    implementation(projects.songs)
    implementation(projects.bibleTab)
    implementation(projects.bibleFormats)
    // AutoStartManager reads whether this is a Store install.
    implementation(projects.updater)
    // AutoStartManager's Windows registry calls.
    implementation(libs.jna)
    implementation(libs.jna.platform)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.profiles))
    testImplementation(testFixtures(projects.bible))
    testImplementation(projects.diagnostics)
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.roborazzi.composeDesktop)
}

// The suite gets a home of its own under build/ so a test can never touch the real ~/.churchpresenter.
tasks.withType<Test>().configureEach {
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.mkdirs() }
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("java.awt.headless", "true")
}

// Committed, beside the module, as :composeApp's and the other modules' are.
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
