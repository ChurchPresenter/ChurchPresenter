package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import engine.EngineHandle
import engine.EngineServer
import engine.engine.DetectionLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import org.json.JSONObject

/**
 * Client for the Bible Lookup Engine (BLE) microservice. Replaces in-app detection: it (optionally)
 * starts the engine in-process when STT connects, opens a WebSocket to `/bible-engine`, and forwards
 * `scripture.*` events to [onScripture]. The level chip is pushed to the engine via [setLevel].
 *
 * @param onScripture (bookId, chapter, verseStart, verseEnd, verseText, matchType, segmentId) for
 *   each event. [segmentId] is the STT segment that triggered the detection (clock-free correlation
 *   key), or null when the STT stream didn't provide one.
 */
class BibleEngineClient(
    private val onScripture: (Int, Int, Int, Int?, String, String, String?) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = HttpClient(CIO) { install(WebSockets) }

    private val _connected = mutableStateOf(false)
    val connected: State<Boolean> = _connected

    // True when an in-process engine was requested but failed to start (e.g. port collision / bind
    // failure). Surfaced so the Bible tab can show "engine unavailable" instead of silently listening
    // forever. Cleared on each (re)start attempt.
    private val _startFailed = mutableStateOf(false)
    val startFailed: State<Boolean> = _startFailed

    private var engineHandle: EngineHandle? = null
    private var wsJob: Job? = null
    @Volatile private var session: DefaultClientWebSocketSession? = null
    @Volatile private var currentLevel: String = "off"

    /**
     * Starts (or restarts) the engine link. When [runLocal] is true the engine is launched in-process
     * pointed at [sttUrl] + [bibleRoot]; otherwise we just connect to an already-running engine.
     */
    fun start(
        sttUrl: String,
        bibleRoot: String,
        bibleFiles: List<String>,
        runLocal: Boolean,
        host: String,
        port: Int,
        level: String,
    ) {
        stop()
        currentLevel = level
        _startFailed.value = false
        // Engine startup (SPB load + BM25 index) is heavy — keep it off the UI thread.
        wsJob = scope.launch {
            if (runLocal) {
                engineHandle = runCatching { EngineServer.start(sttUrl, bibleRoot, port, bibleFiles) }.getOrNull()
                if (engineHandle == null) {
                    // The engine could not start (bad config or — the headline bug — a port collision
                    // that prevents the WS server from binding). Don't enter the connect loop against
                    // a server that will never exist; surface the failure for retry instead.
                    System.err.println("bible-engine: in-process engine failed to start on port $port")
                    _startFailed.value = true
                    return@launch
                }
                val logDir = File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs").also { it.mkdirs() }
                DetectionLogger.path = File(logDir, "detection-log.jsonl").absolutePath
            }
            // A locally-started engine always lives on loopback regardless of the configured host
            // (which may point at a remote engine for the non-local case).
            val connectHost = if (runLocal) "127.0.0.1" else host
            connectLoop(connectHost, port)
        }
    }

    private suspend fun connectLoop(host: String, port: Int) {
        while (scope.isActive) {
            try {
                httpClient.webSocket(host = host, port = port, path = "/bible-engine") {
                    session = this
                    _connected.value = true
                    runCatching { send(Frame.Text(levelMessage(currentLevel))) }
                    for (frame in incoming) {
                        if (frame is Frame.Text) handleMessage(frame.readText())
                    }
                }
            } catch (_: Exception) {
                // fall through to retry
            }
            session = null
            _connected.value = false
            if (!scope.isActive) break
            delay(2000)
        }
    }

    private fun handleMessage(raw: String) {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (!obj.optString("type").startsWith("scripture.")) return
        val ref = obj.optJSONObject("reference") ?: return
        val bookId = ref.optInt("bookId", -1)
        if (bookId < 0) return
        onScripture(
            bookId,
            ref.optInt("chapter", 0),
            ref.optInt("verseStart", 0),
            if (ref.isNull("verseEnd")) null else ref.optInt("verseEnd"),
            obj.optString("verseText", ""),
            obj.optString("matchType", "reverse"),
            if (obj.isNull("segmentId")) null else obj.optString("segmentId").takeIf { it.isNotEmpty() },
        )
    }

    /** Pushes the reverse-lookup aggressiveness level to the engine. */
    fun setLevel(level: String) {
        currentLevel = level
        val s = session ?: return
        scope.launch { runCatching { s.send(Frame.Text(levelMessage(level))) } }
    }

    private fun levelMessage(level: String) = """{"type":"set_tuning","level":"$level"}"""

    /** Stops the WebSocket link and the in-process engine (if we started one). */
    fun stop() {
        wsJob?.cancel()
        wsJob = null
        session = null
        _connected.value = false
        _startFailed.value = false
        engineHandle?.stop()
        engineHandle = null
    }

    fun dispose() {
        stop()
        runCatching { httpClient.close() }
        scope.cancel()
    }
}
