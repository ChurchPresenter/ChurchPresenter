@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.app.churchpresenter.dialogs.tabs.recolor
import org.churchpresenter.app.churchpresenter.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Shape source, whose panel is assembled differently for the two families of shape it serves.
 *
 * A shape that encloses an area — a rectangle, an ellipse — gets a fill, a "show stroke" switch and a
 * gradient. A shape that is only a line — `line`, `arrow`, `freehand` — has nothing to fill, so the
 * panel drops the switch, the fill colour, the fill opacity and the whole gradient block, and shows
 * the stroke controls unconditionally instead. That distinction is drawn in four separate `if`s over
 * the same derived flag, and getting any one of them wrong offers an operator a control that writes
 * to a field their shape does not use. Each family is therefore inventoried in full, and every one of
 * the three stroke-only shape types is checked to land in the same place.
 *
 * The other thing worth pinning is the interaction between "show stroke" and the stroke controls: for
 * an enclosing shape they appear only when it is ticked, and for a line they must appear regardless
 * of what the flag happens to hold.
 */
class SourcePropertiesShapeTest {

    /** With the stroke shown, the stroke width input is the panel's own first field. */
    private object Field {
        const val STROKE_WIDTH = 6
        const val GRADIENT_ANGLE = 7
        const val GRADIENT_POSITION = 8
    }

    private val strokeOnlyTypes = listOf("line", "arrow", "freehand")

    private fun line() = Fixture.shape().copy(shapeType = "line")

    // ── An enclosing shape ────────────────────────────────────────────────────

    @Test
    fun `the section is headed and every control of an enclosing shape captioned`() =
        sourcePanel(Fixture.shape()) { _ ->
            listOf(
                "Show Stroke", "STROKE", "Stroke Opacity", "FILL", "Fill Opacity",
                "Stroke Width", "Gradient",
            ).forEach { caption ->
                onNodeWithText(caption).assertExists("\"$caption\" must caption a control on the shape panel")
            }
            onNodeWithText(Label.SHAPE).assertIsDisplayed()
        }

    @Test
    fun `an enclosing shape offers two checkboxes and one field of its own`() =
        sourcePanel(Fixture.shape()) { _ ->
            // Show Stroke and Gradient; the stroke width input. The gradient is off, so its own
            // controls are not counted here.
            checkboxes().assertCountEquals(2)
            textFields().assertCountEquals(7)
        }

    @Test
    fun `an enclosing shape shows its stored colours and width`() {
        val styled = Fixture.shape().copy(strokeColor = "#FF0000", fillColor = "#00FF00", strokeWidth = 8f)
        sourcePanel(styled) { _ ->
            onNodeWithText("#FF0000").assertExists("the stroke colour field reads out its hex")
            onNodeWithText("#00FF00").assertExists("the fill colour field reads out its hex")
            assertFieldShows("8", "the stroke width input")
        }
    }

    // ── A stroke-only shape ───────────────────────────────────────────────────

    @Test
    fun `a line offers stroke controls and nothing that fills`() = sourcePanel(line()) { _ ->
        onNodeWithText("STROKE").assertExists("a line still has a stroke colour")
        onNodeWithText("Stroke Opacity").assertExists()
        onNodeWithText("Stroke Width").assertExists()

        listOf("Show Stroke", "FILL", "Fill Opacity", "Gradient").forEach { caption ->
            assertEquals(0, countOf(caption), "\"$caption\" has nothing to act on for a line")
        }
        checkboxes().assertCountEquals(0)
    }

    @Test
    fun `every stroke-only shape type is treated the same way`() {
        strokeOnlyTypes.forEach { type ->
            sourcePanel(Fixture.shape().copy(shapeType = type)) { _ ->
                assertEquals(0, countOf("Show Stroke"), "\"$type\" must not offer a stroke switch")
                assertEquals(0, countOf("FILL"), "\"$type\" must not offer a fill")
                onNodeWithText("Stroke Width").assertExists("\"$type\" must still offer a stroke width")
            }
        }
    }

    @Test
    fun `a line shows its stroke controls even with the stroke flag off`() {
        // The flag is meaningless for a line, so the panel must ignore it rather than obey it.
        sourcePanel(line().copy(showStroke = false)) { _ ->
            onNodeWithText("STROKE").assertExists()
            onNodeWithText("Stroke Width").assertExists()
        }
    }

    // ── Show Stroke ───────────────────────────────────────────────────────────

    @Test
    fun `Show Stroke is ticked out of the box`() = sourcePanel(Fixture.shape()) { _ ->
        checkboxes()[0].assertIsOn()
    }

    @Test
    fun `unticking Show Stroke stores the flag and takes the stroke controls away`() =
        sourcePanel(Fixture.shape()) { get ->
            toggleCheckbox(0)

            assertEquals(
                Fixture.shape().copy(showStroke = false), get(),
                "unticking the box may change only that flag",
            )
            checkboxes()[0].assertIsOff()
            assertEquals(0, countOf("STROKE"), "the stroke colour goes with it")
            assertEquals(0, countOf("Stroke Opacity"))
            assertEquals(0, countOf("Stroke Width"))
        }

    @Test
    fun `ticking Show Stroke brings them back`() {
        sourcePanel(Fixture.shape().copy(showStroke = false)) { get ->
            toggleCheckbox(0)

            assertEquals(true, (get() as SceneSource.ShapeSource).showStroke)
            onNodeWithText("STROKE").assertExists()
            onNodeWithText("Stroke Width").assertExists()
        }
    }

    // ── Colours ───────────────────────────────────────────────────────────────

