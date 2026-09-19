package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.PresenterTransitionEffects
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The exact live scenario reported after PR #564: a Lottie-backed lower third, a screen limited to
 * one Bible translation via a per-screen override (`bibleTranslations = [0]`), on a narrow/portrait
 * canvas (400x800, the dev fallback window's shape) — the combination [BibleLottieBandBoxTest]
 * never exercises (no full render) and [BiblePresenterLayoutRenderTest]'s override test never
 * exercises (1920x1080, non-Lottie background).
 *
 * **Cannot assert with `onNodeWithText`**: the band's text is painted into the Lottie composition
 * through Compottie's dynamic text layers, not drawn as a Compose `Text` node — see
 * `TransitionTimelineSupport.kt`'s class doc. This measures ink the same way
 * [BandPixelTimelineTest] does.
 */
@OptIn(ExperimentalTestApi::class)
class LottieBandPortraitOverrideTest {

    private val dir = Files.createTempDirectory("lottie-band-portrait-override").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun sideBySideTemplate(): File = LottieBandTestSupport.writeTemplate(
        dir,
        cfg = BibleLottieGenConfig(
            canvasW = 1920, canvasH = 194, layout = SlotLayout.SIDE_BY_SIDE,
            bgInSeconds = 0.1f, textInSeconds = 0.1f, holdSeconds = 0.2f,
            textOutSeconds = 0.1f, bgOutSeconds = 0.1f,
        ),
    )

    private fun verse(text: String, abbreviation: String, fileName: String) = SelectedVerse(
        translationFileName = fileName,
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
    )

    @Test
    fun `a Bible screen overridden to one translation still draws ink on a portrait canvas`() = runComposeUiTest {
        TestSingletons.latchSkikoHostOs()
        val template = sideBySideTemplate()
        val manager = PresenterManager()
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(bibleLowerThirdBackground = lottieBackground(template)),
            bibleSettings = BibleSettings().withTranslations(
                listOf(
                    BibleTranslationSettings(fileName = "kjv.spb"),
                    BibleTranslationSettings(fileName = "rst.spb"),
                ),
            ),
        )

        mainClock.autoAdvance = false
        try {
            setContent {
                PresenterTransitionEffects(manager, settings)
                CompositionLocalProvider(
                    LocalLottieBandClock provides manager.lottieBandClock,
                    LocalBandSongLineIndex provides manager.bandSongLineIndex.value,
                    LocalBandOutgoing provides manager.bandOutgoing.value,
                ) {
                    Box(Modifier.size(PORTRAIT_W.dp, PORTRAIT_H.dp).background(Color.Black).testTag(SURFACE)) {
                        BiblePresenter(
                            selectedVerses = manager.displayedVerses.value,
                            appSettings = settings,
                            isLowerThird = true,
                            transitionAlpha = manager.bibleTransitionAlpha.value,
                            bibleTranslations = listOf(0),
                        )
                    }
                }
            }

            manager.setSelectedVerses(
                listOf(
                    verse("For God so loved the world", "KJV", "kjv.spb"),
                    verse("Ибо так возлюбил Бог мир", "RST", "rst.spb"),
                ),
            )
            manager.setPresentingMode(Presenting.BIBLE)
            advanceUntil("the band holds") { manager.lottieBandClock.value.phase == BibleBandPhase.HOLD }
            advanceUntil("the surviving translation is drawn") {
                onNodeWithTag(SURFACE).captureToImage().toPixelMap().inkCount() > 0
            }

            val ink = onNodeWithTag(SURFACE).captureToImage().toPixelMap().inkCount()
            assertTrue(ink > 0, "the portrait canvas must show the surviving translation's ink, got $ink")
        } finally {
            mainClock.autoAdvance = true
        }
    }

    @Test
    fun `a Song screen overridden to one language still draws ink on a portrait canvas`() = runComposeUiTest {
        TestSingletons.latchSkikoHostOs()
        val template = sideBySideTemplate()
        val manager = PresenterManager()
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(songLowerThirdBackground = lottieBackground(template)),
        )

        mainClock.autoAdvance = false
        try {
            setContent {
                PresenterTransitionEffects(manager, settings)
                CompositionLocalProvider(
                    LocalLottieBandClock provides manager.lottieBandClock,
                    LocalBandSongLineIndex provides manager.bandSongLineIndex.value,
                    LocalBandOutgoing provides manager.bandOutgoing.value,
                ) {
                    Box(Modifier.size(PORTRAIT_W.dp, PORTRAIT_H.dp).background(Color.Black).testTag(SURFACE)) {
                        SongPresenter(
                            lyricSection = manager.displayedLyricSection.value,
                            appSettings = settings,
                            isLowerThird = true,
                            transitionAlpha = manager.songTransitionAlpha.value,
                            languageOverride = Constants.SONG_LANG_PRIMARY,
                        )
                    }
                }
            }

            manager.setLyricSection(
                LyricSection(
                    type = "verse",
                    lines = listOf("Amazing grace, how sweet the sound"),
                    secondaryLines = listOf("Дивная благодать"),
                ),
            )
            manager.setPresentingMode(Presenting.LYRICS)
            advanceUntil("the band holds") { manager.lottieBandClock.value.phase == BibleBandPhase.HOLD }
            advanceUntil("the surviving language is drawn") {
                onNodeWithTag(SURFACE).captureToImage().toPixelMap().inkCount() > 0
            }

            val ink = onNodeWithTag(SURFACE).captureToImage().toPixelMap().inkCount()
            assertTrue(ink > 0, "the portrait canvas must show the surviving language's ink, got $ink")
        } finally {
            mainClock.autoAdvance = true
        }
    }

    private companion object {
        const val SURFACE = "surface"
        const val PORTRAIT_W = 400
        const val PORTRAIT_H = 800
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
