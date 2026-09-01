package org.churchpresenter.app.churchpresenter.composables

import java.io.File

/**
 * Where ffmpeg actually is, rather than where `PATH` says it is.
 *
 * **The app ships its own ffmpeg.** It is the one dependency of the three that may be redistributed
 * — this app is GPLv3 and ffmpeg is GPL, where NDI's licence forbids it and VLC is a separate
 * application — so cameras work on a machine where nothing has been installed. That is not a
 * convenience: nothing in the JVM ever touches AVFoundation, only the ffmpeg it spawns does, so on
 * a Mac with no ffmpeg no camera is ever opened, macOS is never asked for permission, and the app
 * never appears in System Settings → Privacy → Camera at all. Issue #464 is both halves of that.
 *
 * A discovered ffmpeg is still worth finding, because a bundled binary can be absent: an unpackaged
 * run before the download task has fetched one, or a Linux package that strips it. So the search
 * keeps the paths it always had:
 *
 * **A desktop app does not inherit the shell's `PATH`.** Launched from Finder, a macOS `.app` gets
 * launchd's default — `/usr/bin:/bin:/usr/sbin:/sbin` — which contains neither Homebrew prefix, so
 * `ProcessBuilder("ffmpeg", …)` fails to start with the binary sitting in `/opt/homebrew/bin`
 * where the operator installed it and where their terminal finds it every time. The same gap
 * exists on Linux for snap and flatpak, and on Windows for an unpacked build in `C:\ffmpeg`.
 *
 * The symptom is not "ffmpeg is missing". It is a camera that appears in the dropdown — device
 * names come from `system_profiler`, which is in `/usr/sbin` and always resolves — and then never
 * shows a picture, next to a hint telling the operator to install a tool they already installed.
 * That is issue #431.
 *
 * Resolution is cached rather than repeated: it costs a process launch, and every camera listing
 * would otherwise pay it. [recheck] is what clears that cache, and it exists for the settings row —
 * an operator who has just pointed [customPath] at another build gets an answer without restarting
 * the app. It is deliberately *not* a test seam: the decisions this object makes are the pure
 * [ffmpegSearchOrder], [bundledFfmpegPath] and [resolveFfmpegPath] beside it, and those are what
 * the tests drive.
 */
internal object FfmpegBinary {

    /** How long to wait for `ffmpeg -version`; a candidate that has not answered by then is not it. */
    private const val VERSION_TIMEOUT_S = 5L

    /**
     * An operator's own ffmpeg, from `ProjectionSettings.ffmpegPath`, or empty for the bundled one.
     *
     * Assigned once at startup from the saved settings and again when the settings row changes it,
     * exactly as `vlcCustomPath` is. Changing it does not invalidate anything by itself — the
     * caller follows with [recheck], because the probe it triggers must not run on the UI thread.
     */
    var customPath: String = ""

    private var resolved: String? = null
    private var available: Boolean? = null

    /** The resolved executable, or the bare name when nothing answered — the caller reports that. */
    val path: String
        get() = resolved ?: resolve().also { resolved = it }

    /** True when this process can actually be started and identifies itself as ffmpeg. */
    val isAvailable: Boolean
        get() = available ?: runsAsFfmpeg(path).also { available = it }

    /**
     * True when [path] is the copy shipped inside the app rather than one found on the machine.
     *
     * The settings row says which, because "using ffmpeg at /opt/homebrew/bin/ffmpeg" and "using
     * the one that came with the app" are different situations to be in when a camera misbehaves.
     */
    val isBundled: Boolean
        get() = path == bundledFfmpegPath(System.getProperty("os.name", ""), appResourcesDir())

    /** Forgets what was resolved and probes again, returning whether ffmpeg is now available. */
    fun recheck(): Boolean {
        resolved = null
        available = null
        return isAvailable
    }

    private fun resolve(): String {
        val osName = System.getProperty("os.name", "")
        val candidates = ffmpegSearchOrder(
            customPath = customPath,
            bundledPath = bundledFfmpegPath(osName, appResourcesDir()),
            discovered = ffmpegCandidatePaths(osName, System::getenv),
        )
        return resolveFfmpegPath(candidates) { runsAsFfmpeg(it) }
    }

    private fun runsAsFfmpeg(candidate: String): Boolean =
        readCommandOutput(listOf(candidate, "-version"), VERSION_TIMEOUT_S).exitCode == 0
}

/** The bare name, tried first among the discovered paths: it means a deliberately configured `PATH`. */
private const val FFMPEG_ON_PATH = "ffmpeg"

/**
 * The directory the packaged app keeps its per-OS resources in, or `null` when running from source.
 *
 * `compose.application.resources.dir` is set both by `:composeApp:run` and in the packaged bundle,
 * so the source-tree fallback is only for a launch that bypasses Gradle — an IDE run configuration
 * that has not been given the property. `SwingFileChooser` walks up for its icon for the same
 * reason and in the same way.
 */
private fun appResourcesDir(): File? = appResourcesDirFrom(
    packagedDir = System.getProperty("compose.application.resources.dir"),
    workingDir = File(System.getProperty("user.dir", ".")).absoluteFile,
    osName = System.getProperty("os.name", ""),
)

