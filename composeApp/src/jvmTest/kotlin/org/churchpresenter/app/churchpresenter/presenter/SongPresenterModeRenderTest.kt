package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test

/**
 * The two song layouts the everyday verse-on-a-slide tests don't reach: look-ahead and line-by-line.
 *
 * Look-ahead is for the band — the current section plus a preview of what's coming, so the
 * transition stays smooth. Its whole layout branch is separate from the normal one, and if the
 * "next" preview silently stops rendering the musicians fly blind while the congregation sees no
 * difference. Line-by-line mode shows one line at a time (common on a lower third); the failure that
 * matters is showing the wrong line — every other line of the section must stay off screen.
 *
 * Both assert on the lyric text that lands on screen, so the whole selection path runs and nothing
 * races a crossfade.
 */
@OptIn(ExperimentalTestApi::class)
class SongPresenterModeRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun section(vararg lines: String, header: String = "[Verse 1]") = LyricSection(
        header = header,
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines.toList(),
        secondaryLines = emptyList(),
    )

    @Test
    fun `look-ahead shows the current section and a preview of the next`() = runComposeUiTest {
        val current = section("Amazing grace how sweet the sound")
        val next = section("That saved a wretch like me", header = "[Verse 2]")
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = current,
                    appSettings = AppSettings(),   // lookAheadDisplayMode defaults to whole-verse
                    lookAheadEnabled = true,
                    allLyricSections = listOf(current, next),
                    displaySectionIndex = 0,
                )
            }
        }
        onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists("the section being sung must be on screen")
        onNodeWithText("That saved a wretch like me", substring = true)
            .assertExists("the band's look-ahead preview of the next section must render")
    }

    @Test
    fun `line mode shows only the selected line and hides the rest`() = runComposeUiTest {
        val lineMode = AppSettings(songSettings = SongSettings(fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_LINE))
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = section("first line", "second line", "third line"),
                    appSettings = lineMode,
                    displayLineIndex = 1,
                )
            }
        }
        onNodeWithText("second line", substring = true).assertExists("the selected line must be the one shown")
        onNodeWithText("first line", substring = true).assertDoesNotExist()
        onNodeWithText("third line", substring = true).assertDoesNotExist()
    }
}
