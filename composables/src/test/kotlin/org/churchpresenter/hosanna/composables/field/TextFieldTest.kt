@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testIcon

class TextFieldTest {

    private val field = hasSetTextAction()

    @Test
    fun `typing reaches onValueChange and hides the placeholder`() = runComposeUiTest {
        var value by mutableStateOf("")
        setContent {
            HosannaSurface { TextField(value, { value = it }, Modifier.width(240.dp), placeholder = "Title") }
        }
        onNodeWithText("Title").assertExists()
        onNode(field).performTextInput("Grace")
        waitForIdle()
        assertEquals("Grace", value)
        onNodeWithText("Title").assertDoesNotExist()
    }

    @Test
    fun `a labeled field shows its label in capitals and is 44dp tall`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    TextField("Amazing Grace", {}, Modifier.width(240.dp), label = "Song title")
                    TextField("", {}, Modifier.width(240.dp), placeholder = "Search")
                    TextField("", {}, Modifier.width(240.dp), placeholder = "Notes", multiline = true)
                }
            }
        }
        onNodeWithText("SONG TITLE").assertExists()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Amazing Grace")))
            .assertHeightIsEqualTo(FieldDefaults.LabeledHeight)
        onNodeWithText("Search").assertExists()
    }

    @Test
    fun `compact and multiline fields keep their heights`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    TextField("compact", {}, Modifier.width(240.dp))
                    TextField("multi", {}, Modifier.width(240.dp), multiline = true)
                }
            }
        }
        onNodeWithText("compact").assertHeightIsEqualTo(FieldDefaults.CompactHeight)
        onNodeWithText("multi")
            .assertHeightIsAtLeast(FieldDefaults.MultilineMinHeight - FieldDefaults.MultilineVerticalPadding * 2)
    }

    @Test
    fun `the clear button appears with text and focus, and clears`() = runComposeUiTest {
        var value by mutableStateOf("Grace")
        setContent {
            HosannaSurface {
                TextField(
                    value, { value = it }, Modifier.width(240.dp),
                    leadingIcon = testIcon(), clearContentDescription = "Clear search",
                )
            }
        }
        onNodeWithContentDescription("Clear search").assertDoesNotExist()
        onNode(field).requestFocus()
        waitForIdle()
        onNodeWithContentDescription("Clear search").performMouseInput {
            moveTo(center)
            press()
            release()
        }
        waitForIdle()
        assertEquals("", value)
        onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }

    @Test
    fun `an error replaces the helper and the counter shows the limit`() = runComposeUiTest {
        var error by mutableStateOf<String?>(null)
        setContent {
            HosannaSurface {
                TextField(
                    "Too long name", {}, Modifier.width(240.dp),
                    helper = "Short name", error = error, maxLength = 10, suffix = "px",
                )
            }
        }
        onNodeWithText("Short name").assertExists()
        onNodeWithText("13 / 10").assertExists()
        onNodeWithText("px").assertExists()
        error = "Too long"
        waitForIdle()
        onNodeWithText("Too long").assertExists()
        onNodeWithText("Short name").assertDoesNotExist()
    }

    @Test
    fun `a disabled field cannot be edited`() = runComposeUiTest {
        var value by mutableStateOf("Fixed")
        setContent { HosannaSurface { TextField(value, { value = it }, Modifier.width(240.dp), enabled = false) } }
        onNodeWithText("Fixed").assertIsNotEnabled().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
    }

    @Test
    fun `a read-only field shows its text but offers no editing`() = runComposeUiTest {
        setContent { HosannaSurface { TextField("Shown", {}, Modifier.width(240.dp), readOnly = true) } }
        onNodeWithText("Shown").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.InsertTextAtCursor))
    }
}
