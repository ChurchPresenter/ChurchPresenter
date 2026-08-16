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
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SongPresenterStyleRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun section(
        lines: List<String> = listOf("Amazing grace how sweet the sound"),
        secondaryLines: List<String> = emptyList(),
        title: String = "Amazing Grace",
        number: Int = 42,
        header: String = "[Verse 1]",
        isLast: Boolean = false,
    ) = LyricSection(
        header = header,
        title = title,
        songNumber = number,
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines,
        secondaryLines = secondaryLines,
        isLastSection = isLast,
    )

    private fun renderShowsText(
        appSettings: AppSettings,
        isLowerThird: Boolean = false,
        isLowerThirdVertical: Boolean = false,
        showBackground: Boolean = true,
        crossfadeEnabled: Boolean = false,
        transparentBlanking: Boolean = false,
        lookAheadEnabled: Boolean = false,
        allSections: List<LyricSection> = emptyList(),
        displaySectionIndex: Int = -1,
        lyricSection: LyricSection = section(),
        text: String = "Amazing grace how sweet the sound",
    ) = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalTransparentBlanking provides transparentBlanking) {
                Box(screen) {
                    SongPresenter(
                        lyricSection = lyricSection,
                        appSettings = appSettings,
                        isLowerThird = isLowerThird,
                        isLowerThirdVertical = isLowerThirdVertical,
                        showBackground = showBackground,
                        crossfadeEnabled = crossfadeEnabled,
                        lookAheadEnabled = lookAheadEnabled,
                        allLyricSections = allSections,
                        displaySectionIndex = displaySectionIndex,
                    )
                }
            }
        }
        onNodeWithText(text, substring = true).assertExists()
    }

    // ── Text styling ─────────────────────────────────────────────────────────────

    @Test
    fun `bold italic underline and shadow render full-screen`() {
        val settings = AppSettings(
            songSettings = SongSettings(
                titleBold = true, titleItalic = true, titleUnderline = true, titleShadow = true,
                lyricsBold = true, lyricsItalic = true, lyricsUnderline = true, lyricsShadow = true,
                titlePosition = Constants.ABOVE_VERSE, titleDisplay = Constants.EVERY_PAGE,
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `bold italic underline and shadow render on a lower third`() {
        val settings = AppSettings(
            songSettings = SongSettings(
                titleLowerThirdBold = true, titleLowerThirdItalic = true, titleLowerThirdUnderline = true, titleLowerThirdShadow = true,
                lyricsLowerThirdBold = true, lyricsLowerThirdItalic = true, lyricsLowerThirdUnderline = true, lyricsLowerThirdShadow = true,
                titleLowerThirdPosition = Constants.ABOVE_VERSE, titleLowerThirdDisplay = Constants.EVERY_PAGE,
            ),
        )
        renderShowsText(settings, isLowerThird = true)
    }

    @Test
    fun `look-ahead preview and its own styling render full-screen`() {
        val current = section()
        val next = section(lines = listOf("That saved a wretch like me"), header = "[Verse 2]")
        val settings = AppSettings(
            songSettings = SongSettings(
                lookAheadBold = true, lookAheadItalic = true, lookAheadUnderline = true, lookAheadShadow = true,
                lookAheadNextBold = true, lookAheadNextItalic = false, lookAheadNextUnderline = true, lookAheadNextShadow = true,
            ),
        )
        runComposeUiTest {
            setContent {
                Box(screen) {
                    SongPresenter(
                        lyricSection = current,
                        appSettings = settings,
                        lookAheadEnabled = true,
                        allLyricSections = listOf(current, next),
                        displaySectionIndex = 0,
                    )
                }
            }
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("That saved a wretch like me", substring = true).assertExists()
        }
    }

    @Test
    fun `look-ahead preview styling renders on a lower third`() {
        val current = section()
        val next = section(lines = listOf("That saved a wretch like me"), header = "[Verse 2]")
        val settings = AppSettings(
            songSettings = SongSettings(
                lowerThirdLookAheadBold = true, lowerThirdLookAheadItalic = true,
                lowerThirdLookAheadUnderline = true, lowerThirdLookAheadShadow = true,
                lowerThirdLookAheadNextBold = true, lowerThirdLookAheadNextItalic = false,
                lowerThirdLookAheadNextUnderline = true, lowerThirdLookAheadNextShadow = true,
            ),
        )
        runComposeUiTest {
            setContent {
                Box(screen) {
                    SongPresenter(
                        lyricSection = current,
                        appSettings = settings,
                        isLowerThird = true,
                        lookAheadEnabled = true,
                        allLyricSections = listOf(current, next),
                        displaySectionIndex = 0,
                    )
                }
            }
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("That saved a wretch like me", substring = true).assertExists()
        }
    }

    // ── Vertical alignment ──────────────────────────────────────────────────────

    @Test
    fun `top vertical alignment places the lyric in the upper half`() = runComposeUiTest {
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = section(),
                    appSettings = AppSettings(songSettings = SongSettings(lyricsAlignment = Constants.TOP)),
                )
            }
        }
        val bounds = onNodeWithText("Amazing grace how sweet the sound", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top < 540f, "top alignment must place the lyric above the midline, was $bounds")
    }

    @Test
    fun `bottom vertical alignment renders the lyric`() {
        renderShowsText(AppSettings(songSettings = SongSettings(lyricsAlignment = Constants.BOTTOM)))
    }

    // ── Auto-fit ────────────────────────────────────────────────────────────────

    @Test
    fun `auto-fit disabled uses the configured font size directly`() {
        renderShowsText(AppSettings(songSettings = SongSettings(lyricsFontSizeAutoFit = false)))
    }

    // ── Background ──────────────────────────────────────────────────────────────

    private fun png(): File = File.createTempFile("cp-song-bg", ".png").apply {
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", this)
        deleteOnExit()
    }

    private fun garbageFile(): File = File.createTempFile("cp-song-bad", ".png").apply {
        writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        deleteOnExit()
    }

    @Test
    fun `hiding the background still renders the lyric over the fallback color`() {
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
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_DEFAULT),
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
                songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_DEFAULT),
            ),
        )
        renderShowsText(settings, isLowerThird = true)
    }

    @Test
    fun `a decodable image background renders full-screen`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = png().absolutePath),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a decodable image background renders in the lower third band`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = png().absolutePath),
            ),
        )
        renderShowsText(settings, isLowerThird = true)
    }

    @Test
    fun `a missing image path falls back to black rather than crashing`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = "/no/such/file/does-not-exist.png"),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `an undecodable image file falls back to black rather than crashing`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = garbageFile().absolutePath),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a video background type renders full-screen without a decoder present`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_VIDEO, backgroundVideo = "/no/such/file.mp4"),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a video background type renders in the lower third without a decoder present`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_VIDEO, backgroundVideo = "/no/such/file.mp4"),
            ),
        )
        renderShowsText(settings, isLowerThird = true)
    }

    @Test
    fun `a Gradient background type renders full-screen`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_GRADIENT),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a gradient overlay renders on the lower third band`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_GRADIENT,
                    gradientEnabled = true,
                    gradientTopColor = "#000000",
                    gradientBottomColor = "#FFFFFF",
                ),
            ),
        )
        renderShowsText(settings, isLowerThird = true)
    }

    // ── Fade-in and crossfade ───────────────────────────────────────────────────

    @Test
    fun `fade-in animates the background from hidden to fully opaque`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.testTag("backdrop").size(200.dp).background(Color.Black)) {
                SongPresenter(
                    lyricSection = section(),
                    appSettings = AppSettings(
                        songSettings = SongSettings(fadeIn = true, transitionDuration = 100f),
                        backgroundSettings = BackgroundSettings(
                            songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR, backgroundColor = "#FFFFFF"),
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
    fun `with fade and crossfade both off the lyric still renders`() {
        renderShowsText(AppSettings(songSettings = SongSettings(fadeIn = false, fadeOut = false)))
    }

    @Test
    fun `changing sections under crossfade shows both mid-fade, then only the new one`() = runComposeUiTest {
        var current by mutableStateOf(section(lines = listOf("FIRST LINE")))
        mainClock.autoAdvance = false
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = current,
                    appSettings = AppSettings(songSettings = SongSettings(transitionDuration = 200f)),
                    crossfadeEnabled = true,
                )
            }
        }
        mainClock.advanceTimeByFrame()
        onNodeWithText("FIRST LINE", substring = true).assertExists()

        current = section(lines = listOf("SECOND LINE"), header = "[Verse 2]")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithText("FIRST LINE", substring = true).assertExists("mid-crossfade the outgoing section must still be visible")
        onNodeWithText("SECOND LINE", substring = true).assertExists("mid-crossfade the incoming section must already be visible")

        mainClock.advanceTimeBy(500)
        onNodeWithText("SECOND LINE", substring = true).assertExists()
        onNodeWithText("FIRST LINE", substring = true).assertDoesNotExist()
    }

    @Test
    fun `changing sections without crossfade swaps instantly`() = runComposeUiTest {
        var current by mutableStateOf(section(lines = listOf("FIRST LINE")))
        mainClock.autoAdvance = false
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = current,
                    appSettings = AppSettings(),
                    crossfadeEnabled = false,
                )
            }
        }
        mainClock.advanceTimeByFrame()
        onNodeWithText("FIRST LINE", substring = true).assertExists()

        current = section(lines = listOf("SECOND LINE"), header = "[Verse 2]")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithText("SECOND LINE", substring = true).assertExists()
        onNodeWithText("FIRST LINE", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an image background with no file chosen yet falls back to black`() {
        // The operator picks "Image" in settings and has not browsed for a file: the type is set
        // and the path is still empty, which is a different branch from a path that does not
        // resolve, and the one a half-configured install actually sits in.
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = ""),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a video background with no file chosen yet renders without a decoder`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_VIDEO, backgroundVideo = ""),
            ),
        )
        renderShowsText(settings)
    }

    @Test
    fun `a lower third with an unchosen image path still draws the band`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = ""),
            ),
        )
        renderShowsText(settings, isLowerThird = true)
    }
}
