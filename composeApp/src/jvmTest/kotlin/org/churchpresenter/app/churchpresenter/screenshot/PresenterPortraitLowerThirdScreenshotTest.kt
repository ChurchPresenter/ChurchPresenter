@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.core.models.songs.SectionTranslation
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.viewmodel.titleSlideSection
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.settings.SongCreditStyle
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.settings.utils.Constants
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * The portrait/mobile-aspect counterpart of [PresenterLowerThirdScreenshotTest] -- every one of its
 * states, re-rendered on a 1080x1920 output instead of 1920x1080.
 *
 * This is also where the band-geometry fix was found: the band's background used to be sized against
 * the full output while its text content was sized against a padded, inset copy of it, so a title or
 * verse positioned "above" the rest of its content rendered above the visible band rather than inside
 * it -- present at any aspect ratio, but only found while building this portrait suite.
 */
class PresenterPortraitLowerThirdScreenshotTest {

    private val screen = Modifier.size(1080.dp, 1920.dp)

    private fun shoot(name: String, content: @Composable () -> Unit) =
        runDesktopComposeUiTest(width = 1080, height = 1920) {
            setContent { MaterialTheme { Box(screen) { content() } } }
            waitForIdle()
            capture(name)
        }

    private fun ComposeUiTest.capture(name: String) {
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$SECTION/$name.png")
    }

    // ── Songs ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a lyric band`() = shootSong("song", song())

    /** The band drawn as a chord chart, and how far auto-fit steps the type down to fit one. */
    @Test
    fun `a chord chart in the band`() =
        shootSong("song_chords", song(chords = CHORD_LINES), showChords = true)

    /** The chords take the band's own chord colour, separate from the full-screen one. */
    @Test
    fun `chords in the band's own colour`() = shootSong(
        "song_chords_coloured",
        song(chords = CHORD_LINES),
        settings = songSettings(lyricsLowerThirdChordColor = "#FFD54F"),
        showChords = true,
    )

    /** The vertical band, where the chart is narrower still. */
    @Test
    fun `a chord chart in the vertical band`() =
        shootSong("song_chords_vertical", song(chords = CHORD_LINES), vertical = true, showChords = true)

    @Test
    fun `a lyric band carrying the title and number`() = shootSong(
        "song_title_and_number",
        song(),
        songSettings(
            titleLowerThirdDisplay = Constants.EVERY_PAGE,
            titleLowerThirdPosition = Constants.ABOVE_VERSE,
            showNumberLowerThird = Constants.EVERY_PAGE,
        ),
    )

    @Test
    fun `a bare lyric band`() = shootSong(
        "song_bare",
        song(),
        songSettings(titleLowerThirdDisplay = Constants.NONE, showNumberLowerThird = Constants.NONE),
    )

    /** A whole verse in the band: the lower third defaults to one line at a time for a reason. */
    @Test
    fun `a whole verse in the band`() = shootSong(
        "song_whole_verse",
        song(lines = LONG_VERSE),
        songSettings(lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE),
    )

    @Test
    fun `one line of a long verse`() = shootSong("song_line", song(lines = LONG_VERSE), displayLineIndex = 2)

    @Test
    fun `both languages in the band`() = shootSong(
        "song_bilingual",
        song(secondary = SECONDARY_LINES),
        songSettings(lowerThirdLanguageDisplay = Constants.SONG_LANG_BOTH),
    )

    @Test
    fun `the second language alone in the band`() = shootSong(
        "song_secondary_only",
        song(secondary = SECONDARY_LINES),
        songSettings(lowerThirdLanguageDisplay = Constants.SONG_LANG_SECONDARY),
    )

    @Test
    fun `styled lyrics in the band`() = shootSong(
        "song_styled",
        song(),
        songSettings(
            lyricsLowerThirdColor = "#FFD54F",
            lyricsLowerThirdBold = true,
            lyricsLowerThirdItalic = true,
            lyricsLowerThirdShadow = true,
        ),
    )

