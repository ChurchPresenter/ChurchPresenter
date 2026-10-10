@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.converter.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuplicateFinderTabTest {

    private fun song(dir: File, name: String, title: String, vararg lyrics: String): File = File(dir, "$name.song").apply {
        parentFile.mkdirs()
        writeText(
            "[Primary]\ntitle: $title\n\n[Verse 1]\n${lyrics.joinToString("\n")}\n",
            Charsets.UTF_8,
        )
    }

    private fun library(dir: File) {
        song(File(dir, "A"), "grace", "Amazing Grace", "Amazing grace how sweet the sound", "That saved a wretch")
        song(File(dir, "B"), "grace", "Amazing Grace", "Amazing grace how sweet the sound", "That saved a wretch")
        song(File(dir, "B"), "other", "Something Else", "Completely different words")
    }

    @Test
    fun `a scan groups the duplicates, and the marked copy is deleted after confirming`() = withTempDir("dupes-delete") { dir ->
        library(dir)
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { DuplicateFinderTab() }

            assertTrue(isShowing(Strings.dupesEmptyState))
            click(Strings.selectFolder)
            assertTrue(isShowing(dir.absolutePath))
            click(Strings.scanForDuplicates)
            awaitShowing(Strings.groupSummary(1, 2, 3))
            assertTrue(isShowing(Strings.showingGroups(1, 1)))
            assertTrue(isShowing(Strings.groupHeader(1, "Amazing Grace")))
            assertTrue(isShowing(Strings.selectHint))

            click(Strings.expandAll)
            assertTrue(isShowing(Strings.collapseAll))
            assertTrue(isShowing(Strings.titlePrefix("Amazing Grace"), substring = true))

            onAllNodes(isToggleable()).onLast().performClick()
            waitForIdle()
            assertTrue(isShowing(Strings.labelDelete))
            click(Strings.deleteNSelected(1))
            assertTrue(isShowing(Strings.permanentlyDelete(1)))
            click(Strings.cancel)
            click(Strings.deleteNSelected(1))
            click(Strings.delete)
            awaitShowing(Strings.doneDeleted(1, 0))
            assertFalse(File(dir, "B/grace.song").exists())
            assertTrue(File(dir, "A/grace.song").exists())

            click(Strings.rescan)
            awaitShowing(Strings.noDupesFound(2))
        }
    }

    @Test
    fun `choosing a folder to keep marks the copies outside it`() = withTempDir("dupes-keep") { dir ->
        library(dir)
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { DuplicateFinderTab() }
            click(Strings.selectFolder)
            click(Strings.scanForDuplicates)
            awaitShowing(Strings.groupSummary(1, 2, 3))

            click(Strings.keepFolder)
            click("A")
            awaitShowing(Strings.deleteNSelected(1))
            click(Strings.expandAll)
            assertTrue(isShowing(Strings.labelKeep))
            click(Strings.deleteNSelected(1))
            assertTrue(isShowing(Strings.keepFolderPrefix(File(dir, "A").absolutePath)))
            click(Strings.cancel)

            click(Strings.groupHeader(1, "Amazing Grace"))
            click(Strings.scanAgain)
            assertTrue(isShowing(Strings.scanForDuplicates))
        }
    }

    @Test
    fun `copies in the same folder are marked together`() = withTempDir("dupes-same") { dir ->
        song(dir, "one", "Joy", "Joy to the world the Lord is come")
        song(dir, "two", "Joy", "Joy to the world the Lord is come")
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { DuplicateFinderTab() }
            click(Strings.selectFolder)
            click(Strings.scanForDuplicates)
            awaitShowing(Strings.groupSummary(1, 2, 2))
            click(Strings.selectSameFolder)
            awaitShowing(Strings.deleteNSelected(1))
        }
    }

    @Test
    fun `filters narrow the groups and can be cleared`() = withTempDir("dupes-filter") { dir ->
        library(dir)
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { DuplicateFinderTab() }
            click(Strings.matchByNumber)
            click(Strings.matchByTitle)
            click(Strings.matchByTitle)
            click(Strings.selectFolder)
            click(Strings.scanForDuplicates)
            awaitShowing(Strings.showingGroups(1, 1))

            scrollAndClick(Strings.catSameTitle)
            scrollAndClick(Strings.catSimilarLyrics)
            scrollAndClick(Strings.catSameNumber)
            assertTrue(isShowing(Strings.showingGroups(0, 1)))
            scrollAndClick(Strings.clearFilters)
            assertTrue(isShowing(Strings.showingGroups(1, 1)))

            onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("3")
            waitForIdle()
            assertTrue(isShowing(Strings.showingGroups(0, 1)))
            onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("1")
            onAllNodes(hasSetTextAction()).onLast().performTextReplacement("5")
            waitForIdle()
            scrollAndClick(Strings.clearFilters)
            assertTrue(isShowing(Strings.showingGroups(1, 1)))
        }
    }

    @Test
    fun `look-alike letters found before a scan can be fixed or skipped`() = withTempDir("dupes-homoglyph") { dir ->
        val mixed = song(dir, "mixed", "Слава", "Славa Богу")
        song(dir, "plain", "Other", "words")
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { DuplicateFinderTab() }
            click(Strings.selectFolder)
            click(Strings.scanForDuplicates)
            awaitShowing(Strings.homoglyphDialogTitle)
            click(Strings.skipAndScan)
            awaitShowing(Strings.noDupesFound(2))
            assertTrue(mixed.readText().contains("Славa"))

            click(Strings.findHomoglyphs)
            awaitShowing(Strings.filesWithHomoglyphs(1))
            assertTrue(isShowing("mixed.song"))
            click(Strings.fixNFiles(1))
            awaitShowing(Strings.doneFixed(1, 0))
            assertTrue(mixed.readText().contains("Слава Богу"))

            click(Strings.findHomoglyphs)
            awaitShowing(Strings.noHomoglyphs)

            click(Strings.scanAgain)
            mixed.writeText("[Primary]\ntitle: Слава\n\n[Verse 1]\nСлавa Богу\n")
            click(Strings.scanForDuplicates)
            awaitShowing(Strings.fixAndScan)
            click(Strings.fixAndScan)
            awaitShowing(Strings.noDupesFound(2))
            assertTrue(mixed.readText().contains("Слава Богу"))
        }
    }

    @Test
    fun `control characters are found and cleaned`() = withTempDir("dupes-control") { dir ->
        val dirty = song(dir, "dirty", "Dirty", "a\u000Bline")
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { DuplicateFinderTab() }
            click(Strings.selectFolder)
            click(Strings.scanForDuplicates)
            awaitShowing(Strings.noDupesFound(1))

            click(Strings.findControlChars)
            awaitShowing(Strings.filesWithControlChars(1))
            click(Strings.fixNFiles(1))
            awaitShowing(Strings.doneFixed(1, 0))
            assertFalse(dirty.readBytes().contains(0x0B.toByte()))

            click(Strings.findControlChars)
            awaitShowing(Strings.noControlChars)
        }
    }

    @Test
    fun `more than five flagged files are summarized`() = withTempDir("dupes-many") { dir ->
        repeat(6) { song(dir, "dirty$it", "Dirty $it", "line\u0000$it") }
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { DuplicateFinderTab() }
            click(Strings.selectFolder)
            click(Strings.scanForDuplicates)
            awaitShowing(Strings.scanAgain)
            click(Strings.findControlChars)
            awaitShowing(Strings.andNMore(1))
            assertEquals(6, dir.listFiles()!!.size)
        }
    }
}
