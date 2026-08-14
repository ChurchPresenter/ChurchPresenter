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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color as ComposeColor
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.data.settings.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.DictionarySettings
import org.churchpresenter.app.churchpresenter.data.settings.QASettings
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundConfig
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.Question
import org.churchpresenter.app.churchpresenter.models.QuestionStatus
import org.churchpresenter.app.churchpresenter.models.Scene
import org.churchpresenter.app.churchpresenter.models.SceneSource
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.models.SourceTransform
import org.churchpresenter.app.churchpresenter.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.DictionaryPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.PresentationPresenter
import org.churchpresenter.app.churchpresenter.presenter.QAPresenter
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * What the congregation sees, full screen — every variation the two main surfaces have.
 *
 * **One image per state, not two.** The rest of the screenshot suite stacks a light and a dark render
 * of each state, because those surfaces follow the operator's theme. These do not: the audience
 * screen is drawn from `SongSettings`/`BibleSettings`/`BackgroundSettings` and looks the same
 * whichever theme the operator has chosen. Stacking would write the same picture twice.
 *
 * Rendered at 1920x1080, which is what these surfaces are drawn onto in practice — and it matters
 * here more than elsewhere, because auto-fit sizes the text against the space it is given.
 *
 * Lower-third variants live in `PresenterScreenshotTest`; this file is the full-screen half.
 */
class PresenterFullScreenScreenshotTest {

    /** A 1080p output. */
    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun shoot(name: String, content: @Composable () -> Unit) = runComposeUiTest {
        setContent { MaterialTheme { Box(screen) { content() } } }
        waitForIdle()
        capture(name)
    }

    private fun ComposeUiTest.capture(name: String) {
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$SECTION/$name.png")
    }

    // ── Songs: what is on the slide ─────────────────────────────────────────────────────────────

    @Test
    fun `a verse`() = shoot("song") { SongPresenter(lyricSection = song(), appSettings = AppSettings()) }

