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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.settings.StageMonitorZoneStyle
import kotlin.test.assertEquals

/**
 * Harness and node locators shared by the `StageMonitorSettingsTab` test classes.
 *
 * The tab is two things stacked: a **routing** section, where each of the 15 content types picks the
 * zone it appears in (plus the metronome's anchor), a **preview** of the resulting screen, and then
 * **six identical zone-style editors** — one per drawable zone — each carrying a font family, a font
 * size, a text colour, a background colour, a shadow colour/size/intensity, bold/italic/underline/
 * shadow, and vertical and horizontal alignment.
 *
 * Six copies of the same editor is what makes this tab awkward to address: out of the box the tree
 * holds 18 colour fields showing two distinct hexes between them, 18 number fields showing four
 * distinct values, and six font dropdowns all reading "Arial". Locating is therefore **by value from
 * a fixture** — [zoneStyled] gives the zone under test a value no other control on the tab holds,
 * and the test finds it by that. Where a control has no value to search by (the style and alignment
 * buttons) it is addressed by ordinal through [ZoneOrdinal], whose order
 * `StageMonitorSettingsTabStructureTest` pins.
 *
 * **The trap on this tab is [DropdownSettingsField].** It keeps its own `currentValue` and displays
 * whatever was last clicked, so asserting a routing dropdown's text after picking passes whether or
 * not anything was stored — the same vacuous assertion that slipped into the Song tab's tests. Every
 * routing test here closes the loop through the settings object and, for the round trip, through a
 * fresh render of the saved settings instead.
 *
 * Known gaps — what these tests do not reach, and why:
 *
 *  * **The stepper arrows** on all 18 number fields are asserted clickable but the fields are driven
 *    by typing. What the arrows do with a value — the range clamp at either end — belongs to the
 *    shared `NumberSettingsTextField` and is covered by `NumberSettingsTextFieldTest`.
 *  * **`MetronomeDot`** is not asserted at all. It is a bare `Box` with no semantics — nothing to
 *    locate — and its only visible property is an alpha driven by a `delay` loop, so the one way to
 *    see it is a pixel capture whose result depends on where that loop happens to be. Asserting on
 *    it would be asserting on timing, which `AGENT.md` rules out. What the *settings* say about the
 *    anchor is covered instead, in the routing and preview tests.
 */
@OptIn(ExperimentalTestApi::class)
internal fun stageMonitorTab(
    initial: AppSettings = AppSettings(),
    block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
) = runComposeUiTest {
    var current = initial
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(current) }
            StageMonitorSettingsTab(
                settings = state,
                onSettingsChange = { transform -> state = transform(state); current = state },
            )
        }
    }
    block { current }
}

// ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

/** Settings whose stage-monitor section is [change] applied to the defaults. */
internal fun stageSettings(change: StageMonitorSettings.() -> StageMonitorSettings): AppSettings =
    AppSettings().let { it.copy(stageMonitorSettings = it.stageMonitorSettings.change()) }

/** Settings in which [zone]'s style is [change] applied to that zone's default. */
internal fun zoneStyled(
    zone: StageMonitorStyleZone,
    change: StageMonitorZoneStyle.() -> StageMonitorZoneStyle,
): AppSettings = stageSettings {
    copy(zoneStyles = zoneStyles + (zone to styleFor(zone).change()))
}

/** The stored style of [zone] in [settings]. */
internal fun AppSettings.styleOf(zone: StageMonitorStyleZone): StageMonitorZoneStyle =
    stageMonitorSettings.styleFor(zone)

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

/** Zone names, as they appear in the routing dropdowns and as zone-editor titles. */
internal object ZoneLabel {
    const val TOP_LEFT = "Top-Left"
    const val TOP_RIGHT = "Top-Right"
    const val BOTTOM_LEFT = "Bottom-Left"
    const val BOTTOM_CENTER = "Bottom-Center"
    const val BOTTOM_RIGHT = "Bottom-Right"
    const val FULL_SCREEN = "Full Screen"
    const val NONE = "None"

    /** Every option a routing dropdown offers, in the order [StageMonitorZone] declares them. */
    val all = listOf(TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT, FULL_SCREEN, NONE)

