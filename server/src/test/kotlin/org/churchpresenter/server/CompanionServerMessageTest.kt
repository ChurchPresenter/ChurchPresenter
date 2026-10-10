package org.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Putting a message up from a remote client: `POST /api/message` and the WebSocket `message`
 * command, with the client's own text or a message saved in the app.
 */
class CompanionServerMessageTest {

    private lateinit var client: HttpClient
    private var collector: CoroutineScope? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val nursery = MessageTemplate("message1", "Nursery", "Parent of child #{number}, please come", 120)

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer(shutdownGraceMs = 0)
            server.start(port = testPort(39_940))
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
    fun reset() {
        // These are dev mode only features (AGENT.md); off, they refuse -- see the dev mode test.
        server.devMode = true
        client = HttpClient(CIO) { install(WebSockets) }
        server.updateApiKey(enabled = false, key = "")
        server.messageTemplates = listOf(nursery)
    }

    @AfterTest
    fun close() {
        runCatching { collector?.cancel() }
        collector = null
        runCatching { client.close() }
    }

    private fun post(body: String, apiKey: String? = null): HttpResponse = runBlocking {
        client.post("http://127.0.0.1:$port${Constants.ENDPOINT_MESSAGE}") {
            apiKey?.let { header(Constants.HEADER_API_KEY, it) }
            setBody(body)
        }
    }

    /** What reaches the app next, collected from before the request is sent. */
    private fun nextMessage(): CompletableDeferred<RemoteMessage> {
        val next = CompletableDeferred<RemoteMessage>()
        val scope = CoroutineScope(Dispatchers.IO).also { collector = it }
        scope.launch { server.onMessage.collect { next.complete(it) } }
        runBlocking { withTimeoutOrNull(5_000) { server.onMessage.subscriptionCount.first { it > 0 } } }
        return next
    }

    private fun CompletableDeferred<RemoteMessage>.awaited(): RemoteMessage? =
        runBlocking { withTimeoutOrNull(2_000) { await() } }

    @Test
    fun `a message of the client's own goes up as sent`() {
        val next = nextMessage()
        val response = post("""{"text":"Car KX12 has its lights on","durationSeconds":30}""")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Car KX12 has its lights on", runBlocking { response.bodyAsText() }.let {
            json.parseToJsonElement(it).jsonObject.getValue("text").jsonPrimitive.content
        })
        assertEquals(RemoteMessage("Car KX12 has its lights on", null, 30), next.awaited())
    }

    @Test
    fun `a saved message is named and its tokens filled in`() {
        val next = nextMessage()
        val response = post("""{"template":"nursery","tokens":{"number":"42"}}""")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(RemoteMessage("Parent of child #42, please come", "Nursery", 120), next.awaited())
    }

    @Test
    fun `a message that cannot be put up is refused`() {
        listOf(
            """{"template":"Choir"}""",
            """{"text":"   "}""",
            """not json""",
        ).forEach { body ->
            assertEquals(HttpStatusCode.BadRequest, post(body).status, body)
        }
    }

    @Test
    fun `the API key is asked for`() {
        server.updateApiKey(enabled = true, key = "secret")
        assertEquals(HttpStatusCode.Unauthorized, post("""{"text":"Hello"}""").status)
        assertEquals(HttpStatusCode.OK, post("""{"text":"Hello"}""", apiKey = "secret").status)
    }

    @Test
    fun `over the socket the same message is acknowledged and reaches the app`() {
        val next = nextMessage()
        val acks = acksFor(
            setOf("cmd-ok", "cmd-bad"),
            command("""{"template":"Nursery","tokens":{"number":"7"}}""", "cmd-ok"),
            command("""{"template":"Choir"}""", "cmd-bad"),
        )
        assertEquals(RemoteMessage("Parent of child #7, please come", "Nursery", 120), next.awaited())
        assertTrue(acks.first { it.commandId == "cmd-ok" }.ok)
        assertFalse(acks.first { it.commandId == "cmd-bad" }.ok)
    }

    private fun command(payload: String, commandId: String): String = json.encodeToString(
        WebSocketMessage.serializer(),
        WebSocketMessage(type = Constants.WS_CMD_MESSAGE, payload = payload, commandId = commandId),
    )

    private fun ackOf(frame: String): CommandAckPayload? =
        runCatching { json.decodeFromString(WebSocketMessage.serializer(), frame) }.getOrNull()
            ?.takeIf { it.type == Constants.WS_EVENT_COMMAND_ACK }
            ?.let { runCatching { json.decodeFromString(CommandAckPayload.serializer(), it.payload) }.getOrNull() }

    /** Sends [frames] and reads replies until an acknowledgement has come back for each of [ids]. */
    private fun acksFor(ids: Set<String>, vararg frames: String): List<CommandAckPayload> = runBlocking {
        val acks = mutableListOf<CommandAckPayload>()
        withTimeoutOrNull(10_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}") {
                frames.forEach { send(Frame.Text(it)) }
                while (acks.map { it.commandId }.toSet() != ids) {
                    val frame = incoming.receive()
                    if (frame is Frame.Text) ackOf(frame.readText())?.takeIf { it.commandId in ids }?.let(acks::add)
                }
            }
        }
        acks
    }
}

/** How a [MessageRequest] resolves against the saved messages, without a server. */
class ResolveMessageTest {

    private val saved = listOf(
        MessageTemplate("message1", "Nursery", "Parent of child #{number}", 60),
        MessageTemplate("message2", "Plain", "No tokens here"),
    )

    @Test
    fun `a saved message is found by id or by name in any case`() {
        assertEquals("Nursery", resolveMessage(MessageRequest(template = "message1"), saved)?.template)
        assertEquals("Nursery", resolveMessage(MessageRequest(template = " NURSERY "), saved)?.template)
    }

    @Test
    fun `the request's own text and duration win over the saved message's`() {
        val message = resolveMessage(MessageRequest(text = "Child {number}", template = "Nursery", durationSeconds = 5,
            tokens = mapOf("number" to "3")), saved)
        assertEquals(RemoteMessage("Child 3", "Nursery", 5), message)
    }

    @Test
    fun `an unknown template or no text at all resolves to nothing`() {
        assertNull(resolveMessage(MessageRequest(template = "Choir"), saved))
        assertNull(resolveMessage(MessageRequest(), saved))
        assertNull(resolveMessage(MessageRequest(text = "{number}"), saved)?.takeIf { it.text.isBlank() })
    }

    @Test
    fun `a blank template name is no template`() {
        assertEquals(RemoteMessage("Hi"), resolveMessage(MessageRequest(text = "Hi", template = "  "), saved))
        assertNotNull(resolveMessage(MessageRequest(template = "Plain"), saved))
    }
}
