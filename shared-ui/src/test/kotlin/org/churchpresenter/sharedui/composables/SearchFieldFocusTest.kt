package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The search box helpers the tabs share: moving the keyboard into the box with its query selected
 * ([SearchFieldFocus], [rememberSearchFieldValue]), the banner that says the keyboard is there
 * ([SearchKeyboardBanner]), and the press that hands a tab root the keyboard ([goLiveKeyTarget]).
 */
@OptIn(ExperimentalTestApi::class)
class SearchFieldFocusTest {

    private fun selection(range: TextRange) =
        SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, range)

    @Test
    fun `asking for the search box focuses it with the whole query selected`() = runComposeUiTest {
        val focus = SearchFieldFocus()
        setContent {
            MaterialTheme {
                var value by rememberSearchFieldValue("john 3", focus)
                BasicTextField(value, { value = it }, Modifier.testTag("field").focusRequester(focus.requester))
            }
        }
        waitForIdle()

        focus.focusAndSelectAll()
        waitForIdle()

        onNodeWithTag("field").assertIsFocused()
        onNodeWithTag("field").assert(selection(TextRange(0, 6)))
    }

    @Test
    fun `a query changed from outside puts the caret at its end`() = runComposeUiTest {
        val focus = SearchFieldFocus()
        var query by mutableStateOf("gen")
        setContent {
            MaterialTheme {
                var value by rememberSearchFieldValue(query, focus)
                BasicTextField(value, { value = it }, Modifier.testTag("field").focusRequester(focus.requester))
            }
        }
        waitForIdle()

        query = "Genesis 2"
        waitForIdle()

        onNodeWithTag("field").assert(hasText("Genesis 2"))
        onNodeWithTag("field").assert(selection(TextRange(9)))
    }

    @Test
    fun `the banner names the go live key, and the way back when something is live`() = runComposeUiTest {
        var live by mutableStateOf(false)
        var clicks = 0
        setContent { MaterialTheme { SearchKeyboardBanner(somethingLive = live, onClick = { clicks++ }) } }

        onNodeWithText("go live", substring = true).assertExists()
        live = true
        waitForIdle()
        onNodeWithText("back to live", substring = true).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `a press inside a tab hands its root the keyboard again`() = runComposeUiTest {
        var live = 0
        setContent {
            MaterialTheme {
                Column(Modifier.size(300.dp).goLiveKeyTarget(enabled = true) { live++ }) {
                    var text by mutableStateOf("")
                    BasicTextField(text, { text = it }, singleLine = true, modifier = Modifier.testTag("field"))
                    Box(Modifier.size(100.dp).testTag("blank"))
                }
            }
        }
        onNodeWithTag("field").requestFocus()
        waitForIdle()

        onNodeWithTag("blank").performMouseInput { click(center) }
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(1, live, "the press took the keyboard off the field and onto the tab")
    }
}
