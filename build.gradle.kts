plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
}

// Point Git at the repo's own hooks directory so every checkout gets the pre-commit branch
// guard in .githooks/. `core.hooksPath` lives in .git/config, which is never cloned or pushed
// — Git deliberately refuses to let a clone activate hooks by itself — so it has to be set
// once per working copy. Doing it here means the first `./gradlew` run wires it up.
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