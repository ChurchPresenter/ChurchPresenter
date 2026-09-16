package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.PresenterTransitionEffects
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the text does over the course of every kind of transition, frame by frame.
 *
 * The suite exists because the faults being chased — a band that fades twice for one verse change,
 * and one that shows the previous line for a moment before settling — are both invisible to an
 * assertion on the end state, and one of them is invisible to an assertion on the list of phases
 * too. See `TransitionTimelineSupport.kt` for what each assertion detects and for the one thing
 * this harness cannot see: the band's text is painted into the Lottie composition rather than into
 * Compose text nodes, so these tests read the state the band renders from, not its pixels.
 */
@OptIn(ExperimentalTestApi::class)
class TransitionTimelineTest {

    private val dir = Files.createTempDirectory("transition-timeline").toFile()
    private val template = quickBandTemplate(dir)

    private val john16 = SelectedVerse(
        bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved the world",
    )
    private val john17 = john16.copy(verseNumber = 17, verseText = "For God did not send his Son")

    private val verse1 = section("Amazing grace how sweet the sound", "That saved a wretch like me")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun bandSettings(bible: Boolean = false, song: Boolean = false) = AppSettings(
        backgroundSettings = BackgroundSettings(
            bibleLowerThirdBackground = if (bible) lottieBackground(template) else BackgroundConfig(),
            songLowerThirdBackground = if (song) lottieBackground(template) else BackgroundConfig(),
        ),
    )

    @Test
    fun `a bible verse change on the lottie band crossfades exactly once`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = bandSettings(bible = true)
        val timeline = recordTimeline(
            manager = manager,
            probe = bibleProbe(manager),
            content = { PresenterTransitionEffects(manager, settings) },
        ) {
            manager.setSelectedVerses(listOf(john16))
            manager.setPresentingMode(Presenting.BIBLE)
            advanceUntil("the band holds the first verse") {
                manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
            }
            manager.setSelectedVerses(listOf(john17))
            advanceUntil("the band holds the second verse") {
                manager.displayedVerses.value == listOf(john17) &&
                    manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
            }
        }

