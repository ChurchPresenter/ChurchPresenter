package org.churchpresenter.app.churchpresenter.models.songs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the search box, the songbook filter and the column headers do to the list of songs.
 *
 * The rows are the only way into a library of thousands, so a search that drops a song hides it
 * completely — which is why the negative cases below matter as much as the positive ones.
 */
class SongGridTest {

    private fun song(
        title: String,
        number: String = "",
        songbook: String = "",
        author: String = "",
        composer: String = "",
        tune: String = "",
        ccli: String = "",
        secondaryTitle: String = "",
    ) = SongItem(
        sourceFile = "/library/$songbook/$title.song",
        number = number,
        title = title,
        secondaryTitle = secondaryTitle,
        songbook = songbook,
        author = author,
        composer = composer,
        tune = tune,
        ccliNumber = ccli,
    )

    private val library = listOf(
        song("Amazing Grace", number = "1", songbook = "Hymns", author = "John Newton", ccli = "22025"),
        song("Be Thou My Vision", number = "10", songbook = "Hymns", author = "Dallan Forgaill"),
        song("Rise Up", number = "2", songbook = "Kids/AM", composer = "Chris Tomlin"),
        song("Loose Song", songbook = "", tune = "ST ANNE"),
    )

    private fun titles(view: GridView) = SongGrid.rows(library, view).map { it.title }

    // ── Searching ─────────────────────────────────────────────────────────────

    @Test
    fun `an empty search shows everything`() {
        assertEquals(library.size, titles(GridView()).size)
    }

    @Test
    fun `a search matches the title, the author, the number and the rest`() {
        assertEquals(listOf("Amazing Grace"), titles(GridView(query = "amazing")))
        assertEquals(listOf("Amazing Grace"), titles(GridView(query = "newton")))
        assertEquals(listOf("Amazing Grace"), titles(GridView(query = "22025")))
        assertEquals(listOf("Rise Up"), titles(GridView(query = "tomlin")))
        assertEquals(listOf("Loose Song"), titles(GridView(query = "st anne")))
    }

    @Test
    fun `each word may match a different field`() {
        // How a person who half-remembers a song looks for it.
        assertEquals(listOf("Amazing Grace"), titles(GridView(query = "newton grace")))
    }

    @Test
    fun `a word that matches nothing leaves no rows`() {
        assertTrue(titles(GridView(query = "grace mozart")).isEmpty())
    }

    @Test
    fun `search ignores case and surrounding space`() {
        assertEquals(listOf("Amazing Grace"), titles(GridView(query = "  AMAZING  ")))
    }

    // ── The songbook filter ───────────────────────────────────────────────────

    @Test
    fun `a songbook shows its own songs and the ones filed under it`() {
        assertEquals(listOf("Amazing Grace", "Be Thou My Vision"), titles(GridView(songbook = "Hymns")))
        assertEquals(listOf("Rise Up"), titles(GridView(songbook = "Kids")))
        assertEquals(listOf("Rise Up"), titles(GridView(songbook = "Kids/AM")))
    }

    @Test
    fun `the songs filed in no songbook can be listed on their own`() {
        assertEquals(listOf("Loose Song"), titles(GridView(songbook = "")))
    }

    @Test
    fun `the filter and the search narrow together`() {
        assertTrue(titles(GridView(query = "amazing", songbook = "Kids")).isEmpty())
    }

    @Test
    fun `a view with neither a search nor a filter is not filtered`() {
        assertTrue(GridView().isFiltered.not())
        assertTrue(GridView(query = "x").isFiltered)
        assertTrue(GridView(songbook = "").isFiltered)
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    @Test
    fun `numbers sort as numbers, not as text`() {
        val sorted = titles(GridView(sortBy = SortColumn.NUMBER))
        assertEquals(listOf("Amazing Grace", "Rise Up", "Be Thou My Vision", "Loose Song"), sorted)
    }

    @Test
    fun `a song with no number sorts after every song that has one`() {
        assertEquals("Loose Song", titles(GridView(sortBy = SortColumn.NUMBER)).last())
    }

    @Test
    fun `within a songbook the number is the order`() {
        val sorted = titles(GridView(sortBy = SortColumn.SONGBOOK))
        assertEquals(listOf("Loose Song", "Amazing Grace", "Be Thou My Vision", "Rise Up"), sorted)
    }

    @Test
    fun `every other column sorts by its own text, ignoring case`() {
        assertEquals("Amazing Grace", titles(GridView(sortBy = SortColumn.TITLE)).first())
        assertEquals("Be Thou My Vision", titles(GridView(sortBy = SortColumn.AUTHOR)).first())
        assertEquals("Loose Song", titles(GridView(sortBy = SortColumn.TUNE)).first())
    }

    @Test
    fun `the songs with nothing in the sorted column come last`() {
        // Sorting by composer to see who wrote what should start with the ones that say.
        val byComposer = titles(GridView(sortBy = SortColumn.COMPOSER))
        assertEquals("Rise Up", byComposer.first())
        assertEquals(3, byComposer.drop(1).size, "and the rest, which name nobody, follow")
    }

    @Test
    fun `the same order reversed is what a second click on a header gives`() {
        val up = titles(GridView(sortBy = SortColumn.TITLE))
        val down = titles(GridView(sortBy = SortColumn.TITLE, ascending = false))
        assertEquals(up.reversed(), down)
    }

    // ── Counts beside the filter ──────────────────────────────────────────────

    @Test
    fun `a songbook is counted with the songs of the books under it`() {
        val counts = SongGrid.countsBySongbook(library)
        assertEquals(2, counts["Hymns"])
        assertEquals(1, counts["Kids/AM"])
        assertEquals(1, counts["Kids"], "a parent counts what its children hold")
        assertEquals(1, counts[""], "and the songs in no songbook are counted too")
    }
}
