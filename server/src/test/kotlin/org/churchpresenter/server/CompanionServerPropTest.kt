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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.PropKind
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

/** Switching props from a remote client: `GET /api/props`, `POST /api/props/{id}/...` and WS `prop`. */
class CompanionServerPropTest {

    private lateinit var client: HttpClient
    private var collector: CoroutineScope? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val logo = PropDefinition("prop1", "Logo", PropKind.IMAGE)
    private val live = PropDefinition("prop2", "Live", PropKind.BADGE, text = "LIVE")

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_945))
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
        client = HttpClient(CIO) { install(WebSockets) }
        server.updateApiKey(enabled = false, key = "")
        server.props = listOf(logo, live)
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

    private fun nextSwitch(): CompletableDeferred<PropSwitch> {
        val next = CompletableDeferred<PropSwitch>()
        val scope = CoroutineScope(Dispatchers.IO).also { collector = it }
        scope.launch { server.onProp.collect { next.complete(it) } }
        runBlocking { withTimeoutOrNull(5_000) { server.onProp.subscriptionCount.first { it > 0 } } }
        return next
    }

    private fun CompletableDeferred<PropSwitch>.awaited(): PropSwitch? = runBlocking { withTimeoutOrNull(2_000) { await() } }

    @Test
    fun `the props are listed with whether each is up`() {
        server.updateLiveState(LiveContent(mode = "PROPS", overlays = emptyList(), props = listOf("prop2")))
        val body = runBlocking { client.get(url(Constants.ENDPOINT_PROPS)).bodyAsText() }
        val listed = json.decodeFromString(ListSerializer(PropDto.serializer()), body)
        assertEquals(
            listOf(PropDto("prop1", "Logo", "IMAGE", false), PropDto("prop2", "Live", "BADGE", true)),
            listed,
        )
    }

    @Test
    fun `a prop is switched on, off or the other way, by id or by name`() {
        listOf(
            "/prop1/on" to PropSwitch("prop1", true),
            "/live/off" to PropSwitch("prop2", false),
            "/LOGO/toggle" to PropSwitch("prop1", null),
        ).forEach { (path, expected) ->
            val next = nextSwitch()
            assertEquals(HttpStatusCode.OK, post(Constants.ENDPOINT_PROPS + path).status, path)
            assertEquals(expected, next.awaited(), path)
            close()
            client = HttpClient(CIO) { install(WebSockets) }
        }
    }

    @Test
    fun `an unknown prop or action is refused`() {
        assertEquals(HttpStatusCode.NotFound, post("${Constants.ENDPOINT_PROPS}/choir/on").status)
        assertEquals(HttpStatusCode.BadRequest, post("${Constants.ENDPOINT_PROPS}/prop1/flash").status)
    }

    @Test
    fun `the API key is asked for`() {
        server.updateApiKey(enabled = true, key = "secret")
        assertEquals(HttpStatusCode.Unauthorized, post("${Constants.ENDPOINT_PROPS}/prop1/on").status)
        assertEquals(HttpStatusCode.OK, post("${Constants.ENDPOINT_PROPS}/prop1/on", apiKey = "secret").status)
    }

    @Test
    fun `over the socket a prop is switched, or toggled with no on`() {
        val next = nextSwitch()
        val acks = acksFor(
            setOf("ok", "bad"),
            command("""{"id":"Live"}""", "ok"),
            command("""{"id":"choir","on":true}""", "bad"),
        )
        assertEquals(PropSwitch("prop2", null), next.awaited())
        assertTrue(acks.first { it.commandId == "ok" }.ok)
        assertFalse(acks.first { it.commandId == "bad" }.ok)
    }

    private fun command(payload: String, commandId: String): String = json.encodeToString(
        WebSocketMessage.serializer(),
        WebSocketMessage(type = Constants.WS_CMD_PROP, payload = payload, commandId = commandId),
    )

    private fun acksFor(ids: Set<String>, vararg frames: String): List<CommandAckPayload> = runBlocking {
        val acks = mutableListOf<CommandAckPayload>()
        withTimeoutOrNull(10_000) {
            client.webSocket(urlString = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}") {
                frames.forEach { send(Frame.Text(it)) }
                while (acks.map { it.commandId }.toSet() != ids) {
                    val frame = incoming.receive() as? Frame.Text ?: continue
                    val message = runCatching { json.decodeFromString(WebSocketMessage.serializer(), frame.readText()) }
                        .getOrNull()?.takeIf { it.type == Constants.WS_EVENT_COMMAND_ACK } ?: continue
                    json.decodeFromString(CommandAckPayload.serializer(), message.payload)
                        .takeIf { it.commandId in ids }?.let(acks::add)
                }
            }
        }
        acks
    }
}

/** Naming a prop and its action, without a server. */
class PropNamingTest {

    private val props = listOf(PropDefinition("prop1", "Logo"), PropDefinition("prop2", "Live"))

    @Test
    fun `a prop is found by id, or by name in any case and spacing`() {
        assertEquals("prop1", findProp(props, "prop1")?.id)
        assertEquals("prop2", findProp(props, " LIVE ")?.id)
        assertNull(findProp(props, "Choir"))
    }

    @Test
    fun `each action names its switch`() {
        assertEquals(true, propAction("on").getOrNull())
        assertEquals(false, propAction("off").getOrNull())
        assertNull(propAction("toggle").getOrThrow())
        assertTrue(propAction("flash").isFailure)
        assertTrue(propAction(null).isFailure)
    }
}
