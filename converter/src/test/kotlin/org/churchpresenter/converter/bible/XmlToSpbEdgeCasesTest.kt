package org.churchpresenter.converter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The parts of the `.spb` writer that only a Septuagint Psalter, a broken book name or a patched
 * verse reaches.
 *
 * Psalm numbering is the reason most of this exists: an Orthodox translation numbers Psalms 10-147
 * one behind the Hebrew text, and the `BxxxCxxxVxxx` code has to be written in Hebrew numbering or
 * two translations shown side by side drift apart from Psalm 10 onwards. Each arm of that mapping
 * is pinned individually because the merged and split psalms (9, 113, 114/115, 146/147) are the
 * ones a straight offset gets wrong.
 */
class XmlToSpbEdgeCasesTest {

    private val temp: File = Files.createTempDirectory("converter-xml-edges").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun psalter(vararg chapters: Pair<Int, List<String>>): List<String> {
        val books = listOf(
            BibleBook(
                19,
                "Псалтирь",
                chapters.map { (num, verses) ->
                    BibleChapter(num, verses.mapIndexed { i, t -> BibleVerse(i + 1, t) })
                },
            )
        )
        val out = File(temp, "psalms.spb")
        XmlToSpbConverter.write(ParsedBible("Синодальный", "", "RUS", books), out)
        return out.readLines().filter { it.startsWith("B019") }
    }

    private fun codeFor(lines: List<String>, displayChapter: Int, displayVerse: Int): String =
        lines.first { line ->
            val cols = line.split("\t")
            cols[2].toInt() == displayChapter && cols[3].toInt() == displayVerse
        }.substringBefore("\t")

    // ── Septuagint → Hebrew psalm numbering ───────────────────────────────────

    @Test
    fun `psalms up to 8 keep their number`() {
        val lines = psalter(1 to listOf("Блажен муж"), 8 to listOf("Господи"))
        assertEquals("B019C001V001", codeFor(lines, 1, 1))
        assertEquals("B019C008V001", codeFor(lines, 8, 1))
    }

    @Test
    fun `the merged psalms keep the Hebrew number of the psalm they open`() {
        // LXX 9 is Hebrew 9+10 and LXX 113 is Hebrew 114+115: both are one psalm in the Septuagint
        // and two in the Hebrew text, so the code takes the number of the first of the pair.
        val lines = psalter(9 to listOf("Буду славить"), 113 to listOf("Когда вышел Израиль"))
        assertEquals("B019C009V001", codeFor(lines, 9, 1))
        assertEquals("B019C114V001", codeFor(lines, 113, 1))
    }

    @Test
    fun `the psalms numbered one behind the Hebrew text are shifted up`() {
        val lines = psalter(
            10 to listOf("Для чего, Господи"),
            112 to listOf("Хвалите, рабы Господни"),
            116 to listOf("Хвалите Господа"),
            145 to listOf("Хвали, душа моя"),
        )
        assertEquals("B019C011V001", codeFor(lines, 10, 1))
        assertEquals("B019C113V001", codeFor(lines, 112, 1))
        assertEquals("B019C117V001", codeFor(lines, 116, 1))
        assertEquals("B019C146V001", codeFor(lines, 145, 1))
    }

    @Test
    fun `the split psalms both point at the Hebrew psalm they came from`() {
        // LXX 114 and 115 are the two halves of Hebrew 116; LXX 146 and 147 are the halves of 147.
        val lines = psalter(
            114 to listOf("Я радуюсь"),
            115 to listOf("Я веровал"),
            146 to listOf("Хвалите Господа"),
            147 to listOf("Хвали, Иерусалим"),
        )
        assertEquals("B019C116V001", codeFor(lines, 114, 1))
        assertEquals("B019C116V001", codeFor(lines, 115, 1))
        assertEquals("B019C147V001", codeFor(lines, 146, 1))
        assertEquals("B019C147V001", codeFor(lines, 147, 1))
    }

    @Test
    fun `the psalms after the split are numbered alike in both traditions`() {
        val lines = psalter(148 to listOf("Хвалите Господа"), 150 to listOf("Хвалите Бога"))
        assertEquals("B019C148V001", codeFor(lines, 148, 1))
        assertEquals("B019C150V001", codeFor(lines, 150, 1))
    }

    @Test
    fun `a translation outside the Septuagint tradition is numbered as it stands`() {
        val books = listOf(BibleBook(19, "Psalms", listOf(BibleChapter(10, listOf(BibleVerse(1, "Why, Lord"))))))
        val out = File(temp, "kjv.spb")
        XmlToSpbConverter.write(ParsedBible("KJV", "", "ENG", books), out)
        assertTrue(out.readLines().any { it.startsWith("B019C010V001\t") })
    }

