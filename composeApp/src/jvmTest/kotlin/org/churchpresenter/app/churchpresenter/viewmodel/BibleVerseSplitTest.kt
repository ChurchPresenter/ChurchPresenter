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

/** The setting's default; the suite's fixture verse is 60 words, on the far side of it. */
private const val DEFAULT_WORD_COUNT = 45

class BibleVerseSplitTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    /** 60 words, past the threshold. Numbered so a half can be named by the words at its edges. */
    private val longVerse = (1..60).joinToString(" ") { "word$it" }

    /**
     * 36 words -- the length of Esther 8:9 in Tamil, against 90 in the KJV.
     *
     * The verse the tunable threshold exists for: long on screen, short by word count, and left
     * whole by the English-shaped default of 45.
     */
    private val tamilLengthVerse = (1..36).joinToString(" ") { "sol$it" }

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
            SpbFixture.Verse(17, 1, 4, tamilLengthVerse),
        ),
    )

    private fun viewModel(split: Boolean, words: Int = DEFAULT_WORD_COUNT) = BibleViewModel(
        AppSettings(
            bibleSettings = BibleSettings(
                storageDirectory = dir.absolutePath,
                primaryBible = "test.spb",
                splitLongVerses = split,
                longVerseWordCount = words,
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
        assertFalse(isLongVerse("Jesus wept", DEFAULT_WORD_COUNT))
        assertTrue(isLongVerse(longVerse, DEFAULT_WORD_COUNT))
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


    // ── The threshold is the operator's, not a constant ─────────────────────────

    @Test
    fun `the threshold decides, so the same verse splits at one setting and not another`() {
        assertFalse(isLongVerse(tamilLengthVerse, DEFAULT_WORD_COUNT), "36 words is short at 45")
        assertTrue(isLongVerse(tamilLengthVerse, 30), "the same verse is long at 30")
        assertFalse(isLongVerse(tamilLengthVerse, 36), "the bound is exclusive: 36 words is not past 36")
        assertTrue(isLongVerse(tamilLengthVerse, 35), "and is past 35")
    }

    @Test
    fun `a threshold outside the slider's range is clamped rather than obeyed`() {
        assertFalse(isLongVerse("Jesus wept", 0), "0 must not split every verse in the Bible")
        assertTrue(isLongVerse(longVerse, 0), "clamped to the floor, not ignored")
        assertFalse(isLongVerse(longVerse, 500), "60 words is the ceiling, so 60 is what 500 means")
        assertTrue(isLongVerse((1..61).joinToString(" ") { "w$it" }, 500))
    }

    @Test
    fun `lowering the threshold splits a verse the default leaves whole`() {
        val whole = viewModel(split = true)
        openChapter(whole)
        whole.selectVerse(3)
        assertEquals(tamilLengthVerse, liveText(whole), "36 words is whole at the default of 45")
        whole.dispose()

        val split = viewModel(split = true, words = 30)
        openChapter(split)
        split.selectVerse(3)
        val (first, second) = splitAtWordMidpoint(tamilLengthVerse)
        assertEquals(first, liveText(split), "at 30 the same verse opens on its first half")
        assertTrue(split.navigateNextVerse())
        assertEquals(second, liveText(split), "and the next-verse key steps to the second")
        split.dispose()
    }

    // ── The one slider that carries both the on/off and the number ──────────────

    @Test
    fun `the handle sits at the stored threshold, and at Off when splitting is off`() {
        assertEquals(35, longVerseSliderPosition(splitting = true, wordCount = 35))
        assertEquals(LONG_VERSE_WORDS_OFF, longVerseSliderPosition(splitting = false, wordCount = 35))
        assertEquals(
            LONG_VERSE_WORDS_MIN,
            longVerseSliderPosition(splitting = true, wordCount = 5),
            "a stored value below the range still has to land on the track",
        )
        assertEquals(LONG_VERSE_WORDS_MAX, longVerseSliderPosition(splitting = true, wordCount = 900))
    }

    @Test
    fun `dropping the handle snaps to a stop and only the Off stop turns splitting off`() {
        assertEquals(true to 35, longVerseSliderStop(34.4f, currentWordCount = 45))
        assertEquals(true to 35, longVerseSliderStop(35.6f, currentWordCount = 45))
        assertEquals(true to LONG_VERSE_WORDS_MIN, longVerseSliderStop(24.9f, currentWordCount = 45))
        assertEquals(true to LONG_VERSE_WORDS_MAX, longVerseSliderStop(60f, currentWordCount = 45))
    }

    @Test
    fun `the Off stop keeps the tuned threshold rather than resetting it`() {
        val (splitting, words) = longVerseSliderStop(LONG_VERSE_WORDS_OFF.toFloat(), currentWordCount = 30)
        assertFalse(splitting)
        assertEquals(30, words, "coming back off Off must return the operator to 30, not to the default")
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
