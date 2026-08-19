plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinx.serialization)
    `java-test-fixtures`
    jacoco
}

group = "presentation.engine"

// Coverage for this module's own logic. UI composables and the CLI diagnostic entry points are
// excluded: the first need a real display, the second exist to print to stdout.
// This module is a parser and a rasterizer: much of it is exercised only by real decks and its
// output is pixels, so it sits well below the 85% the other modules hold. The floors below are its
// measured coverage rounded down — a ratchet against regression, not a target. Raise them as
// coverage improves; never lower one to make a change fit.
extra["coverageFloors"] = mapOf(
    "INSTRUCTION" to "0.60",
    "BRANCH" to "0.45",
    "LINE" to "0.70",
    "COMPLEXITY" to "0.40",
    "METHOD" to "0.75",
)

extra["coverageExcludes"] =
    listOf("**/ui/**", "**/MainKt*", "**/*Dump*", "**/MakeSampleDeck*", "**/ComposableSingletons*")

kotlin {
    jvmToolchain(21)

    // Deck's constructor and DeckSource are internal — only the loaders build a deck. The fixtures
    // consumers' tests use to fake one (src/testFixtures) therefore have to see this module's
    // internals, which is what associating the two compilations does.
    target.compilations.named("testFixtures") {
        associateWith(target.compilations.getByName("main"))
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdfbox)
    implementation(libs.apache.poi)
    // poi-ooxml-lite is excluded in favor of poi-ooxml-full: the <p:timing> animation tree
    // (CTTLTimeNode*, CTTLAnimateBehavior, …) is not exercised by POI's own code, so the lite
    // schema jar omits those classes. Exactly one schema jar may be on the classpath.
    implementation(libs.apache.poi.ooxml) {
        exclude(group = "org.apache.poi", module = "poi-ooxml-lite")
    }
    implementation(libs.apache.poi.ooxmlFull)
    implementation(libs.apache.poi.scratchpad)
    // Pure-Java snappy decompressor — used by the Keynote IWA reader. No native libraries,
    // honoring the engine's "everything in-JVM, all platforms" rule.
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
    mainClass.set("presentation.engine.tools.MakeSampleDeck")
    systemProperty("java.awt.headless", "true")
    (project.findProperty("out") as String?)?.let { args(it) }
}

tasks.register<JavaExec>("dumpKeynote") {
    group = "verification"
    description = "Dumps the reverse-engineered IWA structure of a .key file: ./gradlew :presentation-engine:dumpKeynote -Pfile=/path/deck.key"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("presentation.engine.tools.DumpKeynote")
    systemProperty("java.awt.headless", "true")
    (project.findProperty("file") as String?)?.let { args(it) }
}

tasks.register<JavaExec>("dumpTiming") {
    group = "verification"
    description = "Dumps layers/timeline/transition parsing + degrade warnings for a deck: ./gradlew :presentation-engine:dumpTiming -Pfile=/path/deck.pptx"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("presentation.engine.tools.DumpTiming")
    systemProperty("java.awt.headless", "true")
    (project.findProperty("file") as String?)?.let { args(it) }
    (project.findProperty("out") as String?)?.let { args(it) }
}
