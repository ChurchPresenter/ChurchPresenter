package converter.library

import converter.song.MarkdownToSongConverter
import converter.song.ParsedSong
import converter.song.SongSection

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Finding duplicate songs across a library, and the homoglyph repair that goes with it.
 *
 * Both matter because the destructive action downstream is a delete. The homoglyph half exists
 * because Cyrillic lyrics routinely contain Latin lookalikes (`о`/`o`, `е`/`e`, `а`/`a`) typed by
 * accident — two copies of the same song that differ only in those characters are byte-different
 * but identical to a reader, so they have to compare equal or the duplicate is never found.
 */
class DuplicateFinderTest {

    private val temp: File = Files.createTempDirectory("converter-dup-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun song(dir: File, name: String, title: String, vararg lyrics: String): File {
        dir.mkdirs()
        val file = File(dir, "$name.song")
        file.writeText(
            buildString {
                appendLine("[Primary]")
                appendLine("title: $title")
                appendLine()
                appendLine("[Verse 1]")
                lyrics.forEach { appendLine(it) }
            },
            Charsets.UTF_8,
        )
        return file
    }

    // ── Similarity ────────────────────────────────────────────────────────────

    @Test
    fun `identical text is perfectly similar`() {
        assertEquals(1.0, DuplicateFinder.similarity("amazing grace", "amazing grace"), 0.0001)
    }

    @Test
    fun `similarity ignores case, punctuation and spacing`() {
        assertTrue(
            DuplicateFinder.similarity("Amazing Grace!", "amazing   grace") > 0.99,
            "only letters and digits carry meaning for a match"
        )
    }

    @Test
    fun `unrelated text scores near zero`() {
        assertTrue(DuplicateFinder.similarity("amazing grace", "zzzz qqqq") < 0.2)
    }

    @Test
    fun `a Latin lookalike inside Cyrillic text still compares equal`() {
        // "Слава" with a Latin "a" in place of the Cyrillic one — indistinguishable on screen.
        val cyrillic = "Слава Богу"
        val withLatinA = "Сл${'а'}в${'a'} Богу"   // second 'a' is Latin U+0061
        assertTrue(
            DuplicateFinder.similarity(cyrillic, withLatinA) > 0.95,
            "homoglyphs are folded before comparison, got ${DuplicateFinder.similarity(cyrillic, withLatinA)}"
        )
    }

    @Test
    fun `strings too short to have bigrams compare as identical`() {
        // Recorded rather than asserted-as-desirable: similarity is Dice over character bigrams,
        // and a string under two characters has none, so any two of them score 1.0. Harmless for
        // real input — titles and lyrics are never one character — but worth knowing before
        // reusing `similarity` for something shorter.
        assertEquals(1.0, DuplicateFinder.similarity("a", ""), 0.0001)
        assertEquals(1.0, DuplicateFinder.similarity("a", "b"), 0.0001)
        // A short string against a long one still scores 0, so this cannot group real songs.
        assertEquals(0.0, DuplicateFinder.similarity("a", "amazing grace"), 0.0001)
    }

    @Test
    fun `a song file with no frontmatter is still read for its title and lyrics`() {
        // Regression: parseSong skipped every line until a `---` block closed, so a file without
        // one parsed as titleless and lyricless. MarkdownToSongConverter omits frontmatter for any
        // song with no author/composer/copyright, so this converter's own output was invisible to
        // its own duplicate finder.
        val file = File(temp, "no-frontmatter.song")
        file.writeText(
            MarkdownToSongConverter.buildSongContent(
                ParsedSong(
                    title = "Amazing Grace",
                    sections = listOf(SongSection("Verse 1", listOf("Amazing grace how sweet"))),
                )
            ),
            Charsets.UTF_8,
        )
        val parsed = DuplicateFinder.scanSongs(temp).single()
        assertEquals("Amazing Grace", parsed.title, "the title comes from the content, not the filename")
        assertTrue(parsed.lyricsText.contains("Amazing grace how sweet"), "lyrics are read: '${parsed.lyricsText}'")
    }

    @Test
    fun `a song file with frontmatter is read the same way`() {
        val file = File(temp, "with-frontmatter.song")
        file.writeText(
            MarkdownToSongConverter.buildSongContent(
                ParsedSong(
                    title = "Amazing Grace", author = "Newton",
                    sections = listOf(SongSection("Verse 1", listOf("Amazing grace how sweet"))),
                )
            ),
            Charsets.UTF_8,
        )
        val parsed = DuplicateFinder.scanSongs(temp).single()
        assertEquals("Amazing Grace", parsed.title)
        assertTrue(parsed.lyricsText.contains("Amazing grace how sweet"))
        assertTrue(!parsed.lyricsText.contains("Newton"), "frontmatter is not lyrics")
    }

    // ── Scanning ──────────────────────────────────────────────────────────────

    @Test
    fun `scanning finds song files in nested folders`() {
        song(temp, "one", "First", "line")
        song(File(temp, "nested/deeper"), "two", "Second", "line")
        val found = DuplicateFinder.scanSongs(temp)
        assertEquals(2, found.size, "the walk is recursive")
    }

    @Test
    fun `scanning ignores files that are not songs`() {
        song(temp, "real", "Real", "line")
        File(temp, "notes.txt").writeText("not a song")
        assertEquals(1, DuplicateFinder.scanSongs(temp).size)
    }