    @Test
    fun `a verse with its title and number`() = shoot("song_title_and_number") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(
                titleDisplay = Constants.EVERY_PAGE,
                titlePosition = Constants.ABOVE_VERSE,
                showNumber = Constants.EVERY_PAGE,
            ),
        )
    }

    @Test
    fun `neither title nor number`() = shoot("song_bare") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(titleDisplay = Constants.NONE, showNumber = Constants.NONE),
        )
    }

    @Test
    fun `a chorus`() = shoot("song_chorus") {
        SongPresenter(
            lyricSection = song(
                header = "{Chorus}",
                type = Constants.SECTION_TYPE_CHORUS,
                lines = listOf("Praise the Lord, praise the Lord", "Let the earth hear His voice"),
            ),
            appSettings = AppSettings(),
        )
    }

    /** Long enough that auto-fit has to shrink it to fit the frame. */
    @Test
    fun `a long verse`() = shoot("song_long_verse") {
        SongPresenter(lyricSection = song(lines = LONG_VERSE), appSettings = AppSettings())
    }

    /** One line at a time, which is what a congregation reading along gets. */
    @Test
    fun `one line at a time`() = shoot("song_line_by_line") {
        SongPresenter(
            lyricSection = song(lines = LONG_VERSE),
            appSettings = songSettings(fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_LINE),
            displayLineIndex = 1,
        )
    }

    @Test
    fun `the look-ahead the band sees`() = shoot("song_look_ahead") {
        SongPresenter(lyricSection = song(), appSettings = AppSettings(), lookAheadEnabled = true)
    }

    // ── Songs: two languages ────────────────────────────────────────────────────────────────────

    @Test
    fun `both languages, side by side`() = shoot("song_bilingual_side_by_side") {
        SongPresenter(
            lyricSection = song(secondary = SECONDARY_LINES),
            appSettings = songSettings(
                fullscreenLanguageDisplay = Constants.SONG_LANG_BOTH,
                bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE,
            ),
        )
    }

    @Test
    fun `both languages, stacked`() = shoot("song_bilingual_stacked") {
        SongPresenter(
            lyricSection = song(secondary = SECONDARY_LINES),
            appSettings = songSettings(
                fullscreenLanguageDisplay = Constants.SONG_LANG_BOTH,
                bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM,
            ),
        )
    }

    @Test
    fun `the second language alone`() = shoot("song_secondary_only") {
        SongPresenter(
            lyricSection = song(secondary = SECONDARY_LINES),
            appSettings = songSettings(fullscreenLanguageDisplay = Constants.SONG_LANG_SECONDARY),
        )
    }

    // ── Songs: how it is typeset ────────────────────────────────────────────────────────────────

    @Test
    fun `styled lyrics`() = shoot("song_styled") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(
                lyricsColor = "#FFD54F",
                lyricsBold = true,
                lyricsItalic = true,
                lyricsShadow = true,
            ),
        )
    }

    @Test
    fun `underlined lyrics`() =
        shoot("song_underlined") { SongPresenter(lyricSection = song(), appSettings = songSettings(lyricsUnderline = true)) }

    @Test
    fun `aligned left`() = shoot("song_align_left") {
        SongPresenter(lyricSection = song(), appSettings = songSettings(lyricsHorizontalAlignment = Constants.LEFT))
    }

    @Test
    fun `aligned right`() = shoot("song_align_right") {
        SongPresenter(lyricSection = song(), appSettings = songSettings(lyricsHorizontalAlignment = Constants.RIGHT))
    }

    @Test
    fun `sitting at the top`() = shoot("song_top") {
        SongPresenter(lyricSection = song(), appSettings = songSettings(lyricsAlignment = Constants.TOP))
    }

    @Test
    fun `sitting at the bottom`() = shoot("song_bottom") {
        SongPresenter(lyricSection = song(), appSettings = songSettings(lyricsAlignment = Constants.BOTTOM))
    }

    /** Auto-fit off, so the configured size stands whether or not it fits. */
    @Test
    fun `a fixed font size`() = shoot("song_fixed_size") {
        SongPresenter(
            lyricSection = song(lines = LONG_VERSE),
            appSettings = songSettings(lyricsFontSizeAutoFit = false, lyricsFontSize = 40),
        )
    }

    @Test
    fun `wide margins`() = shoot("song_wide_margins") {
        SongPresenter(
            lyricSection = song(lines = LONG_VERSE),
            appSettings = songSettings(marginLeft = 400, marginRight = 400, marginTop = 200, marginBottom = 200),
        )
    }

    // ── The title and number, which carry their own size and colour ─────────────────────────────
    // Independently configurable from the lyrics, and the commonest reason a slide reads badly is a
    // number or title set to the same weight as the words it sits beside.

    @Test
    fun `a number set apart from the lyrics`() = shoot("song_number_styled") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(
                showNumber = Constants.EVERY_PAGE,
                songNumberColor = "#FFD54F",
                songNumberFontSize = 140,
                songNumberBold = true,
            ),
        )
    }

    /** The number small and quiet at the other corner, rather than large in the default one. */
    @Test
    fun `a small number in the opposite corner`() = shoot("song_number_small") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(
                showNumber = Constants.EVERY_PAGE,
                songNumberColor = "#8A8A94",
                songNumberFontSize = 28,
                songNumberHorizontalAlignment = Constants.LEFT,
                songNumberPosition = Constants.ABOVE_VERSE,
            ),
        )
    }

    @Test
    fun `a title set apart from the lyrics`() = shoot("song_title_styled") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(
                titleDisplay = Constants.EVERY_PAGE,
                titlePosition = Constants.ABOVE_VERSE,
                titleColor = "#90CAF9",
                titleFontSize = 44,
                titleBold = true,
            ),
        )
    }

    /** A number large enough to run out of its corner — what overflow does to it. */
    @Test
    fun `a number too large for its corner`() = shoot("song_number_overflow") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(
                showNumber = Constants.EVERY_PAGE,
                songNumberFontSize = 400,
                songNumberColor = "#FFD54F",
            ),
        )
    }

    /** A title longer than the frame at a size that will not shrink. */
    @Test
    fun `a title too long for the frame`() = shoot("song_title_overflow") {
        SongPresenter(
            lyricSection = song().copy(title = LONG_TITLE),
            appSettings = songSettings(
                titleDisplay = Constants.EVERY_PAGE,
                titlePosition = Constants.ABOVE_VERSE,
                titleFontSize = 120,
            ),
        )
    }

    /** Title and number over a photograph with their own shadows — the legibility case. */
    @Test
    fun `title and number shadowed over a photograph`() = shoot("song_title_number_shadow_on_image") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(
                titleDisplay = Constants.EVERY_PAGE,
                titlePosition = Constants.ABOVE_VERSE,
                titleColor = "#FFD54F",
                titleShadow = true,
                showNumber = Constants.EVERY_PAGE,
                songNumberShadow = true,
                lyricsShadow = true,
            ).copy(backgroundSettings = BackgroundSettings(songBackground = imageBackground())),
        )
    }

    /** The same over a photograph with every shadow off, for the comparison. */
    @Test
    fun `title and number unshadowed over a photograph`() = shoot("song_title_number_on_image") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(
                titleDisplay = Constants.EVERY_PAGE,
                titlePosition = Constants.ABOVE_VERSE,
                titleColor = "#FFD54F",
                showNumber = Constants.EVERY_PAGE,
            ).copy(backgroundSettings = BackgroundSettings(songBackground = imageBackground())),
        )
    }

    // ── Songs: what is behind the words ─────────────────────────────────────────────────────────

    @Test
    fun `on a colour`() = shoot("song_background_colour") {
        SongPresenter(lyricSection = song(),
            appSettings = songBackground(BackgroundConfig(backgroundColor = "#1B2A5B")))
    }

    @Test
    fun `on a gradient`() = shoot("song_background_gradient") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songBackground(
                BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_GRADIENT,
                    backgroundColor = "#1B2A5B",
                    gradientEnabled = true,
                    gradientTopColor = "#7B3FA6",
                    gradientTopOpacity = 0.8f,
                    gradientBottomColor = "#000000",
                    gradientBottomOpacity = 0.9f,
                )
            ),
        )
    }

    @Test
    fun `on a photograph`() = shoot("song_background_image") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songBackground(
                BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = photo().absolutePath)
            ),
        )
    }

    /** Blanking paints black on a projector, so it is shot against a coloured slide to be visible. */
    @Test
    fun `with the background suppressed`() = shoot("song_no_background") {
        SongPresenter(lyricSection = song(), appSettings = colouredSong(), showBackground = false)
    }

    // ── The intro slide ─────────────────────────────────────────────────────────────────────────

    /**
     * The slide a song opens on: its title and number, no lyrics.
     *
     * Built as the songs tab builds it — a section of type `song` carrying the title and no lines —
     * rather than by turning `titleSlideEnabled` on, which is the switch that makes the *tab* put
     * this section in front of the verses.
     */
    @Test
    fun `the intro slide`() = shoot("song_intro_slide") {
        SongPresenter(lyricSection = introSlide(), appSettings = songSettings(titleSlideEnabled = true))
    }

    // Not shot: the intro slide in the second language. The secondary title only replaces the primary
    // once the slide is drawn in secondary mode *and* carries secondary content, which an intro slide
    // — title and number, no lines — does not, so it renders identically to `song_intro_slide`.

    @Test
    fun `the intro slide over a photograph`() = shoot("song_intro_slide_on_image") {
        SongPresenter(
            lyricSection = introSlide(),
            appSettings = songSettings(titleSlideEnabled = true).copy(
                backgroundSettings = BackgroundSettings(songBackground = imageBackground()),
            ),
        )
    }

    // ── Shadow, which is what makes words readable over a picture ───────────────────────────────

    @Test
    fun `words over a photograph, with a shadow`() = shoot("song_shadow_on_image") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(lyricsShadow = true).copy(
                backgroundSettings = BackgroundSettings(songBackground = imageBackground()),
            ),
        )
    }

    /** The shadow's own settings — a larger, more opaque, coloured one. */
    @Test
    fun `a heavy coloured shadow`() = shoot("song_shadow_heavy") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(
                lyricsShadow = true,
                lyricsShadowColor = "#1B2A5B",
                lyricsShadowSize = 300,
                lyricsShadowOpacity = 100,
            ).copy(backgroundSettings = BackgroundSettings(songBackground = imageBackground())),
        )
    }

    @Test
    fun `scripture over a photograph, with a shadow`() = shoot("bible_shadow_on_image") {
        BiblePresenter(
            selectedVerses = listOf(verse()),
            appSettings = bibleSettings(textShadow = true).copy(
                backgroundSettings = BackgroundSettings(bibleBackground = imageBackground()),
            ),
        )
    }

    // ── Very large text ─────────────────────────────────────────────────────────────────────────

    /** Auto-fit off at 160pt: the size the operator asked for, whether or not it fits. */
    @Test
    fun `a very large fixed size`() = shoot("song_huge_fixed") {
        SongPresenter(
            lyricSection = song(),
            appSettings = songSettings(lyricsFontSizeAutoFit = false, lyricsFontSize = 160),
        )
    }

    /** The same size against a whole verse — what overflow looks like when nothing shrinks it. */
    @Test
    fun `a very large fixed size overflowing`() = shoot("song_huge_fixed_overflow") {
        SongPresenter(
            lyricSection = song(lines = LONG_VERSE),
            appSettings = songSettings(lyricsFontSizeAutoFit = false, lyricsFontSize = 160),
        )
    }

    /** Auto-fit with its ceiling raised — how large it will grow a short line on its own. */
    @Test
    fun `auto-fit given a high ceiling`() = shoot("song_huge_autofit") {
        SongPresenter(
            lyricSection = song(lines = listOf("Amazing grace")),
            appSettings = songSettings(lyricsMaxFontSize = 220),
        )
    }

    @Test
    fun `very large scripture`() = shoot("bible_huge") {
        BiblePresenter(selectedVerses = listOf(verse()), appSettings = bibleSettings(textFontSize = 160))
    }

    // ── Songs: the broadcast outputs ────────────────────────────────────────────────────────────

    // Shot against coloured text on a coloured ground: the default white-on-black slide rasterises
    // identically down all three roles, so a fill/key pair taken from it would say nothing.

    /** Fill carries the colours — byte for byte what the normal output shows, so that is not shot twice. */
    @Test
    fun `the fill signal`() = shoot("song_fill") {
        SongPresenter(lyricSection = song(), appSettings = colouredSong(), outputRole = Constants.OUTPUT_ROLE_FILL)
    }

    /** The key signal: a white matte of the same words for a hardware keyer. */
    @Test
    fun `the key signal`() = shoot("song_key") {
        SongPresenter(lyricSection = song(), appSettings = colouredSong(), outputRole = Constants.OUTPUT_ROLE_KEY)
    }

    @Test
    fun `mid-crossfade`() = shoot("song_crossfade") {
        SongPresenter(lyricSection = song(), appSettings = AppSettings(), transitionAlpha = 0.4f)
    }

    // ── Bible ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a verse of scripture`() =
        shoot("bible") { BiblePresenter(selectedVerses = listOf(verse()), appSettings = bibleSettings()) }

    /** A range is one entry carrying the whole passage — the list is one entry per *translation*. */
    @Test
    fun `a range of verses`() = shoot("bible_range") {
        BiblePresenter(
            selectedVerses = listOf(
                verse(
                    text = "For God so loved the world, that he gave his only begotten Son, that " +
                        "whosoever believeth in him should not perish, but have everlasting life. " +
                        "For God sent not his Son into the world to condemn the world.",
                    range = "16-17",
                ),
            ),
            appSettings = bibleSettings(),
        )
    }

    @Test
    fun `a long passage`() =
        shoot("bible_long") { BiblePresenter(selectedVerses = listOf(verse(text = LONG_PASSAGE)), appSettings = bibleSettings()) }

    @Test
    fun `two translations`() = shoot("bible_two_translations") {
        BiblePresenter(selectedVerses = listOf(verse(), verseRu()), appSettings = translations(2))
    }

    @Test
    fun `three translations`() = shoot("bible_three_translations") {
        BiblePresenter(
            selectedVerses = listOf(verse(), verseRu(), verseEs()),
            appSettings = translations(3),
        )
    }

    @Test
    fun `three translations with dividers`() = shoot("bible_translations_divided") {
        BiblePresenter(
            selectedVerses = listOf(verse(), verseRu(), verseEs()),
            appSettings = translations(3).let {
                it.copy(bibleSettings = it.bibleSettings.copy(multiTranslationDivider = true))
            },
        )
    }

    @Test
    fun `the reference above the text`() = shoot("bible_reference_above") {
        BiblePresenter(selectedVerses = listOf(verse()), appSettings = bibleSettings(referencePosition = "Above"))
    }

    @Test
    fun `the reference carrying the translation`() = shoot("bible_reference_abbreviation") {
        BiblePresenter(selectedVerses = listOf(verse()), appSettings = bibleSettings(showAbbreviation = true))
    }

    @Test
    fun `styled scripture`() = shoot("bible_styled") {
        BiblePresenter(
            selectedVerses = listOf(verse()),
            appSettings = bibleSettings(
                textColor = "#FFD54F",
                textBold = true,
                textItalic = true,
                textShadow = true,
                referenceColor = "#90CAF9",
            ),
        )
    }

    /** The reference in its own size and colour — it is not the verse and should not read as it. */
    @Test
    fun `a reference set apart from the verse`() = shoot("bible_reference_styled") {
        BiblePresenter(
            selectedVerses = listOf(verse()),
            appSettings = bibleSettings(
                referenceColor = "#FFD54F",
                referenceFontSize = 36,
                referenceBold = true,
            ),
        )
    }

    /** The other way round: a reference larger than the verse, centred over it. */
    @Test
    fun `a reference larger than the verse`() = shoot("bible_reference_large") {
        BiblePresenter(
            selectedVerses = listOf(verse()),
            appSettings = bibleSettings(
                referencePosition = "Above",
                referenceFontSize = 96,
                referenceColor = "#90CAF9",
                referenceHorizontalAlignment = Constants.CENTER,
            ),
        )
    }

    /** A reference at a size the frame cannot hold. */
    @Test
    fun `a reference too large for the frame`() = shoot("bible_reference_overflow") {
        BiblePresenter(
            selectedVerses = listOf(verse()),
            appSettings = bibleSettings(referenceFontSize = 300, referenceColor = "#FFD54F"),
        )
    }

    /** Verse and reference shadowed over a photograph, and the same without, to compare. */
    @Test
    fun `scripture and reference shadowed over a photograph`() = shoot("bible_reference_shadow_on_image") {
        BiblePresenter(
            selectedVerses = listOf(verse()),
            appSettings = bibleSettings(
                textShadow = true,
                referenceShadow = true,
                referenceColor = "#FFD54F",
                referenceFontSize = 40,
            ).copy(backgroundSettings = BackgroundSettings(bibleBackground = imageBackground())),
        )
    }

    @Test
    fun `scripture and reference unshadowed over a photograph`() = shoot("bible_reference_on_image") {
        BiblePresenter(
            selectedVerses = listOf(verse()),
            appSettings = bibleSettings(referenceColor = "#FFD54F", referenceFontSize = 40)
                .copy(backgroundSettings = BackgroundSettings(bibleBackground = imageBackground())),
        )
    }

    @Test
    fun `scripture centred`() = shoot("bible_align_center") {
        BiblePresenter(
            selectedVerses = listOf(verse()),
            appSettings = bibleSettings(textHorizontalAlignment = Constants.CENTER),
        )
    }

    @Test
    fun `scripture on a gradient`() = shoot("bible_background_gradient") {
        BiblePresenter(
            selectedVerses = listOf(verse()),
            appSettings = bibleBackground(
                BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_GRADIENT,
                    backgroundColor = "#10131A",
                    gradientEnabled = true,
                    gradientTopColor = "#2B3A67",
                    gradientTopOpacity = 0.9f,
                    gradientBottomColor = "#000000",
                    gradientBottomOpacity = 0.9f,
                )
            ),
        )
    }

    @Test
    fun `scripture on a photograph`() = shoot("bible_background_image") {
        BiblePresenter(
            selectedVerses = listOf(verse()),
            appSettings = bibleBackground(
                BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = photo().absolutePath)
            ),
        )
    }

    @Test
    fun `scripture on the normal output, for comparison`() = shoot("bible_normal_coloured") {
        BiblePresenter(selectedVerses = listOf(verse()), appSettings = colouredBible())
    }

    @Test
    fun `scripture with the background suppressed`() = shoot("bible_no_background") {
        BiblePresenter(selectedVerses = listOf(verse()), appSettings = colouredBible(), showBackground = false)
    }

    @Test
    fun `the scripture key signal`() = shoot("bible_key") {
        BiblePresenter(
            selectedVerses = listOf(verse()),
            appSettings = colouredBible(),
            outputRole = Constants.OUTPUT_ROLE_KEY,
        )
    }

    @Test
    fun `scripture mid-crossfade`() = shoot("bible_crossfade") {
        BiblePresenter(selectedVerses = listOf(verse()), appSettings = bibleSettings(), transitionAlpha = 0.4f)
    }

    // ── Announcements ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `an announcement`() = shoot("announcement") {
        AnnouncementsPresenter(text = NOTICE, appSettings = announcementSettings())
    }

    @Test
    fun `an announcement in a corner`() = shoot("announcement_corner") {
        AnnouncementsPresenter(
            text = NOTICE,
            appSettings = announcementSettings(position = Constants.TOP_LEFT),
        )
    }

    @Test
    fun `a styled announcement on a plate`() = shoot("announcement_styled") {
        AnnouncementsPresenter(
            text = NOTICE,
            appSettings = announcementSettings(
                textColor = "#FFD54F",
                backgroundColor = "#1B2A5B",
                fontSize = 96,
                bold = true,
            ),
        )
    }

    @Test
    fun `a countdown on screen`() = shoot("announcement_timer") {
        AnnouncementsPresenter(text = "05:00", appSettings = announcementSettings(fontSize = 200))
    }

    // Not shot: an announcement with the background suppressed. The announcement's own plate is
    // drawn either way and its ground is transparent by default, so it renders as `announcement` does.

    // ── Pictures ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a photograph`() = shoot("picture") { PicturePresenter(imagePath = photo().absolutePath) }

    /** Half way through a crossfade from one photograph to the next. */
    @Test
    fun `a picture mid-crossfade`() = shoot("picture_crossfade") {
        PicturePresenter(
            imagePath = photo().absolutePath,
            previousImagePath = secondPhoto().absolutePath,
            transitionAlpha = 0.5f,
        )
    }

    @Test
    fun `a picture mid-slide`() = shoot("picture_sliding") {
        PicturePresenter(
            imagePath = photo().absolutePath,
            previousImagePath = secondPhoto().absolutePath,
            slideOffset = 0.4f,
            animationType = AnimationType.SLIDE_LEFT,
        )
    }

    @Test
    fun `no picture to show`() = shoot("picture_none") { PicturePresenter(imagePath = null) }

    // ── Presentation slides ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a slide`() = shoot("presentation_slide") {
        PresentationPresenter(frame = null, slide = slideBitmap())
    }

    @Test
    fun `a slide mid-crossfade`() = shoot("presentation_crossfade") {
        PresentationPresenter(frame = null, slide = slideBitmap(), transitionAlpha = 0.45f)
    }

    /** Frozen: the output holds black rather than showing the deck mid-move. */
    @Test
    fun `a frozen deck`() = shoot("presentation_frozen") {
        PresentationPresenter(frame = null, slide = slideBitmap(), frozen = true)
    }

    // ── Questions ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a question`() = shoot("qa_question") { QAPresenter(question = question()) }

    @Test
    fun `a styled question`() = shoot("qa_question_styled") {
        QAPresenter(
            question = question(),
            qaSettings = QASettings(
                textColor = "#FFD54F",
                backgroundColor = "#1B2A5B",
                fontSize = 72,
                bold = true,
                position = Constants.CENTER,
            ),
        )
    }

    @Test
    fun `a long question`() = shoot("qa_question_long") {
        QAPresenter(question = question(LONG_QUESTION))
    }

    // ── Dictionary ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a Strong's entry`() = shoot("dictionary_entry") {
        DictionaryPresenter(entry = strongs(), dictionarySettings = DictionarySettings())
    }

    @Test
    fun `a styled Strong's entry`() = shoot("dictionary_entry_styled") {
        DictionaryPresenter(
            entry = strongs(),
            dictionarySettings = DictionarySettings(
                wordColor = "#FFD54F",
                wordFontSize = 140,
                wordBold = true,
                referenceColor = "#90CAF9",
                definitionColor = "#FFFFFF",
            ),
        )
    }

    // ── Canvas scenes ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a canvas scene`() = shoot("scene") { ScenePresenter(scene = scene()) }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun song(
        header: String = "[Verse 1]",
        type: String = Constants.SECTION_TYPE_VERSE,
        lines: List<String> = VERSE_LINES,
        secondary: List<String> = emptyList(),
    ) = LyricSection(
        header = header,
        title = "Amazing Grace",
        songNumber = 42,
        type = type,
        lines = lines,
        secondaryLines = secondary,
    )

    private fun songSettings(
        titleDisplay: String = SongSettings().titleDisplay,
        titlePosition: String = SongSettings().titlePosition,
        showNumber: String = SongSettings().showNumber,
        fullscreenLanguageDisplay: String = SongSettings().fullscreenLanguageDisplay,
        bilingualLayout: String = SongSettings().bilingualLayout,
        lyricsColor: String = SongSettings().lyricsColor,
        lyricsBold: Boolean = false,
        lyricsItalic: Boolean = false,
        lyricsUnderline: Boolean = false,
        lyricsShadow: Boolean = false,
        lyricsAlignment: String = SongSettings().lyricsAlignment,
        fullscreenDisplayMode: String = SongSettings().fullscreenDisplayMode,
        lyricsHorizontalAlignment: String = SongSettings().lyricsHorizontalAlignment,
        lyricsFontSize: Int = SongSettings().lyricsFontSize,
        lyricsFontSizeAutoFit: Boolean = true,
        lyricsMaxFontSize: Int = SongSettings().lyricsMaxFontSize,
        lyricsShadowColor: String = SongSettings().lyricsShadowColor,
        lyricsShadowSize: Int = SongSettings().lyricsShadowSize,
        lyricsShadowOpacity: Int = SongSettings().lyricsShadowOpacity,
        titleSlideEnabled: Boolean = false,
        titleFontSize: Int = SongSettings().titleFontSize,
        titleColor: String = SongSettings().titleColor,
        titleBold: Boolean = false,
        songNumberFontSize: Int = SongSettings().songNumberFontSize,
        songNumberColor: String = SongSettings().songNumberColor,
        songNumberBold: Boolean = false,
        songNumberPosition: String = SongSettings().songNumberPosition,
        songNumberHorizontalAlignment: String = SongSettings().songNumberHorizontalAlignment,
        songNumberShadow: Boolean = false,
        titleShadow: Boolean = false,
        marginTop: Int = SongSettings().marginTop,
        marginBottom: Int = SongSettings().marginBottom,
        marginLeft: Int = SongSettings().marginLeft,
        marginRight: Int = SongSettings().marginRight,
    ) = AppSettings(
        songSettings = SongSettings(
            titleDisplay = titleDisplay,
            titlePosition = titlePosition,
            showNumber = showNumber,
            fullscreenLanguageDisplay = fullscreenLanguageDisplay,
            bilingualLayout = bilingualLayout,
            lyricsColor = lyricsColor,
            lyricsBold = lyricsBold,
            lyricsItalic = lyricsItalic,
            lyricsUnderline = lyricsUnderline,
            lyricsShadow = lyricsShadow,
            lyricsAlignment = lyricsAlignment,
            fullscreenDisplayMode = fullscreenDisplayMode,
            lyricsHorizontalAlignment = lyricsHorizontalAlignment,
            lyricsFontSize = lyricsFontSize,
            lyricsFontSizeAutoFit = lyricsFontSizeAutoFit,
            lyricsMaxFontSize = lyricsMaxFontSize,
            lyricsShadowColor = lyricsShadowColor,
            lyricsShadowSize = lyricsShadowSize,
            lyricsShadowOpacity = lyricsShadowOpacity,
            titleSlideEnabled = titleSlideEnabled,
            titleFontSize = titleFontSize,
            titleColor = titleColor,
            titleBold = titleBold,
            songNumberFontSize = songNumberFontSize,
            songNumberColor = songNumberColor,
            songNumberBold = songNumberBold,
            songNumberPosition = songNumberPosition,
            songNumberHorizontalAlignment = songNumberHorizontalAlignment,
            songNumberShadow = songNumberShadow,
            titleShadow = titleShadow,
            marginTop = marginTop,
            marginBottom = marginBottom,
            marginLeft = marginLeft,
            marginRight = marginRight,
        ),
    )

    /** Amber words on a navy ground, so the three output roles are told apart. */
    private fun colouredSong() = AppSettings(
        songSettings = SongSettings(lyricsColor = "#FFD54F"),
        backgroundSettings = BackgroundSettings(songBackground = BackgroundConfig(backgroundColor = "#3B1F5B")),
    )

    private fun songBackground(config: BackgroundConfig) =
        AppSettings(backgroundSettings = BackgroundSettings(songBackground = config))

    /** Amber scripture on a purple ground, so the output roles are told apart. */
    private fun colouredBible() = bibleSettings(textColor = "#FFD54F", referenceColor = "#FFD54F").copy(
        backgroundSettings = BackgroundSettings(bibleBackground = BackgroundConfig(backgroundColor = "#3B1F5B")),
    )

    private fun bibleBackground(config: BackgroundConfig) = bibleSettings().copy(
        backgroundSettings = BackgroundSettings(bibleBackground = config),
    )

    /**
     * The full-screen scripture profile.
     *
     * `BibleSettings` carries a `primary*` set for the navigation bible and a `lowerThird*` set
     * beside it; these are the former, which is what a full-screen shot is of.
     */
    private fun bibleSettings(
        textColor: String = BibleSettings().primaryBibleColor,
        textBold: Boolean = false,
        textItalic: Boolean = false,
        textShadow: Boolean = false,
        textHorizontalAlignment: String = BibleSettings().primaryBibleHorizontalAlignment,
        referenceColor: String = BibleSettings().primaryReferenceColor,
        referencePosition: String = BibleSettings().primaryReferencePosition,
        showAbbreviation: Boolean = false,
        textFontSize: Int = BibleSettings().primaryBibleFontSize,
        referenceFontSize: Int = BibleSettings().primaryReferenceFontSize,
        referenceBold: Boolean = false,
        referenceShadow: Boolean = false,
        referenceHorizontalAlignment: String = BibleSettings().primaryReferenceHorizontalAlignment,
    ) = AppSettings(
        bibleSettings = BibleSettings(
            // Without this the translation list is empty, no entry matches the verse's own
            // `translationFileName`, and every setting below is silently ignored.
            primaryBible = KJV,
            primaryBibleColor = textColor,
            primaryBibleBold = textBold,
            primaryBibleItalic = textItalic,
            primaryBibleShadow = textShadow,
            primaryBibleFontSize = textFontSize,
            primaryBibleHorizontalAlignment = textHorizontalAlignment,
            primaryReferenceColor = referenceColor,
            primaryReferenceFontSize = referenceFontSize,
            primaryReferenceBold = referenceBold,
            primaryReferenceShadow = referenceShadow,
            primaryReferenceHorizontalAlignment = referenceHorizontalAlignment,
            primaryReferencePosition = referencePosition,
            primaryShowAbbreviation = showAbbreviation,
        ),
    )

    /** [count] translations configured, which is what puts the presenter in multi-translation mode. */
    private fun translations(count: Int) = AppSettings(
        bibleSettings = BibleSettings(
            translations = TRANSLATION_FILES.take(count).map { BibleTranslationSettings(fileName = it) },
        ),
    )

    private fun verse(
        number: Int = 16,
        text: String = "For God so loved the world, that he gave his only begotten Son.",
        fileName: String = KJV,
        abbreviation: String = "KJV",
        range: String = "",
    ) = SelectedVerse(
        translationFileName = fileName,
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = "John",
        chapter = 3,
        verseNumber = number,
        verseText = text,
        verseRange = range,
    )

    private fun verseRu() = verse(
        text = "Ибо так возлюбил Бог мир, что отдал Сына Своего Единородного.",
        fileName = "rst.spb",
        abbreviation = "RST",
    )

    private fun verseEs() = verse(
        text = "Porque de tal manera amó Dios al mundo, que ha dado a su Hijo unigénito.",
        fileName = "rvr.spb",
        abbreviation = "RVR",
    )

    private fun announcementSettings(
        textColor: String = AnnouncementsSettings().textColor,
        backgroundColor: String = AnnouncementsSettings().backgroundColor,
        fontSize: Int = AnnouncementsSettings().fontSize,
        bold: Boolean = false,
        position: String = AnnouncementsSettings().position,
    ) = AppSettings(
        announcementsSettings = AnnouncementsSettings(
            text = NOTICE,
            textColor = textColor,
            backgroundColor = backgroundColor,
            fontSize = fontSize,
            bold = bold,
            position = position,
            // The shipped default slides the text in over twelve seconds, so a capture of it is an
            // empty frame — the same reason the Announcements *tab* shots pin this off.
            animationType = Constants.ANIMATION_NONE,
        ),
    )

    private fun question(text: String = "How do I join a small group?") =
        Question(id = "q1", text = text, timestamp = 0L, status = QuestionStatus.APPROVED)

    private fun strongs() = StrongsEntry(
        number = "G26",
        word = "ἀγάπη",
        transliteration = "agape",
        pronunciation = "ag-ah'-pay",
        definition = "brotherly love, affection, benevolence",
        kjvUsage = "love, charity",
    )

    private fun scene() = Scene(
        name = "Welcome",
        sources = listOf(
            SceneSource.ColorSource(id = "c1", name = "Backdrop", color = "#1B2A5B"),
            SceneSource.TextSource(
                id = "t1",
                name = "Welcome",
                text = "Welcome to the 10:30 service",
                transform = SourceTransform(x = 0.1f, y = 0.4f, width = 0.8f, height = 0.2f),
                fontSize = 96,
            ),
        ),
    )

    /** A stand-in for a rasterised deck slide. */
    private fun slideBitmap(): ImageBitmap {
        val bitmap = ImageBitmap(1920, 1080)
        val canvas = Canvas(bitmap)
        fun bar(left: Float, top: Float, width: Float, height: Float, colour: ComposeColor) {
            canvas.drawRect(left, top, left + width, top + height, Paint().apply { color = colour })
        }
        bar(0f, 0f, 1920f, 1080f, ComposeColor(0xFFFAFAFA))
        bar(0f, 0f, 1920f, 160f, ComposeColor(0xFF2B3A67))
        bar(120f, 320f, 1100f, 80f, ComposeColor(0xFF20242B))
        listOf(480f, 580f, 680f).forEach { y -> bar(120f, y, 1400f, 40f, ComposeColor(0xFFC9CDD4)) }
        bar(120f, 820f, 520f, 90f, ComposeColor(0xFF3F7D58))
        return bitmap
    }

    private fun imageBackground() =
        BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = photo().absolutePath)

    /** The slide a song opens on: title and number, no lyrics — as the songs tab builds it. */
    private fun introSlide() = LyricSection(
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_SONG,
    )

    /** A second photograph, so a transition has something to move between. */
    private fun secondPhoto(): File {
        FIXTURES.mkdirs()
        val file = File(FIXTURES, "backdrop2.png")
        val image = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB)
        val canvas = image.createGraphics()
        canvas.paint = GradientPaint(0f, 0f, Color(0x7B3FA6), 1920f, 1080f, Color(0xF5A08E))
        canvas.fillRect(0, 0, 1920, 1080)
        canvas.dispose()
        ImageIO.write(image, "png", file)
        return file
    }

    /** A real, decodable image for the image-background states. */
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
        const val SECTION = "presenterFullScreen"

        val FIXTURES = File("build/screenshot-fixtures/presenter")

        val VERSE_LINES = listOf(
            "Amazing grace how sweet the sound",
            "That saved a wretch like me",
        )

        val SECONDARY_LINES = listOf(
            "О благодать, спасён тобой",
            "Я из пучины бед",
        )

        val LONG_VERSE = listOf(
            "Amazing grace how sweet the sound that saved a wretch like me",
            "I once was lost but now am found, was blind but now I see",
            "'Twas grace that taught my heart to fear, and grace my fears relieved",
            "How precious did that grace appear the hour I first believed",
        )

        const val LONG_TITLE = "Amazing Grace, How Sweet the Sound That Saved a Wretch Like Me"

        const val NOTICE = "Prayer meeting Wednesday at 7pm in the hall"

        const val LONG_QUESTION =
            "How should a small group decide what to study together, and how often should the " +
                "group change what it is reading?"

        const val LONG_PASSAGE =
            "The LORD is my shepherd; I shall not want. He maketh me to lie down in green " +
                "pastures: he leadeth me beside the still waters. He restoreth my soul: he leadeth " +
                "me in the paths of righteousness for his name's sake."

        const val KJV = "kjv.spb"

        val TRANSLATION_FILES = listOf(KJV, "rst.spb", "rvr.spb")
    }
}
