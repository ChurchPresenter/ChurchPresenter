plugins {
    id("cp.jvm-module")
    id("cp.compose")
    `java-test-fixtures`
}

dependencies {
    // PresentationFrame, the frame PresentationPresenter draws, is the deck port's.
    api(projects.outputApi)
    api(projects.presentationEngine)
    implementation(projects.sharedUi)
    implementation(projects.strings)
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.diagnostics)

    // ImageIO readers for what Skia refuses -- see PictureDecoder. jpeg covers CMYK/YCCK JPEGs, psd
    // a Photoshop file saved with a picture's name.
    implementation(libs.twelvemonkeys.imageio.core)
    implementation(libs.twelvemonkeys.imageio.jpeg)
    implementation(libs.twelvemonkeys.imageio.psd)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.presentationEngine))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
