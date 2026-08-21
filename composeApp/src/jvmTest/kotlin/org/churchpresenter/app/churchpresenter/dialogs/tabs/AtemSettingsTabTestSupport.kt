@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs
import kotlin.test.assertEquals

/**
 * Harness and node locators shared by the `AtemSettingsTab` test classes.
 *
 * The tab is **thirteen text boxes, three switches and one button** spread over three cards, and the
 * boxes are what makes it awkward to address: eleven of them hold a bare number, several hold the
 * same number out of the box, and none carries a test tag. So every fixture here gives each box a
 * value no other box holds, and boxes are found by the caption printed above them — see
 * [atemFieldUnder]. The two boxes on the IP-address row have no caption of their own (they sit in a
 * `SettingRow` that captions the pair), so they are found by position on that row instead.
 *
 * Two conventions the tab follows that the tests lean on:
 *
 *  * **Slots, M/E, keyers and DSK are stored 0-based and shown 1-based**, matching ATEM Software
 *    Control. A box reading "5" is a stored 4. Every slot assertion checks both halves, because the
 *    off-by-one is the whole point of the conversion.
 *  * **A box keeps whatever is typed, but only what parses reaches the settings.** `remember(key)` is
 *    keyed on the stored value, so a parse that lands re-formats the box (typing `0` into a slot
 *    snaps it back to `1`, the coerced minimum) while a parse that fails leaves the typed text alone.
 *
 * The one thing a `SettingsTextField` publishes nowhere in the semantics tree is its **error state** —
 * `isError` only recolours the border. [fieldBorderColour] reads that pixel so the range checks can be
 * asserted as what an operator actually sees, following `SongSettingsTabTestSupport.renderedPixels`.
 *
 * Known gaps — what these tests do not reach, and why:
 *
 *  * **A successful Test Connection.** The button constructs an `AtemClient` against the typed host
 *    and calls `queryState()`; the connected path needs a real ATEM answering the Blackmagic UDP
 *    handshake, so the "Connected" status, the detected-video-mode line and the write-back of the
 *    detected slot/keyer counts are unreachable from here. The failure and in-flight paths are both
 *    driven — see `AtemSettingsTabConnectionTest`.
 */
@OptIn(ExperimentalTestApi::class)
internal fun atemTab(
    initial: AppSettings = AppSettings(),
    block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
) = runComposeUiTest {
    var current = initial
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(current) }
            AtemSettingsTab(
                settings = state,
                onSettingsChange = { transform -> state = transform(state); current = state },
            )
        }
    }
    block { current }
}

/** Settings whose ATEM section is [change] applied to the defaults. */
internal fun atemSettings(change: AtemSettings.() -> AtemSettings): AppSettings =
    AppSettings().let { it.copy(atemSettings = it.atemSettings.change()) }

/**
 * A fixture in which **every box holds a different value**, so any of them can be found by what it
 * displays and no assertion can pass by accidentally reading its neighbour. The detected counts are
 * set wide enough that nothing here is out of range.
 */
internal fun atemAllDistinct(): AppSettings =
    atemSettings {
        copy(
            host = "10.0.0.5",
            port = 9911,
            defaultStillSlot = 4,      // shown 5
            defaultClipSlot = 6,       // shown 7
            backgroundSlot1 = 8,       // shown 9
            backgroundSlot2 = 10,      // shown 11
            renderWidth = 1280,
            renderHeight = 720,
            clipFps = 25.0,
            keyMixEffect = 1,          // shown 2
            keyIndex = 2,              // shown 3
            dskIndex = 3,              // shown 4
            keyPreRollMs = 150,
            keyPostRollMs = 250,
            detectedStillSlots = 20,
            detectedClipSlots = 20,
            detectedMixEffects = 4,
            detectedKeyersPerMe = listOf(4, 4, 4, 4),
            detectedDownstreamKeyers = 4,
        )
    }

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

/**
 * `SettingsTextField` upper-cases the label it is given, so the box captions are shouted here while
 * the card titles, switch captions and hints are not.
 */
internal object AtemLabel {
    const val SECTION_CONNECTION = "Blackmagic ATEM"
    const val SECTION_LOWER_THIRD = "Lower Third Uploads"
    const val SECTION_BACKGROUNDS = "Background Uploads"
    const val DESCRIPTION = "Upload lower third animations to a Blackmagic ATEM switcher's media " +
        "pool. Use Bitfocus Companion to trigger the DSK on/off."
    const val HOST_ROW = "ATEM IP Address"
    const val HOST_HINT = "e.g. 192.168.1.100"
    const val RESOLUTION = "Render Resolution"
    const val TEST_HINT = "Press Test Connection to auto-detect video format and FPS from your ATEM."

