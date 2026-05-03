package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

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

    fun connect(url: String) {
        if (_connected.value || _connecting.value) return
        _connecting.value = true

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
                    scope.launch {
                        _connected.value = true
                        _connecting.value = false
                    }
                    // Request initial data
                    s.emit("request_all_entries")
                    s.emit("request_all_translation_entries")
                    // Fetch word highlighting via REST (not sent on connect via socket)
                    scope.launch(Dispatchers.IO) {
                        fetchWordHighlighting(url)
                    }
                }

                s.on(Socket.EVENT_DISCONNECT) {
                    scope.launch {
                        _connected.value = false
                    }
                }

                s.on(Socket.EVENT_CONNECT_ERROR) {
                    scope.launch {
                        _connected.value = false
                        _connecting.value = false
                    }
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
            } catch (e: Exception) {
                scope.launch {
                    _connecting.value = false
                    _connected.value = false
                }
            }
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.close()
        socket = null
        _connected.value = false
        _connecting.value = false
    }

    private fun handleTranscriptionUpdate(data: JSONObject) {
        val segmentsArray = data.optJSONArray("segments")
        if (segmentsArray != null) {
            _segments.clear()
            for (i in 0 until segmentsArray.length()) {
                val seg = segmentsArray.getJSONObject(i)
                _segments.add(
                    STTSegment(
                        id = seg.optInt("id", i),
                        timestamp = seg.optString("timestamp", ""),
                        text = seg.optString("text", ""),
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

    private fun handleTranslationUpdate(data: JSONObject) {
        val segmentsArray = data.optJSONArray("segments")
        if (segmentsArray != null) {
            _translationSegments.clear()
            for (i in 0 until segmentsArray.length()) {
                val seg = segmentsArray.getJSONObject(i)
                // STT app sends "translated_text" for translation segments
                val text = seg.optString("translated_text", "").ifBlank {
                    seg.optString("text", "")
                }
                _translationSegments.add(
                    STTSegment(
                        id = seg.optInt("id", i),
                        timestamp = seg.optString("timestamp", ""),
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
            is JSONObject -> inProgress.optString("translated_text", "")
            is String -> inProgress
            null, JSONObject.NULL -> ""
            else -> inProgress.toString()
        }

        _translationLanguage.value = data.optString("target_language_name", "")
    }

    private fun handleWordHighlightingUpdate(data: JSONObject) {
        _wordHighlightingEnabled.value = data.optBoolean("enabled", true)
        val wordsArray = data.optJSONArray("words")
        _highlightedWords.clear()
        if (wordsArray != null) {
            for (i in 0 until wordsArray.length()) {
                val w = wordsArray.getJSONObject(i)
                _highlightedWords.add(
                    HighlightedWord(
                        word = w.optString("word", ""),
                        color = w.optString("color", "#ffff00"),
                        caseSensitive = w.optBoolean("case_sensitive", false),
                        isRegex = w.optBoolean("is_regex", false)
                    )
                )
            }
        }
    }

    private fun fetchWordHighlighting(baseUrl: String) {
        try {
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/word-highlighting/words"))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val json = JSONObject(response.body())
                if (json.optBoolean("success", false)) {
                    scope.launch {
                        _wordHighlightingEnabled.value = json.optBoolean("enabled", true)
                        val wordsArray = json.optJSONArray("words")
                        _highlightedWords.clear()
                        if (wordsArray != null) {
                            for (i in 0 until wordsArray.length()) {
                                val w = wordsArray.getJSONObject(i)
                                _highlightedWords.add(
                                    HighlightedWord(
                                        word = w.optString("word", ""),
                                        color = w.optString("color", "#ffff00"),
                                        caseSensitive = w.optBoolean("case_sensitive", false),
                                        isRegex = w.optBoolean("is_regex", false)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Silently ignore — highlighting is optional
        }
    }

    fun dispose() {
        disconnect()
    }
}
