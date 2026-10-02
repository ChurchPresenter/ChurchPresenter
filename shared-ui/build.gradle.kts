plugins {
    alias(libs.plugins.kotlinJvm)
    `java-test-fixtures`
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

// The strings and icons come from :strings and :icons; this module ships no resources of its own,
// so it has no Res class to generate.
compose.resources {
    generateResClass = never
}

dependencies {
    api(projects.strings)
    api(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.songChords)
    implementation(projects.bible)
    implementation(projects.diagnostics)

    // The file chooser: DBus for the Linux portal, FileKit for the native Windows and macOS
    // dialogs. FileKit's own native UNIX transport conflicts with the junixsocket one, so it is
    // excluded -- see the same note in composeApp/build.gradle.kts.
    implementation("com.github.hypfvieh:dbus-java-core:5.2.0")
    implementation("com.github.hypfvieh:dbus-java-transport-junixsocket:5.2.0")
    implementation("io.github.vinceglb:filekit-dialogs:${libs.versions.filekit.get()}") {
        exclude(group = "com.github.hypfvieh", module = "dbus-java-transport-native-unixsocket")
    }

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.coreModels))
    testImplementation(testFixtures(projects.bible))
    testImplementation(libs.mockk)

    // The screenshot harness both this module's and :composeApp's `…ScreenshotTest` suites shoot through.
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.material3)
    testFixturesImplementation(libs.compose.uiTest)
    testFixturesImplementation(libs.roborazzi.composeDesktop)
    testFixturesImplementation(projects.theme)
    testFixturesImplementation(projects.settings)
    testFixturesImplementation(kotlin("test"))
    testImplementation(libs.roborazzi.composeDesktop)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Several of these classes read and write under `user.home`; the suite gets a home of its own under
// build/ so a test can never touch the real ~/.churchpresenter.
tasks.withType<Test>().configureEach {
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.mkdirs() }
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("java.awt.headless", "true")
}

// Committed, beside the module, for the same reason :composeApp's are: a reviewer opens and approves
// them. The tests write paths relative to the module directory (`screenshots/...`).
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