/**
 * The resources directory implied by [packagedDir], or the one [workingDir] sits inside.
 *
 * Taken apart from the system properties it reads so the decision — packaged wins, otherwise walk
 * up looking for this OS's source directory — is one a test can drive on any machine.
 */
internal fun appResourcesDirFrom(packagedDir: String?, workingDir: File, osName: String): File? {
    if (packagedDir != null) return File(packagedDir)

    val osDir = appResourcesOsDirName(osName) ?: return null
    return generateSequence(workingDir) { it.parentFile }
        .map { File(it, "composeApp/src/jvmMain/appResources/$osDir") }
        .firstOrNull { it.isDirectory }
}

/** The `appResources` subdirectory [osName] is packaged from, or `null` for an OS we do not ship. */
internal fun appResourcesOsDirName(osName: String): String? {
    val os = osName.lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") -> "macos"
        os.contains("win") -> "windows"
        os.contains("nux") || os.contains("nix") -> "linux"
        else -> null
    }
}

/**
 * The ffmpeg shipped inside the app, or `null` when this build has none.
 *
 * [resourcesDir] null is an unpackaged run with no source tree to fall back on; a name that does
 * not exist on disk is a build whose download task has not run, or a package that stripped it.
 * Both mean "there is no bundled ffmpeg", which the search order simply skips over.
 */
internal fun bundledFfmpegPath(osName: String, resourcesDir: File?): String? {
    if (resourcesDir == null) return null
    val name = if (osName.lowercase().contains("win")) "ffmpeg.exe" else "ffmpeg"
    return File(resourcesDir, name).takeIf { it.isFile }?.absolutePath
}

/**
 * Every ffmpeg worth trying, best first: the operator's own, then ours, then whatever is installed.
 *
 * The order is the whole point and mirrors `NdiRuntime.searchDirsFor`. [customPath] wins because it
 * is an explicit choice, and it is the only way to override a bundled binary that turns out to be
 * wrong for a particular machine. [bundledPath] comes next rather than last so the app behaves the
 * same on every machine it is installed on, whatever the operator happens to have in `/usr/local`.
 * [discovered] is the fallback for a build that ships no binary.
 */
internal fun ffmpegSearchOrder(
    customPath: String,
    bundledPath: String?,
    discovered: List<String>,
): List<String> =
    (listOfNotNull(customPath.trim().takeIf(String::isNotEmpty), bundledPath) + discovered).distinct()

/**
 * Every place ffmpeg is plausibly installed on [osName], best first.
 *
 * The bare name leads, so nothing here overrides a deliberately configured `PATH`. What follows are
 * the default prefixes of the package managers people actually use — Homebrew on both
 * architectures and MacPorts; snap and flatpak beside the distro's own; and on Windows the two
 * layouts a downloaded build lands in. [env] reads the environment, so a test can supply one.
 */
internal fun ffmpegCandidatePaths(osName: String, env: (String) -> String?): List<String> {
    val os = osName.lowercase()
    val extras = when {
        os.contains("mac") || os.contains("darwin") -> listOf(
            "/opt/homebrew/bin/ffmpeg",   // Homebrew, Apple silicon
            "/usr/local/bin/ffmpeg",      // Homebrew, Intel
            "/opt/local/bin/ffmpeg",      // MacPorts
            "/usr/bin/ffmpeg",
        )

        os.contains("win") -> listOfNotNull(
            env("LOCALAPPDATA")?.let { "$it\\Microsoft\\WindowsApps\\ffmpeg.exe" },
            env("ProgramFiles")?.let { "$it\\ffmpeg\\bin\\ffmpeg.exe" },
            "C:\\ffmpeg\\bin\\ffmpeg.exe",
        )

        else -> listOf(
            "/usr/bin/ffmpeg",
            "/usr/local/bin/ffmpeg",
            "/snap/bin/ffmpeg",
            "/var/lib/flatpak/exports/bin/ffmpeg",
            "/home/linuxbrew/.linuxbrew/bin/ffmpeg",
        )
    }
    return listOf(FFMPEG_ON_PATH) + extras
}

/**
 * The first of [candidates] that [isExecutable] finds and [canRun] accepts, or the bare name.
 *
 * Absolute candidates are checked for existence first so a machine with none of them installed
 * pays one process launch — for the bare name — rather than one per directory that does not exist.
 * Falling back to the bare name rather than to `null` keeps the "ffmpeg is not installed" message
 * the callers already show, which is the right thing to say when nothing answered.
 *
 * [isExecutable] is a parameter so a test can describe a machine rather than depend on the one it
 * happens to run on — this repo's suite runs on three platforms and on developer machines that do
 * have Homebrew's ffmpeg installed.
 */
internal fun resolveFfmpegPath(
    candidates: List<String>,
    isExecutable: (String) -> Boolean = { File(it).canExecute() },
    canRun: (String) -> Boolean,
): String =
    candidates.firstOrNull { candidate ->
        (candidate == FFMPEG_ON_PATH || isExecutable(candidate)) && canRun(candidate)
    } ?: FFMPEG_ON_PATH