    fun of(zone: StageMonitorZone): String = when (zone) {
        StageMonitorZone.TOP_LEFT -> TOP_LEFT
        StageMonitorZone.TOP_RIGHT -> TOP_RIGHT
        StageMonitorZone.BOTTOM_LEFT -> BOTTOM_LEFT
        StageMonitorZone.BOTTOM_MIDDLE -> BOTTOM_CENTER
        StageMonitorZone.BOTTOM_RIGHT -> BOTTOM_RIGHT
        StageMonitorZone.FULL_SCREEN -> FULL_SCREEN
        StageMonitorZone.NONE -> NONE
    }

    fun of(zone: StageMonitorStyleZone): String = when (zone) {
        StageMonitorStyleZone.TOP_LEFT -> TOP_LEFT
        StageMonitorStyleZone.TOP_RIGHT -> TOP_RIGHT
        StageMonitorStyleZone.BOTTOM_LEFT -> BOTTOM_LEFT
        StageMonitorStyleZone.BOTTOM_MIDDLE -> BOTTOM_CENTER
        StageMonitorStyleZone.BOTTOM_RIGHT -> BOTTOM_RIGHT
        StageMonitorStyleZone.FULL_SCREEN -> FULL_SCREEN
    }
}

/** Metronome anchor names — a 3x3 grid plus None, sharing several strings with [ZoneLabel]. */
internal object MetronomeLabel {
    const val NONE = "None"
    const val TOP_CENTER = "Top-Center"
    const val MIDDLE_LEFT = "Middle-Left"
    const val CENTER = "Center"
    const val MIDDLE_RIGHT = "Middle-Right"
    val all = listOf(
        NONE, ZoneLabel.TOP_LEFT, TOP_CENTER, ZoneLabel.TOP_RIGHT,
        MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
        ZoneLabel.BOTTOM_LEFT, ZoneLabel.BOTTOM_CENTER, ZoneLabel.BOTTOM_RIGHT,
    )

    fun of(position: MetronomePosition): String = when (position) {
        MetronomePosition.NONE -> NONE
        MetronomePosition.TOP_LEFT -> ZoneLabel.TOP_LEFT
        MetronomePosition.TOP_CENTER -> TOP_CENTER
        MetronomePosition.TOP_RIGHT -> ZoneLabel.TOP_RIGHT
        MetronomePosition.MIDDLE_LEFT -> MIDDLE_LEFT
        MetronomePosition.CENTER -> CENTER
        MetronomePosition.MIDDLE_RIGHT -> MIDDLE_RIGHT
        MetronomePosition.BOTTOM_LEFT -> ZoneLabel.BOTTOM_LEFT
        MetronomePosition.BOTTOM_CENTER -> ZoneLabel.BOTTOM_CENTER
        MetronomePosition.BOTTOM_RIGHT -> ZoneLabel.BOTTOM_RIGHT
    }
}

/** The caption each routing dropdown carries — `DropdownSettingsField` renders labels uppercased. */
internal object ContentLabel {
    const val METRONOME = "METRONOME POSITION"

    fun of(type: StageMonitorContentType): String = when (type) {
        StageMonitorContentType.BIBLE -> "BIBLE"
        StageMonitorContentType.SONGS -> "SONGS"
        StageMonitorContentType.PRESENTATION -> "PRESENTATION"
        StageMonitorContentType.PRESENTATION_NOTES -> "PRESENTER NOTES"
        StageMonitorContentType.PICTURES -> "PICTURES"
        StageMonitorContentType.MEDIA -> "MEDIA"
        StageMonitorContentType.LOWER_THIRD -> "LOWER THIRD"
        StageMonitorContentType.WEB -> "WEB"
        StageMonitorContentType.STT -> "STT"
        StageMonitorContentType.CANVAS -> "CANVAS"
        StageMonitorContentType.QA -> "Q&A"
        StageMonitorContentType.DICTIONARY -> "DICTIONARY"
        StageMonitorContentType.CLOCK -> "CLOCK"
        StageMonitorContentType.ANNOUNCEMENT_TEXT -> "ANNOUNCEMENTS"
        StageMonitorContentType.NEXT -> "NEXT"
    }

