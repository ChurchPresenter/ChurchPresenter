package org.churchpresenter.omt

import java.io.File

/**
 * Where `libomt` is, if it is anywhere.
 *
 * **Unlike NDI, the app ships it.** `libomt` and `libvmx` are MIT, so the installer carries both —
 * fetched and pinned at build time the way ffmpeg is — and an ordinary install needs nothing done
 * to it. The search still exists for three reasons: `run` from a checkout that never fetched them,
 * an operator who wants a newer build than the one bundled, and a platform the bundle does not
 * cover.
 *
 * Search order, highest priority first:
 *  1. the operator's explicit path from settings, if they set one;
 *  2. the directory the app's bundled copy lives in;
 *  3. the platform's conventional library directories, for a copy installed system-wide.
 *
 * Pure over its arguments — the environment and the filesystem are passed in — so the whole search
 * is testable with nothing installed.
 */
object OmtRuntime {
    /** The shared library's file name on [osName]. */
    fun libraryFileNameFor(osName: String): String {
        val os = osName.lowercase()
        return when {
            // macOS first: "darwin" contains "win".
            os.contains("mac") || os.contains("darwin") -> "libomt.dylib"
            os.contains("win") -> "libomt.dll"
            else -> "libomt.so"
        }
    }

    /** Where a system-wide copy would conventionally be on [osName]. Empty on Windows, which has none. */
    fun systemDirsFor(osName: String): List<String> {
        val os = osName.lowercase()
        return when {
            os.contains("mac") || os.contains("darwin") -> listOf("/usr/local/lib", "/opt/homebrew/lib")
            os.contains("win") -> emptyList()
            else -> listOf("/usr/local/lib", "/usr/lib", "/usr/lib/x86_64-linux-gnu", "/usr/lib/aarch64-linux-gnu")
        }
    }

    /**
     * Every directory to look in, in priority order.
     *
     * [customPath] is the operator's override; [bundledDir] is where the app's own copy is, or blank
     * when this run has none — a checkout that never fetched it, or a test. A [customPath] that names
     * the library file rather than its directory is accepted as its directory.
     */
    fun searchDirsFor(osName: String, customPath: String = "", bundledDir: String = ""): List<String> {
        val custom = customPath.trim().takeIf { it.isNotEmpty() }?.let { path ->
            if (path.endsWith(libraryFileNameFor(osName))) path.dropLast(libraryFileNameFor(osName).length) else path
        }
        val bundled = bundledDir.trim().takeIf { it.isNotEmpty() }
        return (listOfNotNull(custom, bundled) + systemDirsFor(osName)).distinct()
    }

    /**
     * The first `libomt` found by walking [searchDirsFor], or null when none of them holds one.
     *
     * [exists] is the filesystem, injected so the search can be tested against a made-up one.
     */
    fun locate(
        osName: String,
        customPath: String = "",
        bundledDir: String = "",
        exists: (String) -> Boolean,
    ): String? {
        val name = libraryFileNameFor(osName)
        return searchDirsFor(osName, customPath, bundledDir)
            .map { it.trimEnd('/', '\\') + File.separator + name }
            .firstOrNull(exists)
    }

    /** [locate] against the real platform and the real filesystem. */
    fun detect(customPath: String = "", bundledDir: String = ""): String? = locate(
        osName = System.getProperty("os.name").orEmpty(),
        customPath = customPath,
        bundledDir = bundledDir,
        exists = { File(it).isFile },
    )
}
