package org.churchpresenter.songlibrary

import org.churchpresenter.core.models.songs.SaveOutcome
import org.churchpresenter.core.models.songs.SongField
import org.churchpresenter.core.models.songs.SortColumn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the window is showing, and what it does to the folder underneath it.
 *
 * Everything here runs against a real temp library, because the half of this class that matters is
 * the half that writes: a save moves files, a delete removes them, and both are keyed on a path the
 * save itself changes.
 */
class SongLibraryStateTest {

    private val root: File = Files.createTempDirectory("songlibrary-state").toFile()
    private lateinit var state: SongLibraryState

    @BeforeTest
    fun setUp() {
        write("Hymns/0002 - Rise.song", "Rise", author = "Watts")
        write("Hymns/0010 - Ten.song", "Ten")
        write("Kids/AM/0001 - Clap.song", "Clap")
        write("Loose.song", "Loose")
        state = SongLibraryState(root)
        // The path the window itself takes. Unconfined rather than IO so the load has finished by
        // the time this returns and every test below can assert without waiting on anything.
        runBlocking { state.reloadAsync(Dispatchers.Unconfined) }
    }

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun write(path: String, title: String, author: String = "") {
        File(root, path).apply {
            parentFile.mkdirs()
            val header = if (author.isBlank()) "" else "---\nauthor: $author\n---\n\n"
            writeText("$header[Primary]\ntitle: $title\n\nA line of lyrics\n", Charsets.UTF_8)
        }
    }

    private fun fileOf(title: String): String = state.songs.first { it.title == title }.sourceFile

    // ── What is on screen ─────────────────────────────────────────────────────

    @Test
    fun `reload reads every song under the folder`() {
        assertEquals(listOf("Loose", "Rise", "Ten", "Clap"), state.songs.map { it.title })
        assertFalse(state.isDirty)
        assertFalse(state.isLoading)
    }

    @Test
    fun `the songbooks are the folders, parents included`() {
        assertEquals(listOf("Hymns", "Kids", "Kids/AM"), state.songbooks)
        assertEquals(2, state.counts.getValue("Hymns"))
        assertEquals(1, state.counts.getValue("Kids/AM"))
        assertEquals(1, state.counts.getValue("Kids"))
    }

    @Test
    fun `the search box filters the rows and leaves the songs alone`() {
        state.view = state.view.copy(query = "watts")

        assertEquals(listOf("Rise"), state.rows.map { it.title })
        assertEquals(4, state.songs.size)
    }

    @Test
    fun `a songbook filter takes the book and everything under it`() {
        state.view = state.view.copy(songbook = "Kids")
        assertEquals(listOf("Clap"), state.rows.map { it.title })

        state.view = state.view.copy(songbook = "")
        assertEquals(listOf("Loose"), state.rows.map { it.title })
    }

    @Test
    fun `clicking a header sorts by it and clicking it again turns it around`() {
        state.sortBy(SortColumn.TITLE)
        assertEquals(listOf("Clap", "Loose", "Rise", "Ten"), state.rows.map { it.title })

        state.sortBy(SortColumn.TITLE)
        assertFalse(state.view.ascending)
        assertEquals(listOf("Ten", "Rise", "Loose", "Clap"), state.rows.map { it.title })

        // And a third click turns it back, rather than sticking at descending.
        state.sortBy(SortColumn.TITLE)
        assertTrue(state.view.ascending)
        assertEquals(listOf("Clap", "Loose", "Rise", "Ten"), state.rows.map { it.title })

        state.sortBy(SortColumn.AUTHOR)
        assertTrue(state.view.ascending)
        assertEquals(SortColumn.AUTHOR, state.view.sortBy)
    }

    // ── Columns ───────────────────────────────────────────────────────────────

    @Test
    fun `the title column cannot be hidden and stays first`() {
        OPTIONAL_COLUMNS.forEach { state.toggleColumn(it) }

        assertEquals(listOf(SongField.TITLE), state.visibleColumns)
        assertFalse(SongField.TITLE in OPTIONAL_COLUMNS)
    }

