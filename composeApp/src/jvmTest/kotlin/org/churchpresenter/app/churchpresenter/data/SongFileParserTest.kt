package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every song in a user's library is read through [SongFileParser]. A parsing mistake here doesn't
 * throw — it silently produces a song with a wrong title, missing verses, or lyrics merged into
 * the wrong language column, and the operator only finds out on screen.
 *
 * The format: an optional `---` YAML-ish header, then `[Primary]` / `[Secondary]` sections each
 * starting with a `title:` line. Section headers like `[Verse 1]` inside the body are lyric
 * content, NOT structure — the section splitter downstream handles those.
 */
class SongFileParserTest {

    private val parser = SongFileParser()
    private lateinit var tempDir: File

    @BeforeTest
    fun createTempDir() {
        tempDir = Files.createTempDirectory("cp-songparser-test").toFile()
    }

    @AfterTest
    fun deleteTempDir() {
        tempDir.deleteRecursively()
    }

    // ── Body parsing ────────────────────────────────────────────────────────────

    @Test
    fun `a minimal song yields its title and lyrics`() {
        val song = assertNotNull(
            parser.parseSongContent(
                """
                [Primary]
                title: Amazing Grace

                Amazing grace how sweet the sound
                That saved a wretch like me
                """.trimIndent(),
            ),
        )
        assertEquals("Amazing Grace", song.title)
        assertEquals(
            listOf("Amazing grace how sweet the sound", "That saved a wretch like me"),
            song.lyrics,
        )
    }

