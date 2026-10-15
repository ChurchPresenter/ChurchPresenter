package org.churchpresenter.stt

import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket

/**
 * A loopback port that accepts TCP connections and then says nothing at all.
 *
 * Used wherever a test clicks Connect. It has to be a real URL, and it has to keep socket.io in its
 * *connecting* state deterministically, because that is what several assertions here are about — so
 * the endpoint has to be reachable at the TCP level and silent above it. Binding a `ServerSocket` and
 * never calling `accept()` does exactly that: the kernel completes the handshake into the backlog and
 * no HTTP response ever comes.
 *
 * The earlier fixture was `http://192.0.2.1:1` (TEST-NET-1). That failed fast on macOS but on a Linux
 * CI runner packets to an unrouted address are dropped rather than refused, so every attempt waited
 * out the full TCP connect timeout while socket.io retried forever
 * (`setReconnectionAttempts(Int.MAX_VALUE)`) — which pushed the whole `jvmTest` task past CI's 25
 * minute step budget. A refused port would have fixed the hang but broken the assertions, since the
 * manager would leave `connecting` before a test could read it.
 *
 * One socket for the whole JVM, opened on first use. It is deliberately never closed: it holds a
 * single file descriptor and the tests that depend on it run throughout the suite.
 */
private val silentSttSocket: ServerSocket by lazy {
    ServerSocket(0, 1, InetAddress.getLoopbackAddress())
}

val SILENT_STT_URL: String
    get() = "http://127.0.0.1:${silentSttSocket.localPort}"

// ── Feeding the manager what the STT server would send ──────────────────────────────────────────

/** One completed transcription segment, as `transcription_update` delivers it. */
@Suppress("MaxLineLength")
fun STTManager.transcribe(vararg texts: String) {
    val segments = texts.mapIndexed { index, text ->
        """{"id":$index,"timestamp":"00:0$index","text":"$text","start":$index.0,"end":${index + 1}.0,"completed":true}"""
    }
    transcript.handleTranscriptionUpdate(JSONObject("""{"segments":[${segments.joinToString(",")}]}"""))
}

/**
 * The partial phrase currently being spoken, which the tab draws dimmed under the segments.
 *
 * Sent as a bare string: the transcription parser stringifies anything that is not a String, so an
 * `{"text": …}` object would arrive as its own JSON rather than as the phrase. (Translation's parser
 * does read the object form — see [translateInProgress].)
 */
fun STTManager.transcribeInProgress(text: String) {
    // No "segments" key: the parser clears the segment list whenever one is present, so including an
    // empty array here would wipe whatever captions a test had already sent.
    transcript.handleTranscriptionUpdate(JSONObject("""{"in_progress":"$text"}"""))
}

@Suppress("MaxLineLength")
fun STTManager.translate(vararg texts: String) {
    val segments = texts.mapIndexed { index, text ->
        """{"id":$index,"timestamp":"00:0$index","translated_text":"$text","start":$index.0,"end":${index + 1}.0,"completed":true}"""
    }
    transcript.handleTranslationUpdate(JSONObject("""{"segments":[${segments.joinToString(",")}]}"""))
}

fun STTManager.translateInProgress(text: String) {
    // As with [transcribeInProgress], no "segments" key — it would clear the translated captions.
    transcript.handleTranslationUpdate(JSONObject("""{"in_progress":{"translated_text":"$text"}}"""))
}

/** Words the STT server wants coloured, as `word_highlighting_update` delivers them. */
fun STTManager.highlight(vararg words: Pair<String, String>, enabled: Boolean = true) {
    val entries = words.map { (word, color) -> """{"word":"$word","color":"$color"}""" }
    transcript.handleWordHighlightingUpdate(
        JSONObject("""{"enabled":$enabled,"words":[${entries.joinToString(",")}]}""")
    )
}

/** Connected, with captions already on screen — the tab's main state. */
fun STTManager.live(vararg texts: String) {
    applyConnected()
    if (texts.isNotEmpty()) transcribe(*texts)
}
