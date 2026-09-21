@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.FixedViewport
import org.churchpresenter.app.churchpresenter.ViewportProbe
import org.churchpresenter.app.churchpresenter.horizontalOverflow
import org.churchpresenter.app.churchpresenter.verticalOverflow
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * "Nothing on the audience screen may be drawn outside the box it was given" -- checked directly
 * against node positions rather than by eye, because a screenshot diff only tells a human that
 * something moved, not that it now runs off the edge.
 *
 * This is the property [MIN_PRESENTER_SCALE]'s old floor of 0.5 broke: it inflated `scaleFactor` on
 * any output narrower than the floor's own ratio -- a 720x1280 output (nothing in
 * [org.churchpresenter.app.churchpresenter.utils.OutputGeometry]'s 16:9-family presets ever exercised
 * the bug) drew lyrics wider than the screen actually was. Fixed in [presenterScale]; these tests are
 * the regression guard.
 *
 * A 1920x1080 case is kept alongside each portrait one specifically so a failure here reads as
 * "portrait broke it", not "this content always overflows a bit".
 */
class PresenterOverflowTest {

    private fun song(lines: List<String> = LONG_VERSE) = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines,
    )

    private fun verse(text: String = LONG_PASSAGE) = SelectedVerse(
        translationFileName = "kjv.spb",
        bibleAbbreviation = "KJV",
        bibleName = "KJV",
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
    )

    private fun bibleSettings() = AppSettings(bibleSettings = BibleSettings(primaryBible = "kjv.spb"))

    /**
     * [width]/[height] are real pixels of the test *window*, not a `Modifier.size` inside an
     * unrelated one -- `runComposeUiTest`'s default window is a fixed 1024x768 that silently clamps
     * any larger `Modifier.size`, which is exactly the trap that let an earlier version of this test
     * pass at "720x1280" and "720x6000" while actually composing both at 720x768.
     */
    private fun assertNoOverflow(width: Int, height: Int, content: @Composable () -> Unit) =
        runDesktopComposeUiTest(width = width, height = height) {
            val probe = ViewportProbe()
            setContent { FixedViewport(width.dp, height.dp, probe) { content() } }
            waitForIdle()
            val h = horizontalOverflow(probe)
            val v = verticalOverflow(probe)
            assertTrue(h <= TOLERANCE, "content runs ${h.value}dp past the right edge of a ${width}x${height} box")
            assertTrue(v <= TOLERANCE, "content runs ${v.value}dp past the bottom edge of a ${width}x${height} box")
        }

    // ── Songs ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a long song fits a 1080p output`() =
        assertNoOverflow(1920, 1080) { SongPresenter(lyricSection = song(), appSettings = AppSettings()) }

    @Test
    fun `a long song fits a narrow portrait output`() =
        assertNoOverflow(720, 1280) { SongPresenter(lyricSection = song(), appSettings = AppSettings()) }

    @Test
    fun `a long song fits the shipped portrait preset`() =
        assertNoOverflow(1080, 1920) { SongPresenter(lyricSection = song(), appSettings = AppSettings()) }

    @Test
    fun `a song lower third fits a narrow portrait output`() = assertNoOverflow(720, 1280) {
        SongPresenter(lyricSection = song(), appSettings = AppSettings(), isLowerThird = true)
    }

    // ── Bible ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a long passage fits a 1080p output`() =
        assertNoOverflow(1920, 1080) { BiblePresenter(selectedVerses = listOf(verse()), appSettings = bibleSettings()) }

    @Test
    fun `a long passage fits a narrow portrait output`() =
        assertNoOverflow(720, 1280) { BiblePresenter(selectedVerses = listOf(verse()), appSettings = bibleSettings()) }

    @Test
    fun `a long passage fits the shipped portrait preset`() =
        assertNoOverflow(1080, 1920) { BiblePresenter(selectedVerses = listOf(verse()), appSettings = bibleSettings()) }

    private companion object {
        /** Absorbs sub-pixel layout rounding; not slack for a real overflow. */
        val TOLERANCE = Dp(1f)

        val LONG_VERSE = listOf(
            "Amazing grace how sweet the sound that saved a wretch like me",
            "I once was lost but now am found, was blind but now I see",
            "'Twas grace that taught my heart to fear, and grace my fears relieved",
            "How precious did that grace appear the hour I first believed",
        )

        const val LONG_PASSAGE =
            "The LORD is my shepherd; I shall not want. He maketh me to lie down in green " +
                "pastures: he leadeth me beside the still waters. He restoreth my soul: he leadeth " +
                "me in the paths of righteousness for his name's sake."
    }
}
