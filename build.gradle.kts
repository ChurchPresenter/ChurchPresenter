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
tasks.register("moduleGraph") {
    group = "help"
    description = "Prints each module's project dependencies, for CI's change detection."
    val graph = subprojects.sortedBy { it.path }.map { project ->
        val dependencies = project.configurations
            .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
            .map { it.path }
            .filter { it != project.path }
            .toSortedSet()
        "MODULE ${project.path} ${dependencies.joinToString(" ")}".trimEnd()
    }
    doLast { graph.forEach(::println) }
}
