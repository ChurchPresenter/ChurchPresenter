@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.DictionarySettings
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Harness and node locators shared by the `DictionarySettingsTab` test classes.
 *
 * The tab styles the Strong's dictionary card: six sections across two columns — Word, Definition
 * and Card Background on the left; Reference, KJV Usage and Transitions on the right. It is built
 * entirely from shared controls (`SettingRow`, `ColorPickerField`, `TextStyleButtons`,
 * `ShadowDetailRow`, `FontSettingsDropdown`, `NumberSettingsTextField`, `SlimSlider`), publishes no
 * `testTag`, and nothing here is refactored for testability.
 *
 * Locating is **by displayed value** wherever a control has one: seven colour fields, eight number
 * fields and two font dropdowns all look alike in the tree, so a test gives the one it drives a
 * value no other control on the tab holds and finds it by that. The colour, number and font helpers
 * are the ones already written for the Song and Background tabs — [colorFields], [recolor],
 * [retypeNumberField], [pickFont] — reused rather than duplicated. Only the switches and the style
 * buttons are addressed by ordinal, and `DictionarySettingsTabStructureTest` pins those orders.
 *
 * Known gaps — what these tests do not reach, and why:
 *
 *  * **The stepper arrows** on every `NumberSettingsTextField` are asserted clickable but the fields
 *    are driven by typing. What the arrows do with a value — the range clamp at either end — belongs
 *    to the shared control and is covered by `NumberSettingsTextFieldTest`.
 *  * **`GraphicsEnvironment.availableFontFamilyNames`** is called at composition. Unlike
 *    `screenDevices` it works headless, so it needs no seam — but it means the font dropdowns offer
 *    whatever the running machine has installed, which is why [uniquelyNamedFont] is used rather
 *    than any hard-coded family.
 */
@OptIn(ExperimentalTestApi::class)
internal fun dictionaryTab(
    initial: AppSettings = AppSettings(),
    block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
) = runComposeUiTest {
    var current = initial
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(current) }
            DictionarySettingsTab(
                settings = state,
                onSettingsChange = { transform -> state = transform(state); current = state },
            )
        }
    }
    block { current }
}

/** Settings whose dictionary section is [change] applied to the defaults. */
internal fun dictionarySettings(change: DictionarySettings.() -> DictionarySettings): AppSettings =
    AppSettings().let { it.copy(dictionarySettings = it.dictionarySettings.change()) }

// ── Ordinals ────────────────────────────────────────────────────────────────────────────────────

/**
 * Where each switch sits in composition order. The left column composes first, so the four "Show"
 * switches come before the two transition switches even though Fade In sits higher on screen than
 * nothing in particular — the order is the composition's, not the layout's.
 */
internal object Switches {
    const val SHOW_WORD = 0
    const val SHOW_DEFINITION = 1
    const val SHOW_REFERENCE = 2
    const val SHOW_KJV = 3
    const val FADE_IN = 4
    const val FADE_OUT = 5
    const val COUNT = 6
}

/** The two `TextStyleButtons` groups, in composition order. */
internal object DictStyleGroup {
    const val WORD = 0
    const val REFERENCE = 1
    const val COUNT = 2
}

/** Section headings, in composition order. */
internal object Section {
    const val WORD = "Word (Original)"
    const val DEFINITION = "Definition"
    const val CARD_BACKGROUND = "Card Background"
    const val REFERENCE = "Reference & Transliteration"
    const val KJV_USAGE = "KJV Usage"
    const val TRANSITIONS = "Transition Settings"
    val all = listOf(WORD, DEFINITION, CARD_BACKGROUND, REFERENCE, KJV_USAGE, TRANSITIONS)
}

// ── Locators ────────────────────────────────────────────────────────────────────────────────────

/** Every `Switch` on the tab, in composition order — address them through [Switches]. */
internal fun ComposeUiTest.switches(): SemanticsNodeInteractionCollection =
    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))

internal fun ComposeUiTest.switch(ordinal: Int): SemanticsNodeInteraction = switches()[ordinal]


// ── Sliders ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Bounds of the row captioned [caption] and of the readout belonging to it.
 *
 * `SlimSlider` draws its track on a bare `Canvas` and publishes no semantics, so there is no node to
 * address. Unlike the Background tab's sliders these sit *beside* their caption rather than under
 * it: `SettingRow` lays the label out on the left and the slider row fills the rest, with the
 * trailing readout at its right-hand end. The readout that belongs to a caption is therefore the
 * first one on the same horizontal band — vertically overlapping it — and to its right.
 */
