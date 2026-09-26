package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Auto fitting each slide on its own rather than the whole song (#658), measured on screen.
 *
 * A song with one short verse and one long one: fitted as a whole, the long verse sets the size for
 * both, so the short one is drawn small; fitted slide by slide, the short verse gets the size it has
 * room for. Compared by the drawn height of the same line rather than by any exact size, which
 * differs across the platforms' font metrics.
 */
@OptIn(ExperimentalTestApi::class)
class SongPresenterEachSlideFitTest {

    private val short = LyricSection(
        header = "[Verse 1]",
        type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("Short"),
    )
    private val long = LyricSection(
        header = "[Verse 2]",
        type = Constants.SECTION_TYPE_VERSE,
        slideIndex = 0,
        lines = List(12) { "A much longer line of lyrics number $it" },
    )

    private fun shortLineHeight(eachSlide: Boolean): Float {
        var height = 0f
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1920.dp, 1080.dp)) {
                        SongPresenter(
                            lyricSection = short,
                            appSettings = AppSettings(
                                songSettings = SongSettings(
                                    lyricsFontSize = 200,
                                    lyricsFontSizeAutoFit = true,
                                    layoutExtras = SongLayoutExtras(autoFitEachSlide = eachSlide),
                                ),
                            ),
                            allLyricSections = listOf(short, long),
                            displaySectionIndex = 0,
                        )
                    }
                }
            }
            height = onNodeWithText("Short").fetchSemanticsNode().boundsInRoot.height
        }
        return height
    }

    @Test
    fun `each slide draws a short verse larger than the whole-song fit does`() {
        val wholeSong = shortLineHeight(eachSlide = false)
        val eachSlide = shortLineHeight(eachSlide = true)
        assertTrue(
            eachSlide > wholeSong * 1.5f,
            "the short verse should grow once the long one no longer sets its size: " +
                "whole song $wholeSong, each slide $eachSlide",
        )
    }
}
