plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.currentOs)
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
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

tasks.test {
    systemProperty("java.awt.headless", "true")
}
