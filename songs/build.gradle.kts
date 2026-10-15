plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    id("cp.compose")
    // Committed, beside the module, as :composeApp's, :shared-ui's, :slides' and :media's are.
    id("cp.screenshots")
    `java-test-fixtures`
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.songChords)
    // The song editor: its chord preview draws `ChordLine`, and its footer formats the typical
    // live time the way the calendar does. The Background button is the app's, passed in as a slot,
    // so `:profiles` is only on the test classpath below.
    implementation(projects.presenter)
    implementation(projects.calendarCore)

    // Mac SongPresenter `.sps` libraries are SQLite files.
    implementation(libs.sqlite.jdbc)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // The tab-driving harness and its fixtures, shared with :composeApp's suites.
    testFixturesImplementation(projects.theme)
    testFixturesImplementation(projects.settings)
    testFixturesImplementation(projects.coreModels)
    testFixturesImplementation(projects.sharedUi)
    // PinnedFonts, which every screenshot of the tab is taken inside.
    testFixturesImplementation(testFixtures(projects.sharedUi))
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.material3)
    testFixturesImplementation(libs.compose.uiTest)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    // The editor's suites draw the real Background button in its slot, as the app does.
    testImplementation(projects.profiles)
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.roborazzi.composeDesktop)
}

