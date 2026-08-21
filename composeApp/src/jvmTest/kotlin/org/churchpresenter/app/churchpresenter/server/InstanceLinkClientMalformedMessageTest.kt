package org.churchpresenter.app.churchpresenter.server

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InstanceLinkClientMalformedMessageTest {

    private val clients = mutableListOf<InstanceLinkClient>()
    private var fakePrimary: FakePrimary? = null

    @AfterTest
    fun cleanUp() {
        clients.forEach { runCatching { it.dispose() } }
        clients.clear()
        fakePrimary?.stop()
        fakePrimary = null
    }

    private class FakePrimary {
        @Volatile var session: DefaultWebSocketServerSession? = null

        var port: Int = 0
            private set

        private val server = embeddedServer(Netty, port = 0) {
            install(WebSockets)
            routing {
                webSocket(Constants.ENDPOINT_WS) {
                    session = this
                    try {
                        while (incoming.receiveCatching().isSuccess) Unit
                    } catch (_: Exception) {
                        // the client disconnected, which every test here does on cleanup
                    }
                }
            }
        }

        fun start() {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        suspend fun send(text: String) {
            val deadline = System.currentTimeMillis() + 5_000
            while (session == null && System.currentTimeMillis() < deadline) delay(10)
            (session ?: error("client never connected")).send(Frame.Text(text))
        }

        fun stop() = server.stop(0, 0)
    }

    private fun startFake(): FakePrimary = FakePrimary().also { it.start(); fakePrimary = it }

    private fun connectedClient(
        fake: FakePrimary,
        onScheduleUpdated: (List<ScheduleItemDto>) -> Unit,
    ): InstanceLinkClient {
        val client = InstanceLinkClient(
            onStatusChanged = {},
            onScheduleUpdated = onScheduleUpdated,
            onLiveStateUpdated = {},
            onDisplayCleared = {},
            onSongSectionSelected = {},
            onPresentationSlideChanged = { _, _, _, _, _ -> },
            onSongsUpdated = {},
        )
        clients += client
        client.connect(
            host = "127.0.0.1",
            port = fake.port,
            apiKey = "",
            deviceId = "test-device",
            reconnectDelayMs = 60_000,
        )
        return client
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    @Test
    fun `a completely malformed envelope is dropped, and a later valid message still arrives`() = runBlocking {
        val fake = startFake()
        val updates = mutableListOf<List<ScheduleItemDto>>()
        connectedClient(fake, onScheduleUpdated = { updates.add(it) })

        fake.send("not json at all")
        fake.send("""{"type":"${Constants.WS_EVENT_SCHEDULE_UPDATED}","payload":"{\"items\":[],\"total\":0}"}""")

        awaitUntil("the schedule update to arrive") { updates.isNotEmpty() }
        assertEquals(1, updates.size, "the malformed envelope before it must not have produced its own update")
    }

    @Test
    fun `a recognised type with an unparseable payload is dropped, and a later valid message still arrives`() =
        runBlocking {
        val fake = startFake()
        val updates = mutableListOf<List<ScheduleItemDto>>()
        connectedClient(fake, onScheduleUpdated = { updates.add(it) })

        fake.send("""{"type":"${Constants.WS_EVENT_SCHEDULE_UPDATED}","payload":"not an object"}""")
        fake.send("""{"type":"${Constants.WS_EVENT_SCHEDULE_UPDATED}","payload":"{\"items\":[],\"total\":0}"}""")

        awaitUntil("the schedule update to arrive") { updates.isNotEmpty() }
        assertEquals(1, updates.size, "the unparseable payload before it must not have produced its own update")
    }
}
