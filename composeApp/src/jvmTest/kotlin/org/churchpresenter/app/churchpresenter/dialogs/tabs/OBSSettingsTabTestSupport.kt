@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OBSSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import kotlin.test.assertEquals

/**
 * Harness and node locators shared by the `OBSSettingsTab` test classes.
 *
 * The tab is a gated form: nothing but the enable switch exists until OBS is switched on, at which
 * point a connection card and a scene-mapping card appear. Most fixtures here therefore start with
 * `enabled = true` rather than clicking through the switch each time — `OBSSettingsTabTest` already
 * covers the gate itself.
 *
 * Locating is **by displayed value**. The tab holds up to sixteen text boxes that look identical in
 * the tree — host, port, password, default scene and twelve scene mappings — with no tags, so each
 * test gives the box it drives a value no other box holds and finds it by that. The scene rows are
 * the exception: they are found by the mode label beside them, which is what [sceneFieldFor] does.
 *
 * Known gaps — what these tests do not reach, and why:
 *
 *  * **A real OBS connection.** `OBSWebSocketManager.status` is a read-only `State` fed by a private
 *    field, so the CONNECTED and ERROR states cannot be posed by a fixture; they need a WebSocket
 *    server to talk to. `OBSSettingsTabConnectionTest` stands one up for the connected path.
 *  * **The password box is masked** by a `PasswordVisualTransformation`, and the mask reaches the
 *    semantics: its `EditableText` is the bullets, not the characters. Nothing in the tree carries
 *    the typed password, so the assertions read the stored setting and check the box's bullet count
 *    against the length — which is all an operator can see either.
 */
@OptIn(ExperimentalTestApi::class)
internal fun obsTab(
    initial: AppSettings = AppSettings(),
    manager: OBSWebSocketManager = OBSWebSocketManager(),
    block: ComposeUiTest.(get: () -> AppSettings, obs: OBSWebSocketManager) -> Unit,
) = runComposeUiTest {
    var current = initial
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(current) }
            OBSSettingsTab(
                settings = state,
                onSettingsChange = { transform -> state = transform(state); current = state },
                obsManager = manager,
            )
        }
    }
    block({ current }, manager)
}

/** Settings whose OBS section is [change] applied to the defaults. */
internal fun obsSettings(change: OBSSettings.() -> OBSSettings): AppSettings =
    AppSettings().let { it.copy(obsSettings = it.obsSettings.change()) }

/** Settings with OBS switched on, so the connection and mapping cards are composed. */
internal fun obsEnabled(change: OBSSettings.() -> OBSSettings = { this }): AppSettings =
    obsSettings { copy(enabled = true).change() }

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object ObsLabel {
    const val SECTION_CONNECTION = "OBS WebSocket"
    const val SECTION_MAPPINGS = "Scene Mappings"
    const val ENABLE = "Connect to OBS Studio"
    const val HOST = "Host"
    const val PASSWORD = "Password"
    const val DEFAULT_SCENE = "Default Scene"
    const val CONNECT = "Connect"
    const val DISCONNECT = "Disconnect"
    const val CONNECTED = "Connected"
    const val CONNECTING = "Connecting…"
    const val DISCONNECTED = "Not connected"
    const val ERROR = "Error"
}

/**
 * Every presenting mode that gets a scene box, paired with the label the tab puts beside it, in the
 * order the tab lists them. The key stored in `sceneMappings` is the enum's own name.
 */
internal val obsSceneModes: List<Pair<Presenting, String>> = listOf(
    Presenting.BIBLE to "Bible",
    Presenting.LYRICS to "Songs",
    Presenting.PICTURES to "Pictures",
    Presenting.PRESENTATION to "Presentation",
    Presenting.MEDIA to "Media",
    Presenting.LOWER_THIRD to "Lower Third",
    Presenting.ANNOUNCEMENTS to "Announcements",
    Presenting.WEBSITE to "Website",
    Presenting.CANVAS to "Canvas",
    Presenting.QA to "Q&A",
    Presenting.STT to "Transcription",
    Presenting.NONE to "Clear Display",
)

