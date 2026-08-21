package converter.song

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Section names as SongBeamer and the document importer spell them, in both languages.
 *
 * These two carry their own name tables rather than going through [SectionLabel], because both
 * read names a person typed rather than a code the program wrote: `Refrain`, `Припев`, `Окончание`,
 * `## **Chorus**`. A name that misses its table is not lost — it is passed through as it stands —
 * so what these pin is that the common spellings land on the same label the rest of the app uses.
 */
class SectionNamingAcrossFormatsTest {

    private val temp: File = Files.createTempDirectory("converter-section-naming").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun sng(vararg sections: String): File =
        File(temp, "song.sng").apply {
            writeText(
                "#Title=Test Song\n#Author=Someone\n---\n" + sections.joinToString("---\n"),
                Charsets.UTF_8,
            )
        }

    private fun labelsOf(file: File): List<String> =
        SngToSongConverter.parse(file).let { song ->
            val out = File(temp, "out.song")
            SngToSongConverter.convert(file, out)
            Regex("""^\[(.+)]$""", RegexOption.MULTILINE).findAll(out.readText())
                .map { it.groupValues[1] }
                .filter { it != "Primary" }
                .toList()
                .also { assertTrue(song.sections.isNotEmpty()) }
        }

    // ── SongBeamer ────────────────────────────────────────────────────────────

    @Test
    fun `every English section name SongBeamer writes is recognised`() {
        val labels = labelsOf(
            sng(
                "Verse 1\nfirst\n", "Chorus\nsecond\n", "Refrain\nthird\n", "Bridge\nfourth\n",
                "Pre-Chorus\nfifth\n", "PreChorus\nsixth\n", "Ending\nseventh\n", "Outro\neighth\n",
                "Intro\nninth\n",
            )
        )
        assertEquals(
            listOf("Verse 1", "Chorus", "Chorus", "Bridge", "Pre-Chorus", "Pre-Chorus", "Ending", "Ending", "Intro"),
            labels,
        )
    }

    @Test
    fun `every Russian section name SongBeamer writes is recognised`() {
        val labels = labelsOf(
            sng(
                "Куплет 1\nпервый\n", "Припев\nвторой\n", "Хор\nтретий\n", "Мост\nчетвёртый\n",
                "Окончание\nпятый\n", "Конец\nшестой\n", "Вступление\nседьмой\n",
            )
        )
        assertEquals(
            listOf("Verse 1", "Chorus", "Chorus", "Bridge", "Ending", "Ending", "Intro"),
            labels,
        )
    }

    @Test
    fun `a name SongBeamer's own vocabulary does not cover is kept as it stands`() {
        assertEquals(listOf("Antiphon"), labelsOf(sng("Antiphon\nthe line\n")))
    }

    @Test
    fun `a verse order names sections by type, by name or by both`() {
        val file = File(temp, "ordered.sng").apply {
            writeText(
                "#Title=Ordered\n#VerseOrder=Chorus,Verse 2,Verse 1\n---\n" +
                    "Verse 1\nfirst\n---\nVerse 2\nsecond\n---\nChorus\nrefrain\n",
                Charsets.UTF_8,
            )
        }
        val out = File(temp, "ordered.song")
        SngToSongConverter.convert(file, out)
        val labels = Regex("""^\[(.+)]$""", RegexOption.MULTILINE).findAll(out.readText())
            .map { it.groupValues[1] }.filter { it != "Primary" }.toList()
        assertEquals(listOf("Chorus", "Verse 2", "Verse 1"), labels)
    }

    @Test
    fun `a header line with no equals sign is not a header`() {
        val file = File(temp, "oddheader.sng").apply {
            writeText("#Title=Kept\n#JustAComment\n---\nVerse 1\nthe line\n", Charsets.UTF_8)
        }
        assertEquals("Kept", SngToSongConverter.parse(file).title)
    }

    @Test
    fun `a file with no separator is read for its headers alone`() {
        // Without the `---` that ends the header block there is no section boundary to find, so
        // the file yields its metadata and no lyrics rather than one section of run-together text.
        val file = File(temp, "noseparator.sng").apply {
            writeText("#Title=Loose\nVerse 1\nthe line\n", Charsets.UTF_8)
        }
        val song = SngToSongConverter.parse(file)
        assertEquals("Loose", song.title)
        assertTrue(song.sections.isEmpty())
    }

