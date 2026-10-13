import org.churchpresenter.buildlogic.appVersion
import org.churchpresenter.buildlogic.gitBuildType
import org.churchpresenter.buildlogic.gitCommitCount
import org.churchpresenter.buildlogic.gitCommitHash
import org.churchpresenter.buildlogic.gitRepoSlug
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// :composeApp's BuildConfig: the version, the build's provenance and the endpoints a build is made
// with. Applied after `kotlin { jvm() }`, whose jvmMain source set it adds the generated file to.

// Generate BuildConfig with version info accessible at runtime
val generateBuildConfig = tasks.register("generateBuildConfig") {
    val commitHash = gitCommitHash()
    val commits = gitCommitCount()
    val appVersion = appVersion()
    // A packaged installer build (packageDmg/Msi/Exe/Deb, packageDistributionForCurrentOS,
    // signing/notarization — see .github/workflows/build.yml) is a real release. A plain
    // `run` or IDE launch is a developer build. Used to tag live-map pings dev vs. user.
    //
    // Task names only. `taskNames` also carries task options and their values, and a test filter
    // is matched like any other string: `--tests '*KeySignalModifierTest*'` contains "sign", so
    // naming that class compiled the whole run as a release -- and every test that branches on
    // IS_RELEASE saw production behaviour, depending on which classes the command line happened
    // to name.
    val requested = gradle.startParameter.taskNames
    val isRelease = requested.filterIndexed { i, arg ->
        !arg.startsWith("-") && requested.getOrNull(i - 1) != "--tests"
    }.any { task ->
        val t = task.lowercase()
        listOf("package", "distributable", "sign", "notariz").any { t.contains(it) }
    }
    // Planning Center integration: one shared OAuth app for the whole project (per PCO's own
    // multi-tenant guidance — each church authorizes this one app, rather than registering their
    // own). Neither value is committed — both are read from env vars at build time, same pattern
    // as SENTRY_AUTH_TOKEN in the app's build script. Local/dev builds without these set simply
    // can't complete OAuth.
    // Build provenance for the live-map ping — see the helpers in AppBuild.kt. Resolved here at
    // build time, never at runtime: a runtime git call would read whatever directory the user
    // happened to launch the app from.
    val repoSlug = gitRepoSlug()
    val buildType = gitBuildType(isRelease)
    // Whether this build came off CI or somebody's machine. `IS_RELEASE` cannot answer that: it
    // means "a packaging task ran", so `./gradlew packageDmg` on a laptop produces a build that
    // calls itself a release and reports `environment=production` to Sentry. Over 30 days that
    // left 224 of 234 camera reports labelled production, including a tester's, and no way to tell
    // an operator's build from one built to try a fix out.
    //
    // `providers.environmentVariable` and NOT `System.getenv`, for the reason recorded on
    // `printCoverageLink` (cp.app-coverage): `System.getenv` reads the Gradle DAEMON's environment,
    // frozen when the daemon started, which need not be the environment of the build asking.
    val buildChannel = providers.environmentVariable("GITHUB_ACTIONS")
        .map { if (it == "true") "ci" else "local" }
        .getOrElse("local")
    val planningCenterClientId = System.getenv("PLANNING_CENTER_CLIENT_ID") ?: ""
    val planningCenterClientSecret = System.getenv("PLANNING_CENTER_CLIENT_SECRET") ?: ""
    // Calendar sync: the relay and the website endpoint its client key is fetched from. Kept out of
    // the repository like the Planning Center pair -- from GitHub Secrets in CI, and locally from the
    // environment or ~/.gradle/gradle.properties. A build without them has calendar sync but no
    // relay to reach. Only characters a URL needs survive, so nothing can break the generated source.
    fun endpoint(name: String): String =
        (providers.environmentVariable(name).orNull ?: providers.gradleProperty(name).orNull ?: "")
            .filter { it.isLetterOrDigit() || it in ":/.-_~?=&%" }
    val calendarRelayUrl = endpoint("CALENDAR_RELAY_URL")
    val calendarRelayConfigUrl = endpoint("CALENDAR_RELAY_CONFIG_URL")
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")

    // Always re-run — git state (commit count/hash) can change without any file edits
    outputs.upToDateWhen { false }
    outputs.dir(outputDir)

    doLast {
        val dir = outputDir.get().asFile.resolve("org/churchpresenter/app/churchpresenter")
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            """
            |package org.churchpresenter.app.churchpresenter
            |
            |object BuildConfig {
            |    const val APP_VERSION = "$appVersion"
            |    const val COMMIT_HASH = "$commitHash"
            |    const val COMMIT_COUNT = "$commits"
            |    const val VERSION_DISPLAY = "$appVersion ($commitHash)"
            |    const val IS_RELEASE = $isRelease
            |    const val REPO_SLUG = "$repoSlug"
            |    const val BUILD_TYPE = "$buildType"
            |    const val BUILD_CHANNEL = "$buildChannel"
            |    const val PLANNING_CENTER_CLIENT_ID = "$planningCenterClientId"
            |    const val PLANNING_CENTER_CLIENT_SECRET = "$planningCenterClientSecret"
            |    const val CALENDAR_RELAY_URL = "$calendarRelayUrl"
            |    const val CALENDAR_RELAY_CONFIG_URL = "$calendarRelayConfigUrl"
            |}
            """.trimMargin()
        )
    }
}

extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.named("jvmMain") {
        kotlin.srcDir(generateBuildConfig.map { layout.buildDirectory.dir("generated/buildconfig") })
    }
}

tasks.named("compileKotlinJvm") {
    dependsOn(generateBuildConfig)
}
