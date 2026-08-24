import org.jetbrains.compose.resources.ResourcesExtension

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
    `java-test-fixtures`
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
    api(projects.resources)
    implementation(projects.uiComponents)
    implementation(projects.theme)
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    // The transcript arrives over socket.io; org.json rides along with it and is what the wire
    // format is parsed with -- see JsonExt.kt for why optString cannot be used directly.
    implementation(libs.socket.io.client)
    // STTManager's scope is Dispatchers.Main -- without a Main dispatcher on the classpath
    // every socket callback is silently dropped, which shows up as a suite of 5s timeouts.
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.roborazzi.composeDesktop)
    testImplementation(testFixtures(projects.uiComponents))
    // The Compose compiler plugin is applied to every source set, so the fixtures need the
    // runtime on their path even though SilentSttServer.kt is plain JVM code.
    testFixturesImplementation(compose.desktop.currentOs)
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
