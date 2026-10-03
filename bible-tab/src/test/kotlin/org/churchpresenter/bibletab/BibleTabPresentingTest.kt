@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleTabPresentingTest {

    private fun splitBrowse(app: AppSettings) = app.copy(bibleSettings = app.bibleSettings.copy(splitBrowseMode = true))

    @Test
    fun `moving to another book while presenting holds the output`() {
        val output = FakeBibleOutput()
        bibleTab(presenter = output, isPresenting = true) { _, _ ->
            onNodeWithText("John").performClick()
            waitForIdle()

            assertTrue(output.bibleHold.value, "browsing away from what is live must not change the screen")
        }
    }

    @Test
    fun `moving to another book while not presenting leaves the output alone`() {
        val output = FakeBibleOutput()
        bibleTab(presenter = output) { _, _ ->
            onNodeWithText("John").performClick()
            waitForIdle()

            assertFalse(output.bibleHold.value)
        }
    }

    @Test
    fun `split browse never holds when moving away, the live panel keeps the output`() {
        val output = FakeBibleOutput()
        bibleTab(presenter = output, isPresenting = true, settings = ::splitBrowse) { _, _ ->
            onNodeWithText("John").performClick()
            waitForIdle()

            assertFalse(output.bibleHold.value)
        }
    }

    @Test
    fun `stepping to the next chapter while presenting does not hold`() {
        val output = FakeBibleOutput()
        bibleTab(presenter = output, isPresenting = true) { vm, _ ->
            runOnIdle { vm.navigateNextChapter() }
            waitForIdle()

            assertEquals(2, vm.selectedChapter.value)
            assertFalse(output.bibleHold.value, "reading on into the next chapter is not navigating away")
        }
    }

    @Test
    fun `clicking a verse while presenting sends it`() = bibleTab(isPresenting = true) { _, reports ->
        onNodeWithText("3. And God said, Let there be light.").performClick()
        waitForIdle()

        assertEquals(3, reports.live?.single()?.verseNumber)
    }

    @Test
    fun `building a passage while presenting does not send each verse as it is added`() =
        bibleTab(isPresenting = true) { vm, reports ->
            val before = reports.selectedVerses.size

            runOnIdle { vm.ctrlClickVerse(1) }
            waitForIdle()

            assertTrue(vm.multiVerseEnabled.value)
            assertEquals(before, reports.selectedVerses.size, "a passage goes out on Go Live, not piece by piece")
        }
}
