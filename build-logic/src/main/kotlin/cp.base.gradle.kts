// What every module of the build carries, the Kotlin Multiplatform app included: its version and
// the security floors below.
version = "1.0.0"

// Security floors for what socket.io-client (:stt, :bible-engine, :composeApp) brings, even at its
// newest release: okhttp 3.12.12, which can accept the wrong certificate (GHSA-3cqm-mf7h-prrj), and
// org.json 20090211, whose parser can be made to exhaust memory and stack (GHSA-3vqj-43w4-2q58,
// GHSA-4jq9-2xhw-jpx7, GHSA-rm7j-f5g5-27vv). Lifted in every configuration -- a module's own
// constraint reaches only its consumers' runtime, not their compile classpath. Both keep the API
// engine.io calls.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun floorOf(alias: String) = catalog.findLibrary(alias).get().get().version!!
val okhttpVersion = floorOf("okhttp")
val orgJsonVersion = floorOf("org-json")

// Floors for what other libraries bring older than their advisories' fixes: log4j-api from POI
// (GHSA "improper encoding of non-finite values"), commons-lang3 (uncontrolled recursion on long
// input), and the old Guava some test libraries pull (insecure temp directory, information
// disclosure). Only an older request is lifted; a newer one is left alone. Guava keeps the flavour
// (`-android`, `-jre`) it was asked for.
val versionFloors = mapOf(
    "org.apache.logging.log4j:log4j-api" to floorOf("log4j-api"),
    "org.apache.commons:commons-lang3" to floorOf("commons-lang3"),
    "com.google.guava:guava" to floorOf("guava"),
)

/** Whether dotted version [a] is older than [b], comparing the leading numbers of each part. */
fun olderThan(a: String, b: String): Boolean {
    val left = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val right = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrElse(i) { 0 }
        val r = right.getOrElse(i) { 0 }
        if (l != r) return l < r
    }
    return false
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.squareup.okhttp3" && requested.version?.startsWith("3.") == true) {
            useVersion(okhttpVersion)
            because("GHSA-3cqm-mf7h-prrj: okhttp 3.x can accept the wrong certificate")
        }
        if (requested.group == "org.json" && requested.name == "json") {
            useVersion(orgJsonVersion)
            because("GHSA-3vqj-43w4-2q58 and two more: the old org.json parser")
        }
        val floor = versionFloors["${requested.group}:${requested.name}"]
        val asked = requested.version
        if (floor != null && asked != null && olderThan(asked, floor)) {
            val flavour = if (requested.group == "com.google.guava") "-" + asked.substringAfter('-', "jre") else ""
            useVersion(floor + flavour)
            because("a security advisory fixed at $floor")
        }
    }
}
