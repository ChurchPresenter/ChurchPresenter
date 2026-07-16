package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import engine.EngineHandle
import engine.EngineServer
import engine.engine.DetectionLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.io.File
import java.time.Instant
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.json.JSONObject

/**
 * Client for the Bible Lookup Engine (BLE) microservice. Replaces in-app detection: it (optionally)
 * starts the engine in-process when STT connects, opens a WebSocket to `/bible-engine`, and forwards
 * `scripture.*` events to [onScripture]. The level chip is pushed to the engine via [setLevel].
 *
 * @param onScripture (bookId, chapter, verseStart, verseEnd, verseText, matchType,
 *   canonicalCodeStart, canonicalCodeEnd, segmentId, sessionId, tracks) for each event.
 *   [canonicalCodeStart]/[canonicalCodeEnd] are the engine's numbering-independent internal codes
 *   (`BXXXCXXXVXXX`), forwarded so the CP side can land the reference in the primary Bible's own
 *   display numbering (book order + Psalm numbering). [segmentId] is the STT segment that triggered the
 *   detection (clock-free correlation key), or null when the STT stream didn't provide one. [sessionId]
 *   is the stable per-service session id from STT — the exact join key that ties the STT db, the engine
 *   detection-log and the CP live-references log, and keys the live-references filename. [tracks] is
 *   the subset of {"transcription","translation"} that corroborated the detection.
 */
