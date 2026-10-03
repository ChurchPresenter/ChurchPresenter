package org.churchpresenter.server

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScheduleItemWireFormTest {

    @Test
    fun `a run-of-show cue goes out as its wall-clock time`() {
        val dto = ScheduleItem.CueItem(id = "c1", action = "start", label = "Doors", absoluteTime = "09:45").toDto()

        assertEquals("cue", dto.type)
        assertEquals("Doors", dto.displayText)
        assertEquals("09:45", dto.text)
    }

    @Test
    fun `a ministry row goes out with its detail`() {
        val dto = ScheduleItem.MinistryItem(id = "m1", title = "Offering", detail = "Ushers forward").toDto()

        assertEquals("ministry", dto.type)
        assertEquals("Offering", dto.displayText)
        assertEquals("Ushers forward", dto.text)
    }

    @Test
    fun `an announcement sent with only its text gets the desktop's defaults`() {
        val item = assertIs<ScheduleItem.AnnouncementItem>(RemoteItemDto(announcementText = "").toScheduleItem())

        assertEquals("#FFFFFF", item.textColor)
        assertEquals("#000000", item.backgroundColor)
        assertEquals(48, item.fontSize)
        assertEquals("SLIDE_FROM_BOTTOM", item.animationType)
        assertEquals(500, item.animationDuration)
        assertEquals(Constants.TIMER_MODE_DURATION, item.timerMode)
        assertEquals("HH:mm:ss", item.liveClockFormat)
    }

    @Test
    fun `an announcement keeps the look the phone asked for, and its timer takes the text color`() {
        val dto = RemoteItemDto(
            announcementText = "Welcome",
            textColor = "#FFEE00",
            backgroundColor = "#112233",
            fontSize = 72,
            animationType = "FADE",
            animationDuration = 900,
        )

        val item = assertIs<ScheduleItem.AnnouncementItem>(dto.toScheduleItem())

        assertEquals("#112233", item.backgroundColor)
        assertEquals(72, item.fontSize)
        assertEquals("FADE", item.animationType)
        assertEquals(900, item.animationDuration)
        assertEquals("#FFEE00", item.timerTextColor)
    }
}
