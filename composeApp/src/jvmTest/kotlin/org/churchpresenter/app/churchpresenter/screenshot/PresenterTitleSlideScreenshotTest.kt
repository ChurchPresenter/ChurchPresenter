@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.viewmodel.titleSlideSection
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.SongCreditStyle
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * The title slide a song opens on, on a full-screen output -- every state the settings can put it
 * in. Split out of [PresenterFullScreenScreenshotTest], which is the lyric slides' and was already
 * at the size detekt allows a class.
 *
 * Built by [titleSlideSection], exactly as the songs tab builds it, rather than by turning
 * `titleSlideEnabled` on, which is the switch that makes the *tab* put this section in front of the
 * verses. Each line is drawn in its own element's profile -- the number's, the title's, and the
 * four credits' -- which is what most of these states are there to show. The band's own states are
 * in [PresenterLowerThirdScreenshotTest]. Single images, like every presenter shot: the audience
 * screen does not follow the operator's theme.
 */
class PresenterTitleSlideScreenshotTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun shoot(name: String, content: @Composable () -> Unit) = runComposeUiTest {
        setContent { MaterialTheme { Box(screen) { content() } } }
        waitForIdle()
        capture(name)
    }

    private fun ComposeUiTest.capture(name: String) {
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$SECTION/$name.png")
    }

    /**
     * The slide a song opens on: its number ahead of its title on one line, the credits under.
     *
     * Built by [titleSlideSection], exactly as the songs tab builds it, rather than by turning
     * `titleSlideEnabled` on, which is the switch that makes the *tab* put this section in front of
     * the verses. Each line is drawn in its own element's profile: the title bold and coloured here,
     * the number in the number's own plain white, so the picture shows the two are not one style.
     */
    @Test
    fun `the intro slide`() = shoot("intro_slide") {
        SongPresenter(
            lyricSection = introSlide(),
            appSettings = titleSlideSettings(titleBold = true, titleColor = "#FFD54F"),
        )
    }

    /** Every element on, both languages, the licence and the tempo in their quieter defaults. */
    @Test
    fun `the intro slide with every credit`() = shoot("intro_slide_everything") {
        SongPresenter(
            lyricSection = fullIntroSlide(),
            appSettings = titleSlideSettings(),
            languageOverride = Constants.SONG_LANG_BOTH,
        )
    }

    /** The second language alone: the translated title takes the primary's place. */
    @Test
    fun `the intro slide in the second language`() = shoot("intro_slide_secondary") {
        SongPresenter(
            lyricSection = fullIntroSlide(),
            appSettings = titleSlideSettings(),
            languageOverride = Constants.SONG_LANG_SECONDARY,
        )
    }

    /** The number on a row of its own, in its own right-aligned style, the block at the top. */
    @Test
    fun `the intro slide with the number on its own row`() = shoot("intro_slide_number_own_row") {
        SongPresenter(
            lyricSection = fullIntroSlide(),
            appSettings = titleSlideSettings(
                titleSlideNumberBeforeTitle = false,
                titleSlideVerticalAlignment = Constants.TOP,
                songNumberColor = "#7CFC00",
            ),
        )
    }

    /** A long title wraps under the number as one paragraph; the block sits at the bottom. */
    @Test
    fun `the intro slide with a long title`() = shoot("intro_slide_long_title") {
        SongPresenter(
            lyricSection = fullIntroSlide().copy(
                title = "Great Is Thy Faithfulness, O God My Father",
                secondaryTitle = "Велика верность Твоя, о Боже, Отец наш",
            ),
            appSettings = titleSlideSettings(
                titleSlideVerticalAlignment = Constants.BOTTOM,
                titleTransform = Constants.TEXT_TRANSFORM_UPPERCASE,
                titleBold = true,
            ),
            languageOverride = Constants.SONG_LANG_BOTH,
        )
    }

    /** The credits restyled: a coloured italic author, a large composer, the title left-aligned. */
    @Test
    fun `the intro slide with styled credits`() = shoot("intro_slide_styled_credits") {
        SongPresenter(
            lyricSection = fullIntroSlide(),
            appSettings = titleSlideSettings(
                titleHorizontalAlignment = Constants.LEFT,
                songNumberHorizontalAlignment = Constants.LEFT,
                titleSlideAuthor = SongCreditStyle(
                    color = "#FFD54F",
                    italic = true,
                    horizontalAlignment = Constants.LEFT,
                ),
                titleSlideComposer = SongCreditStyle(fontSize = 60, bold = true, horizontalAlignment = Constants.LEFT),
                titleSlideCcli = SongCreditStyle(
                    transform = Constants.TEXT_TRANSFORM_UPPERCASE,
                    letterSpacing = 6,
                    horizontalAlignment = Constants.LEFT,
                ),
                titleSlideTempo = SongCreditStyle(shadow = true, horizontalAlignment = Constants.LEFT),
            ),
        )
    }

    /** The key signal: every element white, whatever colour it was given. */
    @Test
    fun `the intro slide's key signal`() = shoot("intro_slide_key") {
        SongPresenter(
            lyricSection = fullIntroSlide(),
            appSettings = titleSlideSettings(titleColor = "#FFD54F", songNumberColor = "#7CFC00"),
            outputRole = Constants.OUTPUT_ROLE_KEY,
        )
    }

    @Test
    fun `the intro slide over a photograph`() = shoot("intro_slide_on_image") {
        SongPresenter(
            lyricSection = introSlide(),
            appSettings = titleSlideSettings().copy(
                backgroundSettings = BackgroundSettings(songBackground = imageBackground()),
            ),
        )
    }

    /** The song with every credit filled in, so each of the slide's elements has something to draw. */
    private fun fullIntroSlide() = titleSlideSection(
        SongItem(
            number = "427",
            title = "Amazing Grace",
            secondaryTitle = "О, благодать",
            author = "John Newton",
            composer = "William Walker",
            ccliNumber = "22025",
        ),
        SongTuning(bpm = 84),
        titleSlideSettings().songSettings,
    )

    /** The title slide's own switches on top of the defaults, and whatever else a state restyles. */
    @Suppress("LongParameterList")
    private fun titleSlideSettings(
        titleSlideNumberBeforeTitle: Boolean = true,
        titleSlideVerticalAlignment: String = Constants.MIDDLE,
        titleColor: String = SongSettings().titleColor,
        titleBold: Boolean = false,
        titleTransform: String = Constants.TEXT_TRANSFORM_NONE,
        titleHorizontalAlignment: String = Constants.CENTER,
        songNumberColor: String = SongSettings().songNumberColor,
        songNumberHorizontalAlignment: String = SongSettings().songNumberHorizontalAlignment,
        titleSlideAuthor: SongCreditStyle = SongSettings().titleSlideAuthor,
        titleSlideComposer: SongCreditStyle = SongSettings().titleSlideComposer,
        titleSlideCcli: SongCreditStyle = SongSettings().titleSlideCcli,
        titleSlideTempo: SongCreditStyle = SongSettings().titleSlideTempo,
    ) = AppSettings(
        songSettings = SongSettings(
            titleSlideEnabled = true,
            titleSlideShowCcli = true,
            titleSlideShowTempo = true,
            titleSlideNumberBeforeTitle = titleSlideNumberBeforeTitle,
            titleSlideVerticalAlignment = titleSlideVerticalAlignment,
            titleColor = titleColor,
            titleBold = titleBold,
            titleTransform = titleTransform,
            titleHorizontalAlignment = titleHorizontalAlignment,
            songNumberColor = songNumberColor,
            songNumberHorizontalAlignment = songNumberHorizontalAlignment,
            titleSlideAuthor = titleSlideAuthor,
            titleSlideComposer = titleSlideComposer,
            titleSlideCcli = titleSlideCcli,
            titleSlideTempo = titleSlideTempo,
        ),
    )

    /** The slide a song opens on: title and number, no lyrics — as the songs tab builds it. */
    private fun introSlide() = titleSlideSection(
        SongItem(number = "42", title = "Amazing Grace", author = "John Newton"),
        SongTuning(),
    )

    private fun imageBackground() =
        BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = photo().absolutePath)

    /** A real, decodable image for the image-background state -- the lyric suite's own backdrop. */
    private fun photo(): File {
        FIXTURES.mkdirs()
        val file = File(FIXTURES, "backdrop.png")
        val image = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB)
        val canvas = image.createGraphics()
        canvas.paint = GradientPaint(0f, 0f, Color(0x2B3A67), 1920f, 1080f, Color(0x8FB3F5))
        canvas.fillRect(0, 0, 1920, 1080)
        canvas.color = Color(0x1B2A5B)
        canvas.fillOval(1300, 120, 420, 420)
        canvas.dispose()
        ImageIO.write(image, "png", file)
        return file
    }

    private companion object {
        const val SECTION = "presenterTitleSlide"
        val FIXTURES = File("build/screenshot-fixtures/presenter")
    }
}
