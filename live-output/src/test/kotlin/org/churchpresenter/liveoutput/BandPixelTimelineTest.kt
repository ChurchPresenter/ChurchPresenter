package org.churchpresenter.liveoutput

import org.churchpresenter.presenter.BibleBandClock
import org.churchpresenter.presenter.BibleBandPhase
import org.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.presenter.LocalBandOutgoing
import org.churchpresenter.presenter.LocalBandSongLineIndex
import org.churchpresenter.presenter.LocalLottieBandClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundSettings
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.fail
import org.churchpresenter.sharedui.models.Presenting

/**
 * What the band actually *looks* like over a transition, measured from its pixels.
 *
 * The state timeline in `TransitionTimelineTest` proves the driver runs one crossfade per change.
 * It cannot prove the band draws one, because the band's text goes into the Lottie composition and
 * the composition is rendered, not described. This suite closes that gap the only way that is
 * honest about it: render the real presenter through the real driver and count ink per frame.
 */
@OptIn(ExperimentalTestApi::class)
class BandPixelTimelineTest {

    private val dir = Files.createTempDirectory("band-pixels").toFile()
    private val template = quickBandTemplate(dir)

    private val john16 = SelectedVerse(
        bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved the world",
    )
    private val john17 = john16.copy(verseNumber = 17, verseText = "For God did not send his Son")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a verse change never blanks the band`() = runComposeUiTest {
        val profile = inkProfile { manager ->
            manager.setSelectedVerses(listOf(john17))
        }
        assertNeverBlank(profile)
    }

    /**
     * A second change landing mid-crossfade. This is what the operator does stepping quickly, and
     * it used to play two fades end to end; the text must still never disappear while it retargets.
     */
    @Test
    fun `a verse change during a crossfade never blanks the band`() = runComposeUiTest {
        val john18 = john16.copy(verseNumber = 18, verseText = "Whoever believes in him is not condemned")
        val profile = inkProfile { manager ->
            manager.setSelectedVerses(listOf(john17))
            repeat(MID_SWAP_FRAMES) { mainClock.advanceTimeByFrame() }
            manager.setSelectedVerses(listOf(john18))
        }
        assertNeverBlank(profile)
    }

    /**
     * The band drew nothing at all for the first frame of every text change: the incoming text is
     * fully transparent then by design, and the layer playing the old text out was created at that
     * same moment and did not draw on its first frame. One black frame, every change.
     *
     * The crossfade itself dips a little as the two texts cross, so the floor is a fraction of the
     * settled amount rather than "never changes" — what is being caught is a blank, not a wobble.
     */
    private fun assertNeverBlank(profile: InkProfile) {
        val ink = profile.ink
        val settled = ink.first()
        val floor = (settled * MIN_INK_FRACTION).toInt()
        val worst = ink.withIndex().minByOrNull { it.value }!!
        if (worst.value <= floor) fail(profile.describeFailure(worst.index, floor))
    }

    /**
     * What one run of the band looked like: the ink on every sampled frame, the band clock on each,
     * and how the wait for the first drawn verse ended.
     *
     * The clock and the first-ink figures are there for #677. The crossfade test has failed on CI
     * with every sample at zero -- the baseline frames before the change included, one frame after
     * the wait had seen ink -- and never locally, so a failure has to carry what the band was
     * doing, or there is nothing to diagnose it from.
     */
    private class InkProfile(
        val ink: List<Int>,
        val clocks: List<BibleBandClock>,
        val framesToFirstInk: Int,
        val inkAtFirstSight: Int,
    ) {
        fun describeFailure(worstFrame: Int, floor: Int): String = buildString {
            appendLine("the band lost its text at frame $worstFrame: ${ink[worstFrame]} ink, floor $floor")
            appendLine("band clock on that frame: ${clocks[worstFrame]}")
            appendLine(
                "the first verse was drawn after $framesToFirstInk frame(s), with $inkAtFirstSight ink; " +
                    "frames 0 to ${BASELINE - 1} are before the change, the rest after it",
            )
            ink.indices.forEach { frame -> appendLine("  frame $frame: ${ink[frame]} ink, ${clocks[frame]}") }
        }
    }

    /** Renders the band through the real driver and counts text pixels per frame while [change] runs. */
    private fun ComposeUiTest.inkProfile(change: ComposeUiTest.(PresenterManager) -> Unit): InkProfile {
        TestSingletons.latchSkikoHostOs()
        val manager = PresenterManager()
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(bibleLowerThirdBackground = lottieBackground(template)),
        )
        val ink = mutableListOf<Int>()
        val clocks = mutableListOf<BibleBandClock>()
        var framesToFirstInk = 0
        var inkAtFirstSight = 0

        mainClock.autoAdvance = false
        try {
            setContent {
                PresenterTransitionEffects(manager, settings)
                CompositionLocalProvider(
                    LocalLottieBandClock provides manager.lottieBandClock,
                    LocalBandSongLineIndex provides manager.bandSongLineIndex.value,
                    LocalBandOutgoing provides manager.bandOutgoing.value,
                ) {
                    Box(Modifier.size(WIDTH.dp, HEIGHT.dp).background(Color.Black).testTag(SURFACE)) {
                        BiblePresenter(
                            selectedVerses = manager.displayedVerses.value,
                            appSettings = settings,
                            isLowerThird = true,
                            transitionAlpha = manager.bibleTransitionAlpha.value,
                            // The band bar is opaque and fills its area, so with it drawn every
                            // frame counts the same. Hiding it leaves the text as the only ink,
                            // which is what a crossfade is actually about.
                            showBackground = false,
                        )
                    }
                }
            }

            fun sample() {
                ink += onNodeWithTag(SURFACE).captureToImage().toPixelMap().inkCount()
                clocks += manager.lottieBandClock.value
            }

            manager.setSelectedVerses(listOf(john16))
            manager.setPresentingMode(Presenting.BIBLE)
            advanceUntil("the band holds") { manager.lottieBandClock.value.phase == BibleBandPhase.HOLD }
            // The Lottie composition loads off the main thread, so settle until there is ink to
            // measure before treating a zero as meaningful.
            advanceUntil("the first verse is drawn") {
                framesToFirstInk++
                inkAtFirstSight = onNodeWithTag(SURFACE).captureToImage().toPixelMap().inkCount()
                inkAtFirstSight > 0
            }
            repeat(BASELINE) {
                mainClock.advanceTimeByFrame()
                sample()
            }
            change(manager)
            repeat(SAMPLES) {
                mainClock.advanceTimeByFrame()
                sample()
            }
        } finally {
            mainClock.autoAdvance = true
        }
        return InkProfile(ink, clocks, framesToFirstInk, inkAtFirstSight)
    }

    private companion object {
        const val SURFACE = "surface"
        const val WIDTH = 480
        const val HEIGHT = 270
        const val SAMPLES = 30
        const val BASELINE = 4
        const val MID_SWAP_FRAMES = 3

        /** How far the ink may legitimately dip while two texts cross. Below this it has gone. */
        const val MIN_INK_FRACTION = 0.4f
    }
}

/** How many pixels are not the black backdrop — a stand-in for "how much band and text is drawn". */
private fun PixelMap.inkCount(): Int {
    var count = 0
    for (y in 0 until height step 2) {
        for (x in 0 until width step 2) {
            val c = this[x, y]
            if (c.red > 0.08f || c.green > 0.08f || c.blue > 0.08f) count++
        }
    }
    return count
}