// ── Locators ────────────────────────────────────────────────────────────────────────────────────

/** Every text box on the tab. */
internal fun ComposeUiTest.obsFields(): SemanticsNodeInteractionCollection = onAllNodes(hasSetTextAction())

/** The box currently displaying [value]. */
internal fun ComposeUiTest.obsFieldShowing(value: String): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasText(value))

/**
 * The box laid out to the right of the caption [caption], on the same row.
 *
 * Most boxes on this tab are blank and carry no label of their own, so they cannot be told apart by
 * value. `SettingRow` puts its caption on the left and the control beside it, which is what this
 * uses.
 */
internal fun ComposeUiTest.fieldRightOf(caption: String): SemanticsNodeInteraction {
    val captionBounds = onAllNodes(hasText(caption) and !hasSetTextAction())
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
        .minByOrNull { it.top }
        ?: error("no caption \"$caption\" on screen")
    val box = obsFields().fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filter {
            it.boundsInRoot.left >= captionBounds.right &&
                it.boundsInRoot.top < captionBounds.bottom &&
                it.boundsInRoot.bottom > captionBounds.top
        }
        .minByOrNull { it.boundsInRoot.left }
        ?: error("no box to the right of \"$caption\"")
    return onAllNodes(SemanticsMatcher("bounds == ${box.boundsInRoot}") { it.boundsInRoot == box.boundsInRoot })[0]
}

/**
 * The scene box belonging to [mode].
 *
 * The boxes are all blank out of the box and carry no label of their own, so they are found by
 * position instead: the mode's caption sits immediately to the left of its box on the same row, and
 * the tab lays the modes out two to a row in [obsSceneModes] order.
 */
internal fun ComposeUiTest.sceneFieldFor(mode: Presenting): SemanticsNodeInteraction {
    val label = obsSceneModes.first { it.first == mode }.second
    val captionBounds = onAllNodes(hasText(label) and !hasSetTextAction())
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
        .minByOrNull { it.top }
        ?: error("no caption \"$label\" on screen")
    val boxes = obsFields().fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filter { it.boundsInRoot.left >= captionBounds.right && it.boundsInRoot.top < captionBounds.bottom &&
            it.boundsInRoot.bottom > captionBounds.top }
        .minByOrNull { it.boundsInRoot.left }
        ?: error("no scene box to the right of \"$label\"")
    return onAllNodes(SemanticsMatcher("bounds == ${boxes.boundsInRoot}") { it.boundsInRoot == boxes.boundsInRoot })[0]
}

// ── Actions ─────────────────────────────────────────────────────────────────────────────────────

/** Types [scene] into [mode]'s box. */
internal fun ComposeUiTest.setScene(mode: Presenting, scene: String) {
    sceneFieldFor(mode).performTextReplacement(scene)
    waitForIdle()
}

/** Asserts some box on the tab is displaying [value]. */
internal fun ComposeUiTest.assertObsFieldShows(value: String, what: String) {
    val shown = obsFields().fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
    assertEquals(true, value in shown, "$what must display \"$value\" — boxes show $shown")
}

/**
 * A socket that accepts connections and then says nothing at all.
 *
 * Connecting to a closed port fails instantly, which makes the CONNECTING state too short-lived to
 * assert on. This accepts the TCP connection and never answers the WebSocket handshake, so the
 * manager stays in CONNECTING for as long as the test needs — deterministically, and without waiting
 * on a timeout for the success path.
 */
internal class BlackHoleSocket : AutoCloseable {
    private val socket = java.net.ServerSocket(0)
    val port: Int get() = socket.localPort
    private val accepted = mutableListOf<java.net.Socket>()

    init {
        Thread {
            while (!socket.isClosed) {
                try {
                    accepted += socket.accept()
                } catch (_: Exception) {
                    return@Thread
                }
            }
        }.apply { isDaemon = true; start() }
    }

    override fun close() {
        accepted.forEach { runCatching { it.close() } }
        runCatching { socket.close() }
    }
}
