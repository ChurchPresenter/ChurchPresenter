package org.churchpresenter.app.churchpresenter.models.songs

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The library as files on disk: what a save actually does.
 *
 * Three of a song's fields decide where its file lives — the number and title name it, the songbook
 * is the folder holding it — so editing any of them is a move, and a move is the operation that can
 * lose a song. The order is deliberate everywhere below: the new file is written before the old one
 * is removed, so an interrupted save leaves the song twice rather than not at all.
 */
class SongLibraryTest {

    private val root: File = Files.createTempDirectory("songlibrary-store").toFile()
    private val library = SongLibrary(root)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun write(path: String, title: String, author: String = ""): File =
        File(root, path).apply {
            parentFile.mkdirs()
            val header = if (author.isBlank()) "" else "---\nauthor: $author\n---\n\n"
            writeText("$header[Primary]\ntitle: $title\n\nA line of lyrics\n", Charsets.UTF_8)
        }

    // ── Loading ───────────────────────────────────────────────────────────────

    @Test
    fun `a song's folder is the songbook it belongs to`() {
        write("Hymns/0001 - Grace.song", "Grace")
        write("Kids/AM/0002 - Rise.song", "Rise")
        write("Loose.song", "Loose")

        val songs = library.load().associateBy { it.title }
        assertEquals("Hymns", songs.getValue("Grace").songbook)
        assertEquals("Kids/AM", songs.getValue("Rise").songbook)
        assertEquals("", songs.getValue("Loose").songbook)
    }

    @Test
    fun `songs come back by songbook and then by number, counting as numbers`() {
        write("Hymns/0010 - Ten.song", "Ten")
        write("Hymns/0002 - Two.song", "Two")
        write("Anthems/0001 - One.song", "One")

        assertEquals(listOf("One", "Two", "Ten"), library.load().map { it.title })
    }

    @Test
    fun `a file that is not a song is left where it is`() {
        write("Hymns/0001 - Grace.song", "Grace")
        File(root, "Hymns/notes.txt").writeText("not a song", Charsets.UTF_8)

        assertEquals(listOf("Grace"), library.load().map { it.title })
    }

    @Test
    fun `every songbook is listed, including the parent of a nested one`() {
        write("Kids/AM/0001 - Rise.song", "Rise")
        write("Hymns/0002 - Grace.song", "Grace")
        write("Loose.song", "Loose")

        assertEquals(listOf("Hymns", "Kids", "Kids/AM"), library.songbooks())
    }

    // ── Saving ────────────────────────────────────────────────────────────────

    @Test
    fun `an edited field is written back to the same file`() {
        write("Hymns/0001 - Grace.song", "Grace")
        val loaded = library.load()
        val edited = loaded.map { it.copy(author = "John Newton") }

        assertEquals(SaveOutcome(1, emptyList()), library.save(loaded.associateBy { it.sourceFile }, edited))
        assertEquals("John Newton", library.load().single().author)
    }

    @Test
    fun `a song nothing changed on is not rewritten`() {
        write("Hymns/0001 - Grace.song", "Grace")
        val loaded = library.load()

        assertEquals(0, library.save(loaded.associateBy { it.sourceFile }, loaded).saved)
    }

    @Test
    fun `renaming a song renames its file, and the old one goes`() {
        val before = write("Hymns/0001 - Grace.song", "Grace")
        val loaded = library.load()

        library.save(loaded.associateBy { it.sourceFile }, loaded.map { it.copy(title = "Amazing Grace") })

        assertFalse(before.exists(), "the old file must not be left behind")
        assertEquals(listOf("Hymns/0001 - Amazing Grace.song"), relativeFiles())
    }

    @Test
    fun `renumbering a song renames its file`() {
        write("Hymns/0001 - Grace.song", "Grace")
        val loaded = library.load()

        library.save(loaded.associateBy { it.sourceFile }, loaded.map { it.copy(number = "0042") })

        assertEquals(listOf("Hymns/0042 - Grace.song"), relativeFiles())
    }

    @Test
    fun `moving a song to another songbook moves its file into that folder`() {
        write("Hymns/0001 - Grace.song", "Grace")
        val loaded = library.load()

        library.save(loaded.associateBy { it.sourceFile }, loaded.map { it.copy(songbook = "Kids/AM") })

        assertEquals(listOf("Kids/AM/0001 - Grace.song"), relativeFiles())
        assertEquals("Kids/AM", library.load().single().songbook)
    }

