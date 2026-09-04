@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import org.churchpresenter.settings.StageMonitorContentType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A complete inventory of the text the tab renders, with the exact number of times each string
 * appears.
 *
 * The behaviour tests drive the controls; this asserts the words around them are there. On a tab
 * built from six copies of one editor the counts are the substance: "FONT TYPE" appearing six times
 * is what says all six editors rendered, and a caption pointed at the wrong `stringResource` would
 * change nothing a behaviour test notices, because those find their targets by value or by ordinal.
 *
 * The list was generated from the rendered semantics tree, so it is exhaustive by construction.
 * Counts are over *values* rather than nodes: `DropdownSettingsField` merges its caption and its
 * current value into one node, so a zone name is counted once for the editor title that carries it
 * and once for every routing dropdown currently set to it.
 */
class StageMonitorSettingsTabLabelsTest {

    /** Every string the tab renders out of the box, and how many times it must appear. */
    private val outOfTheBox = mapOf(
        // Section headings.
        "Screen Layout" to 1,
        "Screen Content" to 1,
        "Shows" to 1,
        "Transition Settings" to 1,

        // The layout catalog: one tab per zone count, and the name of the layout in force.
        "2 zones" to 1, "3 zones" to 1, "4 zones" to 1, "5 zones" to 1,
        ZoneLabel.CLASSIC to 1,
        // The other five-zone arrangement, whose card is drawn beside the classic one.
        "Top / 4 below" to 1,

        // The transition card.
        "Transition Duration:" to 1,
        "500ms" to 1,
        "Fade In" to 1, "Fade Out" to 1, "Crossfade" to 1,

        // Routing dropdown captions — one per content type, plus the metronome.
        "BIBLE" to 1, "SONGS" to 1, "PRESENTATION" to 1, "PRESENTER NOTES" to 1, "PICTURES" to 1,
        "MEDIA" to 1, "LOWER THIRD" to 1, "WEB" to 1, "STT" to 1, "CANVAS" to 1, "Q&A" to 1,
        "DICTIONARY" to 1, "CLOCK" to 1, "ANNOUNCEMENTS" to 1, "NEXT" to 1,
        ContentLabel.METRONOME to 1,

        // Zone names. Each names one preview cell and titles one editor, and is additionally the
        // value of every routing dropdown set to it.
        ZoneLabel.ZONE_1 to 4,        // cell + title + Bible + Songs
        ZoneLabel.ZONE_2 to 3,        // cell + title + Next
        ZoneLabel.ZONE_3 to 3,        // cell + title + Announcements
        ZoneLabel.ZONE_4 to 3,        // cell + title + Clock
        ZoneLabel.ZONE_5 to 2,        // cell + title — nothing is routed there
        ZoneLabel.FULL_SCREEN to 12,  // chip + title + the 10 types routed there
        ZoneLabel.NONE to 2,          // the None chip and the metronome's value

        // What the preview cells say is in each zone.
        "Bible, Songs" to 1,
        "Next" to 1,
        "Announcements" to 1,
        "Clock" to 1,
        "\u2014" to 2,                 // Zone 5 is empty, and so is None

        // Per-zone controls, six of each.
        "FONT TYPE" to 6,
        "FONT SIZE" to 6,
        "Arial" to 6,
        "TEXT COLOR" to 6,
        "BACKGROUND COLOR" to 6,
        "Shadow" to 6,
        "SIZE (%)" to 6,
        "INTENSITY (%)" to 6,
        "B" to 6, "I" to 6, "U" to 6, "S" to 6,
        // The backdrop split button: a chip drawing "A", and a caret beside it.
        "A" to 6,

        // "COLOR" is now only the shadow colour's caption: the text colour has a name of its own,
        // and every zone but the full screen also carries a chord colour.
        "COLOR" to 6,
        "CHORD COLOR" to CHORD_COLOUR_ZONES,
        "#4FD3E8" to CHORD_COLOUR_ZONES,

        // The stored values those controls display.
        "#FFFFFF" to 6,   // every zone's text colour
        "#000000" to 12,  // every zone's background and shadow colour
        "35" to 5,        // five zones' font size
        "80" to 7,        // Full Screen's font size, plus every zone's shadow intensity
        "100" to 6,       // every zone's shadow size
    )

    @Test
    fun `every label the tab renders is on screen the expected number of times`() = stageMonitorTab { _ ->
        for ((text, count) in outOfTheBox) {
            onAllNodesWithText(text).assertCountEquals(count)
        }
    }

    /** The one preview string long enough to be worth building rather than spelling out. */
    @Test
    fun `the full-screen preview row lists its ten content types`() = stageMonitorTab { _ ->
        val expected = StageMonitorContentType.entries
            .filter { it !in setOf(
                StageMonitorContentType.BIBLE, StageMonitorContentType.SONGS,
                StageMonitorContentType.NEXT, StageMonitorContentType.CLOCK,
                StageMonitorContentType.ANNOUNCEMENT_TEXT,
            ) }
            .joinToString(", ") { ContentLabel.previewOf(it) }
        onAllNodesWithText(expected).assertCountEquals(1)
    }

    /** Guards against a control being added to the tab without a test noticing. */
    /** Guards against a control being added to the tab without a test noticing. */
    @Test
    fun `the tab renders no text beyond the inventory`() = stageMonitorTab { _ ->
        val longPreviewRow = StageMonitorContentType.entries
            .filter { it !in setOf(
                StageMonitorContentType.BIBLE, StageMonitorContentType.SONGS,
                StageMonitorContentType.NEXT, StageMonitorContentType.CLOCK,
                StageMonitorContentType.ANNOUNCEMENT_TEXT,
            ) }
            .joinToString(", ") { ContentLabel.previewOf(it) }

        val rendered = mutableSetOf<String>()
        onAllNodesWithText("", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .forEach { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { rendered += it.text }
                node.config.getOrNull(SemanticsProperties.EditableText)?.let { rendered += it.text }
            }
        assertEquals(
            emptyList(),
            rendered.filter { it.isNotBlank() && it !in outOfTheBox && it != longPreviewRow }.sorted(),
            "the tab renders text the inventory does not list — add it, with its expected count",
        )
    }

    /**
     * The only content descriptions on the tab: the stepper arrows, the vertical-alignment
     * buttons, and the two halves of the backdrop button. The horizontal-alignment buttons
     * deliberately appear nowhere here — they publish none, which is the accessibility gap the
     * support file records.
     */
    @Test
    fun `every content description the tab publishes is accounted for`() = stageMonitorTab { _ ->
        onAllNodesWithContentDescription("Increment").assertCountEquals(18)
        onAllNodesWithContentDescription("Decrement").assertCountEquals(18)
        for (description in VAlign.descriptions) {
            onAllNodesWithContentDescription(description).assertCountEquals(6)
        }

        // Collected from nodes that carry a description, not from nodes that carry text: the
        // stepper arrows and alignment icons publish a description and nothing else, so sweeping
        // text nodes finds none of them.
        val described = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription) }
            .flatten()
            .toSet()
        assertEquals(
            listOf(
                "Align Bottom", "Align Middle", "Align Top", "Decrement", "Increment",
                "Text backing", "Text backing options",
            ),
            described.sorted(),
            "the tab publishes a content description the inventory does not list",
        )
    }
}
