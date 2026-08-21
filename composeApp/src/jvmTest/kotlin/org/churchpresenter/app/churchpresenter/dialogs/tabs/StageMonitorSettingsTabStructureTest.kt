@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorStyleZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the shape of the tab and validates the ordinals the behaviour tests use.
 *
 * Six identical zone editors and sixteen identical dropdowns mean almost nothing on this tab can be
 * told apart by type — only by value or by position. This class is what makes positional addressing
 * honest: it asserts how many of each control exist and that each ordinal really belongs to the zone
 * it is named for, so a control added, removed or reordered fails here first and says so.
 */
class StageMonitorSettingsTabStructureTest {

    // ── Sections ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the tab renders its two headed sections and all six zone editors`() = stageMonitorTab { _ ->
        onNodeWithText("Screen Content").assertExists("the routing section must be headed")
        onNodeWithText("Screen Layout").assertExists("as must the preview")

        // Each zone editor is titled with its zone name. The titles are matched as *non-clickable*
        // text: a routing dropdown merges its caption and its current value into one node, so
        // "Top-Left" also matches the Bible and Songs dropdowns that are currently set to it.
        for (zone in StageMonitorStyleZone.entries) {
            val expected = if (zone == StageMonitorStyleZone.FULL_SCREEN) 2 else 1 // + the preview row
            onAllNodes(hasText(ZoneLabel.of(zone)) and !hasClickAction())
                .assertCountEquals(expected)
        }
    }

    // ── Routing dropdowns ───────────────────────────────────────────────────────────────────────

    @Test
    fun `every content type gets its own routing dropdown`() = stageMonitorTab { _ ->
        for (type in StageMonitorContentType.entries) {
            onAllNodesWithText(ContentLabel.of(type))
                .assertCountEquals(1) // the caption is merged into the dropdown node
        }
        assertEquals(15, StageMonitorContentType.entries.size, "a new content type needs a new label here")
        onAllNodesWithText(ContentLabel.METRONOME).assertCountEquals(1)
    }

    @Test
    fun `each routing dropdown starts on the default zone for its content type`() = stageMonitorTab { get ->
        for (type in StageMonitorContentType.entries) {
            assertRoutingShows(ContentLabel.of(type), ZoneLabel.of(get().stageMonitorSettings.zoneFor(type)))
        }
        assertRoutingShows(ContentLabel.METRONOME, MetronomeLabel.NONE)
    }

    // ── The repeated per-zone controls ──────────────────────────────────────────────────────────

    @Test
    fun `each zone editor offers one font family and one font size`() = stageMonitorTab { _ ->
        fontFields().assertCountEquals(ZoneOrdinal.COUNT)
        onAllNodesWithText("FONT TYPE").assertCountEquals(ZoneOrdinal.COUNT)
        onAllNodesWithText("FONT SIZE").assertCountEquals(ZoneOrdinal.COUNT)
    }

    /** Three per zone: the text colour, the background colour and the shadow colour. */
    @Test
    fun `each zone editor offers three colour fields`() = stageMonitorTab { _ ->
        // Plus a chord colour in the two zones a song's chart can land in.
        colorFields().assertCountEquals(ZoneOrdinal.COUNT * 3 + CHORD_COLOUR_ZONES)
        onAllNodesWithText("BACKGROUND COLOR").assertCountEquals(ZoneOrdinal.COUNT)
    }

    /** Three per zone too: the font size and the shadow's size and intensity. */
    @Test
    fun `each zone editor offers three number fields`() = stageMonitorTab { _ ->
        numberFields().assertCountEquals(ZoneOrdinal.COUNT * 3)
        onAllNodesWithText("SIZE (%)").assertCountEquals(ZoneOrdinal.COUNT)
        onAllNodesWithText("INTENSITY (%)").assertCountEquals(ZoneOrdinal.COUNT)
    }

    /**
     * Unlike the Dictionary tab's, this tab's shadow controls are always on screen — they sit in a
     * plain `SettingRow` rather than behind an `AnimatedVisibility` keyed on the shadow flag, so
     * turning shadow off does not take them away.
     */
    @Test
    fun `the shadow controls are always shown, whatever the shadow flag says`() {
        stageMonitorTab(initial = zoneStyled(StageMonitorStyleZone.TOP_LEFT) { copy(shadow = false) }) { _ ->
            onAllNodesWithText("Shadow").assertCountEquals(ZoneOrdinal.COUNT)
            onAllNodesWithText("SIZE (%)").assertCountEquals(ZoneOrdinal.COUNT)
        }
    }

    @Test
    fun `each zone editor offers all four style buttons`() = stageMonitorTab { _ ->
        for (label in listOf("B", "I", "U", "S")) {
            onAllNodesWithText(label).assertCountEquals(ZoneOrdinal.COUNT)
        }
    }

    @Test
    fun `each zone editor offers three vertical and three horizontal alignment buttons`() =
        stageMonitorTab { _ ->
            for (description in VAlign.descriptions) {
                onAllNodesWithContentDescription(description).assertCountEquals(ZoneOrdinal.COUNT)
            }
            // The horizontal buttons publish no description at all, so they are counted by shape.
            horizontalAlignButtons().assertCountEquals(ZoneOrdinal.COUNT * 3)
        }

    /**
     * The one control on the tab that carries no text, no content description and no role: the
     * expand arrow inside each font dropdown. It is invisible to both of
     * `StageMonitorSettingsTabLabelsTest`'s completeness guards — one sweeps text, the other
     * descriptions — so its count is pinned here instead. A new unlabelled control fails this.
     */
    @Test
    fun `the only unlabelled controls are the six font dropdown arrows`() = stageMonitorTab { _ ->
        fontDropdownArrows().assertCountEquals(ZoneOrdinal.COUNT)
    }

    // ── The ordinals themselves ─────────────────────────────────────────────────────────────────

    /**
     * Validates [ZoneOrdinal] against the settings rather than against the layout: each zone in turn
     * is given a font size no other zone holds, and the field carrying it must be the one at that
     * zone's ordinal. This is what licenses every positional lookup in the behaviour tests.
     */
    @Test
    fun `each zone ordinal addresses the zone it is named for`() {
        for ((index, zone) in ZoneOrdinal.inOrder.withIndex()) {
            val marker = 111 + index
            stageMonitorTab(initial = zoneStyled(zone) { copy(fontSize = marker) }) { _ ->
                assertEquals(
                    marker.toString(),
                    numberFields()[index * 3].fetchSemanticsNode().config
                        .let { c -> c[SemanticsProperties.EditableText].text },
                    "the font size at ordinal $index must belong to $zone",
                )
                assertEquals(index, ZoneOrdinal.of(zone), "ZoneOrdinal.of($zone) must agree")
            }
        }
    }

    // ── The stepper arrows ──────────────────────────────────────────────────────────────────────

    /**
     * Every stepper arrow on the tab is laid out at a size a click can reach.
     *
     * They used to collapse to zero pixels wide on every tab in the app — a defect in the shared
     * `NumberSettingsTextField`, which this test pinned as present-but-unusable while it stood. Now
     * that it is fixed, the same test guards the fix.
     */
    @Test
    fun `the stepper arrows are laid out where they can be clicked`() = stageMonitorTab { _ ->
        assertStepperArrowsUsable(expected = ZoneOrdinal.COUNT * 3)
    }
}