    @Test
    fun `lyric section headers are kept as content`() {
        // [Verse 1] is a lyric line the section splitter later interprets — the parser must not
        // eat it as structure.
        val song = assertNotNull(
            parser.parseSongContent(
                """
                [Primary]
                title: Test

                [Verse 1]
                First line
                {Chorus}
                Chorus line
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("[Verse 1]", "First line", "{Chorus}", "Chorus line"), song.lyrics)
    }

    @Test
    fun `blank lines are trimmed from the ends but kept inside`() {
        val song = assertNotNull(
            parser.parseSongContent("[Primary]\ntitle: T\n\n\nFirst\n\nSecond\n\n\n"),
        )
        assertEquals(listOf("First", "", "Second"), song.lyrics, "inner blank lines separate verses")
    }

    @Test
    fun `a bilingual song splits into primary and secondary`() {
        val song = assertNotNull(
            parser.parseSongContent(
                """
                [Primary]
                title: Amazing Grace

                Amazing grace

                [Secondary]
                title: Удивительная благодать

                Удивительная благодать
                """.trimIndent(),
            ),
        )
        assertEquals("Amazing Grace", song.title)
        assertEquals(listOf("Amazing grace"), song.lyrics)
        assertEquals("Удивительная благодать", song.secondaryTitle)
        assertEquals(listOf("Удивительная благодать"), song.secondaryLyrics)
    }

    @Test
    fun `a song with no secondary section has empty secondary fields`() {
        val song = assertNotNull(parser.parseSongContent("[Primary]\ntitle: T\n\nLine"))
        assertEquals("", song.secondaryTitle)
        assertTrue(song.secondaryLyrics.isEmpty())
    }

    @Test
    fun `section tags are case-insensitive`() {
        val song = assertNotNull(
            parser.parseSongContent("[primary]\ntitle: T\n\nLine\n\n[SECONDARY]\ntitle: S\n\nLinea"),
        )
        assertEquals(listOf("Line"), song.lyrics)
        assertEquals(listOf("Linea"), song.secondaryLyrics)
        assertEquals("S", song.secondaryTitle)
    }

    @Test
    fun `content before any section tag is ignored`() {
        val song = assertNotNull(
            parser.parseSongContent("stray text\nmore stray\n[Primary]\ntitle: T\n\nReal line"),
        )
        assertEquals(listOf("Real line"), song.lyrics, "loose text must not become lyrics")
    }

    // ── Header parsing ──────────────────────────────────────────────────────────

    @Test
    fun `the metadata header is read and kept out of the lyrics`() {
        val song = assertNotNull(
            parser.parseSongContent(
                """
                ---
                author: John Newton
                composer: Traditional
                tune: NEW BRITAIN
                ccli: 22025
                ---

                [Primary]
                title: Amazing Grace

                Amazing grace
                """.trimIndent(),
            ),
        )
        assertEquals("John Newton", song.author)
        assertEquals("Traditional", song.composer)
        assertEquals("NEW BRITAIN", song.tune)
        assertEquals("22025", song.ccliNumber)
        assertEquals(listOf("Amazing grace"), song.lyrics, "header fields must not leak into lyrics")
    }

    @Test
    fun `header keys are case-insensitive and values are trimmed`() {
        val song = assertNotNull(
            parser.parseSongContent("---\nAUTHOR:   John Newton   \nTune: NEW BRITAIN\n---\n[Primary]\ntitle: T\n\nL"),
        )
        assertEquals("John Newton", song.author)
        assertEquals("NEW BRITAIN", song.tune)
    }

    @Test
    fun `an unknown header key is ignored rather than failing the parse`() {
        val song = assertNotNull(
            parser.parseSongContent("---\nauthor: A\nunknown_field: whatever\n---\n[Primary]\ntitle: T\n\nL"),
        )
        assertEquals("A", song.author)
        assertEquals("T", song.title)
    }

    @Test
    fun `a song with no header parses fine`() {
        val song = assertNotNull(parser.parseSongContent("[Primary]\ntitle: T\n\nLine"))
        assertEquals("", song.author)
        assertEquals("", song.ccliNumber)
    }

    @Test
    fun `a value containing a colon survives`() {
        // e.g. an author field with a qualifier.
        val song = assertNotNull(
            parser.parseSongContent("---\nauthor: Newton, John: the elder\n---\n[Primary]\ntitle: T\n\nL"),
        )
        assertEquals("Newton, John: the elder", song.author)
    }

    // ── Filename-derived fields ─────────────────────────────────────────────────

    @Test
    fun `the song number is taken from the filename prefix`() {
        for (name in listOf("0001 - Amazing Grace", "0001- Amazing Grace", "0001-Amazing Grace")) {
            val song = assertNotNull(
                parser.parseSongContent("[Primary]\ntitle: T\n\nL", filePath = "/songs/$name.song"),
            )
            assertEquals("0001", song.number, "failed for \"$name\"")
        }
    }

    @Test
    fun `a filename with no numeric prefix yields no number`() {
        val song = assertNotNull(
            parser.parseSongContent("[Primary]\ntitle: T\n\nL", filePath = "/songs/Amazing Grace.song"),
        )
        assertEquals("", song.number)
    }

    @Test
    fun `a number embedded mid-name is not treated as the song number`() {
        val song = assertNotNull(
            parser.parseSongContent("[Primary]\ntitle: T\n\nL", filePath = "/songs/Psalm 23 - Shepherd.song"),
        )
        assertEquals("", song.number, "only a leading number counts")
    }

    @Test
    fun `the filename is the fallback title when none is declared`() {
        val song = assertNotNull(
            parser.parseSongContent("[Primary]\n\nJust lyrics", filePath = "/songs/0007 - Fallback Name.song"),
        )
        assertEquals("0007 - Fallback Name", song.title)
    }

    @Test
    fun `the songbook is carried through untouched`() {
        val song = assertNotNull(
            parser.parseSongContent("[Primary]\ntitle: T\n\nL", songbook = "Hymnal/Russian"),
        )
        assertEquals("Hymnal/Russian", song.songbook)
    }

    // ── File I/O ────────────────────────────────────────────────────────────────

    @Test
    fun `a missing file yields null rather than throwing`() {
        assertNull(parser.parseSongFile(File(tempDir, "nope.song").path))
    }

    @Test
    fun `a song survives a write and read round-trip`() {
        val original = SongItem(
            number = "0042",
            title = "Amazing Grace",
            songbook = "Hymnal",
            tune = "NEW BRITAIN",
            author = "John Newton",
            composer = "Traditional",
            ccliNumber = "22025",
            lyrics = listOf("[Verse 1]", "Amazing grace how sweet the sound", "", "[Verse 2]", "Twas grace that taught"),
            secondaryTitle = "Удивительная благодать",
            secondaryLyrics = listOf("[Куплет 1]", "Удивительная благодать"),
        )
        val path = File(tempDir, "0042 - Amazing Grace.song").path

        parser.writeSongFile(original, path)
        val reloaded = assertNotNull(parser.parseSongFile(path, songbook = "Hymnal"))

        assertEquals(original.title, reloaded.title)
        assertEquals(original.author, reloaded.author)
        assertEquals(original.composer, reloaded.composer)
        assertEquals(original.tune, reloaded.tune)
        assertEquals(original.ccliNumber, reloaded.ccliNumber)
        assertEquals(original.lyrics, reloaded.lyrics, "lyrics must survive the round-trip exactly")
        assertEquals(original.secondaryTitle, reloaded.secondaryTitle)
        assertEquals(original.secondaryLyrics, reloaded.secondaryLyrics)
        assertEquals(original.number, reloaded.number, "the number comes back from the filename")
        assertEquals("Hymnal", reloaded.songbook)
    }

    @Test
    fun `a song with no metadata round-trips without an empty header`() {
        val original = SongItem(number = "", title = "Simple", lyrics = listOf("One line"))
        val path = File(tempDir, "Simple.song").path

        parser.writeSongFile(original, path)
        val written = File(path).readText()
        assertTrue("---" !in written, "no metadata means no header block: $written")

        val reloaded = assertNotNull(parser.parseSongFile(path))
        assertEquals("Simple", reloaded.title)
        assertEquals(listOf("One line"), reloaded.lyrics)
    }

    @Test
    fun `writing creates missing parent directories`() {
        val path = File(tempDir, "nested/deeper/Song.song").path
        parser.writeSongFile(SongItem(number = "", title = "T", lyrics = listOf("L")), path)
        assertTrue(File(path).exists())
    }

    @Test
    fun `non-ascii lyrics survive as utf-8`() {
        val original = SongItem(
            number = "",
            title = "Тест",
            lyrics = listOf("Слава Богу", "Ἐν ἀρχῇ ἦν ὁ λόγος"),
        )
        val path = File(tempDir, "utf8.song").path
        parser.writeSongFile(original, path)

        val reloaded = assertNotNull(parser.parseSongFile(path))
        assertEquals("Тест", reloaded.title)
        assertEquals(original.lyrics, reloaded.lyrics)
    }

    // ── Degradation ─────────────────────────────────────────────────────────────

    @Test
    fun `an empty or structureless file still produces a song rather than null`() {
        // A song with no [Primary] tag has no lyrics, but must not break a library load.
        val song = assertNotNull(parser.parseSongContent("", filePath = "/songs/Empty.song"))
        assertEquals("Empty", song.title, "falls back to the filename")
        assertTrue(song.lyrics.isEmpty())
    }

    // ── Headers that are not well formed ────────────────────────────────────────

    @Test
    fun `a header line with no colon is skipped rather than ending the parse`() {
        // Hand-edited `.song` files carry stray lines in the header block — a note, a leftover
        // word. There is no key to read, and the rest of the header still has to be picked up.
        val song = assertNotNull(
            parser.parseSongContent(
                """
                ---
                just a stray line
                : leading colon with no key
                author: John Newton
                ---

                [Primary]
                title: Amazing Grace

                Amazing grace
                """.trimIndent(),
            ),
        )

        assertEquals("John Newton", song.author, "the well-formed line after the junk still reads")
        assertEquals(listOf("Amazing grace"), song.lyrics)
    }

    @Test
    fun `a header that is opened and never closed does not swallow the song`() {
        // A truncated write, or a file edited to death. The opening `---` has no partner, so
        // there is no end index to slice at — the whole file would otherwise be read as header
        // and the song would come back with no lyrics at all.
        val song = assertNotNull(
            parser.parseSongContent(
                """
                ---
                author: John Newton

                [Primary]
                title: Amazing Grace

                Amazing grace
                """.trimIndent(),
            ),
        )

        assertEquals("Amazing Grace", song.title, "the song still names itself")
        assertEquals(listOf("Amazing grace"), song.lyrics, "and its lyrics are still reachable")
    }

    // ── Writing the halves of a header ──────────────────────────────────────────

    @Test
    fun `a song credited to an author alone still gets a header`() {
        // The header is written when ANY of the four fields is set, and each is written on its
        // own. A song with only an author must not lose it for want of a composer.
        val path = File(tempDir, "Author Only.song").path
        parser.writeSongFile(
            SongItem(number = "", title = "Author Only", author = "John Newton", lyrics = listOf("One line")),
            path,
        )

        val written = File(path).readText()
        assertTrue("author: John Newton" in written, written)
        assertTrue("composer:" !in written, "an absent field must not be written blank: $written")

        val reloaded = assertNotNull(parser.parseSongFile(path))
        assertEquals("John Newton", reloaded.author)
        assertEquals("", reloaded.composer)
    }

    @Test
    fun `a song carrying only a ccli number still gets a header`() {
        // The reporting path sets this on its own, with no credits at all — the branch that
        // decides whether a header exists has to consider it too.
        val path = File(tempDir, "Ccli Only.song").path
        parser.writeSongFile(
            SongItem(number = "", title = "Ccli Only", ccliNumber = "22025", lyrics = listOf("One line")),
            path,
        )

        assertEquals("22025", assertNotNull(parser.parseSongFile(path)).ccliNumber)
    }

    @Test
    fun `a translation with lyrics but no title of its own round-trips`() {
        // The secondary section is written when EITHER field is set, and its title line only when
        // there is one. A translation whose title was never filled in must still keep its lyrics.
        val path = File(tempDir, "Untitled Translation.song").path
        parser.writeSongFile(
            SongItem(
                number = "",
                title = "Amazing Grace",
                lyrics = listOf("Amazing grace"),
                secondaryTitle = "",
                secondaryLyrics = listOf("Удивительная благодать"),
            ),
            path,
        )

        val reloaded = assertNotNull(parser.parseSongFile(path))
        assertEquals("", reloaded.secondaryTitle)
        assertEquals(listOf("Удивительная благодать"), reloaded.secondaryLyrics, "the translation must survive")
    }

    @Test
    fun `a translation with a title but no lyrics yet round-trips`() {
        // The other half of the same condition: the operator named the translation in the editor
        // and has not typed it in yet. Saving must not drop the name they entered.
        val path = File(tempDir, "Named Translation.song").path
        parser.writeSongFile(
            SongItem(
                number = "",
                title = "Amazing Grace",
                lyrics = listOf("Amazing grace"),
                secondaryTitle = "Удивительная благодать",
                secondaryLyrics = emptyList(),
            ),
            path,
        )

        val reloaded = assertNotNull(parser.parseSongFile(path))
        assertEquals("Удивительная благодать", reloaded.secondaryTitle)
        assertTrue(reloaded.secondaryLyrics.isEmpty())
    }
}
