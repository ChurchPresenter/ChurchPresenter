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
import org.churchpresenter.sharedui.screenshot.PinnedFonts

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
    block: ComposeUiTest.(stt: STTManager, captionsLive: MutableState<Boolean>, reports: STTReports) -> Unit,
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
    val captionsLive = mutableStateOf(false)
    val reports = STTReports()
    try {
        stt.seed()
        runComposeUiTest {
            setContent {
                PinnedFonts {
                    ThemedForTest(themeMode) {
                        // The tab paints no ground of its own — in the app it sits on the window's
                        // `colorScheme.background`, and without that the capture is transparent
                        // everywhere the tab does not draw.
                        Surface(color = MaterialTheme.colorScheme.background) {
                        Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                        STTTab(
                            sttManager = stt,
                            captionsLive = captionsLive,
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
            }
            block(stt, captionsLive, reports)
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
