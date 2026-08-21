@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals

class BibleTabRemoteSelectionTest {

    @Test
    fun `a matching verse item navigates the browser to it`() = bibleTab(
        selectedVerseItem = ScheduleItem.BibleVerseItem(
            id = "1", bookName = "John", chapter = 3, verseNumber = 16, verseText = "v", bookId = 43,
        ),
    ) { vm, _ ->
        waitForIdle()
        assertEquals(2, vm.selectedBookIndex.value, "John is the third configured book")
        assertEquals(3, vm.selectedChapter.value)
        assertEquals(0, vm.selectedVerseIndex.value)
    }

    @Test
    fun `a verse item resolves by book name even without a bookId`() = bibleTab(
        selectedVerseItem = ScheduleItem.BibleVerseItem(
            id = "1", bookName = "Psalms", chapter = 23, verseNumber = 1, verseText = "v",
        ),
    ) { vm, _ ->
        waitForIdle()
        assertEquals(1, vm.selectedBookIndex.value, "Psalms is the second configured book")
        assertEquals(23, vm.selectedChapter.value)
    }

    @Test
    fun `a verse item for a book that doesn't exist leaves the default selection alone`() = bibleTab(
        selectedVerseItem = ScheduleItem.BibleVerseItem(
            id = "1", bookName = "Acts", chapter = 1, verseNumber = 1, verseText = "v",
        ),
    ) { vm, _ ->
        waitForIdle()
        assertEquals(0, vm.selectedBookIndex.value, "the tab opens on the first book by default")
        assertEquals(1, vm.selectedChapter.value)
    }

    @Test
    fun `no verse item at all leaves the tab on its normal opening selection`() = bibleTab { vm, _ ->
        waitForIdle()
        assertEquals(0, vm.selectedBookIndex.value)
        assertEquals(1, vm.selectedChapter.value)
    }
}
