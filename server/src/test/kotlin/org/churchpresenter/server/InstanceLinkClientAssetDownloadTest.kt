package org.churchpresenter.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.churchpresenter.settings.utils.Constants
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstanceLinkClientAssetDownloadTest {

    private val clients = mutableListOf<InstanceLinkClient>()
    private var fakePrimary: FakePrimary? = null

    @AfterTest
    fun cleanUp() {
        clients.forEach { runCatching { it.dispose() } }
        clients.clear()
        fakePrimary?.stop()
        fakePrimary = null
    }

    private data class Seen(val uri: String, val apiKey: String?)

    private class FakePrimary(private val answer: (String) -> Pair<HttpStatusCode, ByteArray>) {
        val seen = CopyOnWriteArrayList<Seen>()

        var port: Int = 0
            private set

        private val server = embeddedServer(Netty, port = 0) {
            routing {
                get("{path...}") {
                    val uri = URLDecoder.decode(call.request.uri, "UTF-8")
                    seen += Seen(uri, call.request.headers[Constants.HEADER_API_KEY])
                    val (status, body) = answer(uri)
                    call.respondBytes(body, status = status)
                }
            }
        }

        fun start() {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        fun stop() = server.stop(0, 0)
    }

    private fun startFake(answer: (String) -> Pair<HttpStatusCode, ByteArray>) =
        FakePrimary(answer).also { it.start(); fakePrimary = it }

    private fun followerOf(port: Int, apiKey: String): InstanceLinkClient {
        val client = InstanceLinkClient(
            onStatusChanged = {},
            onScheduleUpdated = {},
            onLiveStateUpdated = {},
            onDisplayCleared = {},
            onSongSectionSelected = {},
            onPresentationSlideChanged = { _, _, _, _, _ -> },
        )
        clients += client
        client.connect("127.0.0.1", port, apiKey, deviceId = "follower", reconnectDelayMs = 60_000)
        return client
    }

    private fun unusedPort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `an open primary serves every asset to a follower that has no api key`() = runBlocking {
        val fake = startFake { uri -> HttpStatusCode.OK to uri.toByteArray() }
        val client = followerOf(fake.port, apiKey = "")

        assertEquals(
            "${Constants.ENDPOINT_PICTURES}/folder-1/images/2",
            client.fetchPictureImageBytes("folder-1", 2)?.decodeToString(),
        )
        assertEquals(
            "${Constants.ENDPOINT_PRESENTATIONS}/deck-1/slides/3",
            client.fetchPresentationSlideBytes("deck-1", 3)?.decodeToString(),
        )
        assertEquals(
            "${Constants.ENDPOINT_BIBLE_FILE}/secondary",
            client.fetchSecondaryBibleFile()?.decodeToString(),
        )
        assertEquals(
            "${Constants.ENDPOINT_BACKGROUNDS}/asset/bible?type=video",
            client.fetchBackgroundAsset("bible", isVideo = true)?.decodeToString(),
        )
        assertTrue(fake.seen.all { it.apiKey == null }, "an unkeyed follower sent a key: ${fake.seen}")
    }

    @Test
    fun `a lower third whose name has spaces reaches the primary under that same name`() = runBlocking {
        val fake = startFake { uri -> HttpStatusCode.OK to uri.toByteArray() }
        val client = followerOf(fake.port, apiKey = "")

        val body = client.fetchLowerThirdJson("Speaker Name+Title")?.decodeToString()

        assertEquals("${Constants.ENDPOINT_LOWER_THIRDS}/Speaker Name+Title/json", body)
    }

    @Test
    fun `a primary answering with an error leaves every asset missing even for a keyed follower`() = runBlocking {
        val fake = startFake { HttpStatusCode.InternalServerError to ByteArray(0) }
        val client = followerOf(fake.port, apiKey = "s3cret")

        assertNull(client.fetchPictureImageBytes("folder-1", 0))
        assertNull(client.fetchPresentationSlideBytes("deck-1", 0))
        assertNull(client.fetchSecondaryBibleFile())
        assertNull(client.fetchLowerThirdJson("Speaker"))
        assertNull(client.fetchBackgroundAsset("default", isVideo = false))
        val fetches = fake.seen.filter { it.uri.startsWith("/api/") }
        assertEquals(5, fetches.size)
        assertTrue(fetches.all { it.apiKey == "s3cret" }, "a keyed follower dropped its key: $fetches")
    }

    @Test
    fun `a primary that went away leaves every asset missing without throwing`() = runBlocking {
        val client = followerOf(unusedPort(), apiKey = "")

        assertNull(client.fetchPictureImageBytes("folder-1", 0))
        assertNull(client.fetchPresentationSlideBytes("deck-1", 0))
        assertNull(client.fetchSecondaryBibleFile())
        assertNull(client.fetchLowerThirdJson("Speaker"))
        assertNull(client.fetchBackgroundAsset("default", isVideo = false))
    }

    @Test
    fun `bible modules the primary cannot hand over are left out of the download`() = runBlocking {
        val fake = startFake { uri ->
            when {
                uri.endsWith("/translations") -> HttpStatusCode.OK to """["KJV","RST","NIV"]""".toByteArray()
                uri.endsWith("/translation/1") -> HttpStatusCode.NotFound to ByteArray(0)
                else -> HttpStatusCode.OK to uri.substringAfterLast('/').toByteArray()
            }
        }
        val client = followerOf(fake.port, apiKey = "")

        val modules = client.fetchBibleTranslations()

        assertEquals(
            listOf("KJV" to "0", "NIV" to "2"),
            modules.map { (name, bytes) -> name to bytes.decodeToString() },
        )
    }

    @Test
    fun `a bible manifest that is not a list of names yields no modules`() = runBlocking {
        val fake = startFake { HttpStatusCode.OK to "<html>maintenance</html>".toByteArray() }
        val client = followerOf(fake.port, apiKey = "")

        assertTrue(client.fetchBibleTranslations().isEmpty())
        assertContains(fake.seen.map { it.uri }, "${Constants.ENDPOINT_BIBLE_FILE}/translations")
    }
}
