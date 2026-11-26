@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsExactly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleTabEmptyStatesTest {

    private fun ComposeUiTest.searchFor(query: String) {
        bibleSearch(query)
        actionButton(BibleLabel.SEARCH).performClick()
        waitForIdle()
    }

    @Test
    fun `with no primary Bible the tab sends the operator to its settings`() {
        var opened = 0
        bibleTab(
            settings = { it.copy(bibleSettings = BibleSettings(primaryBible = "")) },
            onOpenBibleSettings = { opened++ },
        ) { _, _ ->
            assertTrue(showsExactly("No Bible selected"), renderedText().take(20).toString())
            onNodeWithText("Open Bible Settings").performClick()
            waitForIdle()
            assertEquals(1, opened)
        }
    }

    @Test
    fun `a whole-word search that finds nothing offers to match inside words`() = bibleTab { vm, _ ->
        runOnIdle { vm.updateSelectedModeIndex(1) }
        searchFor("shepher")

        assertTrue(showsExactly("No verses match “shepher”"), renderedText().take(20).toString())
        assertTrue(showsExactly("Only whole words were matched. Check the spelling, or match inside words too."))

        onNodeWithText("Match inside words").performClick()
        waitUntil(timeoutMillis = 5_000) { vm.searchResults.value.isNotEmpty() }
        waitForIdle()

        assertEquals(0, vm.selectedModeIndex.value)
        assertFalse(showsExactly("Match inside words"))
    }

    @Test
    fun `Clear search leaves the no-match state`() = bibleTab { vm, _ ->
        searchFor("nothingmatchesthis")
        onNodeWithText("Clear search").performClick()
        waitForIdle()

        assertTrue(vm.searchQuery.value.isEmpty())
        assertFalse(showsExactly("No verses match “nothingmatchesthis”"))
    }
}
