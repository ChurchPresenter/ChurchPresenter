package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import kotlin.test.Test
import kotlin.test.assertEquals

/** A multi-line field that Tab and Shift+Tab leave, instead of filling it with tab characters. */
@OptIn(ExperimentalTestApi::class)
class TabMovesFocusTest {

    @Test
    fun `tab moves on from a multi-line field and shift tab comes back, typing nothing`() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent {
            Column {
                BasicTextField("", {}, Modifier.testTag("before"))
                BasicTextField(text, { text = it }, Modifier.testTag("field").tabMovesFocus(), maxLines = 4)
                BasicTextField("", {}, Modifier.testTag("after"))
            }
        }
        onNodeWithTag("field").performClick()
        onNodeWithTag("field").performKeyInput { pressKey(Key.Tab) }
        onNodeWithTag("after").assertIsFocused()

        onNodeWithTag("after").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        onNodeWithTag("field").assertIsFocused()
        onNodeWithTag("field").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        onNodeWithTag("before").assertIsFocused()

        assertEquals("", text, "no tab character is typed")
    }
}
