package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongItem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The song editor's duplicate warning: the same number, title and song book as another song. */
class SongDuplicateIndexTest {

    private val library = SongDuplicateIndex(
        listOf(
            SongItem(number = "12", title = "Amazing Grace", songbook = "Hymns", sourceFile = "hymns/12.song"),
            SongItem(number = "3", title = "Ιησούς", songbook = "", sourceFile = "3.song"),
        )
    )

    @Test
    fun `the same number, title and song book in another file is a duplicate`() {
        assertTrue(library.hasDuplicate("12", "Amazing Grace", "Hymns", "new.song"))
    }

    @Test
    fun `title and song book match ignoring case`() {
        assertTrue(library.hasDuplicate("12", "AMAZING grace", "hymns", "new.song"))
        assertTrue(library.hasDuplicate("3", "ΙΗΣΟΎΣ", "", "new.song"))
    }

    @Test
    fun `the song being edited is not its own duplicate`() {
        assertFalse(library.hasDuplicate("12", "Amazing Grace", "Hymns", "hymns/12.song"))
    }

    @Test
    fun `another number, title or song book is not a duplicate`() {
        assertFalse(library.hasDuplicate("13", "Amazing Grace", "Hymns", "new.song"))
        assertFalse(library.hasDuplicate("12", "Amazing Grace!", "Hymns", "new.song"))
        assertFalse(library.hasDuplicate("12", "Amazing Grace", "", "new.song"))
    }

    @Test
    fun `a blank title is never a duplicate`() {
        val blank = SongDuplicateIndex(listOf(SongItem(number = "1", title = " ", sourceFile = "a.song")))

        assertFalse(blank.hasDuplicate("1", " ", "", "b.song"))
    }
}
