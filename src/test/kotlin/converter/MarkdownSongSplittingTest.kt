package converter

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
 * **Known gap, deliberately not asserted here.** `parseSections` also *means* to split unlabelled
 * paragraphs into verses — on a blank line, on a bare `1.` marker, and by relabelling a repeated
 * block as the chorus. None of those three branches can ever run: the fall-through that collects a
 * lyric line sets `currentLabel = "Verse N"` on the first content line, and all three are guarded
 * by `currentLabel == null`. So a document with no section labels imports as one verse holding the
 * entire song, which the app then shows as a single slide.
 *
 * Tests for that are absent rather than written against the broken output, because a test that
 * asserts a defect is what keeps the defect — this repo has been bitten by exactly that before.
 * Fixing it changes how existing documents import, so it deserves a deliberate decision rather
 * than arriving as a side effect of adding tests.
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
