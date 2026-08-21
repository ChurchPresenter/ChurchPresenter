package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.Dispatchers

import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SongsViewModel] against a real song library on disk — loading, section/line navigation, and the
 * create/edit/delete round-trips that write `.song` files.
 *
 * Everything here needs `_allSongItems` actually populated, which only happens through the
 * asynchronous `loadSongs()` disk scan; the earlier filtering tests injected a catalog through the
 * Instance Link path instead and so never reached any of this.
 */
class SongsViewModelLibraryTest {

    private lateinit var dir: File
    private val created = mutableListOf<SongsViewModel>()

    @BeforeTest
    fun createLibrary() {
        dir = Files.createTempDirectory("cp-songs-library-test").toFile()
        writeSong(
            songbook = "Hymnal", number = "0001", title = "Amazing Grace",
            lyrics = listOf(
                "[Verse 1]", "Amazing grace how sweet the sound", "That saved a wretch like me",
                "{Chorus}", "How sweet the sound",
                "[Verse 2]", "Twas grace that taught my heart to fear",
            ),
        )
        writeSong(
            songbook = "Hymnal", number = "0002", title = "How Great Thou Art",
            lyrics = listOf("[Verse 1]", "O Lord my God"),
        )
        writeSong(
            songbook = "Worship", number = "0005", title = "Blessed Be Your Name",
            lyrics = listOf("[Verse 1]", "Blessed be your name"),
        )
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    private fun writeSong(songbook: String, number: String, title: String, lyrics: List<String>) {
        val target = File(File(dir, songbook), "$number - $title.song")
        SongFileParser().writeSongFile(
            SongItem(number = number, title = title, songbook = songbook, lyrics = lyrics),
            target.absolutePath,
        )
    }

    private fun viewModel(): SongsViewModel {
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        created.add(vm)
        awaitUntil("songs to load") { vm.filteredSongItems.value.isNotEmpty() }
        return vm
    }

    /**
     * Asserts [what] has already happened.
     *
     * The view model is built on an immediate dispatcher for both its scope and its file reads, so a
     * load is complete by the time the constructor or the call returns — there is nothing to wait
     * for. This used to poll a wall clock for up to 5s, which is what made these tests fail on a
     * loaded CI runner (issue #56): the condition was right, the coroutine just had not been
     * scheduled yet. Nothing here now depends on timing.
     */
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        if (!condition()) throw AssertionError("expected $what to have completed synchronously")
    }

    private fun SongsViewModel.selectByTitle(title: String) {
        val index = filteredSongItems.value.indexOfFirst { it.title == title }
        assertTrue(index >= 0, "no song titled $title")
        selectSong(index)
    }

    // ── Loading from disk ───────────────────────────────────────────────────────

    @Test
    fun `songs are discovered across songbook folders`() {
        val vm = viewModel()
        assertEquals(3, vm.filteredSongItems.value.size)
        assertTrue(vm.songbooks.value.containsAll(listOf("Hymnal", "Worship")))
    }

    @Test
    fun `a loaded song carries its parsed lyrics and source file`() {
        val vm = viewModel()
        val song = assertNotNull(vm.filteredSongItems.value.firstOrNull { it.title == "Amazing Grace" })
        assertTrue(song.lyrics.isNotEmpty())
        assertTrue(song.sourceFile.endsWith(".song"))
        assertEquals("Hymnal", song.songbook)
    }

    @Test
    fun `an empty storage directory yields no songs`() {
        val vm = SongsViewModel(
            AppSettings(),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        ).also { created.add(it) }
        awaitUntil("the load to finish") { !vm.isLoading.value }
        assertTrue(vm.filteredSongItems.value.isEmpty())
    }

    // ── Reading a selection that is not there ───────────────────────────────────
    //
    // Every read of the current song is guarded on the index, because the two can come apart: the
    // library reloads on a folder watch, a filter narrows the list under a selection made against
    // the wider one, or a linked instance sends a selection this machine's library cannot satisfy.
    // The guards must answer "nothing", never index into the list.

