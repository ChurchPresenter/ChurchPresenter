package org.churchpresenter.appsettings

import org.churchpresenter.appsettings.AutoStartManager.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.updater.StorePackage

/**
 * Registers/unregisters the app to launch automatically when the user logs in.
 *
 * The OS registration (registry value / LaunchAgent plist / XDG autostart entry)
 * is the source of truth — no flag is persisted in the app settings, so the
 * toggle always reflects reality even if the user removes the entry externally
 * (e.g. via Task Manager's Startup tab).
 *
 * The platform decision, the payload contents and the escaping are pure functions so they can be
 * tested directly; the only genuinely platform-bound step is the Windows registry, reached through
 * [WindowsRunKey] so tests can drive its control flow with an in-memory stand-in off Windows.
 */
object AutoStartManager {

    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    internal const val APP_NAME = "ChurchPresenter"

    internal enum class Platform { WINDOWS, MAC, LINUX }

    /**
     * Set by jpackage to the installed launcher path; null when running from Gradle/IDE, and for a
     * Store install, whose virtualized Run key would never be read at login (its manifest declares
     * a startup task instead).
     */
    private val exePath: String? = launcherPath(System.getProperty("jpackage.app-path"), StorePackage.isInstalled)

    internal fun launcherPath(appPath: String?, storeInstall: Boolean): String? =
        appPath?.takeUnless { storeInstall }

    /** Maps an `os.name` string to the platform whose registration mechanism applies. */
    internal fun platformFor(osName: String): Platform {
        val n = osName.lowercase()
        return when {
            n.contains("win") -> Platform.WINDOWS
            n.contains("mac") -> Platform.MAC
            else -> Platform.LINUX
        }
    }

    private val currentPlatform: Platform = platformFor(System.getProperty("os.name", ""))

    /** Autostart can only be registered for the installed app, not a dev run. */
    val isSupported: Boolean get() = exePath != null

    private val macPlistFile: File
        get() = File(System.getProperty("user.home"), "Library/LaunchAgents/org.churchpresenter.app.plist")

    private val linuxDesktopFile: File
        get() = File(System.getProperty("user.home"), ".config/autostart/churchpresenter.desktop")

    /** The file backing autostart on a file-based platform (mac/linux); null on Windows (registry). */
    internal fun autostartFile(platform: Platform): File? = when (platform) {
        Platform.MAC -> macPlistFile
        Platform.LINUX -> linuxDesktopFile
        Platform.WINDOWS -> null
    }

    /** The Windows `Run` registry value, isolated so its control flow is reachable off Windows. */
    internal interface WindowsRunKey {
        fun exists(): Boolean
        fun read(): String?
        fun write(value: String)
        fun delete()
    }

    private val realRunKey = object : WindowsRunKey {
        override fun exists(): Boolean =
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)

        override fun read(): String? =
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)

        override fun write(value: String) =
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME, value)

        override fun delete() =
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)
    }

    fun isEnabled(): Boolean = isEnabledFor(currentPlatform)

    // Throwable, not Exception: a missing/broken JNA native dispatch library (jnidispatch.dll
    // failing to extract, or a prior failed Native.<clinit> leaving NoClassDefFoundError on every
    // later call) throws UnsatisfiedLinkError/NoClassDefFoundError, both Errors — an unreachable
    // registry is "autostart unavailable", not a reason to bring the whole app down (Sentry
    // CHURCH-PRESENTER-DESKTOP-6D, -6E).
    internal fun isEnabledFor(platform: Platform, runKey: WindowsRunKey = realRunKey): Boolean = try {
        val file = autostartFile(platform)
        if (file != null) file.exists() else runKey.exists()
    } catch (_: Throwable) {
        false
    }

    fun setEnabled(enabled: Boolean): Boolean {
        val exe = exePath ?: return false
        return setEnabledFor(exe, currentPlatform, enabled)
    }

    internal fun setEnabledFor(
        exe: String,
        platform: Platform,
        enabled: Boolean,
        runKey: WindowsRunKey = realRunKey,
    ): Boolean = try {
        if (enabled) AutostartEntries.register(exe, platform, runKey) else AutostartEntries.unregister(platform, runKey)
        true
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        CrashReporter.reportException(e, context = "AutoStartManager.setEnabled($enabled)")
        false
    }

    /**
     * Repairs a stale registration after the install location changes (update,
     * reinstall to another path). Idempotent; call once on startup.
     */
    fun syncRegistration() {
        val exe = exePath ?: return
        syncRegistrationFor(exe, currentPlatform)
    }

    internal fun syncRegistrationFor(exe: String, platform: Platform, runKey: WindowsRunKey = realRunKey) {
        try {
            if (!isEnabledFor(platform, runKey)) return
            // Value/file removed or retyped between the checks (Task Manager, cleaner apps) is an
            // expected race, not a crash — just re-register.
            val registered = AutostartEntries.readRegistration(platform, runKey)
            if (registered == AutostartEntries.registrationContent(exe, platform)) return
            AutostartEntries.register(exe, platform, runKey)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            CrashReporter.reportException(e, context = "AutoStartManager.syncRegistration")
        }
    }

    /** The currently-stored registration payload, or null if absent/removed/unreadable. */

}

/**
 * What autostart registers on each platform — the Run value, the LaunchAgent plist, the .desktop
 * entry — and writing, reading and removing it.
 */
internal object AutostartEntries {
    internal fun readRegistration(platform: Platform, runKey: AutoStartManager.WindowsRunKey): String? {
        val file = AutoStartManager.autostartFile(platform)
        return try {
            if (file != null) file.readText() else runKey.read()
        } catch (_: Throwable) {
            null
        }
    }

    internal fun register(exe: String, platform: Platform, runKey: AutoStartManager.WindowsRunKey) {
        val file = AutoStartManager.autostartFile(platform)
        if (file != null) file.apply { parentFile?.mkdirs() }.writeText(registrationContent(exe, platform))
        else runKey.write(AutostartEntries.windowsRunValue(exe))
    }

    internal fun unregister(platform: Platform, runKey: AutoStartManager.WindowsRunKey) {
        val file = AutoStartManager.autostartFile(platform)
        if (file != null) file.delete()
        else if (runKey.exists()) runKey.delete()
    }

    /** The exact registration payload per platform — registry value or file content. */
    internal fun registrationContent(exe: String, platform: Platform): String = when (platform) {
        Platform.WINDOWS -> windowsRunValue(exe)
        Platform.MAC -> macPlistContent(exe)
        Platform.LINUX -> linuxDesktopContent(exe)
    }

    internal fun windowsRunValue(exe: String): String = "\"$exe\""

    internal fun macPlistContent(exe: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>Label</key>
            <string>org.churchpresenter.app</string>
            <key>ProgramArguments</key>
            <array>
                <string>${escapeXml(exe)}</string>
            </array>
            <key>RunAtLoad</key>
            <true/>
        </dict>
        </plist>
        """.trimIndent()

    internal fun linuxDesktopContent(exe: String): String = """
        [Desktop Entry]
        Type=Application
        Name=${AutoStartManager.APP_NAME}
        Exec="${escapeExec(exe)}"
        X-GNOME-Autostart-enabled=true
        """.trimIndent()

    internal fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /** Escapes reserved characters inside a quoted Exec value per the freedesktop spec. */
    internal fun escapeExec(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("$", "\\$")
        .replace("`", "\\`")
}
