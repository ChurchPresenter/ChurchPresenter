package org.churchpresenter.hosanna.composables.model

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListModelTest {

    @Test
    fun `list keys read the arrows and page keys`() {
        assertEquals(ListKey.Up, listKey(Key.DirectionUp))
        assertEquals(ListKey.Down, listKey(Key.DirectionDown))
        assertEquals(ListKey.Home, listKey(Key.MoveHome))
        assertEquals(ListKey.End, listKey(Key.MoveEnd))
        assertEquals(ListKey.PageUp, listKey(Key.PageUp))
        assertEquals(ListKey.PageDown, listKey(Key.PageDown))
        assertNull(listKey(Key.A))
    }

    @Test
    fun `moves stay inside the list`() {
        assertEquals(3, moveIndex(2, ListKey.Down, 5, 10))
        assertEquals(4, moveIndex(4, ListKey.Down, 5, 10))
        assertEquals(0, moveIndex(0, ListKey.Up, 5, 10))
        assertEquals(4, moveIndex(-1, ListKey.Up, 5, 10))
        assertEquals(0, moveIndex(-1, ListKey.Down, 5, 10))
        assertEquals(0, moveIndex(3, ListKey.Home, 5, 10))
        assertEquals(4, moveIndex(0, ListKey.End, 5, 10))
        assertEquals(12, moveIndex(2, ListKey.PageDown, 30, 10))
        assertEquals(0, moveIndex(2, ListKey.PageUp, 30, 10))
        assertEquals(-1, moveIndex(0, ListKey.Down, 0, 10))
    }

    @Test
    fun `typing jumps to the next match after the current row`() {
        val books = listOf("Genesis", "Exodus", "Esther", "Ezra")
        assertEquals(1, jumpTarget(books, "e", 0))
        assertEquals(2, jumpTarget(books, "e", 2))
        assertEquals(3, jumpTarget(books, "ez", 0))
        assertNull(jumpTarget(books, "x", 0))
        assertNull(jumpTarget(books, " ", 0))
    }
}
