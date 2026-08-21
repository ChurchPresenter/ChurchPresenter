plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    `java-test-fixtures`
    jacoco
}

group = "org.churchpresenter"

// Only branch and complexity still fall short of the root build's 85% default — a parser and a
// rasterizer are dense with per-format special cases, and the last of those need real documents to
// reach. Both are the measured value rounded down: a ratchet, raised as tests are added, never
// lowered to make a change fit, and deleted outright once a counter clears 85%.
extra["coverageFloors"] = mapOf(
    "BRANCH" to "0.77",
    "COMPLEXITY" to "0.71",
)

extra["coverageExcludes"] =
    listOf("**/ui/**", "**/MainKt*", "**/*Dump*", "**/MakeSampleDeck*", "**/ComposableSingletons*")

kotlin {
    jvmToolchain(21)

    target.compilations.named("testFixtures") {
        associateWith(target.compilations.getByName("main"))
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdfbox)
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml) {
        exclude(group = "org.apache.poi", module = "poi-ooxml-lite")
    }
    implementation(libs.apache.poi.ooxmlFull)
    implementation(libs.apache.poi.scratchpad)
    implementation(libs.aircompressor)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    // All rendering must work without a display server — CI-safe on every OS.
    systemProperty("java.awt.headless", "true")
    System.getProperty("updateGolden")?.let { systemProperty("updateGolden", it) }
}

tasks.register<JavaExec>("makeSampleDeck") {
    group = "verification"
    description = "Writes a sample animated .pptx (builds + transitions) for hands-on testing: ./gradlew :presentation-engine:makeSampleDeck -Pout=/path/sample.pptx"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.churchpresenter.presentationengine.tools.MakeSampleDeck")
    systemProperty("java.awt.headless", "true")
    (project.findProperty("out") as String?)?.let { args(it) }
}

tasks.register<JavaExec>("dumpKeynote") {
    group = "verification"
    description = "Dumps the reverse-engineered IWA structure of a .key file: ./gradlew :presentation-engine:dumpKeynote -Pfile=/path/deck.key"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.churchpresenter.presentationengine.tools.DumpKeynote")
    systemProperty("java.awt.headless", "true")
    (project.findProperty("file") as String?)?.let { args(it) }
}

tasks.register<JavaExec>("dumpTiming") {
    group = "verification"
    description = "Dumps layers/timeline/transition parsing + degrade warnings for a deck: ./gradlew :presentation-engine:dumpTiming -Pfile=/path/deck.pptx"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.churchpresenter.presentationengine.tools.DumpTiming")
    systemProperty("java.awt.headless", "true")
    (project.findProperty("file") as String?)?.let { args(it) }
    (project.findProperty("out") as String?)?.let { args(it) }
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
