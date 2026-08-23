package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reusable single-select list (songbooks, categories, translations, …).
 *
 * Every entry must render — a dropped item is an option the operator can never pick — and clicking
 * one must report that exact item back, since the caller acts on the string it receives.
 */
@OptIn(ExperimentalTestApi::class)
class SelectionListTest {

    private val items = listOf("Alpha", "Beta", "Gamma")

    @Test
    fun `every item in the list is rendered`() = runComposeUiTest {
        setContent { MaterialTheme { SelectionList(list = items, onItemSelected = {}) } }
        items.forEach { onNodeWithText(it, substring = true).assertExists("every option must be selectable") }
    }

    @Test
    fun `clicking an item reports that exact item`() = runComposeUiTest {
        var picked: String? = null
        setContent { MaterialTheme { SelectionList(list = items, onItemSelected = { picked = it }) } }
        onNodeWithText("Beta", substring = true).performClick()
        assertEquals("Beta", picked, "the callback must carry the clicked item, not a stale or wrong one")
    }
}
