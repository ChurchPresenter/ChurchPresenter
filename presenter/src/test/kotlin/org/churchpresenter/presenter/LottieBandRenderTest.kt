package org.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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
import org.churchpresenter.lottiegen.band.BandTextAlign
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LottieBandRenderTest {

    private val dir = Files.createTempDirectory("lottie-band-render").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun template(
        animation: TextAnimation = TextAnimation.FADE,
        align: BandTextAlign = BandTextAlign.FOLLOW_SETTINGS,
    ): File = LottieBandTestSupport.writeTemplate(
        dir,
        cfg = BibleLottieGenConfig(
            canvasW = 1920, canvasH = 194, layout = SlotLayout.SIDE_BY_SIDE,
            textAnimation = animation, textAlign = align, referenceAlign = align,
        ),
    )

    private fun band(file: File) =
        BackgroundConfig(backgroundType = Constants.BACKGROUND_LOTTIE, backgroundLottie = file.path)

    private fun verse(text: String, abbreviation: String) = SelectedVerse(
        translationFileName = "${abbreviation.lowercase()}.spb",
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
    )

    private val verses = listOf(verse("For God so loved the world", "KJV"), verse("Ибо так возлюбил Бог мир", "RST"))

    private fun bibleSettings(file: File, italic: Boolean = false) = AppSettings(
        backgroundSettings = BackgroundSettings(bibleLowerThirdBackground = band(file)),
        bibleSettings = BibleSettings().withTranslations(
            listOf(
                BibleTranslationSettings(fileName = "kjv.spb", lowerThirdTextItalic = italic),
                BibleTranslationSettings(fileName = "rst.spb"),
            ),
        ),
    )

    private fun ComposeUiTest.drawsInk(
        clock: BibleBandClock = BibleBandClock(),
        outgoing: BandOutgoing = BandOutgoing(),
        content: @Composable () -> Unit,
    ) {
        TestSingletons.latchSkikoHostOs()
        setContent {
            CompositionLocalProvider(
                LocalLottieBandClock provides mutableStateOf(clock),
                LocalBandOutgoing provides outgoing,
            ) {
                Box(Modifier.size(960.dp, 540.dp).background(Color.Black).testTag(SURFACE)) { content() }
            }
        }
        waitUntil("the band is drawn", timeoutMillis = 5_000) {
            onNodeWithTag(SURFACE).captureToImage().toPixelMap().inkCount() > 0
        }
    }

    private fun bibleBandDraws(file: File, italic: Boolean = false) = runComposeUiTest {
        drawsInk {
            BiblePresenter(selectedVerses = verses, appSettings = bibleSettings(file, italic), isLowerThird = true)
        }
    }

    @Test
    fun `a Bible band holding its verse draws`() = bibleBandDraws(template())

    @Test
    fun `a ticker band draws`() = bibleBandDraws(template(animation = TextAnimation.TICKER))

    @Test
    fun `a band pinned left draws`() = bibleBandDraws(template(align = BandTextAlign.LEFT), italic = true)

    @Test
    fun `a band pinned right draws`() = bibleBandDraws(template(align = BandTextAlign.RIGHT))

    @Test
    fun `a band pinned centre draws`() = bibleBandDraws(template(align = BandTextAlign.CENTER))

    @Test
    fun `a band mid-swap draws the outgoing verse over the incoming one`() = runComposeUiTest {
        val settings = bibleSettings(template())
        drawsInk(
            clock = BibleBandClock(BibleBandPhase.TEXT_SWAP, 0.5f),
            outgoing = BandOutgoing(verses = listOf(verse("In the beginning", "KJV"))),
        ) {
            BiblePresenter(selectedVerses = verses, appSettings = settings, isLowerThird = true)
        }
    }

    @Test
    fun `a song band draws its lyric`() = runComposeUiTest {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(songLowerThirdBackground = band(template())),
        )
        drawsInk {
            SongPresenter(
                lyricSection = section("Amazing grace, how sweet the sound", "That saved a wretch like me"),
                appSettings = settings,
                isLowerThird = true,
            )
        }
    }

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
