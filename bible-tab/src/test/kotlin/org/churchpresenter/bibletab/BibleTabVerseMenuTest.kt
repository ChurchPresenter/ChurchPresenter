@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsContainingText
import org.churchpresenter.sharedui.testing.showsExactly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleTabVerseMenuTest {

    private val verse2 = "2. And the earth was without form, and void."
    private val verse3 = "3. And God said, Let there be light."

    private fun references() = CrossReferenceRepository {
        """{"v":1,"r":{"001001002":"019023001"}}""".toByteArray()
    }

    private fun docked(settings: AppSettings) =
        settings.copy(bibleSettings = settings.bibleSettings.copy(crossReferencesPanel = true))

    private fun ComposeUiTest.openMenuOn(verseLine: String) {
        onNodeWithText(verseLine).performMouseInput { rightClick() }
        waitForIdle()
    }

    private fun ComposeUiTest.clickMenuItem(label: String) {
        val nodes = onAllNodesWithText(label, substring = true)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.showsItem(label: String) = renderedText().any { it.startsWith(label) }

    @Test
    fun `the header names the verse that was right-clicked and the translation`() = bibleTab { _, _ ->
        openMenuOn(verse2)

        assertTrue(showsExactly("Genesis 1:2"), renderedText().toString())
        assertTrue(showsExactly("Test Bible"))
    }

    @Test
    fun `Go Live leads the menu, with its key`() = bibleTab { _, _ ->
        openMenuOn(verse3)

        val items = renderedText().filter { line -> MENU.any { line.startsWith(it) } }
        assertEquals("Go LiveEnter", items.first(), items.toString())
        assertTrue(showsItem("Add to ScheduleF2"))
        assertTrue(showsItem("Copy Verse"))
    }

    @Test
    fun `Go Live from the menu puts the right-clicked verse live and closes the menu`() =
        bibleTab { vm, reports ->
            openMenuOn(verse3)
            clickMenuItem("Go Live")

            assertEquals(2, vm.selectedVerseIndex.value)
            assertEquals(3, reports.live?.single()?.verseNumber, reports.live.toString())
            assertFalse(showsItem("Copy Verse"), "the menu closes")
        }

    @Test
    fun `Add to Schedule from the menu schedules the verse`() = bibleTab { _, reports ->
        openMenuOn(verse2)
        clickMenuItem("Add to Schedule")

        assertEquals(1, reports.scheduled.size, reports.scheduled.toString())
    }

    @Test
    fun `a verse without references offers no Cross References item`() =
        bibleTab(crossReferences = references()) { _, _ ->
            openMenuOn(verse3)

            assertFalse(showsItem("Cross References"), renderedText().toString())
        }

    @Test
    fun `a verse with references offers them, and the item opens the popover`() =
        bibleTab(crossReferences = references()) { _, _ ->
            openMenuOn(verse2)
            assertTrue(showsItem("Cross References"), renderedText().toString())

            clickMenuItem("Cross References")

            assertTrue(showsExactly("Esc to close"), "the popover is open: ${renderedText()}")
        }

    @Test
    fun `with the panel docked the menu leaves cross references to it`() =
        bibleTab(settings = ::docked, crossReferences = references()) { _, _ ->
            openMenuOn(verse2)

            assertTrue(showsContainingText("Copy Verse"))
            assertFalse(showsItem("Cross References"))
        }

    private companion object {
        val MENU = listOf("Go Live", "Add to Schedule", "Copy Verse", "Cross References")
    }
}
