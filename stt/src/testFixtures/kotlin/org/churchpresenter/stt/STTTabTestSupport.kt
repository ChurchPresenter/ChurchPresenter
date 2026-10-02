@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.stt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Harness and fixtures shared by the `STTTab` test classes.
 *
 * The tab is driven through a real [STTManager], and the output's mode is a plain state the test
 * sets. Nothing needs a socket: captions arrive by handing the manager the same JSON payloads the STT server sends
 * (`handleTranscriptionUpdate` and friends), and the connection state is set through the
 * `apply*` transitions the socket callbacks themselves call. Nothing here opens a connection unless
 * a test clicks Connect, and [sttTab] disposes the manager afterwards either way.
 *
 * `appSettings` is a fixed value rather than hoisted state — as in the other tab suites — so a test
 * that needs a different display mode or segment cap passes it up front via [settings] rather than
 * expecting a click to change it. What a click *would* have changed is recorded in [STTReports].
 */

// ── What the tab reported back ──────────────────────────────────────────────────────────────────

class STTReports {
    val presenting = mutableListOf<Presenting>()
    var settingsChanges = 0
    var settingsAfterChange: AppSettings? = null
}

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Composes `STTTab` over a real [STTManager] and runs [block], with the output presenting nothing
 * until the test sets the mode it is handed.
 *
 * [seed] runs before the first composition, for tests that want captions or a connection already in
 * place when the tab first draws.
 */
@OptIn(ExperimentalTestApi::class)
fun sttTab(
    settings: STTSettings = STTSettings(serverUrl = SILENT_STT_URL),
    seed: STTManager.() -> Unit = {},
    width: Dp? = null,
    themeMode: ThemeMode? = null,
    block: ComposeUiTest.(stt: STTManager, presentingMode: MutableState<Presenting>, reports: STTReports) -> Unit,
) {
    // On the document for the server, and on every profile for the rest: the tab's transcript
    // reads the caption settings of a profile that shows captions, which are per profile now.
    val appSettings = AppSettings(sttSettings = settings).let { doc ->
        val projection = doc.projectionSettings
        doc.copy(
            projectionSettings = projection.copy(
                outputProfiles = projection.outputProfiles.map { it.copy(sttSettings = settings) },
            ),
        )
    }
    val stt = STTManager()
    val presentingMode = mutableStateOf(Presenting.NONE)
    val reports = STTReports()
    try {
        stt.seed()
        runComposeUiTest {
            setContent {
                ThemedForTest(themeMode) {
                    // The tab paints no ground of its own — in the app it sits on the window's
                    // `colorScheme.background`, and without that the capture is transparent
                    // everywhere the tab does not draw.
                    Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                    STTTab(
                        sttManager = stt,
                        presentingMode = presentingMode,
                        presenting = { reports.presenting += it },
                        appSettings = appSettings,
                        onSettingsChange = { transform ->
                            reports.settingsChanges++
                            reports.settingsAfterChange = transform(appSettings)
                        },
                    )
                    }
                    }
                }
            }
            block(stt, presentingMode, reports)
        }
    } finally {
        runCatching { stt.dispose() }
    }
}

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}

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

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

object STTLabel {
    const val SERVER_URL = "STT Server URL"
    const val CONNECT = "Connect"
    const val DISCONNECT = "Disconnect"
    const val CLEAR = "Clear"
    const val SETTINGS = "STT Display Settings"
    const val GO_LIVE = "Go Live"
    const val LIVE_PREVIEW = "Live Preview"
    const val NOT_CONNECTED = "Not connected. Enter the STT server URL and click Connect."
    const val WAITING = "Waiting for transcription…"
    const val TRANSCRIPTION = "Transcription"
    const val TRANSLATION = "Translation"
    const val CONNECTING = "Connecting to STT…"
    const val UNREACHABLE = "Can't reach STT server — retrying…"
    const val RECONNECTING = "STT disconnected — reconnecting…"
}
