package org.churchpresenter.companionserver

import kotlinx.serialization.json.Json
import org.churchpresenter.settings.AtemSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtemBridgeTest {

    private fun bridge() = AtemBridge(Json { ignoreUnknownKeys = true })

    @Test
    fun `configuration is held for later requests`() {
        val b = bridge()
        val settings = AtemSettings(host = "10.0.0.5", port = 9910)
        b.updateConfig(settings, "/lower-thirds")
        assertEquals(settings, b._atemSettings)
    }

    @Test
    fun `reconfiguring replaces the previous settings`() {
        val b = bridge()
        b.updateConfig(AtemSettings(host = "10.0.0.5"), "")
        b.updateConfig(AtemSettings(host = "10.0.0.9"), "")
        assertEquals("10.0.0.9", b._atemSettings?.host)
    }

    @Test
    fun `no lower-third folder means no files`() {
        assertTrue(bridge().lowerThirdFiles().isEmpty())
    }

    @Test
    fun `a folder that does not exist yields no files rather than throwing`() {
        val b = bridge()
        b.updateConfig(AtemSettings(), "/no/such/folder/anywhere")
        assertTrue(b.lowerThirdFiles().isEmpty())
    }

    @Test
    fun `strings are encoded as quoted json`() {
        assertEquals("\"hello\"", bridge().jsonStr("hello"))
    }

    @Test
    fun `strings that would break json are escaped by the encoder`() {
        val encoded = bridge().jsonStr("say \"hi\"\nthere")
        assertTrue(encoded.startsWith("\"") && encoded.endsWith("\""))
        assertTrue(encoded.contains("\\\""), encoded)
        assertTrue(encoded.contains("\\n"), encoded)
    }

    @Test
    fun `an undetected atem accepts any key target`() {
        // detected* are 0 until the ATEM reports its topology; refusing then would block every
        // request made before the first successful connection.
        val b = bridge()
        assertNull(b.validateKeyTarget(AtemSettings(), useDsk = false, mixEffect = 7, keyer = 7))
        assertNull(b.validateKeyTarget(AtemSettings(), useDsk = true, mixEffect = 0, keyer = 7))
    }

    @Test
    fun `a downstream keyer beyond the detected count is refused`() {
        val b = bridge()
        val atem = AtemSettings(detectedDownstreamKeyers = 2)
        assertNull(b.validateKeyTarget(atem, useDsk = true, mixEffect = 0, keyer = 0))
        assertNull(b.validateKeyTarget(atem, useDsk = true, mixEffect = 0, keyer = 1))
        val error = b.validateKeyTarget(atem, useDsk = true, mixEffect = 0, keyer = 2)
        assertNotNull(error)
        assertTrue(error.contains("DSK 3"), error)
        assertTrue(error.contains("1-2"), error)
    }

    @Test
    fun `a mix effect beyond the detected count is refused`() {
        val b = bridge()
        val atem = AtemSettings(detectedMixEffects = 1)
        assertNull(b.validateKeyTarget(atem, useDsk = false, mixEffect = 0, keyer = 0))
        val error = b.validateKeyTarget(atem, useDsk = false, mixEffect = 1, keyer = 0)
        assertNotNull(error)
        assertTrue(error.contains("M/E 2"), error)
    }

    @Test
    fun `an upstream keyer beyond that mix effect's keyers is refused`() {
        val b = bridge()
        val atem = AtemSettings(detectedMixEffects = 2, detectedKeyersPerMe = listOf(4, 1))
        assertNull(b.validateKeyTarget(atem, useDsk = false, mixEffect = 1, keyer = 0))
        val error = b.validateKeyTarget(atem, useDsk = false, mixEffect = 1, keyer = 1)
        assertNotNull(error)
        assertTrue(error.contains("Key 2"), error)
        assertTrue(error.contains("M/E 2"), error)
    }

    @Test
    fun `a mix effect with no reported keyers is not second-guessed`() {
        val b = bridge()
        val atem = AtemSettings(detectedMixEffects = 2, detectedKeyersPerMe = listOf(4, 0))
        assertNull(b.validateKeyTarget(atem, useDsk = false, mixEffect = 1, keyer = 3))
    }
}
