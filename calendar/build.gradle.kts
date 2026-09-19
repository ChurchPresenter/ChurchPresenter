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

compose.resources {
    packageOfResClass = "org.churchpresenter.calendar.generated.resources"
    publicResClass = true
}

dependencies {
    // A run of show is a List<ScheduleItem> and nothing else, so the planner and the Schedule tab
    // speak one model; SongLibrary is how the add-item picker reads the song folder.
    implementation(projects.coreModels)
    implementation(projects.theme)

    implementation(libs.kotlinx.coroutines.core)
    // The run-of-show PDF export. Already on the build for :presentation-engine and :converter, so
    // the version comes from the catalogue rather than a literal.
    implementation(libs.pdfbox)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
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
