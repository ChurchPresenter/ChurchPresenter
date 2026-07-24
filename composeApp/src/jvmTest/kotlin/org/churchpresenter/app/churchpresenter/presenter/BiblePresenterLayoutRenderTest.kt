package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import kotlin.test.Test

/**
 * Two verse layouts the full-screen single-verse tests in [PresenterRenderTest] don't reach:
 * lower-third mode and a multi-verse selection.
 *
 * Lower-third mode is an entirely separate styling path (every font/colour/size picker switches to
 * its `*LowerThird*` field); if it stopped composing the verse the band would go blank on air while
 * the operator's full-screen preview looked fine. Lower-third mode WITH a second Bible exercises the
 * secondary-translation branch of that same path — neither the full-screen bilingual test nor the
 * mono lower-third test reaches it — and both translations must appear in the band.
 *
 * Both assert on the verse text and reference that land on screen, so the layout path runs and
 * nothing races a crossfade.
 */
@OptIn(ExperimentalTestApi::class)
class BiblePresenterLayoutRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun verse(
        text: String,
        number: Int,
        book: String = "John",
        chapter: Int = 3,
        abbreviation: String = "KJV",
    ) = SelectedVerse(
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = book,
        chapter = chapter,
        verseNumber = number,
        verseText = text,
    )

    @Test
    fun `lower-third mode still composes the verse and its reference`() = runComposeUiTest {
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse("For God so loved the world", 16)),
                    appSettings = AppSettings(),
                    isLowerThird = true,
                )
            }
        }
        onNodeWithText("For God so loved the world", substring = true).assertExists("the band must show the verse on air")
        onNodeWithText("John 3:16", substring = true).assertExists("the lower third still needs its reference")
    }

    @Test
    fun `lower-third mode carries both translations when a second bible is configured`() = runComposeUiTest {
        val bilingual = AppSettings(bibleSettings = BibleSettings(secondaryBible = "RST"))
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16),
                        verse("Ибо так возлюбил Бог мир", 16, book = "Иоанна", abbreviation = "RST"),
                    ),
                    appSettings = bilingual,
                    isLowerThird = true,
                )
            }
        }
        onNodeWithText("For God so loved the world", substring = true).assertExists("the band must carry the primary translation")
        onNodeWithText("Ибо так возлюбил Бог мир", substring = true)
            .assertExists("the lower-third secondary-translation style path must render the second language too")
    }
}
