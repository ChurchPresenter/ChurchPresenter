package engine.socket

import engine.Config
import engine.engine.DetectionEngine
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
                if (frame !is Frame.Text) continue
                val raw = frame.readText()

                val obj = try {
                    json.parseToJsonElement(raw).jsonObject
                } catch (e: Exception) {
                    System.err.println("Invalid JSON from $remoteAddr: $raw")
                    continue
                }

                val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: continue
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: ""

                when (type) {
                    "ping" -> send(Frame.Text("""{"type":"pong"}"""))
                    "set_tuning" -> {
                        val level = obj["level"]?.jsonPrimitive?.contentOrNull
                        val continuationSpeed = obj["continuationSpeed"]?.jsonPrimitive?.contentOrNull
                        withContext(detectionContext) {
                            if (level != null) Config.applyLevel(level)
                            if (continuationSpeed != null) Config.applyContinuationSpeed(continuationSpeed)
                        }
                    }
                    "transcription_update" -> {
                        if (id.isBlank()) continue
                        // CP→engine WS path carries no STT session id — only the STT socket path does.
                        withContext(detectionContext) {
                            engine.processTranscription(id, text, sessionId = null).forEach { broadcaster.broadcast(it) }
                        }
                    }
                    "translation_update" -> {
                        if (id.isBlank()) continue
                        withContext(detectionContext) {
                            engine.processTranslation(id, text, sessionId = null).forEach { broadcaster.broadcast(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (Config.verboseLog) println("WebSocket error ($remoteAddr): ${e.message}")
        } finally {
            broadcaster.unregister(this)
            if (Config.verboseLog) println("WebSocket disconnected: $remoteAddr")
        }
    }
}
