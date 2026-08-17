package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [SvPanel] and [HueBar] (widened from `private` to `internal` for this) are the two drag
 * surfaces behind [ColorPickerDialog] — a bare `Box` with its own `pointerInput`/`awaitEachGesture`
 * drag detector and a `Canvas`, no semantics of its own, the same shape as `SlimSlider`. Tests
 * below drive them by tag and read back the resulting saturation/brightness or hue through the
 * `onChanged`/`onHueChange` callback rather than any drawn pixel, then a separate group drives the
 * full [ColorPickerDialog] end to end and asserts on the hex it ultimately reports through
 * [onColorSelected] — the outcome a caller actually observes, not the dialog's internal text state.
 *
 * `SvPanel`/`HueBar` size themselves from the `Modifier` passed by the caller (`onSizeChanged`), so
 * geometry expectations here are derived from the node's own measured size rather than a hardcoded
 * pixel count, keeping them independent of test-environment density.
 */
@OptIn(ExperimentalTestApi::class)
class ColorPickerDialogTest {

    private val recentColorsFile = File(System.getProperty("user.home"), ".churchpresenter/recent_colors.json")

    @BeforeTest
    fun freshRecentColors() {
        recentColorsFile.delete()
        RecentColors.colors.clear()
    }

    @AfterTest
    fun cleanupRecentColors() {
        recentColorsFile.delete()
        RecentColors.colors.clear()
    }

    // ── SvPanel ────────────────────────────────────────────────────────────────

    @Test
    fun `dragging to the panel's center reports half saturation and half brightness`() = runComposeUiTest {
        var saturation: Float? = null
        var brightness: Float? = null
        setContent {
            SvPanel(
                hue = 0f,
                saturation = 0f,
                brightness = 0f,
                onChanged = { s, v -> saturation = s; brightness = v },
                modifier = Modifier.testTag("sv").size(200.dp),
            )
        }
        val size = onNodeWithTag("sv").fetchSemanticsNode().size
        onNodeWithTag("sv").performTouchInput {
            down(Offset(size.width / 2f, size.height / 2f))
            up()
        }
        assertApprox(0.5f, requireNotNull(saturation), "center saturation")
        assertApprox(0.5f, requireNotNull(brightness), "center brightness")
    }

    @Test
    fun `dragging to the top-left corner reports zero saturation and full brightness`() = runComposeUiTest {
        var saturation: Float? = null
        var brightness: Float? = null
        setContent {
            SvPanel(
                hue = 0f,
                saturation = 1f,
                brightness = 1f,
                onChanged = { s, v -> saturation = s; brightness = v },
                modifier = Modifier.testTag("sv").size(200.dp),
            )
        }
        onNodeWithTag("sv").performTouchInput {
            down(Offset(0f, 0f))
            up()
        }
        assertApprox(0f, requireNotNull(saturation), "top-left saturation")
        assertApprox(1f, requireNotNull(brightness), "top-left brightness (y=0 is full brightness)")
    }

    @Test
    fun `dragging past the bottom-right edge clamps to full saturation and zero brightness`() = runComposeUiTest {
        var saturation: Float? = null
        var brightness: Float? = null
        setContent {
            SvPanel(
                hue = 0f,
                saturation = 0f,
                brightness = 0f,
                onChanged = { s, v -> saturation = s; brightness = v },
                modifier = Modifier.testTag("sv").size(200.dp),
            )
        }
        val size = onNodeWithTag("sv").fetchSemanticsNode().size
        onNodeWithTag("sv").performTouchInput {
            down(Offset(size.width / 2f, size.height / 2f))
            moveTo(Offset(size.width + 500f, size.height + 500f))
            up()
        }
        assertEquals(1f, saturation, "dragging past the right edge must clamp saturation to 1")
        assertEquals(0f, brightness, "dragging past the bottom edge must clamp brightness to 0")
    }

    @Test
    fun `a plain tap (no drag) still reports the tapped position`() = runComposeUiTest {
        var reported = false
        setContent {
            SvPanel(
                hue = 0f,
                saturation = 0f,
                brightness = 0f,
                onChanged = { _, _ -> reported = true },
                modifier = Modifier.testTag("sv").size(200.dp),
            )
        }
        onNodeWithTag("sv").performTouchInput { click(Offset(10f, 10f)) }
        assertTrue(reported, "even a tap with no drag must fire onChanged once, from the initial down")
    }

    // ── HueBar ─────────────────────────────────────────────────────────────────

