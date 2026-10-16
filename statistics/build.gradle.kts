plugins {
    id("cp.jvm-module")
    // StatisticsManager keeps statistics.json and play_log.json under `user.home`; the suite gets a
    // home of its own under build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    id("cp.compose")
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

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    // The XLS export. HSSF is in the core jar, so none of the OOXML schema jars come with it.
    implementation(libs.apache.poi)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // The isolated home, the seeding helpers and the report finders, shared with :composeApp's
    // tests.
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.material3)
    testFixturesImplementation(libs.compose.uiTest)

    testImplementation(kotlin("test"))
    testImplementation(projects.coreModels)
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

