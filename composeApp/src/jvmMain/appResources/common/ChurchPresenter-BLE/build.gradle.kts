plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
    application
    jacoco
}

group = "engine"
version = "1.0.0"

// Kept in sync with ChurchPresenter (gradle/libs.versions.toml) so the standalone build matches the
// versions the engine source is compiled against when run in-process inside the app.
val ktorVersion = "3.4.0"
val coroutinesVersion = "1.10.2"
val serializationVersion = "1.10.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("io.socket:socket.io-client:2.1.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    // Replay harness only: read archived service .db backups in DbReplayTest. The .db files are
    // never committed; the test skips gracefully when -Dreplay.db is unset (see DbReplayTest).
    testImplementation("org.xerial:sqlite-jdbc:3.45.3.0")
    // A real WebSocket client for EngineServerTest, which drives the engine over loopback exactly
    // as ChurchPresenter does rather than reaching past the socket.
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-websockets:$ktorVersion")
}

application {
    mainClass.set("engine.MainKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    maxHeapSize = "2g"
    systemProperty("bible.root", "${projectDir}/Bibles")
    // Forward the optional .db replay path + fixture id to the forked test JVM (DbReplayTest skips
    // if unset).
    System.getProperty("replay.db")?.let { systemProperty("replay.db", it) }
    System.getProperty("replay.fixture")?.let { systemProperty("replay.fixture", it) }
    System.getProperty("replay.bibles")?.let { systemProperty("replay.bibles", it) }
    System.getProperty("replay.level")?.let { systemProperty("replay.level", it) }
    System.getProperty("replay.updateGolden")?.let { systemProperty("replay.updateGolden", it) }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "engine.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<JavaExec>("replayEval") {
    group = "verification"
    description = "Replays a recorded STT service .db through the pipeline and scores it against operator ground truth (see DbReplayTest/ReplayEval)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("engine.replay.ReplayEval")
}

tasks.register<JavaExec>("stickyAudit") {
    group = "verification"
    description = "Audits a sticky-log-*.jsonl for unexplained/risky sticky jumps (see TRAINING_PLAN.md)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("engine.tools.StickyAuditKt")
}

// Coverage for this module's own logic. UI composables and the CLI diagnostic entry points are
// excluded: the first need a real display, the second exist to print to stdout.
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
            exclude("**/ui/**", "**/MainKt*", "**/*Dump*", "**/MakeSampleDeck*")
        }
    )
}