    @Test
    fun `a Windows-1251 file is read in its own encoding`() {
        val file = File(temp, "cp1251.sng").apply {
            writeBytes(
                "#Title=Слава\n---\nКуплет 1\nСлава Богу\n".toByteArray(Charset.forName("windows-1251"))
            )
        }
        val song = SngToSongConverter.parse(file)
        assertEquals("Слава", song.title)
        assertTrue(song.sections.single().text.contains("Слава Богу"))
    }

    @Test
    fun `a UTF-8 file with a byte order mark loses the mark, not its first header`() {
        val file = File(temp, "bom.sng").apply {
            writeBytes(
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                    "#Title=Marked\n---\nVerse 1\nthe line\n".toByteArray(Charsets.UTF_8)
            )
        }
        assertEquals("Marked", SngToSongConverter.parse(file).title)
    }

    // ── Documents ─────────────────────────────────────────────────────────────

    private fun markdownLabels(markdown: String): List<String> =
        MarkdownToSongConverter.parseMarkdown(markdown, "document.docx").single().sections.map { it.label }

    @Test
    fun `every section name a document can carry is recognised`() {
        val labels = markdownLabels(
            """
            # A Song

            Verse 1
            first line

            Chorus
            second line

            Bridge
            third line

            Pre-Chorus
            fourth line

            Ending
            fifth line

            Intro
            sixth line

            Coda
            seventh line

            Tag
            eighth line
            """.trimIndent()
        )
        assertEquals(
            listOf("Verse 1", "Chorus", "Bridge", "Pre-Chorus", "Ending", "Intro", "Coda", "Tag"),
            labels,
        )
    }

    @Test
    fun `Russian section names in a document are recognised too`() {
        val labels = markdownLabels(
            """
            # Песня

            Куплет 1
            первая строка

            Припев
            вторая строка

            Мост
            третья строка

            Окончание
            четвёртая строка
            """.trimIndent()
        )
        assertEquals(listOf("Verse 1", "Chorus", "Bridge", "Ending"), labels)
    }

    @Test
    fun `a section name written as a sub-heading is one`() {
        val labels = markdownLabels(
            """
            # A Song

            ## **Chorus**
            praise the Lord

            ### Verse 2
            the second verse
            """.trimIndent()
        )
        assertEquals(listOf("Chorus", "Verse 2"), labels)
    }

    @Test
    fun `a sub-heading that names no section is part of the song`() {
        val song = MarkdownToSongConverter.parseMarkdown(
            """
            # A Song

            ## About this hymn
            the only line
            """.trimIndent(),
            "document.docx",
        ).single()
        assertTrue(song.sections.any { it.lines.any { line -> line.contains("the only line") } })
    }

    @Test
    fun `metadata lines are read out of the document rather than sung`() {
        val song = MarkdownToSongConverter.parseMarkdown(
            """
            # Amazing Grace

            Author: John Newton
            Music: William Walker
            Copyright: Public Domain

            Verse 1
            Amazing grace how sweet the sound
            """.trimIndent(),
            "document.docx",
        ).single()

        assertEquals("Amazing Grace", song.title)
        assertEquals("John Newton", song.author)
        assertEquals("William Walker", song.composer)
        assertEquals("Public Domain", song.copyright)
        assertTrue(song.sections.single().lines.none { it.contains("Newton") })
    }

    @Test
    fun `the frontmatter written out carries only the metadata the song has`() {
        val bare = ParsedSong("Bare", sections = listOf(SongSection("Verse 1", listOf("a line"))))
        assertTrue(MarkdownToSongConverter.buildSongContent(bare).startsWith("[Primary]"))

        val full = ParsedSong(
            "Full",
            author = "John Newton",
            composer = "William Walker",
            copyright = "Public Domain",
            sections = listOf(SongSection("Verse 1", listOf("a line"))),
        )
        val content = MarkdownToSongConverter.buildSongContent(full)
        assertTrue(content.contains("author: John Newton"))
        assertTrue(content.contains("composer: William Walker"))
        assertTrue(content.contains("copyright: Public Domain"))
    }

    @Test
    fun `a document holding nothing at all yields no songs`() {
        assertTrue(MarkdownToSongConverter.parseMarkdown("   \n\n  ", "document.docx").isEmpty())
    }
}
