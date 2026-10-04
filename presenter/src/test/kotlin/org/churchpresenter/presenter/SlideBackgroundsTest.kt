@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The full-screen backgrounds a Bible or song slide draws on an output's background layer, alone:
 * what they draw, when they draw nothing, and how they change -- and the presenters themselves
 * drawing no background of their own when the layer below does.
 *
 * Read from the top-left corner, which no text reaches, against a grey under it so "drew nothing"
 * can be told from "drew black"; colours are compared within a tolerance.
 */
class SlideBackgroundsTest {

    private val blue = Color(0xFF336699)
    private val red = Color(0xFFCC2200)
    private val under = Color(0xFF808080)

    private fun colour(hex: String) = BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR, backgroundColor = hex)

    private fun settings(fadeIn: Boolean = false) = AppSettings(
        bibleSettings = BibleSettings(transitionDuration = 0f, fadeIn = fadeIn),
        songSettings = SongSettings(transitionDuration = 0f, fadeIn = fadeIn),
        backgroundSettings = BackgroundSettings(bibleBackground = colour("#336699"), songBackground = colour("#336699")),
    )

    private val verse = SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved")

    private fun drawn(body: ComposeUiTest.() -> Unit = {}, content: @Composable () -> Unit): Color {
        var corner = Color.Unspecified
        runComposeUiTest {
            setContent {
                Box(Modifier.size(160.dp, 90.dp).background(under).testTag(TAG)) { content() }
            }
            body()
            waitForIdle()
            corner = onNodeWithTag(TAG).captureToImage().toPixelMap()[1, 1]
        }
        return corner
    }

    private fun assertColour(expected: Color, actual: Color, what: String) {
        val close = abs(expected.red - actual.red) < TOLERANCE &&
            abs(expected.green - actual.green) < TOLERANCE &&
            abs(expected.blue - actual.blue) < TOLERANCE
        assertTrue(close, "$what: expected $expected, drew $actual")
    }

    @Composable
    private fun Bible(
        verses: List<SelectedVerse> = listOf(verse),
        appSettings: AppSettings = settings(),
        isLowerThird: Boolean = false,
        alpha: Float = 1f,
        clearing: Boolean = false,
    ) = BibleSlideBackground(
        modifier = Modifier,
        selectedVerses = verses,
        appSettings = appSettings,
        isLowerThird = isLowerThird,
        outputRole = Constants.OUTPUT_ROLE_NORMAL,
        transitionAlpha = alpha,
        clearing = clearing,
        showBackground = true,
        bibleTranslations = emptyList(),
    )

    @Composable
    private fun Song(section: LyricSection = LyricSection(title = "One"), isLowerThird: Boolean = false) =
        SongSlideBackground(
            modifier = Modifier,
            lyricSection = section,
            appSettings = settings(),
            isLowerThird = isLowerThird,
            transitionAlpha = 1f,
            clearing = false,
            showBackground = true,
        )

    // ── Bible ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a verse's background is drawn on its own`() = assertColour(blue, drawn { Bible() }, "the Bible background")

    @Test
    fun `no verse, no background`() = assertColour(under, drawn { Bible(verses = emptyList()) }, "nothing up")

    @Test
    fun `a lower third draws none, its band carries it`() =
        assertColour(under, drawn { Bible(isLowerThird = true) }, "a lower third")

    @Test
    fun `the background stays up while the verse fades between slides`() =
        assertColour(blue, drawn { Bible(alpha = 0f) }, "between verses")

    @Test
    fun `clearing fades the background with the verse`() =
        assertColour(under, drawn { Bible(alpha = 0f, clearing = true) }, "cleared")

    @Test
    fun `a fade-in ends fully up`() =
        assertColour(blue, drawn { Bible(appSettings = settings(fadeIn = true)) }, "after its fade-in")

    // ── Songs ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a song's own background wins over the songs background`() {
        val own = SongBackground(type = SongBackgroundType.COLOR, color = "#CC2200")
        assertColour(red, drawn { Song(LyricSection(title = "One", background = own)) }, "the song's own")
    }

    @Test
    fun `a song on a lower third draws no background`() =
        assertColour(under, drawn { Song(isLowerThird = true) }, "a lower third")

    @Test
    fun `a new background replaces the old one once it can be drawn`() {
        var section by mutableStateOf(LyricSection(title = "One"))
        val corner = drawn(body = {
            section = LyricSection(
                title = "Two",
                background = SongBackground(type = SongBackgroundType.COLOR, color = "#CC2200"),
            )
        }) { Song(section) }
        assertColour(red, corner, "the next section's background")
    }

    @Test
    fun `a picture that is gone is drawn as black rather than waited for`() {
        val gone = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = "/nowhere/at/all.png")
        val pictured = settings().let { it.copy(backgroundSettings = it.backgroundSettings.copy(bibleBackground = gone)) }
        assertColour(Color.Black, drawn { Bible(appSettings = pictured) }, "a missing picture")
    }

    // ── The presenters, over a background layer ─────────────────────────────────────────────────

    @Test
    fun `a Bible slide over the background layer draws no background of its own`() = assertColour(
        under,
        drawn { BiblePresenter(selectedVerses = listOf(verse), appSettings = settings(), drawsBackground = false) },
        "the slide alone",
    )

    @Test
    fun `a song slide over the background layer draws no background of its own`() = assertColour(
        under,
        drawn {
            SongPresenter(lyricSection = section("Amazing grace"), appSettings = settings(), drawsBackground = false)
        },
        "the slide alone",
    )

    private companion object {
        const val TAG = "slide_background"
        const val TOLERANCE = 0.03f
    }
}
