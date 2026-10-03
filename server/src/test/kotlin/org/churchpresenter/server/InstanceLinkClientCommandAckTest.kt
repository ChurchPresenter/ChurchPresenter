package org.churchpresenter.server

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstanceLinkClientCommandAckTest {

    private val clients = mutableListOf<InstanceLinkClient>()
    private var fakePrimary: FakePrimary? = null
    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest
    fun cleanUp() {
        clients.forEach { runCatching { it.dispose() } }
        clients.clear()
        fakePrimary?.stop()
        fakePrimary = null
    }

    private inner class FakePrimary(
        private val greeting: List<Frame>,
        private val verdict: (type: String) -> CommandAckPayload?,
    ) {
        var port: Int = 0
            private set

        private val server = embeddedServer(Netty, port = 0) {
            install(WebSockets)
            routing {
                webSocket(Constants.ENDPOINT_WS) {
                    greeting.forEach { send(it) }
                    runCatching {
                        for (frame in incoming) {
                            (frame as? Frame.Text)?.let { ackFrame(it.readText()) }?.let { send(it) }
                        }
                    }
                }
            }
        }

        private fun ackFrame(text: String): Frame? {
            val command = json.decodeFromString(WebSocketMessage.serializer(), text)
            val ack = verdict(command.type)?.copy(commandId = command.commandId.orEmpty()) ?: return null
            val payload = json.encodeToString(CommandAckPayload.serializer(), ack)
            return Frame.Text(json.encodeToString(
                WebSocketMessage.serializer(),
                WebSocketMessage(type = Constants.WS_EVENT_COMMAND_ACK, payload = payload),
            ))
        }

        fun start() {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        fun stop() = server.stop(0, 0)
    }

    private class Follower {
        @Volatile var status = InstanceLinkStatus.DISCONNECTED
        val failures = CopyOnWriteArrayList<Pair<String, String?>>()
        val schedules = CopyOnWriteArrayList<List<ScheduleItemDto>>()
    }

    private fun connect(
        greeting: List<Frame> = emptyList(),
        verdict: (String) -> CommandAckPayload? = { null },
    ): Pair<InstanceLinkClient, Follower> {
        val fake = FakePrimary(greeting, verdict).also { it.start(); fakePrimary = it }
        val follower = Follower()
        val client = InstanceLinkClient(
            onStatusChanged = { follower.status = it },
            onScheduleUpdated = { follower.schedules += it },
            onLiveStateUpdated = {},
            onDisplayCleared = {},
            onSongSectionSelected = {},
            onPresentationSlideChanged = { _, _, _, _, _ -> },
            onCommandFailed = { type, reason -> follower.failures += type to reason },
        )
        clients += client
        client.connect("127.0.0.1", fake.port, apiKey = "", deviceId = "follower", reconnectDelayMs = 60_000)
        awaitUntil("the link to come up") { follower.status == InstanceLinkStatus.CONNECTED }
        return client to follower
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun ack(ok: Boolean, reason: String? = null) = CommandAckPayload(commandId = "", ok = ok, reason = reason)

    @Test
    fun `a command the primary rejects is reported with the primary's reason`() {
        val (client, follower) = connect { ack(ok = false, reason = "busy") }

        client.sendRemoveFromSchedule("item-1")

        awaitUntil("the rejection to be reported") { follower.failures.isNotEmpty() }
        assertEquals(listOf(Constants.WS_CMD_REMOVE_FROM_SCHEDULE to "busy"), follower.failures.toList())
    }

    @Test
    fun `a command the primary accepts is never reported as failed`() {
        val (client, follower) = connect { type ->
            if (type == Constants.WS_CMD_REMOVE_FROM_SCHEDULE) ack(ok = true) else ack(ok = false, reason = "denied")
        }

        client.sendRemoveFromSchedule("item-1")
        client.sendAddToSchedule(
            ScheduleItem.LabelItem(id = "l1", text = "Welcome", textColor = "#fff", backgroundColor = "#000"),
        )

        awaitUntil("the rejected add to be reported") { follower.failures.isNotEmpty() }
        assertEquals(listOf(Constants.WS_CMD_ADD_TO_SCHEDULE to "denied"), follower.failures.toList())
    }

    @Test
    fun `binary frames from the primary are skipped and the text after them still arrives`() {
        val schedule = """{"type":"${Constants.WS_EVENT_SCHEDULE_UPDATED}","payload":"{\"items\":[],\"total\":0}"}"""
        val (_, follower) = connect(greeting = listOf(Frame.Binary(true, byteArrayOf(1, 2, 3)), Frame.Text(schedule)))

        awaitUntil("the schedule after the binary frame") { follower.schedules.isNotEmpty() }
        assertEquals(InstanceLinkStatus.CONNECTED, follower.status)
        assertTrue(follower.schedules.single().isEmpty())
        assertTrue(follower.failures.isEmpty())
    }
}
