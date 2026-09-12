@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BackgroundScope
import org.churchpresenter.app.churchpresenter.dialogs.tabs.lottieBandSeed
import org.churchpresenter.app.churchpresenter.presenter.BibleBandClock
import org.churchpresenter.app.churchpresenter.presenter.BibleBandPhase
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.LocalLottieBandClock
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.viewmodel.titleSlideSection
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.lottiegen.band.BandColorRole
import org.churchpresenter.lottiegen.band.BandContentKind
import org.churchpresenter.lottiegen.band.BandEntrance
import org.churchpresenter.lottiegen.band.BandImage
import org.churchpresenter.lottiegen.band.BandStyle
import org.churchpresenter.lottiegen.band.BibleLottieGenApp
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenerator
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.ChurchPresenterTheme
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The Lottie lower third: the generated band the Bible and Songs presenters play instead of the
 * classic one when a template is picked, and the generator that writes it.
 *
 * The band shots are the presenter on a 1920x1080 frame, like `PresenterLowerThirdScreenshotTest`,
 * with the template generated fresh from a config rather than committed — the generator is the
 * fixture, so a change to it moves these images too. The generator shots are stacked per theme
 * like `LottieGenScreenshotTest`, and scrub the preview the same way, for the same reason.
 */
class LottieBandScreenshotTest {

    private val frame = Size(1920f, 1080f)
    private val screen = Modifier.size(frame.width.dp, frame.height.dp)
    private val window = Size(1240f, 820f)

    @AfterTest
    fun restoreLocale() = Strings.setLocale(Locale.getDefault())

    // ── The band on the output ──────────────────────────────────────────────────────────────────

    @Test
    fun `a scripture band`() = shootBible("bible", listOf(verse()))

    @Test
    fun `a scripture band in two translations`() = shootBible(
        "bible_two_translations",
        listOf(verse(), verseRu()),
        translations(2),
        BibleLottieGenConfig(layout = SlotLayout.SIDE_BY_SIDE),
    )

    @Test
    fun `two translations stacked in one slot`() =
        shootBible("bible_two_translations_stacked", listOf(verse(), verseRu()), translations(2))

    /** Half-way through the entrance: the band slid half-way up, the text not yet in. */
    @Test
    fun `the scripture band entering`() = shootBible(
        "bible_entering",
        listOf(verse()),
        clock = BibleBandClock(BibleBandPhase.ENTER, 0.5f),
    )

    @Test
    fun `styled scripture in the band`() = shootBible(
        "bible_styled",
        listOf(verse()),
        bibleSettings(textColor = "#FFD54F", textBold = true, textShadow = true, referenceColor = "#FFD54F"),
        BibleLottieGenConfig(bandStyle = BandStyle.GRADIENT_ANGLED, entrance = BandEntrance.WIPE_LEFT),
    )

    @Test
    fun `the band over a picture`() = shootBible(
        "bible_picture",
        listOf(verse()),
        template = BibleLottieGenConfig(
            bandStyle = BandStyle.SPLIT_SHUTTER,
            images = mapOf(BandColorRole.BACKGROUND to picture()),
        ),
    )

    @Test
    fun `a typewriter half-way through its line`() = shootBible(
        "bible_typewriter",
        listOf(verse()),
        template = BibleLottieGenConfig(textAnimation = TextAnimation.TYPEWRITER),
        clock = BibleBandClock(BibleBandPhase.TEXT_IN, 0.5f),
    )

    @Test
    fun `the scripture band's key signal`() =
        shootBible("bible_key", listOf(verse()), outputRole = Constants.OUTPUT_ROLE_KEY)

    @Test
    fun `a lyric band`() = shootSong("song", song())

    @Test
    fun `both languages in the band`() = shootSong(
        "song_bilingual",
        song(secondary = SECONDARY_LINES),
        songSettings(lowerThirdLanguageDisplay = Constants.SONG_LANG_BOTH),
        BibleLottieGenConfig(kind = BandContentKind.SONG, layout = SlotLayout.STACKED),
    )

    @Test
    fun `the intro slide in the band`() = shootSong(
        "song_intro_slide",
        introSlide(),
        songSettings(titleSlideEnabled = true),
        BibleLottieGenConfig(kind = BandContentKind.SONG, bandStyle = BandStyle.HORIZONTAL_BANDS),
    )

