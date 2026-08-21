package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.data.settings.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.models.schedule.ScheduleItem
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals

class MainDesktopAnnouncementSettingsTest {

    private val item = ScheduleItem.AnnouncementItem(
        id = "ann-1",
        text = "Potluck after the service",
        textColor = "#112233",
        backgroundColor = "#445566",
        fontSize = 61,
        fontType = "Georgia",
        bold = true,
        italic = true,
        underline = true,
        shadow = true,
        shadowColor = "#778899",
        shadowSize = 91,
        shadowOpacity = 92,
        horizontalAlignment = "left",
        position = "top",
        animationType = "FADE",
        animationDuration = 731,
        loopCount = 4,
        isTimer = true,
        timerHours = 1,
        timerMinutes = 2,
        timerSeconds = 3,
        timerTextColor = "#AABBCC",
        timerExpiredText = "We are starting",
        timerMode = "clock",
        targetHour = 18,
        targetMinute = 45,
        targetSecond = 30,
        liveClockFormat = "hh:mm a",
    )

    @Test
    fun `every announcement field is carried onto the settings`() {
        val after = withAnnouncementFrom(AppSettings(), item).announcementsSettings

        assertEquals("Potluck after the service", after.text)
        assertEquals("#112233", after.textColor)
        assertEquals("#445566", after.backgroundColor)
        assertEquals(61, after.fontSize)
        assertEquals("Georgia", after.fontType)
        assertEquals(listOf(true, true, true, true), listOf(after.bold, after.italic, after.underline, after.shadow))
        assertEquals("#778899", after.shadowColor)
        assertEquals(91, after.shadowSize)
        assertEquals(92, after.shadowOpacity)
        assertEquals("left", after.horizontalAlignment)
        assertEquals("top", after.position)
        assertEquals("FADE", after.animationType)
        assertEquals(731, after.animationDuration)
        assertEquals(4, after.loopCount)
        assertEquals("#AABBCC", after.timerTextColor)
        assertEquals("We are starting", after.timerExpiredText)
        assertEquals("clock", after.timerMode)
        assertEquals("hh:mm a", after.liveClockFormat)
    }

    @Test
    fun `the countdown's hours, minutes and seconds do not get crossed`() {
        val after = withAnnouncementFrom(AppSettings(), item).announcementsSettings

        assertEquals(listOf(1, 2, 3), listOf(after.timerHours, after.timerMinutes, after.timerSeconds))
        assertEquals(listOf(18, 45, 30), listOf(after.targetHour, after.targetMinute, after.targetSecond))
    }

    @Test
    fun `no announcement setting is left behind at its previous value`() {
        val stale = AnnouncementsSettings(
            text = "stale", textColor = "#000001", backgroundColor = "#000002",
            fontSize = 7, fontType = "Stale", bold = false, italic = false,
            underline = false, shadow = false, shadowColor = "#000003",
            shadowSize = 8, shadowOpacity = 9, horizontalAlignment = "right",
            position = "bottom", animationType = "NONE", animationDuration = 10,
            loopCount = 11, timerHours = 12, timerMinutes = 13, timerSeconds = 14,
            timerTextColor = "#000004", timerExpiredText = "stale", timerMode = "duration",
            targetHour = 15, targetMinute = 16, targetSecond = 17, liveClockFormat = "stale",
        )

        val after = withAnnouncementFrom(
            AppSettings(announcementsSettings = stale), item,
        ).announcementsSettings

        val survived = AnnouncementsSettings::class.memberProperties
            .filter { it.get(after) == it.get(stale) }
            .map { it.name }
        assertEquals(
            emptyList(), survived,
            "these settings were not taken from the scheduled announcement: $survived",
        )
    }

    @Test
    fun `applying an announcement leaves the rest of the settings alone`() {
        val before = AppSettings()
        val after = withAnnouncementFrom(before, item)

        assertEquals(before.songSettings, after.songSettings)
        assertEquals(before.bibleSettings, after.bibleSettings)
        assertEquals(before.projectionSettings, after.projectionSettings)
        assertEquals(before.windowedLayout, after.windowedLayout)
        assertEquals(before.maximizedLayout, after.maximizedLayout)
    }
}
