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
 */
object AutoStartManager {

    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val APP_NAME = "ChurchPresenter"

    /** Set by jpackage to the installed launcher path; null when running from Gradle/IDE. */
    private val exePath: String? = System.getProperty("jpackage.app-path")

    private val osName = System.getProperty("os.name", "").lowercase()
    private val isWindows = osName.contains("win")
    private val isMac = osName.contains("mac")

    /** Autostart can only be registered for the installed app, not a dev run. */
    val isSupported: Boolean get() = exePath != null

    private val macPlistFile: File
        get() = File(System.getProperty("user.home"), "Library/LaunchAgents/org.churchpresenter.app.plist")

    private val linuxDesktopFile: File
        get() = File(System.getProperty("user.home"), ".config/autostart/churchpresenter.desktop")

    fun isEnabled(): Boolean = try {
        when {
            isWindows -> Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)
            isMac -> macPlistFile.exists()
            else -> linuxDesktopFile.exists()
        }
    } catch (_: Exception) {
        false
    }

    fun setEnabled(enabled: Boolean): Boolean {
        val exe = exePath ?: return false
        return try {
            if (enabled) register(exe) else unregister()
            true
        } catch (e: Exception) {
            CrashReporter.reportException(e, context = "AutoStartManager.setEnabled($enabled)")
            false
        }
    }

    /**
     * Repairs a stale registration after the install location changes (update,
     * reinstall to another path). Idempotent; call once on startup.
     */
    fun syncRegistration() {
        val exe = exePath ?: return
        try {
            if (!isEnabled()) return
            val current = when {
                // Value removed or retyped between the checks (Task Manager, cleaner
                // apps) is an expected race, not a crash — just re-register
                isWindows -> try {
                    Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)
                } catch (_: Exception) { null }
                isMac -> try { macPlistFile.readText() } catch (_: Exception) { null }
                else -> try { linuxDesktopFile.readText() } catch (_: Exception) { null }
            }
            if (current == registrationContent(exe)) return
            register(exe)
        } catch (e: Exception) {
            CrashReporter.reportException(e, context = "AutoStartManager.syncRegistration")
        }
    }

    /** The exact registration payload per platform — registry value or file content. */
    private fun registrationContent(exe: String): String = when {
        isWindows -> windowsRunValue(exe)
        isMac -> macPlistContent(exe)
        else -> linuxDesktopContent(exe)
    }

    private fun register(exe: String) {
        when {
            isWindows -> Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME, windowsRunValue(exe))
            isMac -> macPlistFile.apply { parentFile?.mkdirs() }.writeText(macPlistContent(exe))
            else -> linuxDesktopFile.apply { parentFile?.mkdirs() }.writeText(linuxDesktopContent(exe))
        }
    }

    private fun windowsRunValue(exe: String): String = "\"$exe\""

    private fun macPlistContent(exe: String): String = """
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

    private fun linuxDesktopContent(exe: String): String = """
        [Desktop Entry]
        Type=Application
        Name=$APP_NAME
        Exec="${escapeExec(exe)}"
        X-GNOME-Autostart-enabled=true
        """.trimIndent()

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /** Escapes reserved characters inside a quoted Exec value per the freedesktop spec. */
    private fun escapeExec(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("$", "\\$")
        .replace("`", "\\`")

    private fun unregister() {
        when {
            isWindows -> {
                if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)) {
                    Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)
                }
            }
            isMac -> macPlistFile.delete()
            else -> linuxDesktopFile.delete()
        }
    }
}