    // ── The generator ───────────────────────────────────────────────────────────────────────────

    /** Seeded as the settings tabs seed it, so the sample text and sizes are the ones a user sees. */
    @Test
    fun `the generator`() = generator("generator", lottieBandSeed(AppSettings(), BackgroundScope.BIBLE_LOWER_THIRD))

    @Test
    fun `the generator for songs`() =
        generator("generator_song", lottieBandSeed(AppSettings(), BackgroundScope.SONG_LOWER_THIRD))

    /** The style list, which is long enough to scroll and carries its own bar. */
    @Test
    fun `the style menu`() = generator("generator_style_menu", rootIndex = 1) {
        onNodeWithText(Strings.bandEnumLabel("style", BandStyle.SOLID_BAR.name)).performClick()
        waitForIdle()
    }

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    private fun shootBible(
        name: String,
        verses: List<SelectedVerse>,
        settings: AppSettings = bibleSettings(),
        template: BibleLottieGenConfig = BibleLottieGenConfig(),
        clock: BibleBandClock = BibleBandClock(),
        outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    ) {
        val file = writeTemplate(name, template)
        val withBand = settings.copy(
            backgroundSettings = settings.backgroundSettings.copy(bibleLowerThirdBackground = lottie(file)),
        )
        shoot(name, clock) {
            BiblePresenter(
                selectedVerses = verses,
                appSettings = withBand,
                isLowerThird = true,
                outputRole = outputRole,
            )
        }
    }

    private fun shootSong(
        name: String,
        section: LyricSection,
        settings: AppSettings = songSettings(),
        template: BibleLottieGenConfig = BibleLottieGenConfig(kind = BandContentKind.SONG),
    ) {
        val file = writeTemplate(name, template)
        val withBand = settings.copy(
            backgroundSettings = settings.backgroundSettings.copy(songLowerThirdBackground = lottie(file)),
        )
        shoot(name, BibleBandClock()) {
            SongPresenter(lyricSection = section, appSettings = withBand, isLowerThird = true)
        }
    }

