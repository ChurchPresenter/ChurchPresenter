@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Harness, locators and label constants shared by the `SourcePropertiesPanel` test classes.
 *
 * The panel is the canvas compositor's inspector: one public composable that renders a shared header
 * (name + transform) and then dispatches on the sealed [SceneSource] type to one of eleven private
 * per-type property blocks. Everything below drives it through that single public entry point — no
 * production parameter, test tag or widened member was added for these tests.
 *
 * **The feedback loop is the point.** Each control hands the caller a *copy* of the source with one
 * field replaced; [sourcePanel] feeds that copy straight back in, exactly as `CanvasTab` does. That
 * is what makes it possible to assert both halves of what was asked for here — the value the control
 * wrote into the model, and the text the panel then shows for it. A control wired to the wrong
 * `copy(...)` field fails the first; one that writes correctly but reads from somewhere else fails
 * the second.
 *
 * **Locating controls.** None of the panel's fields merge their caption into their own semantics —
 * `StyledTextField`, `DropdownSelector` and `ColorPickerField` each render the label as a separate
 * uppercased `Text` beside the control. So a field is addressed either by the value it is currently
 * displaying ([fieldShowing]) or by its ordinal among the panel's fields ([textFields]), and the
 * ordinals are pinned by a structure test in each class rather than assumed.
 *
 * Known gaps — what these tests do not reach, and why:
 *
 *  * **The two Browse buttons** (image and video) default to `FileChooser.platformInstance`, a real
 *    native dialog — but `SourcePropertiesPanel` takes a `fileChooser` parameter precisely so a test
 *    can swap that out, which [sourcePanel] does via its own `fileChooser` parameter. See
 *    `SourcePropertiesImageTest`/`SourcePropertiesVideoTest` for the Browse-button coverage that
 *    unlocks.
 *  * **Camera and window enumeration** shell out to `ffmpeg`, `system_profiler`, `xprop` and
 *    `osascript`. What they return is the machine's hardware, which no fixture can set, and on macOS
 *    the window listing can raise an accessibility prompt. Those two panels are driven under
 *    [withOsName] instead — see `SourcePropertiesCameraTest` and `SourcePropertiesScreenCaptureTest`
 *    for exactly what that reaches and what it leaves uncovered.
 *
 * Colour fields are driven end to end, through the real `ColorPickerDialog`, using the `recolor`
 * helpers already shared by the settings-tab tests.
 */

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Renders the panel over [initial] and runs [block] with a getter for the source as it now stands.
 *
 * The panel is re-fed its own output, so an interaction is followed through to the text it changes.
 */
@OptIn(ExperimentalTestApi::class)
internal fun sourcePanel(
    initial: SceneSource,
    appSettings: AppSettings? = null,
    fileChooser: FileChooser = FileChooser.platformInstance,
    block: ComposeUiTest.(get: () -> SceneSource) -> Unit,
) = runComposeUiTest {
    var current = initial
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(initial) }
            SourcePropertiesPanel(
                source = state,
                appSettings = appSettings,
                fileChooser = fileChooser,
                onSourceUpdate = { updated -> state = updated; current = updated },
            )
        }
    }
    block { current }
}

/**
 * Like [sourcePanel], but hands [block] a `redraw` that recomposes the panel *without* changing the
 * source it is editing.
 *
 * The panel's caller owns a `Modifier` and an `AppSettings` besides the source, and either can change
 * while the source does not — a resize, a settings edit elsewhere in the dialog. When that happens
 * every control has to hold what it is showing: a half-typed value in a field, an open dropdown, a
 * countdown mid-run. `redraw` reproduces exactly that by handing the panel a fresh modifier and
 * leaving the source alone.
 */
@OptIn(ExperimentalTestApi::class)
internal fun redrawablePanel(
    initial: SceneSource,
    appSettings: AppSettings? = null,
    block: ComposeUiTest.(get: () -> SceneSource, redraw: () -> Unit) -> Unit,
) = runComposeUiTest {
    var current = initial
    var bump: (() -> Unit)? = null
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(initial) }
            var tick by remember { mutableStateOf(0) }
            bump = { tick++ }
            SourcePropertiesPanel(
                source = state,
                // A one-pixel padding that alternates: enough to be a different modifier every time,
                // little enough not to move anything a test measures.
                modifier = Modifier.padding(top = (tick % 2).dp),
                appSettings = appSettings,
                onSourceUpdate = { updated -> state = updated; current = updated },
            )
        }
    }
    block({ current }, { bump?.invoke(); waitForIdle() })
}

