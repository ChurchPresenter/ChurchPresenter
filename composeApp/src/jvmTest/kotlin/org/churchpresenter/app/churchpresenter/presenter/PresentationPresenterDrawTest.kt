@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import presentation.engine.model.RevealClip
import presentation.engine.model.LayerState
import kotlin.test.Test

class PresentationPresenterDrawTest {

    private fun render(frame: PresentationFrame, boxSize: Int = 200): PixelMap {
        lateinit var pixels: PixelMap
        runComposeUiTest {
            setContent {
                Box(Modifier.size(boxSize.dp, boxSize.dp)) {
                    PresentationPresenter(frame = frame, slide = null, modifier = Modifier.testTag("canvas"))
                }
            }
            pixels = onNodeWithTag("canvas").captureToImage().toPixelMap()
        }
        return pixels
    }

    @Test
    fun `a single full-frame layer fills the whole canvas when aspect ratios match`() {
        val frame = presentationFrame(
            listOf(placedLayer(Color.Red, width = 100, height = 100)),
            frameWidthPx = 100,
            frameHeightPx = 100
        )
        val pixels = render(frame)
        assertColorAt(pixels, 20, 20, Color.Red)
        assertColorAt(pixels, 100, 100, Color.Red)
        assertColorAt(pixels, 180, 180, Color.Red)
    }

    @Test
    fun `a mismatched aspect ratio letterboxes with the black background`() {
        val frame = presentationFrame(
            listOf(placedLayer(Color.Red, width = 100, height = 200)),
            frameWidthPx = 100,
            frameHeightPx = 200
        )
        val pixels = render(frame, boxSize = 200)
        assertColorAt(pixels, 25, 100, Color.Black)
        assertColorAt(pixels, 100, 100, Color.Red)
        assertColorAt(pixels, 175, 100, Color.Black)
    }

    @Test
    fun `a layer offset within the frame leaves the rest as background`() {
        val layer = placedLayer(Color.Red, width = 40, height = 40, offsetXPx = 30, offsetYPx = 30)
        val frame = presentationFrame(listOf(layer), frameWidthPx = 100, frameHeightPx = 100)
        val pixels = render(frame)
        assertColorAt(pixels, 100, 100, Color.Red)
        assertColorAt(pixels, 10, 10, Color.Black)
    }

    @Test
    fun `a layer with zero alpha is not drawn`() {
        val layer = placedLayer(Color.Red, width = 100, height = 100, state = LayerState(alpha = 0.0))
        val frame = presentationFrame(listOf(layer), frameWidthPx = 100, frameHeightPx = 100)
        val pixels = render(frame)
        assertColorAt(pixels, 100, 100, Color.Black)
    }

    @Test
    fun `later layers in the list draw over earlier ones`() {
        val bottom = placedLayer(Color.Red, width = 100, height = 100, id = "bottom")
        val top = placedLayer(Color.Blue, width = 100, height = 100, id = "top")
        val frame = presentationFrame(listOf(bottom, top), frameWidthPx = 100, frameHeightPx = 100)
        val pixels = render(frame)
        assertColorAt(pixels, 100, 100, Color.Blue)
    }

    @Test
    fun `a clipped layer only paints within its reveal rect`() {
        val layer = placedLayer(
            Color.Red,
            width = 100,
            height = 100,
            state = LayerState(clip = RevealClip(left = 0.0, top = 0.0, right = 0.5, bottom = 1.0)),
        )
        val frame = presentationFrame(listOf(layer), frameWidthPx = 100, frameHeightPx = 100)
        val pixels = render(frame)
        assertColorAt(pixels, 50, 100, Color.Red)
        assertColorAt(pixels, 150, 100, Color.Black)
    }
}
