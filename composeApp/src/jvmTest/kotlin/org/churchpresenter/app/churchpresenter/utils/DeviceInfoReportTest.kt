package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings
import org.churchpresenter.app.churchpresenter.data.settings.InstanceLinkSettings
import org.churchpresenter.app.churchpresenter.data.settings.OBSSettings
import org.churchpresenter.app.churchpresenter.data.settings.ServerSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DeviceInfoReport] exists to be pasted into a public GitHub issue, so its real contract is a
 * PRIVACY one: booleans and counts only — never a hostname, port, password or API key. A future
 * edit that helpfully interpolates `settings.atemSettings.host` into the report would leak a
 * user's network layout into a public bug tracker, and nothing else would catch it.
 */
class DeviceInfoReportTest {

    /** Every secret-bearing field filled with a value distinctive enough to grep the output for. */
    private val settingsWithSecrets = AppSettings(
        atemSettings = AtemSettings(host = "atem-secret-host.example.internal", port = 19910),
        obsSettings = OBSSettings(
            enabled = true,
            host = "obs-secret-host.example.internal",
            port = 14455,
            password = "obs-secret-password",
        ),
        serverSettings = ServerSettings(
            enabled = true,
            port = 18080,
            apiKeyEnabled = true,
            apiKey = "server-secret-apikey",
        ),
        instanceLink = InstanceLinkSettings(enabled = true, apiKey = "link-secret-apikey"),
    )

    private fun report(): String = DeviceInfoReport.generate(settingsWithSecrets)

    @Test
    fun `no secret value from settings appears anywhere in the report`() {
        val text = report()
        for (secret in listOf(
            "atem-secret-host.example.internal",
            "obs-secret-host.example.internal",
            "obs-secret-password",
            "server-secret-apikey",
            "link-secret-apikey",
        )) {
            assertFalse(secret in text, "report leaked \"$secret\":\n$text")
        }
    }

    @Test
    fun `configured ports are not printed either`() {
        val text = report()
        // Deliberately non-default ports, so a match cannot be coincidental.
        for (port in listOf("19910", "14455", "18080")) {
            assertFalse(port in text, "report leaked port $port")
        }
    }

    @Test
    fun `integrations are reported as state, not as configuration`() {
        val text = report()
        assertTrue("ATEM: configured" in text, "a set ATEM host should read as 'configured'")
        assertTrue("OBS: enabled" in text)
        assertTrue("Companion server: enabled" in text)
        assertTrue("Instance Link: enabled" in text)
    }

    @Test
    fun `an unconfigured ATEM reads as not configured`() {
        val text = DeviceInfoReport.generate(AppSettings())
        assertTrue("ATEM: not configured" in text)
        assertTrue("OBS: disabled" in text)
        assertTrue("Companion server: disabled" in text)
        assertTrue("Instance Link: disabled" in text)
    }

    @Test
    fun `every expected section is present`() {
        val text = report()
        for (section in listOf(
            "=== ChurchPresenter Diagnostic Report ===",
            "-- App --", "-- System --", "-- Displays --", "-- DeckLink --",
            "-- Video / Web --", "-- Libraries --", "-- Outputs & Integrations --",
        )) {
            assertTrue(section in text, "missing section: $section")
        }
    }

    @Test
    fun `system facts are filled in rather than left unknown`() {
        val text = report()
        assertTrue("CPU cores: " in text)
        assertTrue(Regex("""CPU cores: [1-9]\d*""").containsMatchIn(text), "cores should be a real count")
        assertTrue(Regex("""Memory: \d+MB used / \d+MB max heap""").containsMatchIn(text))
        assertTrue(Regex("""Java: \d+""").containsMatchIn(text), "java.version should resolve")
    }

    @Test
    fun `a headless machine reports displays as unenumerable rather than crashing`() {
        // The test JVM is headless, which is the same situation as a server/CI machine.
        val text = report()
        assertTrue("-- Displays --" in text)
        assertTrue("(unable to enumerate)" in text, "headless display enumeration should degrade, not throw")
    }

    @Test
    fun `generation is deterministic apart from the timestamp and memory figures`() {
        val a = report().lines().filterNot { it.startsWith("Generated:") || it.startsWith("Memory:") }
        val b = report().lines().filterNot { it.startsWith("Generated:") || it.startsWith("Memory:") }
        assertEquals(a, b)
    }

    private val fixedTime = java.time.LocalDateTime.of(2026, 1, 1, 9, 30)

