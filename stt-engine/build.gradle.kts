plugins {
    id("cp.jvm-module")
    // STTManager's capture and the training-data logs write under `user.home`; the suite gets a home of
    // its own under build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    // The manager publishes its captions as Compose state.
    id("cp.compose")
    `java-test-fixtures`
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.coreModels)

    implementation(libs.kotlinx.coroutines.core)
    // The STT server speaks socket.io; org.json comes with it.
    implementation(libs.socket.io.client)

    implementation(compose.desktop.currentOs)

    // The feeders that hand the manager what the server would send, and a silent server to connect to;
    // shared with the STT tab's and the Bible tab's suites.
    testFixturesImplementation(libs.socket.io.client)
    testFixturesImplementation(compose.runtime)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
