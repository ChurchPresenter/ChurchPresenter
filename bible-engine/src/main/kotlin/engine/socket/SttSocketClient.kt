package engine.socket

import engine.Config
import engine.engine.DetectionEngine
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class SttSocketClient(
    private val serverUrl: String,
    private val detectionEngine: DetectionEngine,
    private val broadcaster: Broadcaster,
    /** Single-threaded detection scope (EngineServer) — all engine mutation is confined to it,
     *  and the Socket.IO event thread only parses payloads and enqueues work. Launches from the
     *  one event thread onto the one-thread dispatcher keep FIFO order. */
    private val detectionScope: CoroutineScope,
    /** STT link state transitions (connected/disconnected/error) — feeds the engine_status
     *  broadcast so consuming apps can show the engine's REAL upstream health. */
    private val onSttStatus: (connected: Boolean) -> Unit = {},
) {
    private lateinit var socket: Socket

    fun connect() {
        val options = IO.Options.builder()
            .setTransports(arrayOf("websocket"))
            .build()

        socket = IO.socket(serverUrl, options)

        socket.on(Socket.EVENT_CONNECT) {
            if (Config.verboseLog) println("Connected to STT server: $serverUrl")
            onSttStatus(true)
        }
        socket.on(Socket.EVENT_DISCONNECT) {
            if (Config.verboseLog) println("Disconnected from STT server")
            onSttStatus(false)
        }
        // Reconnection itself is socket.io-client's default behavior (infinite attempts with
        // backoff); this handler only surfaces the state.
        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            System.err.println("STT server connection error: ${args.firstOrNull()}")
            onSttStatus(false)
        }

        // Both streams feed ONE utterance ("live") so the detector sees the transcript AND its
        // translation together (cross-language corroboration + shared sticky context). Using two ids
        // here previously split them into independent states that never combined.
        // Payload parsing/windowing lives in SttPayload.kt, shared with the replay harness.
        socket.on("transcription_update") { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@on
            transcriptionUpdate(payload)?.let { u ->
                detectionScope.launch {
                    detectionEngine.processTranscription(
                        "live", u.text, u.speechType, u.segmentId, u.startTime, u.sessionId
                    ).forEach { broadcaster.broadcast(it) }
                }
            }
        }

        socket.on("translation_update") { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@on
            translationUpdate(payload)?.let { u ->
                detectionScope.launch {
                    detectionEngine.processTranslation(
                        "live", u.text, u.speechType, u.segmentId, u.startTime, u.sessionId
                    ).forEach { broadcaster.broadcast(it) }
                }
            }
        }

        socket.connect()
    }

    fun disconnect() {
        if (::socket.isInitialized) socket.disconnect()
    }
}
