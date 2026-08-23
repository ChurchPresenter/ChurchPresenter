package org.churchpresenter.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The custom seek-bar slider used for media/volume/timing controls: a bare `Box` driving its own
 * tap/drag gesture detectors and a `Canvas` track — no [androidx.compose.material3.Slider], and no
 * semantics of its own. Every test here locates the track through a `testTag` on the [modifier]
 * parameter (applied to the outer `Row`) rather than through semantics, and omits [trailingLabel]
 * in position-sensitive tests so that `Row`'s bounds equal the track `Box`'s bounds — the same
 * constraint `SourcePropertiesPanelTestSupport`'s position-based slider helpers document.
 *
 * The track/fill/handle drawn on the `Canvas`, and the hover-driven handle fade-in, have no
 * semantics trace at all, so they can't be asserted on directly; the hover test instead confirms
 * that engaging hover doesn't disrupt a subsequent tap, which is the one externally observable
 * consequence of that code path running without throwing.
 */
@OptIn(ExperimentalTestApi::class)
class SlimSliderTest {

    @Test
    fun `tapping the track seeks to that position and reports the change as finished`() = runComposeUiTest {
        var value: Float? = null
        var finished = false
        setContent {
            MaterialTheme {
                SlimSlider(
                    value = 0f,
                    onValueChange = { value = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.testTag("slider").width(200.dp),
                    onValueChangeFinished = { finished = true },
                )
            }
        }
        onNodeWithTag("slider").performTouchInput { click(Offset(100f, 10f)) }
        assertEquals(50f, value, "tapping the midpoint of a 0..100 track must report the midpoint value")
        assertTrue(finished, "a tap must report the change as finished")
    }

    @Test
    fun `tapping without an onValueChangeFinished callback does not crash`() = runComposeUiTest {
        var value: Float? = null
        setContent {
            MaterialTheme {
                SlimSlider(
                    value = 0f,
                    onValueChange = { value = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.testTag("slider").width(200.dp),
                )
            }
        }
        onNodeWithTag("slider").performTouchInput { click(Offset(100f, 10f)) }
        assertEquals(50f, value, "the tap must still report the value even with no finished callback")
    }

    @Test
    fun `dragging across the track seeks continuously and reports the change as finished on release`() =
        runComposeUiTest {
            var value: Float? = null
            var finished = false
            setContent {
                MaterialTheme {
                    SlimSlider(
                        value = 0f,
                        onValueChange = { value = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.testTag("slider").width(200.dp),
                        onValueChangeFinished = { finished = true },
                    )
                }
            }
            onNodeWithTag("slider").performTouchInput {
                down(Offset(20f, 10f))
                moveTo(Offset(150f, 10f))
                up()
            }
            assertEquals(75f, value, "dragging to 150/200 of the track must report 75% of the range")
            assertTrue(finished, "releasing a drag must report the change as finished")
        }

    @Test
    fun `dragging without an onValueChangeFinished callback does not crash`() = runComposeUiTest {
        var value: Float? = null
        setContent {
            MaterialTheme {
                SlimSlider(
                    value = 0f,
                    onValueChange = { value = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.testTag("slider").width(200.dp),
                )
            }
        }
        onNodeWithTag("slider").performTouchInput {
            down(Offset(20f, 10f))
            moveTo(Offset(150f, 10f))
            up()
        }
        assertEquals(75f, value, "the drag must still report the value even with no finished callback")
    }

    @Test
    fun `dragging past the track's edge clamps the value to the range's bound`() = runComposeUiTest {
        var value: Float? = null
        setContent {
            MaterialTheme {
                SlimSlider(
                    value = 0f,
                    onValueChange = { value = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.testTag("slider").width(200.dp),
                )
            }
        }
        onNodeWithTag("slider").performTouchInput {
            down(Offset(20f, 10f))
            moveTo(Offset(500f, 10f))
            up()
        }
        assertEquals(100f, value, "dragging past the right edge must clamp to the range's maximum")
    }

    @Test
    fun `canceling a drag does not report the change as finished`() = runComposeUiTest {
        var finished = false
        setContent {
            MaterialTheme {
                SlimSlider(
                    value = 0f,
                    onValueChange = { },
                    valueRange = 0f..100f,
                    modifier = Modifier.testTag("slider").width(200.dp),
                    onValueChangeFinished = { finished = true },
                )
            }
        }
        onNodeWithTag("slider").performTouchInput {
            down(Offset(20f, 10f))
            moveTo(Offset(80f, 10f))
            cancel()
        }
        assertFalse(finished, "a canceled drag must not be reported as a finished change, unlike a released one")
    }

    @Test
    fun `a disabled slider ignores taps`() = runComposeUiTest {
        var value: Float? = null
        setContent {
            MaterialTheme {
                SlimSlider(
                    value = 0f,
                    onValueChange = { value = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.testTag("slider").width(200.dp),
                    enabled = false,
                )
            }
        }
        onNodeWithTag("slider").performTouchInput { click(Offset(100f, 10f)) }
        assertNull(value, "a disabled slider must not report a value change from a tap")
    }

    @Test
    fun `a disabled slider ignores drags`() = runComposeUiTest {
        var value: Float? = null
        setContent {
            MaterialTheme {
                SlimSlider(
                    value = 0f,
                    onValueChange = { value = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.testTag("slider").width(200.dp),
                    enabled = false,
                )
            }
        }
        onNodeWithTag("slider").performTouchInput {
            down(Offset(20f, 10f))
            moveTo(Offset(150f, 10f))
            up()
        }
        assertNull(value, "a disabled slider must not report a value change from a drag")
    }

    @Test
    fun `a valueRange with equal start and end does not crash`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SlimSlider(
                    value = 5f,
                    onValueChange = { },
                    valueRange = 5f..5f,
                    modifier = Modifier.testTag("slider").width(200.dp),
                )
            }
        }
        onNodeWithTag("slider").assertExists("a zero-width range must still render, not crash on the division")
    }

    @Test
    fun `the trailing label is shown when provided`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SlimSlider(
                    value = 50f,
                    onValueChange = { },
                    valueRange = 0f..100f,
                    trailingLabel = "50%",
                )
            }
        }
        onNodeWithText("50%").assertExists("the trailing label must be shown when supplied")
    }

    @Test
    fun `the trailing label is omitted by default`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SlimSlider(value = 50f, onValueChange = { }, valueRange = 0f..100f)
            }
        }
        onNodeWithText("50%", substring = true).assertDoesNotExist()
    }

    @Test
    fun `hovering the track does not disrupt a subsequent tap`() = runComposeUiTest {
        var value: Float? = null
        setContent {
            MaterialTheme {
                SlimSlider(
                    value = 0f,
                    onValueChange = { value = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.testTag("slider").width(200.dp),
                )
            }
        }
        onNodeWithTag("slider").performMouseInput { moveTo(Offset(100f, 10f)) }
        waitForIdle()
        onNodeWithTag("slider").performTouchInput { click(Offset(100f, 10f)) }
        assertEquals(50f, value, "the hover-driven handle animation must not interfere with a following tap")
    }
}
