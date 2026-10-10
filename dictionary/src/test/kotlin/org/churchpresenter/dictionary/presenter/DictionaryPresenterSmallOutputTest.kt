package org.churchpresenter.dictionary.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.settings.DictionarySettings
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The entry on an output far smaller than full HD -- a dev window, a preview tile: the full-HD card
 * scaled down, so the word's size cannot push the definition off the card.
 */
@OptIn(ExperimentalTestApi::class)
class DictionaryPresenterSmallOutputTest {

    private val definition = "something said (including the thought); a word, a saying, a discourse"

    @Test
    fun `on a small window the definition is still drawn, inside the window`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(320.dp, 180.dp)) {
                DictionaryPresenter(
                    entry = StrongsEntry("G3056", "λόγος", "logos", "log'-os", definition),
                    dictionarySettings = DictionarySettings(),
                )
            }
        }
        val bounds = onNodeWithText(definition, substring = true).getBoundsInRoot()
        assertTrue(bounds.bottom > bounds.top, "the definition was squeezed out: $bounds")
        assertTrue(bounds.left >= 0.dp && bounds.right <= 320.dp && bounds.bottom <= 180.dp, "inside: $bounds")
    }
}
