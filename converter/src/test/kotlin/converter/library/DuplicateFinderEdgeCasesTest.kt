package converter.library

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a real library throws at the duplicate finder besides two clean copies of one song:
 * malformed song files, songs that share a single line, a title match over unrelated lyrics, and
 * files that are not UTF-8 at all.
 *
 * The delete downstream is what makes the negative cases the important ones — a song grouped with
 * a song it is not is a song the user loses.
 */
class DuplicateFinderEdgeCasesTest {

    private val temp: File = Files.createTempDirectory("converter-dup-edges").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun write(folder: String, fileName: String, body: String): File {
        val dir = File(temp, folder).apply { mkdirs() }
        return File(dir, "$fileName.song").apply { writeText(body, Charsets.UTF_8) }
    }

    private fun song(folder: String, fileName: String, title: String, vararg lyrics: String): File =
        write(
            folder, fileName,
            buildString {
                appendLine("[Primary]")
                appendLine("title: $title")
                appendLine()
                appendLine("[Verse 1]")
                lyrics.forEach { appendLine(it) }
            },
        )

    private val grace = arrayOf(
        "Amazing grace how sweet the sound",
        "That saved a wretch like me",
        "I once was lost but now am found",
        "Was blind but now I see",
    )

    private val fortress = arrayOf(
        "A mighty fortress is our God",
        "A bulwark never failing",
        "Our helper he amid the flood",
        "Of mortal ills prevailing",
    )

    // ── Matching by song number ───────────────────────────────────────────────

    @Test
    fun `a number that appears once is not a duplicate of anything`() {
        song("a", "0407 - Grace", "Grace", *grace)
        song("b", "0408 - Fortress", "Fortress", *fortress)
        val groups = DuplicateFinder.findDuplicates(temp, matchByNumber = true, matchByTitle = false)
        assertTrue(groups.isEmpty(), "got ${groups.map { it.reason }}")
    }

    @Test
    fun `a file with no leading number is left out of number matching`() {
        song("a", "Grace", "Grace", *grace)
        song("b", "Grace", "Grace", *grace)
        val groups = DuplicateFinder.findDuplicates(temp, matchByNumber = true, matchByTitle = false)
        assertTrue(groups.none { it.reason == "Same song number" })
    }

    // ── Matching by title ─────────────────────────────────────────────────────

    @Test
    fun `two songs sharing a title but nothing else are left apart`() {
        // A title collision is common in a merged library ("Holy, Holy" twice, two different
        // hymns), so the lyrics still have to agree before anything is grouped.
        song("a", "Holy", "Holy", *grace)
        song("b", "Holy", "Holy", "Completely different words that share nothing at all")
        val groups = DuplicateFinder.findDuplicates(temp, matchByTitle = true)
        assertTrue(groups.isEmpty(), "got ${groups.map { it.reason }}")
    }

    @Test
    fun `a filename that already matches the title is only indexed once`() {
        song("a", "Grace", "Grace", *grace)
        song("b", "Grace", "Grace", *grace)
        val group = DuplicateFinder.findDuplicates(temp, matchByTitle = true).single()
        assertEquals(2, group.songs.size)
    }

    @Test
    fun `a numbered filename is matched against a plain one of the same title`() {
        song("a", "0407 - Amazing Grace", "Amazing Grace", *grace)
        song("b", "Amazing Grace", "Amazing Grace", *grace)
        val group = DuplicateFinder.findDuplicates(temp, matchByTitle = true).single()
        assertEquals("Same title", group.reason)
    }

    // ── Matching by content ───────────────────────────────────────────────────

    @Test
    fun `a third copy joins the group its two siblings already formed`() {
        song("a", "one", "First Title", *grace)
        song("b", "two", "Second Title", *grace)
        song("c", "three", "Third Title", *grace)
        val group = DuplicateFinder.findDuplicates(temp, matchByTitle = false).single()
        assertEquals("Similar lyrics", group.reason)
        assertEquals(3, group.songs.size)
        assertEquals(3, group.similarities.size)
    }

    @Test
    fun `songs sharing a single line are not candidates`() {
        song("a", "one", "First", "Hallelujah", "Verses entirely of its own", "Nothing else in common")
        song("b", "two", "Second", "Hallelujah", "A different second line here", "And a different third")
        val groups = DuplicateFinder.findDuplicates(temp, matchByTitle = false)
        assertTrue(groups.isEmpty(), "got ${groups.map { it.reason }}")
    }

