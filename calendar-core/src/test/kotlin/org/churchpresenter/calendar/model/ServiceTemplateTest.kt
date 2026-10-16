package org.churchpresenter.calendar.model

import org.churchpresenter.calendar.model.RowSamples.song
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a new service can be started from, each under the id the sheet keys it by. */
class ServiceTemplateTest {

    @Test
    fun `each starting point is keyed by what it starts from`() {
        val earlier = PlannedService(id = "svc-1", date = "2026-09-13", name = "Sunday", startTime = "10:00")
        val saved = SavedTemplate(id = "tpl-1", name = "Sunday", startTime = "10:00")

        assertEquals("blank", ServiceTemplate.Blank.id)
        assertEquals("svc-1", ServiceTemplate.CopyOf(earlier).id)
        assertEquals("tpl-1", ServiceTemplate.Saved(saved).id)
        assertEquals("schedule", ServiceTemplate.FromSchedule(listOf(song())).id)
    }

    @Test
    fun `each starting point carries what it starts from`() {
        val earlier = PlannedService(id = "svc-1", date = "2026-09-13", name = "Sunday", startTime = "10:00")
        val saved = SavedTemplate(id = "tpl-1", name = "Sunday", startTime = "10:00")

        assertEquals(earlier, ServiceTemplate.CopyOf(earlier).service)
        assertEquals(saved, ServiceTemplate.Saved(saved).template)
        assertEquals(listOf(song()), ServiceTemplate.FromSchedule(listOf(song())).items)
    }
}
