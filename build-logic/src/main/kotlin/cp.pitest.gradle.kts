import info.solidsoft.gradle.pitest.PitestPluginExtension

// Mutation testing, for the modules that hold the core logic (:song-chords, :live-show,
// :core-models, :schedule). `./gradlew :<module>:pitest` writes build/reports/pitest/;
// mutation-test.yml runs it weekly. Not a gate: no mutationThreshold.
apply(plugin = "info.solidsoft.pitest")

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<PitestPluginExtension> {
    pitestVersion.set(catalog.findVersion("pitest").get().requiredVersion)
    junit5PluginVersion.set(catalog.findVersion("pitestJunit5").get().requiredVersion)
    targetClasses.set(listOf("org.churchpresenter.*"))
    excludedClasses.set(listOf("*ComposableSingletons*"))
    // Kotlin's generated null checks: mutating them only proves the compiler inserted them.
    avoidCallsTo.set(listOf("kotlin.jvm.internal.Intrinsics"))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    jvmArgs.set(listOf("-Djava.awt.headless=true"))
}
