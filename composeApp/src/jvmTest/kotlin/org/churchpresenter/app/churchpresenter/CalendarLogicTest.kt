package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.server.CalendarEnrollDecision
import org.churchpresenter.app.churchpresenter.server.RemoteAccess
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CalendarLogicTest {

    private val announcement = ScheduleItem.AnnouncementItem(id = "a", text = "Welcome")
    private val timer = ScheduleItem.AnnouncementItem(id = "t", text = "", isTimer = true)
    private val media = ScheduleItem.MediaItem(
        id = "m", mediaUrl = "/clip.mp4", mediaTitle = "Clip", mediaType = "local",
    )
    private val pictures = ScheduleItem.PictureItem(
        id = "p", folderPath = "/pics", folderName = "pics", imageCount = 12,
    )
    private val deck = ScheduleItem.PresentationItem(
        id = "d", filePath = "/deck.pptx", fileName = "deck", slideCount = 6, fileType = "pptx",
    )

    @Test
    fun `a repeating cue carries its count onto a text announcement`() {
        assertEquals(3, (calendarShownItem(announcement, plays = 3) as ScheduleItem.AnnouncementItem).loopCount)
        assertEquals(0, (calendarShownItem(announcement, plays = 0) as ScheduleItem.AnnouncementItem).loopCount)
    }

    @Test
    fun `a single play, a timer and any other row go out unchanged`() {
        assertSame(announcement, calendarShownItem(announcement, plays = 1))
        assertSame(timer, calendarShownItem(timer, plays = 3))
        assertSame(media, calendarShownItem(media, plays = 3))
    }

    @Test
    fun `media counts the repeats after the first play, and zero is forever`() {
        assertEquals(0, calendarMediaLoopCount(0))
        assertEquals(0, calendarMediaLoopCount(1))
        assertEquals(2, calendarMediaLoopCount(3))
    }

    @Test
    fun `a service sets the start when it replaces the schedule or lands in an empty one`() {
        assertTrue(calendarSetsServiceStart(replace = true, wasEmpty = false))
        assertTrue(calendarSetsServiceStart(replace = false, wasEmpty = true))
        assertFalse(calendarSetsServiceStart(replace = false, wasEmpty = false))
    }

    @Test
    fun `a clip runs its own length, read through the lookup it is given`() {
        assertEquals(95, knownRunSeconds(media, 5f, 5f) { if (it == "/clip.mp4") 95 else null })
        assertNull(knownRunSeconds(media, 5f, 5f) { null })
    }

    @Test
    fun `a slideshow runs its count times its own interval`() {
        assertEquals(60, knownRunSeconds(pictures, 5f, 9f) { null }, "twelve pictures at five seconds")
        assertEquals(54, knownRunSeconds(deck, 5f, 9f) { null }, "six slides at nine seconds")
    }

    @Test
    fun `any other row has no known length`() {
        assertNull(knownRunSeconds(announcement, 5f, 5f) { 10 })
    }

    @Test
    fun `the run-of-show font has a bold and a regular face`() {
        assertEquals("OpenSans-Bold", calendarPdfFontName(bold = true))
        assertEquals("OpenSans-Regular", calendarPdfFontName(bold = false))
    }

    @Test
    fun `a phone enrolling is refused without asking when sync is off or it is blocked`() {
        assertEquals(
            CalendarEnrollDecision.SyncOff,
            calendarEnrollGate(syncEnabled = false, RemoteAccess.AUTO_APPROVE),
        )
        assertEquals(CalendarEnrollDecision.Denied, calendarEnrollGate(syncEnabled = true, RemoteAccess.AUTO_REJECT))
    }

    @Test
    fun `any other phone enrolling is put to the operator`() {
        assertNull(calendarEnrollGate(syncEnabled = true, RemoteAccess.PROMPT))
        assertNull(calendarEnrollGate(syncEnabled = true, RemoteAccess.AUTO_APPROVE))
    }
}
