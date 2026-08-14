package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

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
        assertEquals(listOf("number", "title", "tune", "play_count", "author", "composer", "favorites"), cols)
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
                "add_to_schedule",
                "favorites"
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
    fun `a saved order is honoured and newly-available columns are appended`() =
        assertEquals(
            listOf("title", "number", "tune", "favorites"),
            mergeColumnOrder(listOf("title", "number"), available),
        )

    @Test
    fun `columns that no longer exist are dropped from the saved order`() =
        assertEquals(
            listOf("title", "number", "tune", "favorites"),
            mergeColumnOrder(listOf("title", "gone", "number"), available),
        )

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
