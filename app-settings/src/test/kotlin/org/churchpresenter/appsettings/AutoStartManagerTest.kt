package org.churchpresenter.appsettings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AutoStartManager] treats the OS registration as the source of truth. The platform decision, the
 * payload contents and the escaping are pure and tested directly; the file-based registration
 * (mac/linux) is exercised for real against the redirected test-home; the Windows registry path is
 * driven through an in-memory [AutoStartManager.WindowsRunKey] so its control flow is reachable off
 * Windows. Only the real registry binding itself stays uncovered — it can only run on Windows.
 */
class AutoStartManagerTest {

    // AutoStartManager resolves its plist/desktop paths from user.home at call time. Isolate it to a
    // per-test temp dir so registering/unregistering — and the cleanup below — never touch, or delete,
    // the developer's real launch-at-login files under ~/Library/LaunchAgents or ~/.config/autostart.
    private lateinit var homeBackup: String
    private lateinit var tempHome: File
    private lateinit var macPlist: File
    private lateinit var linuxDesktop: File

    @BeforeTest fun before() {
        homeBackup = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-autostart-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        macPlist = File(tempHome, "Library/LaunchAgents/org.churchpresenter.app.plist")
        linuxDesktop = File(tempHome, ".config/autostart/churchpresenter.desktop")
    }

    @AfterTest fun after() {
        System.setProperty("user.home", homeBackup)
        tempHome.deleteRecursively()
    }

    /** In-memory stand-in for the Windows `Run` key, so its branches run on any host. */
    private class FakeRunKey(var value: String? = null) : AutoStartManager.WindowsRunKey {
        override fun exists(): Boolean = value != null
        override fun read(): String? = value
        override fun write(value: String) { this.value = value }
        override fun delete() { value = null }
    }

    // ── unsupported (dev run) ────────────────────────────────────────────────────

    @Test
    fun `autostart is unsupported when not running from an installed app`() {
        assertNull(System.getProperty("jpackage.app-path"), "test precondition: running from Gradle")
        assertFalse(AutoStartManager.isSupported)
    }

    @Test
    fun `enabling is refused when unsupported, rather than half-registering`() {
        assertFalse(AutoStartManager.setEnabled(true), "must report failure, not silently do nothing")
        assertFalse(AutoStartManager.setEnabled(false))
    }

    @Test
    fun `querying the current state never throws`() {
        assertFalse(AutoStartManager.isEnabled())
    }

    @Test
    fun `syncRegistration is a no-op when unsupported`() {
        AutoStartManager.syncRegistration() // exePath is null under Gradle: returns without touching the OS
    }

    // ── platform decision & payloads (pure) ──────────────────────────────────────

    @Test
    fun `os name maps to the platform whose registration mechanism applies`() {
        assertEquals(AutoStartManager.Platform.WINDOWS, AutoStartManager.platformFor("Windows 11"))
        assertEquals(AutoStartManager.Platform.MAC, AutoStartManager.platformFor("Mac OS X"))
        assertEquals(AutoStartManager.Platform.LINUX, AutoStartManager.platformFor("Linux"))
        assertEquals(AutoStartManager.Platform.LINUX, AutoStartManager.platformFor("SunOS"), "unknown falls to XDG")
    }

    @Test
    fun `only windows lacks a backing file`() {
        assertNull(AutoStartManager.autostartFile(AutoStartManager.Platform.WINDOWS))
        assertTrue(AutoStartManager.autostartFile(AutoStartManager.Platform.MAC)!!.name.endsWith(".plist"))
        assertTrue(AutoStartManager.autostartFile(AutoStartManager.Platform.LINUX)!!.name.endsWith(".desktop"))
    }

    @Test
    fun `registration content is the payload for the platform`() {
        val exe = "/opt/CP/cp"
        assertEquals(
            AutostartEntries.windowsRunValue(exe),
            AutostartEntries.registrationContent(exe, AutoStartManager.Platform.WINDOWS),
        )
        assertEquals(
            AutostartEntries.macPlistContent(exe),
            AutostartEntries.registrationContent(exe, AutoStartManager.Platform.MAC),
        )
        assertEquals(
            AutostartEntries.linuxDesktopContent(exe),
            AutostartEntries.registrationContent(exe, AutoStartManager.Platform.LINUX),
        )
    }

    @Test
    fun `the windows run value quotes the launcher path`() {
        assertEquals(
            "\"C:\\Program Files\\CP\\CP.exe\"",
            AutostartEntries.windowsRunValue("C:\\Program Files\\CP\\CP.exe"),
        )
    }

