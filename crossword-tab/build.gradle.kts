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

// The UI strings come from :strings; the puzzles are plain classpath resources (see below), so this
// module generates no Res class of its own.
compose.resources {
    generateResClass = never
}

dependencies {
    implementation(projects.strings)
    implementation(projects.theme)
    implementation(projects.settings)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.roborazzi.composeDesktop)
}

// The encoded puzzles are written by the :crossword authoring tool into crossword/encoded/ and
// committed there; this copies them into this module's resources at build time. The copy is
// git-ignored -- crossword/encoded/ is the one source of truth.
val syncCrosswordFiles = tasks.register<Copy>("syncCrosswordFiles") {
    from(rootProject.file("crossword/encoded"))
    include("*.xwp")
    into(layout.projectDirectory.dir("src/main/resources/crossword"))
    doFirst {
        destinationDir.mkdirs()
    }
}
tasks.named("processResources") { dependsOn(syncCrosswordFiles) }

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}

// Committed, beside the module, as the other modules' suites are.
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
