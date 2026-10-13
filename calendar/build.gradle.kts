plugins {
    id("cp.jvm-module")
    id("cp.compose")
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
}

compose.resources {
    packageOfResClass = "org.churchpresenter.calendar.generated.resources"
    publicResClass = true
}

dependencies {
    // A run of show is a List<ScheduleItem> and nothing else, so the planner and the Schedule tab
    // speak one model; SongLibrary is how the add-item picker reads the song folder.
    implementation(projects.coreModels)
    // The model the window draws: in this module's API, since its public functions take it.
    api(projects.calendarCore)
    // What an obsScene or atemKey cue does is an action, run by the app.
    api(projects.showControl)
    implementation(projects.theme)

    implementation(libs.kotlinx.coroutines.core)
    // The run-of-show PDF export. Already on the build for :presentation-engine and :converter, so
    // the version comes from the catalogue rather than a literal.
    implementation(libs.pdfbox)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.calendarCore))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.roborazzi.composeDesktop)
}

