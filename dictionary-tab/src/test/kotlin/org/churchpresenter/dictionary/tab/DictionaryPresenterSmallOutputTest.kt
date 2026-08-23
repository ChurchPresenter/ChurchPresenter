package org.churchpresenter.dictionary.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.dictionary.StrongsEntry
import org.churchpresenter.settings.DictionarySettings
import kotlin.test.Test

/**
 * The presenter at the sizes that are not a projector: the live preview panel, the stage monitor's
 * quadrant, a lower-third strip. Below 500dp tall it switches to a tighter set of paddings and
 * spacings, and that whole branch had never been rendered — every other suite here shoots 1920x1080.
 */
@OptIn(ExperimentalTestApi::class)
class DictionaryPresenterSmallOutputTest {

    private val agape = StrongsEntry(
        number = "G26",
        word = "ἀγάπη",
        transliteration = "agape",
        pronunciation = "ag-ah'-pay",
        definition = "brotherly love, affection, benevolence",
        kjvUsage = "love, charity",
    )

    private fun shownAt(width: Int, height: Int, body: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                Box(Modifier.size(width.dp, height.dp)) {
                    DictionaryPresenter(entry = agape, dictionarySettings = DictionarySettings())
                }
            }
            body()
        }

    @Test
    fun `a preview-sized output still shows the word and its definition`() = shownAt(480, 270) {
        onNodeWithText(agape.word).assertIsDisplayed()
        onNodeWithText(agape.definition, substring = true).assertIsDisplayed()
    }

    @Test
    fun `a stage-monitor quadrant shows the same entry`() = shownAt(640, 360) {
        onNodeWithText(agape.word).assertIsDisplayed()
    }

    @Test
    fun `one pixel either side of the small-output threshold both render`() {
        shownAt(900, 499) { onNodeWithText(agape.word).assertIsDisplayed() }
        shownAt(900, 501) { onNodeWithText(agape.word).assertIsDisplayed() }
    }
}
