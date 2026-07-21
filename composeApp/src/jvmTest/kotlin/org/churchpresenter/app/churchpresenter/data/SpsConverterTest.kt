package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Importing a legacy SongPresenter `.sps` songbook into ChurchPresenter's own `.song` files.
 *
 * This runs once per library, usually by someone moving off the old app with years of songs in a
 * single file, and it writes to their disk — so the parts worth pinning are the ones that would
 * silently lose or mangle songs: every song in the file getting a file of its own, numbers padded
 * so the folder sorts in songbook order rather than 1, 10, 100, 2, and titles turned into names a
 * filesystem will actually accept.
 *
 * The `.sps` text format: `##` header lines (the second is the songbook name), then one song per
 * line with fields separated by `#$#` — number, title, category, key, author, composer, lyrics.
 */
class SpsConverterTest {

    private lateinit var dir: File
    private lateinit var output: File
    private val converter = SpsConverter()

    @BeforeTest
    fun createDirs() {
        dir = Files.createTempDirectory("cp-sps-converter-test").toFile()
        output = File(dir, "Songs").also { it.mkdirs() }
    }

    @AfterTest
    fun deleteDirs() {
        dir.deleteRecursively()
    }

    /** One song row in the `.sps` line format. */
    private fun song(
        number: String,
        title: String,
        category: String = "1",
        key: String = "G",
        author: String = "Newton",
        composer: String = "Excell",
        lyrics: String = "Amazing grace how sweet the sound",
    ) = listOf(number, title, category, key, author, composer, lyrics).joinToString("#\$#")

    /** Writes a `.sps` file whose second header line names the songbook. */
    private fun spsFile(
        name: String = "library.sps",
        songbook: String = "Hymnal",
        vararg songs: String,
    ): File = File(dir, name).also {
        it.writeText(("##SongPresenter" + "\n##" + songbook + "\n" + songs.joinToString("\n")), Charsets.UTF_8)
    }

    private fun songbookFolder(name: String = "Hymnal") = File(output, name)

    private fun convertedNames(name: String = "Hymnal") = songbookFolder(name).list()?.sorted().orEmpty()

    // ── Converting a songbook ───────────────────────────────────────────────────

    @Test
    fun `every song in the file becomes its own file`() {
        val sps = spsFile(
            songs = arrayOf(
                song("1", "Amazing Grace"),
                song("2", "How Great Thou Art"),
                song("3", "It Is Well"),
            ),
        )

        val result = converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(3, result.songsConverted)
        assertTrue(result.errors.isEmpty(), "got ${result.errors}")
        assertEquals(3, convertedNames().size)
    }

    @Test
    fun `the songbook name from the file becomes the folder`() {
        val sps = spsFile(songbook = "Sunday Hymnal", songs = arrayOf(song("1", "Amazing Grace")))

        val result = converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(File(output, "Sunday Hymnal").absolutePath, result.songbookFolder)
        assertTrue(File(output, "Sunday Hymnal").isDirectory)
    }

    @Test
    fun `song numbers are padded so the folder sorts in songbook order`() {
        val sps = spsFile(
            songs = arrayOf(
                song("2", "Second"),
                song("10", "Tenth"),
                song("100", "Hundredth"),
            ),
        )

        converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(
            listOf("0002 - Second.song", "0010 - Tenth.song", "0100 - Hundredth.song"),
            convertedNames(),
            "unpadded numbers would list 1, 10, 100, 2 — useless for finding a song by number",
        )
    }

    @Test
    fun `a converted song keeps its details`() {
        val sps = spsFile(
            songs = arrayOf(song("1", "Amazing Grace", author = "John Newton", composer = "Excell", key = "G")),
        )

        converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        val written = File(songbookFolder(), "0001 - Amazing Grace.song").readText()
        assertTrue("title: Amazing Grace" in written, written)
        assertTrue("author: John Newton" in written, written)
        assertTrue("composer: Excell" in written, written)
        assertTrue("tune: G" in written, written)
    }

    @Test
    fun `converting twice into the same folder overwrites rather than duplicating`() {
        val sps = spsFile(songs = arrayOf(song("1", "Amazing Grace")))
        converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        val result = converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(1, result.songsConverted)
        assertEquals(1, convertedNames().size, "re-importing a songbook must not leave two copies of every song")
    }

    // ── Names a filesystem will accept ──────────────────────────────────────────

    @Test
    fun `a title containing path characters is still written`() {
        val sps = spsFile(songs = arrayOf(song("1", "Hosanna / Praise: Him")))

        val result = converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(1, result.songsConverted, "errors: ${result.errors}")
        assertEquals(
            listOf("0001 - Hosanna Praise Him.song"),
            convertedNames(),
            "a slash would be read as a folder and a colon is illegal on Windows",
        )
    }

    @Test
    fun `a songbook name containing path characters is still written`() {
        val sps = spsFile(songbook = "Hymns: Book 1/2", songs = arrayOf(song("1", "Amazing Grace")))

        val result = converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(File(output, "Hymns Book 1 2").absolutePath, result.songbookFolder)
        assertTrue(File(output, "Hymns Book 1 2").isDirectory)
    }

    @Test
    fun `runs of spaces in a title collapse to one`() {
        val sps = spsFile(songs = arrayOf(song("1", "Amazing   Grace")))

        converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(listOf("0001 - Amazing Grace.song"), convertedNames())
    }

