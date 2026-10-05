@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement

/*
 * Reaching a control by the row it sits in on the Profiles tab: its label and control share one line
 * (or the control wraps under it), so the control wanted is the one laid out beside the label.
 */

/** How far below its label a row's control may sit and still be that row's: a wrapped control. */
private const val ROW_REACH_PX = 44f

/**
 * The [nth] node matching [matcher] on the line of the row captioned [label] -- the row's own
 * stepper key, number field or button, never another row's that happens to match too.
 */
internal fun ComposeUiTest.inRow(label: String, matcher: SemanticsMatcher, nth: Int = 0): SemanticsNodeInteraction {
    val caption = onAllNodes(hasTextExactly(label), useUnmergedTree = true)[0]
    caption.performScrollTo()
    waitForIdle()
    val row = caption.fetchSemanticsNode().boundsInRoot
    val candidates = onAllNodes(matcher, useUnmergedTree = true)
    val nodes = candidates.fetchSemanticsNodes()
    val onLine = nodes.indices.filter { i ->
        val b = nodes[i].boundsInRoot
        val middle = (b.top + b.bottom) / 2
        middle >= row.top - ROW_REACH_PX / 2 && middle <= row.bottom + ROW_REACH_PX && b.left >= row.left
    }
    check(onLine.size > nth) { "no control #$nth on the line of \"$label\" (found ${onLine.size})" }
    return candidates[onLine[nth]]
}

/** Presses the row's "+" key [times] times. */
internal fun ComposeUiTest.stepUp(label: String, times: Int = 1) {
    repeat(times) {
        inRow(label, hasContentDescription("Increment")).performClick()
        waitForIdle()
    }
}

/** Presses the row's "−" key [times] times. */
internal fun ComposeUiTest.stepDown(label: String, times: Int = 1) {
    repeat(times) {
        inRow(label, hasContentDescription("Decrement")).performClick()
        waitForIdle()
    }
}

/** Types [value] into the row's [nth] number field. */
internal fun ComposeUiTest.typeInRow(label: String, value: Int, nth: Int = 0) {
    inRow(label, hasSetTextAction(), nth).performTextReplacement(value.toString())
    waitForIdle()
}

/**
 * Clicks the node tagged [tag] -- the first, where a preview and a large preview both draw one --
 * scrolled into view first where it sits in a scrolling column: a click below the fold lands nowhere.
 */
internal fun ComposeUiTest.tap(tag: String, useUnmergedTree: Boolean = true) {
    val node = onAllNodes(hasTestTag(tag), useUnmergedTree)[0]
    if (node.fetchSemanticsNode().config.contains(SemanticsProperties.TestTag)) {
        runCatching { node.performScrollTo() }
    }
    node.performClick()
    waitForIdle()
}

/** Clicks the node tagged [tag] at [at] from its top left, clear of any handle drawn over its middle. */
internal fun ComposeUiTest.tapAt(tag: String, at: Offset) {
    onAllNodes(hasTestTag(tag), useUnmergedTree = true)[0].performMouseInput { click(at) }
    waitForIdle()
}

/**
 * Drags the [nth] node tagged [tag] by ([dx], [dy]) pointer pixels, in two moves so a drag gesture
 * is recognised, and releases it.
 */
internal fun ComposeUiTest.dragTag(tag: String, dx: Float, dy: Float, nth: Int = 0) {
    onAllNodes(hasTestTag(tag), useUnmergedTree = true)[nth].performMouseInput {
        moveTo(center)
        press()
        moveBy(Offset(dx / 2, dy / 2))
        moveBy(Offset(dx / 2, dy / 2))
        release()
    }
    waitForIdle()
}

/** Drags the node tagged [tag] by [dx] × [dy], from [at] inside it rather than from its centre. */
internal fun ComposeUiTest.dragTagFrom(tag: String, at: Offset, dx: Float, dy: Float) {
    onAllNodes(hasTestTag(tag), useUnmergedTree = true)[0].performMouseInput {
        moveTo(at)
        press()
        moveBy(Offset(dx / 2, dy / 2))
        moveBy(Offset(dx / 2, dy / 2))
        release()
    }
    waitForIdle()
}

/** How many nodes are tagged [tag]. */
internal fun ComposeUiTest.countTag(tag: String): Int =
    onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size
