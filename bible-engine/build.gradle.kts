plugins {
    id("cp.jvm-module")
    alias(libs.plugins.kotlinx.serialization)
    application
}

group = "engine"

extra["coverageExcludes"] = listOf("**/ComposableSingletons*")

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
    implementation(libs.socket.io.client)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.websockets)
}

application {
    mainClass.set("org.churchpresenter.bibleengine.MainKt")
}

tasks.test {
    maxHeapSize = "2g"
    systemProperty("bible.root", "$projectDir/Bibles")
    System.getProperty("replay.db")?.let { systemProperty("replay.db", it) }
    System.getProperty("replay.fixture")?.let { systemProperty("replay.fixture", it) }
    System.getProperty("replay.bibles")?.let { systemProperty("replay.bibles", it) }
    System.getProperty("replay.level")?.let { systemProperty("replay.level", it) }
    System.getProperty("replay.updateGolden")?.let { systemProperty("replay.updateGolden", it) }
}

tasks.register<JavaExec>("replayEval") {
    group = "verification"
    description = "Replays a recorded STT service .db through the pipeline and scores it against operator ground truth (see DbReplayTest/ReplayEval)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.churchpresenter.bibleengine.replay.ReplayEval")
}

tasks.register<JavaExec>("stickyAudit") {
    group = "verification"
    description = "Audits a sticky-log-*.jsonl for unexplained/risky sticky jumps (see TRAINING_PLAN.md)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.churchpresenter.bibleengine.tools.StickyAuditKt")
}

