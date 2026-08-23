@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The increment/decrement arrows every `NumberSettingsTextField` publishes, shared by every settings
 * tab suites.
 *
 * These arrows used to lay out **zero pixels wide** on every tab — the component's text column held
 * a `fillMaxWidth()` child and carried no `weight`, so it took the whole row and left the 20dp arrow
 * column nothing. Three suites pinned that as present-but-unusable. The layout is fixed and they now
 * assert the opposite: that the arrows occupy space and that clicking one moves a value. Left as a
 * shared helper because the defect was in the shared control, so its regression guard belongs in one
 * place too — `NumberSettingsTextFieldTest` covers the component itself.
 */

/** Every arrow of one direction, with the width it was laid out at. */
fun ComposeUiTest.stepperArrowWidths(direction: String): List<Float> =
    onAllNodesWithContentDescription(direction)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot.width }

/** What every number field on the tab currently shows, in tree order. */
private fun ComposeUiTest.numberFieldValues(): List<String> =
    numberFields()
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.config.getOrNull(SemanticsProperties.EditableText)?.text ?: "" }

/** Whether each number field was actually laid out, in the same tree order as the arrows. */
private fun ComposeUiTest.numberFieldIsDrawn(): List<Boolean> =
    numberFields()
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot.width > 0f }

/**
 * Asserts that [expected] arrows of each direction are published, that each one is laid out at a
 * clickable size wherever its own field is drawn, and that clicking an increment arrow raises
 * exactly one field's value by one.
 *
 * The "wherever its field is drawn" qualifier is load-bearing: these tabs compose sections that are
 * measured to nothing (a zone whose panel is collapsed, an output row for hardware that is absent).
 * Their fields measure `0x0` too, so an arrow of zero width there is the section being absent, not
 * the arrow collapsing — the defect this guards against took the arrows to zero while the field
 * beside them kept the whole row.
 *
 * Which field the click moves differs per tab and does not matter: what is asserted is that an arrow
 * click reaches a value at all, which the collapsed layout made impossible. The range clamp the
 * arrows enforce at either end is covered in `NumberSettingsTextFieldTest`.
 */
fun ComposeUiTest.assertStepperArrowsUsable(expected: Int) {
    val drawn = numberFieldIsDrawn()
    assertTrue(drawn.any { it }, "the tab must draw at least one number field to say anything about")

    for (direction in listOf("Increment", "Decrement")) {
        val widths = stepperArrowWidths(direction)
        assertEquals(expected, widths.size, "one $direction arrow per number field")
        assertEquals(
            drawn,
            widths.map { it > 0f },
            "each $direction arrow must occupy space exactly where its field does, got $widths",
        )
    }

    val first = drawn.indexOfFirst { it }
    val before = numberFieldValues()
    onAllNodesWithContentDescription("Increment")[first].performClick()
    waitForIdle()
    val after = numberFieldValues()

    val moved = before.indices.filter { before[it] != after[it] }
    assertEquals(1, moved.size, "one arrow click must move exactly one field: $before -> $after")
    val i = moved.single()
    assertEquals(
        before[i].toInt() + 1,
        after[i].toInt(),
        "the increment arrow must step its field up by one",
    )
}
