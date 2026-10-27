package org.churchpresenter.app.churchpresenter.demo

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import java.util.Collections

/**
 * A stand-in OBS on the loopback, speaking obs-websocket v5 as `:obs`'s own tests' stand-in does:
 * Hello, Identified, then an answer to every request. The scenes it is asked to switch to are kept
 * in [scenes], so the tour shows what OBS really received.
 */
internal class DemoObs(private val sceneList: List<String>) : AutoCloseable {
    /** OBS's own default port when it is free, so the page shows what it will at church. */
    val port: Int = runCatching { ServerSocket(DEFAULT_PORT).use { it.localPort } }.getOrDefault(0)

    /** Every scene ChurchPresenter switched OBS to, in order. */
    val scenes: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val server = embeddedServer(Netty, port = port) {
        install(WebSockets)
        routing {
            webSocket("/") {
                runCatching {
                    send(Frame.Text(HELLO))
                    for (frame in incoming) {
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        reply(Json.parseToJsonElement(text).jsonObject)?.let { send(Frame.Text(it)) }
                    }
                }
            }
        }
    }

    /** The port actually bound, which differs from [port] only when OBS's own was taken. */
    val boundPort: Int

    init {
        server.start(wait = false)
        boundPort = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    /** What OBS says back to [message]: Identified to an Identify, a response to a request. */
    private fun reply(message: JsonObject): String? = when (message["op"]?.jsonPrimitive?.content) {
        "1" -> IDENTIFIED
        "6" -> message["d"]?.jsonObject?.let(::answer)
        else -> null
    }

    private fun answer(request: JsonObject): String {
        val type = request["requestType"]?.jsonPrimitive?.content.orEmpty()
        val id = request["requestId"]?.jsonPrimitive?.content.orEmpty()
        if (type == "SetCurrentProgramScene") {
            request["requestData"]?.jsonObject?.get("sceneName")?.jsonPrimitive?.content?.let { scenes += it }
        }
        // OBS lists its scenes bottom first.
        val data = if (type == "GetSceneList") {
            ""","responseData":{"scenes":[${sceneList.reversed().joinToString(",") { """{"sceneName":"$it"}""" }}]}"""
        } else {
            ""
        }
        return """{"op":7,"d":{"requestType":"$type","requestId":"$id",""" +
            """"requestStatus":{"result":true,"code":100}$data}}"""
    }

    override fun close() = server.stop(0, 0)
}

private const val DEFAULT_PORT = 4455
private const val HELLO = """{"op":0,"d":{"obsWebSocketVersion":"5.1.0","rpcVersion":1}}"""
private const val IDENTIFIED = """{"op":2,"d":{"negotiatedRpcVersion":1}}"""
