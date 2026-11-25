package org.churchpresenter.bibleeditor

import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.SpbFixture
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MissingVerseTest {

    private val dir: File = Files.createTempDirectory("cp-bible-editor-missing").toFile()

    @AfterTest
    fun delete() {
        dir.deleteRecursively()
    }

    private fun bible(verses: List<SpbFixture.Verse>): Bible = Bible().apply {
        val books = verses.map { it.book }.distinct().map { SpbFixture.Book(it, "Book $it", 150) }
        loadFromSpb(SpbFixture.spbFile(dir, "t.spb", SpbFixture.buildContent("Test", books, verses)).absolutePath)
    }

    private fun chapter(book: Int, chapter: Int, count: Int, skip: Set<Int> = emptySet()) =
        (1..count).filterNot { it in skip }.map { SpbFixture.Verse(book, chapter, it, "V$it") }

    @Test
    fun `a verse missing inside a chapter is found where it belongs`() {
        val check = checkMissingVerses(bible(chapter(PSALMS, 23, 6, skip = setOf(3)) + chapter(JOHN, 3, 36)))
        assertEquals(Numbering.KJV, check.numbering)
        assertEquals(listOf(MissingVerse(PSALMS, 23, 3)), check.missing)
    }

    @Test
    fun `verses missing after a chapter's last one are marked as at its end`() {
        val missing = findMissingVerses(bible(chapter(PSALMS, 23, 4) + chapter(JOHN, 3, 36)))
        assertEquals(listOf(5, 6).map { MissingVerse(PSALMS, 23, it, atChapterEnd = true) }, missing)
    }

    @Test
    fun `a chapter the Bible has no verse of is left out`() {
        assertEquals(emptyList(), findMissingVerses(bible(chapter(JOHN, 3, 36))))
    }

    @Test
    fun `a Bible is matched to the numbering it fits with the fewest issues`() {
        // Synodal: Romans 14 runs to 26 and 16 stops at 24, where the KJV has 23 and 27.
        val synodal = checkMissingVerses(bible(chapter(ROMANS, 14, 26) + chapter(ROMANS, 16, 24)))
        assertEquals(Numbering.SYNODAL, synodal.numbering)
        assertEquals(emptyList(), synodal.missing)
        val kjv = checkMissingVerses(bible(chapter(ROMANS, 14, 23) + chapter(ROMANS, 16, 27)))
        assertEquals(Numbering.KJV, kjv.numbering)
    }

    @Test
    fun `numberings that fit equally well go to the KJV`() {
        assertEquals(Numbering.KJV, checkMissingVerses(bible(chapter(JOHN, 3, 36))).numbering)
    }

    @Test
    fun `a book that fits another numbering better is checked against that one`() {
        // KJV-numbered Psalms, but a Daniel 3 of 33 verses as the Hebrew text numbers it: matched to
        // the KJV as a whole, with Daniel's three extra verses not taken for missing ones elsewhere.
        val verses = (3..6).flatMap { chapter(PSALMS, it, KJV_PSALM_3_TO_6[it - 3]) } + chapter(DANIEL, 3, 33)
        val check = checkMissingVerses(bible(verses))
        assertEquals(Numbering.KJV, check.numbering)
        assertEquals(emptyList(), check.missing)
    }

    @Test
    fun `the numbers the file shows are matched, never the codes an importer wrote`() {
        // Coded one psalm on, as a Septuagint import codes a Hebrew-numbered Psalter.
        val shifted = chapter(PSALMS, 23, 6).map { it.copy(codeChapter = 24) }
        assertEquals(emptyList(), findMissingVerses(bible(shifted + chapter(JOHN, 3, 36))))
    }

    @Test
    fun `books outside the 66 and verses numbered 0 are not checked`() {
        val verses = chapter(JOHN, 3, 36) + SpbFixture.Verse(JOHN, 3, 0, "Title") + chapter(TOBIT, 1, 5)
        assertEquals(emptyList(), findMissingVerses(bible(verses)))
    }

    private companion object {
        const val PSALMS = 19
        const val DANIEL = 27
        const val JOHN = 43
        const val ROMANS = 45
        const val TOBIT = 67
        val KJV_PSALM_3_TO_6 = listOf(8, 8, 12, 10)
    }
}
