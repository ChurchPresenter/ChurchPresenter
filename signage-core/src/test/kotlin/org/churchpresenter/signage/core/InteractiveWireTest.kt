package org.churchpresenter.signage.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Interactive pages, the screen's PIN and gesture, the machine's own commands (power, TV, system
 * update) on the wire: what each carries, that a manifest without them encodes as before, and that
 * the PIN hash is the format ChurchPresenter and the player both read.
 */
class InteractiveWireTest {
    private fun tree(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    @Test
    fun anInteractivePageRoundTripsWithItsResetTimeAndSites() {
        val page = PlaylistItem(
            kind = MediaKind.WEB,
            url = "https://signup.example.org",
            interactive = true,
            idleResetMs = 120_000,
            allowedHosts = listOf("pay.example.com", PlaylistItem.ANY_HOST),
        )
        val text = SignageJson.encodeToString(PlaylistItem.serializer(), page)
        assertTrue("\"interactive\":true" in text)
        assertEquals(page, SignageJson.decodeFromString(PlaylistItem.serializer(), text))
    }

    @Test
    fun aPageThatIsNotInteractiveLeavesTheNewFieldsOut() {
        val text = SignageJson.encodeToString(
            PlaylistItem.serializer(),
            PlaylistItem(kind = MediaKind.WEB, url = "https://example.org"),
        )
        listOf("interactive", "idleResetMs", "allowedHosts").forEach { assertFalse(it in text, it) }
        val screen = SignageJson.encodeToString(ScreenSettings.serializer(), ScreenSettings("Lobby"))
        listOf("kioskPinHash", "kioskGesture", "kioskGesturePlaylistId").forEach { assertFalse(it in screen, it) }
    }

    @Test
    fun theSharedInteractiveManifestCarriesEachValueAndKeepsItsText() {
        val text = fixture("manifest-interactive.json")
        val manifest = SignageJson.decodeFromString(Manifest.serializer(), text)
        val page = manifest.playlist("signup")!!.items.single()
        assertTrue(page.interactive)
        assertEquals(60_000L, page.idleResetMs)
        assertEquals(listOf("pay.example.com"), page.allowedHosts)
        assertFalse(manifest.screen.kioskGesture)
        assertEquals("signup", manifest.screen.kioskGesturePlaylistId)
        assertTrue(KioskPin.matches("2468", manifest.screen.kioskPinHash!!))
        assertEquals(tree(text), tree(SignageJson.encodeToString(Manifest.serializer(), manifest)))
    }

    @Test
    fun aPinHashIsTheSameFormatAsPythonsPbkdf2() {
        // Made with Python's hashlib.pbkdf2_hmac("sha256", b"2468", bytes(range(16)), 100000, 32).
        val fromPython = "pbkdf2:100000:000102030405060708090a0b0c0d0e0f:" +
            "6b27f7b5c0d56a1e644595b276cc3a6c9fffe1a44078e5a048e683b47fc1e4b8"
        assertEquals(fromPython, KioskPin.hash("2468", ByteArray(16) { it.toByte() }))
        assertTrue(KioskPin.matches("2468", fromPython))
        assertFalse(KioskPin.matches("2469", fromPython))
    }

    @Test
    fun aPinIsFourToEightDigitsAndAnythingElseNeverMatches() {
        assertTrue(KioskPin.isValid("1234") && KioskPin.isValid("12345678"))
        assertFalse(KioskPin.isValid("123") || KioskPin.isValid("123456789") || KioskPin.isValid("12a4"))
        val hash = KioskPin.hash("1234")
        assertNotEquals(hash, KioskPin.hash("1234"), "each hash has its own salt")
        assertFalse(KioskPin.matches("12a4", hash))
        listOf("", "pbkdf2:1:zz:00", "sha1:100:00:00", "pbkdf2:0:00:00", "pbkdf2:x:00:00", "pbkdf2:1:0:00")
            .forEach { assertFalse(KioskPin.matches("1234", it), it) }
    }

    @Test
    fun theMachinesCommandsAndTheStatusSayWhatTheyMean() {
        val power = SignageJson.encodeToString(ServerMessage.serializer(), ServerMessage.Power(PowerAction.SHUT_DOWN))
        assertEquals(tree("""{"type":"power","action":"shut_down"}"""), tree(power))
        val tv = SignageJson.encodeToString(ServerMessage.serializer(), ServerMessage.Tv(on = false))
        assertEquals(tree("""{"type":"tv","on":false}"""), tree(tv))
        val update = SignageJson.encodeToString(ServerMessage.serializer(), ServerMessage.UpdateSystem)
        assertEquals(tree("""{"type":"update_system"}"""), tree(update))
        listOf(power, tv, update).forEach { text ->
            assertEquals(text, SignageJson.encodeToString(ServerMessage.serializer(), decode(text)))
        }
        // A status from a player before touch pages reads as none on screen.
        val old = """{"type":"status","showingPlaylistId":"welcome"}"""
        val status = SignageJson.decodeFromString(PlayerMessage.serializer(), old) as PlayerMessage.Status
        assertFalse(status.interactive)
    }

    @Test
    fun aReleaseIndexMayNameItsSetupScript() {
        val withSetup = """{"version":"1.0","protocolMin":1,"protocolMax":1,"assets":{},""" +
            """"setup":{"name":"install.sh","size":3,"sha256":"${"a".repeat(64)}"}}"""
        assertEquals("install.sh", SignageJson.decodeFromString(SignageRelease.serializer(), withSetup).setup?.name)
        val before = """{"version":"1.0","protocolMin":1,"protocolMax":1,"assets":{}}"""
        assertNull(SignageJson.decodeFromString(SignageRelease.serializer(), before).setup)
    }

    private fun decode(text: String) = SignageJson.decodeFromString(ServerMessage.serializer(), text)
}
