package org.churchpresenter.dictionary.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.settings.DICTIONARY_DEFINITION_BOX
import org.churchpresenter.settings.DICTIONARY_KJV_BOX
import org.churchpresenter.settings.DICTIONARY_REFERENCE_BOX
import org.churchpresenter.settings.DICTIONARY_WORD_BOX
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.textBoxKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dictionary output's text boxes: each boxed part is drawn in its own box and leaves the
 * bottom-aligned card, a part that is hidden or empty is drawn in neither, and a card with nothing
 * left in it is not drawn at all.
 *
 * Asserted as containment between drawn bounds rather than pixel values, which differ across the
 * platforms' font metrics.
 */
@OptIn(ExperimentalTestApi::class)
class DictionaryPresenterBoxTest {

    private val entry =
        StrongsEntry("G26", "ἀγάπη", "agape", "ag-ah'-pay", "love, affection", kjvUsage = "charity (27x)")

    private fun box(y: Float) =
        TextBox(enabled = true, xPercent = 0f, yPercent = y, widthPercent = 50f, heightPercent = 20f)

    private fun band(y: Float) = Rect(0f, HEIGHT * y / 100f, WIDTH * 0.5f, HEIGHT * (y + 20f) / 100f)

    private fun boxes(vararg parts: Pair<String, TextBox>) =
        parts.associate { (part, box) -> textBoxKey(part, lowerThird = false) to box }

    private fun render(content: @Composable () -> Unit, check: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent { MaterialTheme { Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) { content() } } }
            check()
        }

    private fun ComposeUiTest.boundsOf(text: String): Rect =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().first().boundsInRoot

    private fun ComposeUiTest.count(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun Rect.inside(outer: Rect) =
        left >= outer.left - 1 && top >= outer.top - 1 && right <= outer.right + 1 && bottom <= outer.bottom + 1

    @Test
    fun `a boxed dictionary word is drawn in its box, out of the bottom-aligned card`() {
        val top = TextBox(enabled = true, xPercent = 0f, yPercent = 0f, widthPercent = 50f, heightPercent = 25f)
        val ds = DictionarySettings(textBoxes = boxes(DICTIONARY_WORD_BOX to top))
        render({ DictionaryPresenter(entry = entry, dictionarySettings = ds) }) {
            val word = boundsOf("ἀγάπη")
            assertTrue(word.inside(Rect(0f, 0f, WIDTH * 0.5f, HEIGHT * 0.25f)), "word $word")
            assertTrue(boundsOf("love, affection").top > HEIGHT * 0.25f, "the rest stays in the card")
        }
    }

    @Test
    fun `every boxed dictionary part is drawn in its own box`() {
        val ds = DictionarySettings(
            textBoxes = boxes(
                DICTIONARY_REFERENCE_BOX to box(0f),
                DICTIONARY_DEFINITION_BOX to box(40f),
                DICTIONARY_KJV_BOX to box(70f),
            ),
        )
        render({ DictionaryPresenter(entry = entry, dictionarySettings = ds) }) {
            assertTrue(boundsOf("G26").inside(band(0f)), "number")
            assertTrue(boundsOf("agape").inside(band(0f)), "the transliteration goes with the number")
            assertTrue(boundsOf("love, affection").inside(band(40f)), "definition")
            assertTrue(boundsOf("charity (27x)").inside(band(70f)), "usage")
        }
    }

    @Test
    fun `boxing every part leaves no card, and each part is drawn once`() {
        val ds = DictionarySettings(
            textBoxes = boxes(
                DICTIONARY_REFERENCE_BOX to box(0f),
                DICTIONARY_WORD_BOX to box(20f),
                DICTIONARY_DEFINITION_BOX to box(40f),
                DICTIONARY_KJV_BOX to box(60f),
            ),
        )
        render({ DictionaryPresenter(entry = entry, dictionarySettings = ds) }) {
            assertTrue(boundsOf("ἀγάπη").inside(band(20f)), "word")
            listOf("G26", "ἀγάπη", "love, affection", "charity (27x)").forEach {
                assertEquals(1, count(it), "$it is drawn only in its box")
            }
        }
    }

    @Test
    fun `a boxed reference with no transliteration draws the number alone`() {
        val bare = entry.copy(transliteration = "", pronunciation = "")
        val ds = DictionarySettings(textBoxes = boxes(DICTIONARY_REFERENCE_BOX to box(0f)))
        render({ DictionaryPresenter(entry = bare, dictionarySettings = ds) }) {
            assertTrue(boundsOf("G26").inside(band(0f)))
            assertEquals(0, count("•"), "no separator without a transliteration")
        }
    }

    @Test
    fun `a hidden part is drawn neither in its box nor in the card`() {
        val ds = DictionarySettings(
            showWord = false,
            showKjvUsage = false,
            textBoxes = boxes(DICTIONARY_WORD_BOX to box(0f), DICTIONARY_KJV_BOX to box(40f)),
        )
        render({ DictionaryPresenter(entry = entry, dictionarySettings = ds) }) {
            onNodeWithText("ἀγάπη").assertDoesNotExist()
            onNodeWithText("charity (27x)").assertDoesNotExist()
            onNodeWithText("love, affection", substring = true).assertExists()
        }
    }

    @Test
    fun `an empty definition is not given its box`() {
        val ds = DictionarySettings(textBoxes = boxes(DICTIONARY_DEFINITION_BOX to box(40f)))
        render({ DictionaryPresenter(entry = entry.copy(definition = ""), dictionarySettings = ds) }) {
            onNodeWithText("love, affection", substring = true).assertDoesNotExist()
            onNodeWithText("ἀγάπη").assertExists()
        }
    }

    private companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
    }
}
