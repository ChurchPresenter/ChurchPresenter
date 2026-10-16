plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    id("cp.compose")
    // Committed, beside the module, as :composeApp's, :shared-ui's, :slides', :media's, :songs' and :bible-tab's are.
    id("cp.screenshots")
    id("cp.pitest")
    alias(libs.plugins.kotlinx.serialization)
    `java-test-fixtures`
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.calendarCore)
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
    testFixturesImplementation(projects.calendarCore)
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
