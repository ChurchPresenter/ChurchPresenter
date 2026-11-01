package org.churchpresenter.songs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import org.churchpresenter.settings.utils.Constants

/**
 * The song-list table's column configuration — availability, saved-order reconciliation, drag
 * reorder, sort keys and drag-target math — extracted from SongsTab. A wrong result here drops a
 * column an operator configured, forgets their saved order, or reorders the wrong column.
 */
class SongColumnsTest {

    // ── availableSongColumns ──────────────────────────────────────────────────

    @Test
    fun `a single songbook and no add-action omits both optional columns`() {
        val cols = availableSongColumns(songbookCount = 1, hasAddToSchedule = false)
        assertFalse("songbook" in cols, "one songbook needs no songbook column")
        assertFalse("add_to_schedule" in cols, "no callback means no add column")
        assertEquals(listOf("number", "title", "tune", "play_count", "author", "composer", "order", "favorites"), cols)
    }

    @Test
    fun `multiple songbooks and an add-action include both optional columns in place`() {
        val cols = availableSongColumns(songbookCount = 3, hasAddToSchedule = true)
        assertEquals(
            listOf(
                "number",
                "title",
                "songbook",
                "tune",
                "play_count",
                "author",
                "composer",
                "order",
                "add_to_schedule",
                "favorites",
            ),
            cols,
        )
    }

    // ── mergeColumnOrder ──────────────────────────────────────────────────────

    private val available = listOf("number", "title", "tune", "favorites")

    @Test
    fun `an empty saved order falls back to the available columns`() =
        assertSame(available, mergeColumnOrder(emptyList(), available))

    @Test
    fun `a saved order is honoured and a new column lands after the one it follows by default`() =
        assertEquals(
            listOf("title", "tune", "favorites", "number"),
            mergeColumnOrder(listOf("title", "number"), available),
        )

    @Test
    fun `a new column that comes first by default lands first`() =
        assertEquals(
            listOf("number", "favorites", "title"),
            mergeColumnOrder(listOf("favorites", "title"), listOf("number", "title", "favorites")),
        )

    @Test
    fun `the order column joins a saved layout after composer and before the buttons`() {
        val saved = listOf("number", "title", "composer", "add_to_schedule", "favorites")
        val available = listOf("number", "title", "composer", "order", "add_to_schedule", "favorites")
        assertEquals(
            listOf("number", "title", "composer", "order", "add_to_schedule", "favorites"),
            mergeColumnOrder(saved, available),
        )
    }

    @Test
    fun `columns that no longer exist are dropped from the saved order`() =
        assertEquals(
            listOf("title", "number", "tune", "favorites"),
            mergeColumnOrder(listOf("title", "gone", "number", "tune", "favorites"), available),
        )

    // ── hiddenWithNewColumns ──────────────────────────────────────────────────

    @Test
    fun `the order column starts hidden while the saved layout has never listed it`() =
        assertEquals(setOf("tune", "order"), hiddenWithNewColumns(setOf("tune"), listOf("number", "title")))

    @Test
    fun `the order column keeps the operator's choice once the saved layout lists it`() =
        assertEquals(setOf("tune"), hiddenWithNewColumns(setOf("tune"), listOf("number", "order")))

    // ── moveColumn ────────────────────────────────────────────────────────────

    private val order = listOf("a", "b", "c", "d")

    @Test
    fun `a column moves to the target's position`() =
        assertEquals(listOf("b", "c", "a", "d"), moveColumn(order, "a", "c"))

    @Test
    fun `moving to an earlier target shifts the rest right`() =
        assertEquals(listOf("a", "d", "b", "c"), moveColumn(order, "d", "b"))

    @Test
    fun `moving a column onto itself is an unchanged same-reference no-op`() =
        assertSame(order, moveColumn(order, "b", "b"))

    @Test
    fun `an absent id is an unchanged same-reference no-op`() =
        assertSame(order, moveColumn(order, "z", "b"))

    // ── songColumnSortKey ─────────────────────────────────────────────────────

    @Test
    fun `each sortable column maps to its sort key`() {
        assertEquals(Constants.SORT_NUMBER, songColumnSortKey("number"))
        assertEquals(Constants.SORT_TITLE, songColumnSortKey("title"))
        assertEquals(Constants.SORT_SONGBOOK, songColumnSortKey("songbook"))
        assertEquals(Constants.SORT_TUNE, songColumnSortKey("tune"))
        assertEquals(Constants.SORT_PLAY_COUNT, songColumnSortKey("play_count"))
        assertEquals(Constants.SORT_FAVORITES, songColumnSortKey("favorites"))
        assertEquals(Constants.SORT_AUTHOR, songColumnSortKey("author"))
        assertEquals(Constants.SORT_COMPOSER, songColumnSortKey("composer"))
    }

    @Test
    fun `a non-sortable column has no sort key`() {
        assertEquals("", songColumnSortKey("add_to_schedule"))
        assertEquals("", songColumnSortKey("anything"))
    }

    // ── draggedColumnIndex ────────────────────────────────────────────────────

    private val visible = listOf("a", "b", "c", "d")

    // Uniform 100px columns + 6px handle → 106px per step, half-step threshold 53px.
    private val width: (String) -> Float = { 100f }

    @Test
    fun `no drag keeps the column where it is`() =
        assertEquals(1, draggedColumnIndex("b", accumulatedDragPx = 0f, visible, width, handleWidthPx = 6f))

    @Test
    fun `dragging past one half-step moves one slot right`() =
        assertEquals(2, draggedColumnIndex("b", accumulatedDragPx = 60f, visible, width, handleWidthPx = 6f))

    @Test
    fun `a large rightward drag clamps at the last column`() =
        assertEquals(3, draggedColumnIndex("b", accumulatedDragPx = 500f, visible, width, handleWidthPx = 6f))

    @Test
    fun `dragging left past one half-step moves one slot left`() =
        assertEquals(0, draggedColumnIndex("b", accumulatedDragPx = -60f, visible, width, handleWidthPx = 6f))

    @Test
    fun `a drag under the half-step threshold does not move`() =
        assertEquals(1, draggedColumnIndex("b", accumulatedDragPx = 40f, visible, width, handleWidthPx = 6f))

    @Test
    fun `an absent dragged id yields index zero`() =
        assertEquals(0, draggedColumnIndex("z", accumulatedDragPx = 60f, visible, width, handleWidthPx = 6f))
}
