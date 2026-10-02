@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.sharedui.testing.showsContainingText

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────
// (renderedText/showsExactly/showsContainingText are shared — see TabRenderedText.kt)

/** A button, addressed by the content description its tooltip gives it. */
fun ComposeUiTest.dictButton(label: String): SemanticsNodeInteraction =
    onNodeWithContentDescription(label)

fun ComposeUiTest.hasDictButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/** The search box — the tab's only freely-typed field. */
fun ComposeUiTest.dictSearchField() = onAllNodes(hasSetTextAction())[0]

fun ComposeUiTest.dictSearch(query: String) {
    dictSearchField().performTextReplacement(query)
    waitForIdle()
}

/**
 * Matches the list row for [entry].
 *
 * A row merges its number, word and transliteration into one node, but they stay *separate* text
 * entries inside it — so a match on the concatenation finds nothing, and a match on any one of them
 * also hits the detail pane, which repeats all three. Requiring two of them together identifies the
 * row and nothing else.
 */
fun rowOf(entry: StrongsEntry) =
    hasText(entry.number) and hasText(entry.transliteration)

/** Whether [entry] is in the list right now. */
fun ComposeUiTest.listShows(entry: StrongsEntry): Boolean =
    onAllNodes(rowOf(entry)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

/**
 * Whether the detail pane is showing [entry].
 *
 * Keyed on the pronunciation because the number, word and transliteration are on the list row too —
 * asserting on those would pass whether or not the entry was ever opened, and would fail when
 * checking that a *previous* entry is no longer on show.
 */
fun ComposeUiTest.detailShows(entry: StrongsEntry): Boolean =
    showsContainingText(entry.pronunciation)

/**
 * Clicks one of the three language chips above the list.
 *
 * Addressed as the topmost node with that label: once interlinear data is available the tab also
 * shows book and chapter filters, which have an "All" of their own, so a bare lookup is ambiguous.
 */
fun ComposeUiTest.clickLanguageFilter(label: String) {
    val nodes = onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false)
    val topmost = nodes.indices.minByOrNull { nodes[it].boundsInRoot.top }
        ?: error("no chip labelled \"$label\" is on screen")
    onAllNodesWithText(label)[topmost].performClick()
    waitForIdle()
}

/** Opens an entry from the list. */
fun ComposeUiTest.selectEntry(entry: StrongsEntry) {
    onNode(rowOf(entry)).performClick()
    waitForIdle()
}
