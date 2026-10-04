package org.churchpresenter.obs

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.runBlocking

/**
 * A stand-in OBS that plays whatever [script] says to each client, for the handshakes a real OBS
 * never sends: a binary greeting, text that is not JSON, a Hello with no data, a hang-up.
 */
internal class ScriptedObs(
    private val script: suspend DefaultWebSocketServerSession.() -> Unit,
) : AutoCloseable {

    private val server = embeddedServer(Netty, port = 0) {
        install(WebSockets)
        routing {
            webSocket("/") {
                try {
                    script()
                } catch (_: Exception) {
                    // the client hung up first, which is what several scripts provoke
                }
            }
        }
    }

    /** The port the OS assigned; bound before the constructor returns. */
    val port: Int

    init {
        server.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    override fun close() = server.stop(0, 0)

    companion object {
        const val HELLO = """{"op":0,"d":{"obsWebSocketVersion":"5.1.0","rpcVersion":1}}"""
        const val IDENTIFIED = """{"op":2,"d":{"negotiatedRpcVersion":1}}"""
    }
}
