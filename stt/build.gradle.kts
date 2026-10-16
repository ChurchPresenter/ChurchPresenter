plugins {
    id("cp.jvm-module")
    // STTManager's capture and the training-data logs write under `user.home`; the suite gets a home of
    // its own under build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    id("cp.compose")
    id("cp.screenshots")
    `java-test-fixtures`
}

dependencies {
    // The manager the tab drives; its API names it.
    api(projects.sttEngine)
    implementation(projects.sharedUi)
    implementation(projects.coreModels)
    implementation(projects.strings)
    implementation(projects.settings)
    implementation(projects.theme)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // The tab-driving helpers, shared with :composeApp's STT screenshot suite, over the engine's feeders.
    testFixturesApi(testFixtures(projects.sttEngine))
    testFixturesImplementation(projects.theme)
    testFixturesImplementation(projects.settings)
    testFixturesImplementation(projects.sharedUi)
    testFixturesImplementation(testFixtures(projects.sharedUi))
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.material3)
    testFixturesImplementation(libs.compose.uiTest)

    testImplementation(kotlin("test"))
    // The caption screenshot suites draw STTPresenter, which is :presenter's.
    testImplementation(projects.presenter)
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.roborazzi.composeDesktop)
}

