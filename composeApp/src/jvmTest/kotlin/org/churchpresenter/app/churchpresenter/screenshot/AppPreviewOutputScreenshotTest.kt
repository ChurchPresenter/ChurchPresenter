@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.DictionaryPresenter
import org.churchpresenter.app.churchpresenter.presenter.LottieFrame
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdOffscreenRenderer
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.PresentationPresenter
import org.churchpresenter.app.churchpresenter.presenter.QAPresenter
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.skia.Bitmap
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test

class AppPreviewOutputScreenshotTest {

    private val screen = Modifier.fillMaxSize()

    private fun settings(): AppSettings {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        // Exactly the settings the tab previews compose with, so an output snapshot shows what
        // that preview's Screen 1 shows — no background of its own.
        return library()
    }

    private fun output(name: String, settings: AppSettings = settings(), content: @Composable () -> Unit) =
        runSkikoComposeUiTest(
            size = Size(OUTPUT_WIDTH.toFloat(), OUTPUT_HEIGHT.toFloat()) * PREVIEW_DENSITY,
            density = Density(PREVIEW_DENSITY),
        ) {
            setContent {
                Box(screen) {
                    PresenterScreen(appSettings = settings) { content() }
                }
            }
            waitForIdle()
            onRoot().captureRoboImage("$SCREENSHOT_ROOT/output/$name.png")
        }

    private fun verse() = SelectedVerse(
        translationFileName = "kjv1769.spb",
        bibleAbbreviation = "KJV",
        bibleName = "King James Version",
        bookName = "Genesis",
        chapter = 1,
        verseNumber = 1,
        verseText = "In the beginning God created the heaven and the earth.",
    )

    private fun slideBitmap(): ImageBitmap =
        PDDocument.load(File(LIBRARY, "Decks/Sermon.pdf")).use { doc ->
            PDFRenderer(doc).renderImageWithDPI(2, 120f).toComposeImageBitmap()
        }

    @Test
    fun `bible verse on screen`() = output("bible") {
        BiblePresenter(selectedVerses = listOf(verse()), appSettings = settings())
    }

    @Test
    fun `song lyrics on screen`() = output("song") {
        SongPresenter(
            lyricSection = LyricSection(
                header = "[Verse 1]",
                title = "Amazing Grace",
                songNumber = 12,
                type = Constants.SECTION_TYPE_VERSE,
                lines = listOf(
                    "Amazing grace! how sweet the sound",
                    "That saved a wretch like me!",
                    "I once was lost, but now am found,",
                    "Was blind, but now I see.",
                ),
            ),
            appSettings = settings(),
        )
    }

    // The gallery image `the pictures tab` clicks and puts live, so the two shots agree.
    @Test
    fun `picture on screen`() = output("picture") {
        PicturePresenter(
            imagePath = File(LIBRARY, "Gallery/04 Church.png").absolutePath,
        )
    }

    @Test
    fun `presentation slide on screen`() = output("presentation") {
        PresentationPresenter(frame = null, slide = slideBitmap())
    }

    @Test
    fun `announcement on screen`() = output("announcement") {
        AnnouncementsPresenter(
            text = "Christ is risen — He is risen indeed!",
            appSettings = settings(),
        )
    }

    @Test
    fun `countdown on screen`() = output("timer_countdown") {
        AnnouncementsPresenter(text = "05:00", appSettings = settings())
    }

    @Test
    fun `canvas scene on screen`() = output("canvas") {
        ScenePresenter(scene = previewScenes().first())
    }

    /**
     * The same preset `the lower third tab` selects and puts live, read from the library that
     * preview's settings point at, so the two shots show the same band.
     *
     * Handed a pre-rendered frame rather than a composition, which is also what a live output
     * draws once the render cache is warm. The alternative — passing the composition and letting
     * the live Compottie painter draw it — cannot be photographed here: that painter arrives
     * through a `produceState` that loads fonts and assets on an IO dispatcher, so `waitForIdle`
     * returns before it exists and the capture is a black screen with nothing to say why.
     */
    @Test
    fun `lower third on screen`() {
        val appSettings = settings()
        val frame = lowerThirdFrame(File(LIBRARY, "Lower Thirds/Guest Speaker.json").readText())
        output("lower_third", appSettings) {
            LowerThirdPresenter(
                composition = null,
                progress = { LOWER_THIRD_PROGRESS },
                appSettings = appSettings,
                frame = frame,
            )
        }
    }

    /**
     * Renders the design off-screen at mid-play and wraps it the way `LottieFrameStream` does —
     * ARGB ints written little-endian are BGRA bytes, which is the N32 layout Skia expects.
     *
     * The emptiness check is the point of doing it here rather than inline: a blank render is
     * otherwise indistinguishable from a lower third that is simply off screen, and it arrives as
     * a committed all-black PNG that a reviewer has to work out for themselves.
     */
    private fun lowerThirdFrame(lottieJson: String): LottieFrame = runBlocking {
        val argb = LowerThirdOffscreenRenderer(OUTPUT_WIDTH, OUTPUT_HEIGHT)
            .withSession(lottieJson, initialProgress = LOWER_THIRD_PROGRESS) { renderFrame ->
                // The scene is pumped until the design actually appears, not for a fixed number of
                // frames: the composition finishing its parse is not the same event as the painter
                // that draws it existing — that one arrives from an IO dispatcher a few frames
                // later, and every frame before it is empty.
                var pixels = renderFrame(LOWER_THIRD_PROGRESS)
                var pumped = 1
                while (pixels.none { (it ushr 24) and 0xFF > 8 }) {
                    check(pumped++ < PAINTER_FRAMES) { "the lower third rendered blank" }
                    delay(FRAME_MS)
                    pixels = renderFrame(LOWER_THIRD_PROGRESS)
                }
                pixels.copyOf()
            }
        val bytes = ByteArray(argb.size * 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(argb)
        val bitmap = Bitmap().apply {
            allocN32Pixels(OUTPUT_WIDTH, OUTPUT_HEIGHT)
            installPixels(bytes)
            setImmutable()
        }
        LottieFrame(bitmap.asComposeImageBitmap(), 0, bitmap)
    }

    @Test
    fun `question on screen`() = output("qa") {
        QAPresenter(
            question = Question(
                id = "q1",
                text = "How do we know the resurrection actually happened?",
                submitterName = "Sarah",
                timestamp = 1_770_000_000_000,
                status = QuestionStatus.APPROVED,
                voteCount = 12,
            ),
        )
    }

    @Test
    fun `dictionary entry on screen`() = output("dictionary") {
        DictionaryPresenter(
            entry = StrongsEntry(
                number = "H2617",
                word = "חֶסֶד",
                transliteration = "chêçêd",
                pronunciation = "kheh'-sed",
                definition = "kindness; by implication (towards God) piety; rarely (by opposition) reproof",
                kjvUsage =
                    "favour, good deed(-liness, -ness), kindly, (loving-) kindness, merciful (kindness), mercy, pity",
            ),
            dictionarySettings = settings().dictionarySettings,
        )
    }

    private companion object {
        /** The output every one of these is drawn onto, in pixels at density 1. */
        const val OUTPUT_WIDTH = 1920
        const val OUTPUT_HEIGHT = 1080

        /**
         * Mid-play, not `1f`: every layer's `op` is the composition's own, so on the last frame
         * they have all gone out and the design is off screen rather than blank by accident.
         */
        const val LOWER_THIRD_PROGRESS = 0.5f
        const val FRAME_MS = 16L
        /** ~1s of scene time for the painter to arrive; only reached when it never does. */
        const val PAINTER_FRAMES = 60
    }
}
