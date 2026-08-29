@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.dialogs.SONG_BACKGROUND_PANEL_HEIGHT
import org.churchpresenter.app.churchpresenter.dialogs.SONG_BACKGROUND_PANEL_WIDTH
import org.churchpresenter.app.churchpresenter.dialogs.SongBackgroundPanel
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The Background panel on its own, at the size it really opens at.
 *
 * Shot apart from the editor because it is where every one of a song's background choices is made
 * and each state is worth a reviewer's eye — inside `EditSongDialogScreenshotTest` they would be a
 * dozen shots of the whole 1120x760 window differing in one corner.
 *
 * The library's Images and Videos tiles come from the stock-background folder under `user.home`,
 * which is per test fork and therefore empty, plus the bundled set from app resources — so what
 * these render does not depend on what the machine happens to have downloaded.
 */
class SongBackgroundPanelScreenshotTest {

    private fun shoot(
        name: String,
        background: SongBackground = SongBackground(),
        lowerThirdBackground: SongBackground = SongBackground(),
        applyToSongbook: (() -> Unit)? = {},
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.size(SONG_BACKGROUND_PANEL_WIDTH, SONG_BACKGROUND_PANEL_HEIGHT)) {
                            SongBackgroundPanel(
                                background = background,
                                lowerThirdBackground = lowerThirdBackground,
                                onBackgroundChange = {},
                                onLowerThirdBackgroundChange = {},
                                sampleLine = SAMPLE_LINE,
                                onApplyToSongbook = applyToSongbook,
                                onDismiss = {},
                            )
                        }
                    }
                }
            }
            drive()
            waitForIdle()
            captureTo(file, 0)
        }
    }

    // ── The two modes ───────────────────────────────────────────────────────────────────────────

    /** Inheriting: the library is faded and the preview says what it is following. */
    @Test
    fun `a song inheriting the settings background`() = shoot("inherited")

    @Test
    fun `a named colour chosen`() = shoot("colour", background = NAVY)

    @Test
    fun `a gradient chosen`() = shoot("gradient", background = DUSK)

    /** A colour outside the named set, so the custom tile is the selected one. */
    @Test
    fun `a colour of the operator's own`() =
        shoot("custom_colour", background = SongBackground(type = SongBackgroundType.COLOR, color = "#abcdef"))

    // ── Dim and blur ────────────────────────────────────────────────────────────────────────────

    /** The heaviest of the four Look presets, so dim and blur are both visibly at work. */
    @Test
    fun `the cinema look`() = shoot("look_cinema", background = DUSK.copy(dim = 65, blur = 12))

    @Test
    fun `dim and blur turned all the way off`() = shoot("look_none", background = DUSK.copy(dim = 0, blur = 0))

    // ── The other categories ────────────────────────────────────────────────────────────────────

    @Test
    fun `the pictures category`() = shoot("images", background = DUSK) {
        onNodeWithText(IMAGES).performClick()
        waitForIdle()
    }

    @Test
    fun `the clips category`() = shoot("videos", background = DUSK) {
        onNodeWithText(VIDEOS).performClick()
        waitForIdle()
    }

    // ── The lower third's own background ────────────────────────────────────────────────────────

    /** Switching target shows the band's background, which here is set while the full screen is not. */
    @Test
    fun `the lower third target`() = shoot("lower_third", lowerThirdBackground = BAND) {
        onNodeWithText(LOWER_THIRD).performClick()
        waitForIdle()
    }

    // ── Without a song book to apply to ─────────────────────────────────────────────────────────

    /** A song with no song book yet: the footer button is not offered at all. */
    @Test
    fun `no song book to apply to`() = shoot("no_songbook", background = NAVY, applyToSongbook = null)

    private companion object {
        const val SECTION = "songBackgroundPanel"

        const val SAMPLE_LINE = "Amazing grace how sweet the sound"
        const val IMAGES = "Images"
        const val VIDEOS = "Videos"
        const val LOWER_THIRD = "Lower third"

        val NAVY = SongBackground(type = SongBackgroundType.COLOR, color = "#0d1b2a", dim = 25, blur = 3)
        val DUSK = SongBackground(
            type = SongBackgroundType.GRADIENT, color = "#131a3a", colorEnd = "#3a2352", dim = 45, blur = 6,
        )
        val BAND = SongBackground(type = SongBackgroundType.COLOR, color = "#2a1130", dim = 65)
    }
}
