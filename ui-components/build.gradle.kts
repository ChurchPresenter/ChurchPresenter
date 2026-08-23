plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The colour schemes and semantic roles these widgets draw themselves with.
    implementation(projects.theme)
    // The icons and labels they carry. `api`, because a widget's own signature can hand a caller a
    // resource type — and because a call site reads `Res.string.x` with the same single accessor
    // whether the composable it is passing to lives here or in :composeApp.
    api(projects.resources)

    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.compose.material3)
    // PrimaryActionButtons draws a Material icon.
    implementation(libs.compose.material.icons.extended)
    // RecentColors keeps the colour picker's own history in ~/.churchpresenter/.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    // Six `LongMethod` findings, all pre-existing: these composables came out of :composeApp, where
    // its baseline had been suppressing them since the size rules were switched on. Every other
    // finding the move surfaced -- 59 `MaxLineLength`, including 42 in AlignmentButtons and one line
    // of 359 characters -- was FIXED rather than baselined. Two of the six (the alignment button
    // groups) crossed the 100-line threshold *because* of that wrapping. Nothing else belongs in
    // here: the rules gate new code in this module.
    baseline = file("config/detekt/baseline.xml")
    source.setFrom("src/main/kotlin", "src/test/kotlin")
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}
