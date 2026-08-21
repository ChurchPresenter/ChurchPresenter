@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Screen Capture source: a region of the screen, or a named window, refreshed on an interval.
 *
 * Choosing between those two modes rebuilds the panel — Region offers four coordinate fields, Window
 * offers a Refresh button and a list of what is open. Everything except that list is asserted here.
 *
 * **Why the OS is faked.** The window list comes from `listOpenWindows()`, which shells out per
 * platform: `xprop` then `wmctrl` on Linux, a native call on Windows, and on macOS an `osascript`
 * against System Events — which can raise an accessibility prompt that blocks the run outright, and
 * costs a process spawn even when it does not. What it returns is also whatever happens to be open on
 * the machine, which no fixture can pin. Every test here therefore runs under [withOsName] naming an
 * OS the function has no enumerator for, so it returns an empty list immediately, having spawned
 * nothing. That is a faithful state — it is what an operator sees on a machine where the listing
 * tools are missing — and it is what makes the mode switch, the Refresh button and the empty-list
 * path testable at all.
 *
 * Known gap: the window dropdown itself, and the `"0x%x"` window-id encoding behind it, are only
 * reached when the listing returns something. Nothing in a unit test can make it do so without
 * driving the real platform tools, so they are not covered.
 */
class SourcePropertiesScreenCaptureTest {

    /** Ordinals of the region fields — the header owns the first six. */
    private object Field {
        const val X = 6
        const val Y = 7
        const val WIDTH = 8
        const val HEIGHT = 9
        const val INTERVAL = 10
    }

    /** Renders the capture panel with the platform's window enumeration deliberately absent. */
    private fun capturePanel(
        source: SceneSource.ScreenCaptureSource = Fixture.capture(),
        block: ComposeUiTest.(get: () -> SceneSource) -> Unit,
    ) = withOsName(OS_WITHOUT_ENUMERATOR) { sourcePanel(source, block = block) }

    private fun windowMode() = Fixture.capture().copy(captureMode = "window")

    // ── What the panel displays ───────────────────────────────────────────────

    @Test
    fun `the section is headed and every region control captioned`() = capturePanel { _ ->
        listOf("CAPTURE MODE", "CAPTURE X", "CAPTURE Y", "CAPTURE WIDTH", "CAPTURE HEIGHT", "Capture Interval")
            .forEach { caption ->
                onNodeWithText(caption).assertExists("\"$caption\" must caption a control on the capture panel")
            }
        onNodeWithText(Label.SCREEN_CAPTURE).assertIsDisplayed()
    }

    @Test
    fun `region mode adds five fields and no checkbox to the header`() = capturePanel { _ ->
        // Four coordinates plus the interval input beside its slider.
        textFields().assertCountEquals(11)
        checkboxes().assertCountEquals(0)
    }

    @Test
    fun `every stored value is shown by the field that owns it`() {
        val region = Fixture.capture().copy(
            captureX = 100, captureY = 200, captureWidth = 800, captureHeight = 600, captureInterval = 250,
        )
        capturePanel(region) { _ ->
            assertFieldShows("100", "the capture X field")
            assertFieldShows("200", "the capture Y field")
            assertFieldShows("800", "the capture width field")
            assertFieldShows("600", "the capture height field")
            assertFieldShows("250", "the capture interval input")
        }
        }

    @Test
    fun `the interval input is suffixed with its unit`() = capturePanel { _ ->
        onNodeWithText("ms").assertExists("an operator must see what the interval number means")
    }

    // ── Capture mode ──────────────────────────────────────────────────────────

    @Test
    fun `the mode dropdown names the stored mode`() {
        listOf("region" to "Screen Region", "window" to "Window").forEach { (stored, shown) ->
            capturePanel(Fixture.capture().copy(captureMode = stored)) { _ ->
                onNodeWithText(shown).assertExists("$stored must read as \"$shown\"")
            }
        }
    }

    @Test
    fun `a mode this build does not know reads as Screen Region`() {
        capturePanel(Fixture.capture().copy(captureMode = "display")) { _ ->
            onNodeWithText("Screen Region").assertExists("an unrecognised mode must name a real option")
            assertEquals(0, countOf("display"), "and must not show itself")
        }
    }

    @Test
    fun `the mode dropdown offers both modes`() = capturePanel { _ ->
        openDropdown(showing = "Screen Region")

        assertEquals(2, countOf("Screen Region"), "the current mode is both selector and menu entry")
        assertEquals(1, countOf("Window"), "and the other mode is offered")
    }

    @Test
    fun `choosing Window stores it and swaps the coordinates for a window picker`() = capturePanel { get ->
        chooseFromDropdown(showing = "Screen Region", option = "Window")

        assertEquals("window", (get() as SceneSource.ScreenCaptureSource).captureMode)
        listOf("CAPTURE X", "CAPTURE Y", "CAPTURE WIDTH", "CAPTURE HEIGHT").forEach { caption ->
            assertEquals(0, countOf(caption), "\"$caption\" belongs to region capture")
        }
        onNodeWithText("Refresh Window List").assertExists("and the window picker takes their place")
    }

    @Test
    fun `choosing Screen Region brings the coordinates back`() {
        capturePanel(windowMode()) { get ->
            chooseFromDropdown(showing = "Window", option = "Screen Region")

            assertEquals("region", (get() as SceneSource.ScreenCaptureSource).captureMode)
            onNodeWithText("CAPTURE X").assertExists()
            assertEquals(0, countOf("Refresh Window List"), "the window picker goes with it")
        }
    }

    // ── Window mode ───────────────────────────────────────────────────────────

