@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.testing

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement

/**
 * In an open `ColorPickerField` dialog: types [hex] and confirms. The hex box is the only editable
 * "#" field.
 */
fun ComposeUiTest.confirmColorDialogWith(hex: String) {
    val fields = onAllNodes(hasSetTextAction() and hasText("#", substring = true))
    check(fields.fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
        "the colour dialog must offer a hex field"
    }
    fields[0].performTextReplacement(hex)
    waitForIdle()
    onNodeWithText("OK").performClick()
    waitForIdle()
}
