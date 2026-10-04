package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Lower thirds, announcements and captions held as overlays over the slide, and what each output
 * makes of them: over its content, or in place of it.
 */
class PresenterManagerOverlayTest {

    private fun manager() = PresenterManager(showPresenterWindowInitially = false)

    private val overContent = OutputProfile(
        lowerThirdOverContent = true,
        announcementsOverContent = true,
        captionsOverContent = true,
    )

    // ── Going live ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an overlay going live leaves the slide as it is`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        assertEquals(Presenting.LYRICS, pm.presentingMode.value)
        assertEquals(setOf(Presenting.LOWER_THIRD), pm.overlays.value)
        assertEquals(Presenting.LOWER_THIRD, pm.lastLive.value)
        assertTrue(pm.isLive(Presenting.LYRICS))
        assertTrue(pm.isLive(Presenting.LOWER_THIRD))
    }

    @Test
    fun `slide content going live takes the overlays down`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        pm.setPresentingMode(Presenting.STT)
        pm.setPresentingMode(Presenting.BIBLE)
        assertTrue(pm.overlays.value.isEmpty())
        assertEquals(Presenting.BIBLE, pm.lastLive.value)
        assertFalse(pm.isLive(Presenting.LOWER_THIRD))
    }

    @Test
    fun `clearing takes everything down`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        pm.setPresentingMode(Presenting.NONE)
        assertTrue(pm.overlays.value.isEmpty())
        assertEquals(Presenting.NONE, pm.lastLive.value)
        assertFalse(pm.anythingLive)
    }

    @Test
    fun `an overlay alone is something live, and can be cleared`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        assertEquals(Presenting.NONE, pm.presentingMode.value)
        assertTrue(pm.anythingLive)
        pm.requestClearDisplay()
        assertTrue(pm.clearDisplayRequested.value, "a lone overlay must still be clearable")
    }

    @Test
    fun `an overlay put up again counts as the newest`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        assertEquals(listOf(Presenting.ANNOUNCEMENTS, Presenting.LOWER_THIRD), pm.overlays.value.toList())
    }

    // ── Taking one down ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `clearing one overlay leaves the slide and the others`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.STT)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        pm.clearOverlay(Presenting.LOWER_THIRD)
        assertEquals(Presenting.LYRICS, pm.presentingMode.value)
        assertEquals(setOf(Presenting.STT), pm.overlays.value)
        assertEquals(Presenting.STT, pm.lastLive.value, "the newest of what is still up")
        pm.clearOverlay(Presenting.STT)
        assertEquals(Presenting.LYRICS, pm.lastLive.value, "and then the slide")
    }

    @Test
    fun `clearing an overlay that is not up changes nothing`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.clearOverlay(Presenting.LOWER_THIRD)
        assertEquals(Presenting.LYRICS, pm.lastLive.value)
        assertTrue(pm.overlays.value.isEmpty())
    }

    @Test
    fun `an overlay finishing clears the display by default`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        pm.overlayFinished(Presenting.LOWER_THIRD, clearsDisplay = true)
        assertTrue(pm.clearDisplayRequested.value)
    }

    @Test
    fun `an overlay finishing can take down only itself`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        pm.overlayFinished(Presenting.ANNOUNCEMENTS, clearsDisplay = false)
        assertFalse(pm.clearDisplayRequested.value)
        assertTrue(pm.overlays.value.isEmpty())
        assertEquals(Presenting.LYRICS, pm.presentingMode.value)
    }

    @Test
    fun `an overlay that is no longer up finishing does nothing`() {
        // Its run ends on its own clock; by then the operator may have moved on.
        val pm = manager()
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        pm.setPresentingMode(Presenting.BIBLE)
        pm.overlayFinished(Presenting.LOWER_THIRD, clearsDisplay = true)
        assertFalse(pm.clearDisplayRequested.value)
        assertEquals(Presenting.BIBLE, pm.presentingMode.value)
    }

    // ── What a tab sees ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a tab sees its own overlay as live while it is up`() {
        val pm = manager()
        val seenByCaptions = pm.modeAsSeenBy(Presenting.STT)
        pm.setPresentingMode(Presenting.LYRICS)
        assertEquals(Presenting.LYRICS, seenByCaptions.value)
        pm.setPresentingMode(Presenting.STT)
        assertEquals(Presenting.STT, seenByCaptions.value)
        pm.clearOverlay(Presenting.STT)
        assertEquals(Presenting.LYRICS, seenByCaptions.value)
    }

    // ── What each output shows ──────────────────────────────────────────────────────────────────

    @Test
    fun `by default an overlay replaces the content, as it always did`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        assertEquals(Presenting.LOWER_THIRD, pm.unlockedModeFor(OutputProfile()))
    }

    @Test
    fun `an output that puts overlays over its content keeps the slide`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        assertEquals(Presenting.LYRICS, pm.unlockedModeFor(overContent))
    }

    @Test
    fun `an output replacing with some overlays shows the newest of those`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        val lowerThirdOver = OutputProfile(lowerThirdOverContent = true)
        assertEquals(Presenting.ANNOUNCEMENTS, pm.unlockedModeFor(lowerThirdOver))
        assertEquals(Presenting.LOWER_THIRD, pm.unlockedModeFor(OutputProfile()))
    }

    @Test
    fun `a screen lock wins over everything`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        assertEquals(Presenting.BIBLE, pm.shownModeFor(OutputProfile(), Presenting.BIBLE))
        assertEquals(Presenting.LOWER_THIRD, pm.shownModeFor(OutputProfile(), Presenting.LYRICS))
    }

    @Test
    fun `each overlay has its own switch`() {
        assertTrue(OutputProfile(lowerThirdOverContent = true).drawsOverContent(Presenting.LOWER_THIRD))
        assertTrue(OutputProfile(announcementsOverContent = true).drawsOverContent(Presenting.ANNOUNCEMENTS))
        assertTrue(OutputProfile(captionsOverContent = true).drawsOverContent(Presenting.STT))
        assertFalse(OutputProfile(lowerThirdOverContent = true).drawsOverContent(Presenting.STT))
        assertFalse(overContent.drawsOverContent(Presenting.LYRICS), "slide content is never an overlay")
    }
}
