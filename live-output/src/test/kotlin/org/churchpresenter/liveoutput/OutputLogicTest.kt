package org.churchpresenter.liveoutput

import org.churchpresenter.settings.BackgroundLook
import org.churchpresenter.settings.OutputLook
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutputLogicTest {

    // ── DeckLink ────────────────────────────────────────────────────────────────

    @Test
    fun `outputs are counted only when the driver is there`() {
        assertEquals(3, deckLinkOutputCount(available = true) { 3 })
    }

    @Test
    fun `no driver means no outputs, and the count is never asked for`() {
        var asked = false
        assertEquals(0, deckLinkOutputCount(available = false) { asked = true; 3 })
        assertFalse(asked, "listing devices without the driver is what crashes")
    }

    // ── Transitions on the output ───────────────────────────────────────────────

    @Test
    fun `the crossfade takes the longer of the two that are on`() {
        // One duration serves both, because a crossfade from scripture to a song is a single
        // transition — the shorter of the two would cut it off part-way.
        val duration = modeCrossfadeDuration(
            BibleSettings(crossfade = true, transitionDuration = 400f),
            SongSettings(crossfade = true, transitionDuration = 900f),
        )
        assertEquals(900, duration)
    }

    @Test
    fun `a crossfade that is switched off contributes nothing`() {
        val duration = modeCrossfadeDuration(
            BibleSettings(crossfade = false, transitionDuration = 5000f),
            SongSettings(crossfade = true, transitionDuration = 400f),
        )
        assertEquals(400, duration, "the disabled one must not set the length")
    }

    @Test
    fun `with both off the transition still has a floor`() {
        // Below this a fade reads as a flicker rather than a transition.
        val duration = modeCrossfadeDuration(
            BibleSettings(crossfade = false), SongSettings(crossfade = false),
        )
        assertEquals(MIN_TRANSITION_MS, duration)
    }

    @Test
    fun `a screen pinned to the mode being cleared is noticed`() {
        assertTrue(isAnyScreenLockedTo(mapOf(0 to Presenting.LYRICS), Presenting.LYRICS))
        assertFalse(isAnyScreenLockedTo(mapOf(0 to Presenting.BIBLE), Presenting.LYRICS))
        assertFalse(isAnyScreenLockedTo(emptyMap(), Presenting.LYRICS))
    }

    @Test
    fun `clearing scripture or a song fades it out first`() {
        assertTrue(
            shouldFadeOnClear(Presenting.BIBLE, false, BibleSettings(fadeOut = true), SongSettings()),
        )
        assertTrue(
            shouldFadeOnClear(Presenting.LYRICS, false, BibleSettings(), SongSettings(fadeOut = true)),
        )
    }

    @Test
    fun `nothing fades while a screen is still showing it`() {
        // That display was not asked to clear, and the alpha is shared — fading would dim it there.
        assertFalse(
            shouldFadeOnClear(Presenting.LYRICS, true, BibleSettings(), SongSettings(fadeOut = true)),
        )
    }

    @Test
    fun `content with no fade of its own clears instantly`() {
        assertFalse(
            shouldFadeOnClear(Presenting.PICTURES, false, BibleSettings(fadeOut = true), SongSettings(fadeOut = true)),
        )
        assertFalse(
            shouldFadeOnClear(Presenting.BIBLE, false, BibleSettings(fadeOut = false), SongSettings()),
        )
    }

    @Test
    fun `each content type fades for its own configured time`() {
        assertEquals(
            700,
            fadeOutDuration(Presenting.BIBLE, BibleSettings(transitionDuration = 700f), SongSettings()),
        )
        assertEquals(
            300,
            fadeOutDuration(Presenting.LYRICS, BibleSettings(), SongSettings(transitionDuration = 300f)),
        )
    }

    @Test
    fun `anything else falls back, and nothing goes below the floor`() {
        assertEquals(500, fadeOutDuration(Presenting.MEDIA, BibleSettings(), SongSettings()))
        assertEquals(
            MIN_TRANSITION_MS,
            fadeOutDuration(Presenting.BIBLE, BibleSettings(transitionDuration = 10f), SongSettings()),
        )
    }

    // ── Announcements ───────────────────────────────────────────────────────────

    @Test
    fun `fade is told apart from cutting and sliding`() {
        assertTrue(isFadeAnnouncement(Constants.ANIMATION_FADE))
        assertFalse(isFadeAnnouncement(Constants.ANIMATION_NONE))
        assertFalse(isFadeAnnouncement("SLIDE_LEFT"))
    }

    @Test
    fun `a sliding announcement is left to the presenter to animate`() {
        // Running a fade here as well would fight the animation already in flight.
        assertTrue(isSlidingAnnouncement("SLIDE_LEFT"))
        assertTrue(isSlidingAnnouncement("SCROLL_UP"))
        assertFalse(isSlidingAnnouncement(Constants.ANIMATION_FADE))
        assertFalse(isSlidingAnnouncement(Constants.ANIMATION_NONE))
    }

    @Test
    fun `clearing fades out only when something was on screen`() {
        assertTrue(shouldFadeOutAnnouncement(isFade = true, wasEmpty = false))
    }

    @Test
    fun `fading out from an empty screen is skipped`() {
        // It would otherwise spend the animation's length showing nothing before the next content.
        assertFalse(shouldFadeOutAnnouncement(isFade = true, wasEmpty = true))
        assertFalse(shouldFadeOutAnnouncement(isFade = false, wasEmpty = false))
    }

    @Test
    fun `a loop count clears itself, and none stays up`() {
        assertTrue(isFiniteAnnouncementLoop(1))
        assertTrue(isFiniteAnnouncementLoop(5))
        assertFalse(isFiniteAnnouncementLoop(0), "zero means stay up until stopped by hand")
    }

    @Test
    fun `the speed slider reads the other way round`() {
        // A higher configured value means faster, so it is subtracted from the slider's span.
        assertEquals(20_500L, announcementDisplayMs(sliderSpan = 30_500L, animationDuration = 10_000L, loopCount = 1))
    }

    @Test
    fun `each loop adds its own time on screen`() {
        assertEquals(61_000L, announcementDisplayMs(sliderSpan = 30_500L, animationDuration = 0L, loopCount = 2))
    }

    @Test
    fun `an announcement is never on screen for less than the floor`() {
        assertEquals(
            MIN_ANNOUNCEMENT_DISPLAY_MS,
            announcementDisplayMs(sliderSpan = 30_500L, animationDuration = 99_999L, loopCount = 1),
        )
    }

    // ── Per-output rendering ────────────────────────────────────────────────────

    @Test
    fun `each layout has its own background switch`() {
        val lowerThird = OutputProfile(
            displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            look = OutputLook(background = BackgroundLook(lowerThird = false, fullscreen = true)),
        )
        assertFalse(showsOutputBackground(lowerThird), "the fullscreen switch must not stand in for it")

        val fullscreen = OutputProfile(
            displayMode = Constants.DISPLAY_MODE_FULLSCREEN,
            look = OutputLook(background = BackgroundLook(lowerThird = false, fullscreen = true)),
        )
        assertTrue(showsOutputBackground(fullscreen))
    }

    @Test
    fun `an output locked to a tab goes on showing it`() {
        val locks = mapOf(0 to Presenting.LYRICS)
        assertEquals(Presenting.LYRICS, effectiveOutputMode(locks, 0, Presenting.BIBLE))
    }

    @Test
    fun `an unlocked output follows whatever is live`() {
        assertEquals(Presenting.BIBLE, effectiveOutputMode(mapOf(0 to Presenting.LYRICS), 1, Presenting.BIBLE))
        assertEquals(Presenting.BIBLE, effectiveOutputMode(emptyMap(), 0, Presenting.BIBLE))
    }

    @Test
    fun `switching between two pieces of content crossfades`() {
        assertTrue(
            isScreenCrossfadeActive(
                BibleSettings(crossfade = true), SongSettings(crossfade = false),
                Presenting.LYRICS, Presenting.BIBLE,
            )
        )
        assertTrue(
            isScreenCrossfadeActive(
                BibleSettings(crossfade = false), SongSettings(crossfade = true),
                Presenting.LYRICS, Presenting.BIBLE,
            )
        )
    }

    @Test
    fun `coming from or going to an empty screen is a fade, not a crossfade`() {
        // The per-type fade settings own that moment; running both would fade twice over it.
        val on = BibleSettings(crossfade = true)
        assertFalse(isScreenCrossfadeActive(on, SongSettings(), Presenting.BIBLE, Presenting.NONE))
        assertFalse(isScreenCrossfadeActive(on, SongSettings(), Presenting.NONE, Presenting.BIBLE))
    }

    @Test
    fun `crossfade switched off everywhere cuts`() {
        assertFalse(
            isScreenCrossfadeActive(BibleSettings(), SongSettings(), Presenting.LYRICS, Presenting.BIBLE)
        )
    }

    @Test
    fun `the QA code points at the tunnel when there is one`() {
        // Only the tunnel URL is reachable from a phone that is not on the venue's WiFi.
        assertEquals(
            "https://abc.trycloudflare.com/qa",
            qaQrCodeUrl("https://abc.trycloudflare.com", "http://10.0.0.5:8080"),
        )
    }

    @Test
    fun `it falls back to the LAN address otherwise`() {
        assertEquals("http://10.0.0.5:8080/qa", qaQrCodeUrl("", "http://10.0.0.5:8080"))
    }

    // ── DeckLink and key outputs ────────────────────────────────────────────────

    @Test
    fun `an output aimed at SDI is told apart from one aimed at a display`() {
        assertTrue(isDeckLinkPrimaryOutput(ScreenAssignment(targetType = Constants.TARGET_TYPE_DECKLINK)))
        assertFalse(isDeckLinkPrimaryOutput(ScreenAssignment(targetType = Constants.TARGET_TYPE_SCREEN)))
    }

    @Test
    fun `a key output on SDI needs a device actually chosen`() {
        val chosen = ScreenAssignment(
            keyTargetType = Constants.TARGET_TYPE_DECKLINK, keyTargetDisplay = 0,
        )
        assertTrue(hasDeckLinkKeyOutput(chosen))
    }

    @Test
    fun `a key output left unconfigured drives nothing`() {
        // KEY_TARGET_NONE is what hasKeyOutput reads as off — nothing may be pushed to a device.
        val off = ScreenAssignment(keyTargetType = Constants.TARGET_TYPE_DECKLINK)
        assertFalse(off.hasKeyOutput, "the default must be off")
        assertFalse(hasDeckLinkKeyOutput(off))
        assertFalse(hasScreenKeyOutput(off))
    }

    @Test
    fun `a key output on a display is not taken for an SDI one`() {
        val onScreen = ScreenAssignment(keyTargetType = Constants.TARGET_TYPE_SCREEN, keyTargetDisplay = 1)
        assertTrue(hasScreenKeyOutput(onScreen))
        assertFalse(hasDeckLinkKeyOutput(onScreen))
        assertFalse(isDeckLinkKeyOutput(onScreen))
    }

    @Test
    fun `an SDI key output is not also driven as a display one`() {
        // Both windows are spawned from the same assignment, so exactly one may claim it.
        val onDeckLink = ScreenAssignment(keyTargetType = Constants.TARGET_TYPE_DECKLINK, keyTargetDisplay = 0)
        assertTrue(hasDeckLinkKeyOutput(onDeckLink))
        assertFalse(hasScreenKeyOutput(onDeckLink))
    }

    @Test
    fun `a key output is placed by its saved bounds, not its saved index`() {
        // Display indices are reordered by the OS when monitors are plugged or unplugged.
        assertEquals(2, keyOutputScreenIndex(matchedByBounds = 2, savedIndex = 0))
    }

    @Test
    fun `the saved index is the fallback when the bounds match nothing`() {
        assertEquals(0, keyOutputScreenIndex(matchedByBounds = null, savedIndex = 0))
    }

    // ── Output windows ──────────────────────────────────────────────────────────

    @Test
    fun `going live raises the output windows, clearing does not`() {
        Presenting.entries.filter { it != Presenting.NONE }
            .forEach { assertTrue(shouldShowPresenterWindowFor(it), it.name) }
        assertFalse(shouldShowPresenterWindowFor(Presenting.NONE))
    }

    @Test
    fun `an index names an attached display, or it does not`() {
        assertTrue(isScreenIndexValid(0, screenCount = 2))
        assertTrue(isScreenIndexValid(1, screenCount = 2))
        assertFalse(isScreenIndexValid(2, screenCount = 2))
        assertFalse(isScreenIndexValid(-1, screenCount = 2))
        assertFalse(isScreenIndexValid(0, screenCount = 0))
    }
}
