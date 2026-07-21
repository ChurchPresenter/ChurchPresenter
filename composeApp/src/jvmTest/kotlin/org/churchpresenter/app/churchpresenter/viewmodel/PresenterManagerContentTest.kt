package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The remaining content types on [PresenterManager] — announcements, websites, lower thirds and
 * the look-ahead sections — checked against the same `onLiveStateChanged` source contract as the
 * Bible/lyrics/pictures setters: a content setter reports the type it belongs to, and purely
 * visual state stays off the wire.
 */
class PresenterManagerContentTest {

    private val reported = mutableListOf<Presenting>()

    private fun manager(): PresenterManager = PresenterManager().apply {
        onLiveStateChanged = { _, source -> reported.add(source) }
    }

    // ── Announcements ───────────────────────────────────────────────────────────

    @Test
    fun `announcement text is broadcast as an announcements change`() {
        val pm = manager()
        pm.setAnnouncementText("Service starts in 5 minutes")
        assertEquals("Service starts in 5 minutes", pm.announcementText.value)
        assertEquals(listOf(Presenting.ANNOUNCEMENTS), reported)
    }

    @Test
    fun `the displayed announcement text is transition state and is not broadcast`() {
        val pm = manager()
        pm.setDisplayedAnnouncementText("mid-fade text")
        pm.setAnnouncementTransitionAlpha(0.5f)
        assertEquals("mid-fade text", pm.displayedAnnouncementText.value)
        assertTrue(reported.isEmpty(), "the fade state is local to the output window")
    }

    // ── Website ─────────────────────────────────────────────────────────────────

    @Test
    fun `the website url is broadcast as a website change`() {
        val pm = manager()
        pm.setWebsiteUrl("https://example.org/notices")
        assertEquals("https://example.org/notices", pm.websiteUrl.value)
        assertEquals(listOf(Presenting.WEBSITE), reported)
    }

    @Test
    fun `an empty url is still a website change`() {
        // Clearing the address is a real state change a follower needs to mirror.
        val pm = manager()
        pm.setWebsiteUrl("")
        assertEquals(listOf(Presenting.WEBSITE), reported)
    }

    // ── Lower third ─────────────────────────────────────────────────────────────

    @Test
    fun `loading a lower third records its preset and pause settings`() {
        val pm = manager()
        pm.setLottieContent(json = "", pauseAtFrame = true, pauseFrame = 30f, pauseDurationMs = 4_000, presetName = "Speaker Name")

        assertEquals("Speaker Name", pm.currentLowerThirdName.value)
        assertEquals(listOf(Presenting.LOWER_THIRD), reported)
    }

    @Test
    fun `loading a lower third bumps the trigger so a repeat replays it`() {
        // Re-selecting the same preset must play it again, so the trigger has to change even
        // when the content is byte-identical.
        val pm = manager()
        val start = pm.lottieTrigger.value
        pm.setLottieContent("", pauseAtFrame = false, pauseFrame = 0f, pauseDurationMs = 0, presetName = "P")
        pm.setLottieContent("", pauseAtFrame = false, pauseFrame = 0f, pauseDurationMs = 0, presetName = "P")
        assertEquals(start + 2, pm.lottieTrigger.value)
    }

    @Test
    fun `loading a lower third clears any pre-rendered frames from the previous one`() {
        val pm = manager()
        pm.setLottieContent("", pauseAtFrame = false, pauseFrame = 0f, pauseDurationMs = 0, presetName = "P")
        assertEquals(null, pm.lottieFrameCount.value, "stale frames would show the previous graphic")
        assertEquals(null, pm.lottieFrame.value)
        assertEquals(0, pm.lottieCurrentFrameIndex.value)
    }

    @Test
    fun `lottie playback progress is not broadcast`() {
        val pm = manager()
        pm.setLottieProgress(0.5f)
        assertEquals(0.5f, pm.lottieProgress.value)
        assertTrue(reported.isEmpty(), "progress ticks every frame; broadcasting would flood the link")
    }

    // ── Look-ahead and notes ────────────────────────────────────────────────────

    @Test
    fun `the look-ahead section list is not itself a live change`() {
        // allLyricSections feeds the stage monitor's "next section" panel; the live content is
        // still whichever single section was set.
        val pm = manager()
        pm.setAllLyricSections(listOf(LyricSection(lines = listOf("a")), LyricSection(lines = listOf("b"))))
        assertEquals(2, pm.allLyricSections.value.size)
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `presenter notes are stage-monitor state, not live content`() {
        val pm = manager()
        pm.setPresenterNotes("Remember to mention the offering")
        assertEquals("Remember to mention the offering", pm.presenterNotes.value)
        assertTrue(reported.isEmpty(), "notes go to the platform, never to the audience output")
    }

    // ── Transition state ────────────────────────────────────────────────────────

    @Test
    fun `every transition alpha is settable and silent`() {
        val pm = manager()
        pm.setSlideTransitionAlpha(0.25f)
        pm.setSlideSlideOffset(120f)
        pm.setMediaTransitionAlpha(0.75f)
        pm.setAnnouncementTransitionAlpha(0.5f)

        assertEquals(0.25f, pm.slideTransitionAlpha.value)
        assertEquals(120f, pm.slideSlideOffset.value)
        assertEquals(0.75f, pm.mediaTransitionAlpha.value)
        assertEquals(0.5f, pm.announcementTransitionAlpha.value)
        assertTrue(reported.isEmpty())
    }

    // ── Browser-source identify ─────────────────────────────────────────────────

    @Test
    fun `identifying a browser source marks that output only`() {
        val pm = manager()
        pm.identifyBrowserSourceOutput(2)
        assertTrue(2 in pm.browserSourceIdentifying.value)
        assertTrue(0 !in pm.browserSourceIdentifying.value)

        pm.identifyBrowserSourceOutput(0)
        assertEquals(setOf(0, 2), pm.browserSourceIdentifying.value, "several outputs can flash at once")
        assertTrue(reported.isEmpty(), "identifying is an operator aid, not live content")
    }
}
