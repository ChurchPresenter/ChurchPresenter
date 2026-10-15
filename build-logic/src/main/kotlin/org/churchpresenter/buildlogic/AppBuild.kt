package org.churchpresenter.buildlogic

import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations
import java.io.File
import java.util.Calendar
import java.util.Properties
import javax.inject.Inject

// What :composeApp's build script and its `cp.app-*` plugins share: the app's version, the build's
// provenance, the signing credentials and the JDK it packages with.

/** The version's first segment: the two-digit year of the build. */
val versionYear: Int = Calendar.getInstance().get(Calendar.YEAR) % 100

/**
 * The app's version: year, then the commit count split across two segments, because Windows MSI
 * limits each version segment to 0-255.
 */
fun Project.appVersion(): String {
    val commits = gitCommitCount()
    return "$versionYear.${commits / 256}.${commits % 256}"
}

fun Project.gitCommitCount(): Int = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 0

fun Project.gitCommitHash(): String = gitOutput("rev-parse", "--short", "HEAD") ?: "unknown"

// ── Build provenance ──────────────────────────────────────────────────────────
// The app is GPLv3 and hardcodes the live-map ping URL, so a fork built from
// source pings churchpresenter.org with a payload identical to an official
// install. Stamping the build's git origin and build kind lets the server tell
// them apart (see the build_channel column and api/ping.ts in the website
// repo). This is attribution for honest forks, not authentication — anyone can
// patch these constants out. Every helper falls back to "unknown"/"nogit"
// rather than failing the build.

/**
 * Runs a git command, returning null when git is missing or exits non-zero. Through
 * `providers.exec`, because starting a process with ProcessBuilder while the build is being
 * configured is deprecated and fails in Gradle 11. Its stderr is captured apart from its output,
 * so git's "fatal: not a git repository" is never handed back as if it were a value.
 */
fun Project.gitOutput(vararg args: String): String? = try {
    val git = providers.exec {
        commandLine("git", *args)
        workingDir = rootProject.projectDir
        isIgnoreExitValue = true
    }
    if (git.result.get().exitValue != 0) null else git.standardOutput.asText.get().trim()
} catch (_: Exception) {
    null
}

// Matches the server's REPO_RE — anything else is stored as 'unknown' there, so
// there's no point sending it.
private val repoSlugRegex = Regex("^[a-z0-9][a-z0-9._-]{0,38}/[a-z0-9][a-z0-9._-]{0,99}$")

/**
 * Parses a git remote URL down to a lowercase "owner/name" slug.
 *
 * Only the slug is ever sent — never the raw URL, which can embed credentials
 * (https://user:token@github.com/...). Handles https://, ssh:// and scp-style
 * (git@host:owner/name) remotes; anything else yields "unknown".
 */
fun parseRepoSlug(remoteUrl: String): String {
    val trimmed = remoteUrl.trim().removeSuffix(".git").trimEnd('/')
    if (trimmed.isEmpty()) return "unknown"
    val path = when {
        trimmed.contains("://") -> trimmed.substringAfter("://").substringAfter('/', "")
        trimmed.contains(':') -> trimmed.substringAfter(':')
        // A bare filesystem path is a local clone, not an identifiable remote.
        else -> return "unknown"
    }
    val parts = path.split('/').filter { it.isNotEmpty() }
    if (parts.size < 2) return "unknown"
    val slug = "${parts[parts.size - 2]}/${parts[parts.size - 1]}".lowercase()
    return if (repoSlugRegex.matches(slug)) slug else "unknown"
}

fun Project.gitRepoSlug(): String = parseRepoSlug(gitOutput("remote", "get-url", "origin") ?: "")

/**
 * Build kind, mirroring the server's KNOWN_BUILD_TYPE set.
 *
 * Precedence puts `release` ahead of `dirty` on purpose: IS_RELEASE means a
 * packaged-installer task ran, which is already this project's user-vs-developer
 * signal (see the ?src=dev flag). Letting a dirty tree outrank it would
 * misclassify official CI builds as developer builds if the packaging steps ever
 * touch a tracked file — and counting real users as developers is the costlier
 * mistake. `dirty` therefore only distinguishes among non-packaged builds.
 */
