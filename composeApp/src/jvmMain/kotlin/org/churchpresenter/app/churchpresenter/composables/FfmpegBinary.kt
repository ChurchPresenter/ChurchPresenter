package org.churchpresenter.app.churchpresenter.composables

import java.io.File

/**
 * Where ffmpeg actually is, rather than where `PATH` says it is.
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
 * Resolution runs once per process and is cached: it costs a process launch, every camera listing
 * would otherwise repeat it, and a binary does not move while the app is running.
 */
internal object FfmpegBinary {

    /** How long to wait for `ffmpeg -version`; a candidate that has not answered by then is not it. */
    private const val VERSION_TIMEOUT_S = 5L

    /** The resolved executable, or the bare name when nothing answered — the caller reports that. */
    val path: String by lazy {
        resolveFfmpegPath(ffmpegCandidatePaths(System.getProperty("os.name", ""), System::getenv)) {
            runsAsFfmpeg(it)
        }
    }

    /** True when this process can actually be started and identifies itself as ffmpeg. */
    val isAvailable: Boolean by lazy { runsAsFfmpeg(path) }

    private fun runsAsFfmpeg(candidate: String): Boolean =
        readCommandOutput(listOf(candidate, "-version"), VERSION_TIMEOUT_S).exitCode == 0
}

/** The bare name, tried first: an operator who put ffmpeg on the app's own `PATH` meant that one. */
private const val FFMPEG_ON_PATH = "ffmpeg"

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
