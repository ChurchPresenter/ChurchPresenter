package converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Turning a pasted/imported document into `.song` files.
 *
 * This is the path a user's existing library arrives through, so the cases that matter are the
 * messy ones: section labels in several languages and markup styles, metadata lines that must not
 * be mistaken for lyrics, and multi-song documents that have to split into separate files.
 */
class MarkdownToSongConverterTest {

    private val temp: File = Files.createTempDirectory("converter-md-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    @Test
    fun `a blank document yields no songs`() {
        assertTrue(MarkdownToSongConverter.parseMarkdown("", "x.md").isEmpty())
        assertTrue(MarkdownToSongConverter.parseMarkdown("   \n\n  ", "x.md").isEmpty())
    }

    @Test
    fun `a song with labelled sections keeps its lyrics under each label`() {
        val songs = MarkdownToSongConverter.parseMarkdown(
            """
            # Amazing Grace

            Verse 1
            Amazing grace how sweet the sound
            That saved a wretch like me

            Chorus
            How sweet the sound
            """.trimIndent(),
            "amazing.md"
        )
        val song = songs.single()
        assertTrue(song.sections.isNotEmpty(), "sections were detected")
        val allLines = song.sections.flatMap { it.lines }
        assertTrue(allLines.any { it.contains("Amazing grace how sweet") }, "lyrics survive: $allLines")
        assertTrue(allLines.none { it.equals("Verse 1", ignoreCase = true) }, "a label is not also a lyric line")
    }

    @Test
    fun `capitalised Cyrillic section labels start their own sections`() {
        // Regression: the label/metadata patterns used `(?i)`, which in Java is ASCII-only case
        // folding unless UNICODE_CASE is set too. Lower-case `куплет` matched but `Куплет` — how
        // anyone actually writes it — did not, so a Russian document imported as ONE section
        // containing every lyric AND every label line. The library this converter exists for is
        // largely Russian, so this was the common case, not an edge one. Fixed with `(?iu)`.
        val song = MarkdownToSongConverter.parseMarkdown(
            """
            # Песня

            Куплет 1
            Первая строка

            Припев
            Припевная строка
            """.trimIndent(),
            "song.md"
        ).single()
        assertEquals(2, song.sections.size, "each label starts a section: ${song.sections.map { it.label }}")
        // Compared whole-line, not by `contains` — "Припевная строка" has "Припев" as a prefix,
        // so a substring check here would pass on the broken output too.
        assertEquals(
            listOf("Первая строка", "Припевная строка"),
            song.sections.flatMap { it.lines }.filter { it.isNotBlank() },
            "the labels are consumed, leaving only lyrics"
        )
    }

    @Test
    fun `capitalised Cyrillic metadata labels are recognised`() {
        // Same root cause as the section labels above.
        val song = MarkdownToSongConverter.parseMarkdown(
            """
            # Песня

            Автор: Иван Иванов
            Музыка: Народная

            Куплет 1
            Первая строка
            """.trimIndent(),
            "song.md"
        ).single()
        assertEquals("Иван Иванов", song.author)
        assertEquals("Народная", song.composer)
        assertTrue(
            song.sections.flatMap { it.lines }.none { it.contains("Иван Иванов") },
            "metadata does not fall through into the lyrics"
        )
    }

    @Test
    fun `a markdown-styled label is recognised without its markup`() {
        val song = MarkdownToSongConverter.parseMarkdown(
            "# T\n\n## **Chorus**\nA line of the chorus\n",
            "t.md"
        ).single()
        assertTrue(
            song.sections.flatMap { it.lines }.none { it.contains("**") },
            "heading and bold markers are consumed with the label"
        )
    }

    @Test
    fun `author, composer and copyright are read as metadata, not lyrics`() {
        val song = MarkdownToSongConverter.parseMarkdown(
            """
            # Hymn

            Author: John Newton
            Music: Traditional
            Copyright: Public Domain

            Verse 1
            Amazing grace
            """.trimIndent(),
            "hymn.md"
        ).single()
        assertEquals("John Newton", song.author)
        assertEquals("Traditional", song.composer)
        assertEquals("Public Domain", song.copyright)
        assertTrue(
            song.sections.flatMap { it.lines }.none { it.contains("John Newton") },
            "metadata does not leak into the lyrics"
        )
    }

    @Test
    fun `a document with no heading takes its first ordinary line as the title`() {
        // Plenty of pasted documents just start with the song name on a bare line, so the first
        // line that is neither metadata nor a section label is treated as the title.
        val song = MarkdownToSongConverter.parseMarkdown("Verse 1\nSome words\n", "My Song.md").single()
        assertEquals("Some words", song.title, "the section label is skipped, the next line is the title")
    }

    @Test
    fun `a document offering no usable title line falls back to the file name`() {
        val song = MarkdownToSongConverter.parseMarkdown("Verse 1\nChorus\n", "My Song.md").single()
        assertEquals("My Song", song.title, "the extension is dropped from the fallback")
    }

    // ── Building .song content ────────────────────────────────────────────────

    @Test
    fun `built content carries the title under a Primary header`() {
        val content = MarkdownToSongConverter.buildSongContent(
            ParsedSong(title = "Amazing Grace", sections = listOf(SongSection("Verse 1", listOf("Line one"))))
        )
        assertTrue(content.contains("[Primary]"))
        assertTrue(content.contains("title: Amazing Grace"))
        assertTrue(content.contains("[Verse 1]"))
        assertTrue(content.contains("Line one"))
    }

    @Test
    fun `frontmatter is written only when there is metadata to write`() {
        val bare = MarkdownToSongConverter.buildSongContent(ParsedSong(title = "T"))
        assertTrue(!bare.startsWith("---"), "no empty frontmatter block")

        val withMeta = MarkdownToSongConverter.buildSongContent(ParsedSong(title = "T", author = "A"))
        assertTrue(withMeta.startsWith("---"))
        assertTrue(withMeta.contains("author: A"))
    }

    @Test
    fun `a built song parses back with its lyrics intact`() {
        val original = ParsedSong(
            title = "Round Trip",
            author = "Someone",
            sections = listOf(SongSection("Verse 1", listOf("First line", "Second line"))),
        )
        val rebuilt = MarkdownToSongConverter.buildSongContent(original)
        assertTrue(rebuilt.contains("First line") && rebuilt.contains("Second line"))
        assertTrue(rebuilt.indexOf("title: Round Trip") < rebuilt.indexOf("[Verse 1]"), "title precedes the sections")
    }

    // ── Writing files ─────────────────────────────────────────────────────────

    @Test
    fun `converting one song writes one file named after its title`() {
        val result = MarkdownToSongConverter.convert(
            "# Amazing Grace\n\nVerse 1\nAmazing grace\n", "src.md", temp
        )
        assertEquals(1, result.songsCreated)
        assertTrue(result.errors.isEmpty(), "no errors: ${result.errors}")
        val file = result.outputFiles.single()
        assertTrue(file.exists())
        assertTrue(file.name.endsWith(".song"))
        assertTrue(file.name.contains("Amazing Grace"), "named after the title, was ${file.name}")
    }

    @Test
    fun `a document with nothing in it reports an error rather than writing files`() {
        val result = MarkdownToSongConverter.convert("   ", "empty.md", temp)
        assertEquals(0, result.songsCreated)
        assertTrue(result.outputFiles.isEmpty())
        assertTrue(result.errors.isNotEmpty(), "the user is told why nothing happened")
    }

    @Test
    fun `the output directory is created if it does not exist`() {
        val nested = File(temp, "a/b/c")
        assertTrue(!nested.exists())
        val result = MarkdownToSongConverter.convert("# T\n\nVerse 1\nWords\n", "s.md", nested)
        assertTrue(nested.exists())
        assertEquals(1, result.songsCreated)
    }

    @Test
    fun `a title with path characters cannot escape the output directory`() {
        // Titles come from arbitrary user documents, so a "/" or ".." in one must not steer the
        // write anywhere but the chosen folder.
        val result = MarkdownToSongConverter.convert(
            "# ../../evil: name?\n\nVerse 1\nWords\n", "s.md", temp
        )
        val file = result.outputFiles.single()
        assertEquals(
            temp.canonicalPath,
            file.canonicalFile.parentFile.path,
            "the file lands directly in the output directory, was ${file.canonicalPath}"
        )
        assertTrue(!file.name.contains('/') && !file.name.contains('\\'))
    }
}
