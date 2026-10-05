package org.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Firing a clear group from a remote client: `GET /api/clear-groups`, `POST /api/clear?group=` and WS `clear`. */
class CompanionServerClearGroupTest {

    private lateinit var client: HttpClient
    private var collector: CoroutineScope? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val text = ClearGroup("clear1", "Clear text", listOf("SLIDE", "MESSAGES"))
    private val graphics = ClearGroup("clear2", "Clear graphics", listOf("GRAPHICS", "PROPS"))

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_950))
            port = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) delay(25)
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
        client = HttpClient(CIO) { install(WebSockets) }
        server.updateApiKey(enabled = false, key = "")
        server.clearGroups = listOf(text, graphics)
    }

    @AfterTest
    fun close() {
        runCatching { collector?.cancel() }
        collector = null
        runCatching { client.close() }
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    private fun post(path: String, apiKey: String? = null): HttpResponse = runBlocking {
        client.post(url(path)) { apiKey?.let { header(Constants.HEADER_API_KEY, it) } }
    }

    private fun nextFired(): CompletableDeferred<String> {
        val next = CompletableDeferred<String>()
        val scope = CoroutineScope(Dispatchers.IO).also { collector = it }
        scope.launch { server.onClearGroup.collect { next.complete(it) } }
        runBlocking { withTimeoutOrNull(5_000) { server.onClearGroup.subscriptionCount.first { it > 0 } } }
        return next
    }

    private fun CompletableDeferred<String>.awaited(): String? = runBlocking { withTimeoutOrNull(2_000) { await() } }

    @Test
    fun `the groups are listed with their layers`() {
        val body = runBlocking { client.get(url(Constants.ENDPOINT_CLEAR_GROUPS)).bodyAsText() }
        assertEquals(
            listOf(
                ClearGroupDto("clear1", "Clear text", listOf("SLIDE", "MESSAGES")),
                ClearGroupDto("clear2", "Clear graphics", listOf("GRAPHICS", "PROPS")),
            ),
            json.decodeFromString(ListSerializer(ClearGroupDto.serializer()), body),
        )
    }

    @Test
    fun `a group is fired by id or by name`() {
        listOf("clear1" to "clear1", "clear%20GRAPHICS" to "clear2").forEach { (wanted, id) ->
            val next = nextFired()
            assertEquals(HttpStatusCode.OK, post("${Constants.ENDPOINT_CLEAR}?group=$wanted").status, wanted)
            assertEquals(id, next.awaited(), wanted)
            close()
            client = HttpClient(CIO) { install(WebSockets) }
        }
    }

    @Test
    fun `an unknown group is refused, and the API key is asked for`() {
        assertEquals(HttpStatusCode.NotFound, post("${Constants.ENDPOINT_CLEAR}?group=choir").status)
        server.updateApiKey(enabled = true, key = "secret")
        assertEquals(HttpStatusCode.Unauthorized, post("${Constants.ENDPOINT_CLEAR}?group=clear1").status)
        assertEquals(HttpStatusCode.OK, post("${Constants.ENDPOINT_CLEAR}?group=clear1", apiKey = "secret").status)
    }

    @Test
    fun `over the socket a clear with a group fires it`() {
        val next = nextFired()
        val acks = acksFor(
            setOf("ok", "bad"),
            command("""{"group":"Clear text"}""", "ok"),
            command("""{"group":"choir"}""", "bad"),
        )
        assertEquals("clear1", next.awaited())
        assertTrue(acks.first { it.commandId == "ok" }.ok)
        assertFalse(acks.first { it.commandId == "bad" }.ok)
    }

    private fun command(payload: String, commandId: String): String = json.encodeToString(
        WebSocketMessage.serializer(),
        WebSocketMessage(type = Constants.WS_CMD_CLEAR, payload = payload, commandId = commandId),
    )

    /** The acknowledgement [frame] carries, if it is one. */
    private fun ackIn(frame: Frame): CommandAckPayload? =
        (frame as? Frame.Text)?.readText()
            ?.let { runCatching { json.decodeFromString(WebSocketMessage.serializer(), it) }.getOrNull() }
            ?.takeIf { it.type == Constants.WS_EVENT_COMMAND_ACK }
            ?.let { json.decodeFromString(CommandAckPayload.serializer(), it.payload) }

    private fun acksFor(ids: Set<String>, vararg frames: String): List<CommandAckPayload> = runBlocking {
        val acks = mutableListOf<CommandAckPayload>()
        withTimeoutOrNull(10_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}") {
                frames.forEach { send(Frame.Text(it)) }
                while (acks.map { it.commandId }.toSet() != ids) {
                    ackIn(incoming.receive())?.takeIf { it.commandId in ids }?.let(acks::add)
                }
            }
        }
        acks
    }
}

/** Naming a clear group, without a server. */
class ClearGroupNamingTest {

    private val groups = listOf(ClearGroup("clear1", "Clear text"), ClearGroup("clear2", "Props"))

    @Test
    fun `a group is found by id, or by name in any case and spacing`() {
        assertEquals("clear1", findClearGroup(groups, "clear1")?.id)
        assertEquals("clear2", findClearGroup(groups, " PROPS ")?.id)
        assertNull(findClearGroup(groups, "Choir"))
    }
}
