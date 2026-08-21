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
import org.churchpresenter.presentationengine.model.Direction
import org.churchpresenter.presentationengine.model.TransitionType
import kotlin.test.Test

class PresentationPresenterTransitionTest {

    private val from = Color.Red
    private val to = Color.Blue

    private fun render(type: TransitionType, direction: Direction?, progress: Float = 0.5f): PixelMap {
        val transition = TransitionOverlay(
            type = type,
            direction = direction,
            progress = progress,
            fromLayers = listOf(placedLayer(from, id = "from")),
        )
        val frame = presentationFrame(listOf(placedLayer(to, id = "to")), transition = transition)
        lateinit var pixels: PixelMap
        runComposeUiTest {
            setContent {
                Box(Modifier.size(200.dp, 200.dp)) {
                    PresentationPresenter(frame = frame, slide = null, modifier = Modifier.testTag("canvas"))
                }
            }
            pixels = onNodeWithTag("canvas").captureToImage().toPixelMap()
        }
        return pixels
    }

    @Test
    fun `FADE blends the from and to layers by progress`() {
        val pixels = render(TransitionType.FADE, direction = null)
        assertColorAt(pixels, 100, 100, Color(0.5f, 0f, 0.5f))
    }

    @Test
    fun `NONE ignores the from layers entirely`() {
        val pixels = render(TransitionType.NONE, direction = null)
        assertColorAt(pixels, 100, 100, to)
    }

    @Test
    fun `PUSH LEFT slides the incoming layer in from the right`() {
        val pixels = render(TransitionType.PUSH, direction = Direction.LEFT)
        assertColorAt(pixels, 50, 100, from)
        assertColorAt(pixels, 150, 100, to)
    }

    @Test
    fun `PUSH RIGHT slides the incoming layer in from the left`() {
        val pixels = render(TransitionType.PUSH, direction = Direction.RIGHT)
        assertColorAt(pixels, 50, 100, to)
        assertColorAt(pixels, 150, 100, from)
    }

    @Test
    fun `PUSH with an unhandled direction falls back to LEFT's movement`() {
        val pixels = render(TransitionType.PUSH, direction = Direction.IN)
        assertColorAt(pixels, 50, 100, from)
        assertColorAt(pixels, 150, 100, to)
    }

    @Test
    fun `COVER leaves the outgoing layer fixed while the incoming layer slides over it`() {
        val pixels = render(TransitionType.COVER, direction = Direction.RIGHT)
        assertColorAt(pixels, 50, 100, to)
        assertColorAt(pixels, 150, 100, from)
    }

    @Test
    fun `WIPE RIGHT reveals the incoming layer from the left edge`() {
        val pixels = render(TransitionType.WIPE, direction = Direction.RIGHT)
        assertColorAt(pixels, 50, 100, to)
        assertColorAt(pixels, 150, 100, from)
    }

    @Test
    fun `WIPE LEFT reveals the incoming layer from the right edge`() {
        val pixels = render(TransitionType.WIPE, direction = Direction.LEFT)
        assertColorAt(pixels, 50, 100, from)
        assertColorAt(pixels, 150, 100, to)
    }

    @Test
    fun `WIPE UP reveals the incoming layer from the bottom edge`() {
        val pixels = render(TransitionType.WIPE, direction = Direction.UP)
        assertColorAt(pixels, 100, 50, from)
        assertColorAt(pixels, 100, 150, to)
    }

    @Test
    fun `WIPE with an unhandled direction reveals like DOWN, from the top edge`() {
        val pixels = render(TransitionType.WIPE, direction = null)
        assertColorAt(pixels, 100, 50, to)
        assertColorAt(pixels, 100, 150, from)
    }

    @Test
    fun `SPLIT LEFT or RIGHT reveals a vertical band down the middle`() {
        val pixels = render(TransitionType.SPLIT, direction = Direction.LEFT)
        assertColorAt(pixels, 100, 100, to)
        assertColorAt(pixels, 10, 100, from)
        assertColorAt(pixels, 190, 100, from)
    }

    @Test
    fun `SPLIT with any other direction reveals a horizontal band through the middle`() {
        val pixels = render(TransitionType.SPLIT, direction = Direction.UP)
        assertColorAt(pixels, 100, 100, to)
        assertColorAt(pixels, 100, 10, from)
        assertColorAt(pixels, 100, 190, from)
    }
}
