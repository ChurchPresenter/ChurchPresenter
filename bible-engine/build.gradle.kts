plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    application
    jacoco
}

group = "engine"

extra["coverageExcludes"] = listOf("**/ui/**", "**/MainKt*", "**/tools/**", "**/ComposableSingletons*")

extra["coverageFloors"] = mapOf(
    "BRANCH" to "0.80",
    "COMPLEXITY" to "0.75",
)

kotlin {
    jvmToolchain(21)
}

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
