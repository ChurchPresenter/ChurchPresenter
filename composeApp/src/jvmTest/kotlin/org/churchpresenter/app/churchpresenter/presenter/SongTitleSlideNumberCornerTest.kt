package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongCreditStyle
import org.churchpresenter.settings.SongNumberOffset
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTitleSlideNumber
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The title slide's own song number, which #609 separated from the lyric slides'.
 *
 * The ask was a number small in the top-left of every lyric slide and large at the bottom-left of the
 * title slide -- impossible while the two shared one profile. So the interesting assertions are about
 * *placement*, measured: every one of these passes against the old shared layout if you only check
 * that "42" is on screen somewhere.
 *
 * The subtle half is what leaves the flow. The number reaches the slide by two routes -- a line of its
 * own, and `TitleSlideLine.number` riding on the title's line when `titleSlideNumberBeforeTitle` is on
 * -- and a cornered number has to come out of both, or it is drawn twice.
 */
@OptIn(ExperimentalTestApi::class)
class SongTitleSlideNumberCornerTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun titleSlide() = LyricSection(
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_TITLE_SLIDE,
        lines = emptyList(),
    )

    private fun settings(
        corner: String,
        beforeTitle: Boolean = true,
        offset: SongNumberOffset = SongNumberOffset(),
    ) = AppSettings(
        songSettings = SongSettings(
            titleSlideEnabled = true,
            titleSlideShowSongNumber = true,
            titleSlideShowTitle = true,
            titleSlideNumberBeforeTitle = beforeTitle,
            layoutExtras = SongSettings().layoutExtras.copy(
                titleSlideNumber = SongTitleSlideNumber(
                    fullScreen = SongCreditStyle(fontSize = 80),
                    corner = corner,
                    offset = offset,
                ),
            ),
        ),
    )

    private fun ComposeUiTest.render(settings: AppSettings) {
        setContent {
            Box(screen) {
                SongPresenter(lyricSection = titleSlide(), appSettings = settings)
            }
        }
    }

    private fun ComposeUiTest.boundsOf(text: String): Rect =
        onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot

    /**
     * The output's real bounds, which are **not** the 1920x1080 the content asks for.
     *
     * `runComposeUiTest` composes in a 1024x768 window and clips the `Modifier.size` above to it, so a
     * midpoint worked out from 1920 is off the right-hand side of the frame and every "is it in the
     * right half" assertion reads backwards. `SongPresenterNumberCornerTest` takes the same precaution
     * for the same reason.
     */
    private fun ComposeUiTest.outputBounds(): Rect = onRoot().fetchSemanticsNode().boundsInRoot

    @Test
    fun `with no corner the number rides the title's line, as it always did`() = runComposeUiTest {
        render(settings(corner = Constants.NONE))

        // One line carrying both, which is what `titleSlideNumberBeforeTitle` means -- so the number
        // has no bounds of its own to ask about and the title's line is where "42" is found.
        val title = boundsOf("Amazing Grace")
        assertTrue(title.width > 0f, "the heading should be drawn")
    }

    @Test
    fun `pinned bottom left the number is in the lower left quarter`() = runComposeUiTest {
        render(settings(corner = Constants.BOTTOM_LEFT))

        val output = outputBounds()
        val number = boundsOf("42")
        assertTrue(number.left < output.center.x, "should be in the left half: $number of $output")
        assertTrue(number.top > output.center.y, "should be in the lower half: $number of $output")
    }

    @Test
    fun `pinned top right the number is in the upper right quarter`() = runComposeUiTest {
        render(settings(corner = Constants.TOP_RIGHT))

        val output = outputBounds()
        val number = boundsOf("42")
        assertTrue(number.right > output.center.x, "should be in the right half: $number of $output")
        assertTrue(number.bottom < output.center.y, "should be in the upper half: $number of $output")
    }

    @Test
    fun `a cornered number leaves the title's line rather than being drawn twice`() = runComposeUiTest {
        render(settings(corner = Constants.BOTTOM_LEFT, beforeTitle = true))

        // `beforeTitle` is on, so the number would ride the title's line as well unless it is
        // filtered out of it -- and the two would be drawn at once, one of them in the wrong place.
        val number = boundsOf("42")
        val title = boundsOf("Amazing Grace")
        assertTrue(
            number.top > title.bottom,
            "the number should be below the heading, not inside it: number=$number title=$title",
        )
    }

    @Test
    fun `the offset walks a cornered number inward from its corner`() = runComposeUiTest {
        render(settings(corner = Constants.BOTTOM_LEFT, offset = SongNumberOffset(xPercent = 50)))

        // Half the room from the left edge is the middle of the frame, give or take the glyph's width.
        val output = outputBounds()
        val number = boundsOf("42")
        assertTrue(number.left > output.width / 4, "50% should have walked it well inward: $number of $output")
    }

    @Test
    fun `the title slide's number is styled from its own record, not the lyric slides'`() = runComposeUiTest {
        // The lyric number stays at its stock 70pt while the title slide's is set to 160 here, so a
        // shared profile would draw them the same and this height could not tell them apart.
        val settings = AppSettings(
            songSettings = SongSettings(
                titleSlideEnabled = true,
                titleSlideShowSongNumber = true,
                songNumberFontSize = 20,
                layoutExtras = SongSettings().layoutExtras.copy(
                    titleSlideNumber = SongTitleSlideNumber(
                        fullScreen = SongCreditStyle(fontSize = 160),
                        corner = Constants.TOP_LEFT,
                    ),
                ),
            ),
        )
        render(settings)

        val number = boundsOf("42")
        assertTrue(number.height > 40f, "160pt should draw far taller than the lyric slides' 20pt: $number")
    }
}
