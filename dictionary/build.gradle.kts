plugins {
    id("cp.jvm-module")
    // The Bible picker scans `user.home`; the suite gets a home of its own under build/ so a test can
    // never touch the real ~/.churchpresenter.
    id("cp.test-home")
    // The strings and icons come from :strings and :icons. The dictionary's own data files are plain
    // classpath resources under src/main/resources/dictionary, read through DictionaryFiles.
    id("cp.compose")
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
    `java-test-fixtures`
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.coreModels)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.bible)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // The tab-driving helpers and the miniature dictionary, shared with :composeApp's dictionary
    // screenshot suite and its server tests.
    testFixturesImplementation(projects.theme)
    testFixturesImplementation(projects.settings)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(projects.sharedUi)
    testFixturesImplementation(testFixtures(projects.sharedUi))
    testFixturesImplementation(libs.kotlinx.serialization.json)
    testFixturesImplementation(libs.mockk)
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.material3)
    testFixturesImplementation(libs.compose.uiTest)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.bible))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.roborazzi.composeDesktop)
}

