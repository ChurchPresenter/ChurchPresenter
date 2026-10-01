package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.LiveHistoryEntry
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What one on-screen history line says, read off a real [PresenterManager].
 *
 * The line follows the mode actually on screen, not the content type of whichever setter fired:
 * the Songs tab pushes a staged song's section while a verse is still up, and logging that would
 * record a song nobody saw.
 */
class LiveHistoryEntryTest {

    private val john316 = Triple(43, 3, 16)

    @Test
    fun `a lyric section is logged by position, with no words`() {
        val pm = PresenterManager()
        pm.setSongDisplaySectionIndex(2)
        pm.setSongDisplayLineIndex(1)
        pm.setLyricSection(
            LyricSection(title = "Amazing Grace", songNumber = 1, type = "chorus", lines = listOf("words")),
        )
        pm.setPresentingMode(Presenting.LYRICS)

        assertEquals(
            LiveHistoryEntry(
                contentType = "LYRICS",
                songNumber = 1,
                songTitle = "Amazing Grace",
                sectionIndex = 2,
                sectionType = "chorus",
                lineIndex = 1,
            ),
            liveHistoryEntryOf(pm, null),
        )
    }

    @Test
    fun `a song staged while a verse is up is not logged until it is on screen`() {
        val pm = PresenterManager()
        pm.setSelectedVerses(listOf(SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16)))
        pm.setPresentingMode(Presenting.BIBLE)
        pm.setLyricSection(LyricSection(title = "Staged", songNumber = 5, lines = listOf("words")))

        val entry = liveHistoryEntryOf(pm, john316)

        assertEquals("BIBLE", entry.contentType)
        assertNull(entry.songTitle)
    }

    @Test
    fun `a verse is logged by its canonical code and the reference shown`() {
        val pm = PresenterManager()
        pm.setSelectedVerses(
            listOf(SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseRange = "16-18")),
        )
        pm.setPresentingMode(Presenting.BIBLE)

        val entry = liveHistoryEntryOf(pm, john316)

        assertEquals("B043C003V016", entry.verseCode)
        assertEquals("John 3:16-18", entry.reference)
    }

    @Test
    fun `a single verse without a range names its own verse`() {
        val pm = PresenterManager()
        pm.setSelectedVerses(listOf(SelectedVerse(bookName = "Psalms", chapter = 23, verseNumber = 1)))
        pm.setPresentingMode(Presenting.BIBLE)

        assertEquals("Psalms 23:1", liveHistoryEntryOf(pm, null).reference)
    }

    @Test
    fun `a cleared screen and a blank mode are both logged as none`() {
        val pm = PresenterManager()
        assertEquals(LiveHistoryEntry("NONE"), liveHistoryEntryOf(pm, null))

        pm.setPresentingMode(Presenting.BIBLE)
        assertEquals(LiveHistoryEntry("NONE"), liveHistoryEntryOf(pm, null), "no verse chosen yet")

        pm.setPresentingMode(Presenting.PICTURES)
        assertEquals(LiveHistoryEntry("NONE"), liveHistoryEntryOf(pm, null), "no picture chosen yet")
    }

    @Test
    fun `a slide is logged by its deck's file name and its index`() {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.PRESENTATION)
        pm.setLiveSlide("sermon.pptx", 4)

        assertEquals(
            LiveHistoryEntry("PRESENTATION", fileName = "sermon.pptx", slideIndex = 4),
            liveHistoryEntryOf(pm, null),
        )
    }

    @Test
    fun `a slide change is reported only while presentation is the live mode`() {
        val pm = PresenterManager()
        val reported = mutableListOf<Presenting>()
        pm.onLiveStateChanged = { _, source -> reported += source }

        pm.setLiveSlide("deck.pdf", 0)
        assertEquals(emptyList(), reported, "a slide pushed ahead of the mode switch is not on screen yet")

        pm.setPresentingMode(Presenting.PRESENTATION)
        pm.setLiveSlide("deck.pdf", 1)
        assertEquals(listOf(Presenting.PRESENTATION, Presenting.PRESENTATION), reported)
    }

    @Test
    fun `leaving presentation forgets the slide`() {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.PRESENTATION)
        pm.setLiveSlide("deck.pdf", 3)

        pm.setPresentingMode(Presenting.NONE)

        assertNull(pm.liveSlide.value)
    }

    @Test
    fun `pictures and media are logged by file name alone`() {
        val pm = PresenterManager()
        pm.setSelectedImagePath("/Users/someone/Pictures/welcome.png")
        pm.setPresentingMode(Presenting.PICTURES)
        assertEquals(LiveHistoryEntry("PICTURES", fileName = "welcome.png"), liveHistoryEntryOf(pm, null))

        pm.setCurrentMedia("https://example.invalid/videos/intro.mp4?token=abc#t=3", "video")
        pm.setPresentingMode(Presenting.MEDIA)
        assertEquals(LiveHistoryEntry("MEDIA", fileName = "intro.mp4"), liveHistoryEntryOf(pm, null))

        pm.setCurrentMedia("C:\\Media\\walk-in.mov", "video")
        assertEquals("walk-in.mov", liveHistoryEntryOf(pm, null).fileName)
    }

    @Test
    fun `any other mode is logged by its name alone`() {
        val pm = PresenterManager()
        pm.setAnnouncementText("Welcome to the service")
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)

        assertEquals(LiveHistoryEntry("ANNOUNCEMENTS"), liveHistoryEntryOf(pm, null))
    }
}
