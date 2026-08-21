package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.utils.TrainingDataLogger
import org.churchpresenter.app.churchpresenter.utils.stringOr
import org.churchpresenter.app.churchpresenter.utils.stringOrNull
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val HTTP_OK = 200
private const val MODEL_POLL_INTERVAL_MS = 60_000L

data class STTSegment(
    val id: Int,
    val timestamp: String,
    val text: String,
    val start: Double,
    val end: Double,
    val completed: Boolean
)

data class HighlightedWord(
    val word: String,
    val color: String,
    val caseSensitive: Boolean = false,
    val isRegex: Boolean = false
)

class STTManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Connection state ──────────────────────────────────────────────
    private var socket: Socket? = null
    private val _connected = mutableStateOf(false)
    val connected: State<Boolean> = _connected

    private val _connecting = mutableStateOf(false)
    val connecting: State<Boolean> = _connecting

    // True after a connection attempt fails to reach the STT server (EVENT_CONNECT_ERROR). Stays true
    // while Socket.IO keeps retrying (reconnection is on), so the UI can show "can't reach server".
    // Cleared on a successful connect, on a fresh connect() call, and on disconnect().
    private val _connectError = mutableStateOf(false)
    val connectError: State<Boolean> = _connectError

    // True after an UNEXPECTED disconnect (network drop / server restart) while reconnection keeps
    // retrying — distinct from a user-initiated disconnect(). Lets the UI show "reconnecting…" only
    // when it's actually trying to come back. Cleared on (re)connect and on manual disconnect().
    private val _reconnecting = mutableStateOf(false)
    val reconnecting: State<Boolean> = _reconnecting

    // ── Transcription state ───────────────────────────────────────────
    private val _segments = mutableStateListOf<STTSegment>()
    val segments: List<STTSegment> get() = _segments

    private val _inProgressText = mutableStateOf("")
    val inProgressText: State<String> = _inProgressText

    // ── Translation state ─────────────────────────────────────────────
    private val _translationSegments = mutableStateListOf<STTSegment>()
    val translationSegments: List<STTSegment> get() = _translationSegments

    private val _inProgressTranslation = mutableStateOf("")
    val inProgressTranslation: State<String> = _inProgressTranslation

    private val _translationLanguage = mutableStateOf("")
    val translationLanguage: State<String> = _translationLanguage

    // ── Word highlighting ─────────────────────────────────────────────
    private val _highlightedWords = mutableStateListOf<HighlightedWord>()
    val highlightedWords: List<HighlightedWord> get() = _highlightedWords

    private val _wordHighlightingEnabled = mutableStateOf(true)
    val wordHighlightingEnabled: State<Boolean> = _wordHighlightingEnabled

    // ── Display state (is STT currently being projected) ──────────────
    private val _isLive = mutableStateOf(false)
    val isLive: State<Boolean> = _isLive

    fun setLive(live: Boolean) {
        _isLive.value = live
    }

    // ── Socket state transitions ──────────────────────────────────────
    // The four connection flags only ever change together, and every combination means something
    // different to the UI (green dot vs "connecting…" vs "can't reach server" vs "reconnecting…").
    // They live here as named functions rather than inline in connect()'s socket callbacks so the
    // transitions are one testable step each: reaching them through a real socket needs a live STT
    // server, but which flags a given event sets is ordinary logic.

    /** An attempt has started: connecting, with any previous failure or retry cleared. */
    internal fun applyConnecting() {
        _connecting.value = true
        _connectError.value = false
        _reconnecting.value = false
    }

    /** A socket connection came up: live, and no longer connecting/failed/retrying. */
    internal fun applyConnected() {
        _connected.value = true
        _connecting.value = false
        _connectError.value = false
        _reconnecting.value = false
    }

    /**
     * The socket went down. [reason] is socket.io's disconnect reason: `"io client disconnect"` means
     * we called [disconnect] ourselves, so the link is simply closed. Anything else (transport close,
     * ping timeout, server disconnect) is an unexpected drop that reconnection keeps retrying, which
     * the UI shows as "reconnecting…".
     */
    internal fun applyDisconnected(reason: String?) {
        _connected.value = false
        if (reason != CLIENT_DISCONNECT_REASON) _reconnecting.value = true
    }

    /** The server could not be reached at all — not connected, not connecting, and flagged failed. */
    internal fun applyConnectError() {
        _connected.value = false
        _connecting.value = false
        _connectError.value = true
    }

    // ── Help Dev: periodic STT .db capture ─────────────────────────────
    // Read live from a background loop, not observed by Compose — kept in sync with the
    // "Help Dev" checkbox (BibleEngineSettings.helpDevMode) by a LaunchedEffect in main.kt, since
    // connect() (and therefore startDbCapture()) only runs once per explicit Connect click.
    var helpDevModeEnabled: Boolean = false
    private var dbCaptureJob: Job? = null
    // Base URL of the STT server the capture loop is pulling from, kept so [disconnect] can take one
    // final snapshot after the loop stops.
    private var captureBaseUrl: String? = null

    /**
     * Whether disconnecting should pull one last .db snapshot. The periodic loop only runs while
     * connected, so without this the archived .db stops at the last 60-second tick — and in practice
     * further back: `2026-07-19_102718.db` ends 5 minutes before the service did, mid-sentence, which
     * silently turned 7 references the engine had actually detected live into replay "misses". A
     * snapshot is only worth pulling when Help Dev is on (nothing else reads it) and a server is
     * known.
     */
    internal fun shouldCaptureFinalSnapshot(helpDev: Boolean, baseUrl: String?): Boolean =
        helpDev && !baseUrl.isNullOrBlank()

    fun connect(url: String) {
        if (_connected.value || _connecting.value) return
        // Clean up any leftover socket (e.g. from a previous failed connection)
        socket?.off()
        socket?.disconnect()
        socket?.close()
        socket = null

        applyConnecting()

        scope.launch(Dispatchers.IO) {
            try {
                val opts = IO.Options.builder()
                    .setTransports(arrayOf("websocket"))
                    .setReconnection(true)
                    .setReconnectionAttempts(Int.MAX_VALUE)
                    .setReconnectionDelay(2000)
                    .build()

                val s = IO.socket(URI.create(url), opts)

                s.on(Socket.EVENT_CONNECT) {
                    scope.launch { applyConnected() }
                    // Request initial data
                    s.emit("request_all_entries")
                    s.emit("request_all_translation_entries")
                    // Fetch word highlighting via REST (not sent on connect via socket)
                    scope.launch(Dispatchers.IO) {
                        fetchWordHighlighting(url)
                    }
                    startDbCapture(url)
                }

                s.on(Socket.EVENT_DISCONNECT) { args ->
                    val reason = args.firstOrNull()?.toString()
                    scope.launch { applyDisconnected(reason) }
                }

                s.on(Socket.EVENT_CONNECT_ERROR) {
                    scope.launch { applyConnectError() }
                }

                s.on("transcription_update") { args ->
                    if (args.isNotEmpty() && args[0] is JSONObject) {
                        val data = args[0] as JSONObject
                        scope.launch {
                            handleTranscriptionUpdate(data)
                        }
                    }
                }

                s.on("translation_update") { args ->
                    if (args.isNotEmpty() && args[0] is JSONObject) {
                        val data = args[0] as JSONObject
                        scope.launch {
                            handleTranslationUpdate(data)
                        }
                    }
                }

                s.on("word_highlighting_update") { args ->
                    if (args.isNotEmpty() && args[0] is JSONObject) {
                        val data = args[0] as JSONObject
                        scope.launch {
                            handleWordHighlightingUpdate(data)
                        }
                    }
                }

                socket = s
                s.connect()
            } catch (_: Exception) {
                scope.launch { applyConnectError() }
            }
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.close()
        socket = null
        _connected.value = false
        _connecting.value = false
        _connectError.value = false
        _reconnecting.value = false
        dbCaptureJob?.cancel()
        dbCaptureJob = null
        val baseUrl = captureBaseUrl
        if (shouldCaptureFinalSnapshot(helpDevModeEnabled, baseUrl)) {
            // Deliberately NOT on dbCaptureJob — that one was just cancelled. Best-effort: a server
            // that has already gone away simply leaves the last periodic snapshot in place.
            scope.launch(Dispatchers.IO) { runCatching { captureDbSnapshot(baseUrl!!) } }
        }
    }

    internal fun handleTranscriptionUpdate(data: JSONObject) {
        val segmentsArray = data.optJSONArray("segments")
        if (segmentsArray != null) {
            _segments.clear()
            for (i in 0 until segmentsArray.length()) {
                val seg = segmentsArray.getJSONObject(i)
                _segments.add(
                    STTSegment(
                        id = seg.optInt("id", i),
                        timestamp = seg.stringOr("timestamp"),
                        text = seg.stringOr("text"),
                        start = seg.optDouble("start", 0.0),
                        end = seg.optDouble("end", 0.0),
                        completed = seg.optBoolean("completed", true)
                    )
                )
            }
        }

        val inProgress = data.opt("in_progress")
        _inProgressText.value = when (inProgress) {
            is String -> inProgress
            null, JSONObject.NULL -> ""
            else -> inProgress.toString()
        }
    }

    internal fun handleTranslationUpdate(data: JSONObject) {
        val segmentsArray = data.optJSONArray("segments")
        if (segmentsArray != null) {
            _translationSegments.clear()
            for (i in 0 until segmentsArray.length()) {
                val seg = segmentsArray.getJSONObject(i)
                // STT app sends "translated_text" for translation segments
                val text = seg.stringOr("translated_text").ifBlank {
                    seg.stringOr("text")
                }
                _translationSegments.add(
                    STTSegment(
                        id = seg.optInt("id", i),
                        timestamp = seg.stringOr("timestamp"),
                        text = text,
                        start = seg.optDouble("start", 0.0),
                        end = seg.optDouble("end", 0.0),
                        completed = seg.optBoolean("completed", true)
                    )
                )
            }
        }

        // in_progress can be a string or a JSON object with "translated_text"
        val inProgress = data.opt("in_progress")
        _inProgressTranslation.value = when (inProgress) {
            is JSONObject -> inProgress.stringOr("translated_text")
            is String -> inProgress
            null, JSONObject.NULL -> ""
            else -> inProgress.toString()
        }

        _translationLanguage.value = data.stringOr("target_language_name")
    }

    internal fun handleWordHighlightingUpdate(data: JSONObject) {
        _wordHighlightingEnabled.value = data.optBoolean("enabled", true)

        val disabledColors = mutableSetOf<String>()
        val disabledArray = data.optJSONArray("disabled_colors")
        if (disabledArray != null) {
            for (i in 0 until disabledArray.length()) {
                disabledColors.add(disabledArray.stringOr(i))
            }
        }

        val wordsArray = data.optJSONArray("words")
        _highlightedWords.clear()
        if (wordsArray != null) {
            for (i in 0 until wordsArray.length()) {
                val w = wordsArray.getJSONObject(i)
                val color = w.stringOr("color", "#ffff00")
                if (color in disabledColors) continue
                _highlightedWords.add(
                    HighlightedWord(
                        word = w.stringOr("word"),
                        color = color,
                        caseSensitive = w.optBoolean("case_sensitive", false),
                        isRegex = w.optBoolean("is_regex", false)
                    )
                )
            }
        }
    }

    /** The enabled highlighted words in the payload; words in a disabled colour group are dropped. */
    private fun highlightedWordsFrom(json: JSONObject): List<HighlightedWord> {
        val disabledArray = json.optJSONArray("disabled_colors")
        val disabledColors = (0 until (disabledArray?.length() ?: 0))
            .map { disabledArray!!.stringOr(it) }
            .toSet()
        val wordsArray = json.optJSONArray("words") ?: return emptyList()
        return (0 until wordsArray.length())
            .map { wordsArray.getJSONObject(it) }
            .filter { it.stringOr("color", "#ffff00") !in disabledColors }
            .map { w ->
                HighlightedWord(
                    word = w.stringOr("word"),
                    color = w.stringOr("color", "#ffff00"),
                    caseSensitive = w.optBoolean("case_sensitive", false),
                    isRegex = w.optBoolean("is_regex", false)
                )
            }
    }

    /**
     * The highlighted-word list is not pushed on connect, so it is pulled over REST once the socket
     * comes up. `internal` rather than private for the same reason as the `handle*Update` parsers:
     * in production it is only reachable from a socket callback, and it is plain HTTP otherwise.
     */
    internal fun fetchWordHighlighting(baseUrl: String) {
        try {
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/word-highlighting/words"))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != HTTP_OK) return
            val json = JSONObject(response.body())
            if (!json.optBoolean("success", false)) return
            val words = highlightedWordsFrom(json)
            scope.launch {
                _wordHighlightingEnabled.value = json.optBoolean("enabled", true)
                _highlightedWords.clear()
                _highlightedWords.addAll(words)
            }
        } catch (_: Exception) {
            // Silently ignore — highlighting is optional
        }
    }

    /**
     * Periodically pulls a fresh copy of the live STT session's .db into
     * ~/.churchpresenter/bible-stt-logs/ (same folder TrainingDataLogger writes to), so the whole
     * session can be submitted as one folder without a manual, end-of-service pull from the STT
     * server — which may not be running by then. Read-only against the STT server (GET only,
     * never triggers its file_mover, which defaults to deleting the source file). Gated by
     * [helpDevModeEnabled] so ordinary users never pay the periodic download cost.
     */
    internal fun startDbCapture(baseUrl: String) {
        captureBaseUrl = baseUrl
        dbCaptureJob?.cancel()
        dbCaptureJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (helpDevModeEnabled) runCatching { captureDbSnapshot(baseUrl) }
                delay(MODEL_POLL_INTERVAL_MS)
            }
        }
    }

    internal fun captureDbSnapshot(baseUrl: String) {
        // Shares TrainingDataLogger's 30-day retention sweep so these .db snapshots don't
        // accumulate forever in bible-stt-logs — see cleanupOldLogsOnce()'s doc for why this
        // cross-package call is `internal` instead of TrainingDataLogger writing the file itself.
        TrainingDataLogger.cleanupOldLogsOnce()

        val client = HttpClient.newHttpClient()
        val dbName = remoteDbName(client, baseUrl) ?: return

        val encodedPath = URLEncoder.encode(dbName, "UTF-8")
        val downloadRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/file-manager/download?path=$encodedPath"))
            .GET()
            .build()
        val downloadResponse = client.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray())
        if (downloadResponse.statusCode() != HTTP_OK) return

        val logDir = File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs").also { it.mkdirs() }
        val target = File(logDir, File(dbName).name)
        val tmp = File(logDir, "${target.name}.tmp")
        tmp.writeBytes(downloadResponse.body())
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun remoteDbName(client: HttpClient, baseUrl: String): String? {
        val statusRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/transcription/status"))
            .GET()
            .build()
        val statusResponse = client.send(statusRequest, HttpResponse.BodyHandlers.ofString())
        if (statusResponse.statusCode() != HTTP_OK) return null
        return JSONObject(statusResponse.body()).optJSONObject("state")?.stringOrNull("db_name")
    }

    fun dispose() {
        disconnect()
    }

    private companion object {
        /** socket.io's disconnect reason when the client itself closed the socket. */
        const val CLIENT_DISCONNECT_REASON = "io client disconnect"
    }
}
