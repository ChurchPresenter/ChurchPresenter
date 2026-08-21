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
        "Screen Content" to 1,
        "Screen Layout" to 1,

        // Routing dropdown captions — one per content type, plus the metronome.
        "BIBLE" to 1, "SONGS" to 1, "PRESENTATION" to 1, "PRESENTER NOTES" to 1, "PICTURES" to 1,
        "MEDIA" to 1, "LOWER THIRD" to 1, "WEB" to 1, "STT" to 1, "CANVAS" to 1, "Q&A" to 1,
        "DICTIONARY" to 1, "CLOCK" to 1, "ANNOUNCEMENTS" to 1, "NEXT" to 1,
        ContentLabel.METRONOME to 1,
        // The preview restates the anchor under a title-case caption of its own, which is a
        // different string from the dropdown's uppercased one above.
        "Metronome Position" to 1,

        // Zone names. Each titles one editor; each is also the value of every dropdown set to it,
        // and Full Screen and None additionally caption a preview row.
        ZoneLabel.TOP_LEFT to 3,        // title + Bible + Songs
        ZoneLabel.TOP_RIGHT to 2,       // title + Next
        ZoneLabel.BOTTOM_LEFT to 2,     // title + Announcements
        ZoneLabel.BOTTOM_CENTER to 2,   // title + Clock
        ZoneLabel.BOTTOM_RIGHT to 1,    // title only — nothing is routed there
        ZoneLabel.FULL_SCREEN to 12,    // title + preview caption + the 10 types routed there
        ZoneLabel.NONE to 3,            // preview caption + the metronome's value and its own caption

        // Per-zone controls, six of each.
        "FONT TYPE" to 6,
        "FONT SIZE" to 6,
        "Arial" to 6,
        "BACKGROUND COLOR" to 6,
        "Shadow" to 6,

        "SIZE (%)" to 6,
        "INTENSITY (%)" to 6,
        "B" to 6, "I" to 6, "U" to 6, "S" to 6,

        // "COLOR" captions the text colour and the shadow colour in every editor — except in the
        // two zones a chart can land in, where the text colour is named "LYRICS COLOR" instead so
        // it says which of the two colours it is.
        "COLOR" to 10,
        "LYRICS COLOR" to CHORD_COLOUR_ZONES,
        "CHORD COLOR" to CHORD_COLOUR_ZONES,
        "#4FD3E8" to CHORD_COLOUR_ZONES,

        // The stored values those controls display.
        "#FFFFFF" to 6,   // every zone's text colour
        "#000000" to 12,  // every zone's background and shadow colour
        "35" to 5,        // five zones' font size
        "80" to 7,        // Full Screen's font size, plus every zone's shadow intensity
        "100" to 6,       // every zone's shadow size

        // The preview.
        "Bible, Songs" to 1,
        "Next" to 1,
        "Announcements" to 1,
        "Clock" to 1,
        "—" to 2,         // Bottom-Right is empty, and nothing is switched off
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
     * The only content descriptions on the tab: the stepper arrows and the vertical-alignment
     * buttons. The horizontal-alignment buttons deliberately appear nowhere here — they publish
     * none, which is the accessibility gap the support file records.
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
            listOf("Align Bottom", "Align Middle", "Align Top", "Decrement", "Increment"),
            described.sorted(),
            "the tab publishes a content description the inventory does not list",
        )
    }
}