    @Test
    fun `nothing is selected in an empty library`() {
        val vm = SongsViewModel(
            AppSettings(),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        ).also { created.add(it) }
        awaitUntil("the load to finish") { !vm.isLoading.value }

        assertNull(vm.getSelectedSong(), "an empty library has no current song")
        assertTrue(vm.getLyricSections().isEmpty(), "and so no sections to present")
    }

    @Test
    fun `a selection past the end of the library reads as nothing`() {
        // What a schedule saved against a larger library produces after songs are deleted.
        val vm = viewModel()
        vm.selectSong(999)

        assertNull(vm.getSelectedSong())
        assertTrue(vm.getLyricSections().isEmpty())
    }

    @Test
    fun `a negative selection reads as nothing`() {
        // -1 is the "nothing chosen yet" value the list starts on, and both readers see it before
        // the operator has clicked anything.
        val vm = viewModel()
        vm.selectSong(-1)

        assertNull(vm.getSelectedSong())
        assertTrue(vm.getLyricSections().isEmpty())
    }

    @Test
    fun `a song whose number is not a number presents as song zero`() {
        // Numbers come from the file name and are free text — "42a" for a second setting of the
        // same hymn is a real convention. The slide carries an Int, so an unparseable number has to
        // fall to 0 rather than throw while the song is going live.
        writeSong(
            songbook = "Hymnal",
            number = "42a",
            title = "Alternate Setting",
            lyrics = listOf("[Verse 1]", "A line"),
        )
        val vm = viewModel()
        vm.selectByTitle("Alternate Setting")

        val section = assertNotNull(vm.getSelectedSong())
        assertEquals(0, section.songNumber, "an unparseable number must not stop the song presenting")
        assertEquals("Alternate Setting", section.title)
    }

    // ── Section navigation ──────────────────────────────────────────────────────

    @Test
    fun `the selected song resolves to its first section`() {
        val vm = viewModel()
        vm.selectByTitle("Amazing Grace")
        val section = assertNotNull(vm.getSelectedSong())
        assertEquals("Amazing Grace", section.title)
    }

    @Test
    fun `sections step forward and back`() {
        val vm = viewModel()
        vm.selectByTitle("Amazing Grace")
        vm.selectSection(0)

        assertTrue(vm.navigateNextSection())
        assertEquals(1, vm.selectedSectionIndex.value)
        assertTrue(vm.navigatePreviousSection())
        assertEquals(0, vm.selectedSectionIndex.value)
    }

    @Test
    fun `section navigation stops at both ends`() {
        val vm = viewModel()
        vm.selectByTitle("How Great Thou Art") // a single verse only
        vm.selectSection(0)

        assertFalse(vm.navigatePreviousSection(), "nothing before the first section")
        assertFalse(vm.navigateNextSection(), "nothing after the only section")
    }

    @Test
    fun `moving between sections resets the line index`() {
        val vm = viewModel()
        vm.selectByTitle("Amazing Grace")
        vm.selectSection(0)
        vm.setLineIndex(1)

        vm.navigateNextSection()
        assertEquals(0, vm.selectedLineIndex.value, "a new section starts at its first line")
    }

    // ── Line navigation ─────────────────────────────────────────────────────────

    @Test
    fun `lines step through the current section`() {
        val vm = viewModel()
        vm.selectByTitle("Amazing Grace")
        vm.selectSection(0) // verse 1 has two lines

        assertTrue(vm.navigateNextLine())
        assertEquals(1, vm.selectedLineIndex.value)
        assertTrue(vm.navigatePreviousLine())
        assertEquals(0, vm.selectedLineIndex.value)
    }

    @Test
    fun `stepping past the last line moves into the next section`() {
        val vm = viewModel()
        vm.selectByTitle("Amazing Grace")
        vm.selectSection(0)
        vm.setLineIndex(1) // last line of verse 1

        assertTrue(vm.navigateNextLine())
        assertEquals(1, vm.selectedSectionIndex.value, "line stepping crosses section boundaries")
        assertEquals(0, vm.selectedLineIndex.value)
    }

