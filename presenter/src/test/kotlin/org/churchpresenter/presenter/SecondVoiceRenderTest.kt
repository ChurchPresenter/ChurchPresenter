package org.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongTextOverrides
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A song's second voice as the outputs draw it: its words in the voice's colour on full screen, on
 * the classic band and on a Lottie band, none of it on a key, and the song's own colours over the
 * profile's. Asserted on the pixels — the colour is the whole point of the markup.
 */
@OptIn(ExperimentalTestApi::class)
class SecondVoiceRenderTest {

    private val dir = Files.createTempDirectory("second-voice-render").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private val red = "#FF0000"

    private fun verse(vararg lines: String, look: SongTextOverrides = SongTextOverrides()) = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines.toList(),
        textOverrides = look,
    )

    private fun settings(background: BackgroundSettings = BackgroundSettings()) = AppSettings(
        songSettings = SongSettings(layoutExtras = SongLayoutExtras(secondVoiceColor = red)),
        backgroundSettings = background,
    )

    private fun lottieBand(): BackgroundSettings {
        val file = LottieBandTestSupport.writeTemplate(
            dir,
            cfg = BibleLottieGenConfig(
                canvasW = 1920, canvasH = 194, layout = SlotLayout.SINGLE, textAnimation = TextAnimation.FADE,
            ),
        )
        return BackgroundSettings(
            songLowerThirdBackground =
                BackgroundConfig(backgroundType = Constants.BACKGROUND_LOTTIE, backgroundLottie = file.path),
        )
    }

    private fun ComposeUiTest.draw(content: @Composable () -> Unit) {
        TestSingletons.latchSkikoHostOs()
        setContent {
            CompositionLocalProvider(LocalLottieBandClock provides mutableStateOf(BibleBandClock())) {
                Box(Modifier.size(960.dp, 540.dp).background(Color.Black).testTag(SURFACE)) { content() }
            }
        }
    }

    private fun ComposeUiTest.pixels(): PixelMap = onNodeWithTag(SURFACE).captureToImage().toPixelMap()

    private fun ComposeUiTest.waitFor(what: String, count: (PixelMap) -> Int) =
        waitUntil(what, timeoutMillis = 5_000) { count(pixels()) > 0 }

    // ── Full screen ─────────────────────────────────────────────────────────────

    @Test
    fun `a voice-2 line is drawn in the voice's colour beside the main voice`() = runComposeUiTest {
        draw { SongPresenter(lyricSection = verse("Amazing grace", "2>That saved a wretch"), appSettings = settings()) }

        waitFor("the second voice is drawn in its colour") { it.count(::isRed) }
        assertTrue(pixels().count(::isWhite) > 0, "the main voice keeps the lyric colour")
        onNodeWithText("That saved a wretch", substring = true).assertExists("the markup is not drawn")
    }

    @Test
    fun `a key output draws the second voice white`() = runComposeUiTest {
        draw {
            SongPresenter(
                lyricSection = verse("Amazing grace", "2>That saved a wretch"),
                appSettings = settings(),
                outputRole = Constants.OUTPUT_ROLE_KEY,
            )
        }

        waitFor("the key draws its matte") { it.count(::isWhite) }
        assertEquals(0, pixels().count(::isRed))
    }

    @Test
    fun `the song's own colours win over the profile's`() = runComposeUiTest {
        val look = SongTextOverrides(color = "#00FF00", secondVoiceColor = "#0000FF")
        draw {
            SongPresenter(lyricSection = verse("Amazing grace", "2>That saved", look = look), appSettings = settings())
        }

        waitFor("the song's text colour") { it.count(::isGreen) }
        waitFor("the song's voice-2 colour") { it.count(::isBlue) }
        assertEquals(0, pixels().count(::isRed), "the profile's voice-2 colour is not used")
    }

    // ── The classic band ────────────────────────────────────────────────────────

    @Test
    fun `a classic band in line mode shows a line and its answer, the answer coloured`() = runComposeUiTest {
        draw {
            SongPresenter(
                lyricSection = verse("Amazing grace", "2>That saved", "I once was lost"),
                appSettings = settings(),
                isLowerThird = true,
                displayLineIndex = 0,
            )
        }

        onNodeWithText("Amazing grace", substring = true).assertExists()
        onNodeWithText("That saved", substring = true).assertExists("the answer shows with the line")
        onNodeWithText("I once was lost", substring = true).assertDoesNotExist()
        waitFor("the answer in the voice's colour") { it.count(::isRed) }
    }

    // ── A Lottie band ───────────────────────────────────────────────────────────

    @Test
    fun `a Lottie band colours just the second voice's words`() = runComposeUiTest {
        draw {
            SongPresenter(
                lyricSection = verse("Was blind, but 2>(now I see)"),
                appSettings = settings(lottieBand()),
                isLowerThird = true,
                displayLineIndex = 0,
            )
        }

        waitFor("the voice pass draws its words") { it.count(::isReddish) }
        assertTrue(pixels().count(::isWhite) > 0, "the main voice's words stay white")
    }

    @Test
    fun `a Lottie band on a key has no voice pass`() = runComposeUiTest {
        draw {
            SongPresenter(
                lyricSection = verse("Was blind, but 2>(now I see)"),
                appSettings = settings(lottieBand()),
                isLowerThird = true,
                displayLineIndex = 0,
                outputRole = Constants.OUTPUT_ROLE_KEY,
            )
        }

        waitFor("the key band draws") { it.count(::isWhite) }
        assertEquals(0, pixels().count(::isReddish))
    }

    private companion object {
        const val SURFACE = "surface"
    }
}

private fun isRed(c: Color) = c.red > 0.7f && c.green < 0.3f && c.blue < 0.3f

// A band's lines are small, and Linux smooths their edges in grey, so no pixel of a red word is
// pure red there: red well ahead of green and blue is what marks one.
private fun isReddish(c: Color) = c.red > 0.6f && c.red - c.green > 0.3f && c.red - c.blue > 0.3f
private fun isGreen(c: Color) = c.green > 0.7f && c.red < 0.3f && c.blue < 0.3f
private fun isBlue(c: Color) = c.blue > 0.7f && c.red < 0.3f && c.green < 0.3f
private fun isWhite(c: Color) = c.red > 0.9f && c.green > 0.9f && c.blue > 0.9f

private fun PixelMap.count(test: (Color) -> Boolean): Int {
    var n = 0
    for (y in 0 until height step 2) {
        for (x in 0 until width step 2) {
            if (test(this[x, y])) n++
        }
    }
    return n
}