/**
 * Runs [block] with `os.name` reporting [name], restoring the real value afterwards whatever happens.
 *
 * The camera and screen-capture panels both branch on `os.name` to decide *how* to enumerate the
 * machine — `/dev/video*`, DirectShow, AVFoundation, `xprop`, `osascript`. Every one of those either
 * spawns a process or, on macOS, can raise an accessibility prompt that blocks the run. Naming an OS
 * the panel has no enumerator for sends it down its own empty-list path with no process spawned at
 * all, which is what makes the surrounding controls — the mode dropdown, the Refresh button, the
 * "nothing found" message — testable at all.
 *
 * Skiko reads `os.name` through a JVM-wide `by lazy` and throws on a name it does not know, so the
 * latch below has to happen before the swap — see [TestSingletons.latchSkikoHostOs] for what breaks
 * without it, and why it breaks only on some machines.
 */
internal fun <T> withOsName(name: String, block: () -> T): T {
    TestSingletons.latchSkikoHostOs()
    val previous = System.getProperty("os.name")
    System.setProperty("os.name", name)
    return try {
        block()
    } finally {
        System.setProperty("os.name", previous)
    }
}

/** An OS no branch of the panel claims to enumerate, so every listing returns empty immediately. */
internal const val OS_WITHOUT_ENUMERATOR = "TestOS"

// ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

/**
 * One source of every kind the panel knows how to render, each with a distinct id.
 *
 * Ids are distinct because `TimerStateManager` keys countdown state by source id in a process-wide
 * singleton — sharing one id across tests would leak a running timer between them.
 */
internal object Fixture {
    fun image(id: String = "img-1", filePath: String = "/tmp/logo.png") =
        SceneSource.ImageSource(id = id, name = "Logo", filePath = filePath)
    fun text(id: String = "txt-1") = SceneSource.TextSource(id = id, name = "Title")
    fun color(id: String = "col-1") = SceneSource.ColorSource(id = id, name = "Backdrop")
    fun video(id: String = "vid-1", filePath: String = "/tmp/bumper.mp4") =
        SceneSource.VideoSource(id = id, name = "Bumper", filePath = filePath)
    fun browser(id: String = "web-1") = SceneSource.BrowserSource(id = id, name = "Feed", url = "https://example.org")
    fun shape(id: String = "shp-1") = SceneSource.ShapeSource(id = id, name = "Box")
    fun clock(id: String = "clk-1") = SceneSource.ClockSource(id = id, name = "Service timer")
    fun qr(id: String = "qr-1") = SceneSource.QRCodeSource(id = id, name = "Scan me")
    fun camera(id: String = "cam-1") = SceneSource.CameraSource(id = id, name = "Cam 1")
    fun capture(id: String = "cap-1") = SceneSource.ScreenCaptureSource(id = id, name = "Stage")
    fun bible(id: String = "bib-1") = SceneSource.BibleSource(id = id, name = "Verse")

    /** Every kind, for the tests that must prove a shared control handles all eleven branches. */
    fun everyKind(suffix: String): List<SceneSource> = listOf(
        image("img-$suffix"), text("txt-$suffix"), color("col-$suffix"), video("vid-$suffix"),
        browser("web-$suffix"), shape("shp-$suffix"), clock("clk-$suffix"), qr("qr-$suffix"),
        camera("cam-$suffix"), capture("cap-$suffix"), bible("bib-$suffix"),
    )
}

// ── Labels, as the panel renders them ───────────────────────────────────────────────────────────

/**
 * The captions the panel puts on screen.
 *
 * `StyledTextField`, `DropdownSelector` and `ColorPickerField` all uppercase their label; section
 * headings and checkbox captions are rendered as written. Both forms are spelled out here so a test
 * never has to remember which control a caption belongs to.
 */
internal object Label {
    // Panel chrome, shared by every source type.
    const val PROPERTIES = "Properties"
    const val NAME = "NAME"
    const val TRANSFORM = "Transform"
    const val X = "X"
    const val Y = "Y"
    const val W = "W"
    const val H = "H"
    const val ROTATION = "Rotation"
    const val OPACITY = "Opacity"

