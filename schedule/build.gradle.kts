plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.pitest)
    `java-test-fixtures`
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

// The strings and icons come from :strings and :icons; this module ships no resources of its own.
compose.resources {
    generateResClass = never
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.calendar)
    // What a row does when it goes live is a list of show-control actions.
    api(projects.showControl)
    implementation(projects.diagnostics)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // The tab-driving harness and its fixtures, shared with :composeApp's suites.
    testFixturesImplementation(projects.theme)
    testFixturesImplementation(projects.settings)
    testFixturesImplementation(projects.coreModels)
    testFixturesImplementation(projects.sharedUi)
    testFixturesImplementation(projects.calendar)
    testFixturesImplementation(testFixtures(projects.sharedUi))
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.material3)
    testFixturesImplementation(libs.compose.uiTest)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.property)
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.diagnostics))
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

// Committed, beside the module, as :composeApp's, :shared-ui's, :slides', :media's, :songs' and :bible-tab's are.
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

// Mutation testing covers the pure logic only: the drag hit-testing and the file format. The tab's
// Compose UI tests would run once per mutant and add nothing the screenshots do not already hold.
pitest {
    targetClasses.set(
        listOf("org.churchpresenter.schedule.ScheduleDragMath*", "org.churchpresenter.schedule.ScheduleCipher*"),
    )
    targetTests.set(
        listOf(
            "org.churchpresenter.schedule.ScheduleDragMathTest",
            "org.churchpresenter.schedule.ScheduleDragMathPropertyTest",
            "org.churchpresenter.schedule.ScheduleFileTest",
        ),
    )
}