    @Test
    fun `lyrics aligned left in the band`() =
        shootSong("song_align_left", song(), songSettings(lyricsLowerThirdHorizontalAlignment = Constants.LEFT))

    @Test
    fun `a larger band`() =
        shootSong(
            "song_large_type",
            song(),
            songSettings(lyricsLowerThirdFontSize = 72, lyricsLowerThirdFontSizeAutoFit = false),
        )

    @Test
    fun `the band on a colour`() =
        shootSong("song_background_colour", song(), songBackground(BackgroundConfig(backgroundColor = "#1B2A5B")))

    @Test
    fun `the band on a gradient`() = shootSong(
        "song_background_gradient",
        song(),
        songBackground(
            BackgroundConfig(
                backgroundType = Constants.BACKGROUND_GRADIENT,
                backgroundColor = "#000000",
                gradientEnabled = true,
                gradientTopColor = "#000000",
                gradientTopOpacity = 0f,
                gradientBottomColor = "#1B2A5B",
                gradientBottomOpacity = 0.95f,
            )
        ),
    )

    /** Transparent: nothing but the words, which is what a keyed broadcast band really is. */
    @Test
    fun `a transparent band`() = shootSong(
        "song_background_transparent",
        song(),
        songBackground(BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT)),
    )

    @Test
    fun `the band over a photograph`() = shootSong(
        "song_background_image",
        song(),
        songBackground(
            BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = photo().absolutePath)
        ),
    )

    /**
     * The vertical variant, which now differs only where there are two languages to lay out.
     *
     * A single-language band renders byte-identically to the horizontal one, so that is not shot:
     * the presenters say so themselves ("same band geometry for horizontal and vertical"). Note the
     * *parameter's* own doc still describes a right-anchored strip, which it no longer produces.
     */
    @Test
    fun `the vertical strip in two languages`() = shootSong(
        "song_vertical_bilingual",
        song(secondary = SECONDARY_LINES),
        songSettings(lowerThirdLanguageDisplay = Constants.SONG_LANG_BOTH),
        vertical = true,
    )

    // ── The band's title and number, which carry their own size and colour ──────────────────────

    @Test
    fun `a number set apart in the band`() = shootSong(
        "song_number_styled",
        song(),
        songSettings(
            showNumberLowerThird = Constants.EVERY_PAGE,
            songNumberLowerThirdColor = "#FFD54F",
            songNumberLowerThirdFontSize = 56,
        ),
    )

    /** A number large enough to run out of the band. */
    @Test
    fun `a number too large for the band`() = shootSong(
        "song_number_overflow",
        song(),
        songSettings(
            showNumberLowerThird = Constants.EVERY_PAGE,
            songNumberLowerThirdFontSize = 200,
            songNumberLowerThirdColor = "#FFD54F",
        ),
    )

    /**
     * The number pinned to the band's other top corner, clear of the title's row.
     *
     * The corners here are the *band's*, not the screen's -- the default bottom right is what every
     * other band shot above carries, so this is the one that shows the difference.
     */
    @Test
    fun `a number cornered at the band's top left`() = shootSong(
        "song_number_corner_top_left",
        song(),
        songSettings(
            titleLowerThirdDisplay = Constants.EVERY_PAGE,
            titleLowerThirdPosition = Constants.ABOVE_VERSE,
            showNumberLowerThird = Constants.EVERY_PAGE,
            songNumberLowerThirdCorner = Constants.TOP_LEFT,
            songNumberLowerThirdColor = "#FFD54F",
        ),
    )

    @Test
    fun `a title set apart in the band`() = shootSong(
        "song_title_styled",
        song(),
        songSettings(
            titleLowerThirdDisplay = Constants.EVERY_PAGE,
            titleLowerThirdPosition = Constants.ABOVE_VERSE,
            titleLowerThirdColor = "#90CAF9",
            titleLowerThirdFontSize = 40,
        ),
    )

    /** Title, number and words shadowed over a camera feed — and the same without, to compare. */
    @Test
    fun `the band shadowed over a photograph`() = shootSong(
        "song_shadow_on_image",
        song(),
        songSettings(
            titleLowerThirdDisplay = Constants.EVERY_PAGE,
            titleLowerThirdPosition = Constants.ABOVE_VERSE,
            titleLowerThirdColor = "#FFD54F",
            titleLowerThirdShadow = true,
            showNumberLowerThird = Constants.EVERY_PAGE,
            songNumberLowerThirdShadow = true,
            lyricsLowerThirdShadow = true,
        ).copy(
            backgroundSettings = BackgroundSettings(
                songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
            ),
        ),
        overlayPhoto = true,
    )

    @Test
    fun `the band unshadowed over a photograph`() = shootSong(
        "song_no_shadow_on_image",
        song(),
        songSettings(
            titleLowerThirdDisplay = Constants.EVERY_PAGE,
            titleLowerThirdPosition = Constants.ABOVE_VERSE,
            titleLowerThirdColor = "#FFD54F",
            showNumberLowerThird = Constants.EVERY_PAGE,
        ).copy(
            backgroundSettings = BackgroundSettings(
                songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
            ),
        ),
        overlayPhoto = true,
    )

    @Test
    fun `the band's key signal`() =
        shootSong("song_key", song(), colouredSong(), outputRole = Constants.OUTPUT_ROLE_KEY)

    @Test
    fun `the band's fill signal`() =
        shootSong("song_fill", song(), colouredSong(), outputRole = Constants.OUTPUT_ROLE_FILL)

    @Test
    fun `the band mid-crossfade`() = shootSong("song_crossfade", song(), transitionAlpha = 0.4f)

    // ── Scripture ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a scripture band`() = shootBible("bible", listOf(verse()))

    @Test
    fun `a scripture band in two translations`() =
        shootBible("bible_two_translations", listOf(verse(), verseRu()), translations(2))

    @Test
    fun `a long passage in the band`() = shootBible("bible_long", listOf(verse(text = LONG_PASSAGE)))

    @Test
    fun `the reference above the band's text`() =
        shootBible("bible_reference_above", listOf(verse()), bibleSettings(referencePosition = "Above"))

    @Test
    fun `the band's reference carrying the translation`() =
        shootBible("bible_reference_abbreviation", listOf(verse()), bibleSettings(showAbbreviation = true))

    @Test
    fun `styled scripture in the band`() = shootBible(
        "bible_styled",
        listOf(verse()),
        bibleSettings(textColor = "#FFD54F", textBold = true, textItalic = true, textShadow = true),
    )

    @Test
    fun `scripture centred in the band`() =
        shootBible("bible_align_center", listOf(verse()), bibleSettings(textHorizontalAlignment = Constants.CENTER))

    @Test
    fun `a larger scripture band`() =
        shootBible("bible_large_type", listOf(verse()), bibleSettings(textFontSize = 56))

    @Test
    fun `the scripture band on a colour`() =
        shootBible(
            "bible_background_colour",
            listOf(verse()),
            bibleBackground(BackgroundConfig(backgroundColor = "#1B2A5B")),
        )

    @Test
    fun `a transparent scripture band`() = shootBible(
        "bible_background_transparent",
        listOf(verse()),
        bibleBackground(BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT)),
    )

    /**
     * A wash over the two thirds the band does not cover — the one state in this suite where the
     * output draws anything above the band at all. Every other image here leaves it black, which is
     * what a lower third has always done and still does until a wash is set.
     */
    @Test
    fun `a wash above the scripture band`() = shootBible(
        "bible_above_band_colour",
        listOf(verse()),
        bibleSettings().copy(
            backgroundSettings = BackgroundSettings(
                bibleLowerThirdBackground = BackgroundConfig(backgroundColor = "#1B2A5B"),
                defaultLowerThirdAboveBandType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdAboveBandColor = "#2E6B4F",
                defaultLowerThirdAboveBandOpacity = 0.75f,
            ),
        ),
    )

    @Test
    fun `the scripture band over a photograph`() = shootBible(
        "bible_background_image",
        listOf(verse()),
        bibleBackground(
            BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = photo().absolutePath)
        ),
    )

    /** Vertical stacks the two translations instead of setting them side by side. */
    @Test
    fun `two translations stacked by the vertical strip`() =
        shootBible("bible_vertical_two_translations", listOf(verse(), verseRu()), translations(2), vertical = true)

    @Test
    fun `a reference set apart in the band`() = shootBible(
        "bible_reference_styled",
        listOf(verse()),
        bibleSettings(referenceColor = "#FFD54F", referenceFontSize = 40),
    )

    /** A reference large enough to run out of the band. */
    @Test
    fun `a reference too large for the band`() = shootBible(
        "bible_reference_overflow",
        listOf(verse()),
        bibleSettings(referenceFontSize = 180, referenceColor = "#FFD54F"),
    )

    @Test
    fun `the scripture band shadowed over a photograph`() = shootBible(
        "bible_shadow_on_image",
        listOf(verse()),
        bibleSettings(textShadow = true, referenceShadow = true, referenceColor = "#FFD54F")
            .copy(
                backgroundSettings = BackgroundSettings(
                    bibleLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
                ),
            ),
        overlayPhoto = true,
    )

    @Test
    fun `the scripture band unshadowed over a photograph`() = shootBible(
        "bible_no_shadow_on_image",
        listOf(verse()),
        bibleSettings(referenceColor = "#FFD54F").copy(
            backgroundSettings = BackgroundSettings(
                bibleLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
            ),
        ),
        overlayPhoto = true,
    )

    @Test
    fun `the scripture band's key signal`() =
        shootBible("bible_key", listOf(verse()), colouredBible(), outputRole = Constants.OUTPUT_ROLE_KEY)

    @Test
    fun `the scripture band mid-crossfade`() =
        shootBible("bible_crossfade", listOf(verse()), transitionAlpha = 0.4f)

    // ── The backdrop and the stroke, in portrait ────────────────────────────────────────────────
    //
    // Neither portrait suite had a single text-backdrop or outline state, and portrait is where the
    // clipping bug bit hardest: a plate is drawn outside the text's own box, and it is only cut off
    // when an ancestor clips *and* the text fills that box — which is exactly what a narrow frame
    // makes a line of scripture do. Landscape text has slack at the end of a line; portrait does not.

    @Test
    fun `a scripture band on a plate`() =
        shootBible("bible_backdrop", listOf(verse()), bibleBackdrop(LINE_PLATE))

    @Test
    fun `a scripture band in a bordered box`() =
        shootBible("bible_backdrop_border", listOf(verse()), bibleBackdrop(BORDER_BOX))

    /** Two translations bordered, which is where the full-screen bug was first unmistakable. */
    @Test
    fun `two translations in a bordered box`() =
        shootBible("bible_two_translations_border", listOf(verse(), verseRu()), bibleBackdrop(BORDER_BOX, 2))

    @Test
    fun `lyrics on a plate`() = shootSong("song_backdrop", song(), songBackdrop(LINE_PLATE))

    @Test
    fun `lyrics in a bordered box`() = shootSong("song_backdrop_border", song(), songBackdrop(BORDER_BOX))

    /** Left-aligned *and* bordered: the pairing no image anywhere made before this batch. */
    @Test
    fun `lyrics aligned left in a bordered box`() = shootSong(
        "song_border_left",
        song(),
        AppSettings(
            songSettings = SongSettings(
                lyricsLowerThirdBackdrop = BORDER_BOX,
                lyricsLowerThirdHorizontalAlignment = Constants.LEFT,
            ),
        ),
    )

    /**
     * The vertical strip, bordered — two translations, which is the only way this state exists.
     *
     * `isLowerThirdVertical` is *purely a text-stacking flag*: both presenters draw the same band
     * geometry either way and use it only to stack parallel translations instead of setting them
     * side by side. So a vertical shot of **one** translation is byte-for-byte the horizontal one,
     * and a first cut of this test was exactly that — caught by the duplicate-hash check AGENT.md
     * prescribes (md5 every PNG in a section and look for a repeat), which exists for precisely this:
     * two captures coming out byte-identical mean the state was never reached. Stacked and bordered
     * is the narrowest box the app draws, and so the least room a plate ever has to reach into.
     */
    @Test
    fun `the vertical strip in a bordered box`() = shootBible(
        "bible_vertical_border",
        listOf(verse(), verseRu()),
        bibleBackdrop(BORDER_BOX, 2),
        vertical = true,
    )

    @Test
    fun `scripture stroked in the band`() = shootBible(
        "bible_outline",
        listOf(verse()),
        bibleOutline(GLYPH_STROKE),
        overlayPhoto = true,
    )

    @Test
    fun `lyrics stroked in the band`() = shootSong(
        "song_outline",
        song(),
        songSettings().copy(
            songSettings = songSettings().songSettings.copy(
                outlines = songSettings().songSettings.outlines.copy(lyricsLowerThird = GLYPH_STROKE),
            ),
            backgroundSettings = BackgroundSettings(
                songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
            ),
        ),
        overlayPhoto = true,
    )

    // ── The intro slide ─────────────────────────────────────────────────────────────────────────

    /** The slide a song opens on, in the band: number and title on one line, the credits under. */
    @Test
    fun `the intro slide in the band`() = shootSong("song_intro_slide", introSlide(), titleSlideSettings())

    /** Every credit on, both languages: the band's own smaller profiles keep it inside the band. */
    @Test
    fun `the intro slide in the band with every credit`() = shootSong(
        "song_intro_slide_everything",
        introSlide(),
        titleSlideSettings(titleSlideShowCcli = true, titleSlideShowTempo = true),
        languageOverride = Constants.SONG_LANG_BOTH,
    )

    /** Over a camera feed, with the band's profiles shadowed. */
    @Test
    fun `the intro slide in the band over a camera`() = shootSong(
        "song_intro_slide_on_camera",
        introSlide(),
        titleSlideSettings(shadowed = true),
        overlayPhoto = true,
    )

    private fun introSlide() = titleSlideSection(
        SongItem(
            number = "427",
            title = "Amazing Grace",
            secondaryTitle = "О, благодать",
            author = "John Newton",
            composer = "William Walker",
            ccliNumber = "22025",
        ),
        SongTuning(bpm = 84),
        SongSettings(titleSlideShowCcli = true, titleSlideShowTempo = true),
    )

    private fun titleSlideSettings(
        titleSlideShowCcli: Boolean = false,
        titleSlideShowTempo: Boolean = false,
        shadowed: Boolean = false,
    ) = AppSettings(
        songSettings = SongSettings(
            titleSlideEnabled = true,
            titleSlideShowCcli = titleSlideShowCcli,
            titleSlideShowTempo = titleSlideShowTempo,
            titleLowerThirdShadow = shadowed,
            songNumberLowerThirdShadow = shadowed,
            titleSlideAuthorLowerThird = SongCreditStyle(fontSize = 20, shadow = shadowed),
            titleSlideComposerLowerThird = SongCreditStyle(fontSize = 20, shadow = shadowed),
        ),
    )

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    private fun shootSong(
        name: String,
        section: LyricSection,
        settings: AppSettings = songSettings(),
        vertical: Boolean = false,
        outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
        transitionAlpha: Float = 1f,
        displayLineIndex: Int = -1,
        overlayPhoto: Boolean = false,
        showChords: Boolean = false,
        languageOverride: String = "",
    ) = shoot(name) {
        if (overlayPhoto) CameraFeed()
        SongPresenter(
            lyricSection = section,
            appSettings = settings,
            isLowerThird = true,
            isLowerThirdVertical = vertical,
            outputRole = outputRole,
            transitionAlpha = transitionAlpha,
            displayLineIndex = displayLineIndex,
            showChords = showChords,
            languageOverride = languageOverride,
        )
    }

    private fun shootBible(
        name: String,
        verses: List<SelectedVerse>,
        settings: AppSettings = bibleSettings(),
        vertical: Boolean = false,
        outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
        transitionAlpha: Float = 1f,
        overlayPhoto: Boolean = false,
    ) = shoot(name) {
        if (overlayPhoto) CameraFeed()
        BiblePresenter(
            selectedVerses = verses,
            appSettings = settings,
            isLowerThird = true,
            isLowerThirdVertical = vertical,
            outputRole = outputRole,
            transitionAlpha = transitionAlpha,
        )
    }

    /**
     * The picture a keyed band is laid over.
     *
     * Drawn behind the presenter rather than set as the band's own background: a broadcast band is
     * transparent and composited over a camera feed, and a shadow only earns its keep against that.
     */
    @Composable
    private fun CameraFeed() {
        Image(
            painter = BitmapPainter(loadImageBitmap(photo().inputStream())),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun song(
        lines: List<String> = VERSE_LINES,
        secondary: List<String> = emptyList(),
        chords: List<String> = emptyList(),
    ) = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines,
        translations = if (secondary.isEmpty()) emptyList() else listOf(SectionTranslation(lines = secondary)),
        chordLines = chords,
    )

    private fun songSettings(
        titleLowerThirdDisplay: String = SongSettings().titleLowerThirdDisplay,
        titleLowerThirdPosition: String = SongSettings().titleLowerThirdPosition,
        showNumberLowerThird: String = SongSettings().showNumberLowerThird,
        lowerThirdDisplayMode: String = SongSettings().lowerThirdDisplayMode,
        lowerThirdLanguageDisplay: String = SongSettings().lowerThirdLanguageDisplay,
        lyricsLowerThirdColor: String = SongSettings().lyricsLowerThirdColor,
        lyricsLowerThirdChordColor: String = SongSettings().lyricsLowerThirdChordColor,
        lyricsLowerThirdBold: Boolean = false,
        lyricsLowerThirdItalic: Boolean = false,
        lyricsLowerThirdShadow: Boolean = false,
        lyricsLowerThirdHorizontalAlignment: String = SongSettings().lyricsLowerThirdHorizontalAlignment,
        lyricsLowerThirdFontSize: Int = SongSettings().lyricsLowerThirdFontSize,
        lyricsLowerThirdFontSizeAutoFit: Boolean = true,
        titleLowerThirdColor: String = SongSettings().titleLowerThirdColor,
        titleLowerThirdFontSize: Int = SongSettings().titleLowerThirdFontSize,
        titleLowerThirdShadow: Boolean = false,
        songNumberLowerThirdCorner: String = SongSettings().songNumberLowerThirdCorner,
        songNumberLowerThirdColor: String = SongSettings().songNumberLowerThirdColor,
        songNumberLowerThirdFontSize: Int = SongSettings().songNumberLowerThirdFontSize,
        songNumberLowerThirdShadow: Boolean = false,
    ) = AppSettings(
        songSettings = SongSettings(
            titleLowerThirdDisplay = titleLowerThirdDisplay,
            titleLowerThirdPosition = titleLowerThirdPosition,
            showNumberLowerThird = showNumberLowerThird,
            lowerThirdDisplayMode = lowerThirdDisplayMode,
            lowerThirdLanguageDisplay = lowerThirdLanguageDisplay,
            lyricsLowerThirdColor = lyricsLowerThirdColor,
            lyricsLowerThirdChordColor = lyricsLowerThirdChordColor,
            lyricsLowerThirdBold = lyricsLowerThirdBold,
            lyricsLowerThirdItalic = lyricsLowerThirdItalic,
            lyricsLowerThirdHorizontalAlignment = lyricsLowerThirdHorizontalAlignment,
            lyricsLowerThirdFontSize = lyricsLowerThirdFontSize,
            lyricsLowerThirdFontSizeAutoFit = lyricsLowerThirdFontSizeAutoFit,
            lyricsLowerThirdShadow = lyricsLowerThirdShadow,
            titleLowerThirdColor = titleLowerThirdColor,
            titleLowerThirdFontSize = titleLowerThirdFontSize,
            titleLowerThirdShadow = titleLowerThirdShadow,
            songNumberLowerThirdCorner = songNumberLowerThirdCorner,
            songNumberLowerThirdColor = songNumberLowerThirdColor,
            songNumberLowerThirdFontSize = songNumberLowerThirdFontSize,
            songNumberLowerThirdShadow = songNumberLowerThirdShadow,
        ),
    )

    private fun songBackground(config: BackgroundConfig) =
        songSettings().copy(backgroundSettings = BackgroundSettings(songLowerThirdBackground = config))

    /** Amber words on a purple band, so the three output roles are told apart. */
    private fun colouredSong() = songSettings(lyricsLowerThirdColor = "#FFD54F")
        .copy(
            backgroundSettings = BackgroundSettings(
                songLowerThirdBackground = BackgroundConfig(backgroundColor = "#3B1F5B"),
            ),
        )

    /**
     * The lower-third scripture profile.
     *
     * `primaryBible` has to name the verse's own module or the translation stack is empty, nothing
     * matches, and every setting below is silently ignored.
     */
    private fun bibleSettings(
        textColor: String = BibleSettings().primaryBibleLowerThirdColor,
        textBold: Boolean = false,
        textItalic: Boolean = false,
        textShadow: Boolean = false,
        textHorizontalAlignment: String = BibleSettings().primaryBibleLowerThirdHorizontalAlignment,
        textFontSize: Int = BibleSettings().primaryBibleLowerThirdFontSize,
        referenceColor: String = BibleSettings().primaryReferenceLowerThirdColor,
        referenceFontSize: Int = BibleSettings().primaryReferenceLowerThirdFontSize,
        referenceShadow: Boolean = false,
        referencePosition: String = BibleSettings().primaryReferenceLowerThirdPosition,
        showAbbreviation: Boolean = false,
    ) = AppSettings(
        bibleSettings = BibleSettings(
            primaryBible = KJV,
            primaryBibleLowerThirdColor = textColor,
            primaryBibleLowerThirdBold = textBold,
            primaryBibleLowerThirdItalic = textItalic,
            primaryBibleLowerThirdShadow = textShadow,
            primaryBibleLowerThirdHorizontalAlignment = textHorizontalAlignment,
            primaryBibleLowerThirdFontSize = textFontSize,
            primaryReferenceLowerThirdColor = referenceColor,
            primaryReferenceLowerThirdFontSize = referenceFontSize,
            primaryReferenceLowerThirdShadow = referenceShadow,
            primaryReferenceLowerThirdPosition = referencePosition,
            primaryShowAbbreviation = showAbbreviation,
        ),
    )


    private fun bibleBackground(config: BackgroundConfig) =
        bibleSettings().copy(backgroundSettings = BackgroundSettings(bibleLowerThirdBackground = config))

    private fun colouredBible() = bibleSettings(textColor = "#FFD54F").copy(
        backgroundSettings = BackgroundSettings(
            bibleLowerThirdBackground = BackgroundConfig(backgroundColor = "#3B1F5B"),
        ),
    )

    private fun translations(count: Int) = AppSettings(
        bibleSettings = BibleSettings(
            translations = TRANSLATION_FILES.take(count).map { BibleTranslationSettings(fileName = it) },
        ),
    )

    /** [count] translations, each drawing its verse text on [backdrop], for the band. */
    private fun bibleBackdrop(backdrop: TextBackdrop, count: Int = 1) = AppSettings(
        bibleSettings = BibleSettings(
            translations = listOf(KJV, "rst.spb").take(count).map {
                BibleTranslationSettings(fileName = it, lowerThirdTextBackdrop = backdrop)
            },
        ),
    )

    /** The lyrics on [backdrop], for the band. */
    private fun songBackdrop(backdrop: TextBackdrop) = AppSettings(
        songSettings = SongSettings(lyricsLowerThirdBackdrop = backdrop),
    )

    /**
     * [outline] around the band's verse glyphs, over a transparent band so the stroke has the
     * photograph behind it rather than black — on the opaque band a dark stroke shows nothing.
     */
    private fun bibleOutline(outline: TextOutline) = AppSettings(
        bibleSettings = BibleSettings(
            translations = listOf(BibleTranslationSettings(fileName = KJV, lowerThirdTextOutline = outline)),
        ),
        backgroundSettings = BackgroundSettings(
            bibleLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
        ),
    )

    private fun verse(
        text: String = "For God so loved the world, that he gave his only begotten Son.",
        fileName: String = KJV,
        abbreviation: String = "KJV",
    ) = SelectedVerse(
        translationFileName = fileName,
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
    )

    private fun verseRu() = verse(
        text = "Ибо так возлюбил Бог мир, что отдал Сына Своего Единородного.",
        fileName = "rst.spb",
        abbreviation = "RST",
    )

    /** A camera feed stands in as a photograph, so a transparent band has something to key over. */
    private fun photo(): File {
        FIXTURES.mkdirs()
        val file = File(FIXTURES, "stage.png")
        val image = BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB)
        val canvas = image.createGraphics()
        canvas.paint = GradientPaint(0f, 0f, Color(0x3F5A7D), 0f, 1920f, Color(0xC9CDD4))
        canvas.fillRect(0, 0, 1080, 1920)
        canvas.color = Color(0x20242B)
        canvas.fillOval(340, 240, 400, 400)
        canvas.dispose()
        ImageIO.write(image, "png", file)
        return file
    }

    private companion object {
        const val SECTION = "presenterPortraitLowerThird"

        /** A band behind each line. Not near-black: the band it sits on is black. */
        val LINE_PLATE = TextBackdrop(
            lineBackground = true,
            lineBackgroundColor = "#1B3A6B",
            lineBackgroundOpacity = 90,
        )

        /** A box around the block. No fill: with one it stops being a box of its own. */
        val BORDER_BOX = TextBackdrop(
            border = true,
            borderColor = "#FFD54F",
            borderWidth = 6,
            borderPadding = 18,
            borderRadius = 12,
        )

        /** `enabled` defaults to false, and without it `isVisible` is false and nothing is stroked. */
        val GLYPH_STROKE = TextOutline(enabled = true, width = 6, color = "#101820")

        const val KJV = "kjv.spb"

        val FIXTURES = File("build/screenshot-fixtures/presenter-portrait-lower-third")

        val VERSE_LINES = listOf(
            "Amazing grace how sweet the sound",
            "That saved a wretch like me",
        )

        /** [VERSE_LINES] as the band reads them, chords inline before the syllable they land on. */
        val CHORD_LINES = listOf(
            "[G]Amazing [C]grace how [G]sweet the sound",
            "That [D]saved a [G]wretch like me",
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

        const val LONG_PASSAGE =
            "The LORD is my shepherd; I shall not want. He maketh me to lie down in green " +
                "pastures: he leadeth me beside the still waters. He restoreth my soul."

        val TRANSLATION_FILES = listOf(KJV, "rst.spb", "rvr.spb")
    }
}
