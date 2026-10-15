package org.churchpresenter.slides.presenter

import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.presentationengine.model.Direction
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.LayerState
import org.churchpresenter.presentationengine.model.RectPt
import org.churchpresenter.presentationengine.model.TransitionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The frame every output window draws a deck from, as the player hands it over. */
class PresentationFrameTest {

    private val bitmap = ImageBitmap(4, 4)
    private val layer = PlacedLayer(
        spec = LayerSpec.StaticComposite(id = "title", zIndex = 0, boundsPt = RectPt(0.0, 0.0, 4.0, 4.0)),
        bitmap = bitmap,
        offsetXPx = 10,
        offsetYPx = 20,
        state = LayerState.VISIBLE,
    )

    @Test
    fun `a placed layer is where and how it was placed`() {
        assertEquals("title", layer.spec.id)
        assertSame(bitmap, layer.bitmap)
        assertEquals(10, layer.offsetXPx)
        assertEquals(20, layer.offsetYPx)
        assertEquals(LayerState.VISIBLE, layer.state)
    }

    @Test
    fun `a frame with no transition running carries none`() {
        val frame = PresentationFrame(
            slideIndex = 2, frameWidthPx = 1920, frameHeightPx = 1080, scalePxPerPt = 2f,
            layers = listOf(layer), completedSteps = 1, stepCount = 3,
        )

        assertNull(frame.transition)
        assertEquals(2, frame.slideIndex)
        assertEquals(1920 to 1080, frame.frameWidthPx to frame.frameHeightPx)
        assertEquals(2f, frame.scalePxPerPt)
        assertEquals(listOf(layer), frame.layers)
        assertEquals(1 to 3, frame.completedSteps to frame.stepCount)
    }

    @Test
    fun `a transition into the slide carries the outgoing layers and how far it has run`() {
        val transition = TransitionOverlay(TransitionType.FADE, Direction.LEFT, 0.25f, listOf(layer))
        val frame = PresentationFrame(0, 100, 100, 1f, emptyList(), 0, 1, transition)

        assertEquals(TransitionType.FADE, frame.transition?.type)
        assertEquals(Direction.LEFT, frame.transition?.direction)
        assertEquals(0.25f, frame.transition?.progress)
        assertEquals(listOf(layer), frame.transition?.fromLayers)
    }
}
