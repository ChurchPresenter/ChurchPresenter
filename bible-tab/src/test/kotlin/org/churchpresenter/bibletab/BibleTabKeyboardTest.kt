@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import kotlin.test.Test
import kotlin.test.assertEquals

class BibleTabKeyboardTest {

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun `pressing down moves the browse selection to the next verse`() = bibleTab { vm, _ ->
        press(Key.DirectionDown)

        assertEquals(1, vm.selectedVerseIndex.value)
    }

    @Test
    fun `pressing up moves the browse selection back`() = bibleTab { vm, _ ->
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        assertEquals(2, vm.selectedVerseIndex.value)

        press(Key.DirectionUp)

        assertEquals(1, vm.selectedVerseIndex.value)
    }

    @Test
    fun `pressing up on the first verse of a chapter stays put`() = bibleTab { vm, _ ->
        press(Key.DirectionUp)

        assertEquals(0, vm.selectedVerseIndex.value)
        assertEquals(1, vm.selectedChapter.value)
    }

    @Test
    fun `pressing down past the last verse rolls into the next chapter`() = bibleTab { vm, _ ->
        repeat(3) { press(Key.DirectionDown) }

        assertEquals(2, vm.selectedChapter.value, "Genesis 1 has three verses in the fixture")
        assertEquals(0, vm.selectedVerseIndex.value, "and it lands on the first verse of chapter 2")
    }

    @Test
    fun `the right arrow opens the next chapter`() = bibleTab { vm, _ ->
        press(Key.DirectionRight)

        assertEquals(2, vm.selectedChapter.value)
    }

    @Test
    fun `the left arrow opens the previous chapter`() = bibleTab { vm, _ ->
        vm.loadChapter(0, 2)
        waitForIdle()

        press(Key.DirectionLeft)

        assertEquals(1, vm.selectedChapter.value)
    }

    @Test
    fun `the left arrow on the first chapter stays put`() = bibleTab { vm, _ ->
        press(Key.DirectionLeft)

        assertEquals(1, vm.selectedChapter.value)
    }

    @Test
    fun `moving to a chapter lands on its first verse`() = bibleTab { vm, _ ->
        press(Key.DirectionDown)
        assertEquals(1, vm.selectedVerseIndex.value)

        press(Key.DirectionRight)

        assertEquals(0, vm.selectedVerseIndex.value)
    }

    @Test
    fun `a key that is not bound to anything is left alone`() = bibleTab { vm, _ ->
        press(Key.F12)

        assertEquals(0, vm.selectedVerseIndex.value)
        assertEquals(1, vm.selectedChapter.value)
    }

    @Test
    fun `arrow keys are ignored while the search field has focus`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        waitForIdle()

        press(Key.DirectionDown)

        assertEquals(0, vm.selectedVerseIndex.value, "a focused search field owns its own arrow keys")
    }

    @Test
    fun `chapter keys are ignored while the search field has focus`() = bibleTab { vm, _ ->
        bibleSearchBox().requestFocus()
        waitForIdle()

        press(Key.DirectionRight)

        assertEquals(1, vm.selectedChapter.value)
    }

    @Test
    fun `each verse reached with the keyboard is handed to the host`() = bibleTab { _, reports ->
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        assertEquals(
            3,
            reports.live?.single()?.verseNumber,
            "the preview follows the browse selection without waiting for Go Live",
        )
    }
}
