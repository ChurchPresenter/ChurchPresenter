package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.SongFileParser
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        val vm = SongsViewModel(AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)))
        created.add(vm)
        awaitUntil("songs to load") { vm.filteredSongItems.value.isNotEmpty() }
        return vm
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
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
        val vm = SongsViewModel(AppSettings()).also { created.add(it) }
        awaitUntil("the load to finish") { !vm.isLoading.value }
        assertTrue(vm.filteredSongItems.value.isEmpty())
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
        assertTrue(vm.createSong(SongItem(number = "", title = "Untitled Hymn", songbook = "Hymnal", lyrics = listOf("l"))))
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

        val unconfigured = SongsViewModel(AppSettings()).also { created.add(it) }
        assertFalse(unconfigured.createSong(SongItem(number = "1", title = "T", songbook = "Hymnal", lyrics = listOf("l"))))
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
        val vm = SongsViewModel(AppSettings()).also { created.add(it) }
        var called = false
        assertFalse(vm.addCurrentSongToSchedule { _, _, _, _ -> called = true })
        assertFalse(called)
    }
}
