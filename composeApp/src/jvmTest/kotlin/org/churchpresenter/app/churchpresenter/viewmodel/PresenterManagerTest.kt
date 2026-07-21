package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [PresenterManager] is the single source of truth for what the audience is seeing. Its most
 * subtle contract is the one on `onLiveStateChanged`: each content setter reports the content type
 * it *belongs to*, never the currently-live `presentingMode`. Deriving it from the live mode would
 * let a broadcast pair the wrong mode with fresh content (or the right mode with stale content)
 * depending on which setter happened to run first — and this callback is what drives Instance Link
 * mirroring, so getting it wrong shows the wrong thing on a follower's output.
 */
class PresenterManagerTest {

    /** Every (manager, source) pair the manager reported, in order. */
    private val reported = mutableListOf<Presenting>()

    private fun manager(): PresenterManager = PresenterManager().apply {
        onLiveStateChanged = { _, source -> reported.add(source) }
    }

    // ── Presenting mode ─────────────────────────────────────────────────────────

    @Test
    fun `a new manager is showing nothing`() {
        val pm = PresenterManager()
        assertEquals(Presenting.NONE, pm.presentingMode.value)
        assertFalse(pm.clearDisplayRequested.value)
        assertTrue(pm.screenLocks.value.isEmpty())
        assertTrue(pm.browserSourceLocks.value.isEmpty())
    }

    @Test
    fun `setting a mode reports that mode and resets the transition alphas`() {
        val pm = manager()
        pm.setBibleTransitionAlpha(0f)
        pm.setSongTransitionAlpha(0f)

        pm.setPresentingMode(Presenting.BIBLE)

        assertEquals(Presenting.BIBLE, pm.presentingMode.value)
        assertEquals(listOf(Presenting.BIBLE), reported)
        // Going live must leave content visible; the presenter's own fade-in does the animation.
        assertEquals(1f, pm.bibleTransitionAlpha.value)
        assertEquals(1f, pm.songTransitionAlpha.value)
    }

    @Test
    fun `going live cancels a pending clear request`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.requestClearDisplay()
        assertTrue(pm.clearDisplayRequested.value)

