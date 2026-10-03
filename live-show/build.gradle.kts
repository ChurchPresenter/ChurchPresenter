plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The cues carry the shared models; the state is Compose snapshot state so the outputs can
    // read it in composition. No composables here, so no Compose compiler plugin.
    api(projects.coreModels)
    api(projects.settings)
    api(libs.compose.runtime)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
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
