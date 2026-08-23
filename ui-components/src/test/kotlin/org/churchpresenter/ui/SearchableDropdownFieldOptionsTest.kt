package org.churchpresenter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The presentation parameters of the searchable dropdown, as opposed to its search behaviour.
 *
 * The existing suite drives the search; every option below changes how the field or its menu is
 * *drawn*, and each is a separate branch. A caller that passes a custom `itemContent` and silently
 * gets the default row would look right in a screenshot of the closed field and wrong only once
 * opened.
 */
@OptIn(ExperimentalTestApi::class)
class SearchableDropdownFieldOptionsTest {

    private val options = listOf("Alpha", "Beta", "Gamma")

    @Test
    fun `a label is drawn above the field`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SearchableDropdownField("Alpha", options, {}, label = "Translation")
            }
        }
        onNodeWithText("TRANSLATION").assertIsDisplayed()
    }

    @Test
    fun `a leading icon is drawn beside the value`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SearchableDropdownField("Alpha", options, {}, leadingIcon = { Text("icon") })
            }
        }
        onNodeWithText("icon").assertIsDisplayed()
    }

    @Test
    fun `a custom item row is used for every option in the menu`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SearchableDropdownField("", options, {}, itemContent = { Text("row:$it") })
            }
        }
        onNode(hasSetTextAction()).performClick()
        waitForIdle()
        onNodeWithText("row:Alpha").assertIsDisplayed()
        onNodeWithText("row:Gamma").assertIsDisplayed()
    }

    @Test
    fun `fillWidth stretches the field inside a bounded parent`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.width(400.dp)) {
                    SearchableDropdownField("Alpha", options, {}, fillWidth = true)
                }
            }
        }
        onNodeWithText("Alpha").assertIsDisplayed()
    }

    @Test
    fun `a custom menu size still lists the options`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SearchableDropdownField("", options, {}, menuWidth = 320.dp, menuHeight = 120.dp)
            }
        }
        onNode(hasSetTextAction()).performClick()
        waitForIdle()
        onNodeWithText("Beta").assertIsDisplayed()
    }

    @Test
    fun `a custom value text style does not stop the value being shown`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SearchableDropdownField(
                    "Alpha", options, {},
                    valueTextStyle = MaterialTheme.typography.titleLarge,
                )
            }
        }
        onNodeWithText("Alpha").assertIsDisplayed()
    }

    @Test
    fun `a custom row is still what commits the value`() = runComposeUiTest {
        var picked = ""
        setContent {
            MaterialTheme {
                SearchableDropdownField("", options, { picked = it }, itemContent = { Text("row:$it") })
            }
        }
        onNode(hasSetTextAction()).performClick()
        waitForIdle()
        onNodeWithText("row:Beta").performClick()
        waitForIdle()
        assertEquals("Beta", picked)
    }

    @Test
    fun `an empty option list opens without a menu of rows`() = runComposeUiTest {
        setContent { MaterialTheme { SearchableDropdownField("", emptyList(), {}) } }
        onNode(hasSetTextAction()).performClick()
        waitForIdle()
        onNode(hasSetTextAction()).performTextInput("x")
        waitForIdle()
    }
}