    @Test
    fun `xml escaping neutralises every markup character`() {
        assertEquals("&amp;", AutostartEntries.escapeXml("&"))
        assertEquals("&lt;", AutostartEntries.escapeXml("<"))
        assertEquals("&gt;", AutostartEntries.escapeXml(">"))
        assertEquals("&quot;", AutostartEntries.escapeXml("\""))
        assertEquals("&apos;", AutostartEntries.escapeXml("'"))
        // Ampersand must be escaped first, or the '&' introduced for '<' would itself be re-escaped.
        assertEquals("&amp;lt;", AutostartEntries.escapeXml("&lt;"))
    }

    @Test
    fun `exec escaping backslash-escapes each freedesktop reserved character`() {
        assertEquals("\\\\", AutostartEntries.escapeExec("\\"))
        assertEquals("\\\"", AutostartEntries.escapeExec("\""))
        assertEquals("\\\$", AutostartEntries.escapeExec("\$"))
        assertEquals("\\`", AutostartEntries.escapeExec("`"))
        // Backslash first, or the backslashes added for the others would themselves be doubled.
        assertEquals("\\\\\\\"", AutostartEntries.escapeExec("\\\""))
    }

    @Test
    fun `the mac plist embeds the launcher path xml-escaped and runs at load`() {
        val plist = AutostartEntries.macPlistContent("/Apps/Church & Co/CP")
        assertTrue(plist.startsWith("<?xml"))
        assertTrue("<key>RunAtLoad</key>" in plist)
        assertTrue("<true/>" in plist)
        assertTrue("Church &amp; Co" in plist, "an & in the path must be escaped")
        assertFalse("Church & Co" in plist, "no raw ampersand may reach the plist")
    }

    @Test
    fun `the linux desktop entry escapes the exec path and enables autostart`() {
        val entry = AutostartEntries.linuxDesktopContent("/opt/\$weird/cp")
        assertTrue("[Desktop Entry]" in entry)
        assertTrue("Type=Application" in entry)
        assertTrue("X-GNOME-Autostart-enabled=true" in entry)
        assertTrue("Exec=\"/opt/\\\$weird/cp\"" in entry, "a \$ in the path must be escaped inside Exec")
    }

    // ── file-based registration (mac/linux), against the redirected test-home ─────

    private fun fileFlowIsSourceOfTruth(platform: AutoStartManager.Platform, file: File) {
        val exe = "/opt/ChurchPresenter/bin/ChurchPresenter"
        assertFalse(AutoStartManager.isEnabledFor(platform), "starts unregistered")

        assertTrue(AutoStartManager.setEnabledFor(exe, platform, enabled = true), "register reports success")
        assertTrue(file.exists(), "registration writes the backing file")
        assertEquals(AutostartEntries.registrationContent(exe, platform), file.readText(), "writes the exact payload")
        assertTrue(AutoStartManager.isEnabledFor(platform), "now reported enabled")

        assertTrue(AutoStartManager.setEnabledFor(exe, platform, enabled = false), "unregister reports success")
        assertFalse(file.exists(), "unregister removes the file")
        assertFalse(AutoStartManager.isEnabledFor(platform))
    }

    @Test
    fun `the mac plist is the source of truth across register and unregister`() {
        fileFlowIsSourceOfTruth(AutoStartManager.Platform.MAC, macPlist)
    }

    @Test
    fun `the linux desktop entry is the source of truth across register and unregister`() {
        fileFlowIsSourceOfTruth(AutoStartManager.Platform.LINUX, linuxDesktop)
    }

    @Test
    fun `sync does nothing when autostart is not enabled`() {
        AutoStartManager.syncRegistrationFor("/opt/CP/cp", AutoStartManager.Platform.LINUX)
        assertFalse(linuxDesktop.exists(), "an unregistered app must not be registered by a sync")
    }

    @Test
    fun `sync leaves a matching registration untouched`() {
        val exe = "/opt/CP/cp"
        AutoStartManager.setEnabledFor(exe, AutoStartManager.Platform.MAC, enabled = true)
        val before = macPlist.readText()
        AutoStartManager.syncRegistrationFor(exe, AutoStartManager.Platform.MAC)
        assertEquals(before, macPlist.readText(), "matching content must not be rewritten")
    }

    @Test
    fun `sync rewrites a stale registration to the new launcher path`() {
        val mac = AutoStartManager.Platform.MAC
        AutoStartManager.setEnabledFor("/old/location/cp", mac, enabled = true)
        AutoStartManager.syncRegistrationFor("/new/location/cp", mac)
        assertEquals(AutostartEntries.registrationContent("/new/location/cp", mac), macPlist.readText())
    }

