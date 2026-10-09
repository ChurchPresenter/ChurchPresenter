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
import org.churchpresenter.settings.Macro
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Running macros from a remote client: `GET /api/macros`, `POST /api/macro/{name}` and WS `macro`. */
class CompanionServerMacroTest {

    private lateinit var client: HttpClient
    private var collector: CoroutineScope? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val walkIn = Macro("macro1", "Walk in", listOf(Action.ClearAll, Action.Wait(1.0)))
    private val closing = Macro("macro2", "Closing")

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer(shutdownGraceMs = 0)
            server.start(port = testPort(39_955))
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
        server.macros = listOf(walkIn, closing)
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

    private fun nextRun(): CompletableDeferred<String> {
        val next = CompletableDeferred<String>()
        val scope = CoroutineScope(Dispatchers.IO).also { collector = it }
        scope.launch { server.onMacro.collect { next.complete(it) } }
        runBlocking { withTimeoutOrNull(5_000) { server.onMacro.subscriptionCount.first { it > 0 } } }
        return next
    }

    private fun CompletableDeferred<String>.awaited(): String? =
        runBlocking { withTimeoutOrNull(2_000) { await() } }

    @Test
    fun `the macros are listed with how many actions each holds`() {
        val body = runBlocking { client.get(url(Constants.ENDPOINT_MACROS)).bodyAsText() }
        val listed = json.decodeFromString(ListSerializer(MacroDto.serializer()), body)
        assertEquals(listOf(MacroDto("macro1", "Walk in", 2), MacroDto("macro2", "Closing", 0)), listed)
    }

    @Test
    fun `a macro runs by id, or by name in any case and spacing`() {
        listOf("macro1" to "macro1", "WALK%20IN" to "macro1", "closing" to "macro2").forEach { (name, id) ->
            val next = nextRun()
            assertEquals(HttpStatusCode.OK, post("${Constants.ENDPOINT_MACRO}/$name").status, name)
            assertEquals(id, next.awaited(), name)
            close()
            client = HttpClient(CIO) { install(WebSockets) }
        }
    }

    @Test
    fun `an unknown macro is refused`() {
        assertEquals(HttpStatusCode.NotFound, post("${Constants.ENDPOINT_MACRO}/choir").status)
    }

    @Test
    fun `the API key is asked for, on the list and on a run`() {
        server.updateApiKey(enabled = true, key = "secret")
        assertEquals(HttpStatusCode.Unauthorized, post("${Constants.ENDPOINT_MACRO}/macro1").status)
        assertEquals(HttpStatusCode.OK, post("${Constants.ENDPOINT_MACRO}/macro1", apiKey = "secret").status)
        val listed = runBlocking { client.get(url(Constants.ENDPOINT_MACROS)).status }
        assertEquals(HttpStatusCode.Unauthorized, listed)
    }

    @Test
    fun `over the socket a macro runs by name, and an unknown or missing one is refused`() {
        val next = nextRun()
        val acks = acksFor(
            setOf("ok", "unknown", "missing"),
            command("""{"name":"Walk in"}""", "ok"),
            command("""{"name":"choir"}""", "unknown"),
            command("""{}""", "missing"),
        )
        assertEquals("macro1", next.awaited())
        assertTrue(acks.first { it.commandId == "ok" }.ok)
        assertFalse(acks.first { it.commandId == "unknown" }.ok)
        assertFalse(acks.first { it.commandId == "missing" }.ok)
    }

    @Test
    fun `outside dev mode the unfinished features refuse, and clearing everything still works`() {
        server.devMode = false
        val gated = listOf(
            "POST ${Constants.ENDPOINT_MACRO}/macro1",
            "POST ${Constants.ENDPOINT_TAKE}",
            "POST ${Constants.ENDPOINT_CLEAR}?layer=slide",
            "POST ${Constants.ENDPOINT_CLEAR}?group=anything",
            "POST ${Constants.ENDPOINT_MESSAGE}",
            "POST ${Constants.ENDPOINT_PROPS}/logo/on",
            "GET ${Constants.ENDPOINT_MACROS}",
            "GET ${Constants.ENDPOINT_PROPS}",
            "GET ${Constants.ENDPOINT_CLEAR_GROUPS}",
        )
        gated.forEach { request ->
            val (verb, path) = request.split(" ")
            val response = runBlocking { if (verb == "GET") client.get(url(path)) else client.post(url(path)) }
            assertEquals(HttpStatusCode.Forbidden, response.status, request)
            assertTrue("dev mode only" in runBlocking { response.bodyAsText() }, request)
        }
        assertEquals(HttpStatusCode.OK, post(Constants.ENDPOINT_CLEAR).status, "clearing everything is not gated")

        val ack = acksFor(setOf("m"), command("""{"name":"Walk in"}""", "m")).single()
        assertFalse(ack.ok)
        assertEquals("dev_mode_only", ack.reason)
    }

    private fun command(payload: String, commandId: String): String = json.encodeToString(
        WebSocketMessage.serializer(),
        WebSocketMessage(type = Constants.WS_CMD_MACRO, payload = payload, commandId = commandId),
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
