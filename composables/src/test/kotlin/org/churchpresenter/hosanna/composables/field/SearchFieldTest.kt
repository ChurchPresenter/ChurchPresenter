@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults
import org.churchpresenter.hosanna.composables.testing.HosannaSurface

class SearchFieldTest {

    private val input = hasSetTextAction()
    private val width = Modifier.width(320.dp)

    @Test
    fun `the key cap shows only while empty and unfocused`() {
        assertTrue(showKeyCap("⌘F", "", focused = false))
        assertFalse(showKeyCap(null, "", focused = false))
        assertFalse(showKeyCap("⌘F", "a", focused = false))
        assertFalse(showKeyCap("⌘F", "", focused = true))
    }

    @Test
    fun `the field is 40dp, or 32dp when compact`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    SearchField("", {}, "Search", "Clear", width.testTag("regular"))
                    SearchField("", {}, "Search", "Clear", width.testTag("compact"), compact = true)
                }
            }
        }
        onNodeWithTag("regular").assertHeightIsEqualTo(FieldDefaults.SearchHeight)
        onNodeWithTag("compact").assertHeightIsEqualTo(FieldDefaults.SearchCompactHeight)
    }

    @Test
    fun `typing reaches onQueryChange, and the placeholder and key cap give way`() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent {
            HosannaSurface { SearchField(query, { query = it }, "Search songs", "Clear", width, shortcut = "⌘F") }
        }
        onNodeWithText("Search songs").assertExists()
        onNodeWithText("⌘F").assertExists()
        onNode(input).requestFocus()
        waitForIdle()
        onNodeWithText("⌘F").assertDoesNotExist()
        onNode(input).performTextInput("grace")
        waitForIdle()
        assertEquals("grace", query)
        onNodeWithText("Search songs").assertDoesNotExist()
    }

    @Test
    fun `a count shows while typing, and a spinner replaces it while searching`() = runComposeUiTest {
        var searching by mutableStateOf(false)
        setContent {
            HosannaSurface {
                SearchField("grace", {}, "Search", "Clear", width, resultCount = "12 results", searching = searching)
            }
        }
        val spinner = SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate,
        )
        onNodeWithText("12 results").assertExists()
        mainClock.autoAdvance = false
        searching = true
        mainClock.advanceTimeByFrame()
        onNodeWithText("12 results").assertDoesNotExist()
        onNode(spinner, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the clear button and the detection chip take mouse clicks`() = runComposeUiTest {
        var query by mutableStateOf("john 3:16")
        var switched = 0
        setContent {
            HosannaSurface {
                SearchField(
                    query, { query = it }, "Search", "Clear search", width,
                    detection = "Reference", onDetectionClick = { switched++ },
                )
            }
        }
        onNodeWithText("Reference").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performMouseInput { click(center) }
        waitForIdle()
        assertEquals(1, switched)
        onNodeWithContentDescription("Clear search").performMouseInput { click(center) }
        waitForIdle()
        assertEquals("", query)
    }

    @Test
    fun `Enter searches, Down moves on, Escape clears then leaves`() = runComposeUiTest {
        var query by mutableStateOf("grace")
        var searches = 0
        var moves = 0
        setContent {
            HosannaSurface {
                SearchField(
                    query, { query = it }, "Search", "Clear", width,
                    onSearch = { searches++ }, onMoveDown = { moves++ },
                )
            }
        }
        onNode(input).requestFocus()
        onNode(input).performKeyInput { pressKey(Key.Enter) }
        onNode(input).performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(1, searches)
        assertEquals(1, moves)
        onNode(input).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals("", query)
        onNode(input).assertIsFocused().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNode(input).assertIsNotFocused()
    }

    @Test
    fun `clicking the magnifier focuses the input`() = runComposeUiTest {
        setContent { HosannaSurface { SearchField("", {}, "Search", "Clear", width.testTag("search")) } }
        onNodeWithTag("search").performMouseInput {
            click(Offset(FieldDefaults.HorizontalPadding.toPx() + 4f, centerY))
        }
        waitForIdle()
        onNode(input).assertIsFocused()
    }

    @Test
    fun `a disabled search cannot be typed in or cleared`() = runComposeUiTest {
        setContent {
            HosannaSurface { SearchField("grace", {}, "Search", "Clear search", width, enabled = false) }
        }
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertIsNotEnabled()
        onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }
}
