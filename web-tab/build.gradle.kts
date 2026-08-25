import org.jetbrains.compose.resources.ResourcesExtension

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

compose.resources {
    generateResClass = ResourcesExtension.ResourceClassGeneration.Never
}
fun currentJcefPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("mac") -> if (arch.contains("aarch64")) "macosx-arm64" else "macosx-amd64"
        os.contains("win") -> when {
            arch.contains("aarch64") -> "windows-arm64"
            arch.contains("x86") && !arch.contains("64") -> "windows-i386"
            else -> "windows-amd64"
        }
        else -> when {
            arch.contains("aarch64") -> "linux-arm64"
            arch.contains("arm") -> "linux-arm"
            else -> "linux-amd64"
        }
    }
}

dependencies {
    api(projects.settings)
    api(projects.coreModels)
    api(projects.resources)
    implementation(projects.uiComponents)
    implementation(projects.theme)
    implementation(projects.diagnostics)
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    api("me.friwi:jcefmaven:143.0.14")
    val jcefNativesVersion = "jcef-cffac27+cef-143.0.14+gdd46a37+chromium-143.0.7499.193"
    runtimeOnly("me.friwi:jcef-natives-${currentJcefPlatform()}:$jcefNativesVersion")
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.roborazzi.composeDesktop)
    testImplementation(testFixtures(projects.uiComponents))
    testImplementation(libs.mockk)
}

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    jvmArgs(
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.lwawt=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    )
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
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

roborazzi {
    outputDir.set(layout.projectDirectory.dir("screenshots"))
}
