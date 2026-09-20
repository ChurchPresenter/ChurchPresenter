package org.churchpresenter.app.churchpresenter.models

import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PresetItemsTest {

    @Test
    fun `carries the tab's text and styling`() {
        val item = announcementPresetItem(
            AnnouncementsSettings(text = "Welcome", textColor = "#FF0000", fontSize = 48, bold = true),
        )

        assertEquals("Welcome", item.text)
        assertEquals("#FF0000", item.textColor)
        assertEquals(48, item.fontSize)
        assertTrue(item.bold)
    }

    @Test
    fun `every preset gets its own id`() {
        val settings = AnnouncementsSettings(text = "Welcome")
        assertNotEquals(announcementPresetItem(settings).id, announcementPresetItem(settings).id)
    }

    @Test
    fun `a zero duration is plain text, not a timer`() {
        assertFalse(announcementPresetItem(AnnouncementsSettings(text = "Welcome")).isTimer)
    }

    @Test
    fun `any part of a duration makes it a timer`() {
        assertTrue(announcementPresetItem(AnnouncementsSettings(timerHours = 1)).isTimer)
        assertTrue(announcementPresetItem(AnnouncementsSettings(timerMinutes = 5)).isTimer)
        assertTrue(announcementPresetItem(AnnouncementsSettings(timerSeconds = 30)).isTimer)
    }

    @Test
    fun `a clock mode is a timer even with no duration set`() {
        val item = announcementPresetItem(AnnouncementsSettings(timerMode = Constants.TIMER_MODE_CLOCK))

        assertTrue(item.isTimer)
        assertEquals(Constants.TIMER_MODE_CLOCK, item.timerMode)
    }
}