    const val TEST = "Test Connection"
    const val CONNECTING = "Connecting…"
    const val CONNECTED = "Connected"
    const val NOT_CONNECTED = "Not connected"

    const val WIDTH = "WIDTH"
    const val HEIGHT = "HEIGHT"
    const val FPS = "FPS"
    const val FPS_HINT = "25, 30, 50 or 60"
    const val STILL_SLOT = "STILL SLOT"
    const val CLIP_SLOT = "CLIP SLOT"
    const val BACKGROUND_SLOT_1 = "BACKGROUND SLOT 1"
    const val BACKGROUND_SLOT_2 = "BACKGROUND SLOT 2"
    const val ME = "M/E"
    const val KEY = "KEY"
    const val DSK = "DSK"
    const val PRE_ROLL = "KEY PRE-ROLL (MS)"
    const val POST_ROLL = "KEY POST-ROLL (MS)"

    const val DSK_SWITCH = "Downstream keyer (DSK)"
    const val DSK_SWITCH_HINT = "Drive the key as a downstream keyer instead of an upstream keyer (USK)"
    const val QUICK_UPLOAD = "Quick upload"
    const val QUICK_UPLOAD_HINT = "Skip the dialog — upload immediately to the default slots"
    const val GO_LIVE_KEY = "Go Live drives ATEM key"
    const val GO_LIVE_KEY_HINT =
        "Pressing Go Live also cuts the upstream key on air and off again when the animation ends"

    const val CAPACITY_UNKNOWN = "Run Test Connection to detect clip capacity"
    const val KEYERS_UNKNOWN = "Run Test Connection to detect M/E and key counts"
}

// ── Locators ────────────────────────────────────────────────────────────────────────────────────

/** Every text box on the tab. */
internal fun ComposeUiTest.atemBoxes(): SemanticsNodeInteractionCollection = onAllNodes(hasSetTextAction())

/** Every switch on the tab. */
internal fun ComposeUiTest.atemSwitches(): SemanticsNodeInteractionCollection = onAllNodes(isToggleable())

/**
 * The box captioned [base].
 *
 * A caption grows a detected range once a Test Connection has run — "STILL SLOT" becomes
 * "STILL SLOT (1–20)" — so it is matched as the base name with or without that suffix, which also
 * keeps "KEY" from matching "KEY PRE-ROLL (MS)". The box itself is the first one below the caption
 * sharing its left edge, which is how `SettingsTextField` stacks the two.
 */
internal fun ComposeUiTest.atemFieldUnder(base: String): SemanticsNodeInteraction {
    val caption = onAllNodes(SemanticsMatcher("field caption \"$base\"") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            .any { it.text == base || it.text.startsWith("$base (1–") }
    }).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .minByOrNull { it.boundsInRoot.top }
        ?: error("no box captioned \"$base\" on screen")
    val box = boxes()
        .filter { abs(it.boundsInRoot.left - caption.boundsInRoot.left) < 1f }
        .filter { it.boundsInRoot.top >= caption.boundsInRoot.bottom }
        .minByOrNull { it.boundsInRoot.top }
        ?: error("no box below the caption \"$base\"")
    return nodeAt(box.boundsInRoot)
}

/** True when the tab is currently drawing a box captioned [base]. */
internal fun ComposeUiTest.hasFieldUnder(base: String): Boolean =
    onAllNodes(SemanticsMatcher("field caption \"$base\"") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            .any { it.text == base || it.text.startsWith("$base (1–") }
    }).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

/** The caption a box carries right now, detected-range suffix and all. */
internal fun ComposeUiTest.captionOf(base: String): String {
    val node = onAllNodes(SemanticsMatcher("field caption \"$base\"") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            .any { it.text == base || it.text.startsWith("$base (1–") }
    }).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .minByOrNull { it.boundsInRoot.top }
        ?: error("no box captioned \"$base\" on screen")
    return node.config.getOrNull(SemanticsProperties.Text).orEmpty().first().text
}

/** The IP address box — the leftmost of the two on the [AtemLabel.HOST_ROW] row. */
internal fun ComposeUiTest.atemHostBox(): SemanticsNodeInteraction = nodeAt(hostRowBoxes()[0].boundsInRoot)

/** The port box — the one beside the IP address box on the same row. */
internal fun ComposeUiTest.atemPortBox(): SemanticsNodeInteraction = nodeAt(hostRowBoxes()[1].boundsInRoot)

/**
 * The switch captioned [caption].
 *
 * The switch and its caption are one node: `LabeledSwitch` puts `toggleable` on the row, so the
 * toggleable node carries both lines of the caption. This used to hunt for a switch positioned to the
 * left of the caption's first line, which is what the hand-rolled `Row(Switch, Column(Text, Text))`
 * required — and which also meant clicking the caption did nothing.
 */
