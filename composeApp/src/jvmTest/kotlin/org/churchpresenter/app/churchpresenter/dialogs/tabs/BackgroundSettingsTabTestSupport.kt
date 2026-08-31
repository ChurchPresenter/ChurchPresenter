@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.settings.AppSettings
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Harness and node locators shared by the `BackgroundSettingsTab` test classes.
 *
 * The tab is a **rail of six surfaces beside one editor**: the rail lists Default and Default Lower
 * Third, then a full-screen and a lower-third row under each of Bible and Songs, and the editor to
 * its right belongs to whichever row is selected. Only one surface's controls exist at a time, so a
 * test that wants to write to a surface selects it first — [openSurface] — and everything after
 * that addresses a single set of controls rather than one of six copies.
 *
 * Locating leans on **which column a node sits in**, because the rail and the editor deliberately
 * share words: "Full Screen" names two rail rows, "Default" names both a rail row and a type
 * segment, and "Color" is both a type segment and the caption above the colour field. [inRail] and
 * [inControls] cut a set of same-named nodes down by x-position, using the two column widths the
 * tab lays out with. Everything else is found by a value no other control holds.
 *
 * Known gaps — what these tests do not reach, and why. All are blocked by what the code does when
 * clicked, not by the tests being thin:
 *
 *  * **The file field** (`FileImagePicker`/`FileVideoPicker`) opens a **native** file chooser, which
 *    would block the run. Its displayed filename is asserted instead, so `onImagePathChange` /
 *    `onVideoPathChange` go uncovered with it.
 *  * **The two browse buttons** open `DialogWindow`s — real AWT windows — which throw
 *    `HeadlessException` under the suite's headless JVM. The Pexels/Pixabay API-key callbacks hang
 *    off those dialogs and are unreachable for the same reason. (The colour picker's dialog *is*
 *    driven: it uses the in-composition `Dialog`.)
 *  * **The ATEM upload buttons** open a TCP connection to the configured switcher, so
 *    `uploadBackgroundToAtem` and the button's busy/error states are not driven. Its one piece of
 *    pure logic, `coverCropArgb`, is tested in `CoverCropArgbTest`; the buttons' visibility rules
 *    are covered in `BackgroundSettingsTabPickerRowTest`.
 *  * **The stage preview's picture.** `BackgroundConfigFill` decodes off the main dispatcher and
 *    draws to a `Canvas`; there is no node to assert on. The preview's *arrangement* — which badge
 *    it shows and where the sample line sits — is covered in `BackgroundSettingsTabStructureTest`.
 *  * **Tooltip contents.** Every tooltip composes only after `TooltipArea`'s own 500 ms hover
 *    delay, which is the test's whole cost and is not injectable — `AGENT.md` says to note that
 *    rather than test it.
 *  * **The "(Install VLC)" branch.** The Video segment is disabled where VLC is absent, so a
 *    machine without it cannot pick Video at all. Video-dependent state is taken from a fixture
 *    rather than a click, which keeps the tests true on both kinds of machine.
 */
@OptIn(ExperimentalTestApi::class)
internal fun backgroundTab(
    initial: AppSettings = AppSettings(),
    block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
) = runComposeUiTest {
    var current = initial
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(current) }
            BackgroundSettingsTab(
                settings = state,
                onSettingsChange = { transform -> state = transform(state); current = state },
            )
        }
    }
    block { current }
}

/**
 * The six rows of the rail, in the order it lists them.
 *
 * [row] is the row's own name and [nth] which of the rows carrying that name this is — the two
 * content groups both call their rows "Full Screen" and "Lower Third". [title] is what the editor
 * header shows once the row is open, which is how a test proves the right surface was opened.
 */
internal enum class Surface(val row: String, val nth: Int, val title: String) {
    DEFAULT("Default", 0, "Default"),
    DEFAULT_LOWER_THIRD("Default Lower Third", 0, "Default Lower Third"),
    BIBLE("Full Screen", 0, "Bible · Full Screen"),
    BIBLE_LOWER_THIRD("Lower Third", 0, "Bible · Lower Third"),
    SONG("Full Screen", 1, "Songs · Full Screen"),
    SONG_LOWER_THIRD("Lower Third", 1, "Songs · Lower Third"),
}

