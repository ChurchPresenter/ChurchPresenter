package org.churchpresenter.sharedui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.OutputScaleMode
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OutputScaleModeTest {

    private fun labelOf(mode: OutputScaleMode): String {
        var label = ""
        runComposeUiTest {
            setContent { label = stringResource(mode.label) }
            waitForIdle()
        }
        return label
    }

    private fun tooltip(shared: OutputScaleMode?, current: OutputScaleMode, content: ScaleButtonContent): String {
        var text = ""
        runComposeUiTest {
            setContent { text = scaleButtonLabel(shared, current, content) }
            waitForIdle()
        }
        return text
    }

    @Test
    fun `fit letterboxes, is named Fit and shows the fit icon`() {
        assertEquals(ContentScale.Fit, OutputScaleMode.FIT.contentScale)
        assertEquals(Icons.Filled.FitScreen, OutputScaleMode.FIT.icon)
        assertEquals("Fit", labelOf(OutputScaleMode.FIT))
    }

    @Test
    fun `fill crops, is named Fill and shows the crop icon`() {
        assertEquals(ContentScale.Crop, OutputScaleMode.FILL.contentScale)
        assertEquals(Icons.Filled.Crop, OutputScaleMode.FILL.icon)
        assertEquals("Fill", labelOf(OutputScaleMode.FILL))
    }

    @Test
    fun `stretch fills the bounds, is named Stretch and shows the aspect icon`() {
        assertEquals(ContentScale.FillBounds, OutputScaleMode.STRETCH.contentScale)
        assertEquals(Icons.Filled.AspectRatio, OutputScaleMode.STRETCH.icon)
        assertEquals("Stretch", labelOf(OutputScaleMode.STRETCH))
    }

    @Test
    fun `the pictures button names the shared mode and every output`() {
        assertEquals(
            "Picture scale on every output: Fill",
            tooltip(OutputScaleMode.FILL, OutputScaleMode.FIT, ScaleButtonContent.PICTURES),
        )
    }

    @Test
    fun `the media button names video rather than pictures`() {
        assertEquals(
            "Video scale on every output: Stretch",
            tooltip(OutputScaleMode.STRETCH, OutputScaleMode.FIT, ScaleButtonContent.MEDIA),
        )
    }

    @Test
    fun `profiles that disagree make the pictures button offer the current mode for all`() {
        assertEquals(
            "Picture scale differs by profile — click to set every output to Fit",
            tooltip(null, OutputScaleMode.FIT, ScaleButtonContent.PICTURES),
        )
    }

    @Test
    fun `profiles that disagree make the media button say the same about video`() {
        assertEquals(
            "Video scale differs by profile — click to set every output to Fill",
            tooltip(null, OutputScaleMode.FILL, ScaleButtonContent.MEDIA),
        )
    }
}
