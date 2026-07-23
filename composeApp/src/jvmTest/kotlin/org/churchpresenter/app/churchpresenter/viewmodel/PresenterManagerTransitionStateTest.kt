package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The visual-transition state on [PresenterManager] — the "displayed", "previous" and alpha/offset
 * values the presenter animations read while crossfading between what is live now and what was live
 * a moment ago.
 *
 * Two things hold for every one of these: the getter reflects what the setter was given, and the
 * set is SILENT — it must not fire `onLiveStateChanged`. That callback drives Instance Link
 * mirroring, so broadcasting an in-flight animation frame would either flood a follower with
 * mid-transition noise or pair it with the wrong content type. Only the content setters (verse,
 * lyric, media, …) broadcast; these do not.
 */
class PresenterManagerTransitionStateTest {

    private val reported = mutableListOf<Presenting>()

    private fun manager(): PresenterManager = PresenterManager().apply {
        onLiveStateChanged = { _, source -> reported.add(source) }
    }

    private fun assertSilent() =
        assertTrue(reported.isEmpty(), "transition state must not broadcast a live change; got $reported")

    // ── Bible crossfade state ────────────────────────────────────────────────────

    @Test
    fun `the displayed and next verses are settable and silent`() {
        val pm = manager()
        val shown = listOf(SelectedVerse(chapter = 1, verseNumber = 1, verseText = "shown"))
        val upcoming = listOf(SelectedVerse(chapter = 1, verseNumber = 2, verseText = "next"))

        pm.setDisplayedVerses(shown)
        pm.setNextVerses(upcoming)
        pm.setPreviousDisplayedVerses(shown)

        assertEquals(shown, pm.displayedVerses.value)
        assertEquals(upcoming, pm.nextVerses.value)
        assertEquals(shown, pm.previousDisplayedVerses.value)
        assertSilent()
    }

    @Test
    fun `the bible alpha and hold are settable and silent`() {
        val pm = manager()

        pm.setPreviousBibleAlpha(0.25f)
        pm.setBibleHold(true)

        assertEquals(0.25f, pm.previousBibleAlpha.value)
        assertTrue(pm.bibleHold.value)
        assertSilent()
    }

    // ── Song crossfade state ─────────────────────────────────────────────────────

    @Test
    fun `the displayed and previous lyric sections are settable and silent`() {
        val pm = manager()
        val shown = LyricSection(title = "Verse 1", lines = listOf("shown line"))
        val faded = LyricSection(title = "Chorus", lines = listOf("previous line"))

        pm.setDisplayedLyricSection(shown)
        pm.setPreviousDisplayedLyricSection(faded)
        pm.setPreviousSongAlpha(0.5f)

        assertEquals(shown, pm.displayedLyricSection.value)
        assertEquals(faded, pm.previousDisplayedLyricSection.value)
        assertEquals(0.5f, pm.previousSongAlpha.value)
        assertSilent()
    }

    // ── Picture crossfade state ──────────────────────────────────────────────────

    @Test
    fun `the displayed and next image paths are settable and silent`() {
        val pm = manager()

        pm.setDisplayedImagePath("/photos/shown.jpg")
        pm.setNextImagePath("/photos/next.jpg")
        pm.setPreviousDisplayedImagePath("/photos/faded.jpg")

        assertEquals("/photos/shown.jpg", pm.displayedImagePath.value)
        assertEquals("/photos/next.jpg", pm.nextImagePath.value)
        assertEquals("/photos/faded.jpg", pm.previousDisplayedImagePath.value)
        assertSilent()
    }

    @Test
    fun `a null image path is a valid state, not a no-op`() {
        val pm = manager()
        pm.setDisplayedImagePath("/photos/shown.jpg")

        pm.setDisplayedImagePath(null)

        assertNull(pm.displayedImagePath.value, "clearing the displayed image is a real state, e.g. between slides")
        assertSilent()
    }

    @Test
    fun `the picture alpha and slide offset are settable and silent`() {
        val pm = manager()

        pm.setPictureTransitionAlpha(0.3f)
        pm.setPictureSlideOffset(-120f)

        assertEquals(0.3f, pm.pictureTransitionAlpha.value)
        assertEquals(-120f, pm.pictureSlideOffset.value)
        assertSilent()
    }

    // ── Shared transition config ─────────────────────────────────────────────────

    @Test
    fun `the animation type and transition duration are settable and silent`() {
        val pm = manager()

        pm.setAnimationType(AnimationType.SLIDE_LEFT)
        pm.setTransitionDuration(750)

        assertEquals(AnimationType.SLIDE_LEFT, pm.animationType.value)
        assertEquals(750, pm.transitionDuration.value)
        assertSilent()
    }

    // ── Presentation / lottie / dev-window visual state ──────────────────────────

    @Test
    fun `the selected slide bitmap is settable and silent`() {
        val pm = manager()
        val bitmap = ImageBitmap(4, 4)

        pm.setSelectedSlide(bitmap)

        assertSame(bitmap, pm.selectedSlide.value)
        assertSilent()
    }

    @Test
    fun `the lottie current frame index is settable and silent`() {
        // With no active frame stream the setter just records the index; playback progress like
        // this is deliberately not broadcast.
        val pm = manager()

        pm.setLottieCurrentFrameIndex(42)

        assertEquals(42, pm.lottieCurrentFrameIndex.value)
        assertSilent()
    }

    @Test
    fun `the dev-window always-on-top flag is settable and silent`() {
        val pm = manager()

        pm.setDevWindowAlwaysOnTop(true)

        assertTrue(pm.devWindowAlwaysOnTop.value)
        assertSilent()
    }
}
