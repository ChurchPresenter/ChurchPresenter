package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PresenterRecompositionTest {

    private val section = LyricSection(
        header = "[Verse 1]", title = "Amazing Grace", songNumber = 42, type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("Amazing grace how sweet the sound"),
        translations = listOf(SectionTranslation(title = "Sublime Gracia", lines = listOf("Sublime gracia"))),
    )

    private fun verse(text: String, abbreviation: String) = SelectedVerse(
        translationFileName = "${abbreviation.lowercase()}.spb", bibleAbbreviation = abbreviation,
        bibleName = abbreviation, bookName = "John", chapter = 3, verseNumber = 16, verseText = text,
    )

    private val verses = listOf(verse("For God so loved the world", "KJV"), verse("Ибо так возлюбил", "RST"))

    private val bible = AppSettings(
        bibleSettings = BibleSettings().withTranslations(
            listOf(BibleTranslationSettings(fileName = "kjv.spb"), BibleTranslationSettings(fileName = "rst.spb")),
        ),
    )

    private fun ComposeUiTest.fadesAndKeeps(text: String, content: @Composable (alpha: Float) -> Unit) {
        var alpha by mutableFloatStateOf(1f)
        setContent { Box(Modifier.size(960.dp, 540.dp)) { content(alpha) } }
        waitForIdle()
        alpha = 0.5f
        waitForIdle()
        assertTrue(onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty(), "\"$text\" survives")
    }

    @Test
    fun `a song fading keeps its full-screen slide`() = runComposeUiTest {
        fadesAndKeeps("Amazing grace") { alpha ->
            SongPresenter(
                lyricSection = section, appSettings = AppSettings(), transitionAlpha = alpha,
                languageOverride = Constants.SONG_LANG_BOTH,
            )
        }
    }

    @Test
    fun `a song fading on a key output keeps its lower third`() = runComposeUiTest {
        fadesAndKeeps("Amazing grace") { alpha ->
            SongPresenter(
                lyricSection = section, appSettings = AppSettings(), isLowerThird = true,
                outputRole = Constants.OUTPUT_ROLE_KEY, transitionAlpha = alpha, lookAheadEnabled = true,
                allLyricSections = listOf(section, section.copy(header = "[Verse 2]")), displaySectionIndex = 0,
            )
        }
    }

    @Test
    fun `a Bible passage fading keeps both translations on screen`() = runComposeUiTest {
        fadesAndKeeps("For God so loved") { alpha ->
            BiblePresenter(selectedVerses = verses, appSettings = bible, transitionAlpha = alpha)
        }
    }

    @Test
    fun `a Bible lower third fading on a key output keeps its verse`() = runComposeUiTest {
        fadesAndKeeps("For God so loved") { alpha ->
            BiblePresenter(
                selectedVerses = verses, appSettings = bible, isLowerThird = true,
                outputRole = Constants.OUTPUT_ROLE_KEY, transitionAlpha = alpha,
            )
        }
    }
}
