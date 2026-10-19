package org.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a trusted device may do: the permissions the operator switches on and off per device, held
 * on every REST route and WebSocket command they gate.
 *
 * Each gated route is driven with the one permission it needs switched off, so a route wired to the
 * wrong permission -- or to none -- fails here by name. A request with no device id must never be
 * refused for its permissions: that is what a Bitfocus Companion trigger URL sends.
 */
class CompanionServerDevicePermissionsTest {

    private lateinit var client: HttpClient
    private var collectors: CoroutineScope? = null
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val DEVICE = "tablet-1"

        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer(shutdownGraceMs = 0)
            server.start(port = testPort(39_960))
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

        /** Every gated REST route, with the permission it needs. */
        val GATED_ROUTES = listOf(
            "/api/songs/1/select" to DevicePermission.CONTROL_OUTPUT,
            "/api/bible/select" to DevicePermission.CONTROL_OUTPUT,
            "/api/presentations/x/select" to DevicePermission.CONTROL_OUTPUT,
            "/api/pictures/select" to DevicePermission.CONTROL_OUTPUT,
            "/api/clear" to DevicePermission.CONTROL_OUTPUT,
            "/api/take" to DevicePermission.CONTROL_OUTPUT,
            "/api/lowerthirds/x/run" to DevicePermission.CONTROL_OUTPUT,
            "/api/lowerthirds/x/show" to DevicePermission.CONTROL_OUTPUT,
            "/api/lowerthirds/hide" to DevicePermission.CONTROL_OUTPUT,
            "/api/atem/still/x" to DevicePermission.CONTROL_OUTPUT,
            "/api/atem/clip/x" to DevicePermission.CONTROL_OUTPUT,
            "/api/atem/key/on" to DevicePermission.CONTROL_OUTPUT,
            "/api/atem/key/off" to DevicePermission.CONTROL_OUTPUT,
            "/api/message" to DevicePermission.CONTROL_OUTPUT,
            "/api/props/x/on" to DevicePermission.CONTROL_OUTPUT,
            "/api/macro/x" to DevicePermission.CONTROL_OUTPUT,
            "/api/schedule/add" to DevicePermission.ADD_TO_SCHEDULE,
            "/api/schedule/add-batch" to DevicePermission.ADD_TO_SCHEDULE,
            "/api/project" to DevicePermission.GO_LIVE,
            "/api/presentations/upload" to DevicePermission.UPLOAD_FILES,
            "/api/pictures/upload" to DevicePermission.UPLOAD_FILES,
            "/api/media/upload" to DevicePermission.UPLOAD_FILES,
        )
    }

    @BeforeTest
    fun resetState() {
        client = HttpClient(CIO) { install(WebSockets) }
        server.updateApiKey(enabled = false, key = "")
        server.blockedClientIds = emptySet()
        server.devicePermissions = emptyMap()
    }

    @AfterTest
    fun closeClient() {
        runCatching { collectors?.cancel() }
        collectors = null
        runCatching { client.close() }
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    private fun posting(path: String, deviceId: String? = DEVICE): HttpResponse = runBlocking {
        client.post(url(path)) {
            deviceId?.let { header(Constants.HEADER_DEVICE_ID, it) }
            setBody("{}")
        }
    }

    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }

    private fun denying(vararg permissions: DevicePermission) {
        server.devicePermissions = mapOf(
            DEVICE to permissions.fold(DevicePermissions.ALL) { p, denied -> p.with(denied, false) },
        )
    }

    // ── The model ────────────────────────────────────────────────────────────────

    @Test
    fun `a device with no permissions of its own may do everything`() {
        DevicePermission.entries.forEach { permission ->
            assertTrue(DevicePermissions.ALL.allows(permission), "$permission")
            assertTrue(server.permits(DEVICE, permission), "$permission")
        }
    }

    @Test
    fun `switching a permission off and on again leaves the device as it was`() {
        val off = DevicePermissions.ALL.with(DevicePermission.GO_LIVE, false)

        assertFalse(off.allows(DevicePermission.GO_LIVE))
        assertTrue(off.allows(DevicePermission.CONTROL_OUTPUT), "only the one switched off is refused")
        assertEquals(DevicePermissions.ALL, off.with(DevicePermission.GO_LIVE, true))
    }

    @Test
    fun `a blocked device may do nothing, and an anonymous one is never refused for its permissions`() {
        server.blockedClientIds = setOf(DEVICE)
        assertFalse(server.permits(DEVICE, DevicePermission.CONTROL_OUTPUT))

        server.devicePermissions = mapOf("" to DevicePermissions(DevicePermission.entries.toSet()))
        assertTrue(server.permits("", DevicePermission.CONTROL_OUTPUT))
    }

    @Test
    fun `every command that changes the screen or the schedule names the permission it needs`() {
        val screen = listOf(
            Constants.WS_CMD_SELECT_SONG, Constants.WS_CMD_SELECT_PICTURE, Constants.WS_CMD_SELECT_SONG_SECTION,
            Constants.WS_CMD_SELECT_SLIDE, Constants.WS_CMD_SELECT_BIBLE_VERSE, Constants.WS_CMD_CLEAR,
            Constants.WS_CMD_TAKE, Constants.WS_CMD_BIBLE_HOLD, Constants.WS_CMD_MESSAGE, Constants.WS_CMD_PROP,
            Constants.WS_CMD_MACRO, Constants.WS_CMD_NEXT_PICTURE, Constants.WS_CMD_PREVIOUS_PICTURE,
            Constants.WS_CMD_NEXT_SLIDE, Constants.WS_CMD_PREVIOUS_SLIDE, Constants.WS_CMD_MEDIA_PLAY_PAUSE,
            Constants.WS_CMD_MEDIA_STOP, Constants.WS_CMD_MEDIA_SEEK_FORWARD, Constants.WS_CMD_MEDIA_SEEK_BACKWARD,
            Constants.WS_CMD_MEDIA_SEEK_TO, Constants.WS_CMD_MEDIA_SET_VOLUME, Constants.WS_CMD_MEDIA_MUTE_TOGGLE,
        )
        screen.forEach { assertEquals(DevicePermission.CONTROL_OUTPUT, commandPermission(it), it) }
        assertEquals(DevicePermission.ADD_TO_SCHEDULE, commandPermission(Constants.WS_CMD_ADD_TO_SCHEDULE))
        assertEquals(DevicePermission.ADD_TO_SCHEDULE, commandPermission(Constants.WS_CMD_ADD_BATCH_TO_SCHEDULE))
        assertEquals(DevicePermission.REMOVE_FROM_SCHEDULE, commandPermission(Constants.WS_CMD_REMOVE_FROM_SCHEDULE))
        assertEquals(DevicePermission.GO_LIVE, commandPermission(Constants.WS_CMD_PROJECT))
        assertEquals(null, commandPermission("something_new"))
    }

    // ── REST ─────────────────────────────────────────────────────────────────────

    @Test
    fun `each gated route refuses a device without the permission it needs, and only that one`() {
        val wrong = GATED_ROUTES.mapNotNull { (path, permission) ->
            denying(permission)
            val refused = posting(path)
            val body = refused.text()
            val refusedRight = refused.status == HttpStatusCode.Forbidden && "not_permitted" in body
            // With every *other* permission off, the route must not be refused for permissions.
            denying(*(DevicePermission.entries - permission).toTypedArray())
            val other = posting(path).text()
            when {
                !refusedRight -> "$path without $permission answered ${refused.status} $body"
                "not_permitted" in other -> "$path is refused for a permission it does not need"
                else -> null
            }
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun `a request with no device id is never refused for its permissions`() {
        denying(*DevicePermission.entries.toTypedArray())

        val refused = GATED_ROUTES.filter { (path, _) -> "not_permitted" in posting(path, deviceId = null).text() }

        assertEquals(emptyList(), refused)
    }

    @Test
    fun `a blocked device is refused by name on the instant routes`() {
        server.blockedClientIds = setOf(DEVICE)

        val response = posting("/api/clear")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue("\"blocked\"" in response.text(), response.text())
    }

    @Test
    fun `the API key is still checked first`() {
        server.updateApiKey(enabled = true, key = "k")
        denying(DevicePermission.CONTROL_OUTPUT)

        assertEquals(HttpStatusCode.Unauthorized, posting("/api/clear").status)
    }

    @Test
    fun `status tells a device what it may do`() {
        server.updateFileUploadEnabled(true)
        denying(DevicePermission.GO_LIVE, DevicePermission.UPLOAD_FILES)

        val status = runBlocking {
            client.get(url(Constants.ENDPOINT_STATUS)) { header(Constants.HEADER_DEVICE_ID, DEVICE) }.bodyAsText()
        }
        val permissions = json.decodeFromString(StatusResponse.serializer(), status).permissions

        assertEquals(
            DevicePermissionsDto(
                canPresent = true,
                canAddToSchedule = true,
                canRemoveFromSchedule = true,
                canGoLive = false,
                canUploadFiles = false,
                maxMediaUploadMb = permissions.maxMediaUploadMb,
            ),
            permissions,
        )
    }

    // ── WebSocket ────────────────────────────────────────────────────────────────

    @Test
    fun `a command the device may not send is refused by name and never reaches the app`() {
        val cleared = CompletableDeferred<Unit>()
        collecting(server.onClear) { cleared.complete(Unit) }
        denying(DevicePermission.CONTROL_OUTPUT)

        val ack = sendAsDevice(command(Constants.WS_CMD_CLEAR, "cmd-clear")).ackFor("cmd-clear")

        // The ack is the positive signal: the refusal is sent and the dispatch skipped outright.
        assertEquals(false, assertNotNull(ack).ok)
        assertEquals("not_permitted", ack.reason)
        assertFalse(cleared.isCompleted)
    }

    @Test
    fun `a device without go live may still clear the screen`() {
        val cleared = CompletableDeferred<Unit>()
        collecting(server.onClear) { cleared.complete(Unit) }
        denying(DevicePermission.GO_LIVE)

        val ack = sendAsDevice(command(Constants.WS_CMD_CLEAR, "cmd-ok")).ackFor("cmd-ok")

        assertEquals(true, assertNotNull(ack).ok)
        assertNotNull(runBlocking { withTimeoutOrNull(2_000) { cleared.await() } })
    }

    @Test
    fun `projecting without go live is refused before the operator is asked`() {
        val asked = CompletableDeferred<Unit>()
        collecting(server.onProject) { asked.complete(Unit) }
        denying(DevicePermission.GO_LIVE)

        val ack = sendAsDevice(
            command(Constants.WS_CMD_PROJECT, "cmd-project", payload = """{"item":{"songNumber":1}}"""),
        ).ackFor("cmd-project")

        assertEquals("not_permitted", assertNotNull(ack).reason)
        assertFalse(asked.isCompleted, "a refused request must not reach the approval prompt")
    }

    private fun <T> collecting(flow: MutableSharedFlow<T>, onEach: (T) -> Unit) {
        val scope = collectors ?: CoroutineScope(Dispatchers.IO).also { collectors = it }
        scope.launch { flow.collect { onEach(it) } }
        runBlocking {
            withTimeoutOrNull(5_000) { flow.subscriptionCount.first { it > 0 } }
                ?: error("collector never subscribed")
        }
    }

    private fun command(type: String, commandId: String, payload: String = ""): String =
        json.encodeToString(
            WebSocketMessage.serializer(),
            WebSocketMessage(type = type, payload = payload, commandId = commandId),
        )

    /** Connects as [DEVICE], drains the connect snapshot, sends [frames] and collects what comes back. */
    private fun sendAsDevice(vararg frames: String, quietMs: Long = 250): List<String> = runBlocking {
        val received = mutableListOf<String>()
        withTimeoutOrNull(quietMs + 10_000) {
            client.webSocket(
                urlString = "ws://127.0.0.1:$port${Constants.ENDPOINT_WS}",
                request = { header(Constants.HEADER_DEVICE_ID, DEVICE) },
            ) {
                while (withTimeoutOrNull(quietMs) { incoming.receive() } != null) Unit
                frames.forEach { send(Frame.Text(it)) }
                while (true) {
                    val frame = withTimeoutOrNull(quietMs) { incoming.receive() } ?: break
                    if (frame is Frame.Text) received.add(frame.readText())
                }
            }
        }
        received
    }

    private fun List<String>.ackFor(commandId: String): CommandAckPayload? =
        mapNotNull { runCatching { json.decodeFromString(WebSocketMessage.serializer(), it) }.getOrNull() }
            .filter { it.type == Constants.WS_EVENT_COMMAND_ACK }
            .mapNotNull { message ->
                runCatching { json.decodeFromString(CommandAckPayload.serializer(), message.payload) }.getOrNull()
            }
            .firstOrNull { it.commandId == commandId }
}