private fun ComposeUiTest.sliderGeometry(caption: String, readoutSuffix: String): Pair<Rect, Rect> {
    val captionBounds = onAllNodesWithText(caption).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
        .singleOrNull()
        ?: error("expected exactly one row captioned \"$caption\"")
    val readout = onAllNodesWithText(readoutSuffix, substring = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
        .filter { it.left >= captionBounds.right && it.top < captionBounds.bottom && it.bottom > captionBounds.top }
        .minByOrNull { it.left }
        ?: error("no \"$readoutSuffix\" readout found on the same row as \"$caption\"")
    return captionBounds to readout
}

/**
 * The slider track captioned [caption], as `left to right` in root coordinates.
 *
 * There is no node for it, so it is derived from the two layouts either side. `SettingRow` gives its
 * label a fixed 120.dp and follows it with `Arrangement.spacedBy(8.dp)`, so the slider's hit area
 * starts 8px past the caption. `SlimSlider` is itself a `Row(spacedBy(10.dp))` of a weighted `Box` —
 * the part that takes the pointer input — and the trailing readout, so it ends 10px before that.
 */
private fun ComposeUiTest.sliderTrack(caption: String, readoutSuffix: String): Triple<Float, Float, Float> {
    val (captionBounds, readoutBounds) = sliderGeometry(caption, readoutSuffix)
    return Triple(captionBounds.right + 8f, readoutBounds.left - 10f, readoutBounds.center.y)
}

/**
 * Taps the slider captioned [caption] at [fraction] along its track.
 *
 * Only for the interior of the range: a tap has to land inside the hit area, and the hit area's
 * right edge is exclusive, so `fraction = 1f` cannot be reached this way. Use [dragSliderToEnd] for
 * the two extremes.
 */
internal fun ComposeUiTest.clickSlider(caption: String, readoutSuffix: String, fraction: Float) {
    onAllNodesWithText(caption)[0].performScrollTo()
    waitForIdle()
    val (trackStart, trackEnd, y) = sliderTrack(caption, readoutSuffix)
    onRoot().performMouseInput { click(Offset(trackStart + (trackEnd - trackStart) * fraction, y)) }
    waitForIdle()
}

/**
 * Drags the slider captioned [caption] clear off one end of its track.
 *
 * This is how an end of the range is reached exactly. `SlimSlider` coerces the dragged fraction into
 * 0..1, and a drag keeps receiving positions after the pointer leaves the hit area — so releasing
 * well outside lands precisely on the bound, which a tap cannot do. It is also what an operator
 * actually does when they want "none" or "all".
 */
internal fun ComposeUiTest.dragSliderToEnd(caption: String, readoutSuffix: String, toRight: Boolean) {
    onAllNodesWithText(caption)[0].performScrollTo()
    waitForIdle()
    val (trackStart, trackEnd, y) = sliderTrack(caption, readoutSuffix)
    val from = (trackStart + trackEnd) / 2f
    val to = if (toRight) trackEnd + 60f else trackStart - 60f
    onRoot().performMouseInput {
        moveTo(Offset(from, y))
        press()
        moveTo(Offset(to, y))
        release()
    }
    waitForIdle()
}

/** The text the readout beside [caption] currently shows, e.g. `"92%"` or `"500 ms"`. */
internal fun ComposeUiTest.readoutBeside(caption: String, readoutSuffix: String): String {
    val (_, readoutBounds) = sliderGeometry(caption, readoutSuffix)
    val node = onAllNodesWithText(readoutSuffix, substring = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .first { it.boundsInRoot == readoutBounds }
    return node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        ?: error("\"$caption\" published no readout text")
}

internal fun assertBetweenFloat(what: String, value: Float, min: Float, max: Float) {
    assertTrue(value in min..max, "$what must land inside $min..$max, was $value")
}

/** Asserts the opacity slider's readout agrees with [stored]. */
internal fun ComposeUiTest.assertOpacityReads(stored: Float) {
    assertEquals(
        "${(stored * 100).toInt()}%",
        readoutBeside("Opacity", "%"),
        "the opacity readout must follow the stored value",
    )
}

/** Asserts the transition-duration readout agrees with [stored]. */
internal fun ComposeUiTest.assertDurationReads(stored: Float) {
    assertEquals(
        "${stored.toInt()} ms",
        readoutBeside("Transition Duration", "ms"),
        "the duration readout must follow the stored value",
    )
}

