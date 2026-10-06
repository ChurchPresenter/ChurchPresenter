package org.churchpresenter.liveoutput

import org.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.presenter.SongPresenter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Slide changes on the outputs, frame by frame: the operator's push goes through the real
 * [PresenterManager] and [PresenterTransitionEffects] into the song and Bible presenters, the test
 * clock steps one frame at a time, and every frame is checked.
 *
 * Each case is something an audience saw for a frame or a fade -- a blank frame before a
 * crossfade, the old verse redrawn at the new verse's size, the look-ahead jumping two verses.
 * Sizes are compared with themselves before the change rather than with fixed numbers, which
 * differ with the platform's font metrics.
 */
@OptIn(ExperimentalTestApi::class)
class SlideTransitionFrameTest {

    private fun verse(n: Int, lines: Int, words: Int) = LyricSection(
        header = "[Verse $n]",
        type = Constants.SECTION_TYPE_VERSE,
        lines = List(lines) { i -> "v$n line $i " + "word ".repeat(words) },
    )

    // One long verse between two short ones: the long one sets a small size, the short ones a large.
    private val song = listOf(
        verse(1, lines = 8, words = 6),
        verse(2, lines = 2, words = 1),
        verse(3, lines = 4, words = 3),
    )

    private fun songSettings(crossfade: Boolean, eachSlide: Boolean = false) = AppSettings(
        songSettings = SongSettings(
            crossfade = crossfade,
            transitionDuration = FADE_MS.toFloat(),
            lyricsFontSize = 150,
            lookAheadFontSize = 150,
            layoutExtras = SongLayoutExtras(autoFitEachSlide = eachSlide),
        ),
    )

    /** What the Songs tab writes for one push, in its order. */
    private fun PresenterManager.push(section: Int) {
        setAllLyricSections(song)
        setSongDisplaySectionIndex(section)
        setSongDisplayLineIndex(0)
        setLyricSection(song[section])
    }

    /** One output as the output window draws it, fed from [manager]. */
    private fun ComposeUiTest.output(
        manager: PresenterManager,
        settings: AppSettings,
        content: @Composable () -> Unit,
    ) {
        setContent {
            PresenterTransitionEffects(manager, settings)
            MaterialTheme { Box(Modifier.size(640.dp, 360.dp).testTag(OUTPUT)) { content() } }
        }
    }

    @Composable
    private fun SongOutput(manager: PresenterManager, settings: AppSettings, lookAhead: Boolean = false) {
        val presenting by manager.slideContent
        val section by manager.displayedLyricSection
        val alpha by manager.songTransitionAlpha
        val position by manager.displayedSongPosition
        if (presenting == Presenting.LYRICS) {
            SongPresenter(
                lyricSection = section,
                appSettings = settings,
                transitionAlpha = alpha,
                displayLineIndex = position.lineIndex,
                lookAheadEnabled = lookAhead,
                allLyricSections = position.allSections,
                displaySectionIndex = position.sectionIndex,
                crossfadeEnabled = settings.songSettings.crossfade,
            )
        }
    }

    /** Puts [first] on the outputs, lets every entrance finish, and stops the clock. */
    private fun ComposeUiTest.goLive(mode: Presenting, manager: PresenterManager, first: PresenterManager.() -> Unit) {
        manager.first()
        manager.setPresentingMode(mode)
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
        mainClock.autoAdvance = false
    }

