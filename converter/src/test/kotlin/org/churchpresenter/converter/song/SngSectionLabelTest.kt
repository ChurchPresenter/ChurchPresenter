package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `.sng` section labelling and the verse order written into the converted song.
 *
 * The interesting rule is deduplication: a `.sng` verse order names the chorus once per repeat,
 * but the app repeats sections itself from the order, so writing the chorus out three times would
 * produce three identical slides in a row. Section labels also arrive in several languages, which
 * works here — unlike the markdown importer — because the label is lower-cased before matching
 * rather than relying on a case-insensitive pattern.
 */
class SngSectionLabelTest {

    private val temp: File = Files.createTempDirectory("converter-sng-label-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun sng(body: String): File =
        File(temp, "song.sng").apply { writeText(body, Charsets.UTF_8) }

    private fun convertedText(body: String): String {
        val out = File(temp, "out.song")
        SngToSongConverter.convert(sng(body), out)
        return out.readText()
    }

    private fun sectionsOf(text: String): List<String> =
        Regex("""^\[(.+)]$""", RegexOption.MULTILINE).findAll(text)
            .map { it.groupValues[1] }
            .filter { it != "Primary" }
            .toList()

    // ── Label recognition ─────────────────────────────────────────────────────

    @Test
    fun `an English verse label keeps its number`() {
        val sections = sectionsOf(convertedText("#Title=T\n---\nVerse 2\nLine\n"))
        assertEquals(listOf("Verse 2"), sections)
    }

    @Test
    fun `a chorus is recognised and carries no number`() {
        assertEquals(listOf("Chorus"), sectionsOf(convertedText("#Title=T\n---\nChorus\nLine\n")))
    }

    @Test
    fun `bridge, pre-chorus and ending are each recognised`() {
        assertEquals(listOf("Bridge"), sectionsOf(convertedText("#Title=T\n---\nBridge\nLine\n")))
        assertEquals(listOf("Pre-Chorus"), sectionsOf(convertedText("#Title=T\n---\nPre-Chorus\nLine\n")))
        assertEquals(listOf("Ending"), sectionsOf(convertedText("#Title=T\n---\nOutro\nLine\n")))
    }

    @Test
    fun `capitalised Cyrillic labels are recognised`() {
        // Works because the label is lower-cased before matching, rather than depending on a
        // case-insensitive pattern — the trap the markdown importer fell into.
        assertEquals(listOf("Verse 1"), sectionsOf(convertedText("#Title=T\n---\nКуплет 1\nСтрока\n")))
        assertEquals(listOf("Chorus"), sectionsOf(convertedText("#Title=T\n---\nПрипев\nСтрока\n")))
        assertEquals(listOf("Bridge"), sectionsOf(convertedText("#Title=T\n---\nМост\nСтрока\n")))
    }

    // ── Verse order ───────────────────────────────────────────────────────────

    @Test
    fun `sections are written in the order the file declares`() {
        val text = convertedText(
            """
            #Title=T
            #VerseOrder=Chorus,Verse 1
            ---
            Verse 1
            First verse line
            ---
            Chorus
            Chorus line
            """.trimIndent()
        )
        assertEquals(listOf("Chorus", "Verse 1"), sectionsOf(text), "the declared order wins over file order")
    }

    @Test
    fun `a section named more than once in the order is written only once`() {
        // The order below repeats the chorus after every verse, as .sng files do.
        val text = convertedText(
            """
            #Title=T
            #VerseOrder=Verse 1,Chorus,Verse 2,Chorus
            ---
            Verse 1
            First verse line
            ---
            Chorus
            Chorus line
            ---
            Verse 2
            Second verse line
            """.trimIndent()
        )
        assertEquals(
            listOf("Verse 1", "Chorus", "Verse 2"),
            sectionsOf(text),
            "the chorus appears once, not once per repeat",
        )
        assertEquals(1, Regex("Chorus line").findAll(text).count(), "and its lyrics are written once")
    }

    @Test
    fun `an order naming a section that does not exist skips it rather than failing`() {
        val text = convertedText(
            """
            #Title=T
            #VerseOrder=Verse 1,Bridge,Verse 2
            ---
            Verse 1
            First
            ---
            Verse 2
            Second
            """.trimIndent()
        )
        assertEquals(listOf("Verse 1", "Verse 2"), sectionsOf(text))
    }

    @Test
    fun `with no declared order the file's own section order is kept`() {
        val text = convertedText(
            """
            #Title=T
            ---
            Verse 1
            First
            ---
            Chorus
            Refrain
            ---
            Verse 2
            Second
            """.trimIndent()
        )
        assertEquals(listOf("Verse 1", "Chorus", "Verse 2"), sectionsOf(text))
    }

    @Test
    fun `the converted file carries the title and frontmatter`() {
        val text = convertedText("#Title=Amazing Grace\n#Author=John Newton\n#(c)=Public Domain\n---\nVerse 1\nLine\n")
        assertTrue(text.startsWith("---"), "frontmatter leads the file")
        assertTrue(text.contains("author: John Newton"))
        assertTrue(text.contains("copyright: Public Domain"))
        assertTrue(text.contains("title: Amazing Grace"))
    }

    @Test
    fun `lyrics survive the conversion intact`() {
        val text = convertedText("#Title=T\n---\nVerse 1\nFirst line\nSecond line\n")
        assertTrue(text.contains("First line"))
        assertTrue(text.contains("Second line"))
    }
}
