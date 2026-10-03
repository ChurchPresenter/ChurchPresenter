package org.churchpresenter.bibletab

import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Bible tab before any Bible is loaded -- a first run, or a module that failed to open. Asking
 * what is selected, what comes next, or for a verse to be offered must answer with nothing rather
 * than fail.
 */
class BibleViewModelNoBibleTest {


    @Test
    fun `with no Bible nothing is selected and nothing comes next`() {
        val vm = BibleViewModel(AppSettings())
        assertTrue(vm.getSelectedVerses().isEmpty())
        assertTrue(vm.getNextVerses().isEmpty())
    }

    @Test
    fun `a verse offered with no Bible behind it carries its text and no names`() {
        val vm = BibleViewModel(AppSettings())
        val offered = vm.buildNextVerseList(bookId = 43, chapter = 3, verseNumber = 16, verseText = "For God so loved")
        assertEquals(1, offered.size)
        assertEquals("", offered.single().bookName)
        assertEquals("", offered.single().bibleName)
        assertEquals(16, offered.single().verseNumber)

        assertTrue(vm.buildNextVerseList(43, 3, 16, verseText = "").isEmpty(), "and nothing without text")
    }
}
