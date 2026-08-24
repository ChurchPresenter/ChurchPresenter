@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary.tab.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.dictionary.StrongsEntry
import org.churchpresenter.dictionary.tab.DictionaryPresenter
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.ui.screenshot.SCREENSHOT_ROOT
import kotlin.test.Test

/**
 * What the congregation sees when a Strong's entry is live, full screen.
 *
 * **One image per state, not two.** The rest of the screenshot suite stacks a light and a dark
 * render of each state, because those surfaces follow the operator's theme. This one does not: the
 * audience screen is drawn from [DictionarySettings] and looks the same whichever theme the
 * operator has chosen. Stacking would write the same picture twice.
 *
 * Rendered at 1920x1080, which is what this surface is drawn onto in practice.
 */
class DictionaryPresenterScreenshotTest {

    /** A 1080p output. */
    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun shoot(name: String, content: @Composable () -> Unit) = runComposeUiTest {
        setContent { MaterialTheme { Box(screen) { content() } } }
        waitForIdle()
        capture(name)
    }

    private fun ComposeUiTest.capture(name: String) {
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$SECTION/$name.png")
    }

    @Test
    fun `a Strong's entry`() = shoot("dictionary_entry") {
        DictionaryPresenter(entry = strongs(), dictionarySettings = DictionarySettings())
    }

    @Test
    fun `a styled Strong's entry`() = shoot("dictionary_entry_styled") {
        DictionaryPresenter(
            entry = strongs(),
            dictionarySettings = DictionarySettings(
                wordColor = "#FFD54F",
                wordFontSize = 140,
                wordBold = true,
                referenceColor = "#90CAF9",
                definitionColor = "#FFFFFF",
            ),
        )
    }

    private fun strongs() = StrongsEntry(
        number = "G26",
        word = "ἀγάπη",
        transliteration = "agape",
        pronunciation = "ag-ah'-pay",
        definition = "brotherly love, affection, benevolence",
        kjvUsage = "love, charity",
    )

    private companion object {
        const val SECTION = "dictionaryPresenter"
    }
}
