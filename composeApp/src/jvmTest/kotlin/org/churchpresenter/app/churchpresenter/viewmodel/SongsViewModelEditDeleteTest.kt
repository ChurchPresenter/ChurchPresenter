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
import kotlin.test.assertTrue

/**
 * Editing and deleting songs, which rewrite and move `.song` files on disk.
 *
 * The subtlety is the file bookkeeping: changing a song's songbook must move its file into the new
 * folder and tidy the old folder if it is now empty; changing the title or number renames the file;
 * a lyrics-only edit must NOT move anything. Deletion removes the file and cleans an emptied folder
 * but leaves a shared one alone. Getting this wrong strands orphan files or deletes a folder still
 * holding other songs — the library tests only cover the simplest in-place edit.
 */
class SongsViewModelEditDeleteTest {

    private lateinit var dir: File
    private val created = mutableListOf<SongsViewModel>()

    @BeforeTest
    fun createLibrary() {
        dir = Files.createTempDirectory("cp-songs-edit-test").toFile()
        writeSong("Hymnal", "0001", "Amazing Grace")
        writeSong("Hymnal", "0002", "How Great Thou Art")
        writeSong("Solo", "0003", "Alone Song") // Solo holds only this song
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    private fun writeSong(songbook: String, number: String, title: String) {
        val target = File(File(dir, songbook), "$number - $title.song")
        SongFileParser().writeSongFile(
            SongItem(number = number, title = title, songbook = songbook, lyrics = listOf("[Verse 1]", "a line")),
            target.absolutePath,
        )
    }

    private fun viewModel(): SongsViewModel {
        val vm = SongsViewModel(AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)))
        created.add(vm)
        awaitUntil("songs") { vm.filteredSongItems.value.size >= 3 }
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

    private fun SongsViewModel.songTitled(title: String): SongItem =
        filteredSongItems.value.first { it.title == title }

    // ── updateSong: moving between songbooks ─────────────────────────────────────

    @Test
    fun `moving a song to another songbook relocates its file and tidies the empty folder`() {
        val vm = viewModel()
        val song = vm.songTitled("Alone Song")

        val ok = vm.updateSong(song, song.copy(songbook = "Worship"))

        assertTrue(ok)
        awaitUntil("reload") { vm.filteredSongItems.value.any { it.songbook == "Worship" } }
        assertTrue(File(dir, "Worship/0003 - Alone Song.song").exists(), "the file moved into the new songbook folder")
        assertFalse(File(dir, "Solo/0003 - Alone Song.song").exists(), "the old copy is gone")
        assertFalse(File(dir, "Solo").exists(), "the now-empty old songbook folder is removed")
    }

    @Test
    fun `renaming the title renames the file in the same folder`() {
        val vm = viewModel()
        val song = vm.songTitled("Amazing Grace")

        val ok = vm.updateSong(song, song.copy(title = "Amazing Grace (Renamed)"))

        assertTrue(ok)
        awaitUntil("reload") { vm.filteredSongItems.value.any { it.title == "Amazing Grace (Renamed)" } }
        assertTrue(File(dir, "Hymnal/0001 - Amazing Grace (Renamed).song").exists())
        assertFalse(File(dir, "Hymnal/0001 - Amazing Grace.song").exists(), "the old filename is gone")
        assertTrue(File(dir, "Hymnal").exists(), "the shared songbook folder stays — it still has other songs")
    }

    @Test
    fun `a lyrics-only edit keeps the very same file`() {
        val vm = viewModel()
        val song = vm.songTitled("How Great Thou Art")
        val originalPath = song.sourceFile

        val ok = vm.updateSong(song, song.copy(lyrics = listOf("[Verse 1]", "edited line")))

        assertTrue(ok)
        awaitUntil("reload") { vm.songTitled("How Great Thou Art").lyrics.contains("edited line") }
        assertTrue(File(originalPath).exists(), "no title/number/songbook change means no move or rename")
    }

    @Test
    fun `editing a song whose file has vanished still saves the new copy`() {
        val vm = viewModel()
        val song = vm.songTitled("Amazing Grace")
        File(song.sourceFile).delete() // the file disappeared out from under us

        val ok = vm.updateSong(song, song.copy(title = "Recreated"))

        assertTrue(ok, "a missing source file skips the move and just writes the song")
        awaitUntil("reload") { vm.filteredSongItems.value.any { it.title == "Recreated" } }
    }

    // ── deleteSong ───────────────────────────────────────────────────────────────

    @Test
    fun `deleting a song removes its file and its now-empty folder`() {
        val vm = viewModel()
        val song = vm.songTitled("Alone Song")

        val ok = vm.deleteSong(song)

        assertTrue(ok)
        awaitUntil("reload") { vm.filteredSongItems.value.none { it.title == "Alone Song" } }
        assertFalse(File(song.sourceFile).exists())
        assertFalse(File(dir, "Solo").exists(), "an emptied songbook folder is cleaned up")
    }

    @Test
    fun `deleting one song leaves a folder that still holds others`() {
        val vm = viewModel()
        val song = vm.songTitled("Amazing Grace")

        vm.deleteSong(song)

        awaitUntil("reload") { vm.filteredSongItems.value.none { it.title == "Amazing Grace" } }
        assertFalse(File(song.sourceFile).exists())
        assertTrue(File(dir, "Hymnal").exists(), "How Great Thou Art still lives in Hymnal")
        assertTrue(File(dir, "Hymnal/0002 - How Great Thou Art.song").exists())
    }

    @Test
    fun `moving a song out of a shared songbook leaves that folder in place`() {
        val vm = viewModel()
        val song = vm.songTitled("Amazing Grace") // Hymnal also holds How Great Thou Art

        vm.updateSong(song, song.copy(songbook = "Worship"))

        awaitUntil("reload") { vm.filteredSongItems.value.any { it.songbook == "Worship" } }
        assertTrue(File(dir, "Worship/0001 - Amazing Grace.song").exists())
        assertTrue(File(dir, "Hymnal").exists(), "the old folder still has another song and must not be deleted")
        assertTrue(File(dir, "Hymnal/0002 - How Great Thou Art.song").exists())
    }

    @Test
    fun `deleting a song whose file has already vanished still succeeds`() {
        val vm = viewModel()
        val song = vm.songTitled("Alone Song")
        File(song.sourceFile).delete() // gone before the delete call

        val ok = vm.deleteSong(song)

        assertTrue(ok, "a missing file is nothing to delete, not an error")
    }

    // ── Refused while mirroring an Instance Link primary ─────────────────────────

    @Test
    fun `editing is refused while following a remote primary`() {
        val vm = viewModel()
        vm.setInstanceLinkSource(active = true, catalog = null, fetchDetail = null)

        val ghost = SongItem(number = "0009", title = "Ghost", songbook = "X")
        assertFalse(
            vm.updateSong(ghost, ghost.copy(title = "Renamed")),
            "a follower's library mirrors the primary; it must not write local files",
        )
    }

    @Test
    fun `deleting is refused while following a remote primary`() {
        val vm = viewModel()
        vm.setInstanceLinkSource(active = true, catalog = null, fetchDetail = null)

        assertFalse(vm.deleteSong(SongItem(number = "0009", title = "Ghost", songbook = "X")))
    }
}
