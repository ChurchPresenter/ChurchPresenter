@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the Browser Source Outputs table — the virtual outputs served as a web page for OBS to
 * pull in.
 *
 * Unlike the screen assignments above it, these are not tied to detected hardware: they are added
 * and removed freely, so a row's controls shift position as soon as another row appears. They are
 * therefore addressed by what they display rather than by ordinal. The enabled switch and the API
 * key checkbox publish real toggle state, so those are asserted directly; the three dropdowns
 * display their stored value, so a display assertion after a pick also proves the round trip.
 */
class ProjectionSettingsTabBrowserSourceTest {

    /** A tab that already has [count] browser-source outputs, so rows can be driven straight away. */
    private fun withOutputs(count: Int): AppSettings = AppSettings().let {
        it.copy(
            projectionSettings = it.projectionSettings.copy(
                browserSourceOutputs = List(count) { ScreenAssignment() },
            ),
        )
    }

    private fun ComposeUiTest.enabledSwitch(): SemanticsNodeInteraction =
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))

    private fun ComposeUiTest.apiKeyCheckbox(): SemanticsNodeInteraction =
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))

    /** The browser-source row's dropdown showing [current] — the last one, below the screen grid. */
    private fun ComposeUiTest.rowDropdown(current: String): SemanticsNodeInteraction =
        onAllNodesWithText(current).onLast()

    private fun output(get: () -> AppSettings, index: Int = 0): ScreenAssignment =
        get().projectionSettings.browserSourceOutputs[index]

    // ── The row itself ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `an added output renders a full row of controls`() {
        projectionTab(initial = withOutputs(1)) { _ ->
            onNodeWithText("Browser Source 1").assertExists("the row must be named")
            onNodeWithText("Enabled").assertExists()
            onNodeWithText("Resolution").assertExists()
            onNodeWithText("Max FPS").assertExists()
            onNodeWithText("Require API Key").assertExists()
            onNodeWithText("Remove").assertExists()
            enabledSwitch().assertIsOn() // a new output starts enabled
            apiKeyCheckbox().assertIsOff() // and unprotected
            rowDropdown("1920×1080").assertExists("with a default resolution")
            rowDropdown("30").assertExists("and a default frame rate")
        }
    }

    @Test
    fun `each added output gets its own numbered row`() {
        projectionTab(initial = withOutputs(3)) { _ ->
            for (n in 1..3) onNodeWithText("Browser Source $n").assertExists()
            onAllNodesWithText("Remove").assertCountEquals(3)
            onAllNodesWithText("Require API Key").assertCountEquals(3)
        }
    }

    // ── Enabled switch and API key ──────────────────────────────────────────────────────────────

    @Test
    fun `the enabled switch turns an output off and on`() {
        projectionTab(initial = withOutputs(1)) { get ->
            assertEquals(true, output(get).browserSourceEnabled, "a new output starts enabled")

            enabledSwitch().performScrollTo().performClick()
            waitForIdle()
            assertEquals(false, output(get).browserSourceEnabled, "the switch must store the change")
            enabledSwitch().assertIsOff()

            enabledSwitch().performClick()
            waitForIdle()
            assertEquals(true, output(get).browserSourceEnabled, "and switch it back")
            enabledSwitch().assertIsOn()
        }
    }

    @Test
    fun `the API key checkbox protects an output`() {
        projectionTab(initial = withOutputs(1)) { get ->
            assertEquals(false, output(get).browserSourceApiKeyRequired, "unprotected out of the box")

            apiKeyCheckbox().performScrollTo().performClick()
            waitForIdle()

            assertEquals(true, output(get).browserSourceApiKeyRequired, "the checkbox must store the change")
            apiKeyCheckbox().assertIsOn()
        }
    }

    // ── Resolution and frame rate ───────────────────────────────────────────────────────────────

    @Test
    fun `the resolution dropdown offers every preset and stores the pick`() {
        projectionTab(initial = withOutputs(1)) { get ->
            assertEquals(1920, output(get).browserSourceWidth, "1080p out of the box")
            assertEquals(1080, output(get).browserSourceHeight)

            rowDropdown("1920×1080").performScrollTo().performClick()
            waitForIdle()
            for (preset in listOf("1280×720", "1920×1080", "2560×1440", "3840×2160")) {
                onAllNodesWithText(preset).assertCountEquals(if (preset == "1920×1080") 2 else 1)
            }
            onNodeWithText("2560×1440").performClick()
            waitForIdle()

            assertEquals(2560, output(get).browserSourceWidth, "the picked width must be stored")
            assertEquals(1440, output(get).browserSourceHeight, "with its height")
            rowDropdown("2560×1440").assertExists("and shown on the row")
        }
    }

    @Test
    fun `the frame rate dropdown offers every preset and stores the pick`() {
        projectionTab(initial = withOutputs(1)) { get ->
            assertEquals(30, output(get).browserSourceFps, "30fps out of the box")

            rowDropdown("30").performScrollTo().performClick()
            waitForIdle()
            for (preset in listOf("10", "15", "24", "60")) {
                onAllNodesWithText(preset).assertCountEquals(1)
            }
            onNodeWithText("60").performClick()
            waitForIdle()

            assertEquals(60, output(get).browserSourceFps, "the picked frame rate must be stored")
            rowDropdown("60").assertExists("and shown on the row")
        }
    }

    @Test
    fun `each output keeps its own resolution`() {
        projectionTab(initial = withOutputs(2)) { get ->
            // Both rows read 1920×1080; the first is the one above.
            onAllNodesWithText("1920×1080")[0].performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("1280×720").performClick()
            waitForIdle()

            assertEquals(1280, output(get, 0).browserSourceWidth, "the first output must change")
            assertEquals(1920, output(get, 1).browserSourceWidth, "the second must be untouched")
        }
    }

    // ── Display mode ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a browser source has its own display mode`() {
        projectionTab(initial = withOutputs(1)) { get ->
            assertEquals(
                Constants.DISPLAY_MODE_FULLSCREEN,
                output(get).displayMode,
                "a new output is full screen",
            )
            // Three dropdowns read "Full Screen": the two screen rows, then this one.
            rowDropdown("Full Screen").performScrollTo().performClick()
            waitForIdle()
            onAllNodesWithText("Horizontal Lower Third").onLast().performClick()
            waitForIdle()

            assertEquals(
                Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                output(get).displayMode,
                "the picked mode must be stored",
            )
            assertTrue(output(get).isLowerThird, "and reported as a lower third")
            assertEquals(
                Constants.DISPLAY_MODE_FULLSCREEN,
                get().projectionSettings.screenAssignments[0].displayMode,
                "the physical screens must be untouched",
            )
        }
    }

    // ── Content outputs ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a browser source has its own content outputs`() {
        projectionTab(initial = withOutputs(1)) { get ->
            // Three summary buttons read the same; the browser source's is the last.
            onAllNodesWithText("16 of 17 enabled").onLast().performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Content Outputs — Browser Source 1")
                .assertExists("the dialog must name the browser source")

            onNode(hasClickAction() and hasTextExactly("Media")).performClick()
            waitForIdle()
            assertEquals(false, output(get).showMedia, "the toggle must store against the browser source")
            assertEquals(
                true,
                get().projectionSettings.screenAssignments[0].showMedia,
                "and leave the physical screens alone",
            )

            onNodeWithText("Done").performClick()
            waitForIdle()
            onAllNodesWithText("15 of 17 enabled").assertCountEquals(1)
        }
    }

    /**
     * When an output is protected and the server has a key configured, the overlay URL the operator
     * copies carries that key — otherwise a Browser Source pointed at it would be refused.
     */
    @Test
    fun `a protected output with a server key renders its row`() {
        val protectedOutput = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    browserSourceOutputs = listOf(ScreenAssignment(browserSourceApiKeyRequired = true)),
                ),
                serverSettings = it.serverSettings.copy(apiKey = "s3cret"),
            )
        }
        projectionTab(initial = protectedOutput) { get ->
            apiKeyCheckbox().assertIsOn() // the stored protection is shown
            assertEquals(true, output(get).browserSourceApiKeyRequired)
            assertEquals("s3cret", get().serverSettings.apiKey, "and the server key is what gets attached")
            onNodeWithText("Browser Source 1").assertExists()
        }
    }

    /** The same output with no server key configured: nothing to attach, row still renders. */
    @Test
    fun `a protected output without a server key still renders`() {
        val noKey = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    browserSourceOutputs = listOf(ScreenAssignment(browserSourceApiKeyRequired = true)),
                ),
            )
        }
        projectionTab(initial = noKey) { get ->
            apiKeyCheckbox().assertIsOn()
            assertEquals("", get().serverSettings.apiKey, "no key configured on the server")
            onNodeWithText("Browser Source 1").assertExists()
        }
    }

    // ── Removing ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Remove asks before deleting an output`() {
        projectionTab(initial = withOutputs(1)) { get ->
            onNodeWithText("Remove").performScrollTo().performClick()
            waitForIdle()

            onNodeWithText("Are you sure you want to remove Browser Source 1?")
                .assertExists("the confirmation must name what is being removed")
            assertEquals(1, get().projectionSettings.browserSourceOutputs.size, "nothing removed yet")
        }
    }

    @Test
    fun `cancelling the confirmation keeps the output`() {
        projectionTab(initial = withOutputs(1)) { get ->
            onNodeWithText("Remove").performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Cancel").performClick()
            waitForIdle()

            assertEquals(1, get().projectionSettings.browserSourceOutputs.size, "Cancel must keep it")
            onNodeWithText("Browser Source 1").assertExists()
            onAllNodesWithText("Are you sure you want to remove Browser Source 1?").assertCountEquals(0)
        }
    }

    @Test
    fun `confirming the removal deletes the output`() {
        projectionTab(initial = withOutputs(2)) { get ->
            onAllNodesWithText("Remove")[0].performScrollTo().performClick()
            waitForIdle()
            // The dialog's own Remove is the one added last.
            onAllNodesWithText("Remove").onLast().performClick()
            waitForIdle()

            assertEquals(1, get().projectionSettings.browserSourceOutputs.size, "one must be gone")
            onAllNodesWithText("Browser Source 2").assertCountEquals(0)
            onNodeWithText("Browser Source 1").assertExists("and the remaining one renumbers")
        }
    }

    // ── Identify ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Identify reports which browser source to flash`() = runComposeUiTest {
        var identified = mutableListOf<Int>()
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(AppSettings().let {
                    it.copy(projectionSettings = it.projectionSettings.copy(
                        browserSourceOutputs = listOf(ScreenAssignment(), ScreenAssignment()),
                    ))
                }) }
                ProjectionSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state) },
                    companionServer = CompanionServer(),
                    onIdentifyBrowserSource = { identified.add(it) },
                    detectScreens = { twoExternalScreens() },
                )
            }
        }
        // The screen grid's Identify comes first; the two browser-source ones follow.
        onAllNodesWithText("Identify")[1].performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithText("Identify")[2].performScrollTo().performClick()
        waitForIdle()

        assertEquals(listOf(0, 1), identified, "each button must identify its own output")
    }

    @Test
    fun `a disabled output still renders its controls`() {
        val disabled = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    browserSourceOutputs = listOf(ScreenAssignment(browserSourceEnabled = false)),
                ),
            )
        }
        projectionTab(initial = disabled) { _ ->
            enabledSwitch().assertIsOff() // the stored state must be shown
            onNodeWithText("Remove").assertExists("and the row stays configurable")
            rowDropdown("1920×1080").assertExists()
        }
    }
}
