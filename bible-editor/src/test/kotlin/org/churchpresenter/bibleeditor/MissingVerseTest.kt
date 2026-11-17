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

    private fun bible(name: String, verses: List<SpbFixture.Verse>): Bible = Bible().apply {
        val books = listOf(SpbFixture.Book(19, "Psalms", 23), SpbFixture.Book(43, "John", 3))
        loadFromSpb(SpbFixture.spbFile(dir, name, SpbFixture.buildContent(name, books, verses)).absolutePath)
    }

    private val reference by lazy {
        bible("kjv.spb", (1..4).map { SpbFixture.Verse(19, 23, it,
            "Psalm $it") } + (1..3).map { SpbFixture.Verse(43, 3, it, "John $it") })
    }

    @Test
    fun `a verse the reference has and this Bible lacks is found where it belongs`() {
        val target = bible("rst.spb", listOf(1, 2, 4).map { SpbFixture.Verse(19, 23, it,
            "P$it") } + SpbFixture.Verse(43, 3, 1, "J1"))
        assertEquals(
            listOf(MissingVerse(19, 23, 3), MissingVerse(43, 3, 2), MissingVerse(43, 3, 3)),
            findMissingVerses(target, reference),
        )
    }

    @Test
    fun `it is placed in this Bible's own numbering, and one that would number below one is not reported`() {
        // Numbered one behind its codes: verse 1 is code 2, verse 3 is code 4.
        val target = bible("lxx.spb", listOf(2, 4).map { SpbFixture.Verse(19, 23, it - 1, "P$it", codeVerse = it) })
        assertEquals(listOf(MissingVerse(19, 23, 2)), findMissingVerses(target, reference))
    }

    @Test
    fun `a missing first verse is placed by the verse after it, and a chapter it lacks entirely is left out`() {
        val target = bible("rst.spb", listOf(2, 3, 4).map { SpbFixture.Verse(19, 23, it, "P$it") })
        assertEquals(listOf(MissingVerse(19, 23, 1)), findMissingVerses(target, reference))
    }

    @Test
    fun `a gap whose place a verse already stands in is not reported`() {
        val target = bible("odd.spb", listOf(
            SpbFixture.Verse(19, 23, 1, "P1", codeVerse = 1),
            SpbFixture.Verse(19, 23, 2, "P3", codeVerse = 3),
            SpbFixture.Verse(19, 23, 3, "P4", codeVerse = 4),
        ))
        assertEquals(emptyList(), findMissingVerses(target, reference))
    }
}
