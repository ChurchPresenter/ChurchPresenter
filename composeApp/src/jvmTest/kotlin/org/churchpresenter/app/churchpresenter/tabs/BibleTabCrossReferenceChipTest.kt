@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.data.CrossReferenceRepository
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleTabCrossReferenceChipTest {

    private fun references() = CrossReferenceRepository {
        """{"v":1,"r":{
             "001001001":"043003016 019023001",
             "001001002":"019023001"
           }}""".toByteArray()
    }

    private fun withPanel(settings: AppSettings) =
        settings.copy(bibleSettings = settings.bibleSettings.copy(crossReferencesPanel = true))

    private fun turnedOff(settings: AppSettings) =
        settings.copy(bibleSettings = settings.bibleSettings.copy(crossReferencesEnabled = false))

    private fun turnedOffWhileDocked(settings: AppSettings) =
        settings.copy(
            bibleSettings = settings.bibleSettings.copy(
                crossReferencesEnabled = false,
                crossReferencesPanel = true,
            )
        )

    private fun chip(count: Int) = "Cross references: $count"

    private fun ComposeUiTest.chipNames(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("") }
            .filter { it.startsWith("Cross references: ") }

    private fun ComposeUiTest.popoverIsOpen() = showsExactly("Esc to close")

    private fun ComposeUiTest.openChipOn(verseLine: String) {
        onNodeWithText(verseLine).performClick()
        waitForIdle()
        actionButton(chip(if (verseLine.startsWith("1.")) 2 else 1)).performClick()
        waitForIdle()
    }

    @Test
    fun `only verses with references are chipped, and the chip counts them`() =
        bibleTab(crossReferences = references()) { _, _ ->
            assertEquals(listOf(chip(2), chip(1)), chipNames(), "Genesis 1:3 has nothing to offer")
        }

    @Test
    fun `chips are drawn without the panel being docked`() =
        bibleTab(crossReferences = references()) { _, _ ->
            assertFalse(hasActionButton(BibleLabel.CROSS_REFS_CLOSE), "the panel is off")
            assertTrue(hasActionButton(chip(2)))
        }

    @Test
    fun `the setting off leaves no chips and no Refs button`() =
        bibleTab(settings = ::turnedOff, crossReferences = references()) { _, _ ->
            assertEquals(emptyList(), chipNames())
            assertFalse(hasActionButton(BibleLabel.CROSS_REFS_TOGGLE))
        }

    @Test
    fun `the setting off hides a panel that was left docked`() =
        bibleTab(settings = ::turnedOffWhileDocked, crossReferences = references()) { _, _ ->
            assertFalse(hasActionButton(BibleLabel.CROSS_REFS_CLOSE))
            assertFalse(showsExactly("John 3:16"))
            assertEquals(emptyList(), chipNames())
        }

    @Test
    fun `the chip opens a popover listing that verse's references in full`() =
        bibleTab(crossReferences = references()) { _, _ ->
            assertFalse(popoverIsOpen())

            openChipOn("1. In the beginning God created the heaven and the earth.")

            assertTrue(popoverIsOpen())
            assertTrue(showsExactly("John 3:16"))
            assertTrue(showsExactly("For God so loved the world."), "the verse is not truncated")
            assertTrue(showsExactly("Psa 23:1"))
        }

    @Test
    fun `the popover describes the verse its chip belongs to`() =
        bibleTab(crossReferences = references()) { _, _ ->
            openChipOn("2. And the earth was without form, and void.")

            assertTrue(showsExactly("Gen 1:2 · cross references: 1"))
            assertTrue(showsExactly("Psa 23:1"))
            assertFalse(showsExactly("John 3:16"), "that is verse 1's reference, not this one's")
        }

    @Test
    fun `clicking the same chip again closes the popover`() =
        bibleTab(crossReferences = references()) { _, _ ->
            openChipOn("1. In the beginning God created the heaven and the earth.")
            assertTrue(popoverIsOpen())

            actionButton(chip(2)).performClick()
            waitForIdle()

            assertFalse(popoverIsOpen())
        }

    @Test
    fun `following a reference from the popover navigates and retires it`() =
        bibleTab(crossReferences = references()) { vm, reports ->
            openChipOn("1. In the beginning God created the heaven and the earth.")

            onNodeWithText("John 3:16").performClick()
            waitForIdle()

            assertEquals(2, vm.selectedBookIndex.value, "John is the third book of the fixture")
            assertEquals(3, vm.selectedChapter.value)
            assertFalse(popoverIsOpen(), "it described a verse we have now left")
            assertFalse(reports.presenting.contains(Presenting.BIBLE), "a single click is navigation")
        }

    @Test
    fun `keep open promotes the popover to the docked panel`() =
        bibleTab(crossReferences = references()) { _, reports ->
            openChipOn("1. In the beginning God created the heaven and the earth.")

            actionButton(BibleLabel.CROSS_REFS_KEEP_OPEN).performClick()
            waitForIdle()

            assertTrue(reports.settingsAfterChange!!.bibleSettings.crossReferencesPanel)
            assertTrue(hasActionButton(BibleLabel.CROSS_REFS_CLOSE), "the panel took its place")
            assertFalse(popoverIsOpen(), "the same list twice would be two answers to one question")
        }

    @Test
    fun `a chip only selects the verse while the panel is docked`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { vm, _ ->
            openChipOn("2. And the earth was without form, and void.")

            assertFalse(popoverIsOpen(), "the docked panel is already answering")
            assertEquals(1, vm.selectedVerseIndex.value, "the chip still moved the selection")
            assertTrue(showsExactly("Psa 23:1"), "so the panel describes that verse")
        }

    @Test
    fun `a card queues its reference without moving the browse selection`() =
        bibleTab(crossReferences = references()) { vm, reports ->
            openChipOn("1. In the beginning God created the heaven and the earth.")

            actionButton("Add to Schedule John 3:16").performClick()
            waitForIdle()

            assertEquals(listOf("John 3:16"), reports.scheduled)
            assertEquals(0, vm.selectedBookIndex.value, "queueing is not navigating")
            assertEquals(1, vm.selectedChapter.value)
        }

    @Test
    fun `the docked panel queues a reference too`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { vm, reports ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()

            actionButton("Add to Schedule Psa 23:1").performClick()
            waitForIdle()

            assertEquals(listOf("Psalms 23:1"), reports.scheduled)
            assertEquals(0, vm.selectedBookIndex.value)
        }

    @Test
    fun `a reference this module does not carry is listed but has nothing to queue`() {
        val references = CrossReferenceRepository {
            """{"v":1,"r":{"001001001":"035003002 043003016"}}""".toByteArray()
        }

        bibleTab(crossReferences = references) { vm, reports ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()
            actionButton(chip(2)).performClick()
            waitForIdle()

            assertTrue(showsExactly("Hab 3:2"), "the reference is still listed")
            assertFalse(hasActionButton("Add to Schedule Hab 3:2"), "there is nothing to queue")
            assertTrue(hasActionButton("Add to Schedule John 3:16"), "the one beside it still works")

            onNodeWithText("Hab 3:2").performClick()
            waitForIdle()
            assertEquals(0, vm.selectedBookIndex.value, "clicking it must not move the selection")
            assertTrue(reports.scheduled.isEmpty())
        }
    }

    @Test
    fun `chips follow the chapter being browsed`() {
        val references = CrossReferenceRepository {
            """{"v":1,"r":{"001001001":"019023001","043003016":"019023001 001001001"}}""".toByteArray()
        }

        bibleTab(crossReferences = references) { vm, _ ->
            assertEquals(listOf(chip(1)), chipNames(), "Genesis 1:1 alone")

            vm.selectBook(2)
            vm.selectChapter(3)
            waitForIdle()

            assertEquals(listOf(chip(2)), chipNames(), "now John 3:16's own count")
        }
    }

    @Test
    fun `the Refs button docks and undocks the panel`() =
        bibleTab(crossReferences = references()) { _, reports ->
            actionButton(BibleLabel.CROSS_REFS_TOGGLE).performClick()
            waitForIdle()

            assertTrue(reports.settingsAfterChange!!.bibleSettings.crossReferencesPanel)
            assertTrue(hasActionButton(BibleLabel.CROSS_REFS_CLOSE))

            actionButton(BibleLabel.CROSS_REFS_TOGGLE).performClick()
            waitForIdle()

            assertFalse(reports.settingsAfterChange!!.bibleSettings.crossReferencesPanel)
            assertFalse(hasActionButton(BibleLabel.CROSS_REFS_CLOSE))
        }

    @Test
    fun `the panel's close button undocks it`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { _, reports ->
            actionButton(BibleLabel.CROSS_REFS_CLOSE).performClick()
            waitForIdle()

            assertFalse(reports.settingsAfterChange!!.bibleSettings.crossReferencesPanel)
            assertFalse(hasActionButton(BibleLabel.CROSS_REFS_CLOSE))
        }

    @Test
    fun `docking closes an open popover`() =
        bibleTab(crossReferences = references()) { _, _ ->
            openChipOn("1. In the beginning God created the heaven and the earth.")
            assertTrue(popoverIsOpen())

            actionButton(BibleLabel.CROSS_REFS_TOGGLE).performClick()
            waitForIdle()

            assertFalse(popoverIsOpen())
        }
}
