@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performTouchInput

/**
 * Clicks the window's bottom-right corner, outside any open menu or dialog, which is how an
 * operator dismisses one without choosing anything. Escape does not reach a desktop popup from the
 * test's semantics tree; a click outside it does.
 */
internal fun ComposeUiTest.clickOutsidePopup() {
    onAllNodes(isRoot())[0].performTouchInput { click(bottomRight - Offset(2f, 2f)) }
    waitForIdle()
}
