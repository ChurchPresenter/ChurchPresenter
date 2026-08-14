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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Harness and node locators shared by the `BackgroundSettingsTab` test classes.
 *
 * The tab is six copies of the same widget set — a background-type dropdown whose choice decides
 * what appears beneath it (a colour field, an image picker, a video picker, gradient controls, or
 * nothing) — arranged as two "default" cards on top and Bible/Songs full-screen and lower-third
 * columns below. Nothing is refactored for testability and the tab carries exactly one `testTag`,
 * so the locators here work from what the tree already publishes: the dropdown's current label, the
 * `#RRGGBB` a colour field displays, and the content descriptions on the picker icon buttons.
 *
 * Locating is **by value** wherever possible — the fixture gives the control under test a value no
 * other control on the tab holds, then finds it by that value. The type dropdowns are the exception
 * and are addressed by ordinal, named through [TypeDropdown]; `BackgroundSettingsTabStructureTest`
 * pins their count so a reordering fails there first.
 *
 * Known gaps — everything in `BackgroundSettingsTab.kt` these tests do not reach, and why. All of
 * them are blocked by what the code does when clicked, not by the tests being thin:
 *
 *  * **The file field** (`FileImagePicker`/`FileVideoPicker`) opens a **native** file chooser, which
 *    would block the run. Its displayed filename is asserted instead. The `onImagePathChange` /
 *    `onVideoPathChange` callbacks are only ever invoked by it or by the dialogs below, so those go
 *    uncovered with it.
 *  * **The two browse buttons** open `DialogWindow`s — real AWT windows — which throw
 *    `HeadlessException` under the suite's headless JVM. (The colour picker's dialog *is* driven,
 *    because it uses the in-composition `Dialog` instead.) The Pexels/Pixabay API-key callbacks
 *    hang off those dialogs and are unreachable for the same reason.
 *  * **The ATEM upload buttons** open a TCP connection to the configured switcher, so
 *    `uploadBackgroundToAtem` and the button's busy/error states are not driven. Its one piece of
 *    pure logic, `coverCropArgb`, is tested directly in `CoverCropArgbTest`. Their visibility rules
 *    are covered in `BackgroundSettingsTabPickerRowTest`.
 *  * **Tooltip contents.** Every icon button's tooltip composes only after `TooltipArea`'s own
 *    500 ms hover delay. That delay is the test's whole cost and is not injectable, which
 *    `AGENT.md` says to note rather than test. The tooltips' text is still asserted — it doubles as
 *    each button's content description, which is how the buttons are located here.
 *  * **The "(Install VLC)" labelling** of the Video option only renders where VLC is absent, and
 *    that item is disabled there — so a machine without VLC (CI) cannot *pick* Video at all. Tests
 *    address the item through [videoMenuLabel] and take video-dependent rows from a fixture rather
 *    than from a click, which keeps them true on both kinds of machine; only the enabled/disabled
 *    branch itself differs, and just the one matching the running machine is exercised.
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

/** Ordinal of each background-type dropdown, in composition order. */
internal object TypeDropdown {
    const val DEFAULT = 0
    const val DEFAULT_LOWER_THIRD = 1
    const val BIBLE_FULLSCREEN = 2
    const val BIBLE_LOWER_THIRD = 3
    const val SONG_FULLSCREEN = 4
    const val SONG_LOWER_THIRD = 5
    const val COUNT = 6
}

/** Every label a background-type dropdown can display. */
internal object TypeLabel {
    const val FOLLOW_DEFAULT = "Follow Default"
    const val DEFAULT = "Default"
    const val COLOR = "Color"
    const val IMAGE = "Image"
    const val VIDEO = "Video Loop"
    const val TRANSPARENT = "Transparent"
    const val GRADIENT = "Gradient"
    val all = listOf(FOLLOW_DEFAULT, DEFAULT, COLOR, IMAGE, VIDEO, TRANSPARENT, GRADIENT)
}

/**
 * The text the Video option carries **inside an open menu**.
 *
 * Where VLC is missing — a CI runner, typically — the tab appends "(Install VLC)" to that one menu
 * item and disables it, so neither its label nor its clickability can be assumed. A dropdown that is
 * *closed* on Video still shows the plain [TypeLabel.VIDEO] either way; this is only for menu items.
 */
internal val videoMenuLabel: String
    get() = if (isVlcAvailable) TypeLabel.VIDEO else "${TypeLabel.VIDEO} (Install VLC)"

// ── Locators ────────────────────────────────────────────────────────────────────────────────────

/**
 * Every background-type dropdown. Each publishes exactly its current label, which distinguishes it
 * from the colour fields (label plus `#RRGGBB`) and the file pickers (a filename).
 */
internal fun ComposeUiTest.typeDropdowns(): SemanticsNodeInteractionCollection =
    onAllNodes(hasClickAction() and TypeLabel.all.map { hasTextExactly(it) }.reduce { a, b -> a or b })

