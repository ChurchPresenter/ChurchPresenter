package org.churchpresenter.schedule

import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals

/** [scheduleRowKeys]: a list key per row that is unique even when two rows share an id. */
class ScheduleRowKeysTest {

    @Test
    fun `rows with their own ids are keyed by them`() {
        val rows = listOf(ScheduleItem.WebsiteItem(id = "a", url = "u"), ScheduleItem.WebsiteItem(id = "b", url = "u"))
        assertEquals(listOf("a", "b"), scheduleRowKeys(rows))
    }

    @Test
    fun `a repeated id keeps its first row's key and gives the later one its position`() {
        val rows = listOf(
            ScheduleItem.WebsiteItem(id = "a", url = "u"),
            ScheduleItem.WebsiteItem(id = "b", url = "u"),
            ScheduleItem.WebsiteItem(id = "a", url = "u"),
        )
        assertEquals(listOf("a", "b", "a#2"), scheduleRowKeys(rows))
    }
}
