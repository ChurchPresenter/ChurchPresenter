@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.app.churchpresenter.dialogs.tabs.recolor
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Color source: a colour, an opacity, and a gradient that unfolds four more controls when it is
 * switched on.
 *
 * The gradient checkbox is the structural control here — half this panel does not exist until it is
 * ticked, so what it reveals and hides is asserted as carefully as what it stores. The Position
 * slider is the other thing worth pinning: it is the one control on the whole panel whose displayed
 * units are not its stored units, showing 0–100 for a fraction stored as 0–1.
 */
class SourcePropertiesColorTest {

    /** With the gradient on, the two `PropertySliderWithInput`s add fields after the header's six. */
    private object Field {
        const val ANGLE = 6
        const val POSITION = 7
    }

    private fun gradient() = Fixture.color().copy(isGradient = true)

    // ── What the panel displays ───────────────────────────────────────────────

    @Test
    fun `the section is headed and the flat-colour controls captioned`() = sourcePanel(Fixture.color()) { _ ->
        onNodeWithText(Label.COLOR).assertIsDisplayed()
        onNodeWithText("COLOR 1").assertIsDisplayed()
        onNodeWithText("Gradient").assertIsDisplayed()
        onNodeWithText("Color 1 Opacity").assertIsDisplayed()
    }

    @Test
    fun `a flat colour shows its hex and full opacity`() = sourcePanel(Fixture.color()) { _ ->
        onNodeWithText("#000000").assertExists("the colour field reads out its stored hex")
        // Both the header's opacity and this panel's own start at 100%.
        assertEquals(2, countOf("100%"), "the colour's opacity slider reads out its stored value")
    }

    @Test
    fun `a flat colour offers no gradient control at all`() = sourcePanel(Fixture.color()) { _ ->
        listOf("Color 2", "COLOR 2", "Color 2 Opacity", "Angle", "Position").forEach { caption ->
            assertEquals(0, countOf(caption), "\"$caption\" belongs to the gradient, which is off")
        }
        checkboxes().assertCountEquals(1)
        textFields().assertCountEquals(6) // the header's six, and nothing more
    }

    @Test
    fun `a gradient captions all four of its own controls`() = sourcePanel(gradient()) { _ ->
        onNodeWithText("COLOR 2").assertIsDisplayed()
        onNodeWithText("Color 2 Opacity").assertExists()
        onNodeWithText("Angle").assertExists()
        onNodeWithText("Position").assertExists()
        onNodeWithText("#FFFFFF").assertExists("the second colour reads out its stored hex")
    }

    @Test
    fun `a gradient shows its angle and position in the units an operator reads`() {
        sourcePanel(gradient().copy(gradientAngle = 90f, gradientPosition = 0.25f)) { _ ->
            assertFieldShows("90", "the angle input")
            assertFieldShows("25", "the position input — a stored 0.25 reads as 25%")
        }
    }

    // ── The gradient toggle ───────────────────────────────────────────────────

    @Test
    fun `the gradient is off out of the box`() = sourcePanel(Fixture.color()) { _ ->
        checkboxes()[0].assertIsOff()
    }

    @Test
    fun `ticking Gradient stores the flag and unfolds the gradient controls`() =
        sourcePanel(Fixture.color()) { get ->
            toggleCheckbox(0)

            assertEquals(true, (get() as SceneSource.ColorSource).isGradient)
            assertEquals(
                Fixture.color().copy(isGradient = true), get(),
                "ticking the box may change only that flag",
            )
            checkboxes()[0].assertIsOn()
            onNodeWithText("COLOR 2").assertExists("the second colour appears with it")
            onNodeWithText("Angle").assertExists()
        }

    @Test
    fun `unticking Gradient folds them away again`() = sourcePanel(gradient()) { get ->
        toggleCheckbox(0)

        assertEquals(false, (get() as SceneSource.ColorSource).isGradient)
        assertEquals(0, countOf("COLOR 2"), "the second colour goes with it")
        assertEquals(0, countOf("Angle"))
        textFields().assertCountEquals(6)
    }

    // ── Colours ───────────────────────────────────────────────────────────────

    @Test
    fun `recolouring the first colour stores the new hex`() = sourcePanel(Fixture.color()) { get ->
        recolor(fromHex = "#000000", toHex = "#FF8800")

        assertEquals("#FF8800", (get() as SceneSource.ColorSource).color)
    }

    @Test
    fun `recolouring the second colour stores it without touching the first`() = sourcePanel(gradient()) { get ->
        recolor(fromHex = "#FFFFFF", toHex = "#0044CC")

        val source = get() as SceneSource.ColorSource
        assertEquals("#0044CC", source.gradientColor2, "the second colour takes the new value")
        assertEquals("#000000", source.color, "and the first is untouched")
    }