    private fun facts(
        screens: List<String> = emptyList(),
        deckLinkAvailable: Boolean = false,
        deckLinkDevices: List<String> = emptyList(),
        vlcAvailable: Boolean = false,
        vlcReason: String = "",
        jcefInitialized: Boolean = false,
        jcefMacUnsupported: Boolean = false,
        songFolderCount: Int = 0,
        totalSongs: Int = 0,
        bibleCount: Int = 0,
        analyticsEnabled: Boolean = false,
    ) = DeviceInfoReport.DeviceFacts(
        appVersion = "1.2.3", buildType = "release", installId = "inst-id",
        didCrashLastRun = false, consecutiveCrashes = 0, videoBackgroundsDisabled = false,
        screens = screens, deckLinkAvailable = deckLinkAvailable, deckLinkDevices = deckLinkDevices,
        vlcAvailable = vlcAvailable, vlcReason = vlcReason,
        jcefInitialized = jcefInitialized, jcefMacUnsupported = jcefMacUnsupported,
        songFolderCount = songFolderCount, totalSongs = totalSongs, bibleCount = bibleCount,
        analyticsEnabled = analyticsEnabled,
    )

    private fun render(f: DeviceInfoReport.DeviceFacts) = DeviceInfoReport.render(AppSettings(), f, fixedTime)

    @Test
    fun `present displays are listed instead of the unenumerable message`() {
        val text = render(facts(screens = listOf("  1. 1920x1080 @60Hz (primary)", "  2. 1280x720 @30Hz")))
        assertTrue("  1. 1920x1080 @60Hz (primary)" in text)
        assertTrue("  2. 1280x720 @30Hz" in text)
        assertFalse("(unable to enumerate)" in text)
    }

    @Test
    fun `an available deckLink lists its devices`() {
        val text = render(facts(deckLinkAvailable = true, deckLinkDevices = listOf("  1. DeckLink Mini")))
        assertTrue("Driver available: true" in text)
        assertTrue("  1. DeckLink Mini" in text)
        assertFalse("(no devices detected)" in text)
    }

    @Test
    fun `an available deckLink with no devices says so`() {
        val text = render(facts(deckLinkAvailable = true, deckLinkDevices = emptyList()))
        assertTrue("Driver available: true" in text)
        assertTrue("  (no devices detected)" in text)
    }

    @Test
    fun `an unavailable deckLink lists nothing`() {
        val text = render(facts(deckLinkAvailable = false))
        assertTrue("Driver available: false" in text)
        assertFalse("(no devices detected)" in text)
    }

    @Test
    fun `available VLC reads plainly`() {
        assertTrue("VLC: available" in render(facts(vlcAvailable = true)))
    }

    @Test
    fun `unavailable VLC includes the reason`() {
        assertTrue("VLC: unavailable (libvlc not found)" in render(facts(vlcAvailable = false, vlcReason = "libvlc not found")))
    }

    @Test
    fun `unavailable VLC with a blank reason falls back to unknown`() {
        assertTrue("VLC: unavailable (unknown reason)" in render(facts(vlcAvailable = false, vlcReason = "")))
    }

    @Test
    fun `initialized JCEF is reported without a caveat`() {
        val text = render(facts(jcefInitialized = true))
        assertTrue("Web browser (JCEF): initialized" in text)
        assertFalse("macOS version too old" in text)
    }

    @Test
    fun `uninitialized JCEF on old macOS notes the version`() {
        assertTrue(
            "Web browser (JCEF): not initialized (macOS version too old)"
                in render(facts(jcefInitialized = false, jcefMacUnsupported = true)),
        )
    }

    @Test
    fun `uninitialized JCEF without the macOS caveat`() {
        val text = render(facts(jcefInitialized = false, jcefMacUnsupported = false))
        assertTrue("Web browser (JCEF): not initialized" in text)
        assertFalse("macOS version too old" in text)
    }

    @Test
    fun `library counts come straight from the gathered facts`() {
        val text = render(facts(songFolderCount = 3, totalSongs = 57, bibleCount = 2))
        assertTrue("Song libraries (songbooks): 3" in text)
        assertTrue("Total songs: 57" in text)
        assertTrue("Bibles: 2" in text)
    }

    @Test
    fun `analytics state is reported both ways`() {
        assertTrue("Analytics reporting: enabled" in render(facts(analyticsEnabled = true)))
        assertTrue("Analytics reporting: disabled" in render(facts(analyticsEnabled = false)))
    }

    @Test
    fun `app facts come from the snapshot, not live singletons`() {
        val text = render(facts())
        assertTrue("Version: 1.2.3 (release)" in text)
        assertTrue("Install ID: inst-id" in text)
    }

    @Test
    fun `a primary screen line is one-based and marked primary`() {
        assertEquals("  1. 1920x1080 @60Hz (primary)", DeviceInfoReport.screenLine(0, 1920, 1080, 60, true))
    }

    @Test
    fun `a secondary screen line carries no suffix`() {
        assertEquals("  2. 1280x720 @30Hz", DeviceInfoReport.screenLine(1, 1280, 720, 30, false))
    }

    @Test
    fun `a device line is one-based and named`() {
        assertEquals("  1. DeckLink Mini Recorder", DeviceInfoReport.deviceLine(0, "DeckLink Mini Recorder"))
    }
}
