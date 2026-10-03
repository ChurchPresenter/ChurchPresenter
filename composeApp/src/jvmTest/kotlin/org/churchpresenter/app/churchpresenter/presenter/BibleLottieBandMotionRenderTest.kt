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
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.PresenterTransitionEffects
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.lottiegen.band.BandTextAlign
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.sharedui.models.Presenting
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BibleLottieBandMotionRenderTest {

    private val dir = Files.createTempDirectory("lottie-band-motion").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun verse(text: String, abbreviation: String, fileName: String) = SelectedVerse(
        translationFileName = fileName,
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
    )

    private fun bandDraws(
        animation: TextAnimation = TextAnimation.FADE,
        align: BandTextAlign = BandTextAlign.FOLLOW_SETTINGS,
        italic: Boolean = false,
    ) = runComposeUiTest {
        TestSingletons.latchSkikoHostOs()
        val template = LottieBandTestSupport.writeTemplate(
            dir,
            cfg = BibleLottieGenConfig(
                canvasW = 1920, canvasH = 194, layout = SlotLayout.SIDE_BY_SIDE,
                textAnimation = animation, textAlign = align, referenceAlign = align,
                bgInSeconds = 0.1f, textInSeconds = 0.1f, holdSeconds = 0.2f,
                textOutSeconds = 0.1f, bgOutSeconds = 0.1f,
            ),
        )
        val manager = PresenterManager()
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(bibleLowerThirdBackground = lottieBackground(template)),
            bibleSettings = BibleSettings().withTranslations(
                listOf(
                    BibleTranslationSettings(fileName = "kjv.spb", lowerThirdTextItalic = italic),
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
                    Box(Modifier.size(960.dp, 540.dp).background(Color.Black).testTag(SURFACE)) {
                        BiblePresenter(
                            selectedVerses = manager.displayedVerses.value,
                            appSettings = settings,
                            isLowerThird = true,
                            transitionAlpha = manager.bibleTransitionAlpha.value,
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
            advanceUntil("the band is drawn") { onNodeWithTag(SURFACE).captureToImage().toPixelMap().inkCount() > 0 }

            assertTrue(onNodeWithTag(SURFACE).captureToImage().toPixelMap().inkCount() > 0)
        } finally {
            mainClock.autoAdvance = true
        }
    }

    @Test
    fun `a ticker band draws`() = bandDraws(animation = TextAnimation.TICKER)

    @Test
    fun `a band pinned left draws`() = bandDraws(align = BandTextAlign.LEFT, italic = true)

    @Test
    fun `a band pinned right draws`() = bandDraws(align = BandTextAlign.RIGHT)

    @Test
    fun `a band pinned centre draws`() = bandDraws(align = BandTextAlign.CENTER)

    private companion object {
        const val SURFACE = "surface"
    }
}

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
