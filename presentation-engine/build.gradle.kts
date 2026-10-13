plugins {
    id("cp.jvm-module")
    alias(libs.plugins.kotlinx.serialization)
    `java-test-fixtures`
}

extra["coverageExcludes"] = listOf("**/ComposableSingletons*")

kotlin {
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
    // No baseline: every finding the module once carried has been fixed in code, so the rules gate
    // all of it. A new finding is fixed, never suppressed.
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

