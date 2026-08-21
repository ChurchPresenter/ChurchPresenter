package org.churchpresenter.settings.utils

import java.io.File

/**
 * Where the app keeps everything it persists — settings, the bundled Bible, presets.
 *
 * This is `~/.churchpresenter` on every machine where the home directory is usable, which is very
 * nearly all of them. It is not universally usable: a Windows profile that is redirected, roaming
 * or simply absent leaves `user.home` pointing at a path that cannot be created, and the first
 * thing to notice was the startup step that writes the bundled KJV out — it failed with a bare
 * `FileNotFoundException (The system cannot find the path specified)` and the user started with no
 * Bible at all. `mkdirs()` had already returned false unnoticed; the write is simply where the
 * missing directory finally surfaced.
 *
 * So the location is *resolved* rather than assumed: the first candidate that can actually be
 * created and written to wins, and the platform's own application-data folder stands behind the
 * home directory. Settings resolve through here too, so a machine that falls back keeps its
 * settings and its Bibles together and finds them again on the next launch — the resolution is
 * deterministic, not remembered anywhere.
 *
 * Nothing is cached: the result is recomputed per call so that a test swapping `user.home` is
 * honoured, rather than latching onto whichever directory the first caller happened to see.
 *
 * `WebsitePresenter` resolves its own root by the same principle for a different reason (it
 * prefers `%ProgramData%` for the JCEF cache), and `CrashReporter` deliberately latches its
 * directory once per JVM. Neither goes through here.
 */
object AppDataDir {

    /** The home-relative name, kept for the overwhelmingly common case. */
    private const val HOME_FOLDER = ".churchpresenter"

    /** The name used under a platform application-data folder, where a dotfile would be odd. */
    private const val APP_FOLDER = "ChurchPresenter"

    /**
     * The folder to persist into, created if it did not exist.
     *
     * Falls back to [homeCandidate] when nothing is usable, so a machine that can write nowhere
     * fails exactly as it did before — at the write, with the path it tried to use in the message.
     */
    fun resolve(
        homeDir: String? = System.getProperty("user.home"),
        osName: String = System.getProperty("os.name", ""),
        tempDir: String? = System.getProperty("java.io.tmpdir"),
        env: (String) -> String? = { System.getenv(it) }
    ): File {
        val candidates = candidates(homeDir, osName, tempDir, env)
        return candidates.firstOrNull { isUsable(it) } ?: candidates.first()
    }

    private fun candidates(
        homeDir: String?,
        osName: String,
        tempDir: String?,
        env: (String) -> String?
    ): List<File> {
        val home = homeCandidate(homeDir)
        val platform = if (osName.lowercase().contains("win")) {
            listOfNotNull(env("LOCALAPPDATA"), env("APPDATA"))
        } else {
            listOfNotNull(
                env("XDG_DATA_HOME"),
                homeDir?.let { File(it, ".local/share").path }
            )
        }
        val fallbacks = (platform + listOfNotNull(tempDir)).map { File(it, APP_FOLDER) }
        return listOf(home) + fallbacks
    }

    /** The preferred location, whether or not it can be created. */
    private fun homeCandidate(homeDir: String?): File = File(homeDir ?: ".", HOME_FOLDER)

    /**
     * Whether [directory] can be written to, creating it if needed.
     *
     * `canWrite` on its own is not enough — the directory has to exist first for the answer to
     * mean anything — and `mkdirs` on its own is not enough either, because a directory can be
     * created inside a folder the process may not write files into.
     */
    private fun isUsable(directory: File): Boolean = runCatching {
        directory.mkdirs()
        directory.isDirectory && directory.canWrite()
    }.getOrDefault(false)
}
