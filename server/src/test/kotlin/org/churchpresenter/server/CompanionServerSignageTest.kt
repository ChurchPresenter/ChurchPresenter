package org.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Manifest
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.ScreenSettings
import org.churchpresenter.signage.core.ServerMessage
import org.churchpresenter.signage.core.SignageJson
import org.churchpresenter.signage.core.UpdateState
import org.churchpresenter.signage.core.sha256Hex
import org.churchpresenter.signage.hub.AgentUpdates
import org.churchpresenter.signage.hub.PendingScreen
import org.churchpresenter.signage.hub.SignageHub
import java.io.IOException
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The signage players' routes on a real running server: pairing over the control channel, a paired
 * player's session, its manifest and files, and the player build it is offered. Dev mode only.
 */
class CompanionServerSignageTest {
    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private var port = 0
    private val dir = Files.createTempDirectory("cp-signage-server")
    private val scope = CoroutineScope(Job())
    private val build = "the linux build".toByteArray()
    private val updates = signedUpdates()
    private val hub = SignageHub(dir, "Main PC", scope, updates = updates)

    @BeforeTest
    fun startServer() {
        runBlocking { hub.registry.load() }
        server = CompanionServer(shutdownGraceMs = 0)
        server.signageEnabled = true
        server.signage = hub
        server.start(port = testPort(39_970))
        port = runBlocking {
            withTimeoutOrNull(WAIT_MS) {
                while (!server.isRunning.value || server.serverUrl.value.isBlank()) delay(POLL_MS)
                server.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")
        client = HttpClient(CIO) { install(WebSockets) }
    }

    @AfterTest
    fun stopServer() {
        runCatching { client.close() }
        runCatching { server.stop() }
        scope.cancel()
        dir.toFile().deleteRecursively()
    }

    private fun url(path: String) = "http://127.0.0.1:$port/api/signage$path"

    private fun hello(code: String? = "4821", protocol: Int = 1, version: String = "26.0.8") = PlayerMessage.Hello(
        playerId = "player-1",
        agentVersion = version,
        protocolMin = protocol,
        protocolMax = protocol,
        screenWidth = 1920,
        screenHeight = 1080,
        platform = Platform.LINUX_X64,
        pairingCode = code,
        features = listOf(Features.PREVIEW, Features.UPDATE),
    )

    private suspend fun DefaultClientWebSocketSession.say(message: PlayerMessage) =
        send(Frame.Text(SignageJson.encodeToString(PlayerMessage.serializer(), message)))

    /** The next message from the server; null once it closes the connection or says nothing in time. */
    private suspend fun DefaultClientWebSocketSession.hear(): ServerMessage? = withTimeoutOrNull(WAIT_MS) {
        (incoming.receiveCatching().getOrNull() as? Frame.Text)
            ?.let { SignageJson.decodeFromString(ServerMessage.serializer(), it.readText()) }
    }

    private fun paired(): String = hub.registry.pair(
        PendingScreen("player-1", "4821", Platform.LINUX_X64, "26.0.8", 1920, 1080, "127.0.0.1"),
        "Lobby",
    )

    @Test
    fun withSignageOffEveryRouteRefuses() = runBlocking<Unit> {
        server.signageEnabled = false
        assertEquals(403, client.get(url("/manifest")).status.value)
    }

    @Test
    fun anUnknownTokenIsRefusedBeforeAnything() = runBlocking<Unit> {
        assertEquals(401, client.get(url("/manifest")) { bearerAuth("nobody") }.status.value)
        assertEquals(401, client.get(url("/manifest")).status.value, "no token at all")
    }

    @Test
    fun aNewPlayerIsPairedOnceApproved() = runBlocking<Unit> {
        client.webSocket(urlString = "ws://127.0.0.1:$port/api/signage/ws") {
            say(hello())
            assertEquals(ServerMessage.PairingPending("4821"), hear())
            withTimeout(WAIT_MS) { hub.pairing.pending.first { it.isNotEmpty() } }
            assertTrue(hub.approve("player-1", "Lobby"))
            val paired = assertIs<ServerMessage.Paired>(hear())
            assertEquals("Main PC", paired.ownerName)
            assertEquals("Lobby", hub.registry.byToken(paired.token)?.name)
        }
    }

    @Test
    fun aDeclinedPlayerIsToldSo() = runBlocking<Unit> {
        client.webSocket(urlString = "ws://127.0.0.1:$port/api/signage/ws") {
            say(hello())
            hear()
            withTimeout(WAIT_MS) { hub.pairing.pending.first { it.isNotEmpty() } }
            hub.reject("player-1")
            assertEquals(ServerMessage.PairingRejected, hear())
        }
    }

    @Test
    fun aPlayerWithNoCodeOrAnotherProtocolIsTurnedAway() = runBlocking<Unit> {
        client.webSocket(urlString = "ws://127.0.0.1:$port/api/signage/ws") {
            say(hello(code = null))
            assertEquals(null, hear(), "closed without a word")
        }
        client.webSocket(urlString = "ws://127.0.0.1:$port/api/signage/ws") {
            say(hello(protocol = 99))
            assertIs<ServerMessage.Unsupported>(hear())
        }
    }

    @Test
    fun aPairedPlayerIsGreetedAndItsReportsAreKept() = runBlocking<Unit> {
        val token = paired()
        val manifest = Manifest(ownerId = hub.registry.ownerId, screen = ScreenSettings("Lobby"), timeZone = "UTC")
        hub.publish("player-1", manifest, emptyMap())
        client.webSocket(urlString = "ws://127.0.0.1:$port/api/signage/ws", request = { bearerAuth(token) }) {
            say(hello(code = null, version = "26.0.9"))
            assertIs<ServerMessage.ManifestChanged>(hear())
            say(PlayerMessage.Status(showingPlaylistId = "welcome"))
            say(PlayerMessage.Preview("/9j/", 480, 270, 1))
            say(PlayerMessage.UpdateProgress("26.0.9", UpdateState.CHECKING))
            withTimeout(WAIT_MS) {
                hub.links.links.first { it["player-1"]?.update != null }
            }
            val link = hub.links.links.value.getValue("player-1")
            assertEquals("welcome", link.status?.showingPlaylistId)
            assertEquals(480, link.preview?.width)
            assertEquals(listOf(Features.PREVIEW, Features.UPDATE), link.features)
            assertEquals("26.0.9", hub.registry.screen("player-1")?.agentVersion)

            assertTrue(hub.links.send("player-1", ServerMessage.Identify(3)))
            assertEquals(ServerMessage.Identify(3), hear())
        }
        withTimeout(WAIT_MS) { hub.links.links.first { "player-1" !in it } }
    }

    @Test
    fun aPairedPlayerFetchesItsManifestAndOnlyItsFiles() = runBlocking<Unit> {
        val token = paired()
        assertEquals(404, client.get(url("/manifest")) { bearerAuth(token) }.status.value, "nothing published yet")
        val picture = dir.resolve("a.jpg").also { Files.write(it, byteArrayOf(1, 2, 3)) }
        val sha = sha256Hex(byteArrayOf(1, 2, 3))
        val manifest = Manifest(ownerId = hub.registry.ownerId, screen = ScreenSettings("Lobby"), timeZone = "UTC")
        hub.publish("player-1", manifest, mapOf(sha to picture))

        val response = client.get(url("/manifest")) { bearerAuth(token) }
        assertEquals("\"${hub.published("player-1")?.version}\"", response.headers[HttpHeaders.ETag])
        assertEquals(manifest, SignageJson.decodeFromString(Manifest.serializer(), response.bodyAsText()))
        assertContentEquals(byteArrayOf(1, 2, 3), client.get(url("/files/$sha")) { bearerAuth(token) }.bodyAsBytes())
        assertEquals(404, client.get(url("/files/${"0".repeat(64)}")) { bearerAuth(token) }.status.value)
    }

    @Test
    fun aPlayerFetchesTheBuildItWasOfferedForItsOwnPlatformOnly() = runBlocking<Unit> {
        val token = paired()
        assertEquals(404, client.get(url("/agent/linux_x64")) { bearerAuth(token) }.status.value, "nothing offered yet")
        updates.check()
        updates.build(Platform.LINUX_X64)
        assertContentEquals(build, client.get(url("/agent/linux_x64")) { bearerAuth(token) }.bodyAsBytes())
        assertEquals(404, client.get(url("/agent/linux_arm64")) { bearerAuth(token) }.status.value)
    }

    /** A release of [build] served from memory, signed with a key made for the test. */
    private fun signedUpdates(): AgentUpdates {
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val index = """{"version":"26.0.9","protocolMin":1,"protocolMax":1,"assets":""" +
            """{"linux_x64":{"name":"b.tar.gz","size":${build.size},"sha256":"${sha256Hex(build)}"}}}"""
        val signature = Signature.getInstance("Ed25519").run {
            initSign(keys.private)
            update(index.toByteArray())
            Base64.getEncoder().encodeToString(sign())
        }
        val files = mapOf(
            "signage-release.json" to index.toByteArray(),
            "signage-release.json.sig" to signature.toByteArray(),
            "b.tar.gz" to build,
        )
        val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
        return AgentUpdates(dir.resolve("builds"), "https://example.test", publicKey) { url, consume ->
            (files[url.substringAfterLast('/')] ?: throw IOException("404")).inputStream().use(consume)
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 25L
    }
}