    @Test
    fun `a failed registration is swallowed into a false result, never thrown`() {
        // A directory sitting at the plist's path makes writeText fail: register throws, the failure
        // is reported and turns into false (the toggle stays off) instead of propagating to the UI.
        macPlist.parentFile?.mkdirs()
        macPlist.mkdir()
        assertFalse(AutoStartManager.setEnabledFor("/opt/CP/cp", AutoStartManager.Platform.MAC, enabled = true))
    }

    @Test
    fun `sync survives an unreadable registration without throwing`() {
        // A directory at the plist path "exists" (so sync proceeds) but can neither be read nor
        // rewritten; both failures must be swallowed rather than propagated to startup.
        macPlist.parentFile?.mkdirs()
        macPlist.mkdir()
        AutoStartManager.syncRegistrationFor("/opt/CP/cp", AutoStartManager.Platform.MAC)
    }

    // ── windows registration, driven through the in-memory run key ────────────────

    @Test
    fun `on windows the registry value is the source of truth across register and unregister`() {
        val key = FakeRunKey()
        val exe = "C:\\Program Files\\CP\\CP.exe"
        val win = AutoStartManager.Platform.WINDOWS

        assertFalse(AutoStartManager.isEnabledFor(win, key))
        assertTrue(AutoStartManager.setEnabledFor(exe, win, enabled = true, runKey = key))
        assertEquals(AutostartEntries.windowsRunValue(exe), key.value, "writes the quoted launcher path")
        assertTrue(AutoStartManager.isEnabledFor(win, key))

        assertTrue(AutoStartManager.setEnabledFor(exe, win, enabled = false, runKey = key))
        assertNull(key.value, "unregister clears the value")
        assertFalse(AutoStartManager.isEnabledFor(win, key))
    }

    @Test
    fun `on windows unregister is a no-op when nothing is registered`() {
        val key = FakeRunKey()
        assertTrue(AutoStartManager.setEnabledFor(
            "C:\\CP.exe",
            AutoStartManager.Platform.WINDOWS,
            enabled = false,
            runKey = key,
        ))
        assertNull(key.value)
    }

    @Test
    fun `on windows sync rewrites a stale registry value but leaves a matching one`() {
        val key = FakeRunKey()
        val win = AutoStartManager.Platform.WINDOWS
        AutoStartManager.setEnabledFor("C:\\old\\CP.exe", win, enabled = true, runKey = key)

        val matching = key.value
        AutoStartManager.syncRegistrationFor("C:\\old\\CP.exe", win, key)
        assertEquals(matching, key.value, "matching value must not be rewritten")

        AutoStartManager.syncRegistrationFor("C:\\new\\CP.exe", win, key)
        assertEquals(AutostartEntries.windowsRunValue("C:\\new\\CP.exe"), key.value, "stale value re-registered")
    }

    // ── a broken JNA native library is "unavailable", not fatal (CHURCH-PRESENTER-DESKTOP-6D/-6E) ─

    /** A run key whose [exists] fails the way a broken JNA native dispatch library does. */
    private class ThrowingRunKey(private val failure: Throwable) : AutoStartManager.WindowsRunKey {
        override fun exists(): Boolean = throw failure
        override fun read(): String? = throw failure
        override fun write(value: String) = throw failure
        override fun delete() = throw failure
    }

    @Test
    fun `isEnabledFor swallows an UnsatisfiedLinkError from a broken native library, same as an Exception`() {
        val key = ThrowingRunKey(UnsatisfiedLinkError("Failed to create temporary file for jnidispatch.dll"))
        assertFalse(AutoStartManager.isEnabledFor(AutoStartManager.Platform.WINDOWS, key))
    }

    @Test
    fun `isEnabledFor swallows a NoClassDefFoundError once JNA has already failed to init`() {
        val key = ThrowingRunKey(NoClassDefFoundError("Could not initialize class com.sun.jna.Native"))
        assertFalse(AutoStartManager.isEnabledFor(AutoStartManager.Platform.WINDOWS, key))
    }

    @Test
    fun `setEnabledFor reports failure instead of crashing when the native library is broken`() {
        val key = ThrowingRunKey(UnsatisfiedLinkError("jnidispatch.dll missing"))
        assertFalse(AutoStartManager.setEnabledFor(
            "C:\\CP.exe",
            AutoStartManager.Platform.WINDOWS,
            enabled = true,
            runKey = key,
        ))
    }

    @Test
    fun `syncRegistrationFor does not propagate a broken native library`() {
        val key = ThrowingRunKey(NoClassDefFoundError("Could not initialize class com.sun.jna.Native"))
        // Must return normally rather than throw — this runs on a background thread at startup
        // whose uncaught exception would otherwise take the whole app down.
        AutoStartManager.syncRegistrationFor("C:\\CP.exe", AutoStartManager.Platform.WINDOWS, key)
    }
}
