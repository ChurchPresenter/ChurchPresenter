package org.churchpresenter.app.churchpresenter.viewmodel

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Switching OBS scenes as the content changes, driven against a stand-in OBS.
 *
 * This manager speaks the obs-websocket v5 handshake by hand: OBS sends `Hello` (op 0), the client
 * answers `Identify` (op 1) — carrying an authentication digest when OBS asked for one — and OBS
 * replies `Identified` (op 2) before anything else may be sent. Every step is a place to get the
 * protocol subtly wrong in a way that only shows up against a real OBS, so a throwaway Ktor server
 * plays OBS here and the frames are inspected as they arrive.
 *
 * The digest is the part worth pinning down: it is `base64(sha256(base64(sha256(password + salt)) +
 * challenge))`, and swapping the two rounds or concatenating in the other order still produces a
 * plausible-looking base64 string that OBS simply rejects.
 */
class OBSWebSocketManagerTest {

    private val managers = mutableListOf<OBSWebSocketManager>()
    private val servers = mutableListOf<FakeObs>()

    @AfterTest
    fun cleanUp() {
        managers.forEach { runCatching { it.disconnect() } }
        managers.clear()
        servers.forEach { runCatching { it.stop() } }
        servers.clear()
    }

    /**
     * A stand-in OBS. Sends [helloOp] as its greeting (0 in real life), optionally asking for
     * authentication, then answers the client's Identify with [identifiedOp] (2 in real life).
     */
    private class FakeObs(
        val port: Int,
        private val helloOp: Int = 0,
        private val identifiedOp: Int = 2,
        private val salt: String? = null,
        private val challenge: String? = null,
    ) {
        val received = LinkedBlockingQueue<String>()
        val sessions = CopyOnWriteArrayList<DefaultWebSocketServerSession>()

        private val hello: String
            get() {
                val auth = if (salt != null && challenge != null) {
                    ""","authentication":{"challenge":"$challenge","salt":"$salt"}"""
                } else {
                    ""
                }
                return """{"op":$helloOp,"d":{"obsWebSocketVersion":"5.1.0","rpcVersion":1$auth}}"""
            }

        private val server = embeddedServer(Netty, port = port) {
            install(WebSockets)
            routing {
                webSocket("/") {
                    sessions.add(this)
                    try {
                        send(Frame.Text(hello))
                        val identify = incoming.receive()
                        if (identify is Frame.Text) received.add(identify.readText())
                        send(Frame.Text("""{"op":$identifiedOp,"d":{"negotiatedRpcVersion":1}}"""))
                        for (frame in incoming) if (frame is Frame.Text) received.add(frame.readText())
                    } catch (_: Exception) {
                        // the client hung up mid-handshake, which several tests do on purpose
                    } finally {
                        sessions.remove(this)
                    }
                }
            }
        }

        fun start() = server.start(wait = false)
        fun dropConnections() = runBlocking { sessions.toList().forEach { it.close() } }
        fun stop() = server.stop(0, 0)
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun startObs(
        helloOp: Int = 0,
        identifiedOp: Int = 2,
        salt: String? = null,
        challenge: String? = null,
    ): FakeObs = FakeObs(freePort(), helloOp, identifiedOp, salt, challenge).also {
        servers.add(it)
        it.start()
    }

    private fun manager() = OBSWebSocketManager().also { managers.add(it) }

    private fun awaitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun FakeObs.nextFrame(timeoutSeconds: Long = 10): JsonObject =
        Json.parseToJsonElement(
            received.poll(timeoutSeconds, TimeUnit.SECONDS) ?: throw AssertionError("OBS was sent nothing")
        ).jsonObject

    /** Connects [this] to [obs] and waits for the handshake to complete. */
    private fun OBSWebSocketManager.connectTo(obs: FakeObs, password: String = "") {
        connect("127.0.0.1", obs.port, password)
        awaitUntil("the OBS handshake to complete") {
            status.value == OBSWebSocketManager.ConnectionStatus.CONNECTED
        }
    }

    /** The digest obs-websocket v5 expects, computed the way its documentation states it. */
    private fun expectedAuth(password: String, salt: String, challenge: String): String {
        val sha = MessageDigest.getInstance("SHA-256")
        val secret = Base64.getEncoder().encodeToString(sha.digest((password + salt).toByteArray(Charsets.UTF_8)))
        sha.reset()
        return Base64.getEncoder().encodeToString(sha.digest((secret + challenge).toByteArray(Charsets.UTF_8)))
    }

    // ── Starting state ──────────────────────────────────────────────────────────

    @Test
    fun `a new manager is not connected to anything`() {
        val obs = manager()
        assertEquals(OBSWebSocketManager.ConnectionStatus.DISCONNECTED, obs.status.value)
        assertEquals("", obs.errorMessage.value)
    }

    // ── Handshake ───────────────────────────────────────────────────────────────

    @Test
    fun `an obs that needs no password is identified straight away`() {
        val server = startObs()
        val obs = manager()

        obs.connectTo(server)

        val identify = server.nextFrame()
        assertEquals(1, identify["op"]?.jsonPrimitive?.int, "Identify is opcode 1")
        assertEquals(1, identify["d"]?.jsonObject?.get("rpcVersion")?.jsonPrimitive?.int)
        assertNull(
            identify["d"]?.jsonObject?.get("authentication"),
            "sending a digest OBS never asked for is rejected by OBS",
        )
    }

    @Test
    fun `an obs that asks for a password gets the expected digest`() {
        val server = startObs(salt = "c2FsdA==", challenge = "Y2hhbGxlbmdl")
        val obs = manager()

        obs.connectTo(server, password = "hunter2")

        val identify = server.nextFrame()
        assertEquals(
            expectedAuth("hunter2", "c2FsdA==", "Y2hhbGxlbmdl"),
            identify["d"]?.jsonObject?.get("authentication")?.jsonPrimitive?.content,
            "a digest built in the wrong order still looks like base64 — OBS just refuses it",
        )
    }

    @Test
    fun `a different password produces a different digest`() {
        val server = startObs(salt = "c2FsdA==", challenge = "Y2hhbGxlbmdl")
        manager().connectTo(server, password = "correct-horse")

        val sent = server.nextFrame()["d"]?.jsonObject?.get("authentication")?.jsonPrimitive?.content

        assertEquals(expectedAuth("correct-horse", "c2FsdA==", "Y2hhbGxlbmdl"), sent)
        assertTrue(sent != expectedAuth("hunter2", "c2FsdA==", "Y2hhbGxlbmdl"))
    }

    @Test
    fun `no password configured means no digest, even when obs offers one`() {
        // OBS only enforces auth when it is turned on there; with the field left blank here the
        // client must still attempt an unauthenticated identify rather than sending a digest of "".
        val server = startObs(salt = "c2FsdA==", challenge = "Y2hhbGxlbmdl")

        manager().connectTo(server, password = "")

        assertNull(server.nextFrame()["d"]?.jsonObject?.get("authentication"))
    }

    @Test
    fun `a greeting that is not Hello is refused`() {
        val server = startObs(helloOp = 5) // an event, not a greeting
        val obs = manager()

        obs.connect("127.0.0.1", server.port, "")

        awaitUntil("the bad greeting to be reported") {
            obs.status.value == OBSWebSocketManager.ConnectionStatus.ERROR
        }
        assertTrue(obs.errorMessage.value.isNotEmpty(), "the operator needs to know why it will not connect")
    }

    @Test
    fun `a rejected password is reported as such`() {
        val server = startObs(identifiedOp = 5) // anything but Identified
        val obs = manager()

        obs.connect("127.0.0.1", server.port, "wrong-password")

        awaitUntil("the rejection to be reported") {
            obs.status.value == OBSWebSocketManager.ConnectionStatus.ERROR
        }
        assertTrue(
            obs.errorMessage.value.contains("password", ignoreCase = true),
            "the message should point at the password: \"${obs.errorMessage.value}\"",
        )
    }

    @Test
    fun `an obs that is not running is reported rather than left spinning`() {
        val obs = manager()

        obs.connect("127.0.0.1", freePort(), "") // nothing listening there

        awaitUntil("the failed connection to be reported") {
            obs.status.value == OBSWebSocketManager.ConnectionStatus.ERROR
        }
        assertTrue(obs.errorMessage.value.isNotEmpty())
    }

    // ── Switching scenes ────────────────────────────────────────────────────────

    @Test
    fun `switching scenes sends the obs request`() {
        val server = startObs()
        val obs = manager()
        obs.connectTo(server)
        server.nextFrame() // the Identify

        obs.setScene("Worship")

        val request = server.nextFrame()
        assertEquals(6, request["op"]?.jsonPrimitive?.int, "Request is opcode 6")
        val data = request["d"]!!.jsonObject
        assertEquals("SetCurrentProgramScene", data["requestType"]?.jsonPrimitive?.content)
        assertEquals("Worship", data["requestData"]?.jsonObject?.get("sceneName")?.jsonPrimitive?.content)
        assertTrue(data["requestId"]?.jsonPrimitive?.content?.isNotEmpty() == true, "OBS requires a request id")
    }

    @Test
    fun `each scene switch gets its own request id`() {
        val server = startObs()
        val obs = manager()
        obs.connectTo(server)
        server.nextFrame()

        obs.setScene("Worship")
        obs.setScene("Sermon")

        val ids = List(2) { server.nextFrame()["d"]!!.jsonObject["requestId"]!!.jsonPrimitive.content }
        assertEquals(2, ids.toSet().size, "OBS matches responses to requests by id; reusing one loses a response")
    }

    @Test
    fun `a scene name with spaces and punctuation goes through unmangled`() {
        val server = startObs()
        val obs = manager()
        obs.connectTo(server)
        server.nextFrame()

        obs.setScene("""Lower Third — "Guest Speaker"""")

        val data = server.nextFrame()["d"]!!.jsonObject
        assertEquals(
            """Lower Third — "Guest Speaker"""",
            data["requestData"]?.jsonObject?.get("sceneName")?.jsonPrimitive?.content,
            "scene names are user-typed; quoting has to be handled by the encoder, not by luck",
        )
    }

    @Test
    fun `switching scenes before connecting does nothing`() {
        val obs = manager()

        obs.setScene("Worship")

        assertEquals(OBSWebSocketManager.ConnectionStatus.DISCONNECTED, obs.status.value)
    }

    @Test
    fun `switching scenes after disconnecting does nothing`() {
        val server = startObs()
        val obs = manager()
        obs.connectTo(server)
        server.nextFrame()
        obs.disconnect()
        awaitUntil("the link to close") { server.sessions.isEmpty() }

        obs.setScene("Ghost")

        // A second, live manager gives the positive signal: if the disconnected one had sent
        // anything, it would be sitting ahead of this in the server's queue.
        val live = manager()
        live.connectTo(server)
        server.nextFrame() // its Identify
        live.setScene("Real")

        assertEquals(
            "Real",
            server.nextFrame()["d"]!!.jsonObject["requestData"]?.jsonObject?.get("sceneName")?.jsonPrimitive?.content,
            "a disconnected manager must not queue requests",
        )
    }

    // ── Disconnecting ───────────────────────────────────────────────────────────

    @Test
    fun `disconnecting closes the link and clears the status`() {
        val server = startObs()
        val obs = manager()
        obs.connectTo(server)

        obs.disconnect()

        assertEquals(OBSWebSocketManager.ConnectionStatus.DISCONNECTED, obs.status.value)
        assertEquals("", obs.errorMessage.value)
        awaitUntil("OBS to see the client go") { server.sessions.isEmpty() }
    }

    @Test
    fun `disconnecting clears a previous error`() {
        val obs = manager()
        obs.connect("127.0.0.1", freePort(), "")
        awaitUntil("the failure") { obs.status.value == OBSWebSocketManager.ConnectionStatus.ERROR }

        obs.disconnect()

        assertEquals("", obs.errorMessage.value, "a stale error beside a disconnected chip is noise")
    }

    @Test
    fun `obs going away drops the status back to disconnected`() {
        val server = startObs()
        val obs = manager()
        obs.connectTo(server)

        server.dropConnections()

        awaitUntil("the dropped link to be noticed") {
            obs.status.value == OBSWebSocketManager.ConnectionStatus.DISCONNECTED
        }
    }

    @Test
    fun `connecting again replaces the previous link`() {
        val first = startObs()
        val second = startObs()
        val obs = manager()
        obs.connectTo(first)

        // NB: not connectTo() here — the status is already CONNECTED from the first link, so
        // waiting on it would pass instantly without the second handshake having happened.
        obs.connect("127.0.0.1", second.port, "")

        awaitUntil("the second link to be established") { second.sessions.size == 1 }
        awaitUntil("the first link to be dropped") { first.sessions.isEmpty() }
        assertEquals(OBSWebSocketManager.ConnectionStatus.CONNECTED, obs.status.value)
    }

    @Test
    fun `disconnecting when never connected is harmless`() {
        val obs = manager()
        obs.disconnect()
        obs.disconnect()
        assertEquals(OBSWebSocketManager.ConnectionStatus.DISCONNECTED, obs.status.value)
    }
}
