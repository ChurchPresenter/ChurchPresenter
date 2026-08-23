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
    api(projects.dictionary)
    api(projects.settings)
    implementation(projects.bible)
    implementation(projects.theme)
    implementation(projects.uiComponents)
    api(projects.resources)
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.roborazzi.composeDesktop)
    testImplementation(testFixtures(projects.bible))
    testImplementation(testFixtures(projects.dictionary))
    testImplementation(testFixtures(projects.uiComponents))
    testImplementation(testFixtures(project(":dictionary-ui")))
    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(projects.dictionary)
    testFixturesImplementation(projects.settings)
    testFixturesImplementation(projects.theme)
    testFixturesImplementation(projects.uiComponents)
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(compose.desktop.uiTestJUnit4)
    testFixturesImplementation(libs.compose.material3)
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
