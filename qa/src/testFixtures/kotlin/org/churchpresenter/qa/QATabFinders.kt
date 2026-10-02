@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────
// (renderedText/showsExactly/showsContainingText are shared — see TabRenderedText.kt)

/** A button, addressed by the content description its tooltip gives it. */
fun ComposeUiTest.qaButton(label: String): SemanticsNodeInteraction =
    onNodeWithContentDescription(label)

fun ComposeUiTest.hasQaButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/** The question-entry box — the tab's first freely-typed field. */
fun ComposeUiTest.qaAddField() = onAllNodes(hasSetTextAction())[0]

/**
 * The inline edit box of the row being edited.
 *
 * The add box is always present and comes first in the tree, so the edit field is the *second*
 * typed field — there is only ever one row in edit mode at a time.
 */
fun ComposeUiTest.editField() = onAllNodes(hasSetTextAction())[1]

/** Clicks a labelled control, taking the topmost when a label repeats across panes. */
fun ComposeUiTest.clickQaLabel(label: String) {
    val nodes = onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false)
    val topmost = nodes.indices.minByOrNull { nodes[it].boundsInRoot.top }
        ?: error("nothing labelled \"$label\" is on screen")
    onAllNodesWithText(label)[topmost].performClick()
    waitForIdle()
}

/**
 * Chooses a view from the FILTER dropdown.
 *
 * The dropdown merges its caption and current value into one node ("FILTERAll (2)"), and each
 * option carries a live count ("Approved (1)"), so the trigger is matched on the caption and the
 * option on its name plus the opening bracket.
 */
fun ComposeUiTest.selectFilter(name: String) {
    onAllNodes(hasText("FILTER", substring = true))[0].performClick()
    waitForIdle()
    onAllNodes(hasText("$name (", substring = true))[0].performClick()
    waitForIdle()
}

/** Chooses an ordering from the SORT dropdown — its options carry no count, unlike FILTER's. */
fun ComposeUiTest.selectSort(name: String) {
    onAllNodes(hasText("SORT", substring = true))[0].performClick()
    waitForIdle()
    onAllNodes(hasText(name))[0].performClick()
    waitForIdle()
}

/** The nth button with this label, top to bottom — row buttons repeat once per question. */
fun ComposeUiTest.qaButton2(label: String, n: Int): SemanticsNodeInteraction {
    val nodes = onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
    val order = nodes.indices.sortedBy { nodes[it].boundsInRoot.top }
    return onAllNodesWithContentDescription(label)[order[n]]
}

/** Which of [texts] are on screen, top to bottom. */
fun ComposeUiTest.orderOfQuestions(vararg texts: String): List<String> =
    texts.mapNotNull { text ->
        onAllNodes(hasText(text, substring = true))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .minByOrNull { it.boundsInRoot.top }
            ?.let { it.boundsInRoot.top to text }
    }.sortedBy { it.first }.map { it.second }

/** The Add button beside the question box — a plain labelled button, not an icon. */
fun ComposeUiTest.addButton(): SemanticsNodeInteraction = onAllNodesWithText(QALabel.ADD)[0]