    @Test
    fun `a Septuagint book that is not the Psalter is left alone`() {
        val books = listOf(BibleBook(20, "Притчи", listOf(BibleChapter(10, listOf(BibleVerse(1, "Сын мудрый"))))))
        val out = File(temp, "proverbs.spb")
        XmlToSpbConverter.write(ParsedBible("Синодальный", "", "RUS", books), out)
        assertTrue(out.readLines().any { it.startsWith("B020C010V001\t") })
    }

    // ── Standalone superscriptions ────────────────────────────────────────────

    @Test
    fun `a psalm opening with a title only numbers that title as verse zero`() {
        val lines = psalter(3 to listOf("Псалом Давида.", "Господи! как умножились"))
        assertEquals("B019C003V000", codeFor(lines, 3, 1))
        assertEquals("B019C003V001", codeFor(lines, 3, 2))
    }

    @Test
    fun `a title with the psalm text behind it is a verse of its own`() {
        // The bracketed title is stripped before the length check, but what follows is the psalm
        // itself, so the verse is content and must keep its number.
        val lines = psalter(
            1 to listOf("«Псалом Давида.» Блажен муж, который не ходит на совет нечестивых и не стоит на пути грешных")
        )
        assertEquals("B019C001V001", codeFor(lines, 1, 1))
    }

    @Test
    fun `a long first verse is content however much it reads like a title`() {
        val lines = psalter(5 to listOf("Псалом Давида. " + "слово ".repeat(40)))
        assertEquals("B019C005V001", codeFor(lines, 5, 1))
    }

    @Test
    fun `a first verse with nothing but a bracketed note is treated as a superscription`() {
        val lines = psalter(6 to listOf("«В конец, в песнях о восьмом.»", "Господи, да не яростию"))
        assertEquals("B019C006V000", codeFor(lines, 6, 1))
    }

    @Test
    fun `an ordinary opening verse keeps verse one`() {
        val lines = psalter(7 to listOf("Господи, Боже мой! на Тебя я уповаю", "да не исторгнет"))
        assertEquals("B019C007V001", codeFor(lines, 7, 1))
    }

    @Test
    fun `an empty psalm is written without a superscription check`() {
        val lines = psalter(2 to emptyList(), 4 to listOf("Когда я взываю"))
        assertEquals("B019C004V001", codeFor(lines, 4, 1))
    }

    // ── Verse patches ─────────────────────────────────────────────────────────

    @Test
    fun `a verse with no patch is returned unchanged`() {
        assertEquals("Original", XmlToSpbConverter.applyPatch("Original", "RUS", 1, 1, 1))
    }

    @Test
    fun `a language-restricted patch is skipped for other translations`() {
        val truncated = "Сына [одной] женщины из дочерей Дановых, — а отец его Тирянин"
        assertEquals(truncated, XmlToSpbConverter.applyPatch(truncated, "UKR", 14, 2, 14))
        assertEquals(truncated, XmlToSpbConverter.applyPatch(truncated, null, 14, 2, 14))
    }

    @Test
    fun `a language-restricted patch is applied to its own translation`() {
        val patched = XmlToSpbConverter.applyPatch(
            "Сына [одной] женщины из дочерей Дановых, — а отец его Тирянин, — умеющего делать",
            "rus",
            14, 2, 14,
        )
        assertTrue(patched.endsWith("отца твоего."), "got '$patched'")
    }

    @Test
    fun `a patch with a minimum length leaves a text too short to be the same verse alone`() {
        assertEquals("...госпо", XmlToSpbConverter.applyPatch("...госпо", "RUS", 14, 2, 14))
    }

    @Test
    fun `an exact-match patch corrects only the wording it names`() {
        val wrong = "Смиренных возвышает Господь, а нечестивых унижает до землю."
        assertEquals(
            "Смиренных возвышает Господь, а нечестивых унижает до земли.",
            XmlToSpbConverter.applyPatch(wrong, "RUS", 19, 146, 6),
        )
        val other = "Совсем другой перевод этого стиха."
        assertEquals(other, XmlToSpbConverter.applyPatch(other, "RUS", 19, 146, 6))
    }

    // ── Book names ────────────────────────────────────────────────────────────

    private fun zefania(books: String, information: String = "", path: String = "names.xml"): File {
        val file = File(temp, path)
        file.parentFile.mkdirs()
        file.writeText(
            """<?xml version="1.0" encoding="UTF-8"?><XMLBIBLE biblename="Test">$information$books</XMLBIBLE>""",
            Charsets.UTF_8,
        )
        return file
    }

    private fun bookNameOf(bookXml: String, information: String = "", path: String = "names.xml"): String =
        XmlToSpbConverter.parse(zefania(bookXml, information, path)).books.single().name

