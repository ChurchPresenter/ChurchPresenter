plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.pitest) apply false
    // Applied by id from build-logic's conventions (`cp.detekt`, `cp.screenshots`), which compile
    // against them but must not load a second copy.
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.roborazzi) apply false
}

val gitHooksPath = ".githooks"
if (layout.projectDirectory.file(".git").asFile.exists()) {
    val current = providers.exec {
        commandLine("git", "config", "--get", "core.hooksPath")
        isIgnoreExitValue = true          // exits 1 when the key is simply unset
    }.standardOutput.asText.map { it.trim() }.getOrElse("")

    if (current != gitHooksPath) {
        providers.exec {
            commandLine("git", "config", "core.hooksPath", gitHooksPath)
            isIgnoreExitValue = true      // a missing/!working git must never fail the build
        }.result.get()
        logger.lifecycle("Configured git core.hooksPath = $gitHooksPath")
    }
}

// What every module used to repeat -- its version, the Kotlin and test conventions, the JaCoCo
// floor, detekt and the security pins -- lives in build-logic/ as convention plugins (`cp.*`), applied
// by each module's own `plugins { }` block. See AGENT.md.

// The configured floor of every module, read off the verification tasks themselves so the number
// reported is the number enforced. CSV: FLOOR,<module>,<counter>,<minimum>
tasks.register("coverageFloors") {
    group = "verification"
    description = "Prints each module's configured JaCoCo floors."
    doLast {
        allprojects.forEach { project ->
            project.tasks.withType(JacocoCoverageVerification::class.java).forEach { task ->
                task.violationRules.rules.forEach { rule ->
                    rule.limits.forEach { limit ->
                        println("FLOOR,${project.name},${limit.counter},${limit.minimum}")
                    }
                }
            }
        }
    }
}

// Every module's project dependencies, test fixtures included, one module per line:
// `MODULE <path> <dependency path>...`. CI's change detection (.github/ci/affected_modules.py) reads
// this to decide which suites a change can affect, so the graph is the build's own, not a copy.
// detekt's plugins are left out: :detekt-rules lints every module, but no module's code or tests
// run it, so a change to the rules affects no suite but its own.
tasks.register("moduleGraph") {
    group = "help"
    description = "Prints each module's project dependencies, for CI's change detection."
    val graph = subprojects.sortedBy { it.path }.map { project ->
        val dependencies = project.configurations
            .filter { it.name != "detektPlugins" }
            .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
            .map { it.path }
            .filter { it != project.path }
            .toSortedSet()
        "MODULE ${project.path} ${dependencies.joinToString(" ")}".trimEnd()
    }
    doLast { graph.forEach(::println) }
}

// ── Live-safe allowlist ───────────────────────────────────────────────────────
// config/architecture/live-safe-allowed.txt names the blocking calls :detekt-rules lets stay on the
// event thread. detekt reports a line whose call is gone from its file, but it never visits a file
// that is gone, so this fails on a line naming one. The list only shrinks (AGENT.md, "Live-safe
// output paths").
tasks.register("checkLiveSafeAllowlist") {
    group = "verification"
    description = "Fails on a live-safe allowlist line that names a file that no longer exists."
    val allowlist = layout.projectDirectory.file("config/architecture/live-safe-allowed.txt").asFile
    val root = layout.projectDirectory.asFile
    inputs.file(allowlist)
    doLast {
        val lines = allowlist.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
        val gone = lines.filterNot { root.resolve(it.substringBefore(':').trim()).isFile }
        if (gone.isNotEmpty()) {
            throw GradleException(
                "Live-safe allowlist lines naming a file that is gone -- delete them:\n" +
                    gone.joinToString("\n") { "  $it" }
            )
        }
        logger.lifecycle("Live-safe allowlist: ${lines.size} lines, every file present.")
    }
}

// ── Layers ────────────────────────────────────────────────────────────────────
// Which modules may depend on which, by layer (config/architecture/layers.properties), over the
// edges main code and test fixtures declare -- a test's own classpath is not checked. An edge the
// rules forbid fails the build unless config/architecture/allowed-edges.txt names it, and an entry
// there that is no longer a forbidden edge fails it too, so that list can only shrink. Nothing
// writes that file: an edge goes on it by hand, with its reason, and only when asked. AGENT.md,
// "Layers", has the table.
val layerRules = mapOf(
    "foundation" to setOf("foundation"),
    "engine" to setOf("foundation", "engine"),
    "feature" to setOf("foundation", "engine"),
    "output" to setOf("foundation", "engine"),
    "integration" to setOf("foundation", "engine", "feature", "output", "integration"),
    "app" to setOf("foundation", "engine", "feature", "output", "integration", "app"),
)
val layerCheckedConfigurations = setOf(
    "api", "implementation", "compileOnly", "compileOnlyApi", "runtimeOnly",
    "testFixturesApi", "testFixturesImplementation", "testFixturesCompileOnly", "testFixturesRuntimeOnly",
)

tasks.register("checkModuleLayers") {
    group = "verification"
    description = "Fails on a dependency between modules that their layers do not allow."
    val layersFile = layout.projectDirectory.file("config/architecture/layers.properties").asFile
    val allowedFile = layout.projectDirectory.file("config/architecture/allowed-edges.txt").asFile
    inputs.files(layersFile, allowedFile)
    val modules = subprojects.map { it.path }.sorted()
    val edges = subprojects.flatMap { project ->
        project.configurations
            .filter { it.name in layerCheckedConfigurations }
            .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
            .map { project.path to it.path }
            .filter { (from, to) -> from != to }
    }.toSortedSet(compareBy({ it.first }, { it.second }))
    doLast {
        val layers = java.util.Properties().apply { layersFile.inputStream().use { load(it) } }
            .entries.associate { (k, v) -> ":$k" to v.toString().trim() }
        val problems = mutableListOf<String>()
        modules.filter { it !in layers }.forEach { problems += "$it has no layer in ${layersFile.name}" }
        layers.filter { (_, layer) -> layer !in layerRules }
            .forEach { (module, layer) -> problems += "$module is in an unknown layer '$layer'" }
        val forbidden = edges.filter { (from, to) ->
            val fromLayer = layers[from] ?: return@filter false
            val toLayer = layers[to] ?: return@filter false
            toLayer !in layerRules.getValue(fromLayer)
        }.map { (from, to) -> "$from -> $to" }.toSet()
        val allowed = if (allowedFile.exists()) {
            allowedFile.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
                .map { it.replace(Regex("\\s+"), " ") }
        } else emptyList()
        (forbidden - allowed.toSet()).sorted().forEach { edge ->
            val (from, to) = edge.split(" -> ")
            fun an(layer: String?) = if (layer.orEmpty().first() in "aeiou") "an $layer" else "a $layer"
            problems += "$edge: ${an(layers[from])} module may not depend on ${an(layers[to])} one"
        }
        (allowed.toSet() - forbidden).sorted()
            .forEach { problems += "$it is on ${allowedFile.name} but is no longer a forbidden edge: delete the line" }
        if (problems.isNotEmpty()) {
            throw GradleException("Module layers:\n" + problems.joinToString("\n") { "  $it" })
        }
        logger.lifecycle("Module layers: ${edges.size} edges checked, ${allowed.size} allowed by exception.")
    }
}
