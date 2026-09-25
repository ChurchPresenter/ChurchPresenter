@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.utils.withPictureScaleEverywhere
import org.churchpresenter.settings.OutputScaleMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Pictures tab's scale button: it names the mode every output is in, and one click moves them
 * all to the next.
 *
 * The harness applies a write to the settings the tab was composed with and does not recompose, so
 * each test starts in one mode and makes one click. The order itself is pinned in `:settings` by
 * `OutputScaleModeTest`.
 */
class PicturesTabScaleTest {

    private fun clickingFrom(mode: OutputScaleMode, label: String, next: OutputScaleMode) = picturesTab(
        settings = { it.withPictureScaleEverywhere(mode) },
    ) { _, reports ->
        pictureButton("Picture scale on every output: $label").performClick()
        waitForIdle()

        // Scaling is per profile; the button is the shortcut that moves every one of them.
        val after = reports.settingsAfterChange
        assertEquals(next, after?.pictureSettings?.scaleMode)
        assertEquals(listOf(next), after?.projectionSettings?.outputProfiles?.map { it.pictureScaleMode }?.distinct())
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
        pictureButton("Picture scale on every output: Fit").assertExists()
    }
}