    @Test
    fun `an English module falls back from a blank bname to its short name`() {
        val name = bookNameOf(
            """<BIBLEBOOK bnumber="1" bname="" bsname="Gen"><CHAPTER cnumber="1">
               <VERS vnumber="1">In the beginning</VERS></CHAPTER></BIBLEBOOK>""",
            information = "<INFORMATION><language>ENG</language></INFORMATION>",
        )
        assertEquals("Gen", name)
    }

    @Test
    fun `an English module with neither name uses the canonical English name`() {
        val name = bookNameOf(
            """<BIBLEBOOK bnumber="1"><CHAPTER cnumber="1">
               <VERS vnumber="1">In the beginning</VERS></CHAPTER></BIBLEBOOK>""",
            information = "<INFORMATION><language>ENG</language></INFORMATION>",
        )
        assertEquals("Genesis", name)
    }

    @Test
    fun `a translated module names its books from the language table, not from the file`() {
        // The file's own bname is deliberately ignored here: the archive's Russian modules spell
        // the same book several ways, and the app matches on its own table.
        val name = bookNameOf(
            """<BIBLEBOOK bnumber="1" bname="1 Moses"><CHAPTER cnumber="1">
               <VERS vnumber="1">В начале</VERS></CHAPTER></BIBLEBOOK>""",
            information = "<INFORMATION><language>RUS</language></INFORMATION>",
        )
        assertEquals("Бытие", name)
    }

    @Test
    fun `a book outside the canon still gets a name in a translated module`() {
        val name = bookNameOf(
            """<BIBLEBOOK bnumber="99"><CHAPTER cnumber="1">
               <VERS vnumber="1">Текст</VERS></CHAPTER></BIBLEBOOK>""",
            information = "<INFORMATION><language>RUS</language></INFORMATION>",
        )
        assertEquals("Book 99", name)
    }

    @Test
    fun `a module with no language reads the book name off the chapter caption`() {
        val name = bookNameOf(
            """<BIBLEBOOK bnumber="1"><CHAPTER cnumber="1"><CAPTION>1. Genèse</CAPTION>
               <VERS vnumber="1">Au commencement</VERS></CHAPTER></BIBLEBOOK>""",
            path = "ZZZ/a/b/caption.xml",
        )
        assertEquals("Genèse", name)
    }

    @Test
    fun `a caption that is a whole sentence is not mistaken for a book name`() {
        val name = bookNameOf(
            """<BIBLEBOOK bnumber="1"><CHAPTER cnumber="1">
               <CAPTION>1. The first book of Moses, commonly called Genesis</CAPTION>
               <VERS vnumber="1">In the beginning</VERS></CHAPTER></BIBLEBOOK>""",
            path = "ZZZ/a/b/long-caption.xml",
        )
        assertEquals("Genesis", name)
    }

    @Test
    fun `a caption with no number prefix is left for the canonical name`() {
        val name = bookNameOf(
            """<BIBLEBOOK bnumber="1"><CHAPTER cnumber="1"><CAPTION>Genèse</CAPTION>
               <VERS vnumber="1">Au commencement</VERS></CHAPTER></BIBLEBOOK>""",
            path = "ZZZ/a/b/bare-caption.xml",
        )
        assertEquals("Genesis", name)
    }

    @Test
    fun `a book with no number at all is labelled by that number rather than failing`() {
        val name = bookNameOf(
            """<BIBLEBOOK><CHAPTER cnumber="1"><VERS vnumber="1">Text</VERS></CHAPTER></BIBLEBOOK>""",
            path = "ZZZ/a/b/no-number.xml",
        )
        assertEquals("Book 0", name)
    }

    // ── Header flattening ─────────────────────────────────────────────────────

    @Test
    fun `a blank copyright and source are left out of the header entirely`() {
        val out = File(temp, "bare.spb")
        XmlToSpbConverter.write(
            ParsedBible("Bare", "", "ENG", listOf(BibleBook(1, "Genesis", emptyList()))),
            out,
        )
        val lines = out.readLines()
        assertTrue(lines.none { it.startsWith("##Copyright:") })
        assertTrue(lines.none { it.startsWith("##Source:") })
    }

    @Test
    fun `progress is reported once per book and ends at one`() {
        val reported = mutableListOf<Float>()
        XmlToSpbConverter.write(
            ParsedBible(
                "Two Books", "", "ENG",
                listOf(BibleBook(1, "Genesis", emptyList()), BibleBook(2, "Exodus", emptyList())),
            ),
            File(temp, "progress.spb"),
        ) { reported.add(it) }
        assertEquals(listOf(0.5f, 1.0f), reported)
    }
}
