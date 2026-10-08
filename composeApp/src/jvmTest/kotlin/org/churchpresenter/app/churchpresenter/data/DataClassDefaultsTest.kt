package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.bible.BibleBook
import org.churchpresenter.bible.BibleSearch
import org.churchpresenter.core.models.songs.CachedSong
import org.churchpresenter.core.models.songs.SongCache
import org.churchpresenter.core.models.songs.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.crosswordtab.data.CrosswordCell

/**
 * Default values for the small `data class`es in this package that every call site so far has
 * constructed with every field spelled out explicitly. The defaults are not decorative: they are
 * what a field decodes to when an older persisted file (a `.spb` cache, a song cache) is missing a
 * field a newer build added. The statistics types' own are `:statistics`'
 * `StatisticsDataClassDefaultsTest`.
 */
class DataClassDefaultsTest {

    @Test
    fun `a bible book with only its name given still has blank ids and a zero chapter count`() {
        val book = BibleBook(book = "Genesis")

        assertEquals(BibleBook("Genesis", "", 0, ""), book)
        assertEquals("", book.abbreviation)
    }

    @Test
    fun `a search result with only its book given still has blank chapter, verse and text`() {
        val result = BibleSearch(book = "Genesis")

        assertEquals(BibleSearch("Genesis", "", "", ""), result)
    }

    @Test
    fun `a cached song with no modification time given defaults to the epoch`() {
        val song = SongItem(number = "1", title = "Amazing Grace", songbook = "Hymns")

        assertEquals(0L, CachedSong(song).lastModified)
    }

    @Test
    fun `a song cache with only its storage directory given still has empty song lists`() {
        val cache = SongCache(storageDirectory = "/songs")

        assertEquals(SongCache("/songs", emptyList(), emptyList()), cache)
    }

    @Test
    fun `a crossword cell with only its answer given has no clue number`() {
        val cell = CrosswordCell(answer = 'A')

        assertEquals(null, cell.clueNumber)
    }
}
