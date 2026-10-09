plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.settings)
    implementation(projects.coreModels)
    implementation(projects.diagnostics)
    implementation(projects.profiles)
    // What a ping counts and the device report lists: chord charts, calendar and Song Library use,
    // conversions, and the cameras, DeckLink, VLC, NDI/OMT and Chromium the machine has.
    implementation(projects.songChords)
    implementation(projects.calendar)
    implementation(projects.converter)
    implementation(projects.songlibrary)
    implementation(projects.canvas)
    implementation(projects.media)
    implementation(projects.web)
    implementation(projects.liveOutput)
    implementation(projects.ndi)
    implementation(projects.omt)
    // GpuInfo's Windows display-adapter enumeration.
    implementation(libs.jna)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.diagnostics))
}

// The suite gets a home of its own under build/ so a test can never touch the real ~/.churchpresenter.
tasks.withType<Test>().configureEach {
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.mkdirs() }
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("java.awt.headless", "true")
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
