package org.churchpresenter.updater

import java.awt.Desktop
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

/**
 * The native command that launches [path] on [osName] (as reported by the `os.name` system
 * property), for [launchInstaller].
 *
 * Deliberately avoids [Desktop.open], which on Windows rejects `.msi` files with
 * "Unsupported URI content". [ProcessBuilder] takes its arguments as a list, so
 * installer paths containing spaces need no shell quoting.
 */
internal fun installerLaunchCommand(osName: String, path: String): List<String> = when {
    // msiexec is the supported way to run an .msi; /i = install.
    osName.lowercase().contains("win") -> listOf("msiexec", "/i", path)
    // mounts the .dmg in Finder; same as what Desktop.open() does on mac.
    osName.lowercase().contains("mac") -> listOf("open", path)
    // .deb: hand to the desktop installer Desktop.open() delegates to on Linux.
    else -> listOf("xdg-open", path)
}

/** Launches the downloaded installer using the platform's native mechanism. */
private fun launchInstaller(file: File) {
    val command = installerLaunchCommand(System.getProperty("os.name", ""), file.absolutePath)
    ProcessBuilder(command).start()
}

/**
 * Runs the downloaded installer and quits, so it can replace the running app. Returns the error to
 * show when the installer could not be started, or null once [quit] has been asked to end the app.
 *
 * [launch] and [quit] are the two steps a test cannot take — one starts a native installer, the
 * other ends the JVM — handed in so the order and the failure handling around them can be driven.
 */
internal fun installAndQuit(
    file: File,
    launch: (File) -> Unit = ::launchInstaller,
    quit: () -> Unit = { exitProcess(0) },
): DownloadState.Error? = try {
    launch(file)
    quit()
    null
} catch (e: IOException) {
    DownloadState.Error(e.message ?: "Failed to launch installer")
} catch (e: SecurityException) {
    DownloadState.Error(e.message ?: "Failed to launch installer")
}
