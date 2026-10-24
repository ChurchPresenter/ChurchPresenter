package org.churchpresenter.signage.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The wire format is a contract with ChurchPresenter, which keeps its own copy of these types.
 * `fixtures/manifest-v1.json` is the shared example: both repositories decode it and must encode it
 * back to the same JSON.
 */
class WireFormatTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    private fun tree(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun theSharedManifestRoundTrips() {
        val text = fixture("manifest-v1.json")
        val manifest = SignageJson.decodeFromString(Manifest.serializer(), text)
        assertEquals("Lobby left", manifest.screen.name)
        assertEquals(Orientation.PORTRAIT, manifest.screen.orientation)
        assertEquals(BlockAction.Service, manifest.schedule[1].action)
        assertEquals(ClockTime.parse("22:00"), manifest.schedule[0].start)
        assertEquals(3, manifest.fileHashes().size)
        assertEquals(tree(text), tree(SignageJson.encodeToString(Manifest.serializer(), manifest)))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val text = """{"ownerId":"o","screen":{"name":"n","brightness":3},"timeZone":"UTC","future":true}"""
        val manifest = SignageJson.decodeFromString(Manifest.serializer(), text)
        assertEquals(PROTOCOL_VERSION, manifest.protocol)
        assertEquals(ScreenSettings.DEFAULT_WIDTH, manifest.screen.width)
    }

    @Test
    fun theVersionFollowsTheContent() {
        val manifest = SignageJson.decodeFromString(Manifest.serializer(), fixture("manifest-v1.json"))
        assertEquals(manifest.version(), manifest.copy().version())
        assertNotEquals(manifest.version(), manifest.copy(defaultPlaylistId = "events").version())
        assertTrue(isSha256Hex(manifest.version()))
    }

    @Test
    fun messagesCarryTheirTypeName() {
        val hello = PlayerMessage.Hello(
            playerId = "p1", agentVersion = "0.1.0", protocolMin = 1, protocolMax = 1,
            screenWidth = 1920, screenHeight = 1080, platform = Platform.LINUX_ARM64, pairingCode = "4821",
        )
        val encoded = SignageJson.encodeToString(PlayerMessage.serializer(), hello)
        assertTrue("\"type\":\"hello\"" in encoded)
        assertTrue("\"platform\":\"linux_arm64\"" in encoded)
        assertEquals(hello, SignageJson.decodeFromString(PlayerMessage.serializer(), encoded))

        val messages = listOf(
            ServerMessage.PairingPending("4821"),
            ServerMessage.Paired("t", "o", "Main PC"),
            ServerMessage.PairingRejected,
            ServerMessage.Released,
            ServerMessage.ManifestChanged("v"),
            ServerMessage.SetOverride(ScreenOverride(OverrideAction.Play("welcome"), Until.Time(5), 1)),
            ServerMessage.SetOverride(null),
            ServerMessage.Identify(),
            ServerMessage.Refresh,
            ServerMessage.Unsupported(PROTOCOL_VERSION),
            ServerMessage.UpdateAvailable("0.2.0", 1, 2, "b".repeat(64), "/api/signage/agent/linux_arm64"),
            ServerMessage.UpdateAvailable(
                "0.2.0", 1, 2, "b".repeat(64), "/a", release = "{}", releaseSignature = "c2ln",
            ),
            ServerMessage.PreviewRequest(),
            ServerMessage.Wipe,
        )
        messages.forEach { message ->
            val text = SignageJson.encodeToString(ServerMessage.serializer(), message)
            assertEquals(message, SignageJson.decodeFromString(ServerMessage.serializer(), text), text)
        }
        assertEquals(
            """{"type":"override","override":""" +
                """{"action":{"type":"tv_off"},"until":{"type":"next_change"},"setAtMs":7}}""",
            SignageJson.encodeToString(
                ServerMessage.serializer(),
                ServerMessage.SetOverride(ScreenOverride(OverrideAction.TvOff, setAtMs = 7)),
            ),
        )
    }

    @Test
    fun theNewPlayerMessagesRoundTrip() {
        val messages = listOf(
            PlayerMessage.Preview("/9j/", 480, 270, 1_000),
            PlayerMessage.UpdateProgress("0.2.0", UpdateState.ROLLED_BACK, "did not start"),
            PlayerMessage.Hello(
                "p", "0.2.0", 1, 1, null, 1920, 1080, Platform.LINUX_X64, features = listOf(Features.PREVIEW),
            ),
        )
        messages.forEach { message ->
            val text = SignageJson.encodeToString(PlayerMessage.serializer(), message)
            assertEquals(message, SignageJson.decodeFromString(PlayerMessage.serializer(), text), text)
        }
        // A 0.1.0 player's hello has no features, and still reads.
        val old = """{"type":"hello","playerId":"p","agentVersion":"0.1.0","protocolMin":1,"protocolMax":1,""" +
            """"screenWidth":1920,"screenHeight":1080,"platform":"linux_x64"}"""
        val hello = SignageJson.decodeFromString(PlayerMessage.serializer(), old) as PlayerMessage.Hello
        assertEquals(emptyList(), hello.features)
    }

    @Test
    fun protocolRangesAndHashesAreChecked() {
        assertTrue(supportsProtocol(1, 3, protocol = 2))
        assertFalse(supportsProtocol(2, 3, protocol = 1))
        assertTrue(isSha256Hex(sha256Hex("x".toByteArray())))
        assertFalse(isSha256Hex("../../etc/passwd"))
        assertFalse(isSha256Hex("A".repeat(64)))
    }
}
