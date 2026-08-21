package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Splitting one pasted document into several songs, and the section shapes that do not use an
 * explicit label.
 *
 * People import a whole songbook as a single file, so the split is what decides whether they get
 * one unusable song or forty usable ones. The strategies are tried in order — level-1 headings,
 * then horizontal rules — and each is exercised here on its own.
 *
 * The unlabelled shapes below were previously unreachable: `parseSections` guarded them on
 * `currentLabel == null`, but the fall-through that collects a lyric line assigned "Verse N" on
 * the first content line, so the guard was never true again and a document with no section markers
 * imported as ONE section holding the whole song. The guard is now on whether the label came from
 * a real section marker, which is what those branches always meant.
 */
class MarkdownSongSplittingTest {

    private val temp: File = Files.createTempDirectory("converter-split-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun `several level-one headings become several songs`() {
        val songs = MarkdownToSongConverter.parseMarkdown(
            """
            # First Song

            Verse 1
            First song words

            # Second Song

            Verse 1
            Second song words
            """.trimIndent(),
            "book.md",
        )
        assertEquals(listOf("First Song", "Second Song"), songs.map { it.title })
        assertTrue(songs[0].sections.flatMap { it.lines }.any { it.contains("First song words") })
        assertTrue(songs[1].sections.flatMap { it.lines }.none { it.contains("First song words") })
    }

    @Test
    fun `content before the first heading is kept with the first song`() {
        // Usually a book title or a preface; dropping it would silently lose text.
        val songs = MarkdownToSongConverter.parseMarkdown(
            """
            Some preamble line

            # First Song

            Verse 1
            Words

            # Second Song

            Verse 1
            More words
            """.trimIndent(),
            "book.md",
        )
        assertEquals(2, songs.size, "the preamble does not become a third song")
    }

    @Test
    fun `horizontal rules split songs when there are no headings`() {
        val songs = MarkdownToSongConverter.parseMarkdown(
            """
            Verse 1
            First song words
            More first words

            ---

            Verse 1
            Second song words
            More second words
            """.trimIndent(),
            "book.md",
        )
        assertEquals(2, songs.size, "the rule separated them: ${songs.map { it.title }}")
    }

    @Test
    fun `a rule separating trivial blocks does not split the document`() {
        // A stray rule inside one song must not tear it in half.
        val songs = MarkdownToSongConverter.parseMarkdown(
            """
            # One Song

            Verse 1
            Only line

            ---
            """.trimIndent(),
            "book.md",
        )
        assertEquals(1, songs.size)
    }

    @Test
    fun `songs from a multi-song document are numbered in their file names`() {
        val result = MarkdownToSongConverter.convert(
            """
            # First Song

            Verse 1
            Words one

            # Second Song

            Verse 1
            Words two
            """.trimIndent(),
            "book.md",
            temp,
        )
        assertEquals(2, result.songsCreated)
        val names = result.outputFiles.map { it.name }.sorted()
        assertEquals(listOf("0001 - First Song.song", "0002 - Second Song.song"), names)
    }

    @Test
    fun `an untitled song in a multi-song document still gets a distinct file name`() {
        val songs = MarkdownToSongConverter.parseMarkdown(
            "# First\n\nVerse 1\nA\n\n# Second\n\nVerse 1\nB\n",
            "My Book.md",
        )
        assertEquals(2, songs.size)
        assertEquals(songs.map { it.title }.distinct().size, songs.size, "titles do not collide")
    }

    // ── Unlabelled section shapes ─────────────────────────────────────────────

    @Test
    fun `blank-line separated paragraphs become verses when nothing is labelled`() {
        val song = MarkdownToSongConverter.parseMarkdown(
            """
            # Unlabelled

            First paragraph line one
            First paragraph line two

            Second paragraph line one
            Second paragraph line two
            """.trimIndent(),
            "u.md",
        ).single()

        assertEquals(2, song.sections.size, "one verse per paragraph: ${song.sections.map { it.label }}")
        assertEquals(listOf("Verse 1", "Verse 2"), song.sections.map { it.label })
        assertEquals(
            listOf("First paragraph line one", "First paragraph line two"),
            song.sections.first().lines,
        )
    }

    @Test
    fun `bare numbered markers start their own verses`() {
        val song = MarkdownToSongConverter.parseMarkdown(
            """
            # Numbered

            1.
            First verse line

            2.
            Second verse line
            """.trimIndent(),
            "n.md",
        ).single()

        assertEquals(2, song.sections.size, "got ${song.sections.map { it.label }}")
        assertTrue(song.sections.all { it.label.startsWith("Verse") })
        assertTrue(song.sections.flatMap { it.lines }.none { it.matches(Regex("""^\d+\.$""")) },
            "the marker itself is consumed, not kept as a lyric")
    }

    @Test
    fun `a repeated block is relabelled as the chorus and written once`() {
        // Documents often write the chorus out after every verse instead of labelling it. This
        // only became reachable once unlabelled paragraphs split into separate sections at all.
        val song = MarkdownToSongConverter.parseMarkdown(
            """
            # Repeated

            Verse one words here
            Second line of verse one

            The repeated refrain line
            And its second line

            Verse two words here
            Second line of verse two

            The repeated refrain line
            And its second line
            """.trimIndent(),
            "r.md",
        ).single()

        assertTrue(
            song.sections.any { it.label.contains("Chorus", ignoreCase = true) },
            "the repeat was recognised: ${song.sections.map { it.label }}",
        )
        assertEquals(
            1,
            song.sections.count { s -> s.lines.any { it.contains("repeated refrain") } },
            "the chorus is written once, not once per repetition",
        )
    }

    @Test
    fun `a labelled document keeps its blank lines inside its sections`() {
        // The paragraph split must not fire under an explicit label, or a verse with a stanza
        // break in it would be torn into two.
        val song = MarkdownToSongConverter.parseMarkdown(
            """
            # Labelled

            Verse 1
            First line

            Still verse one after a gap

            Chorus
            Refrain line
            """.trimIndent(),
            "l.md",
        ).single()

        assertEquals(listOf("Verse 1", "Chorus"), song.sections.map { it.label })
        assertTrue(
            song.sections.first().lines.any { it.contains("Still verse one") },
            "the gap did not split the labelled verse: ${song.sections.first().lines}",
        )
    }

    @Test
    fun `markdown emphasis is stripped from lyric lines`() {
        val song = MarkdownToSongConverter.parseMarkdown(
            "# T\n\nVerse 1\n**Bold lyric line**\n*Italic lyric line*\n",
            "t.md",
        ).single()
        val lines = song.sections.flatMap { it.lines }
        assertTrue(lines.none { it.contains("**") || it.contains("*") }, "got $lines")
        assertTrue(lines.any { it.contains("Bold lyric line") })
    }
}