        timeline.assertSwapRuns(1)
        timeline.assertProgressNeverRestarts()
        timeline.assertTextNeverReverts()
        timeline.assertOnlyTextsInFlight(john16.verseText, john17.verseText)
        timeline.assertOutgoingOnlyWhileSwapping()
    }

    @Test
    fun `a song line change inside one section crossfades exactly once`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = bandSettings(song = true)
        val timeline = recordTimeline(
            manager = manager,
            probe = songProbe(manager),
            content = { PresenterTransitionEffects(manager, settings) },
        ) {
            manager.setLyricSection(verse1)
            manager.setSongDisplayLineIndex(0)
            manager.setPresentingMode(Presenting.LYRICS)
            advanceUntil("the band holds the first line") {
                manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
            }
            manager.setSongDisplayLineIndex(1)
            advanceUntil("the band holds the second line") {
                manager.bandSongLineIndex.value == 1 && manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
            }
        }

        timeline.assertSwapRuns(1)
        timeline.assertProgressNeverRestarts()
        timeline.assertTextNeverReverts()
        timeline.assertOnlyTextsInFlight(verse1.lines[0], verse1.lines[1])
        timeline.assertOutgoingOnlyWhileSwapping()
    }

    /**
     * The template's crossfade length governs how long the words take to change.
     *
     * This is what the band generator's Crossfade slider writes, and it looked broken for a
     * different reason — the running app never re-read the saved file. The length itself was always
     * wired; nothing asserted it, so nothing would have said so.
     */
    @Test
    fun `the template's crossfade length sets how long the swap takes`() = runComposeUiTest {
        fun swapFrames(swapSeconds: Float): Int {
            val band = LottieBandTestSupport.writeTemplate(
                dir,
                name = "swap-$swapSeconds.json",
                cfg = BibleLottieGenConfig(canvasW = 960, canvasH = 180, swapSeconds = swapSeconds),
            )
            val manager = PresenterManager()
            val settings = AppSettings(
                backgroundSettings = BackgroundSettings(bibleLowerThirdBackground = lottieBackground(band)),
            )
            val timeline = recordTimeline(
                manager = manager,
                probe = bibleProbe(manager),
                content = { PresenterTransitionEffects(manager, settings) },
            ) {
                manager.setSelectedVerses(listOf(john16))
                manager.setPresentingMode(Presenting.BIBLE)
                advanceUntil("the band holds") { manager.lottieBandClock.value.phase == BibleBandPhase.HOLD }
                manager.setSelectedVerses(listOf(john17))
                advanceUntil("the swap finished") {
                    manager.displayedVerses.value == listOf(john17) &&
                        manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
                }
            }
            return timeline.frames.count { it.phase == BibleBandPhase.TEXT_SWAP }
        }

        val brief = swapFrames(BRIEF_SWAP_SECONDS)
        val long = swapFrames(LONG_SWAP_SECONDS)
        assertTrue(
            long > brief * SWAP_RATIO_FLOOR,
            "a ${LONG_SWAP_SECONDS}s crossfade spent $long frames swapping and a " +
                "${BRIEF_SWAP_SECONDS}s one spent $brief — the length is not reaching the band",
        )
    }

    /**
     * With the band showing the whole section, the selected line changes nothing on the output, so
     * it must not start a crossfade. Swapping here fades the section to an identical copy of
     * itself — a flicker with no content change behind it at all.
     */
    @Test
    fun `picking a line while the band shows the whole section does not crossfade`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = AppSettings(
            songSettings = SongSettings(lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE),
            backgroundSettings = BackgroundSettings(songLowerThirdBackground = lottieBackground(template)),
        )
        val timeline = recordTimeline(
            manager = manager,
            probe = songProbe(manager),
            content = { PresenterTransitionEffects(manager, settings) },
        ) {
            manager.setLyricSection(verse1)
            manager.setSongDisplayLineIndex(0)
            manager.setPresentingMode(Presenting.LYRICS)
            advanceUntil("the band holds the section") {
                manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
            }
            manager.setSongDisplayLineIndex(1)
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        }

        timeline.assertSwapRuns(0)
        timeline.assertProgressNeverRestarts()
    }

    /**
     * The write sequence `SongsTab.sendToPresenter` actually makes — the sections, the section
     * index, the line index and then the section itself, which bumps the version every time even
     * when the section is unchanged. The synthetic line-only change above does not exercise it.
     */
    @Test
    fun `the songs tab's own push sequence crossfades exactly once`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = bandSettings(song = true)
        fun push(line: Int) {
            manager.setAllLyricSections(listOf(verse1))
            manager.setSongDisplaySectionIndex(0)
            manager.setSongDisplayLineIndex(line)
            manager.setLyricSection(verse1)
        }
        val timeline = recordTimeline(
            manager = manager,
            probe = songProbe(manager),
            content = { PresenterTransitionEffects(manager, settings) },
        ) {
            push(line = 0)
            manager.setPresentingMode(Presenting.LYRICS)
            advanceUntil("the band holds the first line") {
                manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
            }
            push(line = 1)
            advanceUntil("the band holds the second line") {
                manager.bandSongLineIndex.value == 1 && manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
            }
        }

        timeline.assertSwapRuns(1)
        timeline.assertProgressNeverRestarts()
        timeline.assertTextNeverReverts()
        timeline.assertOnlyTextsInFlight(verse1.lines[0], verse1.lines[1])
    }

    /**
     * A change that lands while the band is still crossfading the previous one.
     *
     * The fade in flight finishes and the band settles before the next one starts. Cutting it off
     * and retargeting instead reads as the words jumping part-way through a fade, which is what an
     * operator stepping quickly sees. Two deliberate changes are two crossfades; what must never
     * happen is two for a single change, which the other tests here cover.
     */
    @Test
    fun `a verse change during a crossfade finishes it before starting the next`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = bandSettings(bible = true)
        val john18 = john16.copy(verseNumber = 18, verseText = "Whoever believes in him is not condemned")
        val timeline = recordTimeline(
            manager = manager,
            probe = bibleProbe(manager),
            content = { PresenterTransitionEffects(manager, settings) },
        ) {
            manager.setSelectedVerses(listOf(john16))
            manager.setPresentingMode(Presenting.BIBLE)
            advanceUntil("the band holds the first verse") {
                manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
            }
            manager.setSelectedVerses(listOf(john17))
            advanceUntil("the crossfade is under way") {
                manager.lottieBandClock.value.phase == BibleBandPhase.TEXT_SWAP
            }
            manager.setSelectedVerses(listOf(john18))
            advanceUntil("the band holds the last verse") {
                manager.displayedVerses.value == listOf(john18) &&
                    manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
            }
        }

        timeline.assertSwapRuns(2)
        timeline.assertEachSwapCompletes()
        timeline.assertProgressNeverRestarts()
        timeline.assertTextNeverReverts()
    }

    /** A split verse's second page is a text change like any other, and must fade once. */
    @Test
    fun `stepping to the second page of one verse crossfades exactly once`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = bandSettings(bible = true)
        val page1 = john16.copy(verseText = "For God so loved the world", verseRange = "16a")
        val page2 = john16.copy(verseText = "that he gave his only Son", verseRange = "16b")
        val timeline = recordTimeline(
            manager = manager,
            probe = bibleProbe(manager),
            content = { PresenterTransitionEffects(manager, settings) },
        ) {
            manager.setSelectedVerses(listOf(page1))
            manager.setPresentingMode(Presenting.BIBLE)
            advanceUntil("the band holds the first page") {
                manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
            }
            manager.setSelectedVerses(listOf(page2))
            advanceUntil("the band holds the second page") {
                manager.displayedVerses.value == listOf(page2) &&
                    manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
            }
        }

        timeline.assertSwapRuns(1)
        timeline.assertProgressNeverRestarts()
        timeline.assertTextNeverReverts()
        timeline.assertOnlyTextsInFlight(page1.verseText, page2.verseText)
        timeline.assertOutgoingOnlyWhileSwapping()
    }

    @Test
    fun `going live plays one entrance and never crossfades`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = bandSettings(bible = true)
        val timeline = recordTimeline(
            manager = manager,
            probe = bibleProbe(manager),
            content = { PresenterTransitionEffects(manager, settings) },
        ) {
            manager.setSelectedVerses(listOf(john16))
            manager.setPresentingMode(Presenting.BIBLE)
            advanceUntil("the band holds") { manager.lottieBandClock.value.phase == BibleBandPhase.HOLD }
        }

        timeline.assertSwapRuns(0)
        timeline.assertProgressNeverRestarts()
        timeline.assertTextNeverReverts()
        timeline.assertOutgoingOnlyWhileSwapping()
    }

    @Test
    fun `clearing plays the exit once and leaves nothing being played out`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = bandSettings(bible = true)
        val timeline = recordTimeline(
            manager = manager,
            probe = bibleProbe(manager),
            content = { PresenterTransitionEffects(manager, settings) },
        ) {
            manager.setSelectedVerses(listOf(john16))
            manager.setPresentingMode(Presenting.BIBLE)
            advanceUntil("the band holds") { manager.lottieBandClock.value.phase == BibleBandPhase.HOLD }
            manager.requestClearDisplay()
            advanceUntil("the display cleared") { manager.presentingMode.value == Presenting.NONE }
        }

        timeline.assertSwapRuns(0)
        timeline.assertProgressNeverRestarts()
        timeline.assertOutgoingOnlyWhileSwapping()
    }

    // -----------------------------------------------------------------------------------------
    // The classic band and full screen. Neither uses the Lottie clock — they fade through
    // bibleTransitionAlpha / songTransitionAlpha and through the presenters' own crossfade — so
    // the assertions that apply are the ones about the text, plus "no band clock ever moved".
    // -----------------------------------------------------------------------------------------

    @Test
    fun `without a band a bible verse change swaps the text and moves no clock`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = AppSettings()
        val timeline = recordTimeline(
            manager = manager,
            probe = bibleProbe(manager),
            content = { PresenterTransitionEffects(manager, settings) },
        ) {
            manager.setSelectedVerses(listOf(john16))
            manager.setPresentingMode(Presenting.BIBLE)
            advanceUntil("the first verse is live") { manager.displayedVerses.value == listOf(john16) }
            manager.setSelectedVerses(listOf(john17))
            advanceUntil("the second verse is live") { manager.displayedVerses.value == listOf(john17) }
        }

        timeline.assertSwapRuns(0)
        timeline.assertTextNeverReverts()
        timeline.assertOnlyTextsInFlight(john16.verseText, john17.verseText)
        timeline.assertOutgoingOnlyWhileSwapping()
    }

    @Test
    fun `without a band a song line change swaps the text and moves no clock`() = runComposeUiTest {
        val manager = PresenterManager()
        val settings = AppSettings()
        val timeline = recordTimeline(
            manager = manager,
            probe = songProbe(manager),
            content = { PresenterTransitionEffects(manager, settings) },
        ) {
            manager.setLyricSection(verse1)
            manager.setSongDisplayLineIndex(0)
            manager.setPresentingMode(Presenting.LYRICS)
            advanceUntil("the first line is live") { manager.bandSongLineIndex.value == 0 }
            manager.setSongDisplayLineIndex(1)
            advanceUntil("the second line is live") { manager.bandSongLineIndex.value == 1 }
        }

        timeline.assertSwapRuns(0)
        timeline.assertTextNeverReverts()
        timeline.assertOnlyTextsInFlight(verse1.lines[0], verse1.lines[1])
        timeline.assertOutgoingOnlyWhileSwapping()
    }

    private companion object {
        /** Long enough for a crossfade to have started if one were going to. */
        const val SETTLE_FRAMES = 20

        const val BRIEF_SWAP_SECONDS = 0.1f
        const val LONG_SWAP_SECONDS = 0.6f

        /** The two lengths differ 6x; 3x leaves room for the clock's 16 ms step at the short end. */
        const val SWAP_RATIO_FLOOR = 3
    }
}