fun Project.gitBuildType(isRelease: Boolean): String = when {
    gitOutput("rev-parse", "--git-dir") == null -> "nogit" // source tarball / distro packaging
    isRelease -> "release"
    !gitOutput("status", "--porcelain").isNullOrEmpty() -> "dirty"
    else -> "snapshot"
}

// ── Desktop Signing helpers ───────────────────────────────────────────────────
// Signing credentials are stored in the private ChurchPresenter-Signing repo.
// The path to that repo's desktop/ folder is configured in local.properties:
//   desktop.signing.repo.path=/absolute/path/to/ChurchPresenter-Signing/desktop
// If the property is absent the build proceeds unsigned (safe for dev machines).

private fun loadPropsFile(path: String): Properties {
    val props = Properties()
    val f = File(path)
    if (f.exists()) props.load(f.inputStream())
    return props
}

fun Project.desktopSigningRepoPath(): String? {
    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())
    return localProps.getProperty("desktop.signing.repo.path")
}

/** One platform's signing.properties (`macos`, `windows`, `linux`); empty when signing is not set up. */
fun Project.signingProps(platform: String): Properties {
    val repo = desktopSigningRepoPath() ?: return Properties()
    return loadPropsFile("$repo/$platform/signing.properties")
}

/** Returns true when a signing.properties value looks like it has been filled in. */
fun String?.isConfigured() =
    this != null && isNotBlank() && !contains("XXXXXXXXXX") && this != "CHANGE_ME" && !startsWith("YOUR_")

// Resolve Java 21 home for both the run task and packaging tasks.
// Java 24 breaks jlink/jpackage used by Compose Desktop — always use Java 21.
// 1. Try Gradle's toolchain API (works on any machine with JDK 21 registered).
// 2. Fall back to a manual path scan for common macOS install locations.
fun Project.resolvedJdk21Home(): String? = try {
    val launcher = extensions.getByType(JavaToolchainService::class.java).launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.get()
    // executablePath is …/bin/java; go up two levels to get JAVA_HOME
    launcher.executablePath.asFile.parentFile?.parentFile?.absolutePath
} catch (_: Exception) {
    null
} ?: run {
    val jdk21Paths = listOf(
        "${System.getProperty("user.home")}/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home",
        "${System.getProperty("user.home")}/Library/Java/JavaVirtualMachines/jdk-21.0.6+7/Contents/Home",
        "${System.getProperty("user.home")}/Library/Java/JavaVirtualMachines/temurin-21.0.6/Contents/Home",
        "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home",
        "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home",
        "/Library/Java/JavaVirtualMachines/temurin-21.0.6.jdk/Contents/Home"
    )
    jdk21Paths.firstOrNull { path -> File("$path/bin/java").exists() }
}

// Detect current OS classifier for JavaFX native binaries
fun currentOsClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("mac") -> if (arch.contains("aarch64")) "mac-aarch64" else "mac"
        os.contains("win") -> "win"
        else -> "linux"
    }
}

// Detect current OS+arch for JCEF native binaries
fun currentJcefPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("mac") -> if (arch.contains("aarch64")) "macosx-arm64" else "macosx-amd64"
        os.contains("win") -> when {
            arch.contains("aarch64") -> "windows-arm64"
            arch.contains("x86") && !arch.contains("64") -> "windows-i386"
            else -> "windows-amd64"
        }
        else -> when {
            arch.contains("aarch64") -> "linux-arm64"
            arch.contains("arm") -> "linux-arm"
            else -> "linux-amd64"
        }
    }
}

/** What a task that launches processes at execution time is handed by Gradle. */
interface InjectedExec {
    @get:Inject val exec: ExecOperations
}
