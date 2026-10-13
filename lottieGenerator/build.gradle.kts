import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("cp.jvm-module")
    id("cp.test-home")
    id("cp.compose")
    id("cp.screenshots")
    alias(libs.plugins.kotlinx.serialization)
}

extra["coverageExcludes"] = listOf("**/ComposableSingletons*")

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    // The app's colour schemes, typography and semantic colours — this tool no longer
    // builds its own Material layer, only its hand-drawn panel palette.
    implementation(projects.theme)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.compottie)
    implementation(libs.compottie.dot)

    testImplementation(kotlin("test"))
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(libs.roborazzi.composeDesktop)
    // The screenshot suite reports a hung capture with the shared thread dump.
    testImplementation(projects.diagnostics)
    testImplementation(testFixtures(projects.diagnostics))
}

tasks.register<JavaExec>("dumpStyleReview") {
    group = "verification"
    description = "Renders before/after (Detail off/on) stills of one or more styles, stacked into " +
        "one PNG per style for visual review: ./gradlew :lottieGenerator:dumpStyleReview " +
        "-Pstyles=14,15,16 [-Pout=/dir]"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.churchpresenter.lottiegen.tools.DumpStyleReview")
    systemProperty("java.awt.headless", "true")
    (project.findProperty("styles") as String?)?.let { args(it) }
    (project.findProperty("out") as String?)?.let { args(it) }
}

compose.desktop {
    application {
        mainClass = "org.churchpresenter.lottiegen.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ChurchPresenter-LottieGen"
            packageVersion = "1.0.0"

            windows {
                menuGroup = "ChurchPresenter"
                upgradeUuid = "b2c3d4e5-f6a7-8901-bcde-f23456789012"
            }
        }
    }
}

