package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.SharedVideoOutput
import org.churchpresenter.app.churchpresenter.utils.contentScale
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.settings.OutputScaleMode
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The Media tab's scale button, as the output draws it: a 4:3 video frame on a 16:9 screen, once per
 * [OutputScaleMode]. See [OutputScaleFixture] for how two pixels tell the three apart.
 *
 * The frame goes in through [SharedVideoOutput], the one decoded frame every output draws, so this
 * is the same path a playing clip takes -- only the decoder is left out.
 */
@OptIn(ExperimentalTestApi::class)
class MediaPresenterScaleTest {

    @AfterTest
    fun clearSharedFrame() {
        SharedVideoOutput.frame.value = null
    }

    private fun drawnAs(mode: OutputScaleMode) = runComposeUiTest {
        SharedVideoOutput.frame.value = OutputScaleFixture.source().toComposeImageBitmap()
        val viewModel = MediaViewModel().apply {
            loadMedia("file:///tmp/clip.mp4", Constants.MEDIA_TYPE_LOCAL)
        }

        setContent {
            CompositionLocalProvider(LocalMediaViewModel provides viewModel) {
                MediaPresenter(
                    modifier = Modifier.testTag("output").size(160.dp, 90.dp),
                    contentScale = mode.contentScale,
                )
            }
        }

        OutputScaleFixture.assertDrawnAs(mode, onNodeWithTag("output").captureToImage().toPixelMap())
    }

    @Test
    fun `fit shows the whole frame with bars at the sides`() = drawnAs(OutputScaleMode.FIT)

    @Test
    fun `fill covers the screen by cropping the top and bottom`() = drawnAs(OutputScaleMode.FILL)

    @Test
    fun `stretch covers the screen by squashing the frame`() = drawnAs(OutputScaleMode.STRETCH)
}
