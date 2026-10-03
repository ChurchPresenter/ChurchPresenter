package org.churchpresenter.songs

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sorting the song list by how often each song has been sung.
 *
 * The column is only meaningful once play counts are attached — without one the list is
 * left alone, which `SongsViewModelSortingTest` covers. This drives the attached case, where the
 * order comes from the recorded play counts rather than from anything in the song file, and the
 * song never sung has to sort as zero rather than dropping out of the list.
 */
class SongsViewModelPlayCountSortTest {

    private lateinit var dir: File
    private val created = mutableListOf<SongsViewModel>()

    @BeforeTest
    fun createLibrary() {
        dir = Files.createTempDirectory("cp-songs-playcount-test").toFile()
        song(number = "1", title = "Sung Often")
        song(number = "2", title = "Sung Once")
        song(number = "3", title = "Never Sung")
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    private fun song(number: String, title: String) {
        SongFileParser().writeSongFile(
            SongItem(
                number = number, title = title, songbook = "Hymnal",
                lyrics = listOf("[Verse 1]", "a line"),
            ),
            File(File(dir, "Hymnal"), "$number - $title.song").absolutePath,
        )
    }

    /** Counts saying "Sung Often" went live three times and "Sung Once" once. */
    private fun statistics() = SongPlayCounts { songId -> mapOf("Hymnal::1" to 3, "Hymnal::2" to 1)[songId] ?: 0 }

    private fun viewModel(): SongsViewModel {
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        created.add(vm)
        assertEquals(3, vm.filteredSongItems.value.size, "the library loads synchronously on an immediate dispatcher")
        vm.setPlayCounts(statistics())
        return vm
    }

    private fun titles(vm: SongsViewModel) = vm.filteredSongItems.value.map { it.title }

    @Test
    fun `play count sorts least sung first when ascending`() {
        val vm = viewModel()

        vm.updateSort(Constants.SORT_PLAY_COUNT)

        assertTrue(vm.sortAscending.value, "a newly chosen column starts ascending")
        assertEquals(listOf("Never Sung", "Sung Once", "Sung Often"), titles(vm))
    }

    @Test
    fun `choosing the same column again reverses it`() {
        val vm = viewModel()

        vm.updateSort(Constants.SORT_PLAY_COUNT)
        vm.updateSort(Constants.SORT_PLAY_COUNT)

        assertEquals(listOf("Sung Often", "Sung Once", "Never Sung"), titles(vm))
    }

    @Test
    fun `a song that has never been sung counts as zero rather than disappearing`() {
        val vm = viewModel()

        vm.updateSort(Constants.SORT_PLAY_COUNT)

        assertEquals(3, vm.filteredSongItems.value.size)
        assertEquals("Never Sung", titles(vm).first())
    }
}
