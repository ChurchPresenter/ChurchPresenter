@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag

/**
 * The right-hand (live chapter) copy of a verse line, in split-browse mode.
 *
 * Both panels show the same chapter, so the line appears twice with identical text; they are told
 * apart by position — the live panel is the right-hand one.
 */
fun ComposeUiTest.livePanelVerse(text: String): SemanticsNodeInteraction {
    val nodes = onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false)
    val rightmost = nodes.indices.maxByOrNull { nodes[it].boundsInRoot.left }
        ?: error("no verse line reading \"$text\" is on screen")
    return onAllNodesWithText(text)[rightmost]
}

/** How many times a verse line is on screen — two when the live panel mirrors the browser. */
fun ComposeUiTest.countOnScreen(text: String): Int =
    onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).size

/** The search box's clear (×) button — tagged in the tab because its icon is decorative. */
fun ComposeUiTest.clearSearchButton() = onNodeWithTag("bible_searchClear")

/**
 * The verse lines currently listed, in the order shown.
 *
 * The verse column renders each line as `"<number>. <text>"` (the convention `viewmodel/VerseLine.kt`
 * parses), so a line is identified by that shape rather than by matching fixture text — which keeps
 * the ordering assertions honest instead of quietly becoming presence checks.
 */
fun ComposeUiTest.listedVerseLines(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString("") { it.text } ?: return@mapNotNull null
            if (Regex("""^\d+\.\s""").containsMatchIn(text)) node.boundsInRoot.top to text else null
        }
        .sortedBy { it.first }
        .map { it.second }
        .distinct()
