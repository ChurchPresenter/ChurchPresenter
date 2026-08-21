package converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Splitting a Word or PDF document into songs, and naming what comes out.
 *
 * A document of songs is written for a person, not a program: several songs under one heading each,
 * or separated by a rule, or by nothing at all. The three strategies are tried in that order, and
 * the failure mode worth guarding is the one where a document imports as a *single* song holding
 * everything — the app then shows the whole booklet as one slide.
 */
class MarkdownSongDocumentTest {

    private val temp: File = Files.createTempDirectory("markdown-document").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun parse(markdown: String, fileName: String = "songbook.docx") =
        MarkdownToSongConverter.parseMarkdown(markdown.trimIndent(), fileName)

    // ── Splitting a document into songs ───────────────────────────────────────

    @Test
    fun `each level-one heading opens a song`() {
        val songs = parse(
            """
            # Amazing Grace

            Verse 1
            Amazing grace how sweet the sound

            # Be Thou My Vision

            Verse 1
            Be thou my vision O Lord of my heart
            """
        )
        assertEquals(listOf("Amazing Grace", "Be Thou My Vision"), songs.map { it.title })
    }

    @Test
    fun `what stands before the first heading belongs to the song it introduces`() {
        val songs = parse(
            """
            Author: John Newton

            # Amazing Grace

            Verse 1
            Amazing grace how sweet the sound

            # Be Thou My Vision

            Verse 1
            Be thou my vision
            """
        )
        assertEquals("John Newton", songs.first().author)
        assertEquals("", songs.last().author)
    }

    @Test
    fun `a horizontal rule separates songs when no headings do`() {
        val songs = parse(
            """
            Amazing Grace

            Amazing grace how sweet the sound

            ---

            Be Thou My Vision

            Be thou my vision O Lord of my heart
            """
        )
        assertEquals(listOf("Amazing Grace", "Be Thou My Vision"), songs.map { it.title })
    }

    @Test
    fun `a row of asterisks separates songs the same way`() {
        val songs = parse(
            """
            Amazing Grace

            Amazing grace how sweet

            ***

            Be Thou My Vision

            Be thou my vision
            """
        )
        assertEquals(2, songs.size)
    }

    @Test
    fun `a rule with nothing substantial either side is decoration, not a separator`() {
        val songs = parse(
            """
            # Amazing Grace

            ---

            Verse 1
            Amazing grace how sweet the sound
            """
        )
        assertEquals(1, songs.size)
        assertEquals("Amazing Grace", songs.single().title)
    }

    @Test
    fun `slide markers make sections, not separate songs`() {
        val songs = parse(
            """
            Amazing Grace

            <!-- slide -->
            Amazing grace how sweet the sound

            <!-- slide -->
            Twas grace that taught my heart to fear
            """
        )
        assertEquals(1, songs.size)
    }

    @Test
    fun `a document holding one song stays one song`() {
        val songs = parse(
            """
            Amazing Grace

            Verse 1
            Amazing grace how sweet the sound
            """
        )
        assertEquals(1, songs.size)
    }

    // ── Naming a song ─────────────────────────────────────────────────────────

    @Test
    fun `a bold heading loses its markup`() {
        assertEquals("Amazing Grace", parse("# **Amazing Grace**\n\nAmazing grace how sweet").single().title)
        assertEquals("Amazing Grace", parse("**Amazing Grace**\n\nAmazing grace how sweet").single().title)
    }

    @Test
    fun `a section label is skipped over in the search for a title`() {
        // "Verse 1" is a label, not a title, so taking it would name every such song "Verse 1".
        // The next line is taken instead — for a song typed with no title of its own, that is its
        // opening lyric, which is how such a song is usually referred to anyway.
        val songs = parse(
            """
            Verse 1
            Amazing grace how sweet the sound
            """,
            fileName = "Amazing Grace.docx",
        )
        assertEquals("Amazing grace how sweet the sound", songs.single().title)
    }

