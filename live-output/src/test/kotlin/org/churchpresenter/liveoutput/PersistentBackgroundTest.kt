package org.churchpresenter.liveoutput

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The background layer under Bible and songs, drawn through a real off-screen output: it stays up
 * while a slide fades out and in, follows the slide's fade only while the display is cleared, and
 * crossfades to a new background when the content's background changes.
 *
 * Read from the top-left corner, which no text reaches, and compared within a tolerance -- the
 * colour is what is asserted, not how a platform rounds it.
 */
@OptIn(ExperimentalTestApi::class)
class PersistentBackgroundTest {

    private val bibleBlue = Color(0xFF336699)

    private val blue = BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR, backgroundColor = "#336699")

    private fun settings(): AppSettings {
        val base = AppSettings(
            bibleSettings = BibleSettings(transitionDuration = 0f),
            songSettings = SongSettings(transitionDuration = 0f),
            backgroundSettings = BackgroundSettings(
                bibleBackground = blue,
                songBackground = blue,
            ),
        )
        return base.copy(
            projectionSettings = base.projectionSettings.copy(
                outputProfiles = listOf(
                    OutputProfile(id = "p", bibleSettings = base.bibleSettings, songSettings = base.songSettings),
                ),
            ),
        )
    }

    private fun output(
        mode: Presenting,
        seed: PresenterManager.() -> Unit,
        body: ComposeUiTest.(PresenterManager) -> Unit,
    ) = runComposeUiTest {
            val manager = PresenterManager(showPresenterWindowInitially = false).apply {
                setPresentingMode(mode)
                seed()
            }
            setContent {
                Box(Modifier.size(160.dp, 90.dp).testTag(OUTPUT)) {
                    OffscreenOutputContent(
                        OffscreenOutputContext(
                            presenterManager = manager,
                            appSettingsState = mutableStateOf(settings()),
                            screenAssignmentState = mutableStateOf(ScreenAssignment(activeProfileId = "p")),
                            effectiveModeState = mutableStateOf(mode),
                            outputIndex = 0,
                            kind = OffscreenOutputKind.NDI,
                        ),
                        transparentBlanking = false,
                    )
                }
            }
            waitForIdle()
            body(manager)
        }

    private fun ComposeUiTest.corner(): Color {
        waitForIdle()
        return onNodeWithTag(OUTPUT).captureToImage().toPixelMap()[1, 1]
    }

    private fun assertColour(expected: Color, actual: Color, what: String) {
        val close = abs(expected.red - actual.red) < TOLERANCE &&
            abs(expected.green - actual.green) < TOLERANCE &&
            abs(expected.blue - actual.blue) < TOLERANCE
        assertTrue(close, "$what: expected $expected, drew $actual")
    }

    private val verse = SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved")

    @Test
    fun `the background stays up while a verse fades out for the next`() =
        output(Presenting.BIBLE, { setDisplayedVerses(listOf(verse)) }) { manager ->
            assertColour(bibleBlue, corner(), "with the verse up")
            manager.setBibleTransitionAlpha(0f)
            assertColour(bibleBlue, corner(), "between verses")
        }

    @Test
    fun `clearing the display fades the background with the verse`() =
        output(Presenting.BIBLE, { setDisplayedVerses(listOf(verse)) }) { manager ->
            manager.requestClearDisplay()
            manager.setBibleTransitionAlpha(0f)
            assertColour(Color.Black, corner(), "with the display cleared")
        }

    @Test
    fun `a song's own background replaces the songs background when it goes up`() {
        val red = SongBackground(type = SongBackgroundType.COLOR, color = "#CC2200")
        output(Presenting.LYRICS, { setDisplayedLyricSection(LyricSection(title = "One")) }) { manager ->
            assertColour(bibleBlue, corner(), "the songs background")
            manager.setDisplayedLyricSection(LyricSection(title = "Two", background = red))
            assertColour(Color(0xFFCC2200), corner(), "the next song's own background")
        }
    }

    private companion object {
        const val OUTPUT = "output"
        const val TOLERANCE = 0.03f
    }
}
