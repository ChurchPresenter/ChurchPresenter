package org.churchpresenter.slides.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.presentationengine.model.Direction
import org.churchpresenter.presentationengine.model.TransitionType
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.testing.assertColorAt
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PresentationPresenterOptionsTest {

    private fun render(direction: Direction): PixelMap {
        val transition = TransitionOverlay(
            type = TransitionType.PUSH,
            direction = direction,
            progress = 0.5f,
            fromLayers = listOf(placedLayer(Color.Red, id = "from")),
        )
        val frame = presentationFrame(listOf(placedLayer(Color.Blue, id = "to")), transition = transition)
        lateinit var pixels: PixelMap
        runComposeUiTest {
            setContent {
                Box(Modifier.size(200.dp, 200.dp)) {
                    PresentationPresenter(
                        frame = frame,
                        slide = null,
                        previousSlide = null,
                        transitionAlpha = 1f,
                        slideOffset = 1f,
                        animationType = AnimationType.FADE,
                        outputRole = Constants.OUTPUT_ROLE_NORMAL,
                        frozen = false,
                        modifier = Modifier.testTag("canvas"),
                    )
                }
            }
            pixels = onNodeWithTag("canvas").captureToImage().toPixelMap()
        }
        return pixels
    }

    @Test
    fun `a push downward brings the new slide in from the top`() {
        val pixels = render(Direction.DOWN)
        assertColorAt(pixels, pixels.width / 2, pixels.height / 4, Color.Blue)
        assertColorAt(pixels, pixels.width / 2, pixels.height * 3 / 4, Color.Red)
    }

    @Test
    fun `a push upward brings the new slide in from the bottom`() {
        val pixels = render(Direction.UP)
        assertColorAt(pixels, pixels.width / 2, pixels.height * 3 / 4, Color.Blue)
        assertColorAt(pixels, pixels.width / 2, pixels.height / 4, Color.Red)
    }

    @Test
    fun `a frozen output is blanked, neither the still slide nor the live frame`() = runComposeUiTest {
        val frame = presentationFrame(listOf(placedLayer(Color.Blue, id = "live")))
        val still = solidColorBitmap(16, 9, Color.Green)
        setContent {
            Box(Modifier.size(160.dp, 90.dp)) {
                PresentationPresenter(frame = frame, slide = still, frozen = true)
            }
        }
        onAllNodesWithContentDescription("Presented Slide").assertCountEquals(0)
    }

    @Test
    fun `with no frame yet the still slide is drawn`() = runComposeUiTest {
        val still = solidColorBitmap(16, 9, Color.Green)
        setContent { Box(Modifier.size(160.dp, 90.dp)) { PresentationPresenter(frame = null, slide = still) } }
        assertEquals(1, onAllNodesWithContentDescription("Presented Slide").fetchSemanticsNodes().size)
    }

    @Test
    fun `a frame and the data it carries read back as given`() {
        val transition = TransitionOverlay(TransitionType.FADE, null, 0.25f, emptyList())
        val frame = PresentationFrame(2, 1920, 1080, 2f, emptyList(), 1, 3, transition)
        assertEquals(2, frame.slideIndex)
        assertEquals(3, frame.stepCount)
        assertEquals(1, frame.completedSteps)
        assertEquals(0.25f, frame.transition?.progress)
        assertEquals(frame, frame.copy())
        assertEquals(frame.hashCode(), frame.copy().hashCode())
    }
}
