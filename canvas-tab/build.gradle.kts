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
    api(projects.coreModels)
    api(projects.resources)
    implementation(projects.uiComponents)
    implementation(projects.theme)
    implementation(projects.diagnostics)
    implementation(projects.shortcuts)
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.vlcj)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.roborazzi.composeDesktop)
    testImplementation(testFixtures(projects.uiComponents))
    testImplementation(testFixtures(projects.coreModels))
    // GraphicsDevice is abstract and the suite is headless, so the screen-geometry tests mock it.
    testImplementation(libs.mockk)
    // SharedBrowserFrameCache discovers pages over the DevTools HTTP endpoint, so its tests
    // stand up a real little server rather than mocking a protocol.
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.websockets)
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
