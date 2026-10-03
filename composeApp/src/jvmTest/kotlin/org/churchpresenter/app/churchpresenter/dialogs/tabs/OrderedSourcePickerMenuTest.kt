@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderedSourcePickerMenuTest {

    private val strings = OrderedSourceStrings(
        label = "Bibles",
        offLabel = "Off",
        countFormat = "count",
        noneLoaded = "Nothing loaded",
        orderHeader = "Order",
        addHeader = "Add",
    )
    private val tags = OrderedSourceTags(trigger = "trigger", orderRow = { "order$it" }, addRow = { "add$it" })
    private val items = listOf(
        TranslationChoiceDisplay("KJV", "King James", "Full"),
        TranslationChoiceDisplay("", "Unnamed", "NT"),
        TranslationChoiceDisplay("RST", "Synodal", "Full"),
    )

    private fun picker(
        initialItems: List<TranslationChoiceDisplay>,
        initialShown: List<Int>,
        showRowCode: Boolean = true,
        body: ComposeUiTest.(shown: () -> List<Int>, setItems: (List<TranslationChoiceDisplay>) -> Unit) -> Unit,
    ) = runComposeUiTest {
        var current by mutableStateOf(initialShown)
        var list by mutableStateOf(initialItems)
        setContent {
            MaterialTheme {
                OrderedSourcePicker(list, current, strings, tags, onWrite = { current = it }, showRowCode = showRowCode)
            }
        }
        waitForIdle()
        onNodeWithTag("trigger").performClick()
        waitForIdle()
        body({ current }, { list = it })
    }

    private fun ComposeUiTest.has(text: String) =
        onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.key(row: String, description: String) =
        onAllNodes(hasContentDescription(description), useUnmergedTree = true)[row.removePrefix("order").toInt()]

    @Test
    fun `moving rows up and down, removing one and adding another rewrites the order`() =
        picker(items, listOf(0, 2)) { shown, _ ->
            key("order0", "Move down").performClick()
            waitForIdle()
            assertEquals(listOf(2, 0), shown())

            key("order1", "Move up").performClick()
            waitForIdle()
            assertEquals(listOf(0, 2), shown())

            key("order1", "Remove translation").performClick()
            waitForIdle()
            assertEquals(listOf(0), shown())

            onAllNodes(hasTestTag("add1"), useUnmergedTree = true)[0].performClick()
            waitForIdle()
            assertEquals(listOf(0, 1), shown())
        }

    @Test
    fun `a position past the end of the list is skipped, and a blank code shows only the title`() =
        picker(items, listOf(1, 7)) { _, _ ->
            assertTrue(has("Unnamed"))
            assertTrue(onAllNodes(hasTestTag("order1"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `codes left off the rows when the slot number stands for them`() =
        picker(items, listOf(0), showRowCode = false) { _, _ ->
            assertTrue(has("King James"))
        }

    @Test
    fun `nothing to pick from says so`() = picker(emptyList(), emptyList()) { _, _ ->
        assertTrue(has("Nothing loaded"))
    }

    @Test
    fun `every item drawn leaves nothing to add, and new items appear in the open menu`() =
        picker(items, listOf(0, 1, 2)) { _, setItems ->
            assertTrue(!has("Add"))
            setItems(items + TranslationChoiceDisplay("LSG", "Segond", "Full"))
            waitForIdle()
            assertTrue(has("Segond"))
        }
}
