package org.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.signage.core.Manifest
import org.churchpresenter.signage.core.PROTOCOL_VERSION
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.ServerMessage
import org.churchpresenter.signage.core.SignageJson
import org.churchpresenter.signage.core.isSha256Hex
import org.churchpresenter.signage.core.supportsProtocol
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.PairingDecision
import org.churchpresenter.signage.hub.SignageHub
import java.nio.file.Files

private const val SIGNAGE_ROOT = "/api/signage"
private const val TAG = "Signage"

/**
 * The signage players' side of the server (`docs` in the signage repo's `PROTOCOL.md`): their control
 * channel, their manifest and its files. Dev mode only, and each player authenticates with its own
 * token rather than the API key. An unpaired player may open the control channel without one, to ask
 * to be paired.
 */
internal fun Route.signageRoutes(server: CompanionServer) {
    route(SIGNAGE_ROOT) {
        install(SignageGate) { this.server = server }
        get("/manifest") {
            val screen = call.signageScreen(server) ?: return@get
            val published = server.signage?.published(screen.id)
            if (published == null) {
                call.respond(HttpStatusCode.NotFound, """{"ok":false,"reason":"nothing published yet"}""")
                return@get
            }
            call.response.header(HttpHeaders.ETag, "\"${published.version}\"")
            call.respondText(
                SignageJson.encodeToString(Manifest.serializer(), published.manifest),
                ContentType.Application.Json,
            )
        }
        get("/files/{sha256}") {
            val screen = call.signageScreen(server) ?: return@get
            val sha = call.parameters["sha256"].orEmpty()
            val path = server.signage?.published(screen.id)?.files?.get(sha)?.takeIf { isSha256Hex(sha) }
            if (path == null || !Files.isRegularFile(path)) {
                call.respond(HttpStatusCode.NotFound, """{"ok":false,"reason":"not in this screen's manifest"}""")
                return@get
            }
            call.respondFile(path.toFile())
        }
        get("/agent/{platform}") {
            val screen = call.signageScreen(server) ?: return@get
            val build = server.signage?.updates?.cached(screen.platform)
            // A screen fetches only its own platform's build, and only once ChurchPresenter has checked it.
            if (build == null || call.parameters["platform"] != SignageHub.platformName(screen.platform)) {
                call.respond(HttpStatusCode.NotFound, """{"ok":false,"reason":"no update offered for this screen"}""")
                return@get
            }
            call.respondFile(build.toFile())
        }
        webSocket("/ws") {
            val hub = server.signage ?: return@webSocket
            val hello = receiveHello() ?: return@webSocket
            if (!supportsProtocol(hello.protocolMin, hello.protocolMax)) {
                sendSignage(ServerMessage.Unsupported(PROTOCOL_VERSION))
                close(CloseReason(CloseReason.Codes.NORMAL, "unsupported protocol"))
                return@webSocket
            }
            val screen = call.signageScreen(server, respond = false)
            val address = call.request.origin.remoteAddress
            if (screen != null && screen.id == hello.playerId) {
                runPairedSession(hub, hello, address)
            } else {
                runPairing(hub, hello, address)
            }
        }
    }
}

/**
 * Before any signage route runs: 403 outside dev mode or with no signage hub, and 401 for a token
 * that names no paired screen — before the WebSocket upgrade, so a revoked player sees the 401.
 */
private val SignageGate = createRouteScopedPlugin("SignageGate", ::SignageGateConfig) {
    val server = pluginConfig.server
    onCall { call ->
        when {
            !server.devMode || server.signage == null ->
                call.respond(HttpStatusCode.Forbidden, """{"ok":false,"reason":"dev mode only"}""")
            call.bearerToken() != null && call.signageScreen(server, respond = false) == null ->
                call.respond(HttpStatusCode.Unauthorized, """{"ok":false,"reason":"unknown screen"}""")
        }
    }
}

private class SignageGateConfig {
    lateinit var server: CompanionServer
}

private fun ApplicationCall.bearerToken(): String? =
    request.headers[HttpHeaders.Authorization]?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()

/** The paired screen this call's token names; answers 401 and returns null when there is none. */
private suspend fun ApplicationCall.signageScreen(server: CompanionServer, respond: Boolean = true): PairedScreen? {
    val screen = bearerToken()?.let { token -> server.signage?.registry?.byToken(token) }
    if (screen == null && respond) {
        respond(HttpStatusCode.Unauthorized, """{"ok":false,"reason":"unknown screen"}""")
    }
    return screen
}

private suspend fun DefaultWebSocketServerSession.receiveHello(): PlayerMessage.Hello? {
    val frame = incoming.receiveCatching().getOrNull() as? Frame.Text ?: return null
    return decodePlayerMessage(frame) as? PlayerMessage.Hello
}

private fun decodePlayerMessage(frame: Frame.Text): PlayerMessage? = try {
    SignageJson.decodeFromString(PlayerMessage.serializer(), frame.readText())
} catch (e: SerializationException) {
    Log.warn(TAG, "Ignoring a player message this build cannot read: ${e.message}")
    null
} catch (e: IllegalArgumentException) {
    Log.warn(TAG, "Ignoring a player message this build cannot read: ${e.message}")
    null
}

private suspend fun DefaultWebSocketServerSession.sendSignage(message: ServerMessage) {
    send(Frame.Text(SignageJson.encodeToString(ServerMessage.serializer(), message)))
}

/** An unpaired player: listed in the Signage tab until it is approved or rejected, or goes away. */
private suspend fun DefaultWebSocketServerSession.runPairing(
    hub: SignageHub,
    hello: PlayerMessage.Hello,
    address: String,
) {
    if (hello.pairingCode.isNullOrBlank()) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "no pairing code"))
        return
    }
    sendSignage(ServerMessage.PairingPending(hello.pairingCode.orEmpty()))
    // Reading on keeps the connection's close noticed: the player sends nothing more until paired.
    val watcher = launch { incoming.consumeEach { } }
    val decision = try {
        hub.pairing.await(hello, address)
    } finally {
        watcher.cancel()
    }
    when (decision) {
        is PairingDecision.Approved -> {
            Log.info(TAG, "Paired screen ${hello.playerId} at $address")
            sendSignage(ServerMessage.Paired(decision.token, hub.registry.ownerId, hub.ownerName))
        }
        PairingDecision.Rejected -> sendSignage(ServerMessage.PairingRejected)
    }
    close(CloseReason(CloseReason.Codes.NORMAL, "pairing settled"))
}

/** A paired player: what it is sent, and what it reports, until either side closes. */
private suspend fun DefaultWebSocketServerSession.runPairedSession(
    hub: SignageHub,
    hello: PlayerMessage.Hello,
    address: String,
) {
    hub.registry.refreshFrom(hello)
    val outgoing = launch(start = CoroutineStart.UNDISPATCHED) {
        hub.links.messagesFor(hello.playerId).collect { sendSignage(it) }
    }
    val session = hub.links.opened(hello.playerId, address, hello.manifestVersion, hello.features)
    try {
        hub.greeting(hello).forEach { sendSignage(it) }
        for (frame in incoming) {
            val message = (frame as? Frame.Text)?.let(::decodePlayerMessage) ?: continue
            if (message is PlayerMessage.UpdateProgress) {
                val why = message.message.orEmpty()
                Log.info(TAG, "Screen ${hello.playerId}: update to ${message.version} ${message.state} $why")
            }
            hub.links.reported(hello.playerId, message)
        }
    } finally {
        outgoing.cancel()
        hub.links.closed(hello.playerId, session)
    }
}
