plugins {
    id("cp.jvm-module")
    `java-test-fixtures`
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // A run of show is a List<ScheduleItem> and nothing else, so the planner and the Schedule tab
    // speak one model; it is in this module's API.
    api(projects.coreModels)

    implementation(libs.kotlinx.coroutines.core)
    // The run-of-show PDF export. Already on the build for :presentation-engine and :converter, so
    // the version comes from the catalogue rather than a literal.
    implementation(libs.pdfbox)
    implementation(libs.kotlinx.serialization.json)

    // RowSamples, the one list of every kind of row, shared with :calendar's suites.
    testFixturesApi(projects.coreModels)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