    @Test
    fun `recolouring the stroke stores the new hex`() = sourcePanel(Fixture.shape()) { get ->
        recolor(fromHex = "#FFFFFF", toHex = "#FF3300")

        val source = get() as SceneSource.ShapeSource
        assertEquals("#FF3300", source.strokeColor, "the stroke takes the new colour")
        assertEquals("#00000000", source.fillColor, "and the fill is untouched")
    }

    @Test
    fun `recolouring the fill stores the new hex`() = sourcePanel(Fixture.shape()) { get ->
        recolor(fromHex = "#00000000", toHex = "#0088FF")

        val source = get() as SceneSource.ShapeSource
        assertEquals("#0088FF", source.fillColor, "the fill takes the new colour")
        assertEquals("#FFFFFF", source.strokeColor, "and the stroke is untouched")
    }

    // ── Opacity sliders ───────────────────────────────────────────────────────

    @Test
    fun `dragging stroke opacity to its near end makes the outline invisible`() =
        sourcePanel(Fixture.shape()) { get ->
            tapSliderUnder("Stroke Opacity", fraction = 0f, gapDp = Gap.READOUT)

            val source = get() as SceneSource.ShapeSource
            assertEquals(0f, source.strokeOpacity)
            assertEquals(1f, source.fillOpacity, "and leaves the fill's own opacity alone")
        }

    @Test
    fun `dragging fill opacity to its near end makes the fill invisible`() = sourcePanel(Fixture.shape()) { get ->
        tapSliderUnder("Fill Opacity", fraction = 0f, gapDp = Gap.READOUT)

        val source = get() as SceneSource.ShapeSource
        assertEquals(0f, source.fillOpacity)
        assertEquals(1f, source.strokeOpacity, "and leaves the stroke's own opacity alone")
    }

    // ── Stroke width ──────────────────────────────────────────────────────────

    @Test
    fun `committing a stroke width stores it`() = sourcePanel(Fixture.shape()) { get ->
        commitField(Field.STROKE_WIDTH, "12")

        assertEquals(12f, (get() as SceneSource.ShapeSource).strokeWidth)
        assertFieldShows("12", "the stroke width input after committing")
    }

    @Test
    fun `a stroke width below the minimum is raised to it`() = sourcePanel(Fixture.shape()) { get ->
        commitField(Field.STROKE_WIDTH, "0")

        assertEquals(1f, (get() as SceneSource.ShapeSource).strokeWidth, "the thinnest stroke is 1px")
    }

    @Test
    fun `a stroke width above the maximum is lowered to it`() = sourcePanel(Fixture.shape()) { get ->
        commitField(Field.STROKE_WIDTH, "200")

        assertEquals(20f, (get() as SceneSource.ShapeSource).strokeWidth, "the thickest stroke is 20px")
    }

    @Test
    fun `text that is not a number leaves the stroke width alone`() = sourcePanel(Fixture.shape()) { get ->
        commitField(Field.STROKE_WIDTH, "thick")

        assertEquals(3f, (get() as SceneSource.ShapeSource).strokeWidth)
        assertFieldShows("3", "the stroke width input after rejecting the typed text")
    }

    @Test
    fun `dragging the stroke width slider to its far end is the thickest stroke`() =
        sourcePanel(Fixture.shape()) { get ->
            tapSliderUnder("Stroke Width", fraction = 1f, gapDp = Gap.INPUT)

            assertEquals(20f, (get() as SceneSource.ShapeSource).strokeWidth)
            assertFieldShows("20", "the input beside the slider follows it")
        }

    @Test
    fun `the stroke width input is suffixed with its unit`() = sourcePanel(Fixture.shape()) { _ ->
        onNodeWithText("px").assertExists("an operator must see what the number means")
    }

    // ── Gradient ──────────────────────────────────────────────────────────────

    @Test
    fun `the gradient is off out of the box`() = sourcePanel(Fixture.shape()) { _ ->
        checkboxes()[1].assertIsOff()
        assertEquals(0, countOf("Angle"), "and its own controls are not offered")
    }

    @Test
    fun `ticking Gradient stores the flag and unfolds its controls`() = sourcePanel(Fixture.shape()) { get ->
        toggleCheckbox(1)

        assertEquals(
            Fixture.shape().copy(isGradient = true), get(),
            "ticking the box may change only that flag",
        )
        checkboxes()[1].assertIsOn()
        onNodeWithText("COLOR 2").assertExists()
        onNodeWithText("Angle").assertExists()
        onNodeWithText("Position").assertExists()
        textFields().assertCountEquals(9) // the stroke width, plus the angle and position inputs
    }

    @Test
    fun `committing a gradient angle turns the shape's gradient`() {
        sourcePanel(Fixture.shape().copy(isGradient = true)) { get ->
            commitField(Field.GRADIENT_ANGLE, "45")

            assertEquals(45f, (get() as SceneSource.ShapeSource).gradientAngle)
        }
    }

    @Test
    fun `committing a gradient position stores it as a fraction`() {
        sourcePanel(Fixture.shape().copy(isGradient = true)) { get ->
            commitField(Field.GRADIENT_POSITION, "30")

            assertEquals(
                0.3f, (get() as SceneSource.ShapeSource).gradientPosition,
                "the input reads 0–100 but the model stores 0–1",
            )
        }
    }

    @Test
    fun `dragging the second colour's opacity writes only that opacity`() {
        sourcePanel(Fixture.shape().copy(isGradient = true, gradientColor2Opacity = 0.8f)) { get ->
            tapSliderUnder("Color 2 Opacity", fraction = 0f, gapDp = Gap.READOUT)

            val source = get() as SceneSource.ShapeSource
            assertEquals(0f, source.gradientColor2Opacity)
            assertEquals(1f, source.fillOpacity, "the fill's opacity is a different setting")
            assertEquals(1f, source.strokeOpacity)
        }
    }
}