    /** The name the preview and the layout cells use, which is the label in its display casing. */
    fun previewOf(type: StageMonitorContentType): String = when (type) {
        StageMonitorContentType.BIBLE -> "Bible"
        StageMonitorContentType.SONGS -> "Songs"
        StageMonitorContentType.PRESENTATION -> "Presentation"
        StageMonitorContentType.PRESENTATION_NOTES -> "Presenter Notes"
        StageMonitorContentType.PICTURES -> "Pictures"
        StageMonitorContentType.MEDIA -> "Media"
        StageMonitorContentType.LOWER_THIRD -> "Lower Third"
        StageMonitorContentType.WEB -> "Web"
        StageMonitorContentType.STT -> "STT"
        StageMonitorContentType.CANVAS -> "Canvas"
        StageMonitorContentType.QA -> "Q&A"
        StageMonitorContentType.DICTIONARY -> "Dictionary"
        StageMonitorContentType.CLOCK -> "Clock"
        StageMonitorContentType.ANNOUNCEMENT_TEXT -> "Announcements"
        StageMonitorContentType.NEXT -> "Next"
    }
}

// ── Ordinals ────────────────────────────────────────────────────────────────────────────────────

/** The zones offered a chord colour: the two a song's chart can be routed to. */
internal const val CHORD_COLOUR_ZONES = 2

/**
 * Where each zone's style editor sits among the tab's repeated controls, in composition order.
 *
 * The left column composes first — routing, then Full Screen, Top-Left, Top-Right — and the right
 * column second — preview, then Bottom-Left, Bottom-Center, Bottom-Right. That is the order, not the
 * on-screen one: Bottom-Left's editor sits beside Full Screen's on screen but composes after it.
 */
internal object ZoneOrdinal {
    const val COUNT = 6

    fun of(zone: StageMonitorStyleZone): Int = when (zone) {
        StageMonitorStyleZone.FULL_SCREEN -> 0
        StageMonitorStyleZone.TOP_LEFT -> 1
        StageMonitorStyleZone.TOP_RIGHT -> 2
        StageMonitorStyleZone.BOTTOM_LEFT -> 3
        StageMonitorStyleZone.BOTTOM_MIDDLE -> 4
        StageMonitorStyleZone.BOTTOM_RIGHT -> 5
    }

    /** Composition order, which is the order every ordinal-addressed collection follows. */
    val inOrder = listOf(
        StageMonitorStyleZone.FULL_SCREEN,
        StageMonitorStyleZone.TOP_LEFT,
        StageMonitorStyleZone.TOP_RIGHT,
        StageMonitorStyleZone.BOTTOM_LEFT,
        StageMonitorStyleZone.BOTTOM_MIDDLE,
        StageMonitorStyleZone.BOTTOM_RIGHT,
    )
}

// ── Locators ────────────────────────────────────────────────────────────────────────────────────

/**
 * The routing dropdown captioned [label] — `DropdownSettingsField` merges its caption and its
 * current value into one node, so the caption alone identifies it whatever it is set to.
 */
internal fun ComposeUiTest.routingDropdown(label: String): SemanticsNodeInteraction =
    onNode(hasClickAction() and hasText(label))

/**
 * Every vertical-alignment button, in composition order: three per zone, addressed as
 * `zone * 3 + VAlign.TOP`. These publish a content description; their horizontal counterparts do not
 * — see [horizontalAlignButtonsHere].
 */
internal fun ComposeUiTest.verticalAlignButton(zone: Int, which: Int): SemanticsNodeInteraction =
    onAllNodesWithContentDescription(VAlign.descriptions[which])[zone]

internal object VAlign {
    const val TOP = 0
    const val MIDDLE = 1
    const val BOTTOM = 2
    val descriptions = listOf("Align Top", "Align Middle", "Align Bottom")
}

/**
 * The B/I/U/S and horizontal-alignment buttons are the Song tab's [styleButton] and
 * [horizontalAlignButton], reused: both tabs build them from the same shared composables, so the
 * ordinals and the right-first layout of the horizontal row hold here too. `zone * 3 + HAlign.RIGHT`
 * addresses one alignment button; `styleButton(zone, "B")` one style button.
 */

// ── Actions ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Opens the routing dropdown captioned [label] and picks [option].
 *
 * The open menu's item carries only the option text, while the field behind it always also carries
 * its own caption — which is what tells them apart, since several fields may be showing [option]
 * already.
 */
internal fun ComposeUiTest.chooseRouting(label: String, option: String) {
    routingDropdown(label).performScrollTo().performClick()
    waitForIdle()
    onNode(hasTextExactly(option) and hasClickAction()).performClick()
    waitForIdle()
}

/** Asserts the routing dropdown captioned [label] is displaying [option]. */
internal fun ComposeUiTest.assertRoutingShows(label: String, option: String) {
    assertEquals(
        listOf(label, option),
        routingDropdown(label).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)?.map { it.text },
        "the \"$label\" dropdown must display $option",
    )
}
