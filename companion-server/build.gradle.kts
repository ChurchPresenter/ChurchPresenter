plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinx.serialization)
    `java-test-fixtures`
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(projects.coreModels)
    api(projects.settings)
    api(projects.bible)
    api(projects.dictionary)
    api(projects.atem)

    implementation(libs.kotlinx.coroutines.core)
    implementation(projects.diagnostics)
    implementation(projects.songChords)
    implementation(projects.presentationEngine)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)

    implementation(libs.bouncycastle.pkix)
    implementation(libs.bouncycastle.prov)

    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.ktor.client.core)
    testFixturesImplementation(libs.ktor.client.cio)
    testFixturesImplementation(libs.ktor.client.websockets)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.apache.poi)
    testImplementation(libs.apache.poi.ooxml)
    testImplementation(libs.pdfbox)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.websockets)

    testImplementation(testFixtures(projects.bible))
    testImplementation(testFixtures(projects.diagnostics))
    testImplementation(testFixtures(projects.atem))
    testImplementation(testFixtures(projects.dictionary))
    testImplementation(testFixtures(projects.presentationEngine))
}

// Keeps `kotlin-test` on its JUnit 4 flavour. Both flavours offer the same
// `kotlin-test-framework-impl` capability, so exactly one may be on the classpath, and the root
// build's useJUnitPlatform() makes the Kotlin plugin pick junit5 unless told otherwise.
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

// The suite must never need a display: it runs on headless CI runners.
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
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