/** Every label a background-type segment can carry. */
internal object TypeLabel {
    const val FOLLOW_DEFAULT = "Follow Default"
    const val DEFAULT = "Default"
    const val COLOR = "Color"
    const val IMAGE = "Image"
    const val VIDEO = "Video Loop"
    const val CAMERA = "Camera"
    const val TRANSPARENT = "Transparent"
    const val GRADIENT = "Gradient"
}

/** Captions of the three sliders the editor offers, as `PanelCaption` renders them. */
internal object SliderCaption {
    const val OPACITY = "OPACITY"
    const val DIM = "DIM"
    const val BLUR = "BLUR"
}

/**
 * Where the tab's three columns sit, in px — the test density is 1.
 *
 * [EDITOR_HEADER_BOTTOM] matters as much as the two x edges: the header spans the whole editor, so
 * the surface's title shares the controls column's x range and would otherwise be counted as one of
 * its controls — which is how "Default" managed to be both a rail row and a type segment at once.
 */
private const val RAIL_RIGHT_EDGE = 232f
private const val CONTROLS_RIGHT_EDGE = RAIL_RIGHT_EDGE + 336f
private const val EDITOR_HEADER_BOTTOM = 47f

// ── Locators ────────────────────────────────────────────────────────────────────────────────────

/** Indices of [nodes] whose left edge puts them in the surface rail. */
private fun railIndices(bounds: List<Rect>): List<Int> =
    bounds.indices.filter { bounds[it].left < RAIL_RIGHT_EDGE }

/** Indices of [nodes] whose left edge puts them in the editor's controls column. */
private fun controlIndices(bounds: List<Rect>): List<Int> =
    bounds.indices.filter {
        bounds[it].left in RAIL_RIGHT_EDGE..CONTROLS_RIGHT_EDGE && bounds[it].top >= EDITOR_HEADER_BOTTOM
    }

/**
 * Bounds of every node carrying [text] as one of its own text values.
 *
 * `hasText` and not `hasTextExactly`: a rail row merges its name with the meta line underneath it,
 * so the row that *is* "Default" publishes two strings and an exact match finds nothing.
 */
private fun ComposeUiTest.nodesReading(text: String): List<Rect> =
    onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }

/** The [nth] node reading exactly [text] that sits in the surface rail. */
internal fun ComposeUiTest.inRail(text: String, nth: Int = 0): SemanticsNodeInteraction {
    val indices = railIndices(nodesReading(text))
    check(nth in indices.indices) { "wanted rail node #$nth reading \"$text\" but ${indices.size} exist" }
    return onAllNodes(hasText(text))[indices[nth]]
}

/** The [nth] node reading exactly [text] that sits in the editor's controls column. */
internal fun ComposeUiTest.inControls(text: String, nth: Int = 0): SemanticsNodeInteraction {
    val indices = controlIndices(nodesReading(text))
    check(nth in indices.indices) { "wanted control #$nth reading \"$text\" but ${indices.size} exist" }
    return onAllNodes(hasText(text))[indices[nth]]
}

/** How many nodes read exactly [text] anywhere in the editor's controls column. */
internal fun ComposeUiTest.controlsCount(text: String): Int = controlIndices(nodesReading(text)).size

/** How many rail rows carry [text]. */
internal fun ComposeUiTest.railCount(text: String): Int = railIndices(nodesReading(text)).size

/** How many nodes carrying [text] sit in the editor's header band. */
internal fun ComposeUiTest.headerCount(text: String): Int =
    nodesReading(text).count { it.left >= RAIL_RIGHT_EDGE && it.top < EDITOR_HEADER_BOTTOM }

/** The trailing readouts of the three sliders — "80%", "45%", "6px". */
internal fun ComposeUiTest.sliderReadouts(): SemanticsNodeInteractionCollection =
    onAllNodes((hasText("%", substring = true) or hasText("px", substring = true)) and !hasClickAction())

