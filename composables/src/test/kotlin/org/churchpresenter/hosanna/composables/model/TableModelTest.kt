package org.churchpresenter.hosanna.composables.model

import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class TableModelTest {

    @Test
    fun `a header click sorts up, again reverses, a new column starts up`() {
        assertEquals(TableSort("title", SortDirection.Ascending), nextSort(null, "title"))
        assertEquals(
            TableSort("title", SortDirection.Descending),
            nextSort(TableSort("title", SortDirection.Ascending), "title"),
        )
        assertEquals(
            TableSort("title", SortDirection.Ascending),
            nextSort(TableSort("title", SortDirection.Descending), "title"),
        )
        assertEquals(
            TableSort("book", SortDirection.Ascending),
            nextSort(TableSort("title", SortDirection.Descending), "book"),
        )
    }

    @Test
    fun `the header checkbox is off, mixed or on`() {
        val keys = listOf("a", "b", "c")
        assertEquals(ToggleableState.Off, bulkState(emptySet(), keys))
        assertEquals(ToggleableState.Indeterminate, bulkState(setOf("b"), keys))
        assertEquals(ToggleableState.On, bulkState(setOf("a", "b", "c", "z"), keys))
    }

    @Test
    fun `columns never shrink below their minimum`() {
        assertEquals(120.dp, resizedWidth(100.dp, 20.dp, 64.dp))
        assertEquals(64.dp, resizedWidth(100.dp, (-80).dp, 64.dp))
    }
}