    @Test
    fun `stepping back from the first line lands on the last line of the previous section`() {
        val vm = viewModel()
        vm.selectByTitle("Amazing Grace")
        vm.selectSection(1)
        vm.setLineIndex(0)

        assertTrue(vm.navigatePreviousLine())
        assertEquals(0, vm.selectedSectionIndex.value)
        assertEquals(1, vm.selectedLineIndex.value, "resumes at the END of the previous section")
    }

    @Test
    fun `line navigation stops at the very start and end of the song`() {
        val vm = viewModel()
        vm.selectByTitle("How Great Thou Art") // one section, one line
        vm.selectSection(0)
        vm.setLineIndex(0)

        assertFalse(vm.navigatePreviousLine())
        assertFalse(vm.navigateNextLine())
    }

    // ── Song stepping ───────────────────────────────────────────────────────────

    @Test
    fun `changing song resets the section selection`() {
        val vm = viewModel()
        vm.selectSong(0)
        vm.selectSection(1)
        vm.navigateNextSong()
        assertEquals(-1, vm.selectedSectionIndex.value, "a new song starts with nothing chosen")
    }

    // ── Creating ────────────────────────────────────────────────────────────────

    @Test
    fun `a new song is written to its songbook folder and reloaded`() {
        val vm = viewModel()
        val created = SongItem(
            number = "0009", title = "New Song", songbook = "Hymnal",
            lyrics = listOf("[Verse 1]", "A brand new line"),
        )
        assertTrue(vm.createSong(created))
        awaitUntil("the new song to appear") { vm.filteredSongItems.value.any { it.title == "New Song" } }

        val file = File(File(dir, "Hymnal"), "0009 - New Song.song")
        assertTrue(file.exists(), "expected ${file.absolutePath}")
    }

    @Test
    fun `a new songbook folder is created on demand`() {
        val vm = viewModel()
        assertTrue(
            vm.createSong(
                SongItem(number = "0001", title = "First", songbook = "Christmas", lyrics = listOf("line")),
            ),
        )
        assertTrue(File(dir, "Christmas").isDirectory)
    }

    @Test
    fun `a song with no number is filed under its title alone`() {
        val vm = viewModel()
        assertTrue(vm.createSong(SongItem(
            number = "",
            title = "Untitled Hymn",
            songbook = "Hymnal",
            lyrics = listOf("l"),
        )))
        assertTrue(File(File(dir, "Hymnal"), "Untitled Hymn.song").exists())
    }

    @Test
    fun `a song number is zero-padded in the filename`() {
        val vm = viewModel()
        vm.createSong(SongItem(number = "7", title = "Seven", songbook = "Hymnal", lyrics = listOf("l")))
        assertTrue(File(File(dir, "Hymnal"), "0007 - Seven.song").exists(), "numbers sort correctly on disk")
    }

    @Test
    fun `creating without a songbook or storage directory is refused`() {
        val vm = viewModel()
        assertFalse(vm.createSong(SongItem(number = "1", title = "No Book", songbook = "", lyrics = listOf("l"))))

        val unconfigured = SongsViewModel(
            AppSettings(),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        ).also { created.add(it) }
        assertFalse(unconfigured.createSong(SongItem(
            number = "1",
            title = "T",
            songbook = "Hymnal",
            lyrics = listOf("l"),
        )))
    }

    // ── Editing ─────────────────────────────────────────────────────────────────

    @Test
    fun `editing the lyrics rewrites the file in place`() {
        val vm = viewModel()
        val original = assertNotNull(vm.filteredSongItems.value.firstOrNull { it.title == "How Great Thou Art" })

        val edited = original.copy(lyrics = listOf("[Verse 1]", "Edited line"))
        assertTrue(vm.updateSong(original, edited))
        awaitUntil("the edit to land") {
            vm.filteredSongItems.value.firstOrNull { it.title == "How Great Thou Art" }
                ?.lyrics?.contains("Edited line") == true
        }
        assertTrue(File(original.sourceFile).exists(), "an in-place edit keeps the same file")
    }