    @Test
    fun `a document offering nothing that could be a title falls back to the file name`() {
        // Every line here is either a label or too long to be a title.
        val songs = parse("x".repeat(130) + "\n\nVerse 1\n\nChorus", fileName = "Long Line.docx")
        assertEquals("Long Line", songs.single().title)
    }

    @Test
    fun `a metadata line is never mistaken for the title`() {
        val songs = parse(
            """
            Author: John Newton

            Amazing Grace

            Amazing grace how sweet the sound
            """
        )
        assertEquals("Amazing Grace", songs.single().title)
        assertEquals("John Newton", songs.single().author)
    }

    // ── Repeated verses become the chorus ─────────────────────────────────────

    @Test
    fun `a paragraph repeated between verses is the chorus, written once`() {
        val song = parse(
            """
            Amazing Grace

            First verse of the song

            The repeated refrain

            Second verse of the song

            The repeated refrain
            """
        ).single()

        assertEquals(listOf("Verse 1", "Chorus", "Verse 2"), song.sections.map { it.label })
        assertEquals(listOf("The repeated refrain"), song.sections[1].lines)
    }

    @Test
    fun `a song that repeats nothing keeps every verse`() {
        val song = parse(
            """
            Amazing Grace

            First verse of the song

            Second verse of the song
            """
        ).single()
        assertEquals(listOf("Verse 1", "Verse 2"), song.sections.map { it.label })
    }

    @Test
    fun `a section the document named itself is left alone by that rule`() {
        val song = parse(
            """
            Amazing Grace

            Chorus
            The repeated refrain

            Verse 1
            First verse of the song

            Chorus
            The repeated refrain
            """
        ).single()
        assertTrue(song.sections.count { it.label.startsWith("Chorus") } >= 1)
        assertTrue(song.sections.any { it.lines == listOf("First verse of the song") })
    }

    // ── Writing the songs out ─────────────────────────────────────────────────

    @Test
    fun `one song is written under its own name`() {
        val out = File(temp, "single")
        val result = MarkdownToSongConverter.convert(
            "# Amazing Grace\n\nVerse 1\nAmazing grace how sweet",
            "songbook.docx",
            out,
        )
        assertEquals(listOf("Amazing Grace.song"), result.outputFiles.map { it.name })
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `several songs are numbered so they keep the document's order`() {
        val out = File(temp, "many")
        val result = MarkdownToSongConverter.convert(
            "# Amazing Grace\n\nAmazing grace how sweet\n\n# Be Thou My Vision\n\nBe thou my vision",
            "songbook.docx",
            out,
        )
        assertEquals(listOf("0001 - Amazing Grace.song", "0002 - Be Thou My Vision.song"),
            result.outputFiles.map { it.name })
    }

    @Test
    fun `a song with no title of its own is named after the document it came from`() {
        val out = File(temp, "untitled")
        val result = MarkdownToSongConverter.convert("Verse 1\n\nChorus", "My Songs.docx", out)
        assertEquals(listOf("My Songs.song"), result.outputFiles.map { it.name })
    }

    @Test
    fun `a document with nothing in it reports that rather than writing an empty folder`() {
        val result = MarkdownToSongConverter.convert("   \n\n  ", "empty.docx", File(temp, "none"))
        assertTrue(result.outputFiles.isEmpty())
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `a character a file name cannot hold is replaced rather than failing the write`() {
        val out = File(temp, "sanitized")
        val result = MarkdownToSongConverter.convert(
            "# Grace / Mercy: A Song\n\nVerse 1\nAmazing grace",
            "songbook.docx",
            out,
        )
        val written = result.outputFiles.single()
        assertTrue(written.isFile)
        assertFalse(written.name.contains('/'), written.name)
    }

    // ── Previewing a file that cannot be read ─────────────────────────────────

    @Test
    fun `previewing something that is not a document reports why and offers no songs`() {
        val notAPdf = File(temp, "notes.pdf").apply { writeText("this is not a PDF", Charsets.UTF_8) }
        val (message, songs) = MarkdownToSongConverter.preview(notAPdf)

        assertTrue(songs.isEmpty())
        assertTrue(message.isNotBlank())
    }
}
