package org.churchpresenter.bibleengine.socket

import org.churchpresenter.bibleengine.Config
import org.churchpresenter.bibleengine.engine.DetectionEngine
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Route.bibleEngineSocket(
    engine: DetectionEngine,
    broadcaster: Broadcaster,
    // Single-threaded detection dispatcher (EngineServer) — the engine's shared state
    // (utterances, Stabilizer, Config tuning) is mutated ONLY on this context, which is what
    // makes it safe with multiple concurrent WS clients + the STT thread. Defaults to the
    // caller's context for tests that drive a single connection.
    detectionContext: CoroutineContext = EmptyCoroutineContext,
) {
    webSocket("/bible-engine") {
        val remoteAddr = call.request.local.remoteHost
        if (Config.verboseLog) println("WebSocket connected: $remoteAddr")
        broadcaster.register(this)
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) handleText(frame.readText(), remoteAddr, engine, broadcaster, detectionContext)
            }
        } catch (e: IOException) {
            if (Config.verboseLog) println("WebSocket error ($remoteAddr): ${e.message}")
        } catch (e: ClosedSendChannelException) {
            // The client went away between a frame arriving and its answer going out.
            if (Config.verboseLog) println("WebSocket closed ($remoteAddr): ${e.message}")
        } finally {
            broadcaster.unregister(this)
            if (Config.verboseLog) println("WebSocket disconnected: $remoteAddr")
        }
    }
}

/** One client message: a ping, a tuning change, or a transcription/translation update to detect on. */
private suspend fun WebSocketSession.handleText(
    raw: String,
    remoteAddr: String,
    engine: DetectionEngine,
    broadcaster: Broadcaster,
    detectionContext: CoroutineContext,
) {
    val obj = parseObject(raw, remoteAddr) ?: return
    val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return
    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
    val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: ""

    when (type) {
        "ping" -> send(Frame.Text("""{"type":"pong"}"""))
        "set_tuning" -> applyTuning(obj, detectionContext)
        "transcription_update" -> if (id.isNotBlank()) {
            // CP→engine WS path carries no STT session id — only the STT socket path does.
            withContext(detectionContext) {
                engine.processTranscription(id, text, sessionId = null)
                    .forEach { broadcaster.broadcast(it) }
            }
        }
        "translation_update" -> if (id.isNotBlank()) {
            withContext(detectionContext) {
                engine.processTranslation(id, text, sessionId = null).forEach { broadcaster.broadcast(it) }
            }
        }
    }
}

/** [raw] as a JSON object, or null -- reported -- when it is not one. */
private fun parseObject(raw: String, remoteAddr: String): JsonObject? = try {
    json.parseToJsonElement(raw).jsonObject
} catch (e: IllegalArgumentException) {
    // Malformed JSON (SerializationException) and a non-object payload both land here.
    // The message, not just the payload: "Invalid JSON" over a 4 KB line says the
    // parse failed and nothing about where, which is the half worth having.
    System.err.println("Invalid JSON from $remoteAddr (${e.message}): $raw")
    null
}

private suspend fun applyTuning(obj: JsonObject, detectionContext: CoroutineContext) {
    val level = obj["level"]?.jsonPrimitive?.contentOrNull
    val continuationSpeed = obj["continuationSpeed"]?.jsonPrimitive?.contentOrNull
    withContext(detectionContext) {
        if (level != null) Config.applyLevel(level)
        if (continuationSpeed != null) Config.applyContinuationSpeed(continuationSpeed)
    }
}