    // Section headings, one per source type.
    const val IMAGE = "Image"
    const val TEXT = "Text"
    const val COLOR = "Color"
    const val VIDEO = "Video"
    const val BROWSER = "Browser"
    const val SHAPE = "Shape"
    const val CLOCK = "Clock"
    const val QRCODE = "QR Code"
    const val CAMERA = "Camera"
    const val SCREEN_CAPTURE = "Screen Capture"
    const val BIBLE = "Bible"
}

// ── Locators ────────────────────────────────────────────────────────────────────────────────────

/** Every editable field on the panel, in composition order. */
internal fun ComposeUiTest.textFields(): SemanticsNodeInteractionCollection = onAllNodes(hasSetTextAction())

/** The field currently displaying [value]. */
internal fun ComposeUiTest.fieldShowing(value: String): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasText(value))

/** Every checkbox on the panel, in composition order. */
internal fun ComposeUiTest.checkboxes(): SemanticsNodeInteractionCollection = onAllNodes(isToggleable())

/**
 * Every Material button on the panel, in composition order.
 *
 * `Role.Button` is what separates a real button from the panel's other clickable things: the font
 * dropdown, the colour fields and the "edit in a larger window" link are all bare `clickable`
 * modifiers, which publish a click action but no role. The alignment buttons carry no text and no
 * content description of their own, so this is the only handle on them.
 */
internal fun ComposeUiTest.roleButtons(): SemanticsNodeInteractionCollection =
    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

/** A labelled button, e.g. `button("Insert Verse")`. */
internal fun ComposeUiTest.button(label: String): SemanticsNodeInteraction =
    onNode(hasClickAction() and hasText(label))

/** How many times [text] is rendered anywhere on the panel. */
internal fun ComposeUiTest.countOf(text: String): Int =
    onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).size

