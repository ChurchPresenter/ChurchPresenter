@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────

// renderedText/showsExactly/showsContainingText live in TabRenderedText.kt — they are shared with
// the other tab suites in this package.

/**
 * A toolbar or row button, addressed by the content description [TooltipIconButton] gives it —
 * which is its tooltip text, so the label a user would see is also the test's selector.
 */
internal fun ComposeUiTest.button(label: String) = onNodeWithContentDescription(label)

/**
 * A toolbar button addressed by test tag rather than by its tooltip.
 *
 * Undo and Redo name their keyboard shortcut in the tooltip, and that text is now built from the
 * live binding — it reads `Ctrl+Z` on Windows and Linux but `⌃Z` on macOS. Addressing those two by
 * their visible label would make the test pass on whichever platform the constant was written for
 * and fail on the rest.
 */
internal fun ComposeUiTest.taggedButton(tag: String) = onNodeWithTag(tag)

/**
 * The [n]th button with this label, top to bottom.
 *
 * Row buttons repeat once per schedule item, so a test that means "the second item's Go Live"
 * addresses it by position — which is also what the operator is doing.
 */
internal fun ComposeUiTest.buttonAt(label: String, n: Int) = onAllNodesWithContentDescription(label)[n]

internal fun ComposeUiTest.buttonCount(label: String): Int =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .size

/**
 * The note editor, which only exists once a row's note has been opened.
 *
 * Addressed as the node taking typed text rather than by its placeholder: the placeholder is drawn
 * separately and disappears as soon as anything is typed.
 */
internal fun ComposeUiTest.noteField() = onAllNodes(hasSetTextAction())[0]

/**
 * Where [labels] appear on screen, top to bottom, ignoring any that are absent.
 *
 * Ordered by vertical position rather than by walking the schedule, so a change to the order the
 * tab draws rows in is visible here. Takes explicit labels because a row is not one node: a song
 * draws its number, title and songbook separately, so there is no single node carrying the item's
 * `displayText`.
 */
internal fun ComposeUiTest.orderOf(vararg labels: String): List<String> {
    val wanted = labels.toSet()
    return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString("") { it.text } ?: return@mapNotNull null
            if (text in wanted) node.boundsInRoot.top to text else null
        }
        .sortedBy { it.first }
        .map { it.second }
        .distinct()
}
