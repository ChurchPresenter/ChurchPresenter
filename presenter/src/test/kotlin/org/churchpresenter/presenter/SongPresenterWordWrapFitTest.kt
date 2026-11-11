package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Auto-fit with word wrap on (#999), measured on screen: a line too long for one row at the
 * configured size takes more rows at a large size, instead of being shrunk until it fits on one.
 * Compared by drawn bounds rather than any exact size, which differs across the platforms' fonts.
 */
@OptIn(ExperimentalTestApi::class)
class SongPresenterWordWrapFitTest {

    private val english = "Amazing grace! How sweet the sound"
    private val russian = "О, благодать! Спасён я"

    private fun lineBounds(wrap: Boolean, languagesSeparately: Boolean = false): Rect {
        var bounds = Rect.Zero
        val section = LyricSection(
            header = "[Verse 1]",
            type = Constants.SECTION_TYPE_VERSE,
            lines = listOf(english),
            translations = listOf(SectionTranslation(lines = listOf(russian))),
        )
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1920.dp, 1080.dp)) {
                        SongPresenter(
                            lyricSection = section,
                            appSettings = AppSettings(
                                songSettings = SongSettings(
                                    fullscreenLanguageDisplay = Constants.SONG_LANG_BOTH,
                                    lyricsFontSize = 200,
                                    lyricsFontSizeAutoFit = true,
                                    wordWrap = wrap,
                                    layoutExtras = SongLayoutExtras(fitLanguagesSeparately = languagesSeparately),
                                ),
                            ),
                        )
                    }
                }
            }
            bounds = onNodeWithText(english).fetchSemanticsNode().boundsInRoot
        }
        return bounds
    }

    private fun assertWrapsLarger(languagesSeparately: Boolean) {
        val oneRow = lineBounds(wrap = false, languagesSeparately = languagesSeparately)
        val wrapped = lineBounds(wrap = true, languagesSeparately = languagesSeparately)
        assertTrue(
            wrapped.height > oneRow.height * 3,
            "word wrap should let the line take more rows at a larger size: one row $oneRow, wrapped $wrapped",
        )
        assertTrue(wrapped.bottom <= 1080f && wrapped.top >= 0f, "the wrapped line stays on screen: $wrapped")
    }

    @Test
    fun `with word wrap on a long line wraps at a large size instead of shrinking to one row`() =
        assertWrapsLarger(languagesSeparately = false)

    @Test
    fun `each language fitted on its own wraps too`() = assertWrapsLarger(languagesSeparately = true)
}
