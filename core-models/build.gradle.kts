plugins {
    alias(libs.plugins.kotlinJvm)
    `java-test-fixtures`
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // KeyChord is a keyboard binding, so it speaks Compose's Key/KeyEvent/KeyShortcut. No runtime,
    // no composables: this module has no Compose compiler plugin and must not need one.
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testFixturesImplementation(libs.compose.ui)
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
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
    sourceDirectories.setFrom(files("src/main/kotlin"))
    classDirectories.setFrom(fileTree(layout.buildDirectory.dir("classes/kotlin/main")))
    // Highest multiple of 5 each counter currently clears. Measured 2026-08-18: INSTRUCTION 78.69,
    // BRANCH 64.29, LINE 84.68, COMPLEXITY 68.77, METHOD 68.85, CLASS 79.07. KeyChord reads as 0%
    // here because KeyChordTest stayed in :composeApp, where its keyDown fixture lives.
    violationRules {
        rule {
            mapOf(
                "INSTRUCTION" to "0.75",
                "BRANCH" to "0.60",
                "LINE" to "0.80",
                "COMPLEXITY" to "0.65",
                "METHOD" to "0.65",
                "CLASS" to "0.75",
            ).forEach { (name, floor) ->
                limit { counter = name; value = "COVEREDRATIO"; minimum = floor.toBigDecimal() }
            }
        }
    }
}