    @Test
    fun `a line shared by a whole songbook is too common to pair anything on`() {
        // The inverted index drops any line appearing in more than 50 songs: in a real library
        // that is "Hallelujah" or "Amen", and pairing every song against every other on it turns
        // the scan quadratic for no matches.
        repeat(60) { i ->
            song("common", "song$i", "Song $i", "Hallelujah", "Unique line number $i for this song")
        }
        val groups = DuplicateFinder.findDuplicates(temp, matchByTitle = false)
        assertTrue(groups.isEmpty(), "got ${groups.size} groups")
    }

    @Test
    fun `a near-miss line still counts through fuzzy matching`() {
        song("a", "one", "First", *grace)
        song("b", "two", "Second", "Amazing grace how sweet the sownd", *grace.drop(1).toTypedArray())
        val group = DuplicateFinder.findDuplicates(temp, matchByTitle = false).single()
        assertEquals("Similar lyrics", group.reason)
    }

    @Test
    fun `a song whose lines are a subset of another is scored against the shorter one`() {
        song("a", "long", "Long", *grace)
        song("b", "short", "Short", grace[0], grace[1])
        val group = DuplicateFinder.findDuplicates(temp, matchByTitle = false).single()
        assertEquals(1.0, group.similarities.last(), 0.0001)
    }

    // ── Parsing a `.song` file ────────────────────────────────────────────────

    private fun titleAndLyrics(file: File): Pair<String, String> =
        DuplicateFinder.scanSongs(file.parentFile).single().let { it.title to it.lyricsText }

    @Test
    fun `a second title line is ignored rather than overwriting the first`() {
        val file = write(
            "titles", "two-titles",
            """
            [Primary]
            title: The Real Title
            title: A Later Mistake

            [Verse 1]
            One line of lyrics here
            """.trimIndent(),
        )
        assertEquals("The Real Title", titleAndLyrics(file).first)
    }

    @Test
    fun `a song with no title line is named after its file`() {
        val file = write("untitled", "Named By File", "[Primary]\n\n[Verse 1]\nSome words to sing\n")
        assertEquals("Named By File", titleAndLyrics(file).first)
    }

    @Test
    fun `everything from the secondary language on is left out`() {
        val file = write(
            "bilingual", "two-languages",
            """
            [Primary]
            title: Bilingual

            [Verse 1]
            The English line
            [Secondary]
            [Verse 1]
            The other language line
            """.trimIndent(),
        )
        val (_, lyrics) = titleAndLyrics(file)
        assertTrue(lyrics.contains("The English line"))
        assertFalse(lyrics.contains("other language"), "got '$lyrics'")
    }

    @Test
    fun `section headers before the primary marker do not open a section`() {
        val file = write(
            "early", "header-first",
            """
            [Verse 1]
            A line that belongs to nothing
            [Primary]
            title: Late Primary

            [Verse 1]
            The real first line
            """.trimIndent(),
        )
        val song = DuplicateFinder.scanSongs(file.parentFile).single()
        assertEquals(listOf("Verse 1"), song.sections)
        assertEquals("The real first line", song.lyricsText)
    }

    @Test
    fun `chord and structural lines are not lyrics`() {
        val file = write(
            "markers", "structural",
            """
            [Primary]
            title: With Markers

            [Verse 1]
            {Am}
            Chorus:
            Припев
            The only real line
            """.trimIndent(),
        )
        assertEquals("The only real line", titleAndLyrics(file).second)
    }

    @Test
    fun `a line outside any section still counts as lyrics`() {
        val file = write("loose", "no-section", "[Primary]\ntitle: Loose\n\nA line with no section above it\n")
        val song = DuplicateFinder.scanSongs(file.parentFile).single()
        assertEquals("A line with no section above it", song.lyricsText)
        assertTrue(song.verses.isEmpty())
    }

    @Test
    fun `lines too short to identify a song are dropped before comparing`() {
        song("a", "one", "First", "Oh", "Amazing grace how sweet the sound", "That saved a wretch like me")
        song("b", "two", "Second", "Amazing grace how sweet the sound", "That saved a wretch like me")
        val group = DuplicateFinder.findDuplicates(temp, matchByTitle = false).single()
        assertEquals(2, group.songs.size)
    }

