package org.churchpresenter.bibleformats

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PsalmNumberingTest {

    private val temp: File = Files.createTempDirectory("converter-psalm-numbering").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun psalm(number: Int, verses: List<String>) =
        BibleChapter(number, verses.mapIndexed { i, t -> BibleVerse(i + 1, t) })

    private fun bible(language: String, vararg psalms: BibleChapter) =
        ParsedBible("Test", "", language, listOf(BibleBook(19, "Psalms", psalms.toList())))

    private fun codes(bible: ParsedBible): Map<Pair<Int, Int>, String> {
        val out = File(temp, "out.spb")
        XmlToSpbConverter.write(bible, out)
        return out.readLines().filter { it.startsWith("B019") }.associate { line ->
            val cols = line.split("\t")
            (cols[2].toInt() to cols[3].toInt()) to cols[0]
        }
    }

    private fun verses(count: Int, first: String = "Стих") = (1..count).map { if (it == 1) first else "Стих $it" }

    @Test
    fun `a whole Psalm 9 tells the numbering, whatever the language says`() {
        assertFalse(XmlToSpbConverter.usesLxxPsalms(bible("UKR", psalm(9, verses(20)))), "a Hebrew Psalm 9")
        assertFalse(XmlToSpbConverter.usesLxxPsalms(bible("UKR", psalm(9, verses(21)))), "with its title as verse 1")
        assertTrue(XmlToSpbConverter.usesLxxPsalms(bible("ENG", psalm(9, verses(39)))), "the Septuagint's 9 and 10")
    }

    @Test
    fun `a partial Psalm 9 or none leaves it to the language`() {
        assertTrue(XmlToSpbConverter.usesLxxPsalms(bible("UKR", psalm(9, verses(5)))))
        assertFalse(XmlToSpbConverter.usesLxxPsalms(bible("ENG", psalm(3, verses(8)))))
        assertTrue(XmlToSpbConverter.usesLxxPsalms(bible("rus", psalm(3, verses(8)))))
    }

    @Test
    fun `a Hebrew-numbered Ukrainian Bible is coded as it is numbered`() {
        val codes = codes(bible("UKR", psalm(9, verses(20)), psalm(23, verses(6, "Господь пастир мій"))))
        assertEquals("B019C023V001", codes[23 to 1])
        assertEquals("B019C009V020", codes[9 to 20])
    }

    @Test
    fun `a long title is the psalm's verse 0 when the psalm has one verse more than the Hebrew`() {
        val title = "Начальнику хора. По смерти Лабена. Псалом Давида."
        val codes = codes(bible("RUS", psalm(9, verses(39, title))))
        assertEquals("B019C009V000", codes[9 to 1])
        assertEquals("B019C009V020", codes[9 to 21])
        assertEquals("B019C010V001", codes[9 to 22])
        assertEquals("B019C010V018", codes[9 to 39])
    }

    @Test
    fun `a long first verse that mentions David is content when the count does not call for a title`() {
        // Septuagint 131 is Hebrew 132: 18 verses either way, so its first verse is a verse.
        val codes = codes(bible("RUS", psalm(131, verses(18, "Вспомни, Господи, Давида и все смирение его"))))
        assertEquals("B019C132V001", codes[131 to 1])
    }

    @Test
    fun `a title running over two verses is verse 0 twice`() {
        // Septuagint 50 is Hebrew 51: 19 verses, so 21 means a two-verse title.
        val chapter = BibleChapter(
            50,
            listOf(
                BibleVerse(1, "Начальнику хора. Псалом Давида,"),
                BibleVerse(2, "когда приходил к нему пророк Нафан, после того, как Давид вошел к Вирсавии."),
            ) + (3..21).map { BibleVerse(it, "Стих $it") },
        )
        val codes = codes(bible("RUS", chapter))
        assertEquals("B019C051V000", codes[50 to 1])
        assertEquals("B019C051V000", codes[50 to 2])
        assertEquals("B019C051V001", codes[50 to 3])
        assertEquals("B019C051V019", codes[50 to 21])
    }

    @Test
    fun `a bracketed title with the psalm behind it stays a verse even when the count is one over`() {
        val first = "«Псалом Давида, когда он бежал от Авессалома, сына своего.» " +
            "Господи! как умножились враги мои! Многие восстают на меня"
        val codes = codes(bible("RUS", psalm(3, verses(9, first))))
        assertEquals("B019C003V001", codes[3 to 1])
    }
}