    @Test
    fun `dragging to the bar's horizontal center reports a hue of 180`() = runComposeUiTest {
        var hue: Float? = null
        setContent {
            HueBar(
                hue = 0f,
                onHueChange = { hue = it },
                modifier = Modifier.testTag("hue").width(200.dp).height(24.dp),
            )
        }
        val size = onNodeWithTag("hue").fetchSemanticsNode().size
        onNodeWithTag("hue").performTouchInput {
            down(Offset(size.width / 2f, size.height / 2f))
            up()
        }
        assertApprox(180f, requireNotNull(hue), "center hue", tol = 2f)
    }

    @Test
    fun `dragging to the bar's start reports a hue of 0`() = runComposeUiTest {
        var hue: Float? = null
        setContent {
            HueBar(
                hue = 200f,
                onHueChange = { hue = it },
                modifier = Modifier.testTag("hue").width(200.dp).height(24.dp),
            )
        }
        onNodeWithTag("hue").performTouchInput { down(Offset(0f, 0f)); up() }
        assertApprox(0f, requireNotNull(hue), "start hue")
    }

    @Test
    fun `dragging past the bar's right edge clamps the hue to 360`() = runComposeUiTest {
        var hue: Float? = null
        setContent {
            HueBar(
                hue = 0f,
                onHueChange = { hue = it },
                modifier = Modifier.testTag("hue").width(200.dp).height(24.dp),
            )
        }
        val size = onNodeWithTag("hue").fetchSemanticsNode().size
        onNodeWithTag("hue").performTouchInput {
            down(Offset(size.width / 2f, size.height / 2f))
            moveTo(Offset(size.width + 500f, size.height / 2f))
            up()
        }
        assertEquals(360f, hue, "dragging past the right edge must clamp the hue to 360")
    }

    // ── Full ColorPickerDialog ─────────────────────────────────────────────────

    @Test
    fun `dragging the SV panel then confirming reports the dragged saturation and brightness`() = runComposeUiTest {
        var result: String? = null
        setContent {
            MaterialTheme {
                ColorPickerDialog(initialHex = "#FF0000", onDismiss = {}, onColorSelected = { result = it })
            }
        }
        val size = onNodeWithTag("colorPickerSvPanel").fetchSemanticsNode().size
        onNodeWithTag("colorPickerSvPanel").performTouchInput {
            down(Offset(size.width / 2f, size.height / 2f))
            up()
        }
        onNodeWithText("OK").performClick()
        // Red's hue (0) held fixed, dragged to the panel's center (s=0.5, v=0.5).
        val expected = cpColorToHex(cpHsvToColor(0f, 0.5f, 0.5f))
        assertEquals(expected, result, "confirming after an SV drag must report the dragged saturation/brightness")
    }

    @Test
    fun `dragging the hue bar then confirming reports the new hue at full saturation and value`() = runComposeUiTest {
        var result: String? = null
        setContent {
            MaterialTheme {
                ColorPickerDialog(initialHex = "#FF0000", onDismiss = {}, onColorSelected = { result = it })
            }
        }
        val size = onNodeWithTag("colorPickerHueBar").fetchSemanticsNode().size
        onNodeWithTag("colorPickerHueBar").performTouchInput {
            down(Offset(size.width / 2f, size.height / 2f))
            up()
        }
        onNodeWithText("OK").performClick()
        // Red (hue 0, s=1, v=1) dragged to the bar's center (hue ~180) with saturation/value untouched.
        val expectedHue180 = cpColorToHex(cpHsvToColor(180f, 1f, 1f))
        assertEquals(
            expectedHue180,
            result,
            "confirming after a hue drag must report the new hue at full saturation/value",
        )
    }

    @Test
    fun `clicking a recent color then confirming reports exactly that color`() = runComposeUiTest {
        RecentColors.add("#00FF00")
        var result: String? = null
        setContent {
            MaterialTheme {
                ColorPickerDialog(initialHex = "#FF0000", onDismiss = {}, onColorSelected = { result = it })
            }
        }
        onNodeWithTag("recentColor_#00FF00").performClick()
        onNodeWithText("OK").performClick()
        assertEquals("#00FF00", result, "picking a recent color and confirming must report that exact color")
    }

    @Test
    fun `cancel reports no color`() = runComposeUiTest {
        var dismissed = false
        var selected: String? = null
        setContent {
            MaterialTheme {
                ColorPickerDialog(
                    initialHex = "#FF0000",
                    onDismiss = { dismissed = true },
                    onColorSelected = { selected = it },
                )
            }
        }
        onNodeWithText("Cancel").performClick()
        assertTrue(dismissed, "Cancel must dismiss the dialog")
        assertEquals(null, selected, "Cancel must not report a color")
    }

    private fun assertApprox(expected: Float, actual: Float, what: String, tol: Float = 0.02f) =
        assertTrue(abs(expected - actual) <= tol, "$what: expected ~$expected, was $actual")
}
