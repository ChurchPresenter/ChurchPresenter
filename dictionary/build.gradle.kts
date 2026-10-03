plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    `java-test-fixtures`
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

// The strings and icons come from :strings and :icons. The dictionary's own data files are plain
// classpath resources under src/main/resources/dictionary, read through DictionaryFiles.
compose.resources {
    generateResClass = never
}

dependencies {
    implementation(projects.sharedUi)
    implementation(projects.coreModels)
    implementation(projects.strings)
    implementation(projects.icons)
    implementation(projects.settings)
    implementation(projects.theme)
    implementation(projects.bible)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // The tab-driving helpers and the miniature dictionary, shared with :composeApp's dictionary
    // screenshot suite and its server tests.
    testFixturesImplementation(projects.theme)
    testFixturesImplementation(projects.settings)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(projects.sharedUi)
    testFixturesImplementation(testFixtures(projects.sharedUi))
    testFixturesImplementation(libs.kotlinx.serialization.json)
    testFixturesImplementation(libs.mockk)
    testFixturesImplementation(compose.desktop.currentOs)
    testFixturesImplementation(libs.compose.material3)
    testFixturesImplementation(libs.compose.uiTest)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(projects.sharedUi))
    testImplementation(testFixtures(projects.bible))
    testImplementation(libs.compose.uiTestJunit4)
    testImplementation(libs.compose.uiTest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

// The Bible picker scans `user.home`; the suite gets a home of its own under build/ so a test can
// never touch the real ~/.churchpresenter.
tasks.withType<Test>().configureEach {
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.mkdirs() }
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("java.awt.headless", "true")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin", "src/testFixtures/kotlin")
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
