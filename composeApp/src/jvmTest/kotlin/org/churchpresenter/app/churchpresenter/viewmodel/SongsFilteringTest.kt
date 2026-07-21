package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.server.SongCatalogResponse
import org.churchpresenter.app.churchpresenter.server.SongDto
import org.churchpresenter.app.churchpresenter.server.SongbookEntry
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Song search and filtering — how an operator finds a song mid-service, under time pressure.
 *
 * Songs are injected through `setInstanceLinkSource`, the Instance Link follower path, because it
 * is the one public entry point that populates the catalog from a list rather than from disk. That
 * keeps these tests free of the filesystem and of `loadSongs`' async reload, while exercising the
 * same `applyFilters` code the local path uses.
 */
class SongsFilteringTest {

    private val created = mutableListOf<SongsViewModel>()

    @AfterTest
    fun cleanup() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
    }

    /** Builds a view model already holding [books] worth of songs. */
    private fun vmWith(vararg books: Pair<String, List<Pair<String, String>>>): SongsViewModel {
        val entries = books.map { (bookName, songs) ->
            SongbookEntry(
                bookName = bookName,
                songTotal = songs.size,
                songs = songs.map { (number, title) -> SongDto(number = number, title = title) },
            )
        }
        val catalog = SongCatalogResponse(
            songBook = entries,
            songBooks = entries.size,
            total = entries.sumOf { it.songs.size },
        )
        return SongsViewModel(AppSettings()).also {
            created.add(it)
            it.setInstanceLinkSource(active = true, catalog = catalog, fetchDetail = null)
        }
    }

    private val defaultCatalog = arrayOf(
        "Hymnal" to listOf(
            "1" to "Amazing Grace",
            "2" to "How Great Thou Art",
            "10" to "Great Is Thy Faithfulness",
        ),
        "Worship" to listOf(
            "1" to "Amazing Love",
            "5" to "Blessed Be Your Name",
        ),
    )

    private val SongsViewModel.titles: List<String>
        get() = filteredSongItems.value.map { it.title }

    // ── Baseline ────────────────────────────────────────────────────────────────

    @Test
    fun `all songs are listed with no filter applied`() {
        val vm = vmWith(*defaultCatalog)
        assertEquals(5, vm.filteredSongItems.value.size)
    }

    @Test
    fun `songbooks are discovered from the catalog`() {
        val vm = vmWith(*defaultCatalog)
        assertTrue(vm.songbooks.value.containsAll(listOf("Hymnal", "Worship")))
    }

    // ── Contains ────────────────────────────────────────────────────────────────

    @Test
    fun `contains matches anywhere in the title`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateFilterType(Constants.CONTAINS)
        vm.updateSearchQuery("great")
        assertEquals(listOf("How Great Thou Art", "Great Is Thy Faithfulness"), vm.titles)
    }

    @Test
    fun `contains is case-insensitive`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateFilterType(Constants.CONTAINS)
        vm.updateSearchQuery("AMAZING")
        assertEquals(listOf("Amazing Grace", "Amazing Love"), vm.titles)
    }

    @Test
    fun `contains searches the number and title together`() {
        // The haystack is "<number>. <title>", so an operator can type "10. Great".
        val vm = vmWith(*defaultCatalog)
        vm.updateFilterType(Constants.CONTAINS)
        vm.updateSearchQuery("10. Great")
        assertEquals(listOf("Great Is Thy Faithfulness"), vm.titles)
    }

    @Test
    fun `a query matching nothing yields an empty list`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateFilterType(Constants.CONTAINS)
        vm.updateSearchQuery("no such song exists")
        assertTrue(vm.filteredSongItems.value.isEmpty())
    }

    @Test
    fun `clearing the query restores the full list`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateFilterType(Constants.CONTAINS)
        vm.updateSearchQuery("amazing")
        assertEquals(2, vm.filteredSongItems.value.size)
        vm.updateSearchQuery("")
        assertEquals(5, vm.filteredSongItems.value.size)
    }

    // ── Starts with ─────────────────────────────────────────────────────────────

    @Test
    fun `starts-with anchors to the beginning of the title`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateFilterType(Constants.STARTS_WITH)
        vm.updateSearchQuery("Great")
        assertEquals(listOf("Great Is Thy Faithfulness"), vm.titles, "'How Great' must not match")
    }

    @Test
    fun `starts-with also matches the song number`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateFilterType(Constants.STARTS_WITH)
        vm.updateSearchQuery("1")
        // Numbers 1, 10 and 1 (Worship) all start with "1"; number 2 and 5 do not.
        assertEquals(listOf("Amazing Grace", "Great Is Thy Faithfulness", "Amazing Love"), vm.titles)
    }

    // ── Exact match ─────────────────────────────────────────────────────────────

    @Test
    fun `exact match requires the whole title`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateFilterType(Constants.EXACT_MATCH)
        vm.updateSearchQuery("Amazing Grace")
        assertEquals(listOf("Amazing Grace"), vm.titles)

        vm.updateSearchQuery("Amazing")
        assertTrue(vm.filteredSongItems.value.isEmpty(), "a partial title must not match exactly")
    }

    @Test
    fun `exact match on a number finds that number in every songbook`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateFilterType(Constants.EXACT_MATCH)
        vm.updateSearchQuery("1")
        assertEquals(listOf("Amazing Grace", "Amazing Love"), vm.titles)
    }

    @Test
    fun `exact match is case-insensitive`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateFilterType(Constants.EXACT_MATCH)
        vm.updateSearchQuery("amazing grace")
        assertEquals(listOf("Amazing Grace"), vm.titles)
    }

    // ── Songbook filter ─────────────────────────────────────────────────────────

    @Test
    fun `selecting a songbook narrows the list to it`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateSelectedSongbook("Worship")
        assertEquals(listOf("Amazing Love", "Blessed Be Your Name"), vm.titles)
    }

    @Test
    fun `a songbook filter combines with a search query`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateSelectedSongbook("Hymnal")
        vm.updateFilterType(Constants.CONTAINS)
        vm.updateSearchQuery("amazing")
        assertEquals(listOf("Amazing Grace"), vm.titles, "the Worship 'Amazing Love' must be excluded")
    }

    @Test
    fun `a parent songbook includes its sub-books`() {
        // Selecting "Kids" must also show "Kids/AM" and "Kids/PM" — prefix matching on the path.
        val vm = vmWith(
            "Kids" to listOf("1" to "Jesus Loves Me"),
            "Kids/AM" to listOf("2" to "This Little Light"),
            "Kids/PM" to listOf("3" to "Deep And Wide"),
            "Adults" to listOf("4" to "Amazing Grace"),
        )
        vm.updateSelectedSongbook("Kids")
        assertEquals(
            listOf("Jesus Loves Me", "This Little Light", "Deep And Wide"),
            vm.titles,
        )
        assertFalse("Amazing Grace" in vm.titles, "an unrelated songbook must not leak in")
    }

    @Test
    fun `a similarly named songbook is not treated as a sub-book`() {
        // "Kidsongs" starts with "Kids" as a string but is a different book; the separator matters.
        val vm = vmWith(
            "Kids" to listOf("1" to "Jesus Loves Me"),
            "Kidsongs" to listOf("2" to "Unrelated Song"),
        )
        vm.updateSelectedSongbook("Kids")
        assertEquals(listOf("Jesus Loves Me"), vm.titles)
    }

    @Test
    fun `an unknown songbook selection is ignored rather than emptying the list`() {
        // "All Song Books" and any stale selection fall through to showing everything.
        val vm = vmWith(*defaultCatalog)
        vm.updateSelectedSongbook("Not A Real Songbook")
        assertEquals(5, vm.filteredSongItems.value.size)
    }

    // ── Sorting ─────────────────────────────────────────────────────────────────

    @Test
    fun `sorting by number is numeric, not lexicographic`() {
        // Lexicographic order would put "10" before "2".
        val vm = vmWith("Hymnal" to listOf("2" to "Two", "10" to "Ten", "1" to "One"))
        vm.updateSort(Constants.SORT_NUMBER)
        assertEquals(listOf("One", "Two", "Ten"), vm.titles)
    }

    @Test
    fun `sorting the same column again reverses the order`() {
        val vm = vmWith("Hymnal" to listOf("2" to "Two", "10" to "Ten", "1" to "One"))
        vm.updateSort(Constants.SORT_NUMBER)
        assertTrue(vm.sortAscending.value)
        vm.updateSort(Constants.SORT_NUMBER)
        assertFalse(vm.sortAscending.value)
        assertEquals(listOf("Ten", "Two", "One"), vm.titles)
    }

    @Test
    fun `sorting by title is alphabetical`() {
        val vm = vmWith("Hymnal" to listOf("1" to "Zion", "2" to "Abide", "3" to "Marvellous"))
        vm.updateSort(Constants.SORT_TITLE)
        assertEquals(listOf("Abide", "Marvellous", "Zion"), vm.titles)
    }

    @Test
    fun `the sort indicator reflects the active column and direction`() {
        val vm = vmWith(*defaultCatalog)
        vm.updateSort(Constants.SORT_TITLE)
        assertTrue(vm.getSortIndicator(Constants.SORT_TITLE).isNotEmpty())
        assertEquals("", vm.getSortIndicator(Constants.SORT_NUMBER), "inactive columns show no indicator")

        val ascending = vm.getSortIndicator(Constants.SORT_TITLE)
        vm.updateSort(Constants.SORT_TITLE)
        assertTrue(vm.getSortIndicator(Constants.SORT_TITLE) != ascending, "direction should be visible")
    }

    // ── Selection and navigation ────────────────────────────────────────────────

    @Test
    fun `songs can be stepped through in the filtered order`() {
        val vm = vmWith(*defaultCatalog)
        vm.selectSong(0)
        assertTrue(vm.navigateNextSong())
        assertEquals(1, vm.selectedSongIndex.value)
        assertTrue(vm.navigatePreviousSong())
        assertEquals(0, vm.selectedSongIndex.value)
    }

    @Test
    fun `navigation stops at both ends of the list`() {
        val vm = vmWith(*defaultCatalog)
        vm.selectSong(0)
        assertFalse(vm.navigatePreviousSong(), "already at the first song")

        vm.selectSong(vm.filteredSongItems.value.lastIndex)
        assertFalse(vm.navigateNextSong(), "already at the last song")
    }

    @Test
    fun `selecting a song by its details finds it`() {
        val vm = vmWith(*defaultCatalog)
        assertTrue(vm.selectSongByDetails(songNumber = 5, title = "Blessed Be Your Name", songbook = "Worship"))
        assertEquals("Blessed Be Your Name", vm.filteredSongItems.value[vm.selectedSongIndex.value].title)
    }

    @Test
    fun `selecting a song that is not present reports failure`() {
        val vm = vmWith(*defaultCatalog)
        assertFalse(vm.selectSongByDetails(songNumber = 999, title = "Nonexistent", songbook = "Hymnal"))
    }

    // ── Favourites ──────────────────────────────────────────────────────────────

    @Test
    fun `favourites toggle on and off`() {
        val vm = vmWith(*defaultCatalog)
        val songId = vm.filteredSongItems.value.first().songId

        vm.toggleFavorite(songId)
        assertTrue(songId in vm.favorites.value)
        vm.toggleFavorite(songId)
        assertFalse(songId in vm.favorites.value)
    }

    @Test
    fun `favourite songs can be listed back`() {
        val vm = vmWith(*defaultCatalog)
        val first = vm.filteredSongItems.value[0]
        val third = vm.filteredSongItems.value[2]
        vm.toggleFavorite(first.songId)
        vm.toggleFavorite(third.songId)

        val favourites = vm.getFavoriteSongs().map { it.title }
        assertEquals(2, favourites.size)
        assertTrue(first.title in favourites && third.title in favourites)
    }

    @Test
    fun `clearing favourites empties the set`() {
        val vm = vmWith(*defaultCatalog)
        vm.toggleFavorite(vm.filteredSongItems.value.first().songId)
        vm.clearFavorites()
        assertTrue(vm.favorites.value.isEmpty())
        assertTrue(vm.getFavoriteSongs().isEmpty())
    }

    @Test
    fun `a song id distinguishes the same number in different songbooks`() {
        // Both catalogs have a number 1; without the songbook in the id, favouriting one
        // would favourite the other.
        val vm = vmWith(*defaultCatalog)
        val ids = vm.filteredSongItems.value.filter { it.number == "1" }.map { it.songId }
        assertEquals(2, ids.size)
        assertEquals(2, ids.toSet().size, "song ids collided: $ids")
    }
}
