package org.churchpresenter.songs

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongsEditingCoverage2Test {

    private val dir: File = Files.createTempDirectory("cp-editing2").toFile()
    private val vms = mutableListOf<SongsViewModel>()

    @AfterTest
    fun tearDown() {
        vms.forEach { it.dispose() }
        dir.deleteRecursively()
    }

    private fun write(number: String, title: String, book: String = "Hymnal"): File {
        val file = File(File(dir, book).apply { mkdirs() }, "$number - $title.song")
        SongFileParser().writeSongFile(
            SongItem(number = number, title = title, songbook = book, lyrics = listOf("[Verse 1]", "x")),
            file.absolutePath,
        )
        return file
    }

    private fun vm(storage: String = dir.absolutePath) = SongsViewModel(
        AppSettings(songSettings = SongSettings(storageDirectory = storage)),
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
        enableFolderWatcher = false,
    ).also { vms += it }

    @Test
    fun `an empty folder is deleted, and anything else is left alone`() {
        val v = vm()
        val empty = File(dir, "Empty").apply { mkdirs() }
        val full = File(dir, "Full").apply { mkdirs() }
        File(full, "keep.txt").writeText("x")
        val plain = File(dir, "plain.txt").apply { writeText("x") }

        v.deleteIfEmpty(null)
        v.deleteIfEmpty(plain)
        v.deleteIfEmpty(full)
        v.deleteIfEmpty(empty)

        assertFalse(empty.exists())
        assertTrue(full.exists())
        assertTrue(plain.exists())
    }

    @Test
    fun `an edit to a song with no file and no library folder is not saved`() {
        val v = vm(storage = "")
        assertFalse(v.updateSong(SongItem(number = "1", title = "A"), SongItem(number = "1", title = "B")))
    }

    @Test
    fun `an edit to a song without a file in a library folder writes nowhere`() {
        val v = vm()
        assertFalse(v.updateSong(SongItem(number = "1", title = "A"), SongItem(number = "1", title = "B")))
    }

    @Test
    fun `moving a song to another songbook keeps its file name and removes the emptied folder`() {
        val file = write("1", "Grace", book = "Old")
        val v = vm()
        val song = v.filteredSongItems.value.single()

        assertTrue(v.updateSong(song, song.copy(songbook = "New")))

        assertTrue(File(File(dir, "New"), file.name).exists())
        assertFalse(File(dir, "Old").exists())
    }

    @Test
    fun `deleting a song with no file only reloads`() {
        write("1", "Grace")
        val v = vm()
        assertTrue(v.deleteSong(SongItem(number = "9", title = "Ghost")))
        assertEquals(1, v.filteredSongItems.value.size)
    }

    @Test
    fun `deleting a song whose file is already gone still succeeds and keeps a busy folder`() {
        write("1", "Grace")
        val v = vm()
        val missing = File(File(dir, "Hymnal"), "9 - Gone.song").absolutePath
        assertTrue(v.deleteSong(SongItem(number = "9", title = "Gone", sourceFile = missing)))
        assertTrue(File(dir, "Hymnal").exists())
    }

    @Test
    fun `the last song deleted takes its folder with it`() {
        val file = write("1", "Grace", book = "Solo")
        val v = vm()
        assertTrue(v.deleteSong(v.filteredSongItems.value.single().copy(sourceFile = file.absolutePath)))
        assertFalse(File(dir, "Solo").exists())
    }

    @Test
    fun `adding the selection to the schedule needs a song selected`() {
        val v = vm()
        assertFalse(v.addCurrentSongToSchedule { _, _, _, _ -> })
    }

    @Test
    fun `the selected song goes to the schedule with its number as a number`() {
        write("12b", "Odd")
        val v = vm()
        v.selectSong(0)
        var added: Pair<Int, String>? = null

        assertTrue(v.addCurrentSongToSchedule { number, title, _, _ -> added = number to title })
        assertEquals(0 to "Odd", added)
    }

    @Test
    fun `a songbook background skips other books`() {
        write("1", "Grace", book = "Hymnal")
        write("2", "Shine", book = "Choruses")
        val v = vm()
        val bg = SongBackground()
        assertEquals(1, v.applyBackgroundToSongbook("Choruses", bg, bg))
    }

    @Test
    fun `a section of blank lines is dropped from parsed lyrics`() =
        assertEquals(listOf("[Verse 1]", "a"), parseLyrics("Verse 1@%a@\$ @% @$"))
}