    @Test
    fun `a hidden column comes back, and Show all brings all of them back`() {
        state.toggleColumn(SongField.CCLI)
        assertFalse(SongField.CCLI in state.visibleColumns)

        state.toggleColumn(SongField.CCLI)
        assertTrue(SongField.CCLI in state.visibleColumns)

        state.toggleColumn(SongField.TUNE)
        state.toggleColumn(SongField.AUTHOR)
        assertEquals(setOf(SongField.TUNE, SongField.AUTHOR), state.hiddenColumns.toSet())

        state.showAllColumns()
        assertTrue(state.hiddenColumns.isEmpty())
        assertEquals(listOf(SongField.TITLE) + OPTIONAL_COLUMNS, state.visibleColumns)
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    @Test
    fun `toggling picks a song up and puts it down again`() {
        val rise = fileOf("Rise")
        state.toggle(rise)
        assertEquals(listOf("Rise"), state.selectedSongs().map { it.title })

        state.toggle(rise)
        assertTrue(state.selected.isEmpty())
    }

    @Test
    fun `select-all takes the visible rows, and takes them back`() {
        state.view = state.view.copy(songbook = "Hymns")
        state.toggleAll()
        assertEquals(setOf("Rise", "Ten"), state.selectedSongs().map { it.title }.toSet())

        state.toggleAll()
        assertTrue(state.selected.isEmpty())
    }

    @Test
    fun `select-all adds the rest rather than clearing a partial selection`() {
        state.view = state.view.copy(songbook = "Hymns")
        state.toggle(fileOf("Rise"))
        state.toggleAll()

        assertEquals(2, state.selected.size)
    }

    @Test
    fun `clearing the selection empties it outright`() {
        state.toggleAll()
        state.clearSelection()

        assertTrue(state.selected.isEmpty())
    }

    @Test
    fun `the editor opens on a song and closes again`() {
        assertNull(state.editing)

        state.editing = fileOf("Rise")
        assertEquals("Rise", state.songOf(state.editing!!)?.title)

        state.editing = null
        assertNull(state.editing)
    }

    @Test
    fun `a reload drops the selection, because the paths it holds may be gone`() {
        state.toggle(fileOf("Rise"))
        state.reloadNow()

        assertTrue(state.selected.isEmpty())
    }

    // ── Editing ───────────────────────────────────────────────────────────────

    @Test
    fun `an edit shows immediately and counts as unsaved`() {
        state.edit(fileOf("Rise"), SongField.AUTHOR, "Wesley")

        assertEquals("Wesley", state.songs.first { it.title == "Rise" }.author)
        assertTrue(state.isDirty)
        assertEquals(1, state.changedCount)
    }

    @Test
    fun `a batch edit applies to the selection only`() {
        state.toggle(fileOf("Rise"))
        state.toggle(fileOf("Ten"))
        state.editAll(mapOf(SongField.COMPOSER to "Parry"))

        assertEquals(setOf("Rise", "Ten"), state.songs.filter { it.composer == "Parry" }.map { it.title }.toSet())
        assertEquals(2, state.changedCount)
    }

    @Test
    fun `revert throws the pending edits away`() {
        state.edit(fileOf("Rise"), SongField.AUTHOR, "Wesley")
        state.revert()

        assertEquals("Watts", state.songs.first { it.title == "Rise" }.author)
        assertFalse(state.isDirty)
    }

    @Test
    fun `replace takes every field of an edited song, lyrics included`() {
        val rise = state.songOf(fileOf("Rise"))!!
        state.replace(rise.copy(author = "Wesley", lyrics = listOf("A new line")))

        val edited = state.songs.first { it.title == "Rise" }
        assertEquals("Wesley", edited.author)
        assertEquals(listOf("A new line"), edited.lyrics)
    }

    @Test
    fun `songOf answers for a path it holds and not for one it does not`() {
        assertEquals("Rise", state.songOf(fileOf("Rise"))?.title)
        assertNull(state.songOf(File(root, "nothing.song").absolutePath))
    }

    // ── What writes ───────────────────────────────────────────────────────────

    @Test
    fun `save writes the edit to the file and stops being dirty`() {
        state.edit(fileOf("Rise"), SongField.AUTHOR, "Wesley")
        val outcome = state.saveNow()

        assertEquals(1, outcome.saved)
        assertTrue(outcome.errors.isEmpty())
        assertFalse(state.isDirty)
        assertTrue(File(root, "Hymns/0002 - Rise.song").readText().contains("Wesley"))
        assertEquals(outcome, state.lastOutcome)
    }

    @Test
    fun `renumbering a song moves its file and the state follows it`() {
        state.edit(fileOf("Rise"), SongField.NUMBER, "0042")
        state.saveNow()

        assertFalse(File(root, "Hymns/0002 - Rise.song").exists())
        assertTrue(File(root, "Hymns/0042 - Rise.song").exists())
        assertEquals("0042", state.songs.first { it.title == "Rise" }.number)
    }

    @Test
    fun `a new song is written under the songbook being filtered on`() {
        state.view = state.view.copy(songbook = "Hymns")
        state.newSongNow("Untitled")

        val fresh = state.songs.first { it.title == "Untitled" }
        assertEquals("Hymns", fresh.songbook)
        assertTrue(File(fresh.sourceFile).exists())
    }

    @Test
    fun `a new song with no book being filtered on lands in the library root`() {
        state.newSongNow("Untitled")

        val fresh = state.songs.first { it.title == "Untitled" }
        assertEquals("", fresh.songbook)
        assertEquals(root, File(fresh.sourceFile).parentFile)
    }

    @Test
    fun `a library with no folder set writes nothing and says so`() {
        // The window can be opened before a songs folder has ever been chosen: `storageDirectory`
        // defaults to "", every path built from it resolves to the filesystem root, and New Song
        // threw AccessDeniedException("C:\\New Song.song") out of a coroutine and took the app
        // down with it. Reported to Sentry from two churches on the day it shipped.
        val unset = SongLibraryState(File(""))

        assertFalse(unset.canWrite, "the header disables New Song on this, and explains why")

        unset.newSongNow("Untitled")

        assertTrue(unset.songs.isEmpty(), "nothing was written, and nothing was added to the grid")
        assertFalse(File("/Untitled.song").exists(), "and the root was never even attempted")
        assertEquals(1, unset.lastOutcome?.errors?.size, "and the failure is shown, not thrown")
    }

    @Test
    fun `a configured library may be written to`() {
        assertTrue(state.canWrite)
    }

    @Test
    fun `a song that cannot be written is reported rather than silently dropped`() {
        // A songbook whose folder cannot be created, because a song file of that name is already
        // there. Deterministic and cross-platform, unlike leaning on file permissions -- and a
        // target that merely exists is not enough: the library moves to a free name beside it.
        state.edit(fileOf("Rise"), SongField.SONGBOOK, "Loose.song")
        val outcome = state.saveNow()

        assertEquals(0, outcome.saved)
        assertEquals(1, outcome.errors.size)
        assertTrue(outcome.errors.single().startsWith("Rise:"))
        assertTrue(File(root, "Hymns/0002 - Rise.song").exists())
    }

    @Test
    fun `deleting a selection removes the files and forgets the songs`() {
        val gone = File(fileOf("Rise"))
        state.toggle(gone.absolutePath)
        val outcome = state.deleteSelectedNow()

        assertTrue(outcome.errors.isEmpty())
        assertFalse(gone.exists())
        assertFalse(state.songs.any { it.title == "Rise" })
        assertTrue(state.selected.isEmpty())
        assertEquals(outcome, state.lastOutcome)
    }

    @Test
    fun `a new songbook is a folder, and can take the selection with it`() {
        state.toggle(fileOf("Loose"))

        assertTrue(state.createSongbookNow("Anthems", assignSelected = true))
        assertTrue(File(root, "Anthems").isDirectory)
        assertEquals("Anthems", state.songs.first { it.title == "Loose" }.songbook)
    }

    @Test
    fun `a new songbook can be made without touching the selection`() {
        state.toggle(fileOf("Loose"))

        assertTrue(state.createSongbookNow("Anthems", assignSelected = false))
        assertTrue(File(root, "Anthems").isDirectory)
        assertEquals("", state.songs.first { it.title == "Loose" }.songbook)
        assertFalse(state.isDirty)
    }

    @Test
    fun `a write is flagged while it runs, so the buttons that started it stay off`() {
        // A dispatcher that reads the flag at the moment the disk work is handed to it: the window
        // is drawing then, and that is the frame the Save button has to be off for.
        var duringWrite: Boolean? = null
        val watching = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                if (duringWrite == null) duringWrite = state.isWriting
                block.run()
            }
        }
        state.edit(fileOf("Rise"), SongField.AUTHOR, "Wesley")
        runBlocking { state.save(watching) }

        assertEquals(true, duringWrite)
        assertFalse(state.isWriting)
    }

    @Test
    fun `a songbook outside the library folder is refused`() {
        assertFalse(state.createSongbookNow("../Escaped", assignSelected = false))
        assertFalse(File(root.parentFile, "Escaped").exists())
    }

    // ── The write path, run to completion ─────────────────────────────────────

    // Everything that touches the disk suspends, so the window can keep drawing while it runs.
    // Unconfined executes it inline on this thread, so a test still reads as one call and asserts
    // on the next line without waiting for anything.
    private fun SongLibraryState.reloadNow() = runBlocking { reloadAsync(Dispatchers.Unconfined) }

    private fun SongLibraryState.saveNow(): SaveOutcome = runBlocking { save(Dispatchers.Unconfined) }

    private fun SongLibraryState.newSongNow(title: String) =
        runBlocking { newSong(title, Dispatchers.Unconfined) }

    private fun SongLibraryState.deleteSelectedNow(): SaveOutcome =
        runBlocking { deleteSelected(Dispatchers.Unconfined) }

    private fun SongLibraryState.createSongbookNow(name: String, assignSelected: Boolean): Boolean =
        runBlocking { createSongbook(name, assignSelected, Dispatchers.Unconfined) }
}