    @Test
    fun `a non-ascii title is kept as it is`() {
        val sps = spsFile(songbook = "Гимны", songs = arrayOf(song("1", "Удивительная благодать")))

        converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(
            listOf("0001 - Удивительная благодать.song"),
            convertedNames("Гимны"),
            "stripping non-ascii would rename half a Russian songbook to nothing",
        )
    }

    // ── Nothing to convert ──────────────────────────────────────────────────────

    @Test
    fun `a file with no songs reports that rather than making an empty folder`() {
        val sps = spsFile(name = "empty.sps", songs = arrayOf())

        val result = converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(0, result.songsConverted)
        assertEquals(listOf("No songs found in file"), result.errors)
        assertEquals("", result.songbookFolder)
        assertTrue(output.list()?.isEmpty() ?: true, "an empty songbook folder would look like a broken import")
    }

    @Test
    fun `a file that is not there is reported`() {
        val result = converter.convertSpsToSongFiles(File(dir, "missing.sps").absolutePath, output.absolutePath)

        assertEquals(0, result.songsConverted)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().startsWith("Error reading SPS file"), result.errors.single())
    }

    @Test
    fun `lines that are not songs are skipped rather than failing the import`() {
        val sps = spsFile(
            songs = arrayOf(
                song("1", "Amazing Grace"),
                "this line has no field separators",
                "1#\$#too few fields",
                song("2", "How Great Thou Art"),
            ),
        )

        val result = converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(2, result.songsConverted, "a malformed line must not cost the songs around it")
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `a song that cannot be written is reported rather than silently skipped`() {
        // A directory already standing where the song file has to go. The import carries on with
        // the rest of the songbook — one unwritable song must not cost the other nine hundred.
        val sps = spsFile(songs = arrayOf(song("1", "Amazing Grace"), song("2", "How Great Thou Art")))
        File(output, "Hymnal/0001 - Amazing Grace.song").mkdirs()

        val result = converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(1, result.songsConverted)
        assertEquals(1, result.errors.size)
        assertTrue(
            result.errors.single().startsWith("Error converting song 1 - Amazing Grace"),
            "the operator has to be told WHICH song did not make it: ${result.errors}",
        )
        assertTrue(convertedNames().contains("0002 - How Great Thou Art.song"), "the rest of the import still ran")
    }

    @Test
    fun `a songbook with no name of its own is filed under the file name`() {
        val sps = spsFile(name = "Grace Hymns.sps", songbook = "", songs = arrayOf(song("1", "Amazing Grace")))

        val result = converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertEquals(1, result.songsConverted)
        assertEquals(
            listOf("0001 - Amazing Grace.song"),
            convertedNames("Grace Hymns"),
            "an unnamed songbook must not import into a folder with no name",
        )
    }

    // ── Asking before overwriting ───────────────────────────────────────────────

    @Test
    fun `the target folder can be worked out before converting`() {
        val sps = spsFile(songbook = "Sunday Hymnal", songs = arrayOf(song("1", "Amazing Grace")))

        assertEquals("Sunday Hymnal", converter.getTargetFolderName(sps.absolutePath, output.absolutePath))
    }

    @Test
    fun `the target folder name is sanitised the same way`() {
        val sps = spsFile(songbook = "Hymns: Book 1/2", songs = arrayOf(song("1", "Amazing Grace")))

        assertEquals(
            "Hymns Book 1 2",
            converter.getTargetFolderName(sps.absolutePath, output.absolutePath),
            "the prompt must name the folder that will actually be written",
        )
    }

    @Test
    fun `a file with nothing in it has no target folder`() {
        val sps = spsFile(name = "empty.sps", songs = arrayOf())

        assertNull(converter.getTargetFolderName(sps.absolutePath, output.absolutePath))
        assertFalse(converter.targetFolderExists(sps.absolutePath, output.absolutePath))
    }

    @Test
    fun `an existing songbook folder is detected so the operator can be warned`() {
        val sps = spsFile(songbook = "Hymnal", songs = arrayOf(song("1", "Amazing Grace")))

        assertFalse(converter.targetFolderExists(sps.absolutePath, output.absolutePath))

        converter.convertSpsToSongFiles(sps.absolutePath, output.absolutePath)

        assertTrue(
            converter.targetFolderExists(sps.absolutePath, output.absolutePath),
            "re-importing over an existing songbook should be a question, not a surprise",
        )
    }

    // ── Name sanitising, directly ───────────────────────────────────────────────

    private fun sanitize(name: String): String =
        SpsConverter::class.java
            .getDeclaredMethod("sanitizeName", String::class.java)
            .apply { isAccessible = true }
            .invoke(converter, name) as String

    @Test
    fun `every character a filesystem rejects is replaced`() {
        assertEquals("a b c d e f g h i", sanitize("""a/b\c:d*e?f"g<h>i"""))
    }

    @Test
    fun `control characters are removed rather than spaced out`() {
        assertEquals(
            "AmazingGrace",
            sanitize("Amazing\u0001\u0007Grace"),
            "replacing them with spaces would leave a gap where an invisible byte was",
        )
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("Amazing Grace", sanitize("   Amazing Grace   "))
    }

    @Test
    fun `a title of nothing but illegal characters sanitises to empty`() {
        // Documents CURRENT behaviour: the result is used as a file name, so such a song is written
        // as "0001 - .song". Only reachable from a corrupt source file.
        assertEquals("", sanitize("///"))
    }
}
