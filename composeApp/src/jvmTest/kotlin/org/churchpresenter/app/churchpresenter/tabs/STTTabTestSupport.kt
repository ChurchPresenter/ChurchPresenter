@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.STTSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Harness and fixtures shared by the `STTTab` test classes.
 *
 * The tab is driven through a real [STTManager] and a real [PresenterManager]. Neither needs a
 * socket: captions arrive by handing the manager the same JSON payloads the STT server sends
 * (`handleTranscriptionUpdate` and friends), and the connection state is set through the
 * `apply*` transitions the socket callbacks themselves call. Nothing here opens a connection unless
 * a test clicks Connect, and [sttTab] disposes the manager afterwards either way.
 *
 * `appSettings` is a fixed value rather than hoisted state — as in the other tab suites — so a test
 * that needs a different display mode or segment cap passes it up front via [settings] rather than
 * expecting a click to change it. What a click *would* have changed is recorded in [STTReports].
 */

// ── What the tab reported back ──────────────────────────────────────────────────────────────────

internal class STTReports {
    val presenting = mutableListOf<Presenting>()
    var settingsChanges = 0
    var settingsAfterChange: AppSettings? = null
}

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Composes `STTTab` over a real [STTManager]/[PresenterManager] and runs [block].
 *
 * [seed] runs before the first composition, for tests that want captions or a connection already in
 * place when the tab first draws.
 */
@OptIn(ExperimentalTestApi::class)
internal fun sttTab(
    settings: STTSettings = STTSettings(serverUrl = SILENT_STT_URL),
    seed: STTManager.() -> Unit = {},
    width: Dp? = null,
    themeMode: ThemeMode? = null,
    block: ComposeUiTest.(stt: STTManager, presenter: PresenterManager, reports: STTReports) -> Unit,
) {
    val appSettings = AppSettings(sttSettings = settings)
    val stt = STTManager()
    val presenter = PresenterManager()
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
                        presenterManager = presenter,
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
            block(stt, presenter, reports)
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

internal val SILENT_STT_URL: String
    get() = "http://127.0.0.1:${silentSttSocket.localPort}"

// ── Feeding the manager what the STT server would send ──────────────────────────────────────────

/** One completed transcription segment, as `transcription_update` delivers it. */
internal fun STTManager.transcribe(vararg texts: String) {
    val segments = texts.mapIndexed { index, text ->
        """{"id":$index,"timestamp":"00:0$index","text":"$text","start":$index.0,"end":${index + 1}.0,"completed":true}"""
    }
    handleTranscriptionUpdate(JSONObject("""{"segments":[${segments.joinToString(",")}]}"""))
}

/**
 * The partial phrase currently being spoken, which the tab draws dimmed under the segments.
 *
 * Sent as a bare string: the transcription parser stringifies anything that is not a String, so an
 * `{"text": …}` object would arrive as its own JSON rather than as the phrase. (Translation's parser
 * does read the object form — see [translateInProgress].)
 */
internal fun STTManager.transcribeInProgress(text: String) {
    // No "segments" key: the parser clears the segment list whenever one is present, so including an
    // empty array here would wipe whatever captions a test had already sent.
    handleTranscriptionUpdate(JSONObject("""{"in_progress":"$text"}"""))
}

internal fun STTManager.translate(vararg texts: String) {
    val segments = texts.mapIndexed { index, text ->
        """{"id":$index,"timestamp":"00:0$index","translated_text":"$text","start":$index.0,"end":${index + 1}.0,"completed":true}"""
    }
    handleTranslationUpdate(JSONObject("""{"segments":[${segments.joinToString(",")}]}"""))
}

internal fun STTManager.translateInProgress(text: String) {
    // As with [transcribeInProgress], no "segments" key — it would clear the translated captions.
    handleTranslationUpdate(JSONObject("""{"in_progress":{"translated_text":"$text"}}"""))
}

/** Words the STT server wants coloured, as `word_highlighting_update` delivers them. */
internal fun STTManager.highlight(vararg words: Pair<String, String>, enabled: Boolean = true) {
    val entries = words.map { (word, color) -> """{"word":"$word","color":"$color"}""" }
    handleWordHighlightingUpdate(
        JSONObject("""{"enabled":$enabled,"words":[${entries.joinToString(",")}]}""")
    )
}

/** Connected, with captions already on screen — the tab's main state. */
internal fun STTManager.live(vararg texts: String) {
    applyConnected()
    if (texts.isNotEmpty()) transcribe(*texts)
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object STTLabel {
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

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────

// renderedText/showsExactly/showsContainingText live in TabRenderedText.kt — they are shared with
// the other tab suites in this package.

/** A button, addressed by the content description its tooltip gives it. */
internal fun ComposeUiTest.sttButton(label: String) = onNodeWithContentDescription(label)

internal fun ComposeUiTest.hasSttButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/** The server-URL field — the only control on the tab that takes typed text. */
internal fun ComposeUiTest.urlField() = onAllNodes(hasSetTextAction())[0]

/**
 * Whether the url field can still be typed into.
 *
 * The tab disables it while a connection is up or in flight, and a disabled text field drops its
 * set-text action entirely — so "locked" is the absence of the field rather than a disabled node.
 */
internal fun ComposeUiTest.urlFieldIsEditable(): Boolean =
    onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

/**
 * What the url field currently holds.
 *
 * A field's contents are `EditableText`, not `Text`, so they never appear in [renderedText] — an
 * assertion phrased against that would pass whatever the field said.
 */
internal fun ComposeUiTest.urlFieldText(): String =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
        .orEmpty()