    /** How much of the output is lit -- zero for a blank frame. */
    private fun ComposeUiTest.ink(): Int {
        val pixels = onNodeWithTag(OUTPUT).captureToImage().toPixelMap()
        var lit = 0
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
            val c = pixels[x, y]
            if (c.red + c.green + c.blue > 1.5f) lit++
        }
        return lit
    }

    /** The drawn heights of every node showing [text]: one per layer it is drawn in. */
    private fun ComposeUiTest.heightsOf(text: String): List<Float> =
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().map { it.boundsInRoot.height }

    private fun ComposeUiTest.frames(count: Int = FADE_MS / FRAME_MS + 4, check: (Int) -> Unit) {
        repeat(count) { frame ->
            mainClock.advanceTimeByFrame()
            check(frame)
        }
    }

    private fun ComposeUiTest.assertNoBlankFrame(change: () -> Unit) {
        val before = ink()
        change()
        val during = mutableListOf<Int>()
        frames(count = 6) { during += ink() }
        mainClock.advanceTimeBy(SETTLE_MS)
        val after = ink()
        val floor = minOf(before, after) / 2
        assertTrue(
            during.all { it > floor },
            "a frame of the change went dark: lit $before before, $during frame by frame, $after after",
        )
    }

    @Test
    fun `a song crossfade never draws a blank frame`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = songSettings(crossfade = true)
        output(manager, settings) { SongOutput(manager, settings) }
        goLive(Presenting.LYRICS, manager) { push(0) }

        assertNoBlankFrame { manager.push(1) }
    }

    @Test
    fun `a Bible crossfade never draws a blank frame`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = AppSettings(
            bibleSettings = BibleSettings(crossfade = true, transitionDuration = FADE_MS.toFloat()),
        )
        output(manager, settings) {
            val presenting by manager.slideContent
            val verses by manager.displayedVerses
            val alpha by manager.bibleTransitionAlpha
            if (presenting == Presenting.BIBLE) {
                BiblePresenter(
                    selectedVerses = verses,
                    appSettings = settings,
                    transitionAlpha = alpha,
                    crossfadeEnabled = true,
                )
            }
        }
        val first = SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseText = "word ".repeat(60))
        goLive(Presenting.BIBLE, manager) { setSelectedVerses(listOf(first)) }

        assertNoBlankFrame { manager.setSelectedVerses(listOf(first.copy(verseNumber = 17, verseText = "word word"))) }
    }

    @Test
    fun `the outgoing verse keeps its own size through a crossfade when each slide is fitted`() =
        runComposeUiTest {
            val manager = PresenterManager()
            val settings = songSettings(crossfade = true, eachSlide = true)
            output(manager, settings) { SongOutput(manager, settings) }
            goLive(Presenting.LYRICS, manager) { push(0) }
            val ownSize = heightsOf("v1 line 0").single()

            manager.push(1)
            frames { frame ->
                heightsOf("v1 line 0").forEach { height ->
                    assertTrue(
                        height in ownSize * 0.98f..ownSize * 1.02f,
                        "frame $frame drew the outgoing verse at $height, its own size is $ownSize",
                    )
                }
            }
        }

    @Test
    fun `the old verse is never redrawn at the new one's size on a cut`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = songSettings(crossfade = false, eachSlide = true)
        output(manager, settings) { SongOutput(manager, settings) }
        goLive(Presenting.LYRICS, manager) { push(0) }
        val ownSize = heightsOf("v1 line 0").single()

        manager.push(1)
        frames(count = 4) { frame ->
            heightsOf("v1 line 0").forEach { height ->
                assertTrue(
                    height in ownSize * 0.98f..ownSize * 1.02f,
                    "frame $frame drew the old verse at $height, its own size is $ownSize",
                )
            }
        }
    }

    @Test
    fun `the look-ahead never shows the verse after next`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = songSettings(crossfade = false)
        output(manager, settings) { SongOutput(manager, settings, lookAhead = true) }
        goLive(Presenting.LYRICS, manager) { push(0) }

        manager.push(1)
        frames(count = 4) { frame ->
            val oldVerseUp = heightsOf("v1 line 0").isNotEmpty()
            val verseThreeUp = heightsOf("v3 line 0").isNotEmpty()
            assertTrue(
                !(oldVerseUp && verseThreeUp),
                "frame $frame drew verse 1 with verse 3 as its look-ahead -- verse 2 comes next",
            )
        }
    }

    private companion object {
        const val OUTPUT = "output"
        const val FADE_MS = 320
        const val FRAME_MS = 16
        const val SETTLE_MS = 1500L
    }
}
