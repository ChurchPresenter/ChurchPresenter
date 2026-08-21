package org.churchpresenter.core.models.songs

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every column the grid can be ordered by.
 *
 * One column per branch of a nine-way `when`, and a column wired to the wrong field looks perfectly
 * ordered on screen — just ordered by something else.
 */
class SongGridSortTest {

    private val songs = listOf(
        SongItem(
            number = "10", title = "Bravo", songbook = "Kids", secondaryTitle = "Zulu",
            author = "Bailey", composer = "Bishop", tune = "BETA", ccliNumber = "200",
        ),
        SongItem(
            number = "2", title = "Alpha", songbook = "Hymnal", secondaryTitle = "Yankee",
            author = "Adams", composer = "Archer", tune = "ALPHA", ccliNumber = "100",
        ),
        SongItem(
            number = "", title = "Charlie", songbook = "Hymnal", secondaryTitle = "Xray",
            author = "Clark", composer = "Chase", tune = "GAMMA", ccliNumber = "300",
        ),
    )

    private fun titlesSortedBy(column: SortColumn, ascending: Boolean = true) =
        SongGrid.rows(songs, GridView(sortBy = column, ascending = ascending)).map { it.title }

    @Test
    fun `numbers sort as numbers, not as text`() {
        // The whole reason this is not a string sort: song 10 must come after song 2.
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), titlesSortedBy(SortColumn.NUMBER))
    }

    @Test
    fun `a song with no number sorts last rather than first`() {
        assertEquals("Charlie", titlesSortedBy(SortColumn.NUMBER).last())
    }

    @Test
    fun `each text column orders by its own field`() {
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), titlesSortedBy(SortColumn.TITLE))
        assertEquals(listOf("Charlie", "Alpha", "Bravo"), titlesSortedBy(SortColumn.SECONDARY_TITLE))
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), titlesSortedBy(SortColumn.AUTHOR))
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), titlesSortedBy(SortColumn.COMPOSER))
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), titlesSortedBy(SortColumn.TUNE))
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), titlesSortedBy(SortColumn.CCLI))
    }

    @Test
    fun `within a songbook the number is the order the book itself is in`() {
        assertEquals(listOf("Alpha", "Charlie", "Bravo"), titlesSortedBy(SortColumn.SONGBOOK))
    }

    @Test
    fun `descending is the same order turned around`() {
        assertEquals(titlesSortedBy(SortColumn.TITLE).reversed(), titlesSortedBy(SortColumn.TITLE, ascending = false))
    }
}