/** Every distinct non-blank string the panel currently renders, editable or not. */
internal fun ComposeUiTest.renderedText(): Set<String> {
    val out = mutableSetOf<String>()
    onAllNodesWithText("", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .forEach { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
            node.config.getOrNull(SemanticsProperties.EditableText)?.let { out += it.text }
        }
    return out.filter { it.isNotBlank() }.toSet()
}

// ── Actions ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Types [to] into the field at [ordinal] and confirms it with the IME's Done action.
 *
 * The numeric fields (`PropertyFloatField`, and the input beside `PropertySliderWithInput`) hold what
 * is typed in local state and only push it to the model on Done or on focus loss, so a bare
 * [performTextReplacement] would assert nothing. Done is the half a test can drive deterministically.
 */
internal fun ComposeUiTest.commitField(ordinal: Int, to: String) {
    textFields()[ordinal].performScrollTo().performTextReplacement(to)
    textFields()[ordinal].performImeAction()
    waitForIdle()
}

/** Types [to] into the field at [ordinal] without confirming it — for the fields that update live. */
internal fun ComposeUiTest.typeField(ordinal: Int, to: String) {
    textFields()[ordinal].performScrollTo().performTextReplacement(to)
    waitForIdle()
}

/** Clicks the checkbox at [ordinal], scrolling it into view first. */
internal fun ComposeUiTest.toggleCheckbox(ordinal: Int) {
    checkboxes()[ordinal].performScrollTo().performClick()
    waitForIdle()
}

/**
 * Opens the dropdown whose closed selector currently reads [showing], leaving its menu up.
 *
 * The selector is the *last* node showing that text: an open menu puts its entries below the closed
 * selector, but nothing is open yet, and every other match would be a caption above it.
 */
internal fun ComposeUiTest.openDropdown(showing: String) {
    onAllNodesWithText(showing).onLast().performScrollTo().performClick()
    waitForIdle()
}

/**
 * Opens the dropdown whose closed selector currently reads [showing] and chooses [option].
 *
 * With the menu open the wanted entry is the *last* match — the closed selector above it still reads
 * as the previous choice, and for a menu that offers the current value the label appears twice.
 */
internal fun ComposeUiTest.chooseFromDropdown(showing: String, option: String) {
    onAllNodesWithText(showing).onLast().performScrollTo().performClick()
    waitForIdle()
    onAllNodesWithText(option).onLast().performClick()
    waitForIdle()
}

/**
 * Taps the `SlimSlider` captioned [caption] at [fraction] of its track width.
 *
 * The track is a bare `Box` with pointer input and no semantics of its own, so it cannot be matched
 * and must be tapped by position. Every position here is derived from the rendered tree, via the one
 * node that is reliably on the track's own row: the read-out or input the slider is paired with.
 * Scrolling *that* into view is what guarantees the track is on screen — scrolling the caption in is
 * not enough, because `performScrollTo` moves the least it can and will happily leave a caption flush
 * with the bottom edge and its slider off-screen beneath it.
 *
 * [gapDp] is the distance from the end of the track to the left edge of that anchor — see [Gap].
 */
internal fun ComposeUiTest.tapSliderUnder(caption: String, fraction: Float, gapDp: Float) =
    tapSliderRow(caption, fraction, gapDp, besideCaption = false)

/**
 * Taps a `SlimSlider` that shares a line with its caption rather than sitting under it.
 *
 * The line-spacing rows are laid out that way, so the track starts after the caption plus the row's
 * [captionGapDp] spacing. Everything else matches [tapSliderUnder].
 */
internal fun ComposeUiTest.tapSliderBeside(
    caption: String,
    fraction: Float,
    gapDp: Float,
    captionGapDp: Float = 8f,
) = tapSliderRow(caption, fraction, gapDp, besideCaption = true, captionGapDp = captionGapDp)

private fun ComposeUiTest.tapSliderRow(
    caption: String,
    fraction: Float,
    gapDp: Float,
    besideCaption: Boolean,
    captionGapDp: Float = 8f,
) {
    waitForIdle()
    onAllNodesWithText(caption).onLast().performScrollTo()
    waitForIdle()

    // The anchor is the nearest node to the right of the caption on the slider's own row: the
    // read-out for a plain slider, the paired input's text for one with a number box.
    //
    // The search runs on unclipped positions, not on `boundsInRoot`. A node scrolled past the bottom
    // of the viewport is clipped to nothing and reports empty bounds, which is exactly the case this
    // has to find — the anchor is off-screen until it is scrolled to, and it cannot be scrolled to
    // until it has been found.
    val captionNode = onAllNodesWithText(caption).onLast().fetchSemanticsNode()
    val density = captionNode.layoutInfo.density.density
    val captionTop = captionNode.positionInRoot.y
    val captionBottom = captionTop + captionNode.size.height
    val captionRight = captionNode.positionInRoot.x + captionNode.size.width
    val band = captionTop - 4f * density..captionBottom + 30f * density
    val anchor = onAllNodesWithText("", substring = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filter { it.positionInRoot.x > captionRight && it.positionInRoot.y + it.size.height / 2f in band }
        .minByOrNull { it.positionInRoot.x }
    checkNotNull(anchor) { "the \"$caption\" slider must have a read-out or input on its row" }

    onNode(SemanticsMatcher("is the \"$caption\" slider's read-out") { it.id == anchor.id }).performScrollTo()
    waitForIdle()

    val label = onAllNodesWithText(caption).onLast().fetchSemanticsNode().boundsInRoot
    val readout = onNode(SemanticsMatcher("is the read-out") { it.id == anchor.id })
        .fetchSemanticsNode().boundsInRoot
    val left = if (besideCaption) label.right + captionGapDp * density else label.left
    val right = readout.left - gapDp * density
    val y = readout.center.y
    assertTrue(right > left, "the slider track must have measurable width (left=$left right=$right)")
    val root = onRoot().fetchSemanticsNode().boundsInRoot
    assertTrue(
        y > root.top && y < root.bottom,
        "the \"$caption\" slider must be scrolled into view before it is tapped " +
            "(y=$y, window is ${root.top}..${root.bottom})",
    )

    onRoot().performTouchInput { click(Offset(left + (right - left) * fraction, y)) }
    waitForIdle()
}

/** The gap between the end of a slider's track and the node the tap helpers anchor on. */
internal object Gap {
    /** `PropertySlider`: `SlimSlider`'s own read-out, 10dp after the track. */
    const val READOUT = 10f

    /**
     * `PropertySliderWithInput`: a `StyledTextField` 4dp after the track, whose own text is inset a
     * further 11dp by the field's padding.
     */
    const val INPUT = 15f
}

// ── Assertions ──────────────────────────────────────────────────────────────────────────────────

/** Asserts some field on the panel is displaying [value]. */
internal fun ComposeUiTest.assertFieldShows(value: String, what: String) {
    val shown = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
    assertEquals(true, value in shown, "$what must display \"$value\" — fields show $shown")
}
