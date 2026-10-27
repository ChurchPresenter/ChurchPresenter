package org.churchpresenter.signage.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Links (streams and web pages) and the web page test on the wire: what a link item carries, that
 * an item without one encodes exactly as before links existed, and the new messages.
 */
class LinkWireTest {
    private fun tree(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    @Test
    fun aLinkItemRoundTripsWithItsUrlRefreshAndFrameRate() {
        val web = PlaylistItem(kind = MediaKind.WEB, url = "https://example.org", refreshMs = 600_000, maxFps = 30)
        val text = SignageJson.encodeToString(PlaylistItem.serializer(), web)
        assertTrue("\"kind\":\"web\"" in text)
        assertTrue("\"url\":\"https://example.org\"" in text)
        assertEquals(web, SignageJson.decodeFromString(PlaylistItem.serializer(), text))
        val stream = PlaylistItem(kind = MediaKind.STREAM, url = "rtsp://camera/live")
        val streamText = SignageJson.encodeToString(PlaylistItem.serializer(), stream)
        assertEquals(stream, SignageJson.decodeFromString(PlaylistItem.serializer(), streamText))
    }

    @Test
    fun anItemWithoutALinkLeavesTheNewFieldsOut() {
        val text = SignageJson.encodeToString(PlaylistItem.serializer(), PlaylistItem("a".repeat(64), MediaKind.IMAGE))
        assertFalse("url" in text)
        assertFalse("refreshMs" in text)
        assertFalse("maxFps" in text)
    }

    @Test
    fun theOldManifestStillDecodesAndKeepsItsVersion() {
        val text = fixture("manifest-v1.json")
        val manifest = SignageJson.decodeFromString(Manifest.serializer(), text)
        assertTrue(manifest.playlists.flatMap { it.items }.all { it.url == null && it.maxFps == null })
        // Re-encoded unchanged, so a manifest without links keeps the version it had before.
        assertEquals(tree(text), tree(SignageJson.encodeToString(Manifest.serializer(), manifest)))
    }

    @Test
    fun linksAreNoFilesToFetch() {
        val manifest = Manifest(
            ownerId = "o",
            screen = ScreenSettings("Lobby"),
            playlists = listOf(
                Playlist(
                    "mixed",
                    "Mixed",
                    listOf(
                        PlaylistItem("1".repeat(64), MediaKind.IMAGE),
                        PlaylistItem(kind = MediaKind.WEB, url = "https://example.org"),
                        PlaylistItem(kind = MediaKind.STREAM, url = "https://example.org/live.m3u8"),
                    ),
                ),
            ),
            timeZone = "UTC",
        )
        assertEquals(setOf("1".repeat(64)), manifest.fileHashes())
        assertTrue(MediaKind.WEB.isLink && MediaKind.STREAM.isLink)
        assertFalse(MediaKind.IMAGE.isLink || MediaKind.VIDEO.isLink)
    }

    @Test
    fun theWebTestMessagesCarryTheirValues() {
        val request = SignageJson.encodeToString(ServerMessage.serializer(), ServerMessage.WebTest())
        assertTrue("\"type\":\"web_test\"" in request)
        val decoded = SignageJson.decodeFromString(ServerMessage.serializer(), request) as ServerMessage.WebTest
        assertEquals(listOf(15, 30) to 20, decoded.fps to decoded.secondsEach)

        val result = PlayerMessage.WebTestResult(listOf(WebTestRun(15, 14.5, 40.0, 61.5), WebTestRun(30, 22.0)))
        val text = SignageJson.encodeToString(PlayerMessage.serializer(), result)
        assertTrue("\"type\":\"web_test_result\"" in text)
        assertEquals(result, SignageJson.decodeFromString(PlayerMessage.serializer(), text))
        assertEquals(listOf("stream", "web", "web_test"), listOf(Features.STREAM, Features.WEB, Features.WEB_TEST))
    }
}