class BibleEngineClient(
    private val onScripture: (Int, Int, Int, Int?, String, String, String?, String?, String?, String?, List<String>) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = HttpClient(CIO) {
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
        }
    }

    private val _connected = mutableStateOf(false)
    val connected: State<Boolean> = _connected

    // True when an in-process engine was requested but failed to start (e.g. port collision / bind
    // failure). Surfaced so the Bible tab can show "engine unavailable" instead of silently listening
    // forever. Cleared on each (re)start attempt.
    private val _startFailed = mutableStateOf(false)
    val startFailed: State<Boolean> = _startFailed

    // The engine's OWN upstream STT link, from its engine_status broadcasts. Null = unknown
    // (older engine that never sends the message, or not yet received) — callers keep their
    // previous proxy inference in that case. Reset on disconnect/stop.
    private val _engineSttConnected = mutableStateOf<Boolean?>(null)
    val engineSttConnected: State<Boolean?> = _engineSttConnected

    private var engineHandle: EngineHandle? = null
    private var wsJob: Job? = null
    @Volatile private var session: DefaultClientWebSocketSession? = null
    @Volatile private var currentLevel: String = "off"
    @Volatile private var currentContinuationSpeed: String = "balanced"

    private val engineErrorLock = Any()

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
        continuationSpeed: String = "balanced",
    ) {
        stop()
        currentLevel = level
        currentContinuationSpeed = continuationSpeed
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
                    CrashReporter.reportWarning(
                        "Bible engine: in-process engine failed to start on port $port",
                        tags = mapOf("subsystem" to "bible-engine")
                    )
                    _startFailed.value = true
                    return@launch
                }
                val logDir = File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs").also { it.mkdirs() }
                DetectionLogger.path = File(logDir, "detection-log.jsonl").absolutePath
            }
            // A locally-started engine always lives on loopback, on the port it ACTUALLY bound (which
            // may differ from the configured one when that was taken — e.g. the Companion-server
            // collision). The configured host/port only apply to a remote engine.
            val connectHost = if (runLocal) "127.0.0.1" else host
            val connectPort = if (runLocal) engineHandle?.boundPort ?: port else port
            connectLoop(connectHost, connectPort)
        }
    }

    private suspend fun connectLoop(host: String, port: Int) {
        // Exponential backoff (floor 2s, cap 30s, ±20% jitter — same shape as InstanceLinkClient)
        // instead of a fixed 2s hammer; reset on every successful connection.
        var attempt = 0
        while (scope.isActive) {
            try {
                httpClient.webSocket(host = host, port = port, path = "/bible-engine") {
                    session = this
                    _connected.value = true
                    attempt = 0
                    runCatching { send(Frame.Text(tuningMessage(currentLevel, currentContinuationSpeed))) }
                    for (frame in incoming) {
                        if (frame is Frame.Text) handleMessage(frame.readText())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                System.err.println("bible-engine: connect to ws://$host:$port/bible-engine failed — ${e.message}")
                logEngineError("connectLoop: connect to ws://$host:$port/bible-engine failed", e.toString())
            }
            session = null
            _connected.value = false
            _engineSttConnected.value = null
            if (!scope.isActive) break
            val base = (2_000L shl attempt.coerceAtMost(4)).coerceAtMost(30_000L)
            attempt++
            delay((base * Random.nextDouble(0.8, 1.2)).toLong().coerceAtLeast(2_000L))
        }
    }

    private fun handleMessage(raw: String) {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val type = obj.optString("type")
        if (type == "engine_status") {
            // The engine's real upstream STT health (broadcast on transitions and replayed to
            // late joiners). sttConfigured=false means a deliberate WS-input-only engine — treat
            // its STT link as fine so the UI doesn't flag a non-error.
            val configured = obj.optBoolean("sttConfigured", true)
            _engineSttConnected.value = if (!configured) true else obj.optBoolean("sttConnected", false)
            return
        }
        if (!type.startsWith("scripture.")) return
        val ref = obj.optJSONObject("reference") ?: return
        val bookId = ref.optInt("bookId", -1)
        if (bookId < 0) return
        val codeStart = ref.optString("canonicalCodeStart", "").takeIf { it.isNotEmpty() }
        val codeEnd = if (ref.isNull("canonicalCodeEnd")) null
                      else ref.optString("canonicalCodeEnd").takeIf { it.isNotEmpty() }
        val tracksArr = obj.optJSONArray("tracks")
        val tracks = if (tracksArr == null) emptyList()
                     else (0 until tracksArr.length()).mapNotNull { tracksArr.optString(it).takeIf { s -> s.isNotEmpty() } }
        onScripture(
            bookId,
            ref.optInt("chapter", 0),
            ref.optInt("verseStart", 0),
            if (ref.isNull("verseEnd")) null else ref.optInt("verseEnd"),
            obj.optString("verseText", ""),
            obj.optString("matchType", "reverse"),
            codeStart,
            codeEnd,
            if (obj.isNull("segmentId")) null else obj.optString("segmentId").takeIf { it.isNotEmpty() },
            if (obj.isNull("sessionId")) null else obj.optString("sessionId").takeIf { it.isNotEmpty() },
            tracks,
        )
    }

    /** Pushes the reverse-lookup aggressiveness level to the engine. */
    fun setLevel(level: String) {
        currentLevel = level
        val s = session ?: return
        scope.launch { runCatching { s.send(Frame.Text(tuningMessage(level, currentContinuationSpeed))) } }
    }

    /** Pushes the "Verse speed" preset (sequential continuation floor) to the engine. */
    fun setContinuationSpeed(speed: String) {
        currentContinuationSpeed = speed
        val s = session ?: return
        scope.launch { runCatching { s.send(Frame.Text(tuningMessage(currentLevel, speed))) } }
    }

    private fun tuningMessage(level: String, continuationSpeed: String) =
        """{"type":"set_tuning","level":"$level","continuationSpeed":"$continuationSpeed"}"""

    /** Stops the WebSocket link and the in-process engine (if we started one). */
    fun stop() {
        wsJob?.cancel()
        wsJob = null
        session = null
        _connected.value = false
        _startFailed.value = false
        _engineSttConnected.value = null
        engineHandle?.stop()
        engineHandle = null
    }

    /** Appends a line to ~/.churchpresenter/bible-stt-logs/engine-errors.jsonl for crash-level issues. */
    private fun logEngineError(message: String, detail: String = "") {
        runCatching {
            val dir = File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs").also { it.mkdirs() }
            val file = File(dir, "engine-errors.jsonl")
            val line = buildString {
                append("{\"ts\":\"").append(Instant.now()).append("\",")
                append("\"message\":\"").append(esc(message)).append("\"")
                if (detail.isNotBlank()) append(",\"detail\":\"").append(esc(detail)).append("\"")
                append("}")
            }
            synchronized(engineErrorLock) { file.appendText(line + "\n", Charsets.UTF_8) }
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")

    fun dispose() {
        stop()
        runCatching { httpClient.close() }
        scope.cancel()
    }
}