    @Test
    fun `an unreadable song does not abort the whole scan`() {
        song(temp, "good", "Good", "line")
        File(temp, "broken.song").writeBytes(byteArrayOf(0x00, 0x01, 0x02))
        assertTrue(DuplicateFinder.scanSongs(temp).isNotEmpty(), "one bad file cannot hide the rest")
    }

    // ── Duplicate grouping ────────────────────────────────────────────────────

    @Test
    fun `two songs with the same title and lyrics are grouped`() {
        song(File(temp, "a"), "grace", "Amazing Grace", "Amazing grace how sweet the sound")
        song(File(temp, "b"), "grace", "Amazing Grace", "Amazing grace how sweet the sound")

        val groups = DuplicateFinder.findDuplicates(temp)
        assertEquals(1, groups.size, "one duplicate group")
        assertEquals(2, groups.single().songs.size)
    }

    @Test
    fun `songs that merely share a word are not grouped`() {
        song(File(temp, "a"), "one", "Amazing Grace", "Amazing grace how sweet the sound")
        song(File(temp, "b"), "two", "Total Praise", "Lord I will lift mine eyes to the hills")

        assertTrue(DuplicateFinder.findDuplicates(temp).isEmpty(), "different songs are left alone")
    }

    // ── Resolving what to delete ──────────────────────────────────────────────

    @Test
    fun `duplicates outside the keep folder are the ones deleted`() {
        val keep = File(temp, "keep")
        val other = File(temp, "other")
        song(keep, "grace", "Amazing Grace", "Amazing grace how sweet the sound")
        val doomed = song(other, "grace", "Amazing Grace", "Amazing grace how sweet the sound")

        val deletes = DuplicateFinder.resolveDeletes(DuplicateFinder.findDuplicates(temp), keep)
        assertEquals(listOf(doomed.canonicalPath), deletes.map { it.canonicalPath })
    }

    @Test
    fun `a group with nothing in the keep folder loses nothing`() {
        // Deleting here would remove a song the library has no other copy of inside the folder
        // the user chose to keep — so the safe answer is to delete none of them.
        val a = File(temp, "a")
        val b = File(temp, "b")
        song(a, "grace", "Amazing Grace", "Amazing grace how sweet the sound")
        song(b, "grace", "Amazing Grace", "Amazing grace how sweet the sound")

        val deletes = DuplicateFinder.resolveDeletes(DuplicateFinder.findDuplicates(temp), File(temp, "keep"))
        assertTrue(deletes.isEmpty(), "no keeper means no deletions")
    }

    @Test
    fun `extra copies inside the keep folder are reduced to one`() {
        val keep = File(temp, "keep")
        song(keep, "grace1", "Amazing Grace", "Amazing grace how sweet the sound")
        song(keep, "grace2", "Amazing Grace", "Amazing grace how sweet the sound")

        val groups = DuplicateFinder.findDuplicates(temp)
        val deletes = DuplicateFinder.resolveDeletes(groups, keep)
        assertEquals(1, deletes.size, "one of the two copies survives")
    }

    // ── Homoglyphs ────────────────────────────────────────────────────────────

    @Test
    fun `a Cyrillic lyric containing Latin lookalikes is flagged`() {
        val file = song(temp, "mixed", "Слава", "Славa Богу")   // Latin 'a' in the lyric
        assertTrue(DuplicateFinder.hasHomoglyphs(file))
    }

    @Test
    fun `an English song is not flagged`() {
        val file = song(temp, "english", "Amazing Grace", "Amazing grace how sweet the sound")
        assertTrue(!DuplicateFinder.hasHomoglyphs(file), "Latin letters in a Latin song are correct")
    }

    @Test
    fun `fixing replaces the lookalikes and reports how many`() {
        val file = song(temp, "mixed", "Слава", "Славa Богу")
        val fixed = DuplicateFinder.fixHomoglyphs(file)
        assertTrue(fixed > 0, "at least the Latin 'a' was replaced")
        assertTrue(!DuplicateFinder.hasHomoglyphs(file), "the file is clean afterwards")
        assertTrue(file.readText().contains("Слава Богу"), "the text now reads correctly")
    }

    @Test
    fun `fixing a clean file changes nothing`() {
        val file = song(temp, "clean", "Слава", "Слава Богу")
        val before = file.readText()
        assertEquals(0, DuplicateFinder.fixHomoglyphs(file))
        assertEquals(before, file.readText(), "an untouched file is not rewritten")
    }

    @Test
    fun `metadata and structural lines are left alone`() {
        // `title:` and `[Verse 1]` are protocol, not lyrics — rewriting their ASCII letters into
        // Cyrillic would corrupt the file format itself.
        val file = File(temp, "meta.song")
        file.writeText("[Primary]\ntitle: Слава\n\n[Verse 1]\nСлавa Богу\n", Charsets.UTF_8)
        DuplicateFinder.fixHomoglyphs(file)
        val text = file.readText()
        assertTrue(text.contains("[Primary]"), "section markers survive")
        assertTrue(text.contains("title: "), "the title key survives")
        assertTrue(text.contains("[Verse 1]"))
    }

    @Test
    fun `scanning a folder returns only the files needing repair`() {
        song(temp, "clean", "Слава", "Слава Богу")
        val dirty = song(temp, "dirty", "Слава", "Славa Богу")
        assertEquals(
            listOf(dirty.canonicalPath),
            DuplicateFinder.findHomoglyphFiles(temp).map { it.canonicalPath },
        )
    }
}
