package org.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The songs_updated a socket gets on connect: the bare signal for a phone, which fetches
 * /api/songs itself, and the whole catalog for an Instance Link follower, which mirrors it.
 * A large catalog in one message closed every iPad's socket seconds after it opened.
 */
class CompanionServerPhoneSnapshotTest {

    private lateinit var client: HttpClient
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer(shutdownGraceMs = 0)
            server.start(port = testPort(39_990))
            port = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) delay(25)
                    server.serverUrl.value.substringAfterLast(':').toInt()
                }
            } ?: error("server did not start")
            server.updateSongs(
                listOf(
                    SongItem(
                        number = "42", title = "Amazing Grace", songbook = "Hymns",
                        lyrics = listOf("Amazing grace"),
                    ),
                ),
            )
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            runCatching { server.stop() }
        }
    }

    @BeforeTest
    fun openClient() {
        client = HttpClient(CIO) { install(WebSockets) }
        server.updateApiKey(enabled = false, key = "")
    }

    @AfterTest
    fun closeClient() {
        runCatching { client.close() }
    }

    /** The songs_updated message the server sends on connect, as this client asks for it. */
    private fun songsOnConnect(request: HttpRequestBuilder.() -> Unit = {}): WebSocketMessage? = runBlocking {
        withTimeoutOrNull(5_000) {
            var songs: WebSocketMessage? = null
            client.webSocket(urlString = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}", request = request) {
                for (received in incoming) {
                    songs = (received as? Frame.Text)?.readText()
                        ?.let { runCatching { json.decodeFromString(WebSocketMessage.serializer(), it) }.getOrNull() }
                        ?.takeIf { it.type == Constants.WS_EVENT_SONGS_UPDATED }
                    if (songs != null) break
                }
            }
            songs
        }
    }

    @Test
    fun `a phone is told the songs changed without being sent the catalog`() {
        val songs = assertNotNull(songsOnConnect { header(Constants.HEADER_DEVICE_ID, "ipad") })

        assertEquals("", songs.payload)
    }

    @Test
    fun `a linked instance is sent the whole catalog`() {
        val songs = assertNotNull(
            songsOnConnect {
                header(Constants.HEADER_DEVICE_ID, "follower")
                header(Constants.HEADER_CLIENT_ROLE, Constants.CLIENT_ROLE_INSTANCE_LINK)
            },
        )

        assertTrue("Amazing Grace" in songs.payload, songs.payload)
    }
}
