package org.churchpresenter.songs

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
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
import kotlin.test.assertTrue

class SongsViewModelRemoteEditingTest {

    private lateinit var dir: File
    private val created = mutableListOf<SongsViewModel>()
    private val events = mutableListOf<Pair<String, Map<String, Any?>>>()

    @BeforeTest
    fun createLibrary() {
        dir = Files.createTempDirectory("cp-songs-remote-cov").toFile()
        events.clear()
        write(SongItem("1", "Amazing Grace", "Hymnal", lyrics = listOf("[Verse 1]", "grace line")))
        write(SongItem("2", "Be Thou My Vision", "Hymnal", lyrics = listOf("[Verse 1]", "vision line")))
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    private fun write(song: SongItem) {
        val file = File(File(dir, song.songbook), "${song.number} - ${song.title}.song")
        SongFileParser().writeSongFile(song, file.absolutePath)
    }

    private fun viewModel(): SongsViewModel = SongsViewModel(
        AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
        enableFolderWatcher = false,
        remoteSyncLog = { event, fields -> events += event to fields },
    ).also { created += it }

    private val catalog = listOf(
        SongItem("10", "Remote One", "Kids/AM"),
        SongItem("11", "Remote Two", "Kids/AM"),
    )

    private val SongsViewModel.titles get() = filteredSongItems.value.map { it.title }

    // ── Following a primary ──────────────────────────────────────────────────────

    @Test
    fun `mirroring a catalog logs it with its size`() {
        val vm = viewModel()

        vm.setInstanceLinkSource(active = true, catalog = catalog, fetchLyrics = null)

        assertEquals("songs_sync_result" to mapOf("catalogPresent" to true, "songCount" to 2), events.last())
        assertEquals(listOf("Remote One", "Remote Two"), vm.titles)
    }

    @Test
    fun `mirroring with no catalog yet empties the list and says so`() {
        val vm = viewModel()

        vm.setInstanceLinkSource(active = true, catalog = null, fetchLyrics = null)

        assertEquals(mapOf("catalogPresent" to false, "songCount" to 0), events.last().second)
        assertTrue(vm.filteredSongItems.value.isEmpty())
    }

    @Test
    fun `a mirrored nested songbook lists its parent too`() {
        val vm = viewModel()

        vm.setInstanceLinkSource(active = true, catalog = catalog, fetchLyrics = null)

        assertEquals(listOf("Kids", "Kids/AM"), vm.songbooks.value)
    }

    @Test
    fun `a successful lyric fetch is logged as a success`() {
        val vm = viewModel()
        vm.setInstanceLinkSource(active = true, catalog = catalog, fetchLyrics = { _, _ -> listOf("[Verse 1]", "x") })

        vm.selectSong(0)

        val (event, fields) = events.last()
        assertEquals("song_detail_fetch_result", event)
        assertEquals(mapOf("number" to "10", "songbook" to "Kids/AM", "success" to true), fields)
    }

    @Test
    fun `a failed lyric fetch is logged as a failure`() {
        val vm = viewModel()
        vm.setInstanceLinkSource(active = true, catalog = catalog, fetchLyrics = { _, _ -> null })

        vm.selectSong(1)

        assertEquals(false, events.last().second["success"])
        assertEquals("11", events.last().second["number"])
    }

    @Test
    fun `fetched lyrics reach the songs data and bump the update signal`() {
        val vm = viewModel()
        vm.setInstanceLinkSource(active = true, catalog = catalog, fetchLyrics = { _, _ -> listOf("[Verse 1]", "x") })

        vm.selectSong(0)

        assertEquals(1, vm.remoteLyricsUpdated.value)
        assertEquals(listOf("[Verse 1]", "x"), vm.songsData.value.getSongs().first { it.number == "10" }.lyrics)
        assertEquals(listOf("x"), vm.getLyricSections().single().lines)
    }

    @Test
    fun `lyrics landing after the operator moved on do not bump the signal for that song`() {
        val vm = viewModel()
        var calls = 0
        vm.setInstanceLinkSource(active = true, catalog = catalog, fetchLyrics = { _, _ ->
            calls++
            if (calls == 1) vm.selectSong(1)
            listOf("[Verse 1]", "words $calls")
        })

        vm.selectSong(0)

        assertEquals(2, calls)
        assertEquals(1, vm.remoteLyricsUpdated.value)
        assertEquals(1, vm.selectedSongIndex.value)
        assertTrue(vm.filteredSongItems.value.all { it.lyrics.isNotEmpty() })
    }

    @Test
    fun `a reload is ignored while following`() {
        val vm = viewModel()
        vm.setInstanceLinkSource(active = true, catalog = catalog, fetchLyrics = null)

        vm.loadSongs()

        assertEquals(listOf("Remote One", "Remote Two"), vm.titles)
    }

    @Test
    fun `the mirrored catalog can be searched`() {
        val vm = viewModel()
        vm.setInstanceLinkSource(active = true, catalog = catalog, fetchLyrics = null)

        vm.updateSearchQuery("Two")

        assertEquals(listOf("Remote Two"), vm.titles)
    }

    @Test
    fun `a songbook background is refused while following`() {
        val vm = viewModel()
        vm.setInstanceLinkSource(active = true, catalog = catalog, fetchLyrics = null)

        assertEquals(0, vm.applyBackgroundToSongbook("Kids/AM", SongBackground(), SongBackground()))
    }

    @Test
    fun `disconnecting brings the local library back`() {
        val vm = viewModel()
        vm.setInstanceLinkSource(active = true, catalog = catalog, fetchLyrics = null)

        vm.setInstanceLinkSource(active = false, catalog = null, fetchLyrics = null)

        assertEquals(setOf("Amazing Grace", "Be Thou My Vision"), vm.titles.toSet())
    }

    // ── Editing the local library ────────────────────────────────────────────────

    @Test
    fun `an edited song stays selected after the reload`() {
        val vm = viewModel()
        val idx = vm.filteredSongItems.value.indexOfFirst { it.title == "Be Thou My Vision" }
        vm.selectSong(idx)
        val old = vm.filteredSongItems.value[idx]

        assertTrue(vm.updateSong(old, old.copy(title = "Be Thou My Light")))

        assertEquals("Be Thou My Light", vm.filteredSongItems.value[vm.selectedSongIndex.value].title)
    }

    @Test
    fun `an edit to a song with no file is saved without moving anything`() {
        val vm = viewModel()
        val old = vm.filteredSongItems.value.first { it.title == "Amazing Grace" }

        vm.updateSong(old.copy(sourceFile = ""), old.copy(lyrics = listOf("[Verse 1]", "new words")))

        assertTrue(File(old.sourceFile).exists())
    }

    @Test
    fun `a new song in a nested songbook creates the nested folder`() {
        val vm = viewModel()

        assertTrue(vm.createSong(SongItem("5", "Kids Song", "Kids/PM", lyrics = listOf("[Verse 1]", "x"))))

        assertTrue(File(dir, "Kids/PM/0005 - Kids Song.song").exists())
        assertTrue("Kids" in vm.songbooks.value)
        assertTrue("Kids/PM" in vm.songbooks.value)
    }

    @Test
    fun `a songbook background is written to that book and reported`() {
        val vm = viewModel()
        val dusk = SongBackground(type = SongBackgroundType.COLOR, color = "#101010")

        assertEquals(2, vm.applyBackgroundToSongbook("Hymnal", dusk, SongBackground()))

        assertTrue(vm.filteredSongItems.value.all { it.background == dusk })
    }
}
