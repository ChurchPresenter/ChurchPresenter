@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/**
 * Opening the Customize dialog and driving the controls inside it.
 *
 * The suites that only assert on which controls a category offers keep their own one-line helpers;
 * these are for the ones that click and type, where every test needs the same three steps — open the
 * dialog, pick the category, switch it on — before it can touch anything.
 */

/**
 * Opens row [row]'s Customize dialog on [pane], with [element] chipped.
 *
 * [override] on is the usual case: a category following the global settings swallows pointer input,
 * so a test that clicks anything has to switch it on first, exactly as an operator does.
 */
internal fun ComposeUiTest.openCustomizePane(
    pane: CustomizePane,
    element: CustomizeElement? = null,
    row: Int = 0,
    override: Boolean = true,
) {
    gridButton(Grid.customize(row)).performScrollTo().performClick()
    waitForIdle()
    onNodeWithTag(railTag(pane.name)).performClick()
    waitForIdle()
    if (override) {
        onNodeWithTag(CUSTOMIZE_OVERRIDE_SWITCH_TAG).performClick()
        waitForIdle()
    }
    if (element != null) openElement(element)
}

/** Chips [element] in the already-open dialog. */
internal fun ComposeUiTest.openElement(element: CustomizeElement) {
    onNodeWithTag(elementChipTag(element.name)).performClick()
    waitForIdle()
}

/**
 * Clicks the checkbox captioned [label] — the `ToggleControl` the panes and the strip are built from.
 *
 * [scroll] off for a control on the strip under the preview: only the control column scrolls, and
 * `performScrollTo` fails outright on a node with no scrollable ancestor rather than doing nothing.
 */
internal fun ComposeUiTest.toggleCheckbox(label: String, scroll: Boolean = true) {
    val node = onNode(isToggleable() and hasText(label))
    if (scroll) node.performScrollTo()
    node.performClick()
    waitForIdle()
}

/**
 * Picks [option] from a `ChoiceControl`, whose segments are plain labelled buttons.
 *
 * [nth] because a lower-third surface has two such rows carrying the same words — the band's type
 * row and, below it, the wash above the band, which is drawn with the band's own labels on purpose.
 * They come out in composition order, so 0 is the band's and 1 is the wash's.
 */
internal fun ComposeUiTest.chooseSegment(option: String, scroll: Boolean = true, nth: Int = 0) {
    val node = onAllNodesWithText(option)[nth]
    if (scroll) node.performScrollTo()
    node.performClick()
    waitForIdle()
}
