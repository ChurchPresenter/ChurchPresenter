import org.jetbrains.compose.resources.ResourcesExtension

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
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
    // The bindings as saved, and the KeyChord they are saved as.
    api(projects.settings)
    api(projects.coreModels)
    // Every action name, category and key alias is a translated string.
    api(projects.resources)
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    // keyDown(): the KeyEvent builder the shortcut tests match against. It lives with the
    // KeyChord it produces events for, so there is one definition rather than one per module.
    testImplementation(testFixtures(projects.coreModels))
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