    @Test
    fun `window mode offers a refresh button and no coordinate fields`() = capturePanel(windowMode()) { _ ->
        onNodeWithText("Refresh Window List").assertExists()
        // The header's six, plus the interval input; no coordinates.
        textFields().assertCountEquals(7)
    }

    @Test
    fun `the refresh button can be pressed when the platform lists no windows`() {
        // With no enumerator the list stays empty, so this proves the button survives finding nothing
        // rather than that it found something.
        capturePanel(windowMode()) { get ->
            val before = get()
            onNodeWithText("Refresh Window List").performScrollTo().assertHasClickAction().performClick()
            waitForIdle()

            onNodeWithText("Refresh Window List").assertExists("the button must still be there afterwards")
            assertEquals(before, get(), "refreshing the list must not change the source")
        }
    }

    @Test
    fun `no window dropdown is offered when the platform lists nothing`() = capturePanel(windowMode()) { _ ->
        assertEquals(0, countOf("WINDOW"), "an empty list must not leave an empty dropdown behind")
    }

    // ── Region coordinates ────────────────────────────────────────────────────

    @Test
    fun `typing a capture X stores it`() = capturePanel { get ->
        typeField(Field.X, "640")

        assertEquals(640, (get() as SceneSource.ScreenCaptureSource).captureX)
        assertFieldShows("640", "the capture X field")
    }

    @Test
    fun `a negative capture X is raised to the screen's edge`() = capturePanel { get ->
        typeField(Field.X, "-50")

        assertEquals(0, (get() as SceneSource.ScreenCaptureSource).captureX, "capture cannot start off-screen")
    }

    @Test
    fun `typing a capture Y stores it`() = capturePanel { get ->
        typeField(Field.Y, "360")

        assertEquals(360, (get() as SceneSource.ScreenCaptureSource).captureY)
    }

    @Test
    fun `a negative capture Y is raised to the screen's edge`() = capturePanel { get ->
        typeField(Field.Y, "-1")

        assertEquals(0, (get() as SceneSource.ScreenCaptureSource).captureY)
    }

    @Test
    fun `typing a capture width stores it`() = capturePanel { get ->
        typeField(Field.WIDTH, "1280")

        assertEquals(1280, (get() as SceneSource.ScreenCaptureSource).captureWidth)
    }

    @Test
    fun `a capture width of zero is raised to one pixel`() = capturePanel { get ->
        typeField(Field.WIDTH, "0")

        assertEquals(1, (get() as SceneSource.ScreenCaptureSource).captureWidth, "a capture must have area")
    }

    @Test
    fun `typing a capture height stores it`() = capturePanel { get ->
        typeField(Field.HEIGHT, "720")

        assertEquals(720, (get() as SceneSource.ScreenCaptureSource).captureHeight)
    }

    @Test
    fun `a capture height of zero is raised to one pixel`() = capturePanel { get ->
        typeField(Field.HEIGHT, "0")

        assertEquals(1, (get() as SceneSource.ScreenCaptureSource).captureHeight)
    }

    @Test
    fun `text that is not a number leaves a coordinate alone`() = capturePanel { get ->
        typeField(Field.WIDTH, "half")

        assertEquals(1920, (get() as SceneSource.ScreenCaptureSource).captureWidth, "the stored width is untouched")
    }

    // ── Capture interval ──────────────────────────────────────────────────────

    @Test
    fun `committing an interval stores it`() = capturePanel { get ->
        commitField(Field.INTERVAL, "500")

        assertEquals(500, (get() as SceneSource.ScreenCaptureSource).captureInterval)
        assertFieldShows("500", "the interval input after committing")
    }

    @Test
    fun `an interval below the minimum is raised to it`() = capturePanel { get ->
        commitField(Field.INTERVAL, "1")

        assertEquals(
            33, (get() as SceneSource.ScreenCaptureSource).captureInterval,
            "the fastest capture is 33ms — about 30fps",
        )
    }

    @Test
    fun `an interval above the maximum is lowered to it`() = capturePanel { get ->
        commitField(Field.INTERVAL, "5000")

        assertEquals(1000, (get() as SceneSource.ScreenCaptureSource).captureInterval, "the slowest is one second")
    }

    @Test
    fun `text that is not a number leaves the interval alone`() = capturePanel { get ->
        commitField(Field.INTERVAL, "slowly")

        assertEquals(100, (get() as SceneSource.ScreenCaptureSource).captureInterval)
        assertFieldShows("100", "the interval input after rejecting the typed text")
    }

    @Test
    fun `dragging the interval slider to its far end is the slowest capture`() = capturePanel { get ->
        tapSliderUnder("Capture Interval", fraction = 1f, gapDp = Gap.INPUT)

        assertEquals(1000, (get() as SceneSource.ScreenCaptureSource).captureInterval)
        assertFieldShows("1000", "the input beside the slider follows it")
    }

    @Test
    fun `dragging the interval slider to its near end is the fastest capture`() {
        capturePanel(Fixture.capture().copy(captureInterval = 800)) { get ->
            tapSliderUnder("Capture Interval", fraction = 0f, gapDp = Gap.INPUT)

            assertEquals(33, (get() as SceneSource.ScreenCaptureSource).captureInterval)
        }
    }

    @Test
    fun `the interval slider is offered in window mode too`() = capturePanel(windowMode()) { get ->
        tapSliderUnder("Capture Interval", fraction = 1f, gapDp = Gap.INPUT)

        assertEquals(
            1000, (get() as SceneSource.ScreenCaptureSource).captureInterval,
            "the interval applies to both capture modes",
        )
    }
}
