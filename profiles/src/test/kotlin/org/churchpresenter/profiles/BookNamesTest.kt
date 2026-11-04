package org.churchpresenter.profiles

import org.churchpresenter.bible.SpbFixture
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookNamesTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-book-names-read").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private val titles = BookTitles(
        english = (1..BOOK_COUNT).map { if (it == 1) "Genesis" else "English $it" },
        localized = (1..BOOK_COUNT).map { if (it == 1) "Бытие" else "Local $it" },
    )

    private fun module(count: Int, name: (Int) -> String): String {
        val content = SpbFixture.buildContent(
            title = "T",
            books = (1..count).map { SpbFixture.Book(it, name(it), 1) },
            verses = emptyList(),
        )
        SpbFixture.spbFile(dir, name = "t.spb", content = content)
        return "t.spb"
    }

    private fun read(fileName: String) = readModuleBookNames(dir.absolutePath, fileName)

    @Test
    fun `a file naming every book reads as all`() {
        val module = read(module(BOOK_COUNT) { "Real $it" })

        assertEquals(BookNamesRead.ALL, module.read)
        assertEquals("Real 19", module.names[19])
        assertEquals(BOOK_COUNT, module.names.size)
    }

    @Test
    fun `a file naming only some books reads as some`() {
        val module = read(module(27) { "Real $it" })

        assertEquals(BookNamesRead.SOME, module.read)
        assertEquals(27, module.names.size)
    }

    @Test
    fun `numbered placeholders do not count as names`() {
        val module = read(module(BOOK_COUNT) { if (it <= 3) "Real $it" else "Book $it" })

        assertEquals(BookNamesRead.SOME, module.read)
        assertEquals(setOf(1, 2, 3), module.names.keys)
    }

    @Test
    fun `books past the canon do not make up for missing ones`() {
        val content = SpbFixture.buildContent(
            title = "T",
            books = (2..BOOK_COUNT + 1).map { SpbFixture.Book(it, "Real $it", 1) },
            verses = emptyList(),
        )
        SpbFixture.spbFile(dir, name = "t.spb", content = content)

        assertEquals(BookNamesRead.SOME, read("t.spb").read)
    }

    @Test
    fun `a file with only placeholders reads as none`() {
        assertEquals(BookNamesRead.NONE, read(module(BOOK_COUNT) { "Book $it" }).read)
    }

    @Test
    fun `a file with no book list reads as none`() {
        assertEquals(BookNamesRead.NONE, read(module(0) { "" }).read)
    }

    @Test
    fun `a file that is not text reads as failed`() {
        File(dir, "bad.spb").writeBytes(byteArrayOf(0xC3.toByte(), 0x28, 0xFF.toByte(), 0xFE.toByte(), 10))

        assertEquals(ModuleBookNames(emptyMap(), BookNamesRead.FAILED), read("bad.spb"))
    }

    @Test
    fun `a read that fails after the names keeps them and reads as some`() {
        val header = (1..BOOK_COUNT).joinToString("\n") { "$it Real$it 1" }
        val pastTheReadBuffer = "##Comment\n".repeat(COMMENT_LINES)
        File(dir, "cut.spb").writeBytes(
            "$header\n$pastTheReadBuffer".toByteArray() + byteArrayOf(0xC3.toByte(), 0x28, 10),
        )

        val module = read("cut.spb")

        assertEquals(BookNamesRead.SOME, module.read)
        assertEquals(BOOK_COUNT, module.names.size)
    }

    private companion object {
        const val COMMENT_LINES = 10_000
    }

    @Test
    fun `a missing file or an unset folder reads as failed`() {
        assertEquals(BookNamesRead.FAILED, read("gone.spb").read)
        assertEquals(BookNamesRead.FAILED, readModuleBookNames("", "t.spb").read)
        assertEquals(BookNamesRead.FAILED, read("").read)
    }

    @Test
    fun `the status says what a translation is using`() {
        assertEquals(BookNamesStatus.FromFile, bookNamesStatus(hasFileNames = true, overrideCount = 0))
        assertEquals(BookNamesStatus.Edited(3), bookNamesStatus(hasFileNames = true, overrideCount = 3))
        assertEquals(BookNamesStatus.NotSet, bookNamesStatus(hasFileNames = false, overrideCount = 0))
        assertEquals(BookNamesStatus.PartlySet(3), bookNamesStatus(hasFileNames = false, overrideCount = 3))
        assertEquals(BookNamesStatus.Custom, bookNamesStatus(hasFileNames = false, overrideCount = BOOK_COUNT))
    }

    @Test
    fun `saving trims names and drops blank ones and ones repeating the file`() {
        val saved = savedBookOverrides(
            draft = mapOf(1 to "  Bereshit ", 2 to "   ", 3 to "Leviticus", 4 to "Numbers"),
            fileNames = mapOf(3 to "Leviticus", 4 to "Bemidbar"),
        )

        assertEquals(mapOf(1 to "Bereshit", 4 to "Numbers"), saved)
    }

    private fun shown(filter: BookNameFilter, query: String = "", draft: Map<Int, String> = emptyMap()) =
        bookIdsShown(filter, query, draft, mapOf(2 to "Shemot"), titles)

    @Test
    fun `the filters split the canon and pick out the overrides`() {
        assertEquals((1..BOOK_COUNT).toList(), shown(BookNameFilter.ALL))
        assertEquals((1..OLD_TESTAMENT_BOOKS).toList(), shown(BookNameFilter.OLD_TESTAMENT))
        assertEquals((OLD_TESTAMENT_BOOKS + 1..BOOK_COUNT).toList(), shown(BookNameFilter.NEW_TESTAMENT))
        assertEquals(listOf(5), shown(BookNameFilter.OVERRIDDEN, draft = mapOf(5 to "Devarim")))
    }

    @Test
    fun `a search matches any name a book goes by, ignoring case`() {
        assertEquals(listOf(1), shown(BookNameFilter.ALL, "genesis"))
        assertEquals(listOf(1), shown(BookNameFilter.ALL, "БЫТИЕ"))
        assertEquals(listOf(2), shown(BookNameFilter.ALL, " shemot "))
        assertEquals(listOf(5), shown(BookNameFilter.ALL, "devarim", mapOf(5 to "Devarim")))
        assertTrue(shown(BookNameFilter.ALL, "nowhere").isEmpty())
    }

    @Test
    fun `a search stays inside the filter`() {
        assertTrue(shown(BookNameFilter.NEW_TESTAMENT, "genesis").isEmpty())
    }

    @Test
    fun `titles not loaded yet read as empty`() {
        val empty = BookTitles(emptyList(), emptyList())

        assertEquals("", empty.englishOf(1))
        assertEquals("", empty.localizedOf(1))
    }
}