/** The trailing "NN%" readouts, one per slider. */
internal fun ComposeUiTest.percentReadouts(): SemanticsNodeInteractionCollection =
    onAllNodes(hasText("%", substring = true) and !hasClickAction())

// ── Actions ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Opens the [ordinal]-th type dropdown and picks [label] from its menu.
 *
 * The open menu's item and any closed dropdown already showing [label] are indistinguishable — both
 * are clickable nodes whose only text is that label — so the fixture must be arranged such that no
 * other dropdown is currently on [label]. That is asserted here rather than left to chance.
 */
internal fun ComposeUiTest.chooseBackgroundType(ordinal: Int, label: String) {
    val alreadyShowing = onAllNodes(hasClickAction() and hasTextExactly(label))
        .fetchSemanticsNodes(atLeastOneRootRequired = false).size
    assertEquals(
        0,
        alreadyShowing,
        "fixture error: \"$label\" is already displayed by $alreadyShowing dropdown(s), so the menu " +
            "item cannot be told apart from them — start the other dropdowns on a different type",
    )
    typeDropdowns()[ordinal].performScrollTo().performClick()
    waitForIdle()
    onNode(hasClickAction() and hasTextExactly(label)).performClick()
    waitForIdle()
    typeDropdowns()[ordinal].assertTextEquals(label)
}

/**
 * Bounds of the [ordinal]-th slider captioned [caption], and of the readout that belongs to it.
 *
 * `SlimSlider` draws its track on a bare `Canvas` and publishes no semantics for it, so there is no
 * node to address; the caption above it and the readout to its right are all there is to go on.
 *
 * Picking the right readout takes both axes. "Background Opacity" captions one slider per background
 * slot and the slots sit in two rows of columns, so a readout belonging to a *neighbouring* column
 * can be nearer in `top` than the caption's own. The readout that belongs to a caption is the first
 * one that starts **below the caption's baseline** and **no further left than the caption** — the
 * slider row sits directly underneath, and the readout is at its right-hand end.
 */
private fun ComposeUiTest.sliderGeometry(caption: String, ordinal: Int): Pair<Rect, Rect> {
    // Traversal order, deliberately not sorted by position: a card whose type adds a picker row
    // grows taller, so its slider can sit *below* the one in the card beside it while still coming
    // first in composition. Ordinals here line up with TypeDropdown either way.
    val captions = onAllNodesWithText(caption).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
    check(ordinal in captions.indices) {
        "wanted slider #$ordinal captioned \"$caption\" but only ${captions.size} exist"
    }
    val captionBounds = captions[ordinal]
    val readout = percentReadouts().fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
        .filter { it.top >= captionBounds.bottom && it.left >= captionBounds.left }
        .minWithOrNull(compareBy({ it.top }, { it.left }))
        ?: error("no percentage readout found under \"$caption\" #$ordinal")
    return captionBounds to readout
}

/**
 * Clicks the [ordinal]-th slider captioned [caption] at [fraction] along its track and returns the
 * percentage its readout then shows.
 */
internal fun ComposeUiTest.dragSlider(caption: String, fraction: Float, ordinal: Int = 0): Int {
    onAllNodesWithText(caption)[ordinal].performScrollTo()
    waitForIdle()
    val (captionBounds, readoutBounds) = sliderGeometry(caption, ordinal)
    val trackStart = captionBounds.left
    val trackEnd = readoutBounds.left - 10f // Arrangement.spacedBy(10.dp), density 1 under test
    onRoot().performMouseInput {
        click(Offset(trackStart + (trackEnd - trackStart) * fraction, readoutBounds.center.y))
    }
    waitForIdle()
    return readingOf(caption, ordinal)
}

/** The percentage shown by the readout belonging to the [ordinal]-th slider captioned [caption]. */
internal fun ComposeUiTest.readingOf(caption: String, ordinal: Int = 0): Int {
    val (_, readoutBounds) = sliderGeometry(caption, ordinal)
    val readout = percentReadouts().fetchSemanticsNodes(atLeastOneRootRequired = false)
        .first { it.boundsInRoot == readoutBounds }
    val shown = readout.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        ?: error("\"$caption\" #$ordinal published no readout text")
    return shown.removeSuffix("%").toIntOrNull() ?: error("could not read a percentage out of \"$shown\"")
}

/** Asserts a slider's stored value and its on-screen readout agree. */
internal fun ComposeUiTest.assertSliderShows(caption: String, stored: Float, what: String, ordinal: Int = 0) {
    assertEquals((stored * 100).toInt(), readingOf(caption, ordinal), "$what must read back the stored value")
}

internal fun assertBetween(what: String, value: Float, min: Float, max: Float) {
    assertTrue(value in min..max, "$what must land inside $min..$max, was $value")
}

internal fun SemanticsNodeInteraction.scrollThenClick(): SemanticsNodeInteraction =
    performScrollTo().performClick()
