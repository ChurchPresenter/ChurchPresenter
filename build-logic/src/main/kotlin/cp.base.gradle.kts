import org.churchpresenter.buildlogic.SecurityFloorRule

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
// disclosure). Applied to each library's metadata by SecurityFloorRule, so a detached
// configuration gets them too.
dependencies.components.all(SecurityFloorRule::class.java) {
    params(
        hashMapOf(
            "org.apache.logging.log4j:log4j-api" to floorOf("log4j-api"),
            "org.apache.commons:commons-lang3" to floorOf("commons-lang3"),
            "com.google.guava:guava" to floorOf("guava"),
        ),
    )
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
    }
}
