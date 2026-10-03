@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsContainingText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleTabSearchOptionsTest {

    @Test
    fun `a scope index out of range falls back to the whole Bible`() = bibleTab { vm, _ ->
        runOnIdle { vm.updateSelectedScopeIndex(7) }
        waitForIdle()

        assertTrue(showsContainingText("Entire Bible"), renderedText().toString())
    }

    @Test
    fun `a mode index out of range falls back to contains`() = bibleTab { vm, _ ->
        runOnIdle { vm.updateSelectedModeIndex(7) }
        waitForIdle()

        assertTrue(showsContainingText("Contains Phrase"), renderedText().toString())
    }

    @Test
    fun `the current book scope is shown once chosen`() = bibleTab { vm, _ ->
        runOnIdle { vm.updateSelectedScopeIndex(1) }
        waitForIdle()

        assertTrue(showsContainingText("Current Book"), renderedText().toString())
    }

    @Test
    fun `Enter in the search box goes to the reference typed`() = bibleTab { vm, _ ->
        bibleSearchBox().performTextReplacement("Psalms 23:1")
        waitForIdle()
        bibleSearchBox().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(1, vm.selectedBookIndex.value, "Psalms is the second book")
        assertEquals(23, vm.selectedChapter.value)
    }

    @Test
    fun `Enter on an empty search box leaves the browser where it was`() = bibleTab { vm, _ ->
        bibleSearchBox().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(0, vm.selectedBookIndex.value)
        assertEquals("", vm.searchQuery.value)
    }
}
