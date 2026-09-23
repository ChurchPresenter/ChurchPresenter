@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.OutputScaleMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Pictures tab's scale button: it names the mode the output is in, and one click saves the next.
 *
 * The harness applies a write to the settings the tab was composed with and does not recompose, so
 * each test starts in one mode and makes one click. The order itself is pinned in `:settings` by
 * `OutputScaleModeTest`.
 */
class PicturesTabScaleTest {

    private fun clickingFrom(mode: OutputScaleMode, label: String, next: OutputScaleMode) = picturesTab(
        settings = { it.copy(pictureSettings = it.pictureSettings.copy(scaleMode = mode)) },
    ) { _, reports ->
        pictureButton("Scale: $label").performClick()
        waitForIdle()

        assertEquals(next, reports.settingsAfterChange?.pictureSettings?.scaleMode)
    }

    @Test
    fun `from fit one click saves fill`() = clickingFrom(OutputScaleMode.FIT, "Fit", OutputScaleMode.FILL)

    @Test
    fun `from fill one click saves stretch`() = clickingFrom(OutputScaleMode.FILL, "Fill", OutputScaleMode.STRETCH)

    @Test
    fun `from stretch one click goes back to fit`() =
        clickingFrom(OutputScaleMode.STRETCH, "Stretch", OutputScaleMode.FIT)

    @Test
    fun `without saved settings the button reads fit`() = picturesTab { _, _ ->
        // A tab composed with no settings at all still has to say what the output is doing.
        pictureButton("Scale: Fit").assertExists()
    }
}