    private fun shoot(name: String, clock: BibleBandClock, content: @Composable () -> Unit) =
        runSkikoComposeUiTest(size = frame, density = Density(1f)) {
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLottieBandClock provides clock) { Box(screen) { content() } }
            }
        }
        waitForIdle()
        // The template is read and the composition parsed off the test's clock, so the band is
        // waited for by its pixels: once the band's own fill has replaced the clear frame at its foot
        // the painter has drawn it, and the shot is the same frame every run.
        waitUntil("the band rendered", RENDER_TIMEOUT_MS) { bandPixel().alpha > 0f }
        captureTo(File("$SCREENSHOT_ROOT/$SECTION/$name.png"))
    }

    private fun ComposeUiTest.bandPixel(): Color =
        onRoot().captureToImage().toPixelMap()[BAND_PROBE_X, BAND_PROBE_Y]

    private fun generator(
        name: String,
        seed: BibleLottieGenConfig = lottieBandSeed(AppSettings(), BackgroundScope.BIBLE_LOWER_THIRD),
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        Strings.setLocale(Locale.ENGLISH)
        // The generator reopens on whatever was saved last; the shot wants the seed.
        File(System.getProperty("user.home"), LAST_CONFIG).delete()
        stackedThemes(SECTION, name) { mode, file ->
            runSkikoComposeUiTest(size = window, density = Density(1f)) {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Box(Modifier.size(window.width.dp, window.height.dp)) {
                            BibleLottieGenApp(outputDir = null, onFileSaved = null, seed = seed)
                        }
                    }
                }
                waitForIdle()
                scrubToHeldFrame()
                // Generation is debounced and the composition parsed off the test's clock, so the
                // preview is waited for by its pixels: the sample text is white, and nothing else
                // above the transport is — the checkerboard is grey in both themes.
                waitUntil("the preview rendered", RENDER_TIMEOUT_MS) { previewWhitePixels() >= PREVIEW_TEXT_PIXELS }
                drive()
                captureTo(file, rootIndex)
            }
        }
    }

    private fun ComposeUiTest.previewWhitePixels(): Int {
        // Paused by the scrub, so the button now offers to play.
        val transport = onNodeWithContentDescription("Play").fetchSemanticsNode().boundsInRoot
        val pixels = onRoot().captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until transport.top.toInt()) {
            for (x in transport.left.toInt() until pixels.width) if (pixels[x, y] == Color.White) count++
        }
        return count
    }

    /** As `LottieGenScreenshotTest` does: a tap on the transport pauses the preview on one frame. */
    private fun ComposeUiTest.scrubToHeldFrame() {
        val transport = onNodeWithContentDescription("Pause").fetchSemanticsNode().boundsInRoot
        val x = transport.right + (window.width - transport.right) * SCRUB_FRACTION
        onRoot().performTouchInput { click(Offset(x, transport.center.y)) }
        waitForIdle()
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun writeTemplate(name: String, cfg: BibleLottieGenConfig): File {
        FIXTURES.mkdirs()
        val sized = cfg.copy(canvasW = 1920, canvasH = 356)
        val text = Json.encodeToString(JsonObject.serializer(), BibleLottieGenerator.generate(sized))
        return File(FIXTURES, "$name.json").apply { writeText(text) }
    }

    private fun lottie(file: File) =
        BackgroundConfig(backgroundType = Constants.BACKGROUND_LOTTIE, backgroundLottie = file.absolutePath)

    /** A picture for a band role, as the generator embeds one: a data URL of the file's bytes. */
    private fun picture(): BandImage {
        val image = BufferedImage(640, 120, BufferedImage.TYPE_INT_RGB)
        val canvas = image.createGraphics()
        canvas.paint = GradientPaint(0f, 0f, java.awt.Color(0x3F5A7D), 640f, 120f, java.awt.Color(0xC9CDD4))
        canvas.fillRect(0, 0, 640, 120)
        canvas.color = java.awt.Color(0x20242B)
        canvas.fillOval(240, 10, 100, 100)
        canvas.dispose()
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val data = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
        return BandImage(data, 640, 120, "stage.png")
    }

    private fun bibleSettings(
        textColor: String = BibleSettings().primaryBibleLowerThirdColor,
        textBold: Boolean = false,
        textShadow: Boolean = false,
        referenceColor: String = BibleSettings().primaryReferenceLowerThirdColor,
    ) = AppSettings(
        bibleSettings = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(
                    fileName = KJV,
                    lowerThirdTextColor = textColor,
                    lowerThirdTextBold = textBold,
                    lowerThirdTextShadow = textShadow,
                    lowerThirdReferenceColor = referenceColor,
                ),
            ),
        ),
    )

    private fun translations(count: Int) = AppSettings(
        bibleSettings = BibleSettings(
            translations = TRANSLATION_FILES.take(count).map { BibleTranslationSettings(fileName = it) },
        ),
    )

    private fun songSettings(
        lowerThirdLanguageDisplay: String = SongSettings().lowerThirdLanguageDisplay,
        titleSlideEnabled: Boolean = false,
    ) = AppSettings(
        songSettings = SongSettings(
            lowerThirdLanguageDisplay = lowerThirdLanguageDisplay,
            titleSlideEnabled = titleSlideEnabled,
            titleSlideShowCcli = true,
        ),
    )

    private fun song(secondary: List<String> = emptyList()) = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        secondaryTitle = "О, благодать",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = VERSE_LINES,
        secondaryLines = secondary,
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
        SongSettings(titleSlideShowCcli = true),
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

    private companion object {
        const val SECTION = "lottieBand"
        const val LAST_CONFIG = ".churchpresenter/churchpresenter-lottiegen/bible-band-last.json"
        const val KJV = "kjv.spb"
        val TRANSLATION_FILES = listOf(KJV, "rst.spb")
        val FIXTURES = File("build/screenshot-fixtures/lottie-band")

        /** Mid-band on a 1920x1080 frame with the default 33% band: inside the fill, clear of the text. */
        const val BAND_PROBE_X = 60
        const val BAND_PROBE_Y = 1040

        /** More pure-white pixels than the panel chrome ever has above the transport: the sample text. */
        const val PREVIEW_TEXT_PIXELS = 20

        /** Where to tap on the transport, as `LottieGenScreenshotTest` calibrates it: inside the hold. */
        const val SCRUB_FRACTION = 0.6f

        val VERSE_LINES = listOf("Amazing grace how sweet the sound", "That saved a wretch like me")
        val SECONDARY_LINES = listOf("О благодать, спасён тобой", "Я из пучины бед")
    }
}
