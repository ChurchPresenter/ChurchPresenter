@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the Browser Source Outputs table -- the virtual outputs served as a web page for OBS to
 * pull in.
 *
 * Unlike the screen assignments above it, these are not tied to detected hardware: they are added
 * and removed freely, so a row's controls shift position as soon as another row appears. They are
 * therefore addressed by what they display rather than by ordinal. The enabled switch and the API
 * key checkbox publish real toggle state, so those are asserted directly; the dropdowns display
 * their stored value, so a display assertion after a pick also proves the round trip.
 *
 * Ported from `ProjectionSettingsTabBrowserSourceTest`. What each output *shows* is a profile now,
 * so the row's content-outputs button is a profile picker.
 */
class ProjectionSettingsTabBrowserSourceTest {

    /** A tab that already has [count] browser-source outputs, so rows can be driven straight away. */
    private fun withOutputs(count: Int): AppSettings = withProfiles().let {
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

    /** The browser-source row's dropdown showing [current] -- the last one, below the screen grid. */
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

    // ── Naming an output ────────────────────────────────────────────────────────────────────────

    @Test
    fun `an output can be given a name`() {
        projectionTab(initial = withOutputs(1)) { get ->
            assertEquals("", output(get).browserSourceName, "a new output is unnamed")

            onNodeWithText("Browser Source 1").performScrollTo().performTextInput("Choir")
            waitForIdle()

            assertEquals("Choir", output(get).browserSourceName, "the typed name must be stored")
            onNodeWithText("Choir").assertExists("and shown on the row")
        }
    }

    @Test
    fun `each output keeps its own name`() {
        projectionTab(initial = withOutputs(2)) { get ->
            onNodeWithText("Browser Source 2").performScrollTo().performTextInput("Chords")
            waitForIdle()

            assertEquals("", output(get, 0).browserSourceName, "the first output must be untouched")
            assertEquals("Chords", output(get, 1).browserSourceName, "the second must take the name")
            onNodeWithText("Browser Source 1").assertExists("and the unnamed one keeps its number")
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
            // Menu rows read "1920×1080  16:9" -- the shape is spelled out beside the numbers, so
            // these are substring matches. The row button itself still reads the numbers alone,
            // which is why 1920×1080 is found twice: once on the row, once in the open menu.
            for (preset in listOf("1280×720", "1920×1080", "2560×1440", "3840×2160")) {
                onAllNodesWithText(preset, substring = true)
                    .assertCountEquals(if (preset == "1920×1080") 2 else 1)
            }
            // A 4:3 and an ultrawide are on offer too -- the list used to be 16:9 only, so an
            // operator could not stand a Browser Source in for the shape it was feeding.
            onAllNodesWithText("1024×768", substring = true).assertCountEquals(1)
            onAllNodesWithText("2560×1080", substring = true).assertCountEquals(1)
            onNodeWithText("2560×1440", substring = true).performClick()
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
            onNodeWithText("1280×720", substring = true).performClick()
            waitForIdle()

            assertEquals(1280, output(get, 0).browserSourceWidth, "the first output must change")
            assertEquals(1920, output(get, 1).browserSourceWidth, "the second must be untouched")
        }
    }

    // ── Removing ────────────────────────────────────────────────────────────────────────────────

    /** Remove asks first: an output is a thing an operator has wired OBS up to. */
    @Test
    fun `Remove asks before taking an output away`() {
        projectionTab(initial = withOutputs(2)) { get ->
            onAllNodesWithText("Remove")[0].performScrollTo().performClick()
            waitForIdle()
            assertEquals(2, get().projectionSettings.browserSourceOutputs.size, "nothing yet")

            // Two "Remove" nodes are on screen now -- the rows' buttons and the dialog's. The
            // dialog's is the last, being drawn in a popup above them.
            onAllNodesWithText("Remove").onLast().performClick()
            waitForIdle()

            assertEquals(1, get().projectionSettings.browserSourceOutputs.size)
            // The rows renumber, so the second one is gone by name as well as by count.
            onNodeWithText("Browser Source 2").assertDoesNotExist()
        }
    }

    @Test
    fun `Remove can be backed out of`() {
        projectionTab(initial = withOutputs(2)) { get ->
            onAllNodesWithText("Remove")[0].performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Cancel").performClick()
            waitForIdle()

            assertEquals(2, get().projectionSettings.browserSourceOutputs.size, "both are still there")
        }
    }

    // ── What an output shows, which is a profile now ────────────────────────────────────────────

    @Test
    fun `a browser source takes a profile like any other output`() {
        projectionTab(initial = withOutputs(1)) { get ->
            onAllNodesWithText("None").onLast().performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Foyer").performClick()
            waitForIdle()

            assertEquals("p1", output(get).activeProfileId)
        }
    }
}