    @Test
    fun `two songs with no lyrics at all are identical to each other`() {
        write("empty", "first", "[Primary]\ntitle: Same Name\n")
        write("empty", "second", "[Primary]\ntitle: Same Name\n")
        val group = DuplicateFinder.findDuplicates(temp, matchByTitle = true).single()
        assertEquals(1.0, group.similarities.last(), 0.0001)
    }

    @Test
    fun `a song with no lyrics is not a duplicate of one that has them`() {
        write("mixed", "empty", "[Primary]\ntitle: Amazing Grace\n")
        song("mixed", "full", "Amazing Grace", *grace)
        val groups = DuplicateFinder.findDuplicates(temp, matchByTitle = true)
        assertTrue(groups.isEmpty(), "got ${groups.map { it.reason }}")
    }

    // ── Homoglyphs ────────────────────────────────────────────────────────────

    @Test
    fun `a line with no letters at all is not treated as Cyrillic`() {
        val file = write("glyphs", "numbers", "[Primary]\ntitle: Numbers\n\n[Verse 1]\n1 2 3 4 5\n")
        assertFalse(DuplicateFinder.hasHomoglyphs(file))
    }

    @Test
    fun `a mostly Latin line is left alone even with Cyrillic in it`() {
        val file = write("glyphs", "mixed", "[Primary]\ntitle: Mixed\n\n[Verse 1]\nSing to the Lord a song (песня)\n")
        assertEquals(0, DuplicateFinder.fixHomoglyphs(file))
    }

    @Test
    fun `frontmatter and metadata lines are never repaired`() {
        // `author:` and `tune:` can hold Cyrillic names with the same lookalikes, but rewriting a
        // metadata line would change a field the rest of the app matches on.
        val file = write(
            "glyphs", "metadata",
            """
            ---
            author: Cлава
            composer: Cлава
            tune: Cлава
            ---
            [Primary]
            title: Cлава

            [Verse 1]
            Слава Богу
            """.trimIndent(),
        )
        assertFalse(DuplicateFinder.hasHomoglyphs(file))
        assertEquals(0, DuplicateFinder.fixHomoglyphs(file))
    }

    @Test
    fun `scanning for repairs looks only at song files`() {
        val needsFixing = write("glyphs", "broken", "[Primary]\ntitle: Слава\n\n[Verse 1]\nCлава Богу\n")
        File(temp, "glyphs/notes.txt").writeText("Cлава Богу", Charsets.UTF_8)
        File(temp, "glyphs/nested").mkdirs()
        assertEquals(listOf(needsFixing), DuplicateFinder.findHomoglyphFiles(File(temp, "glyphs")))
    }

    // ── Reading files ─────────────────────────────────────────────────────────

    @Test
    fun `a UTF-8 file with a byte order mark loses the mark, not its first word`() {
        val file = File(temp, "bom.song")
        file.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "title: Слава".toByteArray(Charsets.UTF_8))
        assertEquals("title: Слава", DuplicateFinder.readFileWithFallback(file))
    }

    @Test
    fun `a Windows-1251 file is re-read in its own encoding rather than kept as mojibake`() {
        val file = File(temp, "cp1251.song")
        file.writeBytes("Слава Богу".toByteArray(Charset.forName("windows-1251")))
        assertEquals("Слава Богу", DuplicateFinder.readFileWithFallback(file))
    }

    @Test
    fun `an ASCII file is read as it stands`() {
        val file = File(temp, "plain.song")
        file.writeText("title: Grace", Charsets.UTF_8)
        assertEquals("title: Grace", DuplicateFinder.readFileWithFallback(file))
    }

    // ── Similarity scoring ────────────────────────────────────────────────────

    @Test
    fun `an empty string is unlike anything with content`() {
        assertEquals(0.0, DuplicateFinder.similarity("", "Amazing grace"), 0.0001)
        assertEquals(0.0, DuplicateFinder.similarity("Amazing grace", ""), 0.0001)
    }

    @Test
    fun `a repeated phrase does not score higher than it should`() {
        // Bigram counts are capped at the smaller of the two, so repeating a line does not let a
        // song match a different one that happens to contain it once.
        val sim = DuplicateFinder.similarity("grace grace grace", "grace")
        assertTrue(sim < 1.0, "got $sim")
    }
}
