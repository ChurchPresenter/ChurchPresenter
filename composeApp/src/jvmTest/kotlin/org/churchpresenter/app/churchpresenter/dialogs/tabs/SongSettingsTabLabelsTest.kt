@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test

/**
 * A complete inventory of the text the tab puts on screen with default settings — every section
 * heading, every field caption, every button label and every value shown in a closed field, with the
 * exact number of times each appears.
 *
 * The behaviour tests drive the controls; this asserts the words around them are actually there. A
 * caption is as much a part of the tab as the control it names — a `stringResource` pointed at the
 * wrong key, or a row quietly dropped in a refactor, changes nothing a behaviour test would notice
 * because those find their targets by tag, ordinal or value rather than by caption.
 *
 * The counts are as load-bearing as the strings. "Font Size:" appearing eight times is what says all
 * eight styled-text blocks rendered their size row; if one block disappeared, the string would still
 * be found but the count would not match. The list below is generated from the rendered semantics
 * tree, so it is exhaustive by construction rather than by memory.
 */
class SongSettingsTabLabelsTest {

    /** Every distinct string the tab renders by default, and how many times it must appear. */
    private val expected = mapOf(
        // Section headings — one each.
        "Song Title Slide" to 1,
        "Song Number" to 1,
        "Title" to 1,
        "Transition" to 1,
        "Text Margins" to 1,
        "Lyrics" to 1,
        "Fullscreen Display" to 1,
        "Lower Third Display" to 1,
        "Look Ahead (Fullscreen)" to 1,
        "Look Ahead Next Section (Fullscreen)" to 1,
        "Look Ahead (Lower Third)" to 1,
        "Look Ahead Next Section (Lower Third)" to 1,

        // Row captions. The counts say how many blocks offer each control.
        "Font Size:" to 8,
        "Font Type:" to 3,
        "FONT TYPE:" to 4,
        "Color:" to 1,
        "COLOR:" to 6,
        "Horizontal alignment:" to 6,
        "Vertical alignment:" to 3,
        "Display Mode:" to 4,
        "Show Number:" to 1,
        "Show Title:" to 1,
        "Bilingual Layout:" to 1,
        "Transition Duration:" to 1,
        "End-of-Song (*) Spacing" to 1,

        // Per-output captions inside the paired rows and fields.
        "Full Screen" to 4,
        "Lower Third" to 4,
        "FULL SCREEN" to 6,
        "LOWER THIRD" to 6,

        // Checkbox captions.
        "Enabled" to 1,
        "Show song number before title" to 1,
        "Word Wrap" to 1,
        "Fade In" to 1,
        "Fade Out" to 1,
        "Crossfade" to 1,
        "Auto" to 6,

        // Segmented button labels.
        "1 Verse" to 4,
        "1 Line" to 4,
        "Both" to 4,
        "Primary" to 4,
        "Secondary" to 4,
        "Left / Right" to 1,
        "Top / Bottom" to 1,

        // Style buttons.
        "B" to 8,
        "I" to 8,
        "U" to 8,
        "S" to 8,

        // The margins diagram.
        "TOP" to 1,
        "LEFT" to 1,
        "RIGHT" to 1,
        "BOTTOM" to 1,
        "Screen" to 1,

        // Values shown in closed fields.
        "First Page" to 4,
        "Arial" to 8,   // the default family, shown in all eight font dropdowns
        "#FFFFFF" to 6,
        // The chord colour a chart's chords take, one per lyric block.
        "CHORD COLOR" to 2,
        "#4FD3E8" to 2,
        "#888888" to 2,
        "500ms" to 1,
    )

    @Test
    fun `every label the tab renders is on screen the expected number of times`() = songTab { _ ->
        for ((text, count) in expected) {
            onAllNodesWithText(text).assertCountEquals(count)
        }
    }

    /**
     * The inventory above is the whole of it: nothing else is rendered. Guards against a control
     * being added without a test, which the per-string assertions alone would not catch.
     */
    @Test
    fun `the tab renders no text beyond the inventory`() = songTab { _ ->
        val rendered = mutableSetOf<String>()
        onAllNodesWithText("", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .forEach { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { rendered += it.text }
                node.config.getOrNull(SemanticsProperties.EditableText)?.let { rendered += it.text }
            }
        // The stepper fields publish their numeric value as editable text; those are asserted by the
        // behaviour tests, so only non-numeric strings are compared here.
        val unexpected = rendered.filter { it.isNotBlank() && it.toIntOrNull() == null && it !in expected }
        kotlin.test.assertEquals(
            emptyList(),
            unexpected.sorted(),
            "the tab renders text the label inventory does not list — add it, with its expected count",
        )
    }

    @Test
    fun `a stored non-default value replaces the default shown in its field`() {
        val changed = AppSettings().let {
            it.copy(songSettings = it.songSettings.copy(transitionDuration = 1750f, lyricsColor = "#123456"))
        }
        songTab(initial = changed) { _ ->
            onAllNodesWithText("1750ms").assertCountEquals(1)
            onAllNodesWithText("500ms").assertCountEquals(0)
            onAllNodesWithText("#123456").assertCountEquals(1)
            onAllNodesWithText("#FFFFFF").assertCountEquals(5)
        }
    }
}
