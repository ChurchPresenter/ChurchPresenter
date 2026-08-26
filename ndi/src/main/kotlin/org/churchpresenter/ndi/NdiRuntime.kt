package org.churchpresenter.ndi

import java.io.File

/**
 * Where the NDI Runtime is, if it is anywhere.
 *
 * **This app ships no NDI binaries and cannot.** Vizrt's licence does not permit redistributing the
 * runtime, which is why `obs-ndi`/DistroAV resolved the same problem the same way: load `libndi` at
 * run time and tell the user where to get it when it is missing. So NDI behaves here exactly as VLC
 * already does — a separately installed runtime, auto-detected at startup, overridable by a path in
 * settings, and the feature reading as "not installed yet" rather than as a failure when it is
 * absent.
 *
 * Discovery order, highest priority first:
 *  1. the operator's explicit path from settings, if they set one;
 *  2. `NDI_RUNTIME_DIR_V6`, then `NDI_RUNTIME_DIR_V5` — Vizrt's own supported mechanism, and what
 *     DistroAV keys on, so a machine already set up for OBS needs nothing done to it here;
 *  3. the platform's default install location.
 *
 * Every function here is pure over its arguments — the environment and the filesystem are passed
 * in, not read — so the whole search is testable without an NDI install on the machine.
 */
object NdiRuntime {
    /** The env vars Vizrt documents, newest runtime first. */
    internal val RUNTIME_DIR_ENV_VARS = listOf("NDI_RUNTIME_DIR_V6", "NDI_RUNTIME_DIR_V5")

    /**
     * The shared library's file name on [osName].
     *
     * Linux is versioned and NDI 6 no longer ships the `.so.5` soname, so both are tried in order —
     * this is the exact assumption that left Devolay unable to load an NDI 6 runtime.
     */
    fun libraryFileNamesFor(osName: String): List<String> {
        val os = osName.lowercase()
        return when {
            os.contains("win") -> listOf("Processing.NDI.Lib.x64.dll")
            os.contains("mac") || os.contains("darwin") -> listOf("libndi.dylib")
            else -> listOf("libndi.so.6", "libndi.so.5", "libndi.so")
        }
    }

    /**
     * Where the runtime installs itself by default on [osName], given the environment it would have
     * been installed under. Empty when there is no conventional location to guess at.
     */
    fun defaultInstallDirsFor(osName: String, env: Map<String, String>): List<String> {
        val os = osName.lowercase()
        return when {
            os.contains("win") -> listOfNotNull(
                env["NDI_RUNTIME_DIR_V6"],
                env["ProgramFiles"]?.let { "$it\\NDI\\NDI 6 Runtime\\v6" },
                env["ProgramFiles"]?.let { "$it\\NDI\\NDI 5 Runtime\\v5" },
            )
            os.contains("mac") || os.contains("darwin") -> listOf(
                "/usr/local/lib",
                "/Library/NDI SDK for Apple/lib/macOS",
                "/opt/homebrew/lib",
            )
            else -> listOf("/usr/lib", "/usr/local/lib", "/usr/lib/x86_64-linux-gnu")
        }
    }

    /**
     * Every directory to look in, in priority order, for a runtime on [osName].
     *
     * [customPath] is the operator's override and comes first when it is set — the equivalent of
     * `ProjectionSettings.vlcPath`, and the escape hatch for an install this list does not know
     * about. Blank means they never set one.
     */
    fun searchDirsFor(osName: String, env: Map<String, String>, customPath: String = ""): List<String> {
        val custom = customPath.trim().takeIf { it.isNotEmpty() }
        val fromEnv = RUNTIME_DIR_ENV_VARS.mapNotNull { env[it]?.trim()?.takeIf(String::isNotEmpty) }
        return (listOfNotNull(custom) + fromEnv + defaultInstallDirsFor(osName, env)).distinct()
    }

    /**
     * The first NDI shared library found by walking [searchDirsFor], or null when none of them
     * holds one.
     *
     * [exists] is the filesystem, injected so the search itself can be tested against a made-up
     * one. The real caller passes [File::exists] — see [detect].
     */
    fun locate(
        osName: String,
        env: Map<String, String>,
        customPath: String = "",
        exists: (String) -> Boolean,
    ): String? {
        val fileNames = libraryFileNamesFor(osName)
        for (dir in searchDirsFor(osName, env, customPath)) {
            for (name in fileNames) {
                val candidate = dir.trimEnd('/', '\\') + File.separator + name
                if (exists(candidate)) return candidate
            }
        }
        return null
    }

    /** [locate] against the real environment and the real filesystem. */
    fun detect(customPath: String = ""): String? = locate(
        osName = System.getProperty("os.name").orEmpty(),
        env = System.getenv(),
        customPath = customPath,
        exists = { File(it).isFile },
    )
}
