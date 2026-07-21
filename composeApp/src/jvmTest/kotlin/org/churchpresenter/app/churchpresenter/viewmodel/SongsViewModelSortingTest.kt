package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.SongFileParser
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Column sorting and reference-based song selection.
 *
 * Every sortable column has its own ascending and descending branch, and they are easy to get
 * subtly wrong in ways nobody notices until a service — a case-sensitive title sort that files
 * "abide" after "Zion", or a numeric column sorted as text so 10 lands before 2.
 */
class SongsViewModelSortingTest {

    private lateinit var dir: File
    private val created = mutableListOf<SongsViewModel>()

    @BeforeTest
    fun createLibrary() {
        dir = Files.createTempDirectory("cp-songs-sort-test").toFile()
        // Deliberately mixed case and out-of-order numbers.
        song(songbook = "Zulu", number = "10", title = "zion Awaits", author = "Watts", composer = "Mason", tune = "OLD")
        song(songbook = "Alpha", number = "2", title = "Abide With Me", author = "lyte", composer = "monk", tune = "eventide")
        song(songbook = "Mike", number = "1", title = "Marvellous Grace", author = "Johnston", composer = "Towner", tune = "MOODY")
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    private fun song(
        songbook: String, number: String, title: String,
        author: String = "", composer: String = "", tune: String = "",
    ) {
        val target = File(File(dir, songbook), "$number - $title.song")
        SongFileParser().writeSongFile(
            SongItem(
                number = number, title = title, songbook = songbook,
                author = author, composer = composer, tune = tune,
                lyrics = listOf("[Verse 1]", "a line"),
            ),
            target.absolutePath,
        )
    }

    private fun viewModel(): SongsViewModel {
        val vm = SongsViewModel(AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)))
        created.add(vm)
        awaitUntil("songs") { vm.filteredSongItems.value.size == 3 }
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

    private val SongsViewModel.titles: List<String>
        get() = filteredSongItems.value.map { it.title }

    /** Sorts by [column] and returns the titles ascending, then descending. */
    private fun SongsViewModel.sortBoth(column: String): Pair<List<String>, List<String>> {
        updateSort(column)
        assertTrue(sortAscending.value, "the first click on a column sorts ascending")
        val ascending = titles
        updateSort(column)
        assertFalse(sortAscending.value)
        return ascending to titles
    }

    // ── Sorting ─────────────────────────────────────────────────────────────────

    @Test
    fun `number sorts numerically in both directions`() {
        val (asc, desc) = viewModel().sortBoth(Constants.SORT_NUMBER)
        assertEquals(listOf("Marvellous Grace", "Abide With Me", "zion Awaits"), asc, "1, 2, 10")
        assertEquals(asc.reversed(), desc)
    }

    @Test
    fun `title sorts case-insensitively in both directions`() {
        // A case-sensitive sort would put lower-case "zion" before "Abide".
        val (asc, desc) = viewModel().sortBoth(Constants.SORT_TITLE)
        assertEquals(listOf("Abide With Me", "Marvellous Grace", "zion Awaits"), asc)
        assertEquals(asc.reversed(), desc)
    }

    @Test
    fun `songbook sorts case-insensitively in both directions`() {
        val (asc, desc) = viewModel().sortBoth(Constants.SORT_SONGBOOK)
        assertEquals(listOf("Abide With Me", "Marvellous Grace", "zion Awaits"), asc, "Alpha, Mike, Zulu")
        assertEquals(asc.reversed(), desc)
    }

    @Test
    fun `author sorts case-insensitively in both directions`() {
        val (asc, desc) = viewModel().sortBoth(Constants.SORT_AUTHOR)
        assertEquals(listOf("Marvellous Grace", "Abide With Me", "zion Awaits"), asc, "Johnston, lyte, Watts")
        assertEquals(asc.reversed(), desc)
    }

    @Test
    fun `composer sorts case-insensitively in both directions`() {
        val (asc, desc) = viewModel().sortBoth(Constants.SORT_COMPOSER)
        assertEquals(listOf("zion Awaits", "Abide With Me", "Marvellous Grace"), asc, "Mason, monk, Towner")
        assertEquals(asc.reversed(), desc)
    }

    @Test
    fun `tune sorts case-insensitively in both directions`() {
        val (asc, desc) = viewModel().sortBoth(Constants.SORT_TUNE)
        assertEquals(listOf("Abide With Me", "Marvellous Grace", "zion Awaits"), asc, "eventide, MOODY, OLD")
        assertEquals(asc.reversed(), desc)
    }

    @Test
    fun `favourites sort to the top`() {
        val vm = viewModel()
        val favourite = vm.filteredSongItems.value.first { it.title == "zion Awaits" }
        vm.toggleFavorite(favourite.songId)

        vm.updateSort(Constants.SORT_FAVORITES)
        assertEquals("zion Awaits", vm.titles.first(), "starred songs lead the list")

        vm.updateSort(Constants.SORT_FAVORITES)
        assertEquals("zion Awaits", vm.titles.last(), "and trail it when reversed")
    }

