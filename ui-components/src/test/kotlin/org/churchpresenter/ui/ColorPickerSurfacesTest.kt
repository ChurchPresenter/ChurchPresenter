package org.churchpresenter.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The colour picker's two drag surfaces: the saturation/brightness square and the hue bar.
 *
 * Both report a value derived purely from where the pointer landed, so what matters is the mapping
 * — and its orientation. Brightness runs *up* the square while the y-axis runs down, so a surface
 * that forgot to invert would hand back a colour that gets darker as the operator drags towards the
 * light corner. Every reported value is also clamped, because a drag can leave the surface.
 */
@OptIn(ExperimentalTestApi::class)
class ColorPickerSurfacesTest {

    private val tag = "surface"

    private fun assertClose(expected: Float, actual: Float, what: String, tol: Float = 0.15f) {
        assertTrue(abs(expected - actual) < tol, "$what: expected ~$expected but was $actual")
    }

    @Test
    fun `pressing the middle of the square reports mid saturation and brightness`() = runComposeUiTest {
        var s = -1f
        var v = -1f
        setContent {
            MaterialTheme {
                SvPanel(0f, 0f, 0f, { sat, bri -> s = sat; v = bri }, Modifier.size(200.dp).testTag(tag))
            }
        }
        onNodeWithTag(tag).performTouchInput { down(center); up() }
        waitForIdle()
        assertClose(0.5f, s, "saturation")
        assertClose(0.5f, v, "brightness")
    }

    @Test
    fun `the top-left corner is unsaturated and fully bright`() = runComposeUiTest {
        var s = -1f
        var v = -1f
        setContent {
            MaterialTheme {
                SvPanel(0f, 0f, 0f, { sat, bri -> s = sat; v = bri }, Modifier.size(200.dp).testTag(tag))
            }
        }
        onNodeWithTag(tag).performTouchInput { down(Offset(0f, 0f)); up() }
        waitForIdle()
        assertClose(0f, s, "saturation at the left edge")
        assertClose(1f, v, "brightness at the TOP — the y-axis is inverted")
    }

    @Test
    fun `the bottom-right corner is fully saturated and black`() = runComposeUiTest {
        var s = -1f
        var v = -1f
        setContent {
            MaterialTheme {
                SvPanel(0f, 0f, 0f, { sat, bri -> s = sat; v = bri }, Modifier.size(200.dp).testTag(tag))
            }
        }
        onNodeWithTag(tag).performTouchInput { // one pixel inside: the far corner itself is outside the node's bounds
            down(Offset(width - 1f, height - 1f)); up() }
        waitForIdle()
        assertClose(1f, s, "saturation at the right edge")
        assertClose(0f, v, "brightness at the bottom")
    }

    @Test
    fun `dragging across the square keeps reporting`() = runComposeUiTest {
        val seen = mutableListOf<Pair<Float, Float>>()
        setContent {
            MaterialTheme {
                SvPanel(0f, 0f, 0f, { s, v -> seen += s to v }, Modifier.size(200.dp).testTag(tag))
            }
        }
        onNodeWithTag(tag).performTouchInput {
            down(Offset(10f, 10f))
            moveTo(Offset(150f, 150f))
            up()
        }
        waitForIdle()
        assertTrue(seen.size >= 2, "a drag has to report more than the initial press")
        assertTrue(seen.last().first > seen.first().first, "dragging right raises saturation")
    }

    @Test
    fun `a value dragged past the edge is clamped, not reported out of range`() = runComposeUiTest {
        val seen = mutableListOf<Pair<Float, Float>>()
        setContent {
            MaterialTheme {
                SvPanel(0f, 0f, 0f, { s, v -> seen += s to v }, Modifier.size(200.dp).testTag(tag))
            }
        }
        onNodeWithTag(tag).performTouchInput {
            down(center)
            moveTo(Offset(width * 3f, height * 3f))
            up()
        }
        waitForIdle()
        assertTrue(seen.all { it.first in 0f..1f && it.second in 0f..1f }, "everything reported must be in range")
    }

    @Test
    fun `pressing the hue bar reports a hue from the x position`() = runComposeUiTest {
        var hue = -1f
        setContent {
            MaterialTheme { HueBar(0f, { hue = it }, Modifier.size(360.dp, 20.dp).testTag(tag)) }
        }
        onNodeWithTag(tag).performTouchInput { down(center); up() }
        waitForIdle()
        assertClose(180f, hue, "the middle of the bar is halfway round the wheel", tol = 20f)
    }

    @Test
    fun `the hue bar clamps at both ends`() = runComposeUiTest {
        val seen = mutableListOf<Float>()
        setContent {
            MaterialTheme { HueBar(0f, { seen += it }, Modifier.size(360.dp, 20.dp).testTag(tag)) }
        }
        onNodeWithTag(tag).performTouchInput {
            down(Offset(0f, height / 2f))
            moveTo(Offset(width * 2f, height / 2f))
            up()
        }
        waitForIdle()
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it in 0f..360f }, "a drag off the end must not report past 360°")
        assertEquals(360f, seen.last(), "dragging past the right edge pins to the top of the range")
    }
}
