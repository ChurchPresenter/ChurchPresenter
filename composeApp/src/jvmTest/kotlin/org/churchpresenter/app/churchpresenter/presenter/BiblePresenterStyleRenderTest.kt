package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundConfig
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BiblePresenterStyleRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun verse(
        text: String,
        number: Int = 16,
        book: String = "John",
        chapter: Int = 3,
        abbreviation: String = "KJV",
        fileName: String = "",
    ) = SelectedVerse(
        translationFileName = fileName,
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = book,
        chapter = chapter,
        verseNumber = number,
        verseText = text,
    )

    private fun renderShowsText(
        appSettings: AppSettings,
        isLowerThird: Boolean = false,
        isLowerThirdVertical: Boolean = false,
        showBackground: Boolean = true,
        crossfadeEnabled: Boolean = false,
        transparentBlanking: Boolean = false,
        verses: List<SelectedVerse> = listOf(verse("For God so loved the world")),
    ) = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalTransparentBlanking provides transparentBlanking) {
                Box(screen) {
                    BiblePresenter(
                        selectedVerses = verses,
                        appSettings = appSettings,
                        isLowerThird = isLowerThird,
                        isLowerThirdVertical = isLowerThirdVertical,
                        showBackground = showBackground,
                        crossfadeEnabled = crossfadeEnabled,
                    )
                }
            }
        }
        verses.forEach { onNodeWithText(it.verseText, substring = true).assertExists() }
    }

    private fun styledTranslation(fileName: String, alignment: String = Constants.LEFT) = BibleTranslationSettings(
        fileName = fileName,
        textBold = true, textItalic = true, textUnderline = true, textShadow = true,
        referenceBold = true, referenceItalic = true, referenceUnderline = true, referenceShadow = true,
        lowerThirdTextBold = true,
        lowerThirdTextItalic = true,
        lowerThirdTextUnderline = true,
        lowerThirdTextShadow = true,
        lowerThirdReferenceBold = true,
        lowerThirdReferenceItalic = true,
        lowerThirdReferenceUnderline = true,
        lowerThirdReferenceShadow = true,
        textHorizontalAlignment = alignment,
        referencePosition = Constants.POSITION_ABOVE,
        lowerThirdReferencePosition = Constants.POSITION_ABOVE,
    )

    @Test
    fun `bold italic underline and shadow render in the full-screen translation stack`() {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(styledTranslation("a.spb", Constants.CENTER), styledTranslation("b.spb", Constants.CENTER)),
            ),
        )
        renderShowsText(
            settings,
            verses = listOf(verse("FIRST STYLED", fileName = "a.spb"), verse("SECOND STYLED", fileName = "b.spb")),
        )
    }

    @Test
    fun `a translation aligned right in the full-screen stack still renders`() {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(styledTranslation("a.spb", Constants.RIGHT), styledTranslation("b.spb", Constants.RIGHT)),
            ),
        )
        renderShowsText(
            settings,
            verses = listOf(verse("FIRST RIGHT", fileName = "a.spb"), verse("SECOND RIGHT", fileName = "b.spb")),
        )
    }

    @Test
    fun `bold italic underline and shadow render in the lower-third parallel layout`() {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(styledTranslation("a.spb"), styledTranslation("b.spb")),
            ),
        )
        renderShowsText(
            settings,
            isLowerThird = true,
            verses = listOf(verse("FIRST STYLED", fileName = "a.spb"), verse("SECOND STYLED", fileName = "b.spb")),
        )
    }

    @Test
    fun `a long bilingual passage forces the lower-third parallel fit search to shrink both sides`() {
        val settings = AppSettings(bibleSettings = BibleSettings(secondaryBible = "RST"))
        renderShowsText(
            settings,
            isLowerThird = true,
            verses = listOf(
                verse("FIRST ${"and it came to pass in those days ".repeat(40)}"),
                verse("SECOND ${"и было в те дни ".repeat(40)}", abbreviation = "RST"),
            ),
        )
    }

    @Test
    fun `key output colours the multi-translation divider white in the full-screen stack`() {
        val settings = AppSettings(
            bibleSettings = BibleSettings(
                multiTranslationDivider = true,
            ).withTranslations(
                listOf(BibleTranslationSettings(fileName = "a.spb"), BibleTranslationSettings(fileName = "b.spb")),
            ),
        )
        runComposeUiTest {
            setContent {
                Box(screen) {
                    BiblePresenter(
                        selectedVerses = listOf(
                            verse("FIRST TEXT", fileName = "a.spb"),
                            verse("SECOND TEXT", fileName = "b.spb"),
                        ),
                        appSettings = settings,
                        outputRole = Constants.OUTPUT_ROLE_KEY,
                    )
                }
            }
            onNodeWithText("FIRST TEXT", substring = true).assertExists()
            onNodeWithText("SECOND TEXT", substring = true).assertExists()
        }
    }

    @Test
    fun `centering both text and reference renders in the lower-third parallel layout`() {
        val centered = BibleTranslationSettings(
            textHorizontalAlignment = Constants.CENTER,
            referenceHorizontalAlignment = Constants.CENTER,
            lowerThirdTextHorizontalAlignment = Constants.CENTER,
            lowerThirdReferenceHorizontalAlignment = Constants.CENTER,
        )
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(centered.copy(fileName = "a.spb"), centered.copy(fileName = "b.spb")),
            ),
        )
        renderShowsText(
            settings,
            isLowerThird = true,
            verses = listOf(verse("FIRST CENTERED", fileName = "a.spb"), verse("SECOND CENTERED", fileName = "b.spb")),
        )
    }

    @Test
    fun `text right and reference left renders in the lower-third parallel layout`() {
        val swapped = BibleTranslationSettings(
            textHorizontalAlignment = Constants.RIGHT,
            referenceHorizontalAlignment = Constants.LEFT,
            lowerThirdTextHorizontalAlignment = Constants.RIGHT,
            lowerThirdReferenceHorizontalAlignment = Constants.LEFT,
        )
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(swapped.copy(fileName = "a.spb"), swapped.copy(fileName = "b.spb")),
            ),
        )
        renderShowsText(
            settings,
            isLowerThird = true,
            verses = listOf(verse("FIRST SWAPPED", fileName = "a.spb"), verse("SECOND SWAPPED", fileName = "b.spb")),
        )
    }

    @Test
    fun `top vertical alignment places the verse in the upper half`() = runComposeUiTest {
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse("For God so loved the world")),
                    appSettings = AppSettings(bibleSettings = BibleSettings(verticalAlignment = Constants.TOP)),
                )
            }
        }
        val bounds = onNodeWithText("For God so loved the world", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top < 540f, "top alignment must place the verse above the midline, was $bounds")
    }

    @Test
    fun `middle vertical alignment renders the verse`() {
        renderShowsText(AppSettings(bibleSettings = BibleSettings(verticalAlignment = Constants.MIDDLE)))
    }

    @Test
    fun `isLowerThirdVertical stacks both languages in a single column`() {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(
                    BibleTranslationSettings(
                        fileName = "a.spb",
                        lowerThirdReferencePosition = Constants.POSITION_ABOVE,
                    ),
                    BibleTranslationSettings(
                        fileName = "b.spb",
                        lowerThirdReferencePosition = Constants.POSITION_ABOVE,
                    ),
                ),
            ),
        )
        renderShowsText(
            settings,
            isLowerThird = true,
            isLowerThirdVertical = true,
            verses = listOf(verse("FIRST STACKED", fileName = "a.spb"), verse("SECOND STACKED", fileName = "b.spb")),
        )
    }

    private fun png(): File = File.createTempFile("cp-bible-bg", ".png").apply {
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", this)
        deleteOnExit()
    }

    private fun garbageFile(): File = File.createTempFile("cp-bible-bad", ".png").apply {
        writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        deleteOnExit()
    }

    @Test
    fun `hiding the background still renders the verse over the fallback color`() {
        renderShowsText(AppSettings(), showBackground = false)
    }

    @Test
    fun `hiding the background renders transparently for browser-source scenes`() {
        renderShowsText(AppSettings(), showBackground = false, transparentBlanking = true)
    }

    @Test
    fun `a Default full-screen background type inherits the global default`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_COLOR,
                defaultBackgroundColor = "#112233",
                bibleBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_DEFAULT),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a Default lower-third background type inherits the global lower-third default`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdBackgroundColor = "#112233",
                bibleLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_DEFAULT),
            ),
        )
        renderShowsText(settings, isLowerThird = true)
    }

    @Test
    fun `a decodable image background renders full-screen`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_IMAGE,
                    backgroundImage = png().absolutePath,
                ),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a decodable image background renders in the lower third band`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_IMAGE,
                    backgroundImage = png().absolutePath,
                ),
            ),
        )
        renderShowsText(settings, isLowerThird = true)
    }

    @Test
    fun `a missing image path falls back to black rather than crashing`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_IMAGE,
                    backgroundImage = "/no/such/file/does-not-exist.png",
                ),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `an undecodable image file falls back to black rather than crashing`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_IMAGE,
                    backgroundImage = garbageFile().absolutePath,
                ),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a video background type renders full-screen without a decoder present`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_VIDEO,
                    backgroundVideo = "/no/such/file.mp4",
                ),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a video background type renders in the lower third without a decoder present`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_VIDEO,
                    backgroundVideo = "/no/such/file.mp4",
                ),
            ),
        )
        renderShowsText(settings, isLowerThird = true)
    }

    @Test
    fun `a gradient overlay renders on the lower third band`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_GRADIENT,
                    gradientEnabled = true,
                    gradientTopColor = "#000000",
                    gradientBottomColor = "#FFFFFF",
                ),
            ),
        )
        renderShowsText(settings, isLowerThird = true)
    }

    @Test
    fun `fade-in animates the background from hidden to fully opaque`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.testTag("backdrop").size(200.dp).background(Color.Black)) {
                BiblePresenter(
                    selectedVerses = listOf(verse("For God so loved the world")),
                    appSettings = AppSettings(
                        bibleSettings = BibleSettings(fadeIn = true, transitionDuration = 100f),
                        backgroundSettings = BackgroundSettings(
                            bibleBackground = BackgroundConfig(
                                backgroundType = Constants.BACKGROUND_COLOR,
                                backgroundColor = "#FFFFFF",
                            ),
                        ),
                    ),
                )
            }
        }
        val before = onNodeWithTag("backdrop").captureToImage().toPixelMap()[2, 2]
        assertTrue(before.red < 0.5f, "the fade must start from a hidden background, was $before")

        mainClock.advanceTimeBy(500)
        val after = onNodeWithTag("backdrop").captureToImage().toPixelMap()[2, 2]
        assertTrue(after.red > 0.9f, "once the fade completes the background must be fully opaque, was $after")
    }

    @Test
    fun `with fade and crossfade both off the verse still renders`() {
        renderShowsText(AppSettings(bibleSettings = BibleSettings(fadeIn = false, fadeOut = false)))
    }

    @Test
    fun `changing verses under crossfade shows both mid-fade, then only the new one`() = runComposeUiTest {
        var verses by mutableStateOf(listOf(verse("FIRST VERSE", number = 16)))
        mainClock.autoAdvance = false
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = verses,
                    appSettings = AppSettings(bibleSettings = BibleSettings(transitionDuration = 200f)),
                    crossfadeEnabled = true,
                )
            }
        }
        mainClock.advanceTimeByFrame()
        onNodeWithText("FIRST VERSE", substring = true).assertExists()

        verses = listOf(verse("SECOND VERSE", number = 17))
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithText(
            "FIRST VERSE",
            substring = true,
        ).assertExists("mid-crossfade the outgoing verse must still be visible")
        onNodeWithText(
            "SECOND VERSE",
            substring = true,
        ).assertExists("mid-crossfade the incoming verse must already be visible")

        mainClock.advanceTimeBy(500)
        onNodeWithText("SECOND VERSE", substring = true).assertExists()
        onNodeWithText("FIRST VERSE", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `changing verses without crossfade swaps instantly`() = runComposeUiTest {
        var verses by mutableStateOf(listOf(verse("FIRST VERSE", number = 16)))
        mainClock.autoAdvance = false
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = verses,
                    appSettings = AppSettings(),
                    crossfadeEnabled = false,
                )
            }
        }
        mainClock.advanceTimeByFrame()
        onNodeWithText("FIRST VERSE", substring = true).assertExists()

        verses = listOf(verse("SECOND VERSE", number = 17))
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithText("SECOND VERSE", substring = true).assertExists()
        onNodeWithText("FIRST VERSE", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an image background with no file chosen yet falls back to black`() {
        // The operator picks "Image" in settings and has not browsed for a file: the type is set
        // and the path is still empty, which is a different branch from a path that does not
        // resolve, and the one a half-configured install actually sits in.
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = ""),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a video background with no file chosen yet renders without a decoder`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_VIDEO, backgroundVideo = ""),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a lower third with an unchosen image path still draws the band`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_IMAGE,
                    backgroundImage = "",
                ),
            ),
        )
        renderShowsText(settings, isLowerThird = true)
    }
}
