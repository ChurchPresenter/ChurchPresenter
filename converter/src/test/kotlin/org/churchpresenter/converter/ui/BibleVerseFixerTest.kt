@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.converter.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Bible verse fixer: a Bible from settings or of the person's own, every verse on a line,
 * the book and chapter pinned above them, and edits, additions and removals saved to the file.
 *
 * The rows are 2 Corinthians 10:4-6 with verse 5 written as `V0050`, the shape of the real damage.
 */
class BibleVerseFixerTest {

    private fun spb(dir: File, name: String = "rst.spb"): File = File(dir, name).apply {
        writeText(
            "##Title:\tTest\n47\t2 Corinthians\t13\n-----\n" +
                "B047C010V004\t47\t10\t4\tFour\n" +
                "B047C010V0050\t47\t10\t5\tFive\n" +
                "B047C010V006\t47\t10\t6\tSix\n",
            Charsets.UTF_8,
        )
    }

    private fun ComposeUiTest.openFromSettings(file: File) {
        click(Strings.verseFixerSelectFile)
        click(file.name)
        awaitShowing(Strings.verseFixerFound(1, file.name))
    }

    private fun ComposeUiTest.press(description: String) {
        onAllNodesWithContentDescription(description).onFirst().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.save(file: File, expected: String) {
        click(Strings.verseFixerApply)
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { file.readText().contains(expected) }
    }

    @Test
    fun `a Bible from settings is opened, its damaged verse counted and the book named above the list`() =
        withTempDir("verse-fixer") { dir ->
            val file = spb(dir)
            runComposeUiTest {
                setConverterContent(FakePickers()) { BibleVerseFixerApp(bibleFiles = listOf(file)) }
                openFromSettings(file)
                assertTrue(isShowing(Strings.verseFixerHeader("2 Corinthians", "10")))
                assertTrue(isShowing("Four"))
                assertTrue(isShowing("Six"))
            }
        }

    @Test
    fun `a file of the person's own can be chosen`() = withTempDir("verse-fixer") { dir ->
        val file = spb(dir, "mine.spb")
        runComposeUiTest {
            setConverterContent(FakePickers()) { BibleVerseFixerApp(chooseFile = { file }) }
            click(Strings.verseFixerSelectFile)
            click(Strings.verseFixerChooseOwn)
            awaitShowing(Strings.verseFixerFound(1, "mine.spb"))
        }
    }

    @Test
    fun `use suggestion repairs the damaged id and saving keeps a backup`() = withTempDir("verse-fixer") { dir ->
        val file = spb(dir)
        val saved = mutableListOf<Int>()
        runComposeUiTest {
            setConverterContent(FakePickers()) { BibleVerseFixerApp(bibleFiles = listOf(file), onSaved = { saved += it }) }
            openFromSettings(file)
            click(Strings.verseFixerUseSuggestion, substring = true)
            save(file, "B047C010V005\t")
            awaitShowing(Strings.verseFixerNone)
        }
        assertFalse(file.readText().contains("V0050"))
        assertTrue(File(file.path + ".bak").readText().contains("V0050"))
        assertEquals(listOf(1), saved)
    }

    @Test
    fun `a verse's text can be changed`() = withTempDir("verse-fixer") { dir ->
        val file = spb(dir)
        runComposeUiTest {
            setConverterContent(FakePickers()) { BibleVerseFixerApp(bibleFiles = listOf(file)) }
            openFromSettings(file)
            onNode(hasSetTextAction() and hasText("Four")).performTextReplacement("Four, corrected")
            save(file, "Four, corrected")
        }
    }

    @Test
    fun `a verse can be added below a line and removed`() = withTempDir("verse-fixer") { dir ->
        val file = spb(dir)
        runComposeUiTest {
            setConverterContent(FakePickers()) { BibleVerseFixerApp(bibleFiles = listOf(file)) }
            openFromSettings(file)
            press(Strings.verseFixerAdd)
            assertTrue(isShowing(Strings.verseFixerNew))
            onNode(hasSetTextAction() and hasText("B047C010V005")).assertExists()
            onNode(hasSetTextAction() and hasText("10:5")).assertExists()
            val newText = onAllNodesWithContentDescription(Strings.verseFixerRemove)
            assertTrue(newText.fetchSemanticsNodes().size == 4)
            onNode(hasSetTextAction() and hasText("")).performTextReplacement("Added verse")
            save(file, "Added verse")
        }
        val ids = file.readLines().filter { it.startsWith("B047") }.map { it.substringBefore('\t') }
        assertEquals(listOf("B047C010V004", "B047C010V005", "B047C010V0050", "B047C010V006"), ids)
    }

    @Test
    fun `a removed verse is gone from the file`() = withTempDir("verse-fixer") { dir ->
        val file = spb(dir)
        runComposeUiTest {
            setConverterContent(FakePickers()) { BibleVerseFixerApp(bibleFiles = listOf(file)) }
            openFromSettings(file)
            press(Strings.verseFixerRemove)
            save(file, "B047C010V0050")
            waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !file.readText().contains("Four") }
        }
        assertEquals(2, file.readLines().count { it.startsWith("B047") })
    }

    @Test
    fun `discard drops the changes and save stays off until there is one`() = withTempDir("verse-fixer") { dir ->
        val file = spb(dir)
        runComposeUiTest {
            setConverterContent(FakePickers()) { BibleVerseFixerApp(bibleFiles = listOf(file)) }
            openFromSettings(file)
            press(Strings.verseFixerRemove)
            assertFalse(isShowing("Four"))
            click(Strings.verseFixerDiscard)
            awaitShowing("Four")
        }
        assertFalse(File(file.path + ".bak").exists())
    }

    @Test
    fun `next damaged and only damaged keep the damaged verse in view`() = withTempDir("verse-fixer") { dir ->
        val file = spb(dir)
        runComposeUiTest {
            setConverterContent(FakePickers()) { BibleVerseFixerApp(bibleFiles = listOf(file)) }
            openFromSettings(file)
            click(Strings.verseFixerNextDamaged)
            click(Strings.verseFixerShowDamaged)
            assertTrue(isShowing("Five"))
            click(Strings.verseFixerShowAll)
            assertTrue(isShowing("Six"))
        }
    }

    @Test
    fun `a Bible with nothing damaged says so and can still be edited`() = withTempDir("verse-fixer") { dir ->
        val file = spb(dir).apply { writeText(readText().replace("V0050", "V005")) }
        runComposeUiTest {
            setConverterContent(FakePickers()) { BibleVerseFixerApp(initialFile = file) }
            awaitShowing(Strings.verseFixerNone)
            assertTrue(isShowing("Five"))
        }
    }
}
