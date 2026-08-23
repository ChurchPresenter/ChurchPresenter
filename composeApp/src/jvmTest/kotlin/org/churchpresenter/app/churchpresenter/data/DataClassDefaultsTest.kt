package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.bible.BibleBook
import org.churchpresenter.bible.BibleSearch
import org.churchpresenter.core.models.songs.CachedSong
import org.churchpresenter.core.models.songs.SongCache
import org.churchpresenter.core.models.songs.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.core.models.statistics.SongDisplayEntry
import org.churchpresenter.core.models.statistics.SongPlayEvent
import org.churchpresenter.core.models.statistics.VerseDisplayEntry
import org.churchpresenter.core.models.statistics.VersePlayEvent

/**
 * Default values for the small `data class`es in this package that every call site so far has
 * constructed with every field spelled out explicitly. The defaults are not decorative: they are
 * what a field decodes to when an older persisted file (a `.spb` cache, a statistics log) is
 * missing a field a newer build added — [StatisticsFileFormatTest] pins that at the file-format
 * level for the statistics types, and this pins the same contract for the classes themselves.
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
    fun `a song display entry with only its number given still has a blank title and a zero count`() {
        val entry = SongDisplayEntry(songNumber = 12)

        assertEquals(SongDisplayEntry(12, "", "", 0), entry)
    }

    @Test
    fun `a verse display entry with only its chapter given still has a blank book and a zero count`() {
        val entry = VerseDisplayEntry(chapter = 3)

        assertEquals(VerseDisplayEntry("", "", 3, 0, 0), entry)
    }

    @Test
    fun `a song play event with only its title given still has a zero timestamp`() {
        val event = SongPlayEvent(title = "Amazing Grace")

        assertEquals(SongPlayEvent(0, "Amazing Grace", "", "", 0L), event)
    }

    @Test
    fun `a verse play event with only its book given still has a zero timestamp`() {
        val event = VersePlayEvent(bookName = "Genesis")

        assertEquals(VersePlayEvent("", "Genesis", 0, 0, 0L), event)
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
