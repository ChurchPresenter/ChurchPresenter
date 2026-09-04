@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A complete inventory of what the tab puts on screen: every heading, caption, button label and
 * stored value it displays, with the exact number of times each appears.
 *
 * The behaviour tests drive the controls; this asserts the words around them are actually there. A
 * caption is as much a part of the tab as the control it names — a `stringResource` pointed at the
 * wrong key, or a row quietly dropped in a refactor, changes nothing a behaviour test would notice,
 * because those find their targets by displayed value or by ordinal rather than by caption.
 *
 * The counts carry as much weight as the strings. "FONT SIZE" appearing four times is what says all
 * four sections that have one rendered it; "FONT TYPE" appearing twice is what says only Word and
 * Reference offer a family. Both lists were generated from the rendered semantics tree, so they are
 * exhaustive by construction rather than by memory.
 */
class DictionarySettingsTabLabelsTest {

    /** Every string the tab renders out of the box, and how many times it must appear. */
    private val outOfTheBox = mapOf(
        // The six section headings.
        Section.WORD to 1,
        Section.DEFINITION to 1,
        Section.CARD_BACKGROUND to 1,
        Section.REFERENCE to 1,
        Section.KJV_USAGE to 1,
        Section.TRANSITIONS to 1,

        // Switch captions.
        "Show" to 4,
        "Fade In" to 1,
        "Fade Out" to 1,

        // The five colour fields visible with both shadows off, and the values they hold.
        "COLOR" to 5,
        "#FFFFFF" to 2,   // word and reference share the default white
        "#DDDDDD" to 1,   // definition
        "#AAAAAA" to 1,   // KJV usage
        "#1A1A2E" to 1,   // card background

        // Style buttons — one set for Word, one for Reference, each ending in the backdrop chip.
        "B" to 2,
        "I" to 2,
        "U" to 2,
        "S" to 2,
        "A" to 2,

        // Font controls and their stored values.
        "FONT TYPE" to 2,
        "Arial" to 2,
        "FONT SIZE" to 4,
        "70" to 1,        // word
        "32" to 1,        // definition
        "28" to 1,        // reference
        "22" to 1,        // KJV usage

        // The two sliders.
        "Opacity" to 1,
        "92%" to 1,
        "Transition Duration" to 1,
        "500 ms" to 1,
    )

    @Test
    fun `every label the tab renders is on screen the expected number of times`() = dictionaryTab { _ ->
        for ((text, count) in outOfTheBox) {
            onAllNodesWithText(text).assertCountEquals(count)
        }
    }

    /** Guards against a control being added to the tab without a test noticing. */
    @Test
    fun `the tab renders no text beyond the inventory`() = dictionaryTab { _ ->
        val rendered = mutableSetOf<String>()
        onAllNodesWithText("", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .forEach { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { rendered += it.text }
                node.config.getOrNull(SemanticsProperties.EditableText)?.let { rendered += it.text }
            }
        assertEquals(
            emptyList(),
            rendered.filter { it.isNotBlank() && it !in outOfTheBox }.sorted(),
            "the tab renders text the inventory does not list — add it, with its expected count",
        )
    }

    /**
     * The rest of the tab's vocabulary only appears once a shadow is switched on. This puts both
     * shadow rows on screen at once and checks everything they add, including the stepper arrows'
     * content descriptions, which are the only thing those buttons publish.
     */
    @Test
    fun `every label the shadow rows render is on screen`() {
        dictionaryTab(initial = dictionarySettings { copy(wordShadow = true, referenceShadow = true) }) { _ ->
            onAllNodesWithText("SIZE (%)").assertCountEquals(2)
            onAllNodesWithText("INTENSITY (%)").assertCountEquals(2)
            onAllNodesWithText("100").assertCountEquals(2)  // both shadow sizes
            onAllNodesWithText("90").assertCountEquals(2)   // both shadow intensities
            onAllNodesWithText("#000000").assertCountEquals(2)
            onAllNodesWithText("COLOR").assertCountEquals(7)
            onAllNodesWithContentDescription("Increment").assertCountEquals(8)
            onAllNodesWithContentDescription("Decrement").assertCountEquals(8)
        }
    }

    /**
     * The readouts are the only place a slider's value is shown, so they are checked against a
     * fixture rather than only against the defaults — a readout that ignored its input would still
     * look right out of the box.
     */
    @Test
    fun `the slider readouts render the stored values`() {
        dictionaryTab(
            initial = dictionarySettings { copy(cardBackgroundOpacity = 0.37f, transitionDuration = 1250f) },
        ) { _ ->
            onAllNodesWithText("37%").assertCountEquals(1)
            onAllNodesWithText("1250 ms").assertCountEquals(1)
            onAllNodesWithText("92%").assertCountEquals(0)
            onAllNodesWithText("500 ms").assertCountEquals(0)
        }
    }
}
