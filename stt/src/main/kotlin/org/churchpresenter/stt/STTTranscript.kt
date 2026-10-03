package org.churchpresenter.stt

import org.churchpresenter.sharedui.utils.SttClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private const val HTTP_OK = 200
private const val DEFAULT_HIGHLIGHT_COLOR = "#ffff00"

/**
 * What the STT server sends: the transcript, its translation, and the words to highlight in both.
 * [STTManager] owns the socket and hands each payload here.
 *
 * The `handle*Update` parsers are `internal` rather than private: in production they are reachable
 * only from a socket callback, which needs a live STT server, but what they make of a payload is
 * ordinary parsing.
 */
internal class STTTranscript(private val scope: CoroutineScope) {

    private val _segments = mutableStateListOf<STTSegment>()
    val segments: List<STTSegment> get() = _segments

    private val _inProgressText = mutableStateOf("")
    val inProgressText: State<String> = _inProgressText

    private val _translationSegments = mutableStateListOf<STTSegment>()
    val translationSegments: List<STTSegment> get() = _translationSegments

    private val _inProgressTranslation = mutableStateOf("")
    val inProgressTranslation: State<String> = _inProgressTranslation

    private val _translationLanguage = mutableStateOf("")
    val translationLanguage: State<String> = _translationLanguage

    private val _highlightedWords = mutableStateListOf<HighlightedWord>()
    val highlightedWords: List<HighlightedWord> get() = _highlightedWords

    private val _wordHighlightingEnabled = mutableStateOf(true)
    val wordHighlightingEnabled: State<Boolean> = _wordHighlightingEnabled

    fun handleTranscriptionUpdate(data: JSONObject) {
        applySessionId(data.stringOrNull("session_id"))
        data.optJSONArray("segments")?.let { array ->
            _segments.clear()
            _segments.addAll(segmentsFrom(array) { it.stringOr("text") })
            // How far into the recording STT has transcribed, as of now: the on-screen history's clock
            _segments.maxOfOrNull { it.end }?.let { SttClock.observe(it) }
        }

        val inProgress = data.opt("in_progress")
        _inProgressText.value = when (inProgress) {
            is String -> inProgress
            null, JSONObject.NULL -> ""
            else -> inProgress.toString()
        }
    }

    fun handleTranslationUpdate(data: JSONObject) {
        applySessionId(data.stringOrNull("session_id"))
        data.optJSONArray("segments")?.let { array ->
            _translationSegments.clear()
            // STT app sends "translated_text" for translation segments
            _translationSegments.addAll(
                segmentsFrom(array) { seg -> seg.stringOr("translated_text").ifBlank { seg.stringOr("text") } }
            )
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

    fun handleWordHighlightingUpdate(data: JSONObject) {
        _wordHighlightingEnabled.value = data.optBoolean("enabled", true)
        _highlightedWords.clear()
        _highlightedWords.addAll(highlightedWordsFrom(data))
    }

    /**
     * The highlighted-word list is not pushed on connect, so it is pulled over REST once the socket
     * comes up. `internal` rather than private for the same reason as the `handle*Update` parsers:
     * in production it is only reachable from a socket callback, and it is plain HTTP otherwise.
     */
    fun fetchWordHighlighting(baseUrl: String) {
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

    private fun segmentsFrom(array: JSONArray, textOf: (JSONObject) -> String): List<STTSegment> =
        (0 until array.length()).map { i ->
            val seg = array.getJSONObject(i)
            STTSegment(
                id = seg.optInt("id", i),
                timestamp = seg.stringOr("timestamp"),
                text = textOf(seg),
                start = seg.optDouble("start", 0.0),
                end = seg.optDouble("end", 0.0),
                completed = seg.optBoolean("completed", true)
            )
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
            .filter { it.stringOr("color", DEFAULT_HIGHLIGHT_COLOR) !in disabledColors }
            .map { w ->
                HighlightedWord(
                    word = w.stringOr("word"),
                    color = w.stringOr("color", DEFAULT_HIGHLIGHT_COLOR),
                    caseSensitive = w.optBoolean("case_sensitive", false),
                    isRegex = w.optBoolean("is_regex", false)
                )
            }
    }
}
