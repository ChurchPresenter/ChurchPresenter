package org.churchpresenter.liveoutput

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PresenterOutputsTest {

    private val manager = PresenterManager(showPresenterWindowInitially = false)

    @AfterTest
    fun stopTickers() {
        manager.pauseAnnouncementTimer()
    }

    private fun verse() =
        SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved")

    // ── Media ───────────────────────────────────────────────────────────────────

    @Test
    fun `the media output reaches the manager`() {
        val media = manager.mediaOutput
        media.setShowPresenterWindow(true)
        assertTrue(media.showPresenterWindow.value)
        assertTrue(manager.showPresenterWindow.value)

        media.setCurrentMedia("/clips/intro.mp4", "video")
        assertEquals("/clips/intro.mp4", manager.currentMediaUrl.value)
        assertEquals("video", manager.currentMediaType.value)

        media.setPresentingMode(Presenting.MEDIA)
        assertTrue(media.isLive(Presenting.MEDIA))

        media.requestClearDisplay()
        assertTrue(manager.clearDisplayRequested.value)
    }

    // ── Web ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the web output reaches the manager`() {
        val web = manager.webOutput
        val snapshot = ImageBitmap(2, 2)
        web.setWebsiteUrl("https://example.org")
        web.setWebPageTitle("Example")
        web.setWebSnapshot(snapshot)
        web.setPresentingMode(Presenting.WEBSITE)

        assertEquals("https://example.org", web.websiteUrl.value)
        assertEquals("Example", web.webPageTitle.value)
        assertEquals("Example", manager.webPageTitle.value)
        assertSame(snapshot, web.webSnapshot.value)
        assertNull(web.liveBrowser.value)
        assertNull(manager.liveBrowser.value)
        assertTrue(web.isLive(Presenting.WEBSITE))
    }

    // ── Q&A ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the Q&A output reaches the manager`() {
        val qa = manager.qaOutput
        val question = Question(id = "q1", text = "Why?", timestamp = 1L)
        qa.setDisplayedQuestion(question)
        qa.setShowQRCodeOnDisplay(true)
        manager.setScreenLock(1, Presenting.QA)

        assertEquals(question, manager.displayedQuestion.value)
        assertTrue(manager.showQRCodeOnDisplay.value)
        assertEquals(mapOf(1 to Presenting.QA), qa.screenLocks.value)
        assertEquals(Presenting.NONE, qa.slideContent.value)
    }

    // ── Slides ──────────────────────────────────────────────────────────────────

    @Test
    fun `the slides output reads and writes the manager`() {
        val slides = manager.slidesOutput
        slides.setShowPresenterWindow(true)
        manager.setScreenLock(0, Presenting.PICTURES)

        assertTrue(manager.showPresenterWindow.value)
        assertEquals(mapOf(0 to Presenting.PICTURES), slides.screenLocks.value)
        assertNull(slides.presentationFrame.value)
        assertFalse(slides.isLive(Presenting.PICTURES))
    }

    // ── Announcements ───────────────────────────────────────────────────────────

    @Test
    fun `the announcements output reports the manager's state`() {
        val announcements = manager.announcementsOutput
        announcements.setScreenLock(2, Presenting.ANNOUNCEMENTS)
        announcements.setAnnouncementTickerLive(true)

        assertEquals(mapOf(2 to Presenting.ANNOUNCEMENTS), announcements.screenLocks.value)
        assertTrue(manager.announcementTickerLive.value)
        assertFalse(announcements.announcementsLive)
        assertFalse(announcements.timerRunning.value)
        assertFalse(announcements.announcementTickerActive.value)
        assertFalse(announcements.announcementTimerExpired.value)
        assertEquals(0, announcements.timerRemainingSeconds.value)
    }

    @Test
    fun `an announcement set live through the output is on air`() {
        val announcements = manager.announcementsOutput
        announcements.setAnnouncementText("Welcome")
        announcements.setPresentingMode(Presenting.ANNOUNCEMENTS)

        assertTrue(announcements.announcementsLive)
        assertEquals("Welcome", manager.announcementText.value)

        announcements.requestClearDisplay()
        assertTrue(manager.clearDisplayRequested.value)
    }

    @Test
    fun `a countdown started through the output runs on the manager`() {
        val announcements = manager.announcementsOutput
        announcements.startAnnouncementCountdown(remainingSeconds = 90, expiredText = "Time")
        assertTrue(announcements.timerRunning.value)
        assertEquals(90, announcements.timerRemainingSeconds.value)

        announcements.pauseAnnouncementTimer(45)
        assertFalse(manager.timerRunning.value)
    }

    @Test
    fun `a count-up started through the output runs on the manager`() {
        manager.announcementsOutput.startAnnouncementCountUp(initialElapsedSeconds = 30)
        assertTrue(manager.timerRunning.value)
        assertTrue(manager.announcementTickerActive.value)
        assertEquals(30, manager.timerRemainingSeconds.value)
    }

    @Test
    fun `a specific time and a clock tick without counting`() {
        val announcements = manager.announcementsOutput
        announcements.startAnnouncementSpecificTime(targetHour = 23, targetMinute = 59, targetSecond = 0)
        assertFalse(manager.timerRunning.value)
        assertTrue(manager.announcementTickerActive.value)

        announcements.startAnnouncementClockDisplay("HH:mm")
        assertFalse(manager.timerRunning.value)
        assertTrue(manager.announcementTickerActive.value)
    }

    @Test
    fun `pausing through the interface default keeps the remaining time`() {
        manager.startAnnouncementCountdown(remainingSeconds = 60, expiredText = "")
        val announcements: LiveAnnouncements = manager
        announcements.pauseAnnouncementTimer()
        assertFalse(manager.timerRunning.value)
        assertEquals(60, manager.timerRemainingSeconds.value)
    }

    // ── The manager's parts ─────────────────────────────────────────────────────

    @Test
    fun `each part of the manager holds what was set through it`() {
        val section = LyricSection(title = "Verse 1", lines = listOf("Amazing grace"))
        manager.setSelectedVerses(listOf(verse()))
        manager.setLyricSection(section)
        manager.setSelectedImagePath("/pictures/a.png")
        manager.setWebsiteUrl("https://example.org")
        manager.setScreenLock(0, Presenting.BIBLE)
        manager.setDisplayedQuestion(null)

        assertEquals(listOf(verse()), manager.bible.selectedVerses.value)
        assertEquals(section, manager.songs.lyricSection.value)
        assertEquals("/pictures/a.png", manager.pictures.selectedImagePath.value)
        assertEquals(mapOf(0 to Presenting.BIBLE), manager.locks.screenLocks.value)
        assertEquals("https://example.org", manager.web.websiteUrl.value)
        assertNull(manager.screens.displayedQuestion.value)
        assertNull(manager.slides.liveSlide.value)
        assertEquals(0, manager.lowerThird.lottieCurrentFrameIndex.value)
        assertTrue(manager.overlayLayers.overlays.value.isEmpty())
        assertNull(manager.onLiveStateChanged)
    }

    @Test
    fun `a song section set through the interface default is the live one`() {
        val section = LyricSection(title = "Chorus", lines = listOf("How sweet the sound"))
        val songs: LiveSongs = manager
        songs.setDisplayedLyricSection(section)
        assertEquals(section, manager.displayedLyricSection.value)
    }

    @Test
    fun `a listener hears each change with the content it belongs to`() {
        val heard = mutableListOf<Presenting>()
        manager.onLiveStateChanged = { _, source -> heard += source }
        manager.setSelectedVerses(listOf(verse()))
        manager.setWebsiteUrl("https://example.org")
        assertEquals(listOf(Presenting.BIBLE, Presenting.WEBSITE), heard)
    }

    @Test
    fun `a context on its own ignores what the manager would have handled`() {
        val context = PresenterContext()
        context.notify(Presenting.BIBLE)
        context.setPresentingMode(Presenting.LYRICS)
        context.requestClearDisplay()
        assertEquals(Presenting.NONE, context.slideMode.value)
        assertFalse(context.clearDisplayRequested.value)
    }

    @Test
    fun `the lower third keeps its pre-render rate and the ATEM size it was given`() {
        manager.setAtemRenderSettings(null)
        assertTrue(manager.lottiePrerenderFps.value > 0)
    }

    @Test
    fun `an off-screen output reports the version it was given`() {
        val context = OffscreenOutputContext(
            presenterManager = manager,
            appSettingsState = mutableStateOf(AppSettings()),
            screenAssignmentState = mutableStateOf(ScreenAssignment()),
            effectiveModeState = mutableStateOf(Presenting.NONE),
            appVersion = "9.9.9",
        )
        assertEquals("9.9.9", context.appVersion)
    }
}
