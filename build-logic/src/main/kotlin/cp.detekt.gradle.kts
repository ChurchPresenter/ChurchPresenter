import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// detekt on the build's one shared config, with no baseline anywhere: every finding is fixed in
// code (see AGENT.md). A module whose sources are not `src/main` + `src/test` (+ `src/testFixtures`
// when it has fixtures) says so with its own `detekt { source.setFrom(...) }`.
apply(plugin = "io.gitlab.arturbosch.detekt")

// detekt's ktlint wrapper: which of its rules are on is `formatting:` in config/detekt/detekt.yml,
// shared like the rest of that file.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies.add("detektPlugins", catalog.findLibrary("detekt-formatting").get())

// The build's own rules (`live-safe:` in the config, scoped there to the output modules). Not
// transitive: detekt brings its own Kotlin standard library, and the build's newer one must not
// replace it on detekt's class path.
if (path != ":detekt-rules") {
    val rules = dependencies.add("detektPlugins", dependencies.project(mapOf("path" to ":detekt-rules")))
    (rules as ModuleDependency).isTransitive = false
} else {
    // The rules module lints with its own jar, so detekt knows the `live-safe:` keys in the config.
    pluginManager.withPlugin("java") { dependencies.add("detektPlugins", files(tasks.named("jar"))) }
}

extensions.configure<DetektExtension> {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin")
    parallel = true
}

// A module's test fixtures are scanned with the rest of its code.
pluginManager.withPlugin("java-test-fixtures") {
    extensions.configure<DetektExtension> {
        source.from("src/testFixtures/kotlin")
    }
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}