    // ── Opacity sliders ───────────────────────────────────────────────────────

    @Test
    fun `dragging the first opacity slider to its near end makes the colour invisible`() =
        sourcePanel(Fixture.color()) { get ->
            tapSliderUnder("Color 1 Opacity", fraction = 0f, gapDp = Gap.READOUT)

            assertEquals(0f, (get() as SceneSource.ColorSource).sourceOpacity)
            onNodeWithText("0%").assertExists("the read-out follows the slider")
        }

    @Test
    fun `dragging the first opacity slider to its far end makes the colour solid`() {
        sourcePanel(Fixture.color().copy(sourceOpacity = 0.3f)) { get ->
            tapSliderUnder("Color 1 Opacity", fraction = 1f, gapDp = Gap.READOUT)

            assertEquals(1f, (get() as SceneSource.ColorSource).sourceOpacity)
            assertEquals(2, countOf("100%"), "the colour's read-out joins the header's opacity at 100%")
        }
    }

    @Test
    fun `the second opacity slider drives the gradient's own colour`() {
        sourcePanel(gradient().copy(gradientColor2Opacity = 0.9f)) { get ->
            tapSliderUnder("Color 2 Opacity", fraction = 0f, gapDp = Gap.READOUT)

            val source = get() as SceneSource.ColorSource
            assertEquals(0f, source.gradientColor2Opacity, "the second slider writes the second opacity")
            assertEquals(1f, source.sourceOpacity, "and leaves the first alone")
        }
    }

    // ── Angle ─────────────────────────────────────────────────────────────────

    @Test
    fun `committing an angle turns the gradient`() = sourcePanel(gradient()) { get ->
        commitField(Field.ANGLE, "270")

        assertEquals(270f, (get() as SceneSource.ColorSource).gradientAngle)
        assertFieldShows("270", "the angle input after committing")
    }

    @Test
    fun `an angle past a full turn is clamped to the end of the range`() = sourcePanel(gradient()) { get ->
        commitField(Field.ANGLE, "400")

        assertEquals(360f, (get() as SceneSource.ColorSource).gradientAngle, "the range tops out at 360°")
    }

    @Test
    fun `a negative angle is clamped to zero`() = sourcePanel(gradient()) { get ->
        commitField(Field.ANGLE, "-45")

        assertEquals(0f, (get() as SceneSource.ColorSource).gradientAngle, "the range starts at 0°")
    }

    @Test
    fun `text that is not a number leaves the angle alone`() {
        sourcePanel(gradient().copy(gradientAngle = 120f)) { get ->
            commitField(Field.ANGLE, "diagonal")

            assertEquals(120f, (get() as SceneSource.ColorSource).gradientAngle)
            assertFieldShows("120", "the angle input after rejecting the typed text")
        }
    }

    @Test
    fun `dragging the angle slider to its far end is a full turn`() = sourcePanel(gradient()) { get ->
        tapSliderUnder("Angle", fraction = 1f, gapDp = Gap.INPUT)

        assertEquals(360f, (get() as SceneSource.ColorSource).gradientAngle)
        assertFieldShows("360", "the input beside the slider follows it")
    }

    // ── Position ──────────────────────────────────────────────────────────────

    @Test
    fun `committing a position stores it as a fraction`() = sourcePanel(gradient()) { get ->
        commitField(Field.POSITION, "80")

        assertEquals(
            0.8f, (get() as SceneSource.ColorSource).gradientPosition,
            "the input reads 0–100 but the model stores 0–1",
        )
        assertFieldShows("80", "the position input after committing")
    }

    @Test
    fun `a position past the end of the range is clamped`() = sourcePanel(gradient()) { get ->
        commitField(Field.POSITION, "500")

        assertEquals(1f, (get() as SceneSource.ColorSource).gradientPosition, "a fraction cannot exceed 1")
    }

    @Test
    fun `a negative position is clamped to the start`() = sourcePanel(gradient()) { get ->
        commitField(Field.POSITION, "-20")

        assertEquals(0f, (get() as SceneSource.ColorSource).gradientPosition)
    }

    @Test
    fun `dragging the position slider moves the gradient's midpoint`() = sourcePanel(gradient()) { get ->
        tapSliderUnder("Position", fraction = 1f, gapDp = Gap.INPUT)

        assertEquals(1f, (get() as SceneSource.ColorSource).gradientPosition)
    }

    @Test
    fun `a mid-track tap on the position slider lands between the ends`() = sourcePanel(gradient()) { get ->
        tapSliderUnder("Position", fraction = 0.5f, gapDp = Gap.INPUT)

        val position = (get() as SceneSource.ColorSource).gradientPosition
        assertTrue(position > 0f && position < 1f, "a mid-track tap lands inside the range, was $position")
    }
}