/**
 * The text the Video segment carries where VLC is missing.
 *
 * The segment is always present; without VLC it is disabled and a tooltip says why. The label
 * itself does not change, unlike the old dropdown's menu item, so this is only about clickability.
 */
internal val videoSegmentEnabled: Boolean get() = isVlcAvailable

// ── Actions ─────────────────────────────────────────────────────────────────────────────────────

/** Selects [surface] in the rail and waits for its editor, proving it by the header's title. */
internal fun ComposeUiTest.openSurface(surface: Surface) {
    inRail(surface.row, surface.nth).performScrollTo().performClick()
    waitForIdle()
    onAllNodesWithText(surface.title).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .ifEmpty { error("opening ${surface.name} did not put \"${surface.title}\" in the editor header") }
}

/** Clicks the type segment labelled [label] in the open editor. */
internal fun ComposeUiTest.chooseBackgroundType(label: String) {
    inControls(label).performScrollTo().performClick()
    waitForIdle()
}

/** Opens [surface] and puts it on [label] in one step, which is what most fixtures want. */
internal fun ComposeUiTest.setSurfaceType(surface: Surface, label: String) {
    openSurface(surface)
    chooseBackgroundType(label)
}

/**
 * Bounds of the slider captioned [caption], and of the readout that belongs to it.
 *
 * `SlimSlider` draws its track on a bare `Canvas` and publishes no semantics, so there is no node
 * to address; the caption above it and the readout to its right are all there is. Only one surface
 * is open at a time now, so each caption is unique and the readout that belongs to it is simply the
 * nearest one at or below the caption's own top edge.
 */
private fun ComposeUiTest.sliderGeometry(caption: String): Pair<Rect, Rect> {
    val captionBounds = nodesReading(caption).firstOrNull()
        ?: error("no slider captioned \"$caption\" is on screen")
    val readout = sliderReadouts().fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
        .filter { it.top >= captionBounds.top - 1f && it.left >= captionBounds.left }
        .minWithOrNull(compareBy({ it.top }, { it.left }))
        ?: error("no readout found beside \"$caption\"")
    return captionBounds to readout
}

/** How far under its caption a slider's track is drawn. */
private const val SLIDER_TRACK_DROP = 15f

/**
 * Clicks the slider captioned [caption] at [fraction] along its track and returns what its readout
 * then shows, as a bare number — "45%" and "6px" both come back as their digits.
 */
internal fun ComposeUiTest.dragSlider(caption: String, fraction: Float): Int {
    inControls(caption).performScrollTo()
    waitForIdle()
    val (captionBounds, readoutBounds) = sliderGeometry(caption)
    // The caption carries `weight(1f)`, so it starts where the track starts; the readout beside it
    // ends where the track ends. The track itself is the row underneath the two of them.
    val trackStart = captionBounds.left
    val trackEnd = readoutBounds.right
    onRoot().performMouseInput {
        click(Offset(trackStart + (trackEnd - trackStart) * fraction, captionBounds.bottom + SLIDER_TRACK_DROP))
    }
    waitForIdle()
    return readingOf(caption)
}

/** The number shown by the readout belonging to the slider captioned [caption]. */
internal fun ComposeUiTest.readingOf(caption: String): Int {
    val (_, readoutBounds) = sliderGeometry(caption)
    val readout = sliderReadouts().fetchSemanticsNodes(atLeastOneRootRequired = false)
        .first { it.boundsInRoot == readoutBounds }
    val shown = readout.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        ?: error("\"$caption\" published no readout text")
    return shown.removeSuffix("%").removeSuffix("px").toIntOrNull()
        ?: error("could not read a number out of \"$shown\"")
}

/** Asserts a slider's stored value and its on-screen readout agree. */
internal fun ComposeUiTest.assertSliderShows(caption: String, stored: Int, what: String) {
    assertEquals(stored, readingOf(caption), "$what must read back the stored value")
}

internal fun assertBetween(what: String, value: Int, min: Int, max: Int) {
    assertTrue(value in min..max, "$what must land inside $min..$max, was $value")
}

internal fun SemanticsNodeInteraction.scrollThenClick(): SemanticsNodeInteraction =
    performScrollTo().performClick()
