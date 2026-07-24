package org.churchpresenter.app.churchpresenter.utils

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

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
    private const val APP_NAME = "ChurchPresenter"

    internal enum class Platform { WINDOWS, MAC, LINUX }

    /** Set by jpackage to the installed launcher path; null when running from Gradle/IDE. */
    private val exePath: String? = System.getProperty("jpackage.app-path")

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

    internal fun isEnabledFor(platform: Platform, runKey: WindowsRunKey = realRunKey): Boolean = try {
        val file = autostartFile(platform)
        if (file != null) file.exists() else runKey.exists()
    } catch (_: Exception) {
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
        if (enabled) register(exe, platform, runKey) else unregister(platform, runKey)
        true
    } catch (e: Exception) {
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
            if (readRegistration(platform, runKey) == registrationContent(exe, platform)) return
            register(exe, platform, runKey)
        } catch (e: Exception) {
            CrashReporter.reportException(e, context = "AutoStartManager.syncRegistration")
        }
    }

    /** The currently-stored registration payload, or null if absent/removed/unreadable. */
    private fun readRegistration(platform: Platform, runKey: WindowsRunKey): String? {
        val file = autostartFile(platform)
        return try {
            if (file != null) file.readText() else runKey.read()
        } catch (_: Exception) {
            null
        }
    }

    /** The exact registration payload per platform — registry value or file content. */
    internal fun registrationContent(exe: String, platform: Platform): String = when (platform) {
        Platform.WINDOWS -> windowsRunValue(exe)
        Platform.MAC -> macPlistContent(exe)
        Platform.LINUX -> linuxDesktopContent(exe)
    }

    private fun register(exe: String, platform: Platform, runKey: WindowsRunKey) {
        val file = autostartFile(platform)
        if (file != null) file.apply { parentFile?.mkdirs() }.writeText(registrationContent(exe, platform))
        else runKey.write(windowsRunValue(exe))
    }

    private fun unregister(platform: Platform, runKey: WindowsRunKey) {
        val file = autostartFile(platform)
        if (file != null) file.delete()
        else if (runKey.exists()) runKey.delete()
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
        Name=$APP_NAME
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
