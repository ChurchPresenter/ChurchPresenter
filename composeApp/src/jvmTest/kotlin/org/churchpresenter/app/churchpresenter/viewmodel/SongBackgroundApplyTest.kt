package org.churchpresenter.app.churchpresenter.viewmodel

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-song backgrounds where they touch the library: applying one to a whole song book, and
 * reaching the presenter on the sections a song is split into.
 */
class SongBackgroundApplyTest {

    private lateinit var dir: File
    private val created = mutableListOf<SongsViewModel>()

    private val dusk = SongBackground(
        type = SongBackgroundType.GRADIENT,
        color = "#131a3a",
        colorEnd = "#3a2352",
        dim = 25,
        blur = 3,
    )
    private val band = SongBackground(type = SongBackgroundType.COLOR, color = "#2a1130", dim = 65)

    @BeforeTest
    fun createLibrary() {
        dir = Files.createTempDirectory("cp-song-bg-test").toFile()
        writeSong("Hymnal", "0001", "Amazing Grace")
        writeSong("Hymnal", "0002", "How Great Thou Art")
        writeSong("Chorus Book", "0003", "Other Book Song")
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    private fun writeSong(songbook: String, number: String, title: String) {
        SongFileParser().writeSongFile(
            SongItem(
                number = number,
                title = title,
                songbook = songbook,
                lyrics = listOf("[Verse 1]", "a line", "", "[Chorus]", "a chorus line"),
            ),
            File(File(dir, songbook), "$number - $title.song").absolutePath,
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
        assertTrue(vm.filteredSongItems.value.size >= 3, "the library loaded synchronously")
        return vm
    }

    private fun SongsViewModel.songTitled(title: String): SongItem =
        filteredSongItems.value.first { it.title == title }

    @Test
    fun `applying to a song book writes every song in it and leaves the others alone`() {
        val vm = viewModel()

        val written = vm.applyBackgroundToSongbook("Hymnal", dusk, band)

        assertEquals(2, written)
        assertEquals(dusk, vm.songTitled("Amazing Grace").background)
        assertEquals(band, vm.songTitled("Amazing Grace").lowerThirdBackground)
        assertEquals(dusk, vm.songTitled("How Great Thou Art").background)
        assertFalse(vm.songTitled("Other Book Song").background.isCustom, "another song book is untouched")
    }

    @Test
    fun `applying survives a reload, because it is the files that were rewritten`() {
        viewModel().applyBackgroundToSongbook("Hymnal", dusk, band)

        val reread = SongFileParser().parseSongFile(
            File(dir, "Hymnal/0001 - Amazing Grace.song").absolutePath,
        )

        assertEquals(dusk, reread?.background)
        assertEquals(band, reread?.lowerThirdBackground)
    }

    @Test
    fun `a blank song book applies to nothing`() {
        assertEquals(0, viewModel().applyBackgroundToSongbook("", dusk, band))
    }

    @Test
    fun `a song book nobody has applies to nothing`() {
        assertEquals(0, viewModel().applyBackgroundToSongbook("Not A Book", dusk, band))
    }

    @Test
    fun `a song whose file has gone is skipped rather than recreated`() {
        val vm = viewModel()
        val gone = File(dir, "Hymnal/0002 - How Great Thou Art.song")
        assertTrue(gone.delete())

        val written = vm.applyBackgroundToSongbook("Hymnal", dusk, band)

        assertEquals(1, written)
        assertFalse(gone.exists(), "a deleted song is not resurrected")
    }

    @Test
    fun `every section of a song carries the song's backgrounds to the presenter`() {
        val vm = viewModel()
        vm.applyBackgroundToSongbook("Hymnal", dusk, band)

        val sections = vm.getLyricSections(vm.songTitled("Amazing Grace"))

        assertTrue(sections.size > 1, "the song split into more than one section")
        assertTrue(sections.all { it.background == dusk && it.lowerThirdBackground == band })
    }

    @Test
    fun `the last section keeps both its end-of-song mark and its background`() {
        val vm = viewModel()
        vm.applyBackgroundToSongbook("Hymnal", dusk, band)

        val last = vm.getLyricSections(vm.songTitled("Amazing Grace")).last()

        assertTrue(last.isLastSection)
        assertEquals(dusk, last.background)
    }

    @Test
    fun `a song that inherits sends an inheriting background`() {
        val vm = viewModel()

        val sections = vm.getLyricSections(vm.songTitled("Other Book Song"))

        assertTrue(sections.none { it.background.isCustom })
    }
}
