@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.stt.screenshot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.stt.STTLabel
import org.churchpresenter.stt.highlight
import org.churchpresenter.stt.live
import org.churchpresenter.stt.sttButton
import org.churchpresenter.stt.sttTab
import org.churchpresenter.stt.transcribe
import org.churchpresenter.stt.transcribeInProgress
import org.churchpresenter.stt.translate
import org.churchpresenter.stt.translateInProgress
import org.churchpresenter.stt.STTManager
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.captureTo
import org.churchpresenter.ui.screenshot.stackedThemes

/**
 * Every state of the live-captions tab, in both themes.
 *
 * Nothing here opens a socket. Captions arrive by handing the manager the same JSON the STT server
 * sends, and each connection state is set through the transition its socket callback would call — so
 * `connecting`, `unreachable` and `reconnecting` are all reachable without a server to be unreachable
 * to. The one state that does click Connect points at a port that accepts and then stays silent.
 */
class STTTabScreenshotTest {

    private fun shoot(
        name: String,
        settings: STTSettings = STTSettings(serverUrl = URL),
        seed: STTManager.() -> Unit = {},
        width: Dp? = null,
        drive: ComposeUiTest.(STTManager) -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        sttTab(settings = settings, seed = seed, width = width, themeMode = mode) { stt, _, _ ->
            drive(stt)
            waitForIdle()
            captureTo(file)
        }
    }

    // ── Getting connected ───────────────────────────────────────────────────────────────────────

    @Test
    fun `not connected yet`() = shoot("not_connected")

    @Test
    fun `no server URL entered`() = shoot("no_url", settings = STTSettings(serverUrl = ""))

    /**
     * Driven through the manager's own `applyConnecting` transition rather than by clicking Connect:
     * a click needs a reachable-but-silent port, whose number the URL field would then print — and
     * that port is handed out by the OS, so the image would differ on every run.
     */
    @Test
    fun `connecting`() = shoot("connecting", seed = { applyConnecting() })

    @Test
    fun `the server cannot be reached`() = shoot("unreachable", seed = { applyConnectError() })

    @Test
    fun `the connection dropped and is being retried`() =
        shoot("reconnecting", seed = { applyConnectError(); applyDisconnected(reason = "transport close") })

    @Test
    fun `connected, waiting for the first caption`() = shoot("waiting", seed = { applyConnected() })

    // ── Captions arriving ───────────────────────────────────────────────────────────────────────

    @Test
    fun `captions on screen`() = shoot("transcribing", seed = { live(*SERMON) })

    /** The phrase still being spoken, drawn dimmed under the finished ones. */
    @Test
    fun `a phrase still being spoken`() = shoot(
        "in_progress",
        settings = STTSettings(serverUrl = URL, showInProgress = true),
        seed = { live(*SERMON); transcribeInProgress("and he said to them") },
    )

    @Test
    fun `more captions than the preview keeps`() = shoot("segment_cap", seed = { live(*LONG_SERMON) })

    @Test
    fun `no cap, so every caption stays`() = shoot(
        "no_segment_cap",
        settings = STTSettings(serverUrl = URL, maxSegments = 0),
        seed = { live(*LONG_SERMON) },
    )

    @Test
    fun `words the server asked to be coloured`() = shoot(
        "word_highlighting",
        settings = STTSettings(serverUrl = URL, showWordHighlighting = true),
        seed = {
            live(*SERMON)
            highlight("grace" to "#43A047", "faith" to "#1E88E5", "Lord" to "#E53935")
        },
    )

    // ── Translation ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `translation only`() = shoot(
        "translate_only",
        settings = STTSettings(serverUrl = URL, displayMode = "translate"),
        seed = { applyConnected(); transcribe(*SERMON); translate(*SERMON_ES) },
    )

    // Not shot: translate mode with only a transcription in. The tab draws the same "waiting" panel
    // as a freshly connected one, byte for byte, so a second image says nothing a reviewer can use.
    // `STTTabTest` covers that the two are reached by different paths.

    @Test
    fun `both columns`() = shoot(
        "both_columns",
        settings = STTSettings(serverUrl = URL, displayMode = "both"),
        seed = { applyConnected(); transcribe(*SERMON); translate(*SERMON_ES) },
    )

    @Test
    fun `both columns, both mid-phrase`() = shoot(
        "both_in_progress",
        settings = STTSettings(
            serverUrl = URL,
            displayMode = "both",
            showInProgress = true,
            showTranslationInProgress = true,
        ),
        seed = {
            applyConnected()
            transcribe(*SERMON)
            translate(*SERMON_ES)
            transcribeInProgress("and he said to them")
            translateInProgress("y les dijo")
        },
    )

    // ── Live ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Go Live pressed, so the button has dimmed itself.
     *
     * This image was re-recorded when the tab moved to `:stt-tab`, and the change is a fix rather
     * than a redesign. The old harness gave the tab a real `PresenterManager` but a `presenting`
     * lambda that only *recorded* the call, so `presentingMode` never moved and the button stayed
     * enabled — a state the app cannot reach, because `main.kt`'s lambda calls
     * `setPresentingMode(mode)`. `FakeSttOutput` flips `isLive`, as the real adapter does, so what
     * is captured now is what an operator sees.
     */
    @Test
    fun `captions taken live`() = shoot("live", seed = { live(*SERMON) }) {
        sttButton(STTLabel.GO_LIVE).performClick()
        waitForIdle()
    }

    // ── Panel widths ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a narrow panel`() = shoot("narrow_panel", seed = { live(*SERMON) }, width = 520.dp)

    @Test
    fun `both columns in a narrow panel`() = shoot(
        "narrow_both_columns",
        settings = STTSettings(serverUrl = URL, displayMode = "both"),
        seed = { applyConnected(); transcribe(*SERMON); translate(*SERMON_ES) },
        width = 620.dp,
    )

    private companion object {
        const val SECTION = "sttTab"

        /** Fixed, and never actually dialled: every connection state here is set on the manager. */
        const val URL = "http://stt.church.local:9000"

        val SERMON = arrayOf(
            "For by grace are ye saved through faith",
            "and that not of yourselves",
            "it is the gift of God",
        )

        val SERMON_ES = arrayOf(
            "Porque por gracia sois salvos por medio de la fe",
            "y esto no de vosotros",
            "pues es don de Dios",
        )

        /** Eight captions against the default cap of five, so the drop is visible. */
        val LONG_SERMON = Array(8) { "Caption line number ${it + 1} of the message" }
    }
}
