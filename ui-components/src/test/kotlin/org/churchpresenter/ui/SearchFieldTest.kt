@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shared search box.
 *
 * It is controlled, which is the point of it — the component this replaced held its own state, so a
 * screen could never clear or seed it. These assert on what the caller is told, not on internals.
 */
class SearchFieldTest {

    private val placeholder = "Search actions or keys…"

    private fun field(
        initial: String = "",
        block: ComposeUiTest.(get: () -> String) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf(initial) }
                SearchField(
                    value = value,
                    onValueChange = { value = it; current = it },
                    placeholder = placeholder,
                )
            }
        }
        block { current }
    }

    private fun ComposeUiTest.input() = onNode(hasSetTextAction())
    private fun ComposeUiTest.clearButton() = onNodeWithContentDescription("Clear search")

    @Test
    fun `typing is reported to the caller`() = field { get ->
        input().performTextInput("verse")

        assertEquals("verse", get())
    }

    @Test
    fun `the placeholder shows only while empty`() = field { _ ->
        onNodeWithText(placeholder).assertExists()

        input().performTextInput("v")

        onNodeWithText(placeholder).assertDoesNotExist()
    }

    @Test
    fun `there is nothing to clear when the field is empty`() = field {
        clearButton().assertDoesNotExist()
    }

    @Test
    fun `the clear button appears once there is text, and empties the field`() = field { get ->
        input().performTextInput("ctrl")
        clearButton().assertExists()

        clearButton().performClick()

        assertEquals("", get())
        clearButton().assertDoesNotExist()
        onNodeWithText(placeholder).assertExists()
    }

    @Test
    fun `an initial value is displayed rather than the placeholder`() = field(initial = "seeded") {
        onNodeWithText("seeded").assertExists()
        onNodeWithText(placeholder).assertDoesNotExist()
        clearButton().assertExists()
    }
}