    @Test
    fun `a song moved onto one that is already there does not overwrite it`() {
        write("Hymns/0001 - Grace.song", "Grace")
        write("Kids/0001 - Grace.song", "Grace")
        val loaded = library.load()
        val moved = loaded.map { if (it.songbook == "Hymns") it.copy(songbook = "Kids") else it }

        library.save(loaded.associateBy { it.sourceFile }, moved)

        assertEquals(2, library.load().size)
        assertTrue(relativeFiles().any { it.endsWith("Grace (2).song") }, relativeFiles().toString())
    }

    @Test
    fun `a title the filesystem cannot hold is written under a name it can`() {
        write("Hymns/0001 - Grace.song", "Grace")
        val loaded = library.load()

        library.save(loaded.associateBy { it.sourceFile }, loaded.map { it.copy(title = "AC/DC: Live?") })

        assertEquals(listOf("Hymns/0001 - AC DC Live.song"), relativeFiles())
        assertEquals("AC/DC: Live?", library.load().single().title)
    }

    @Test
    fun `a song with no number at all is filed under its title alone`() {
        write("Hymns/0001 - Grace.song", "Grace")
        val loaded = library.load()

        library.save(loaded.associateBy { it.sourceFile }, loaded.map { it.copy(number = "") })

        assertEquals(listOf("Hymns/Grace.song"), relativeFiles())
    }

    // ── Songbooks, copies and deletes ─────────────────────────────────────────

    @Test
    fun `a new songbook is a folder, and exists before it holds anything`() {
        assertTrue(library.createSongbook("Christmas 2026"))
        assertTrue(File(root, "Christmas 2026").isDirectory)
        // Creating one that is already there is not a failure.
        assertTrue(library.createSongbook("Christmas 2026"))
    }

    @Test
    fun `a songbook name cannot climb out of the library folder`() {
        assertFalse(library.createSongbook("../elsewhere"))
        assertFalse(File(root.parentFile, "elsewhere").exists())
    }

    @Test
    fun `a copy is written beside the song it came from, under a free name`() {
        write("Hymns/0001 - Grace.song", "Grace", author = "John Newton")
        val song = library.load().single()

        val copy = library.writeNew(song.copy(number = "", title = "Grace (copy)"))

        assertTrue(File(copy.sourceFile).isFile)
        assertEquals("Hymns", copy.songbook)
        assertEquals(listOf("Hymns/0001 - Grace.song", "Hymns/Grace (copy).song"), relativeFiles())
        assertEquals("John Newton", library.load().first { it.title == "Grace (copy)" }.author)
    }

    @Test
    fun `deleting takes the files with it`() {
        write("Hymns/0001 - Grace.song", "Grace")
        write("Hymns/0002 - Rise.song", "Rise")
        val songs = library.load()

        assertEquals(SaveOutcome(2, emptyList()), library.delete(songs))
        assertTrue(library.load().isEmpty())
    }

    @Test
    fun `deleting a song whose file has already gone is not an error`() {
        write("Hymns/0001 - Grace.song", "Grace")
        val song = library.load().single()
        File(song.sourceFile).delete()

        assertEquals(SaveOutcome(1, emptyList()), library.delete(listOf(song)))
    }

    @Test
    fun `a song that cannot be written is reported by name, and the rest still save`() {
        write("Hymns/0001 - Grace.song", "Grace")
        write("Hymns/0002 - Rise.song", "Rise")
        val loaded = library.load()

        // A songbook no filesystem will accept: the write fails for that song and only that song.
        val edited = loaded.map {
            if (it.title == "Grace") it.copy(songbook = "Kids\u0000AM") else it.copy(author = "Someone")
        }
        val outcome = library.save(loaded.associateBy { it.sourceFile }, edited)

        assertEquals(1, outcome.saved)
        assertEquals(1, outcome.errors.size)
        assertTrue(outcome.errors.single().startsWith("Grace"), outcome.errors.toString())
        assertEquals("Someone", library.load().first { it.title == "Rise" }.author)
    }

    private fun relativeFiles(): List<String> =
        root.walkTopDown().filter { it.isFile }.map { it.toRelativeString(root).replace(File.separatorChar, '/') }
            .sorted().toList()
}
