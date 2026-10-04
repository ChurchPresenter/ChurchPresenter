package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
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
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BoxedTextRenderTest {

    private val on = TextBox(enabled = true, xPercent = 5f, yPercent = 5f, widthPercent = 60f, heightPercent = 40f)

    private fun ComposeUiTest.shows(text: String) {
        waitForIdle()
        val nodes = onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty(), "\"$text\" is on screen")
    }

    @Test
    fun `boxed song lyrics and title draw in their boxes in bold, italic and shadowed faces`() = runComposeUiTest {
        val song = SongSettings(
            lyricsBold = true, lyricsItalic = true, lyricsShadow = true, titleBold = true, titleItalic = true,
            layoutExtras = SongLayoutExtras(textBoxes = mapOf("LYRICS#0" to on, "TITLE" to on.copy(yPercent = 60f))),
        )
        val section = LyricSection(
            header = "[Verse 1]", title = "Amazing Grace", songNumber = 42, type = Constants.SECTION_TYPE_VERSE,
            lines = listOf("Amazing grace how sweet the sound"),
        )
        setContent {
            Box(Modifier.size(960.dp, 540.dp)) {
                SongPresenter(lyricSection = section, appSettings = AppSettings(songSettings = song))
            }
        }
        shows("Amazing grace how sweet the sound")
    }

    @Test
    fun `boxed verse text and reference draw in bold, italic and shadowed faces`() = runComposeUiTest {
        val kjv = BibleTranslationSettings(
            fileName = "kjv.spb",
            textBold = true, textItalic = true, textShadow = true,
            referenceBold = true, referenceItalic = true, referenceShadow = true,
        )
        val bible = BibleSettings(
            textBoxes = mapOf("TEXT#kjv.spb" to on, "REFERENCE#kjv.spb" to on.copy(yPercent = 60f)),
        ).withTranslations(listOf(kjv))
        val verse = SelectedVerse(
            translationFileName = "kjv.spb", bibleAbbreviation = "KJV", bibleName = "KJV",
            bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved the world",
        )
        setContent {
            Box(Modifier.size(960.dp, 540.dp)) {
                BiblePresenter(selectedVerses = listOf(verse), appSettings = AppSettings(bibleSettings = bible))
            }
        }
        shows("For God so loved the world")
    }

    @Test
    fun `a gradient quick background resolves to its two colours`() {
        val settings = BackgroundSettings(
            quickBackground = SongBackground(
                type = SongBackgroundType.GRADIENT, color = "#ff0000", colorEnd = "#0000ff",
            ),
        )
        lateinit var resolved: ResolvedBackground
        runComposeUiTest {
            setContent {
                resolved = resolveBackground(
                    settings = settings,
                    config = BackgroundConfig(),
                    isLowerThird = false,
                    showBackground = true,
                    transparentWhenBlank = false,
                    knownCameras = null,
                )
            }
        }
        assertEquals(Color.Red, resolved.color)
    }
}
