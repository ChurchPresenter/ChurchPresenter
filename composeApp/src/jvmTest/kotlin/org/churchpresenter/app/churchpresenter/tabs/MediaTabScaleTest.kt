@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.OutputScaleMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Media tab's scale button: it names the mode the output is in, and one click saves the next.
 *
 * Like loop, it waits for something to be loaded. The harness applies a write to the settings the
 * tab was composed with and does not recompose, so each test starts in one mode and makes one click;
 * the order itself is pinned in `:settings` by `OutputScaleModeTest`.
 */
class MediaTabScaleTest {

    private fun ComposeUiTest.loadUrl() {
        onNodeWithText(MediaLabel.NETWORK_URL).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0].performTextReplacement("https://example.org/clip.mp4")
        waitForIdle()
        onNodeWithText("Load").performClick()
        waitForIdle()
    }

    private fun clickingFrom(mode: OutputScaleMode, label: String, next: OutputScaleMode) = mediaTab(
        settings = { it.copy(mediaScaleMode = mode) },
    ) { _, reports ->
        loadUrl()

        mediaButton("Scale: $label").performClick()
        waitForIdle()

        assertEquals(next, reports.settingsAfterChange?.mediaScaleMode)
    }

    @Test
    fun `nothing loaded, the button is disabled and saves nothing`() = mediaTab { _, reports ->
        mediaButton("Scale: Fit").assertIsNotEnabled().performClick()
        waitForIdle()

        assertNull(reports.settingsAfterChange)
    }

    @Test
    fun `from fit one click saves fill`() = clickingFrom(OutputScaleMode.FIT, "Fit", OutputScaleMode.FILL)

    @Test
    fun `from fill one click saves stretch`() = clickingFrom(OutputScaleMode.FILL, "Fill", OutputScaleMode.STRETCH)

    @Test
    fun `from stretch one click goes back to fit`() =
        clickingFrom(OutputScaleMode.STRETCH, "Stretch", OutputScaleMode.FIT)
}
