package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.bibleengine.EngineHandle
import org.churchpresenter.bibleengine.EngineServer
import org.churchpresenter.bibleengine.engine.DetectionLogger
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

private const val JITTER_MIN = 0.8
private const val JITTER_MAX = 1.2

/**
 * One `scripture.*` event from the Bible Lookup Engine, decoded.
 *
 * A data class rather than a positional parameter list because the payload carries several adjacent
 * nullable strings the compiler cannot tell apart — [canonicalCodeStart], [canonicalCodeEnd],
 * [segmentId], [sessionId], [detectedVersion]. A transposition among them would compile cleanly and
 * silently corrupt the training-log join keys.
 *
 * [canonicalCodeStart]/[canonicalCodeEnd] are the engine's numbering-independent internal codes
 * (`BXXXCXXXVXXX`), forwarded so the CP side can land the reference in the primary Bible's own
 * display numbering (book order + Psalm numbering). [segmentId] is the STT segment that triggered the
 * detection (clock-free correlation key), or null when the STT stream didn't provide one. [sessionId]
 * is the stable per-service session id from STT — the exact join key that ties the STT db, the engine
 * detection-log and the CP live-references log, and keys the live-references filename. [tracks] is
 * the subset of {"transcription","translation"} that corroborated the detection.
 *
 * [detectedVersion] is which translation the engine believes is being *read aloud* (a label such as
 * "NASB", scored across every bible in the folder) — informational only, frequently not one of the
 * two bibles CP has loaded, and never the source of [verseText].
 */
data class EngineScripture(
    val bookId: Int,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int?,
    val verseText: String,
    val matchType: String,
    val canonicalCodeStart: String? = null,
    val canonicalCodeEnd: String? = null,
    val segmentId: String? = null,
    val sessionId: String? = null,
    val tracks: List<String> = emptyList(),
    val detectedVersion: String? = null,
)

/** The app's wait between reconnect attempts: long enough not to hammer a restarting engine. */
internal const val DEFAULT_RETRY_FLOOR_MS = 2_000L

/** No reconnect wait grows past this, however many attempts have failed. */
internal const val MAX_RETRY_DELAY_MS = 30_000L

/**
 * How long to wait before reconnect attempt number [attempt] (0-based, so 0 is the wait after the
 * *first* failure): [floorMs] doubled once per prior failure, capped at [MAX_RETRY_DELAY_MS], with
 * ±20% jitter so a roomful of clients that lost the same engine do not come back in lockstep.
 * Jitter never takes it below [floorMs].
 */
internal fun retryDelayMs(attempt: Int, floorMs: Long = DEFAULT_RETRY_FLOOR_MS): Long {
    val base = (floorMs shl attempt.coerceAtMost(4)).coerceAtMost(MAX_RETRY_DELAY_MS)
    return (base * Random.nextDouble(JITTER_MIN, JITTER_MAX)).toLong().coerceAtLeast(floorMs)
}

/**
 * Client for the Bible Lookup Engine (BLE) microservice. Replaces in-app detection: it (optionally)
 * starts the engine in-process when STT connects, opens a WebSocket to `/bible-engine`, and forwards
 * `scripture.*` events to [onScripture]. The level chip is pushed to the engine via [setLevel].
 *
 * @param onVersion the translation the engine believes is being read aloud, or null when it has no
 *   answer. Arrives on its own, not attached to a scripture event: the engine settles this
 *   asynchronously and usually a verse or two after the detection that first hinted at it, so it
 *   routinely lands *after* the rows it applies to are already on screen. Re-sent on connect.
 *
 * Both callbacks are required and neither is the trailing one by convention — pass them by name.
 * A defaulted trailing lambda here would silently bind `BibleEngineClient { … }` to the wrong
 * callback.
 *
 * @param retryFloorMs the shortest wait between reconnect attempts, and the base the backoff doubles
 *   from. Two seconds in the app — long enough not to hammer an engine that is restarting. Tests pass
 *   a few milliseconds so a reconnect is observable inside the per-test time budget; that is the only
 *   reason it is a parameter.
 */
class BibleEngineClient(
    private val onScripture: (EngineScripture) -> Unit,
    private val onVersion: (String?) -> Unit,
    private val retryFloorMs: Long = DEFAULT_RETRY_FLOOR_MS,
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
        // Exponential backoff (floor [retryFloorMs], cap 30s, ±20% jitter — same shape as
        // InstanceLinkClient) instead of a fixed hammer; reset on every successful connection.
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
            delay(retryDelayMs(attempt, retryFloorMs))
            attempt++
        }
    }

    private fun handleMessage(raw: String) {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (val type = obj.optString("type")) {
            "engine_status" -> {
                // The engine's real upstream STT health (broadcast on transitions and replayed to
                // late joiners). sttConfigured=false means a deliberate WS-input-only engine — treat
                // its STT link as fine so the UI doesn't flag a non-error.
                val configured = obj.optBoolean("sttConfigured", true)
                _engineSttConnected.value = if (!configured) true else obj.optBoolean("sttConnected", false)
            }
            "version_detected" -> onVersion(
                if (obj.isNull("version")) null
                else obj.optString("version").takeIf { it.isNotEmpty() }
            )
            else -> if (type.startsWith("scripture.")) handleScripture(obj)
        }
    }

    private fun handleScripture(obj: JSONObject) {
        val ref = obj.optJSONObject("reference") ?: return
        val bookId = ref.optInt("bookId", -1)
        if (bookId < 0) return
        // isNull() first everywhere: org.json's optString turns a JSON null into the STRING "null",
        // which would sail past an isNotEmpty check and become a bogus verse code.
        val codeStart = if (ref.isNull("canonicalCodeStart")) null
                        else ref.optString("canonicalCodeStart", "").takeIf { it.isNotEmpty() }
        val codeEnd = if (ref.isNull("canonicalCodeEnd")) null
                      else ref.optString("canonicalCodeEnd").takeIf { it.isNotEmpty() }
        val tracksArr = obj.optJSONArray("tracks")
        val tracks = if (tracksArr == null) emptyList()
                     else (0 until tracksArr.length()).mapNotNull { tracksArr.optString(it).takeIf { s -> s.isNotEmpty() } }
        onScripture(
            EngineScripture(
                bookId = bookId,
                chapter = ref.optInt("chapter", 0),
                verseStart = ref.optInt("verseStart", 0),
                verseEnd = if (ref.isNull("verseEnd")) null else ref.optInt("verseEnd"),
                verseText = obj.optString("verseText", ""),
                matchType = obj.optString("matchType", "reverse"),
                canonicalCodeStart = codeStart,
                canonicalCodeEnd = codeEnd,
                segmentId = if (obj.isNull("segmentId")) null
                            else obj.optString("segmentId").takeIf { it.isNotEmpty() },
                sessionId = if (obj.isNull("sessionId")) null
                            else obj.optString("sessionId").takeIf { it.isNotEmpty() },
                tracks = tracks,
                // The id and confidence the engine also sends are deliberately not parsed: they exist
                // for the engine's own training log, and nothing on this side consumes them.
                detectedVersion = if (obj.isNull("detectedVersion")) null
                                  else obj.optString("detectedVersion").takeIf { it.isNotEmpty() },
            )
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
