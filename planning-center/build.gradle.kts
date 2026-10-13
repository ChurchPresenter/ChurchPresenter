plugins {
    id("cp.jvm-module")
    // The import reads settings and the song library under `user.home`; the suite gets a home of its own
    // under build/ so a test can never touch the real ~/.churchpresenter.
    id("cp.test-home")
    id("cp.compose")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(projects.settings)
    implementation(projects.diagnostics)

    // The import dialog: the shared widgets, the app's strings and theme, and the song model a
    // plan's songs are matched against and saved as.
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.theme)
    implementation(projects.coreModels)
    // The operator's primary Bible, which the scripture a plan names is read from.
    implementation(projects.bible)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.coreModels))
    testImplementation(testFixtures(projects.bible))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.cio)
    // The fake attachment host in PlanningCenterDownloadTest. The callback listener itself is the
    // JDK's own HttpServer, so production no longer needs a server library at all.
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.netty)
}

