package org.churchpresenter.media.composables

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.churchpresenter.sharedui.utils.CommandRunner
import org.churchpresenter.sharedui.utils.readCommandOutput

/** If a custom VLC path is set and valid, adds it to jna.library.path so VLCJ/JNA can find native libs. */
internal fun applyCustomVlcPath() {
    if (vlcCustomPath.isBlank()) return
    val dir = File(vlcCustomPath)
    if (!dir.isDirectory) return
    val current = System.getProperty("jna.library.path", "")
    if (current.contains(vlcCustomPath)) return
    val newPath = if (current.isBlank()) vlcCustomPath else "$vlcCustomPath${File.pathSeparator}$current"
    System.setProperty("jna.library.path", newPath)
}

/** Checks whether a directory contains a VLC native library (libvlc.dll / .so* / .dylib). */
internal fun dirContainsVlcLib(dir: Path): Boolean {
    if (!Files.isDirectory(dir)) return false
    return try {
        Files.list(dir).use { stream ->
            stream.anyMatch { path ->
                val name = path.fileName.toString()
                name == "libvlc.dll" || name == "libvlc.dylib" ||
                        name == "libvlc.so" || (name.startsWith("libvlc.so.") && !name.startsWith("libvlccore"))
            }
        }
    } catch (_: Exception) { false }
}

/** Returns the auto-detected VLC installation directory, or empty string if not found. */
fun detectVlcInstallPath(): String = detectVlcInstallPathFor(System.getProperty("os.name", "").lowercase())

/**
 * The auto-detected VLC directory for [osName], or empty when VLC isn't in any of the usual places.
 *
 * [osName] is a parameter rather than read from `os.name` here so a test can walk all three platforms'
 * candidate lists without swapping the system property — skiko latches that JVM-wide and would take
 * every later Compose test in the same JVM down with it.
 *
 * The macOS branch is the one that does not simply return the first hit: when `VLC.app` is present but
 * its `MacOS/lib` holds no libvlc, the bundle root is returned anyway, because that is still where the
 * user installed VLC and JNA may yet find the library through it.
 */
internal fun detectVlcInstallPathFor(
    osName: String,
    /**
     * Whether a directory holds libvlc. A parameter for the same reason [osName] is one: left
     * reading the real filesystem, every assertion below becomes a statement about whether the
     * developer happens to have VLC installed. `detectVlcInstallPath finds nothing on a forced
     * Windows OS name` asserted exactly that and failed on any machine with VLC in Program Files.
     */
    hasVlcLib: (Path) -> Boolean = ::dirContainsVlcLib,
    /** Whether a path exists at all — only the macOS bundle fallback needs it. */
    pathExists: (Path) -> Boolean = Files::exists,
): String {
    return when {
        "win" in osName -> {
            val paths = listOfNotNull(
                System.getenv("VLC_PLUGIN_PATH")?.let { Paths.get(it).parent },
                Paths.get(System.getenv("ProgramFiles") ?: "C:\\Program Files", "VideoLAN", "VLC"),
                Paths.get(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "VideoLAN", "VLC")
            )
            paths.firstOrNull { hasVlcLib(it) }?.toString() ?: ""
        }
        "mac" in osName || "darwin" in osName -> {
            val libPath = Paths.get("/Applications/VLC.app/Contents/MacOS/lib")
            if (hasVlcLib(libPath)) libPath.toString()
            else if (pathExists(Paths.get("/Applications/VLC.app"))) "/Applications/VLC.app"
            else ""
        }
        else -> {
            val libDirs = listOf(
                Paths.get("/usr/lib"),
                Paths.get("/usr/lib64"),
                Paths.get("/usr/lib/x86_64-linux-gnu"),
                Paths.get("/usr/lib/aarch64-linux-gnu"),
                Paths.get("/snap/vlc/current/usr/lib")
            )
            libDirs.firstOrNull { hasVlcLib(it) }?.toString() ?: ""
        }
    }
}

/** Checks common installation paths for the VLC native library on each OS. */
internal fun isVlcInstalledOnSystem(): Boolean =
    vlcInstalledOn(System.getProperty("os.name", "").lowercase(), vlcCustomPath, ::readCommandOutput)

/**
 * Whether VLC is installed, given the OS, the user's configured [customPath] and a way to run
 * `which`.
 *
 * The custom path is consulted before the well-known ones so a deliberately chosen install always
 * wins over whatever else happens to be on the machine. `which vlc` is a Linux-only last resort:
 * it proves the *player* is on PATH, not that libvlc is anywhere JNA will look, so it is worth
 * trying only where distributions reliably ship the two together.
 */
internal fun vlcInstalledOn(osName: String, customPath: String, run: CommandRunner): Boolean {
    val custom = customPath.takeIf { it.isNotBlank() }
        ?.let { try { Paths.get(it) } catch (_: Exception) { null } }
    if (custom != null && dirContainsVlcLib(custom)) return true
    if (detectVlcInstallPathFor(osName).isNotBlank()) return true
    val unixLike = "win" !in osName && "mac" !in osName && "darwin" !in osName
    return unixLike && run(listOf("which", "vlc"), 0L).exitCode == 0
}
