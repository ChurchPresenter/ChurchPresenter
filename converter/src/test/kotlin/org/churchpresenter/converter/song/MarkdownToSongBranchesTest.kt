package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownToSongBranchesTest {

    private val temp: File = Files.createTempDirectory("markdown-branches").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun `a document of nothing but a label and no name is dropped`() {
        assertTrue(MarkdownToSongConverter.parseMarkdown("Verse 1", "").isEmpty())
    }

    @Test
    fun `the frontmatter is written for a composer or a CCLI number alone`() {
        val composerOnly = MarkdownToSongConverter.buildSongContent(ParsedSong("T", composer = "Bach"))
        assertTrue(composerOnly.startsWith("---\ncomposer: Bach\n---"), composerOnly)
        assertFalse(composerOnly.contains("author:"))

        val ccliOnly = MarkdownToSongConverter.buildSongContent(ParsedSong("T", ccli = "123"))
        assertTrue(ccliOnly.startsWith("---\nccli: 123\n---"), ccliOnly)

        val none = MarkdownToSongConverter.buildSongContent(ParsedSong("T"))
        assertTrue(none.startsWith("[Primary]"), none)
    }

    @Test
    fun `a song that cannot be written is reported and the result counts none`() {
        val blocked = File(temp, "not-a-folder").apply { writeText("x") }
        val result = MarkdownToSongConverter.convert("Title\n\nVerse 1\nAmazing grace", "doc.md", blocked)
        assertEquals(0, result.songsCreated)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().startsWith("Error writing song 1:"), result.errors.single())
    }

    @Test
    fun `consecutive and trailing rules leave no empty songs`() {
        val markdown = "Song A\nline a1\nline a2\n---\n\n---\nSong B\nline b1\nline b2\n---"
        val songs = MarkdownToSongConverter.parseMarkdown(markdown, "doc.md")
        assertEquals(listOf("Song A", "Song B"), songs.map { it.title })
    }

    @Test
    fun `a bold label written as a heading with its colon inside is still a label`() {
        val song = MarkdownToSongConverter.parseMarkdown("# Hymn\n## **Chorus:**\nPraise him", "h.md").single()
        assertEquals(listOf("Chorus"), song.sections.map { it.label })
        assertEquals(listOf("Praise him"), song.sections.single().lines)
    }

    @Test
    fun `a sub-heading that is not a label is a lyric`() {
        val song = MarkdownToSongConverter.parseMarkdown("# Hymn\nVerse 1\n## Not a label\nsecond", "h.md").single()
        assertEquals(listOf("## Not a label", "second"), song.sections.single().lines)
    }

    @Test
    fun `repeated unlabelled paragraphs become one chorus while explicit sections are kept`() {
        val markdown = "# Hymn\n\nfirst verse\n\nsame refrain\n\nsecond verse\n\nsame refrain\n\nBridge\nbridge line"
        val song = MarkdownToSongConverter.parseMarkdown(markdown, "h.md").single()
        val labels = song.sections.map { it.label }
        assertEquals(1, labels.count { it == "Chorus" })
        assertTrue("Bridge" in labels, labels.toString())
        assertEquals("Verse 1", labels.first())
    }

    @Test
    fun `every spelling of a section kind is normalized`() {
        val markdown = listOf(
            "Strophe 1", "a", "Refrain", "b", "Мост", "c", "Prechorus", "d", "Outro", "e",
            "Вступление", "f", "Coda", "g", "Tag", "h", "Конец", "i",
        ).joinToString("\n")
        val song = MarkdownToSongConverter.parseMarkdown("# T\n$markdown", "t.md").single()
        assertEquals(
            listOf("Verse 1", "Chorus", "Bridge", "Pre-Chorus", "Ending", "Intro", "Coda", "Tag", "Ending"),
            song.sections.map { it.label },
        )
    }

    @Test
    fun `text before the first of several headings belongs to the first song`() {
        val markdown = "Opening line\nsecond line\n# Song A\nline a1\n# Song B\nline b1"
        val songs = MarkdownToSongConverter.parseMarkdown(markdown, "doc.md")
        assertEquals(2, songs.size)
        val first = songs.first()
        val text = (listOf(first.title) + first.sections.flatMap { it.lines }).joinToString("\n")
        assertTrue("Opening line" in text && "line a1" in text && "line b1" !in text, text)
    }

    @Test
    fun `blank lines before the first heading add nothing to it`() {
        val songs = MarkdownToSongConverter.parseMarkdown("\n\n# Song A\nline a1\n# Song B\nline b1", "doc.md")
        assertEquals(listOf("Song A", "Song B"), songs.map { it.title })
    }

    @Test
    fun `a rule with only blank lines after it ends the last song`() {
        val markdown = "Song A\nline a1\nline a2\n***\nSong B\nline b1\nline b2\n---\n\n"
        val songs = MarkdownToSongConverter.parseMarkdown(markdown, "doc.md")
        assertEquals(listOf("Song A", "Song B"), songs.map { it.title })
    }
}
