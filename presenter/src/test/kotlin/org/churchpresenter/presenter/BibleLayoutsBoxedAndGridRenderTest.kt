package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BibleLayoutsBoxedAndGridRenderTest {

    private val files = listOf("kjv.spb", "rst.spb", "lsg.spb", "lut.spb")
    private val texts = listOf(
        "For God so loved the world",
        "Ибо так возлюбил Бог мир",
        "Car Dieu a tant aimé le monde",
        "Also hat Gott die Welt geliebt",
    )
    private val box = TextBox(enabled = true, xPercent = 5f, yPercent = 5f, widthPercent = 40f, heightPercent = 30f)

    private fun verse(i: Int, text: String = texts[i]) = SelectedVerse(
        translationFileName = files[i],
        bibleAbbreviation = files[i].substringBefore('.').uppercase(),
        bibleName = files[i],
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
    )

    private fun ComposeUiTest.shows(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun settings(
        count: Int,
        boxed: List<Pair<Int, BibleStyleElement>> = emptyList(),
        lowerThird: Boolean = false,
        divider: Boolean = false,
        bandLayout: String = Constants.BILINGUAL_SIDE_BY_SIDE,
        fullLayout: String = Constants.BILINGUAL_TOP_BOTTOM,
        style: (Int, BibleTranslationSettings) -> BibleTranslationSettings = { _, t -> t },
    ): AppSettings {
        val base = BibleSettings(
            multiTranslationDivider = divider,
            bilingualLayoutLowerThird = bandLayout,
            bilingualLayout = fullLayout,
        ).withTranslations(files.take(count).mapIndexed { i, f -> style(i, BibleTranslationSettings(fileName = f)) })
        val boxes = boxed.associate { (i, element) -> base.bibleBoxKey(element, lowerThird, files[i]) to box }
        return AppSettings(bibleSettings = base.copy(textBoxes = boxes))
    }

    private fun present(
        settings: AppSettings,
        count: Int,
        isLowerThird: Boolean = false,
        verses: List<SelectedVerse> = List(count) { verse(it) },
        check: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                BiblePresenter(selectedVerses = verses, appSettings = settings, isLowerThird = isLowerThird)
            }
        }
        waitForIdle()
        check()
    }

    @Test
    fun `a split band with the primary verse boxed`() =
        present(settings(2, listOf(0 to BibleStyleElement.TEXT), lowerThird = true), 2, isLowerThird = true) {
            assertTrue(shows(texts[1]))
        }

    @Test
    fun `a split band with the primary reference boxed`() =
        present(settings(2, listOf(0 to BibleStyleElement.REFERENCE), lowerThird = true), 2, isLowerThird = true) {
            assertTrue(shows(texts[0]))
        }

    @Test
    fun `a split band with the secondary verse and reference boxed`() =
        present(
            settings(2, listOf(1 to BibleStyleElement.TEXT, 1 to BibleStyleElement.REFERENCE), lowerThird = true),
            2,
            isLowerThird = true,
        ) {
            assertTrue(shows(texts[0]))
        }

    @Test
    fun `a split band with everything boxed`() =
        present(
            settings(
                2,
                listOf(
                    0 to BibleStyleElement.TEXT, 0 to BibleStyleElement.REFERENCE,
                    1 to BibleStyleElement.TEXT, 1 to BibleStyleElement.REFERENCE,
                ),
                lowerThird = true,
            ),
            2,
            isLowerThird = true,
        ) {
            assertTrue(shows(texts[0]))
        }

    @Test
    fun `a split band whose second language is set smaller shares the smaller size`() =
        present(
            settings(2, lowerThird = true) { i, t -> if (i == 1) t.copy(lowerThirdTextFontSize = 12) else t },
            2,
            isLowerThird = true,
        ) {
            assertTrue(shows(texts[0]))
            assertTrue(shows(texts[1]))
        }

    @Test
    fun `a split band with a long primary verse shrinks to fit`() =
        present(
            settings(2, lowerThird = true),
            2,
            isLowerThird = true,
            verses = listOf(verse(0, "For God so loved the world ".repeat(30)), verse(1)),
        ) {
            assertTrue(shows(texts[1]))
        }

    @Test
    fun `a stacked band with the secondary boxed`() =
        present(
            settings(
                2,
                listOf(1 to BibleStyleElement.TEXT, 1 to BibleStyleElement.REFERENCE),
                lowerThird = true,
                bandLayout = Constants.BILINGUAL_TOP_BOTTOM,
            ),
            2,
            isLowerThird = true,
        ) {
            assertTrue(shows(texts[0]))
        }

    @Test
    fun `a stacked band with the primary boxed`() =
        present(
            settings(
                2,
                listOf(0 to BibleStyleElement.TEXT, 0 to BibleStyleElement.REFERENCE),
                lowerThird = true,
                bandLayout = Constants.BILINGUAL_TOP_BOTTOM,
            ),
            2,
            isLowerThird = true,
        ) {
            assertTrue(shows(texts[1]))
        }

    @Test
    fun `a stacked band whose second language is smaller`() =
        present(
            settings(2, lowerThird = true, bandLayout = Constants.BILINGUAL_TOP_BOTTOM) { i, t ->
                if (i == 1) t.copy(lowerThirdTextFontSize = 12) else t
            },
            2,
            isLowerThird = true,
            verses = listOf(verse(0, "For God so loved the world ".repeat(20)), verse(1)),
        ) {
            assertTrue(shows(texts[1]))
        }

    @Test
    fun `a band of three translations with dividers`() =
        present(settings(3, lowerThird = true, divider = true), 3, isLowerThird = true) {
            assertTrue(shows(texts[2]))
        }

    @Test
    fun `a band of four translations in a 2x2 grid with dividers`() =
        present(
            settings(4, lowerThird = true, divider = true, bandLayout = Constants.BILINGUAL_GRID_2X2),
            4,
            isLowerThird = true,
        ) {
            assertTrue(shows(texts[3]))
        }

    @Test
    fun `a full screen of three columns with dividers`() =
        present(settings(3, divider = true, fullLayout = Constants.BILINGUAL_SIDE_BY_SIDE), 3) {
            assertTrue(shows(texts[2]))
        }

    @Test
    fun `a full screen 2x2 grid with an empty cell and dividers`() =
        present(settings(3, divider = true, fullLayout = Constants.BILINGUAL_GRID_2X2), 3) {
            assertTrue(shows(texts[0]))
        }

    @Test
    fun `a full screen stack with a boxed verse and dividers`() =
        present(settings(2, listOf(0 to BibleStyleElement.TEXT), divider = true), 2) {
            assertTrue(shows(texts[1]))
        }

    @Test
    fun `a full screen stack with boxed references`() =
        present(
            settings(2, listOf(0 to BibleStyleElement.REFERENCE, 1 to BibleStyleElement.REFERENCE)),
            2,
        ) {
            assertTrue(shows(texts[0]))
        }
}
