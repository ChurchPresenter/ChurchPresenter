package org.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompanionServerSocketHandshakeTest {

    private lateinit var client: HttpClient
    private val listeners = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_920))
            port = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    server.serverUrl.value.substringAfterLast(':').toInt()
                }
            } ?: error("server did not start")
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            runCatching { server.stop() }
        }
    }

    @BeforeTest
    fun resetState() {
        client = HttpClient(CIO) { install(WebSockets) }
        server.updateApiKey(enabled = false, key = "")
        server.blockedClientIds = emptySet()
    }

    @AfterTest
    fun closeClient() {
        listeners.cancel()
        runCatching { client.close() }
    }

    private fun command(type: String, payload: String, commandId: String) = json.encodeToString(
        WebSocketMessage.serializer(),
        WebSocketMessage(type = type, payload = payload, commandId = commandId),
    )

    private fun ackOf(text: String, commandId: String): CommandAckPayload? {
        val message = runCatching { json.decodeFromString(WebSocketMessage.serializer(), text) }.getOrNull()
        if (message?.type != Constants.WS_EVENT_COMMAND_ACK) return null
        return json.decodeFromString(CommandAckPayload.serializer(), message.payload)
            .takeIf { it.commandId == commandId }
    }

    private fun exchange(
        query: String = "",
        frame: String? = null,
        until: (String) -> Boolean,
        request: HttpRequestBuilder.() -> Unit = {},
    ): String? = runBlocking {
        withTimeoutOrNull(5_000) {
            var match: String? = null
            val url = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}$query"
            client.webSocket(urlString = url, request = request) {
                frame?.let { send(Frame.Text(it)) }
                for (received in incoming) {
                    val text = (received as? Frame.Text)?.readText()
                    if (text != null && until(text)) {
                        match = text
                        break
                    }
                }
            }
            match
        }
    }

    @Test
    fun `a phone may present the api key in the query string`() {
        server.updateApiKey(enabled = true, key = "s3cret")
        val selected = CompletableDeferred<ScheduleSongDto>()
        val subscribed = CompletableDeferred<Unit>()
        listeners.launch {
            server.onSongSelected.onSubscription { subscribed.complete(Unit) }.collect { selected.complete(it) }
        }
        runBlocking { subscribed.await() }

        val reply = exchange(
            query = "?${Constants.QUERY_PARAM_API_KEY}=s3cret",
            frame = command(
                Constants.WS_CMD_SELECT_SONG,
                """{"id":"s1","songNumber":7,"title":"Seven","songbook":"Hymns"}""",
                "c1",
            ),
            until = { ackOf(it, "c1") != null },
        )

        assertEquals(true, ackOf(assertNotNull(reply), "c1")?.ok)
        assertEquals(7, runBlocking { withTimeoutOrNull(2_000) { selected.await() } }?.songNumber)
    }

    @Test
    fun `a wrong key in the query string wins over the right one in the header`() {
        server.updateApiKey(enabled = true, key = "s3cret")

        val reply = exchange(
            query = "?${Constants.QUERY_PARAM_API_KEY}=guess",
            until = { "error" in it },
            request = { header(Constants.HEADER_API_KEY, "s3cret") },
        )

        assertEquals("""{"error":"Unauthorized"}""", reply)
    }

    @Test
    fun `a device named only in the query string can still be blocked`() {
        server.blockedClientIds = setOf("tablet-7")

        val reply = exchange(query = "?${Constants.HEADER_DEVICE_ID}=tablet-7", until = { "error" in it })

        assertEquals("""{"error":"Blocked"}""", reply)
    }

    @Test
    fun `a volume that is not a number is refused as an invalid payload`() {
        val reply = exchange(
            frame = command(Constants.WS_CMD_MEDIA_SET_VOLUME, "loud", "c2"),
            until = { ackOf(it, "c2") != null },
        )

        val ack = assertNotNull(ackOf(assertNotNull(reply), "c2"))
        assertEquals(false, ack.ok)
        assertEquals("invalid_payload", ack.reason)
    }

    @Test
    fun `a linked instance that gives no device id is never counted as a follower`() {
        val reply = exchange(
            frame = command(Constants.WS_CMD_MEDIA_SET_VOLUME, "0.5", "c3"),
            until = { ackOf(it, "c3") != null },
            request = { header(Constants.HEADER_CLIENT_ROLE, Constants.CLIENT_ROLE_INSTANCE_LINK) },
        )

        assertEquals(true, ackOf(assertNotNull(reply), "c3")?.ok)
        assertTrue(server.connectedInstanceLinkFollowers.value.isEmpty())
    }
}