        pm.setPresentingMode(Presenting.BIBLE)
        assertFalse(pm.clearDisplayRequested.value, "a stale clear would blank the new content")
    }

    @Test
    fun `switching to NONE does not reset the alphas`() {
        // NONE is the end state of a fade-out, so forcing alpha back to 1 would flash the content
        // back on screen just as it finished disappearing.
        val pm = manager()
        pm.setPresentingMode(Presenting.BIBLE)
        pm.setBibleTransitionAlpha(0f)
        pm.setPresentingMode(Presenting.NONE)
        assertEquals(0f, pm.bibleTransitionAlpha.value)
    }

    @Test
    fun `clear display is only requestable while something is live`() {
        val pm = manager()
        pm.requestClearDisplay()
        assertFalse(pm.clearDisplayRequested.value, "nothing is live, so there is nothing to clear")

        pm.setPresentingMode(Presenting.PICTURES)
        pm.requestClearDisplay()
        assertTrue(pm.clearDisplayRequested.value)
    }

    @Test
    fun `re-setting the same mode still reports it`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.BIBLE)
        pm.setPresentingMode(Presenting.BIBLE)
        assertEquals(listOf(Presenting.BIBLE, Presenting.BIBLE), reported)
    }

    @Test
    fun `leaving presentation mode releases the animated player`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.PRESENTATION)
        pm.setPresentingMode(Presenting.LYRICS)
        assertNull(pm.presentationFrame.value, "a stale animated frame must not survive the switch")
    }

    // ── The live-state source contract ──────────────────────────────────────────

    @Test
    fun `a content setter reports its own type, not the live mode`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS) // something else is live
        reported.clear()

        pm.setSelectedVerse(SelectedVerse())
        assertEquals(listOf(Presenting.BIBLE), reported, "a verse change is always a BIBLE change")

        reported.clear()
        pm.setSelectedImagePath("/tmp/a.jpg")
        assertEquals(listOf(Presenting.PICTURES), reported)

        reported.clear()
        pm.setWebsiteUrl("https://example.org")
        assertEquals(listOf(Presenting.WEBSITE), reported)

        reported.clear()
        pm.setLyricSection(LyricSection(lines = listOf("a line")))
        assertEquals(listOf(Presenting.LYRICS), reported)
    }

    @Test
    fun `purely visual state changes are not broadcast`() {
        // These fire on every animation frame; broadcasting them would flood Instance Link.
        val pm = manager()
        pm.setPresentingMode(Presenting.BIBLE)
        reported.clear()

        pm.setBibleTransitionAlpha(0.5f)
        pm.setSongTransitionAlpha(0.5f)
        pm.setPreviousBibleAlpha(0.25f)
        pm.setDisplayedVerses(listOf(SelectedVerse()))
        pm.setNextVerses(listOf(SelectedVerse()))
        pm.setPreviousDisplayedVerses(emptyList())
        pm.setBibleHold(true)
        pm.setDisplayedImagePath("/tmp/b.jpg")
        pm.setSongDisplayLineIndex(2)
        pm.setSongDisplaySectionIndex(1)

        assertTrue(reported.isEmpty(), "visual/transition state should not broadcast: $reported")
    }

    // ── Verses ──────────────────────────────────────────────────────────────────

    @Test
    fun `the singular verse tracks the first of the plural selection`() {
        val pm = manager()
        val first = SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16)
        val second = SelectedVerse(bookName = "John", chapter = 3, verseNumber = 17)

        pm.setSelectedVerses(listOf(first, second))
        assertEquals(listOf(first, second), pm.selectedVerses.value)
        assertEquals(first, pm.selectedVerse.value, "the singular accessor derives from the list")
    }

    @Test
    fun `an empty selection leaves the previous singular verse alone`() {
        val pm = manager()
        val verse = SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16)
        pm.setSelectedVerses(listOf(verse))
        pm.setSelectedVerses(emptyList())

        assertTrue(pm.selectedVerses.value.isEmpty())
        assertEquals(verse, pm.selectedVerse.value, "clearing the list must not null out the singular")
    }

    // ── Lyrics ──────────────────────────────────────────────────────────────────

    @Test
    fun `the lyric section version increments on every set, even for identical content`() {
        // Presenters key off the version to re-render; without the bump, re-sending the same
        // section after an edit would not refresh the output.
        val pm = manager()
        val start = pm.lyricSectionVersion.value
        val section = LyricSection(lines = listOf("Amazing grace"))

        pm.setLyricSection(section)
        pm.setLyricSection(section)

        assertEquals(start + 2, pm.lyricSectionVersion.value)
        assertEquals(section, pm.lyricSection.value)
    }

    @Test
    fun `song display indices are independent of each other`() {
        val pm = manager()
        pm.setSongDisplaySectionIndex(3)
        pm.setSongDisplayLineIndex(2)
        assertEquals(3, pm.songDisplaySectionIndex.value)
        assertEquals(2, pm.songDisplayLineIndex.value)

        pm.setSongDisplayLineIndex(0)
        assertEquals(3, pm.songDisplaySectionIndex.value, "changing the line must not reset the section")
    }

    // ── Output locks ────────────────────────────────────────────────────────────

    @Test
    fun `a screen lock pins one output and null releases it`() {
        val pm = manager()
        pm.setScreenLock(1, Presenting.LYRICS)
        assertEquals(mapOf(1 to Presenting.LYRICS), pm.screenLocks.value)

        pm.setScreenLock(2, Presenting.BIBLE)
        assertEquals(mapOf(1 to Presenting.LYRICS, 2 to Presenting.BIBLE), pm.screenLocks.value)

        pm.setScreenLock(1, null)
        assertEquals(mapOf(2 to Presenting.BIBLE), pm.screenLocks.value, "null releases the lock entirely")
    }

    @Test
    fun `releasing an unlocked screen is harmless`() {
        val pm = manager()
        pm.setScreenLock(5, null)
        assertTrue(pm.screenLocks.value.isEmpty())
    }

    @Test
    fun `re-locking a screen replaces the previous mode`() {
        val pm = manager()
        pm.setScreenLock(0, Presenting.LYRICS)
        pm.setScreenLock(0, Presenting.MEDIA)
        assertEquals(mapOf(0 to Presenting.MEDIA), pm.screenLocks.value)
    }

    @Test
    fun `screen locks and browser-source locks use separate index spaces`() {
        // Both are 0-based and unrelated; sharing one map would have output 0 of one kind
        // silently steal the other's lock.
        val pm = manager()
        pm.setScreenLock(0, Presenting.LYRICS)
        pm.setBrowserSourceLock(0, Presenting.BIBLE)

        assertEquals(mapOf(0 to Presenting.LYRICS), pm.screenLocks.value)
        assertEquals(mapOf(0 to Presenting.BIBLE), pm.browserSourceLocks.value)

        pm.setBrowserSourceLock(0, null)
        assertEquals(mapOf(0 to Presenting.LYRICS), pm.screenLocks.value, "releasing one must not touch the other")
        assertTrue(pm.browserSourceLocks.value.isEmpty())
    }

    @Test
    fun `locks do not broadcast a live-state change`() {
        val pm = manager()
        pm.setScreenLock(0, Presenting.LYRICS)
        pm.setBrowserSourceLock(0, Presenting.BIBLE)
        assertTrue(reported.isEmpty(), "a per-output lock is local, not live content")
    }

    // ── Media ───────────────────────────────────────────────────────────────────

    @Test
    fun `setting the current media records url and type together`() {
        val pm = manager()
        pm.setCurrentMedia("https://example.org/clip.mp4", "video")
        assertEquals("https://example.org/clip.mp4", pm.currentMediaUrl.value)
        assertEquals("video", pm.currentMediaType.value)
        assertEquals(listOf(Presenting.MEDIA), reported)
    }

    // ── Slides ──────────────────────────────────────────────────────────────────

    @Test
    fun `freezing a slide is a display-only toggle`() {
        val pm = manager()
        pm.setSlideFrozen(true)
        assertTrue(pm.slideFrozen.value)
        pm.setSlideFrozen(false)
        assertFalse(pm.slideFrozen.value)
        assertTrue(reported.isEmpty(), "freeze is a local display concern")
    }
}
