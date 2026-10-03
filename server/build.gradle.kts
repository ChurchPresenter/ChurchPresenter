plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.coreModels)
    implementation(projects.settings)
    implementation(projects.sharedUi)
    implementation(projects.diagnostics)
    implementation(projects.calendar)
    implementation(projects.lowerThird)
    implementation(projects.presentationEngine)
    implementation(projects.atem)
    implementation(projects.dictionary)
    implementation(projects.bible)
    implementation(projects.songs)
    implementation(projects.slides)
    implementation(projects.qa)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)

    // The self-signed certificate the companion server offers over HTTPS.
    implementation(libs.bouncycastle.pkix)
    implementation(libs.bouncycastle.prov)

    // JUnit 4 on junit-vintage, as the app's suite was -- the server tests use @get:Rule
    // TemporaryFolder and @BeforeClass, which a JUnit 5 run silently skips.
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(testFixtures(projects.atem))
    testImplementation(testFixtures(projects.bible))
    testImplementation(testFixtures(projects.dictionary))
    testImplementation(libs.pdfbox)
}

// Keeps `kotlin-test` on its JUnit 4 flavour: both flavours offer the same capability, and with
// useJUnitPlatform() the Kotlin plugin otherwise picks junit5 -- see the same block in
// composeApp/build.gradle.kts.
configurations.configureEach {
    resolutionStrategy.capabilitiesResolution.withCapability(
        "org.jetbrains.kotlin:kotlin-test-framework-impl"
    ) {
        val junit4 = candidates.firstOrNull {
            (it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)
                ?.module == "kotlin-test-junit"
        }
        if (junit4 != null) select(junit4)
    }
}

// The suite gets a home of its own under build/ so a test can never touch the real ~/.churchpresenter.
// One fork: the suites bind real loopback ports, and in a single JVM no two of them run at once.
tasks.withType<Test>().configureEach {
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.mkdirs() }
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("java.awt.headless", "true")
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
