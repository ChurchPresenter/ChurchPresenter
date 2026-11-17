package org.churchpresenter.songs

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The song alias in the list (#1047): the Alias column sorts in natural order with the songs that have
 * none last, and the search finds a song by its alias in every filter mode -- but a query of digits
 * alone is still a song number.
 */
class SongAliasListTest {

    private val created = mutableListOf<SongsViewModel>()

    @AfterTest
    fun cleanup() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
    }

    private fun vm(): SongsViewModel {
        val catalog = listOf(
            SongItem(number = "14", title = "O for a Thousand Tongues", songbook = "CGS", alias = "Ch 10"),
            SongItem(number = "14", title = "O for a Thousand Tongues (Azmon)", songbook = "CGS", alias = "Ch 9"),
            SongItem(number = "25", title = "Abide With Me", songbook = "CGS"),
            SongItem(number = "30", title = "Holy, Holy, Holy", songbook = "CGS", alias = "A2"),
        )
        return SongsViewModel(
            AppSettings(),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        ).also {
            created.add(it)
            it.setInstanceLinkSource(active = true, catalog = catalog, fetchLyrics = null)
        }
    }

    private val SongsViewModel.aliases get() = filteredSongItems.value.map { it.alias }

    @Test
    fun `the alias column sorts naturally, songs with no alias last both ways`() {
        val vm = vm()
        vm.updateSort(Constants.SORT_ALIAS)
        assertTrue(vm.sortAscending.value)
        assertEquals(listOf("A2", "Ch 9", "Ch 10", ""), vm.aliases)

        vm.updateSort(Constants.SORT_ALIAS)
        assertEquals(listOf("Ch 10", "Ch 9", "A2", ""), vm.aliases)
    }

    @Test
    fun `natural order compares digit runs as numbers, ignoring case and leading zeros`() {
        assertTrue(naturalCompare("Ch 9", "Ch 10") < 0)
        assertTrue(naturalCompare("ch 010", "CH 10") == 0)
        assertTrue(naturalCompare("B1", "a2") > 0)
    }

    @Test
    fun `contains finds a song by part of its alias`() {
        val vm = vm()
        vm.updateFilterType(Constants.CONTAINS)
        vm.updateSearchQuery("ch 1")
        assertEquals(listOf("Ch 10"), vm.aliases)
    }

    @Test
    fun `starts-with and exact match find a song by its alias`() {
        val vm = vm()
        vm.updateFilterType(Constants.STARTS_WITH)
        vm.updateSearchQuery("Ch")
        assertEquals(listOf("Ch 10", "Ch 9"), vm.aliases)

        vm.updateFilterType(Constants.EXACT_MATCH)
        vm.updateSearchQuery(" ch 9 ")
        assertEquals(listOf("Ch 9"), vm.aliases)
    }

    @Test
    fun `a query of digits alone still matches only the song number`() {
        val vm = vm()
        vm.updateFilterType(Constants.CONTAINS)
        vm.updateSearchQuery("2")
        assertEquals(listOf(""), vm.aliases, "alias A2 must not match, number 25 does")
    }
}
