package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.DICTIONARY_WORD_BOX
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.QA_QR_MESSAGE_BOX
import org.churchpresenter.settings.QA_QUESTION_BOX
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Text boxes as the presenters draw them: a boxed item lands inside its box and leaves where the
 * layout put it; the language gap spaces stacked languages; a region that keeps the background
 * full screen still keeps the text in it.
 *
 * Asserted as containment and relations between drawn bounds rather than pixel values, which differ
 * across the platforms' font metrics.
 */
@OptIn(ExperimentalTestApi::class)
class TextBoxRenderTest {

    private val verse = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("First lyric line", "Last lyric line"),
        translations = listOf(SectionTranslation(lines = listOf("Second language line"))),
    )

    /** The bottom-right quarter of the output, in percent. */
    private val corner =
        TextBox(enabled = true, xPercent = 50f, yPercent = 75f, widthPercent = 50f, heightPercent = 25f)
    private val cornerRect = Rect(WIDTH * 0.5f, HEIGHT * 0.75f, WIDTH.toFloat(), HEIGHT.toFloat())

    private fun bounds(vararg texts: String, content: @Composable () -> Unit): List<Rect> {
        var found = emptyList<Rect>()
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent { MaterialTheme { Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) { content() } } }
            found = texts.map { boundsOf(it) }
        }
        return found
    }

    private fun ComposeUiTest.boundsOf(text: String): Rect =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().first().boundsInRoot

    private fun Rect.inside(outer: Rect) =
        left >= outer.left - 1 && top >= outer.top - 1 && right <= outer.right + 1 && bottom <= outer.bottom + 1

    @Composable
    private fun Song(song: SongSettings, languages: List<Int> = listOf(0), textRegion: ContentRegion? = null) {
        SongPresenter(
            lyricSection = verse,
            appSettings = AppSettings(songSettings = song),
            allLyricSections = listOf(verse),
            displaySectionIndex = 0,
            languageSelection = languages,
            textRegion = textRegion,
        )
    }

    @Test
    fun `a boxed title is drawn in its box, below the lyrics it used to head`() {
        val song = SongSettings(
            titleDisplay = Constants.EVERY_PAGE,
            layoutExtras = SongLayoutExtras(textBoxes = mapOf("TITLE" to corner)),
        )
        val (title, lyric) = bounds("Amazing Grace", "First lyric line") { Song(song) }
        assertTrue(title.inside(cornerRect), "title $title in $cornerRect")
        assertTrue(title.top > lyric.bottom, "the title left its row above the lyrics")
    }

    @Test
    fun `a boxed language is drawn in its box while the other stays in the layout`() {
        val song = SongSettings(layoutExtras = SongLayoutExtras(textBoxes = mapOf("LYRICS#1" to corner)))
        val (second, first) = bounds("Second language line", "First lyric line") { Song(song, listOf(0, 1)) }
        assertTrue(second.inside(cornerRect), "second language $second in $cornerRect")
        assertTrue(!first.inside(cornerRect), "the first language stays where the layout puts it")
    }

    @Test
    fun `a wider gap between languages pushes the stacked languages further apart`() {
        fun gap(extras: SongLayoutExtras): Float {
            val song = SongSettings(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM, layoutExtras = extras)
            val (first, second) = bounds("Last lyric line", "Second language line") { Song(song, listOf(0, 1)) }
            return second.top - first.bottom
        }
        val usual = gap(SongLayoutExtras())
        val wide = gap(SongLayoutExtras(languageGap = 300))
        assertTrue(wide > usual, "gap $wide should be wider than $usual")
    }

    @Test
    fun `a region that leaves the background full screen still keeps the text in it`() {
        val left = ContentRegion(widthPercent = 50, xOffsetPercent = -100, movesBackground = false)
        val (lyric) = bounds("First lyric line") { Song(SongSettings(), textRegion = left) }
        assertTrue(lyric.right <= WIDTH / 2f + 1, "lyrics $lyric stay in the left half")
    }

    @Test
    fun `a boxed Bible reference is drawn in its box, clear of the verse's area`() {
        val top = TextBox(enabled = true, xPercent = 0f, yPercent = 0f, widthPercent = 100f, heightPercent = 10f)
        val bible = BibleSettings(
            translations = listOf(BibleTranslationSettings(fileName = "kjv.spb")),
            textBoxes = mapOf(textBoxKey("REFERENCE", lowerThird = false, language = "kjv.spb") to top),
        )
        val chosen = SelectedVerse(
            translationFileName = "kjv.spb",
            bookName = "John",
            chapter = 3,
            verseNumber = 16,
            verseText = "For God so loved the world",
        )
        val (reference, text) = bounds("John 3:16", "For God so loved") {
            BiblePresenter(selectedVerses = listOf(chosen), appSettings = AppSettings(bibleSettings = bible))
        }
        assertTrue(reference.inside(Rect(0f, 0f, WIDTH.toFloat(), HEIGHT * 0.1f)), "reference $reference")
        assertTrue(text.top > reference.bottom, "the verse is still laid out below it")
    }

    @Test
    fun `a boxed question is drawn in its box`() {
        val qa = QASettings(textBoxes = mapOf(textBoxKey(QA_QUESTION_BOX, lowerThird = false) to corner))
        val (question) = bounds("Where do I sign up") {
            QAPresenter(question = Question(id = "q", text = "Where do I sign up?", timestamp = 0L), qaSettings = qa)
        }
        assertTrue(question.inside(cornerRect), "question $question in $cornerRect")
    }

    @Test
    fun `a boxed QR message is drawn in its box, not under the centred code`() {
        val qa = QASettings(
            qrCodeMessage = "Scan to ask a question",
            textBoxes = mapOf(textBoxKey(QA_QR_MESSAGE_BOX, lowerThird = false) to corner),
        )
        val (message) = bounds("Scan to ask") { QAQRCodePresenter(url = "https://example.org/qa", qaSettings = qa) }
        assertTrue(message.inside(cornerRect), "message $message in $cornerRect")
    }

    @Test
    fun `a boxed dictionary word is drawn in its box, out of the bottom-aligned card`() {
        val top = TextBox(enabled = true, xPercent = 0f, yPercent = 0f, widthPercent = 50f, heightPercent = 25f)
        val ds = DictionarySettings(textBoxes = mapOf(textBoxKey(DICTIONARY_WORD_BOX, lowerThird = false) to top))
        val entry = StrongsEntry("G26", "ἀγάπη", "agape", "ag-ah'-pay", "love, affection")
        val (word, definition) = bounds("ἀγάπη", "love, affection") {
            DictionaryPresenter(entry = entry, dictionarySettings = ds)
        }
        assertTrue(word.inside(Rect(0f, 0f, WIDTH * 0.5f, HEIGHT * 0.25f)), "word $word")
        assertTrue(definition.top > HEIGHT * 0.25f, "the rest stays in the card")
    }

    private companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
    }
}
