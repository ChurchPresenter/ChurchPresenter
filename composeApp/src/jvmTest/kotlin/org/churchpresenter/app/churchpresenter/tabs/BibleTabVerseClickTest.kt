@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.core.utils.keyDown

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleTabVerseClickTest {

    private val verseTwo = "2. And the earth was without form, and void."
    private val verseThree = "3. And God said, Let there be light."

    private fun ComposeUiTest.verse(text: String): SemanticsNodeInteraction = onNodeWithText(text)

    private fun ComposeUiTest.clickHolding(modifier: Key, text: String) {
        verse(text).performKeyInput { keyDown(modifier) }
        verse(text).performMouseInput { click() }
        verse(text).performKeyInput { keyUp(modifier) }
        waitForIdle()
    }

    @Test
    fun `ctrl-clicking a second verse selects both`() = bibleTab { vm, _ ->
        clickHolding(Key.CtrlLeft, verseTwo)

        assertTrue(vm.multiVerseEnabled.value)
        assertEquals(listOf(0, 1), vm.selectedVerseIndices.sorted())
    }

    @Test
    fun `ctrl-clicking a selected verse takes it out of the selection`() = bibleTab { vm, _ ->
        clickHolding(Key.CtrlLeft, verseTwo)
        assertEquals(listOf(0, 1), vm.selectedVerseIndices.sorted())

        clickHolding(Key.CtrlLeft, verseTwo)

        assertEquals(listOf(0), vm.selectedVerseIndices.sorted())
    }

    @Test
    fun `shift-clicking selects the run up to that verse`() = bibleTab { vm, _ ->
        clickHolding(Key.ShiftLeft, verseThree)

        assertEquals(listOf(0, 1, 2), vm.selectedVerseIndices.sorted())
        assertTrue(vm.multiVerseEnabled.value)
    }

    @Test
    fun `right-clicking a verse selects it without starting a multi-verse selection`() =
        bibleTab { vm, _ ->
            verse(verseThree).performMouseInput { rightClick() }
            waitForIdle()

            assertEquals(2, vm.selectedVerseIndex.value)
            assertFalse(vm.multiVerseEnabled.value)
        }

    @Test
    fun `a plain click replaces a multi-verse selection`() = bibleTab { vm, _ ->
        clickHolding(Key.CtrlLeft, verseTwo)
        assertTrue(vm.multiVerseEnabled.value)

        verse(verseThree).performMouseInput { click() }
        waitForIdle()

        assertFalse(vm.multiVerseEnabled.value)
        assertEquals(2, vm.selectedVerseIndex.value)
    }

    @Test
    fun `a ctrl-selected passage is handed to the host as one entry`() = bibleTab { _, reports ->
        clickHolding(Key.CtrlLeft, verseTwo)

        val live = reports.live
        assertEquals(1, live?.size, "the two verses are joined into one entry, not sent as two")
        assertEquals("1-2", live?.single()?.verseRange)
    }
}