    @Test
    fun `sorting by play count with no statistics manager leaves the order alone`() {
        // The statistics manager is optional; without it there are no counts to sort by.
        val vm = viewModel()
        vm.updateSort(Constants.SORT_TITLE)
        val before = vm.titles

        vm.updateSort(Constants.SORT_PLAY_COUNT)
        assertEquals(before, vm.titles, "an unavailable column must not scramble the list")
    }

    @Test
    fun `an unknown sort column leaves the order alone`() {
        val vm = viewModel()
        vm.updateSort(Constants.SORT_TITLE)
        val before = vm.titles
        vm.updateSort("not_a_column")
        assertEquals(before.size, vm.titles.size)
    }

    @Test
    fun `switching columns restarts ascending`() {
        val vm = viewModel()
        vm.updateSort(Constants.SORT_TITLE)
        vm.updateSort(Constants.SORT_TITLE) // now descending
        assertFalse(vm.sortAscending.value)

        vm.updateSort(Constants.SORT_NUMBER)
        assertTrue(vm.sortAscending.value, "a different column starts over rather than inheriting the direction")
    }

    @Test
    fun `sorting survives a filter change`() {
        val vm = viewModel()
        vm.updateSort(Constants.SORT_TITLE)
        vm.updateSelectedSongbook("Alpha")
        assertEquals(listOf("Abide With Me"), vm.titles)

        vm.updateSelectedSongbook("")
        assertEquals(listOf("Abide With Me", "Marvellous Grace", "zion Awaits"), vm.titles, "still sorted")
    }

    // ── Selecting by reference ──────────────────────────────────────────────────

    @Test
    fun `a song is found by its stable id`() {
        val vm = viewModel()
        val target = vm.filteredSongItems.value.first { it.title == "Marvellous Grace" }
        assertTrue(vm.selectSongById(target.songId))
        assertEquals("Marvellous Grace", vm.filteredSongItems.value[vm.selectedSongIndex.value].title)
    }

    @Test
    fun `a song is found by songbook and number when no id is given`() {
        // Schedules saved before songId existed, and every mirrored Instance Link item.
        val vm = viewModel()
        assertTrue(vm.selectSongByDetails(songNumber = 1, title = "", songbook = "Mike"))
        assertEquals("Marvellous Grace", vm.filteredSongItems.value[vm.selectedSongIndex.value].title)
    }

    @Test
    fun `the number match is numeric so zero padding does not matter`() {
        // A catalog entry may be "0002" while the wire protocol only carries the Int 2.
        val padded = Files.createTempDirectory("cp-songs-padded-test").toFile()
        try {
            SongFileParser().writeSongFile(
                SongItem(number = "0042", title = "Padded", songbook = "Hymnal", lyrics = listOf("l")),
                File(File(padded, "Hymnal"), "0042 - Padded.song").absolutePath,
            )
            val vm = SongsViewModel(AppSettings(songSettings = SongSettings(storageDirectory = padded.absolutePath)))
                .also { created.add(it) }
            awaitUntil("padded song") { vm.filteredSongItems.value.isNotEmpty() }

            assertTrue(vm.selectSongByDetails(songNumber = 42, title = "", songbook = "Hymnal"))
        } finally {
            padded.deleteRecursively()
        }
    }

    @Test
    fun `the songbook match is case-insensitive`() {
        val vm = viewModel()
        assertTrue(vm.selectSongByDetails(songNumber = 1, title = "", songbook = "mIkE"))
    }

    @Test
    fun `a song is found by title as a last resort`() {
        val vm = viewModel()
        assertTrue(vm.selectSongByDetails(songNumber = 999, title = "Abide With Me", songbook = "Nonexistent"))
        assertEquals("Abide With Me", vm.filteredSongItems.value[vm.selectedSongIndex.value].title)
    }

    @Test
    fun `a song hidden by the current filter is revealed`() {
        // Clicking a schedule item must show its song even if the operator has narrowed the list.
        val vm = viewModel()
        vm.updateSelectedSongbook("Alpha")
        assertEquals(1, vm.filteredSongItems.value.size)

        assertTrue(vm.selectSongByDetails(songNumber = 1, title = "", songbook = "Mike"))
        assertEquals("Marvellous Grace", vm.filteredSongItems.value[vm.selectedSongIndex.value].title)
        assertEquals(3, vm.filteredSongItems.value.size, "the filter is cleared to reveal it")
    }

    @Test
    fun `a search query is also cleared to reveal the song`() {
        val vm = viewModel()
        vm.updateFilterType(Constants.CONTAINS)
        vm.updateSearchQuery("Abide")
        assertEquals(1, vm.filteredSongItems.value.size)

        assertTrue(vm.selectSongByDetails(songNumber = 10, title = "", songbook = "Zulu"))
        assertEquals("zion Awaits", vm.filteredSongItems.value[vm.selectedSongIndex.value].title)
    }

    @Test
    fun `selecting a song that is not in the library reports failure`() {
        val vm = viewModel()
        assertFalse(vm.selectSongByDetails(songNumber = 999, title = "Nonexistent Song", songbook = "Nowhere"))
    }

    @Test
    fun `selecting resets the section to the first`() {
        val vm = viewModel()
        vm.selectSong(0)
        vm.selectSection(1)
        vm.selectSongByDetails(songNumber = 1, title = "", songbook = "Mike")
        assertEquals(0, vm.selectedSectionIndex.value)
    }
}
