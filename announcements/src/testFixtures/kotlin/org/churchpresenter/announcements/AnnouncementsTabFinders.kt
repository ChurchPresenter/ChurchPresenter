@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.announcements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────
// (renderedText/showsExactly/showsContainingText are shared — see TabRenderedText.kt)

/**
 * A button, addressed by the content description its tooltip gives it and by which half of the tab
 * it belongs to.
 *
 * The announcement and the timer each have their own Go Live and Add to Schedule, identically
 * labelled, so a bare lookup is ambiguous — and an ambiguous lookup fails as "cannot inject mouse
 * input" rather than as anything that names the real problem. They are told apart by position: the
 * announcement's row is above the timer's.
 */
private fun ComposeUiTest.buttonsByRow(label: String) =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .sortedBy { it.boundsInRoot.top }

fun ComposeUiTest.annButton(label: String): SemanticsNodeInteraction {
    val index = buttonsByRow(label).indices.firstOrNull()
        ?: error("no button labelled \"$label\" is on screen")
    return sortedButton(label, index)
}

/** The timer's copy of a button that both halves of the tab have. */
fun ComposeUiTest.timerButton(label: String): SemanticsNodeInteraction =
    sortedButton(label, buttonsByRow(label).lastIndex)

private fun ComposeUiTest.sortedButton(label: String, position: Int): SemanticsNodeInteraction {
    val tops = buttonsByRow(label).map { it.boundsInRoot.top }
    val target = tops.getOrNull(position) ?: error("no button labelled \"$label\" at $position")
    // Re-resolve through the matcher rather than holding the node, so the interaction stays live.
    val unsorted = onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
    return onAllNodesWithContentDescription(label)[unsorted.indexOfFirst { it.boundsInRoot.top == target }]
}

/**
 * The loop count field's own increment button.
 *
 * "Increment"/"Decrement" also label the font-size field's arrows, so a bare lookup is ambiguous.
 * Loop count sits lower on screen (in the right column, below the preview), so it is the one with
 * the larger `top`.
 */
fun ComposeUiTest.loopCountIncrement(): SemanticsNodeInteraction {
    val nodes = onAllNodesWithContentDescription("Increment").fetchSemanticsNodes(atLeastOneRootRequired = false)
    val lowest = nodes.indices.maxByOrNull { nodes[it].boundsInRoot.top } ?: error("no Increment button is on screen")
    return onAllNodesWithContentDescription("Increment")[lowest]
}

fun ComposeUiTest.hasAnnButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/**
 * The announcement text box — the first field taking typed text.
 *
 * Addressed that way rather than by its hint, because the hint is a separate `Text` inside the
 * `BasicTextField` decoration box and vanishes as soon as anything is typed.
 */
fun ComposeUiTest.announcementField() = onAllNodes(hasSetTextAction())[0]

/**
 * The expiry-message field, addressed as the lowest field on screen.
 *
 * Not by index: the tab has eight fields (the announcement, the font name and size, the three timer
 * digits, this, and the loop count) and their order in the semantics tree is not the order they are
 * drawn in. The expiry message is the bottom-most of them in the left column — the loop count sits
 * lower, pinned under the preview in the right column — and it is the only one whose presence
 * depends on the timer mode. The left column is told apart by the announcement field, which spans it.
 */
fun ComposeUiTest.expiredTextField(): SemanticsNodeInteraction {
    val fields = onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false)
    val leftColumnEnd = fields.firstOrNull()?.boundsInRoot?.right ?: error("no text fields are on screen")
    val lowest = fields.indices
        .filter { fields[it].boundsInRoot.left < leftColumnEnd }
        .maxByOrNull { fields[it].boundsInRoot.top }
        ?: error("no text fields are on screen")
    return onAllNodes(hasSetTextAction())[lowest]
}
