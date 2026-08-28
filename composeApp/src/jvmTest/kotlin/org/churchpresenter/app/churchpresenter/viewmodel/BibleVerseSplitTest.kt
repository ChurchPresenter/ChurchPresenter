package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleVerseSplitTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    /** 60 words, past the threshold. Numbered so a half can be named by the words at its edges. */
    private val longVerse = (1..60).joinToString(" ") { "word$it" }

    @BeforeTest
    fun loadBible() {
        dir = Files.createTempDirectory("cp-verse-split-test").toFile()
        SpbFixture.spbFile(dir, name = "test.spb", content = content())
        vm = viewModel(split = true)
        openChapter()
    }

    @AfterTest
    fun cleanUp() {
        vm.dispose()
        dir.deleteRecursively()
    }

    private fun content(): String = SpbFixture.buildContent(
        title = "Split Bible",
        books = listOf(SpbFixture.Book(17, "Esther", 1)),
        verses = listOf(
            SpbFixture.Verse(17, 1, 1, "Short verse one"),
            SpbFixture.Verse(17, 1, 2, longVerse),
            SpbFixture.Verse(17, 1, 3, "Short verse three"),
        ),
    )

    private fun viewModel(split: Boolean) = BibleViewModel(
        AppSettings(
            bibleSettings = BibleSettings(
                storageDirectory = dir.absolutePath,
                primaryBible = "test.spb",
                splitLongVerses = split,
            ),
        ),
    ).also {
        awaitUntil("books") { it.books.value.isNotEmpty() }
        awaitUntil("verses") { it.isFullyLoaded }
    }

    private fun openChapter(target: BibleViewModel = vm) {
        val token = target.verseSelectionToken.value
        target.loadChapter(0, 1)
        awaitUntil("chapter") { target.verseSelectionToken.value > token }
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun liveText(target: BibleViewModel = vm) = target.getSelectedVerses().first().verseText
    private fun firstWord(text: String) = text.substringBefore(' ')
    private fun lastWord(text: String) = text.substringAfterLast(' ')

    // ── The split itself ────────────────────────────────────────────────────────

    @Test
    fun `halves balance by character count, not word count`() {
        val (first, second) = splitAtWordMidpoint(longVerse)
        assertEquals(longVerse, "$first $second", "no word is lost or duplicated")
        assertTrue(
            kotlin.math.abs(first.length - second.length) <= lastWord(first).length,
            "halves within a word's length of each other: ${first.length} vs ${second.length}",
        )
    }

    @Test
    fun `a short verse is not long enough to split`() {
        assertFalse(isLongVerse("Jesus wept"))
        assertTrue(isLongVerse(longVerse))
    }

    @Test
    fun `text with no space at all stays whole`() {
        val (first, second) = splitAtWordMidpoint("Alleluia")
        assertEquals("Alleluia", first)
        assertEquals("", second)
    }

    // ── Paging ──────────────────────────────────────────────────────────────────

    @Test
    fun `a long verse goes live as its first half and steps to the second`() {
        vm.selectVerse(0)
        assertEquals("Short verse one", liveText(), "a short verse is untouched")

        vm.selectVerse(1)
        assertEquals("word1", firstWord(liveText()))
        val firstHalf = liveText()

        assertTrue(vm.navigateNextVerse(), "next steps within the verse")
        assertEquals("word60", lastWord(liveText()), "the second half runs to the end")
        assertEquals(1, vm.selectedVerseIndex.value, "and it is still the same verse")
        assertEquals(longVerse, "$firstHalf ${liveText()}", "the two halves are the whole verse")

        assertTrue(vm.navigateNextVerse(), "next again moves on")
        assertEquals("Short verse three", liveText())
    }

    @Test
    fun `moving back lands on the second half, then the first`() {
        vm.selectVerse(2)
        assertTrue(vm.navigatePreviousVerse())
        assertEquals("word60", lastWord(liveText()), "arriving backwards opens on the second half")
        assertTrue(vm.navigatePreviousVerse())
        assertEquals("word1", firstWord(liveText()), "then its first")
        assertTrue(vm.navigatePreviousVerse())
        assertEquals("Short verse one", liveText())
    }

    /** Regression: a verse left on its second half used to reopen there when it came round again. */
    @Test
    fun `arriving from the preceding verse always opens on the first half`() {
        vm.selectVerse(1)
        vm.navigateNextVerse()
        assertEquals("word60", lastWord(liveText()), "left on the second half")

        vm.selectVerse(0)
        assertTrue(vm.navigateNextVerse(), "down into the verse that was previewed earlier")
        assertEquals("word1", firstWord(liveText()), "opens on the first half, not where it was left")
    }

    @Test
    fun `re-selecting the verse it is already on resets to the first half`() {
        vm.selectVerse(1)
        vm.navigateNextVerse()
        vm.selectVerse(1)
        assertEquals("word1", firstWord(liveText()))
    }

    // ── Look-ahead and the operator's marker ────────────────────────────────────

    @Test
    fun `the look-ahead offers the other half before the next verse`() {
        vm.selectVerse(1)
        assertEquals("word60", lastWord(vm.getNextVerses().first().verseText))
        vm.navigateNextVerse()
        assertEquals("Short verse three", vm.getNextVerses().first().verseText)
    }

    @Test
    fun `the mark names the break and follows which half is live`() {
        vm.selectVerse(1)
        val mark = vm.liveVerseSplitMark(vm.getSelectedVerses().first())!!
        assertEquals(2, mark.verseNumber)
        assertFalse(mark.secondHalfLive)
        val line = vm.verses.value[1]
        assertEquals(lastWord(splitAtWordMidpoint(longVerse).first), lastWord(line.take(mark.breakOffset)))

        vm.navigateNextVerse()
        assertTrue(vm.liveVerseSplitMark(vm.getSelectedVerses().first())!!.secondHalfLive)
    }

    @Test
    fun `nothing is marked for a short verse or a whole one`() {
        vm.selectVerse(0)
        assertNull(vm.liveVerseSplitMark(vm.getSelectedVerses().first()), "short verse")
        val whole = vm.getSelectedVerses().first().copy(verseNumber = 2, verseText = longVerse)
        assertNull(vm.liveVerseSplitMark(whole), "a whole verse on screen is not half of one")
        assertNull(vm.liveVerseSplitMark(null))
    }

    // ── Off by default ──────────────────────────────────────────────────────────

    @Test
    fun `with the setting off the verse stays whole and next moves on`() {
        val off = viewModel(split = false)
        openChapter(off)
        off.selectVerse(1)
        assertEquals(longVerse, liveText(off))
        assertTrue(off.navigateNextVerse())
        assertEquals("Short verse three", liveText(off), "no half to stop at")
        off.dispose()
    }
}
