plugins {
    id("cp.jvm-module")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // An action can carry a schedule item, and names its layer the way the live show spells it.
    api(projects.coreModels)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

// ActionsDocumentedTest reads the two references, so a doc-only change must re-run the suite.
tasks.withType<Test>().configureEach {
    inputs.files(rootProject.file("docs/SHOW_CONTROL.md"), rootProject.file("docs/CONTROL_IN.md"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
