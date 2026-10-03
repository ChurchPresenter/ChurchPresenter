@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import org.churchpresenter.sharedui.testing.showsContainingText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BibleTabCopyVerseTest {

    @Test
    fun `Copy Verse closes the menu and leaves the selection on the verse`() = bibleTab { vm, reports ->
        onNodeWithText("2. And the earth was without form, and void.").performMouseInput { rightClick() }
        waitForIdle()

        val items = onAllNodesWithText("Copy Verse")
        items[items.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()

        assertFalse(showsContainingText("Copy Verse"), "the menu closes once the verse is copied")
        assertEquals(1, vm.selectedVerseIndex.value)
        assertEquals(emptyList(), reports.scheduled)
    }
}
