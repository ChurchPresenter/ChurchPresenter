package org.churchpresenter.core.models.songs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `.song` file format, both directions.
 *
 * Every song in a library is read through here on startup and written back through here on every
 * edit, so a field the writer emits but the reader ignores — or the other way round — silently
 * loses somebody's work. Most of these assert the round trip rather than the exact bytes.
 */
class SongFileParserTest {

    private val parser = SongFileParser()

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "songs-${System.nanoTime()}").apply { mkdirs() }

    // ── Reading ─────────────────────────────────────────────────────────────────

    @Test
    fun `a full header is read into its own fields`() {
        val song = parser.parseSongContent(
            """
            ---
            author: John Newton
            composer: Traditional
            tune: NEW BRITAIN
            ccli: 22025
            ---

            [Primary]
            title: Amazing Grace

            Amazing grace how sweet the sound
            """.trimIndent(),
        )

        assertEquals("John Newton", song?.author)
        assertEquals("Traditional", song?.composer)
        assertEquals("NEW BRITAIN", song?.tune)
        assertEquals("22025", song?.ccliNumber)
        assertEquals("Amazing Grace", song?.title)
        assertEquals(listOf("Amazing grace how sweet the sound"), song?.lyrics)
    }

    @Test
    fun `an unknown header key is dropped rather than kept`() {
        val song = parser.parseSongContent(
            "---\nauthor: A\nnonsense: B\nno-colon-line\n---\n[Primary]\ntitle: T\n",
        )

        assertEquals("A", song?.author)
        assertEquals("T", song?.title)
    }

    @Test
    fun `a file with no header at all still reads`() {
        val song = parser.parseSongContent("[Primary]\ntitle: Plain\n\nJust lyrics\n")

        assertEquals("Plain", song?.title)
        assertEquals("", song?.author)
        assertEquals(listOf("Just lyrics"), song?.lyrics)
    }

    @Test
    fun `an unterminated header does not swallow the song`() {
        // No closing --- : the header runs to the end of the file, so the body is what is left.
        val song = parser.parseSongContent("---\nauthor: A\n[Primary]\ntitle: T\n")

        assertEquals("A", song?.author)
    }

    @Test
    fun `both language halves are kept apart`() {
        val song = parser.parseSongContent(
            """
            [Primary]
            title: Holy God

            Holy God we praise thy name

            [Secondary]
            title: Боже, хвалим

            Боже, хвалим імʼя твоє
            """.trimIndent(),
        )

        assertEquals("Holy God", song?.title)
        assertEquals("Боже, хвалим", song?.secondaryTitle)
        assertEquals(listOf("Holy God we praise thy name"), song?.lyrics)
        assertEquals(listOf("Боже, хвалим імʼя твоє"), song?.secondaryLyrics)
    }

    @Test
    fun `lines before any section tag belong to nothing`() {
        val song = parser.parseSongContent("stray line\n[Primary]\ntitle: T\nkept\n")

        assertEquals(listOf("kept"), song?.lyrics)
    }

    @Test
    fun `blank lines are trimmed from the ends but kept in the middle`() {
        val song = parser.parseSongContent("[Primary]\ntitle: T\n\n\nfirst\n\nsecond\n\n\n")

        assertEquals(listOf("first", "", "second"), song?.lyrics)
    }

    @Test
    fun `the number comes off the file name in each of its spellings`() {
        fun numberOf(fileName: String) =
            parser.parseSongContent("[Primary]\ntitle: T\n", filePath = "/lib/$fileName.song")?.number

        assertEquals("0001", numberOf("0001 - Title"))
        assertEquals("0002", numberOf("0002- Title"))
        assertEquals("0003", numberOf("0003 -Title"))
        assertEquals("0004", numberOf("0004-Title"))
        assertEquals("", numberOf("Title Only"))
    }

    @Test
    fun `a song with no title falls back to its file name`() {
        val song = parser.parseSongContent("[Primary]\n\nlyrics\n", filePath = "/lib/0007 - Fallback.song")

        assertEquals("0007 - Fallback", song?.title)
    }

    @Test
    fun `a path that does not exist reads as nothing rather than throwing`() {
        assertNull(parser.parseSongFile("/no/such/file.song"))
    }

    @Test
    fun `a song read off disk carries its songbook and path`() {
        val dir = tempDir()
        val file = File(dir, "0012 - On Disk.song")
        file.writeText("[Primary]\ntitle: On Disk\n\nline\n")

        val song = parser.parseSongFile(file.absolutePath, songbook = "Hymnal")

        assertEquals("On Disk", song?.title)
        assertEquals("Hymnal", song?.songbook)
        assertEquals("0012", song?.number)
        assertEquals(file.absolutePath, song?.sourceFile)
    }

    // ── Writing ─────────────────────────────────────────────────────────────────

    private fun roundTrip(song: SongItem): SongItem? {
        val file = File(tempDir(), "out.song")
        parser.writeSongFile(song, file.absolutePath)
        return parser.parseSongFile(file.absolutePath)
    }

    @Test
    fun `everything written is read back`() {
        val original = SongItem(
            number = "0001",
            title = "Amazing Grace",
            tune = "NEW BRITAIN",
            author = "John Newton",
            composer = "Traditional",
            lyrics = listOf("Amazing grace", "how sweet the sound"),
            secondaryTitle = "Дивна благодать",
            secondaryLyrics = listOf("Дивна благодать"),
            ccliNumber = "22025",
        )

        val back = roundTrip(original)

        assertEquals(original.title, back?.title)
        assertEquals(original.author, back?.author)
        assertEquals(original.composer, back?.composer)
        assertEquals(original.tune, back?.tune)
        assertEquals(original.ccliNumber, back?.ccliNumber)
        assertEquals(original.lyrics, back?.lyrics)
        assertEquals(original.secondaryTitle, back?.secondaryTitle)
        assertEquals(original.secondaryLyrics, back?.secondaryLyrics)
    }

    @Test
    fun `each credit is written on its own`() {
        // Every field independently decides whether the header exists at all, so each is its own case.
        assertEquals("Only Author", roundTrip(SongItem("", "T", author = "Only Author"))?.author)
        assertEquals("Only Composer", roundTrip(SongItem("", "T", composer = "Only Composer"))?.composer)
        assertEquals("ONLY TUNE", roundTrip(SongItem("", "T", tune = "ONLY TUNE"))?.tune)
        assertEquals("999", roundTrip(SongItem("", "T", ccliNumber = "999"))?.ccliNumber)
    }

    @Test
    fun `a song with no metadata is written without a header`() {
        val file = File(tempDir(), "bare.song")
        parser.writeSongFile(SongItem("", "Bare", lyrics = listOf("line")), file.absolutePath)

        assertTrue("---" !in file.readText(), "a song with nothing to declare must not open with an empty header")
        assertEquals("Bare", parser.parseSongFile(file.absolutePath)?.title)
    }

    @Test
    fun `a secondary half with lyrics but no title still round-trips`() {
        val back = roundTrip(SongItem("", "T", secondaryLyrics = listOf("untitled second half")))

        assertEquals("", back?.secondaryTitle)
        assertEquals(listOf("untitled second half"), back?.secondaryLyrics)
    }

    @Test
    fun `a song with no secondary half writes no secondary section`() {
        val file = File(tempDir(), "single.song")
        parser.writeSongFile(SongItem("", "T", lyrics = listOf("line")), file.absolutePath)

        assertTrue("[Secondary]" !in file.readText())
    }

    @Test
    fun `writing creates the songbook folder it is given`() {
        val file = File(tempDir(), "Kids/AM/0001 - Nested.song")

        parser.writeSongFile(SongItem("0001", "Nested"), file.absolutePath)

        assertTrue(file.exists(), "the parent folders of a new songbook have to be made, not assumed")
    }

    // ── Loading a library ───────────────────────────────────────────────────────

    @Test
    fun `a directory that is not there loads as empty`() {
        assertEquals(emptyList(), parser.loadSongsFromDirectory("/no/such/dir"))
    }

    @Test
    fun `a file where a directory was expected loads as empty`() {
        val file = File(tempDir(), "not-a-dir.txt").apply { writeText("x") }

        assertEquals(emptyList(), parser.loadSongsFromDirectory(file.absolutePath))
    }

    @Test
    fun `subfolders become songbooks and other files are ignored`() {
        val root = tempDir()
        File(root, "0002 - Root Song.song").writeText("[Primary]\ntitle: Root Song\n")
        File(root, "notes.txt").writeText("not a song")
        File(root, "Hymnal").mkdirs()
        File(root, "Hymnal/0001 - Book Song.song").writeText("[Primary]\ntitle: Book Song\n")
        File(root, "Hymnal/Kids").mkdirs()
        File(root, "Hymnal/Kids/0003 - Deep.song").writeText("[Primary]\ntitle: Deep\n")

        val loaded = parser.loadSongsFromDirectory(root.absolutePath).map { it.song }

        assertEquals(listOf("Root Song", "Book Song", "Deep"), loaded.map { it.title })
        assertEquals(listOf("", "Hymnal", "Hymnal/Kids"), loaded.map { it.songbook })
    }

    @Test
    fun `an unchanged file is taken from the cache instead of parsed again`() {
        val root = tempDir()
        val file = File(root, "0001 - Cached.song")
        file.writeText("[Primary]\ntitle: On Disk\n")
        val cached = CachedSong(
            song = SongItem("0001", "From Cache", sourceFile = file.absolutePath),
            lastModified = file.lastModified(),
            fileSize = file.length(),
        )

        val loaded = parser.loadSongsFromDirectory(root.absolutePath, mapOf(file.absolutePath to cached))

        assertEquals("From Cache", loaded.single().song.title, "a matching cache entry must not be re-parsed")
    }

    @Test
    fun `a file whose size changed is parsed again even when the timestamp matches`() {
        // The reason fileSize exists: lastModified has one-second resolution on some filesystems.
        val root = tempDir()
        val file = File(root, "0001 - Edited.song")
        file.writeText("[Primary]\ntitle: On Disk\n")
        val stale = CachedSong(
            song = SongItem("0001", "From Cache", sourceFile = file.absolutePath),
            lastModified = file.lastModified(),
            fileSize = file.length() + 1,
        )

        val loaded = parser.loadSongsFromDirectory(root.absolutePath, mapOf(file.absolutePath to stale))

        assertEquals("On Disk", loaded.single().song.title, "a size mismatch has to force a re-parse")
    }

    @Test
    fun `a file whose timestamp changed is parsed again`() {
        val root = tempDir()
        val file = File(root, "0001 - Touched.song")
        file.writeText("[Primary]\ntitle: On Disk\n")
        val stale = CachedSong(
            song = SongItem("0001", "From Cache", sourceFile = file.absolutePath),
            lastModified = file.lastModified() - 5_000,
            fileSize = file.length(),
        )

        val loaded = parser.loadSongsFromDirectory(root.absolutePath, mapOf(file.absolutePath to stale))

        assertEquals("On Disk", loaded.single().song.title)
    }
}
