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
}