    @Test
    fun `renaming a song renames its file`() {
        val vm = viewModel()
        val original = assertNotNull(vm.filteredSongItems.value.firstOrNull { it.title == "How Great Thou Art" })

        assertTrue(vm.updateSong(original, original.copy(title = "Renamed Song")))
        awaitUntil("the rename") { vm.filteredSongItems.value.any { it.title == "Renamed Song" } }

        assertFalse(File(original.sourceFile).exists(), "the old file should be gone")
        assertTrue(File(File(dir, "Hymnal"), "0002 - Renamed Song.song").exists())
    }

    @Test
    fun `changing the number renames the file too`() {
        val vm = viewModel()
        val original = assertNotNull(vm.filteredSongItems.value.firstOrNull { it.title == "How Great Thou Art" })

        assertTrue(vm.updateSong(original, original.copy(number = "0042")))
        awaitUntil("the renumber") { vm.filteredSongItems.value.any { it.number == "0042" } }
        assertTrue(File(File(dir, "Hymnal"), "0042 - How Great Thou Art.song").exists())
    }

    @Test
    fun `moving a song to another songbook moves its file`() {
        val vm = viewModel()
        val original = assertNotNull(vm.filteredSongItems.value.firstOrNull { it.title == "Blessed Be Your Name" })

        assertTrue(vm.updateSong(original, original.copy(songbook = "Hymnal")))
        awaitUntil("the move") {
            vm.filteredSongItems.value.firstOrNull { it.title == "Blessed Be Your Name" }?.songbook == "Hymnal"
        }

        assertTrue(File(File(dir, "Hymnal"), "0005 - Blessed Be Your Name.song").exists())
        assertFalse(File(original.sourceFile).exists())
        assertFalse(File(dir, "Worship").exists(), "the emptied songbook folder is cleaned up")
    }

    // ── Deleting ────────────────────────────────────────────────────────────────

    @Test
    fun `deleting removes the file and the song`() {
        val vm = viewModel()
        val song = assertNotNull(vm.filteredSongItems.value.firstOrNull { it.title == "Blessed Be Your Name" })

        assertTrue(vm.deleteSong(song))
        awaitUntil("removal") { vm.filteredSongItems.value.none { it.title == "Blessed Be Your Name" } }
        assertFalse(File(song.sourceFile).exists())
    }

    @Test
    fun `deleting the last song in a songbook removes the empty folder`() {
        val vm = viewModel()
        val song = assertNotNull(vm.filteredSongItems.value.firstOrNull { it.songbook == "Worship" })
        vm.deleteSong(song)
        awaitUntil("folder cleanup") { !File(dir, "Worship").exists() }
    }

    @Test
    fun `deleting a song with no file still succeeds`() {
        val vm = viewModel()
        assertTrue(vm.deleteSong(SongItem(number = "1", title = "Phantom", songbook = "Hymnal")))
    }

    // ── Schedule hand-off ───────────────────────────────────────────────────────

    @Test
    fun `the current song can be added to the schedule`() {
        val vm = viewModel()
        vm.selectByTitle("Amazing Grace")

        var captured: Triple<Int, String, String>? = null
        assertTrue(
            vm.addCurrentSongToSchedule { number, title, songbook, _ ->
                captured = Triple(number, title, songbook)
            },
        )
        assertEquals(Triple(1, "Amazing Grace", "Hymnal"), captured)
    }

    @Test
    fun `adding to the schedule with nothing selected reports failure`() {
        val vm = SongsViewModel(
            AppSettings(),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        ).also { created.add(it) }
        var called = false
        assertFalse(vm.addCurrentSongToSchedule { _, _, _, _ -> called = true })
        assertFalse(called)
    }
}
