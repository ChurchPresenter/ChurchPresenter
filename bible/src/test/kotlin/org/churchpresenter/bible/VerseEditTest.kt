package org.churchpresenter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class VerseEditTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-verse-edit").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    /** Genesis 1 with verse 2 missing, and Psalm 23 numbered one behind its codes. */
    private fun bible(): Bible = SpbFixture.loadedBible(
        dir,
        SpbFixture.buildContent(
            "Gaps",
            listOf(SpbFixture.Book(1, "Genesis", 1), SpbFixture.Book(19, "Psalms", 23)),
            listOf(
                SpbFixture.Verse(1, 1, 1, "In the beginning."),
                SpbFixture.Verse(1, 1, 3, "Let there be light."),
                SpbFixture.Verse(19, 23, 2, "The LORD is my shepherd.", codeVerse = 3),
                SpbFixture.Verse(19, 23, 3, "He maketh me to lie down.", codeVerse = 4),
            ),
        ),
    )

    private fun Bible.texts(book: Int, chapter: Int) =
        getChapterVerses(book, chapter).map { it.verseNumber to it.verseText }

    @Test
    fun `an edit replaces the verse text`() {
        val b = bible()
        b.applyVerseEdits(listOf(VerseEdit(1, 1, 3, "And God said, Let there be light.")))
        assertEquals(listOf(1 to "In the beginning.", 3 to "And God said, Let there be light."), b.texts(1, 1))
    }

    @Test
    fun `a null text takes the verse out`() {
        val b = bible()
        b.applyVerseEdits(listOf(VerseEdit(1, 1, 1, null)))
        assertEquals(listOf(3 to "Let there be light."), b.texts(1, 1))
    }

    @Test
    fun `an added verse goes in its place, coded like its neighbour`() {
        val b = bible()
        b.applyVerseEdits(listOf(VerseEdit(1, 1, 2, "And the earth was without form.")))
        assertEquals(listOf(1, 2, 3), b.getChapterVerses(1, 1).map { it.verseNumber })
        assertEquals("B001C001V002", b.getChapterVerses(1, 1)[1].verseId)
        assertEquals("And the earth was without form.", b.getVerseDetailsByCode(1, 1, 2)?.verseText)
    }

    @Test
    fun `a verse added before the first takes the first one's offset`() {
        val b = bible()
        b.applyVerseEdits(listOf(VerseEdit(19, 23, 1, "A Psalm of David.")))
        val verses = b.getChapterVerses(19, 23)
        assertEquals(listOf(1, 2, 3), verses.map { it.verseNumber })
        assertEquals("B019C023V002", verses.first().verseId)
    }

    @Test
    fun `a verse added to a chapter the module has nothing of is left out`() {
        val b = bible()
        b.applyVerseEdits(listOf(VerseEdit(1, 2, 1, "Thus the heavens.")))
        assertEquals(emptyList(), b.getChapterVerses(1, 2))
    }

    @Test
    fun `no edits leaves the loaded verses as they were`() {
        val b = bible()
        val before = b.getChapterVerses(1, 1)
        b.applyVerseEdits(emptyList())
        assertSame(before, b.getChapterVerses(1, 1))
    }

    @Test
    fun `an anchor whose id is not a verse code puts nothing in`() {
        val verses = listOf(BibleVerse("odd", 1, 1, 1, "One."))
        assertEquals(verses, overlaid(verses, listOf(VerseEdit(1, 1, 2, "Two."))))
    }

    @Test
    fun `search finds an edited verse by its new words`() {
        val b = bible()
        b.applyVerseEdits(listOf(VerseEdit(1, 1, 3, "And God said, Let there be light.")))
        assertNotNull(b.searchBible(false, Regex("God said")).singleOrNull())
    }
}
