import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("cp.jvm-module")
    id("cp.compose")
    id("cp.screenshots")
}

compose.resources {
    packageOfResClass = "org.churchpresenter.songlibrary.generated.resources"
    publicResClass = true
}

dependencies {
    implementation(projects.coreModels)
    implementation(projects.songChords)
    implementation(projects.theme)

    implementation(libs.kotlinx.coroutines.core)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.roborazzi.composeDesktop)
}

compose.desktop {
    application {
        mainClass = "org.churchpresenter.songlibrary.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ChurchPresenter-SongLibraryManager"
            packageVersion = "1.0.0"
        }
    }
}