internal fun ComposeUiTest.atemSwitchFor(caption: String): SemanticsNodeInteraction {
    val switch = atemSwitches().fetchSemanticsNodes(atLeastOneRootRequired = false)
        .firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == caption }
        }
        ?: error("no switch captioned \"$caption\" on screen")
    return nodeAt(switch.boundsInRoot)
}

private fun ComposeUiTest.boxes(): List<SemanticsNode> =
    atemBoxes().fetchSemanticsNodes(atLeastOneRootRequired = false)

private fun ComposeUiTest.hostRowBoxes(): List<SemanticsNode> {
    val caption = onAllNodes(SemanticsMatcher("row caption \"${AtemLabel.HOST_ROW}\"") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == AtemLabel.HOST_ROW }
    }).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .minByOrNull { it.boundsInRoot.top }
        ?: error("no \"${AtemLabel.HOST_ROW}\" row on screen")
    val bounds = caption.boundsInRoot
    return boxes()
        .filter { it.boundsInRoot.left >= bounds.right }
        .filter { it.boundsInRoot.top < bounds.bottom && it.boundsInRoot.bottom > bounds.top }
        .sortedBy { it.boundsInRoot.left }
        .also { if (it.size < 2) error("the ${AtemLabel.HOST_ROW} row must hold a host box and a port box") }
}

private fun ComposeUiTest.nodeAt(bounds: Rect): SemanticsNodeInteraction =
    onAllNodes(SemanticsMatcher("bounds == $bounds") { it.boundsInRoot == bounds })[0]

// ── Reading and driving ─────────────────────────────────────────────────────────────────────────

/** Replaces what a box holds and lets the tab settle. */
internal fun ComposeUiTest.type(field: SemanticsNodeInteraction, text: String) {
    field.performTextReplacement(text)
    waitForIdle()
}

/** Asserts a box is displaying [value] — its half of every settings assertion. */
internal fun SemanticsNodeInteraction.assertShows(value: String, what: String) {
    val shown = fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text
    assertEquals(value, shown, "$what must display \"$value\"")
}

/**
 * The colour of the outline drawn around [field].
 *
 * `SettingsTextField` publishes its `isError` state nowhere in the semantics tree — the only thing
 * that changes is the border, drawn in the theme's error colour instead of its outline colour. So it
 * is read off the rendered page instead.
 *
 * The border is *outside* every node that has bounds: `SettingsTextField` draws it before its 9dp
 * inset, so the box's semantics bounds are the padded interior and the stroke is some pixels further
 * left. Rather than bake that inset in, this walks left from the interior — mid-height, where the 6dp
 * corner radius cannot interfere — until the fill gives way to something else, and returns that. It
 * therefore keeps working if the padding is ever retuned.
 */
internal fun ComposeUiTest.fieldBorderColour(field: SemanticsNodeInteraction): Int {
    val bounds = field.fetchSemanticsNode().boundsInRoot
    val pixels = onRoot().captureToImage().toPixelMap()
    val y = ((bounds.top + bounds.bottom) / 2).toInt()
    val edge = bounds.left.toInt() - 1                 // one pixel into the padding, clear of the text
    val fill = pixels[edge, y].toArgb()
    for (x in edge - 1 downTo edge - 16) {
        val pixel = pixels[x, y].toArgb()
        if (pixel != fill) return pixel
    }
    error("no border drawn to the left of the box at $bounds")
}

/**
 * The outline colour of [field] with the field focused.
 *
 * The border carries two states, not one: `SettingsTextField` also draws it in the accent colour while
 * it holds focus, so that a keyboard user can see which of a screenful of boxes they are typing into.
 * A baseline read before a field is typed into is therefore unfocused, and comparing it with a reading
 * taken after typing would differ on focus alone — passing whether or not the error state being tested
 * does anything. Anything comparing a field against itself across an edit takes its baseline here.
 */
internal fun ComposeUiTest.focusedFieldBorderColour(field: SemanticsNodeInteraction): Int {
    field.performClick()
    waitForIdle()
    return fieldBorderColour(field)
}

/**
 * A UDP socket that binds a loopback port and never answers.
 *
 * Test Connection's in-flight state cannot be caught by racing it: against a closed port the attempt
 * can fail the moment it starts. Bound-but-silent makes the wait deterministic — the client's own
 * 5s receive window is the only thing that can end it, so the tab is reliably still connecting when
 * the assertions run, on every platform.
 */
internal class SilentAtem : AutoCloseable {
    private val socket = DatagramSocket(0, InetAddress.getLoopbackAddress())
    val port: Int get() = socket.localPort
    override fun close() = socket.close()
}
