plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
}

group = "presentation.engine"
version = "1.0.0"

// Kept in sync with ChurchPresenter (composeApp/build.gradle.kts) so the standalone build matches
// the versions the engine source is compiled against when run in-process inside the app.
val poiVersion = "5.3.0"
val pdfboxVersion = "2.0.33"
val serializationVersion = "1.10.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
    implementation("org.apache.poi:poi:$poiVersion")
    // poi-ooxml-lite is excluded in favour of poi-ooxml-full: the <p:timing> animation tree
    // (CTTLTimeNode*, CTTLAnimateBehavior, …) is not exercised by POI's own code, so the lite
    // schema jar omits those classes. Keeping exactly one schema jar on the classpath avoids
    // duplicate-class clashes. The app build (composeApp/build.gradle.kts) mirrors this.
    implementation("org.apache.poi:poi-ooxml:$poiVersion") {
        exclude(group = "org.apache.poi", module = "poi-ooxml-lite")
    }
    implementation("org.apache.poi:poi-ooxml-full:$poiVersion")
    implementation("org.apache.poi:poi-scratchpad:$poiVersion")
    // Pure-Java snappy decompressor — used by the Keynote IWA reader. No native libraries,
    // honoring the engine's "everything in-JVM, all platforms" rule.
    implementation("io.airlift:aircompressor:2.0.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
    // All rendering must work without a display server — CI-safe on every OS.
    systemProperty("java.awt.headless", "true")
    System.getProperty("updateGolden")?.let { systemProperty("updateGolden", it) }
}

tasks.register<JavaExec>("makeSampleDeck") {
    group = "verification"
    description = "Writes a sample animated .pptx (builds + transitions) for hands-on testing: ./gradlew makeSampleDeck -Pout=/path/sample.pptx"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("presentation.engine.tools.MakeSampleDeck")
    systemProperty("java.awt.headless", "true")
    (project.findProperty("out") as String?)?.let { args(it) }
}

tasks.register<JavaExec>("dumpKeynote") {
    group = "verification"
    description = "Dumps the reverse-engineered IWA structure of a .key file: ./gradlew dumpKeynote -Pfile=/path/deck.key"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("presentation.engine.tools.DumpKeynote")
    systemProperty("java.awt.headless", "true")
    (project.findProperty("file") as String?)?.let { args(it) }
}

tasks.register<JavaExec>("dumpTiming") {
    group = "verification"
    description = "Dumps layers/timeline/transition parsing + degrade warnings for a deck: ./gradlew dumpTiming -Pfile=/path/deck.pptx"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("presentation.engine.tools.DumpTiming")
    systemProperty("java.awt.headless", "true")
    (project.findProperty("file") as String?)?.let { args(it) }
    (project.findProperty("out") as String?)?.let { args(it) }
}
