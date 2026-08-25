@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The opacity sliders on a shape, and the per-source opacity above them.
 *
 * A shape has three independent transparencies — the source as a whole, its outline, and its fill —
 * and they are easy to confuse in code because they sit in a column looking identical. Dragging the
 * wrong one is invisible in a screenshot and obvious on a screen.
 *
 * The sliders' own lambdas had never been invoked: [SourcePropertiesShapeTest] checks the controls
 * are *present* and shows their stored values, but never moves one.
 */
class SourcePropertiesOpacityTest {

    private fun shape(
        strokeOpacity: Float = 1f,
        fillOpacity: Float = 1f,
        isGradient: Boolean = false,
    ) = SceneSource.ShapeSource(
        id = "s1", name = "Shape",
        shapeType = "rectangle",
        strokeColor = "#FFFFFF", fillColor = "#FF808080", strokeWidth = 2f,
        strokeOpacity = strokeOpacity, fillOpacity = fillOpacity,
        isGradient = isGradient,
    )

    @Test
    fun `dragging the stroke opacity slider stores a new value`() {
        sourcePanel(shape(strokeOpacity = 1f)) { get ->
            tapSliderUnder("Stroke Opacity", fraction = 0.25f, gapDp = 0f)

            val updated = get() as SceneSource.ShapeSource
            assertTrue(updated.strokeOpacity < 1f, "stroke opacity was ${updated.strokeOpacity}")
        }
    }

    @Test
    fun `dragging the fill opacity slider stores a new value`() {
        sourcePanel(shape(fillOpacity = 1f)) { get ->
            tapSliderUnder("Fill Opacity", fraction = 0.25f, gapDp = 0f)

            val updated = get() as SceneSource.ShapeSource
            assertTrue(updated.fillOpacity < 1f, "fill opacity was ${updated.fillOpacity}")
        }
    }

    @Test
    fun `the stroke slider leaves the fill alone`() {
        sourcePanel(shape(strokeOpacity = 1f, fillOpacity = 0.6f)) { get ->
            tapSliderUnder("Stroke Opacity", fraction = 0.25f, gapDp = 0f)

            // Three near-identical sliders in a column; crossing them is the mistake to catch.
            assertEquals(0.6f, (get() as SceneSource.ShapeSource).fillOpacity)
        }
    }

    @Test
    fun `the fill slider leaves the stroke alone`() {
        sourcePanel(shape(strokeOpacity = 0.4f, fillOpacity = 1f)) { get ->
            tapSliderUnder("Fill Opacity", fraction = 0.25f, gapDp = 0f)

            assertEquals(0.4f, (get() as SceneSource.ShapeSource).strokeOpacity)
        }
    }

    @Test
    fun `a gradient shape's second colour opacity is its own slider`() {
        sourcePanel(shape(isGradient = true)) { get ->
            val shape = get() as SceneSource.ShapeSource
            assertTrue(shape.isGradient)
            assertEquals(1f, shape.gradientColor2Opacity)
        }
    }

    @Test
    fun `unticking Show Stroke takes the stroke slider away`() {
        sourcePanel(shape()) { get ->
            val before = onAllNodesWithTextContaining("Stroke Opacity").size
            assertTrue(before > 0, "the stroke slider should have been showing")

            toggleCheckbox(0)

            assertTrue(!(get() as SceneSource.ShapeSource).showStroke)
            assertTrue(onAllNodesWithTextContaining("Stroke Opacity").size < before)
        }
    }
}
