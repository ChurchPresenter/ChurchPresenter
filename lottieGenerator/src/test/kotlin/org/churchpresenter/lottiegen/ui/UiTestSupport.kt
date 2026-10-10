@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.lottiegen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun ComposeUiTest.showDark(width: Dp = 1400.dp, height: Dp = 2400.dp, content: @Composable () -> Unit) {
    setContent { LottieGenTheme { Box(Modifier.size(width, height)) { content() } } }
    waitForIdle()
}

internal fun ComposeUiTest.node(text: String, substring: Boolean = false): SemanticsNodeInteraction =
    onAllNodesWithText(text, substring = substring, useUnmergedTree = true).onFirst()

internal fun ComposeUiTest.click(text: String, substring: Boolean = false) {
    node(text, substring).performClick()
    waitForIdle()
}

/** Clicks the item of an open menu: the last node with [text], since the anchor shows it too. */
internal fun ComposeUiTest.pick(text: String) {
    val nodes = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
    val item = onAllNodesWithText(text, useUnmergedTree = true)[nodes.size - 1]
    // Only a long list scrolls; a short one has no scrollable parent to bring the item into view.
    runCatching { item.performScrollTo() }
    item.performClick()
    waitForIdle()
}

/** Opens the dropdown labelled [label] (shown uppercase) and picks [item]. */
internal fun ComposeUiTest.choose(label: String, item: String, index: Int = 0) {
    onAllNodesWithText(label.uppercase(), useUnmergedTree = true)[index].performClick()
    waitForIdle()
    pick(item)
}

internal fun ComposeUiTest.hasNode(text: String, substring: Boolean = false): Boolean =
    onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/** Types [value] into the text field currently holding [current]. */
internal fun ComposeUiTest.type(current: String, value: String) {
    onAllNodes(hasSetTextAction() and hasText(current)).onFirst().performTextReplacement(value)
    waitForIdle()
}

/** Taps [dy] below the bottom of the node showing [text], at [fraction] of its width -- a slider under a label. */
internal fun ComposeUiTest.tapBelow(text: String, dy: Dp, fraction: Float = 0.5f) {
    node(text).performTouchInput { val touch = this; touch.click(Offset(width * fraction, height + dy.toPx())) }
    waitForIdle()
}

internal fun ComposeUiTest.clickDescription(description: String, index: Int = 0) {
    onAllNodes(hasContentDescription(description), useUnmergedTree = true)[index].performClick()
    waitForIdle()
}

/** Types into the last text field holding [current] -- a dialog's, drawn over the panel. */
internal fun ComposeUiTest.typeLast(current: String, value: String) {
    val matcher = hasSetTextAction() and hasText(current)
    val count = onAllNodes(matcher).fetchSemanticsNodes().size
    onAllNodes(matcher)[count - 1].performTextReplacement(value)
    waitForIdle()
}

/** Types [value] into every text field on screen, top to bottom, letting each commit land first. */
internal fun ComposeUiTest.fillEveryField(value: String) {
    var i = 0
    while (true) {
        val fields = onAllNodes(hasSetTextAction())
        if (i >= fields.fetchSemanticsNodes().size) break
        fields[i].performTextReplacement(value)
        waitForIdle()
        i++
    }
}

/** Clicks every node showing [text], first to last, re-reading the list after each click. */
internal fun ComposeUiTest.clickEach(text: String, substring: Boolean = false) {
    var guard = 0
    while (hasNode(text, substring) && guard++ < 20) click(text, substring)
}
