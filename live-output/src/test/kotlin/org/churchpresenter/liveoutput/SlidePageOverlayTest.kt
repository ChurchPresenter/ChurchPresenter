package org.churchpresenter.liveoutput

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The slide on air moving on to its next page -- a song's section or line, new verses -- takes the
 * overlays over it down, however the page was sent (#991: a hymn number put up as an announcement
 * stayed over the hymn's lyrics). And an announcement that comes down lets go of its words.
 */
class SlidePageOverlayTest {

    private val verse1 = LyricSection(title = "Amazing Grace", lines = listOf("Amazing grace, how sweet"))
    private val verse2 = LyricSection(title = "Amazing Grace", lines = listOf("'Twas grace that taught"))

    private fun verses(n: Int) = listOf(SelectedVerse(bookName = "John", chapter = 3, verseNumber = n))

    /** A song live on [section], with an announcement put up over it. */
    private fun songWithAnnouncement(section: LyricSection = verse1) =
        PresenterManager(showPresenterWindowInitially = false).apply {
            setSongDisplaySectionIndex(0)
            setSongDisplayLineIndex(0)
            setLyricSection(section)
            setPresentingMode(Presenting.LYRICS)
            setAnnouncementText("427")
            setPresentingMode(Presenting.ANNOUNCEMENTS)
        }

    @Test
    fun `the next section of the song on air takes the announcement down`() {
        val pm = songWithAnnouncement()
        pm.setSongDisplaySectionIndex(1)
        pm.setLyricSection(verse2)
        assertTrue(pm.overlays.value.isEmpty())
        assertEquals(Presenting.LYRICS, pm.slideContent.value)
        assertEquals(Presenting.LYRICS, pm.lastLive.value)
    }

    @Test
    fun `the next line of the section on air takes it down`() {
        val pm = songWithAnnouncement()
        pm.setSongDisplayLineIndex(1)
        pm.setLyricSection(verse1)
        assertTrue(pm.overlays.value.isEmpty())
    }

    @Test
    fun `the same page sent again leaves it up`() {
        val pm = songWithAnnouncement()
        pm.setLyricSection(verse1)
        assertEquals(setOf(Presenting.ANNOUNCEMENTS), pm.overlays.value)
    }

    @Test
    fun `a page restated with a new tempo leaves it up`() {
        val pm = songWithAnnouncement()
        pm.setLyricSection(verse1.copy(bpm = 92, capo = 2))
        assertEquals(setOf(Presenting.ANNOUNCEMENTS), pm.overlays.value)
    }

    @Test
    fun `an announcement put up over a page stays until the next page`() {
        val pm = songWithAnnouncement()
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        assertEquals(setOf(Presenting.ANNOUNCEMENTS, Presenting.LOWER_THIRD), pm.overlays.value)
        pm.setSongDisplaySectionIndex(1)
        pm.setLyricSection(verse2)
        assertTrue(pm.overlays.value.isEmpty(), "every overlay comes down, as when slide content goes live")
    }

    @Test
    fun `a song page while other content is the slide changes nothing on air`() {
        val pm = PresenterManager(showPresenterWindowInitially = false)
        pm.setPresentingMode(Presenting.PICTURES)
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        pm.setSongDisplaySectionIndex(3)
        pm.setLyricSection(verse2)
        assertEquals(setOf(Presenting.ANNOUNCEMENTS), pm.overlays.value)
    }

    @Test
    fun `new verses on air take the announcement down`() {
        val pm = PresenterManager(showPresenterWindowInitially = false)
        pm.setSelectedVerses(verses(16))
        pm.setPresentingMode(Presenting.BIBLE)
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        pm.setSelectedVerses(verses(17))
        assertTrue(pm.overlays.value.isEmpty())
    }

    @Test
    fun `verses chosen under a hold leave it up`() {
        val pm = PresenterManager(showPresenterWindowInitially = false)
        pm.setSelectedVerses(verses(16))
        pm.setPresentingMode(Presenting.BIBLE)
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        pm.setBibleHold(true)
        pm.setSelectedVerses(verses(17))
        assertEquals(setOf(Presenting.ANNOUNCEMENTS), pm.overlays.value)
    }

    @Test
    fun `the same verses sent again leave it up`() {
        val pm = PresenterManager(showPresenterWindowInitially = false)
        pm.setSelectedVerses(verses(16))
        pm.setPresentingMode(Presenting.BIBLE)
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        pm.setSelectedVerses(verses(16))
        assertEquals(setOf(Presenting.ANNOUNCEMENTS), pm.overlays.value)
    }

    @Test
    fun `an announcement taken down by the next page lets go of its words`() {
        val pm = songWithAnnouncement()
        pm.setDisplayedAnnouncementText("427")
        pm.setSongDisplaySectionIndex(1)
        pm.setLyricSection(verse2)
        assertEquals("", pm.announcementText.value)
        assertEquals("", pm.displayedAnnouncementText.value)
    }

    @Test
    fun `clearing lets go of the announcement's words`() {
        val pm = songWithAnnouncement()
        pm.setDisplayedAnnouncementText("427")
        pm.setPresentingMode(Presenting.NONE)
        assertEquals("", pm.announcementText.value)
        assertEquals("", pm.displayedAnnouncementText.value)
    }

    @Test
    fun `taking only the announcement down lets go of its words`() {
        val pm = songWithAnnouncement()
        pm.clearOverlay(Presenting.ANNOUNCEMENTS)
        assertEquals("", pm.announcementText.value)
    }

    @Test
    fun `taking another overlay down keeps the announcement's words`() {
        val pm = songWithAnnouncement()
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        pm.clearOverlay(Presenting.LOWER_THIRD)
        assertEquals("427", pm.announcementText.value)
    }
}
