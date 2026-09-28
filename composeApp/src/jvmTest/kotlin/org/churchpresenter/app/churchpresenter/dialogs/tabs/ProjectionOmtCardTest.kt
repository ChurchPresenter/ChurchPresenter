@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.omt.OmtRuntimeStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val BUNDLED = OmtRuntimeStatus.Ready("/app/omt/libomt.dylib", bundled = true)

/**
 * The OMT outputs card of the Projection settings tab: what each control writes back to settings.
 *
 * Every control is clicked and the resulting settings asserted, for the reason `ProjectionNdiCardTest`
 * gives: a rendered composable counts as covered whether or not it is wired to the right field. The
 * status, the counts and the recheck are all pinned, so nothing loads a real library.
 */
class ProjectionOmtCardTest {

    private fun card(
        initial: AppSettings = AppSettings(),
        status: OmtRuntimeStatus = BUNDLED,
        receivers: Int = 0,
        address: String = "",
        identified: MutableList<Int> = mutableListOf(),
        rechecks: MutableList<Pair<String, String>> = CopyOnWriteArrayList(),
        body: ComposeUiTest.(read: () -> AppSettings) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        setContent {
            Surface {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    var state by remember { mutableStateOf(initial) }
                    OmtOutputsCard(
                        settings = state,
                        onSettingsChange = { transform ->
                            state = transform(state)
                            current = state
                        },
                        onIdentifyOmt = { identified += it },
                        status = status,
                        receiverCount = { receivers },
                        addressOf = { address },
                        recheck = { path, server -> rechecks += path to server },
                    )
                }
            }
        }
        waitForIdle()
        body { current }
    }

    private fun withOutputs(vararg outputs: ScreenAssignment = arrayOf(ScreenAssignment())) =
        AppSettings(projectionSettings = ProjectionSettings(omtOutputs = outputs.toList()))

    // ── The library ──────────────────────────────────────────────────────────────

    @Test
    fun `the bundled library says so and offers to add an output`() {
        card { _ ->
            onNodeWithText("OMT library included with ChurchPresenter").assertExists()
            onNodeWithText("Add Output").assertExists()
            onNodeWithText("Check again").assertDoesNotExist()
        }
    }

    @Test
    fun `a library from elsewhere names its path`() {
        card(status = OmtRuntimeStatus.Ready("/opt/omt/libomt.so", bundled = false)) { _ ->
            onNodeWithText("OMT library: /opt/omt/libomt.so").assertExists()
        }
    }

    @Test
    fun `a library that will not load names the path it tried, and offers nothing to configure`() {
        card(status = OmtRuntimeStatus.LoadFailed("/bad/libomt.dll")) { _ ->
            onNodeWithText("/bad/libomt.dll", substring = true).assertExists()
            onNodeWithText("Add Output").assertDoesNotExist()
        }
    }

    @Test
    fun `a Linux machine without Avahi is told to start it, and can check again`() {
        val rechecks = CopyOnWriteArrayList<Pair<String, String>>()
        card(status = OmtRuntimeStatus.DiscoveryServiceMissing, rechecks = rechecks) { _ ->
            onNodeWithText("avahi-daemon", substring = true).assertExists()
            onNodeWithText("Add Output").assertDoesNotExist()
            onNodeWithText("Check again").performClick()
            waitUntil(timeoutMillis = 2_000) { rechecks.isNotEmpty() }
        }
    }

    @Test
    fun `no library says what to do about it`() {
        card(status = OmtRuntimeStatus.NotInstalled) { _ ->
            onNodeWithText("OMT library not found").assertExists()
            onNodeWithText("libomt and libvmx", substring = true).assertExists()
        }
    }

    @Test
    fun `Check again loads from the configured folder with the configured server`() {
        val rechecks = CopyOnWriteArrayList<Pair<String, String>>()
        val configured = AppSettings(
            projectionSettings = ProjectionSettings(omtLibraryPath = "/opt/omt", omtDiscoveryServer = "omt://s:1"),
        )
        card(configured, status = OmtRuntimeStatus.NotInstalled, rechecks = rechecks) { _ ->
            onNodeWithText("Check again").performClick()
            waitUntil(timeoutMillis = 2_000) { rechecks.isNotEmpty() }
            assertEquals(listOf("/opt/omt" to "omt://s:1"), rechecks.toList())
        }
    }

    @Test
    fun `the folder is chosen rather than typed, and Use bundled clears it`() {
        val configured = AppSettings(projectionSettings = ProjectionSettings(omtLibraryPath = "/opt/omt"))
        card(configured) { read ->
            onNodeWithText("/opt/omt").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
            onNodeWithText("Choose Folder").assertExists()
            onNodeWithText("Use bundled").performClick()
            waitForIdle()
            assertEquals("", read().projectionSettings.omtLibraryPath)
            onNodeWithText("Use bundled").assertDoesNotExist()
        }
    }

    // ── The discovery server ─────────────────────────────────────────────────────

    @Test
    fun `the discovery server is committed by Apply, trimmed, and not before`() {
        card { read ->
            onNodeWithText("Apply").assertIsNotEnabled()
            onNodeWithText("omt://host:port").performTextInput(" omt://server:6400 ")
            waitForIdle()
            assertEquals("", read().projectionSettings.omtDiscoveryServer, "not until Apply")
            onNodeWithText("Apply").assertIsEnabled().performClick()
            waitForIdle()
            assertEquals("omt://server:6400", read().projectionSettings.omtDiscoveryServer)
            onNodeWithText("Apply").assertIsNotEnabled()
        }
    }

    @Test
    fun `the discovery help says a change waits for the next start`() {
        card { _ ->
            onNodeWithText("the same server. Takes effect the next time ChurchPresenter starts.", substring = true)
                .assertExists()
        }
    }

    // ── Adding and removing outputs ──────────────────────────────────────────────

    @Test
    fun `Add Output adds one`() {
        card { read ->
            onNodeWithText("Add Output").performClick()
            waitForIdle()
            assertEquals(1, read().projectionSettings.omtOutputs.size)
        }
    }

    @Test
    fun `Remove asks first, and the output survives a cancel`() {
        card(withOutputs()) { read ->
            onNodeWithText("Remove").performClick()
            waitForIdle()
            onNodeWithText("Are you sure you want to remove OMT Output 1?").assertExists()
            onNodeWithText("Cancel").performClick()
            waitForIdle()
            assertEquals(1, read().projectionSettings.omtOutputs.size)
        }
    }

    @Test
    fun `confirming Remove removes it`() {
        card(withOutputs()) { read ->
            onNodeWithText("Remove").performClick()
            waitForIdle()
            onAllNodesWithText("Remove")[1].performClick()
            waitForIdle()
            assertTrue(read().projectionSettings.omtOutputs.isEmpty())
        }
    }

    // ── The per-output controls ──────────────────────────────────────────────────

    @Test
    fun `the enable switch writes back`() {
        card(withOutputs()) { read ->
            onNodeWithText("Enabled").performClick()
            waitForIdle()
            assertFalse(read().projectionSettings.omtOutputs.single().omtEnabled)
        }
    }

    @Test
    fun `a typed name is committed by the output's own Apply, not per keystroke`() {
        card(withOutputs()) { read ->
            onNodeWithText("OMT Output 1").performTextInput("Lyrics")
            waitForIdle()
            assertEquals("", read().projectionSettings.omtOutputs.single().omtName, "not until Apply")
            // The discovery server's Apply comes first on the card; the output's is the second.
            onAllNodesWithText("Apply")[1].assertIsEnabled().performClick()
            waitForIdle()
            assertEquals("Lyrics", read().projectionSettings.omtOutputs.single().omtName)
        }
    }

    @Test
    fun `picking fill writes the stored mode, not the label`() {
        card(withOutputs()) { read ->
            onNodeWithText("Alpha (transparent)").performClick()
            waitForIdle()
            onNodeWithText("Fill only").performClick()
            waitForIdle()
            assertEquals(Constants.OMT_MODE_FILL, read().projectionSettings.omtOutputs.single().omtMode)
        }
    }

    @Test
    fun `picking a quality writes it back`() {
        card(withOutputs()) { read ->
            onNodeWithText("Automatic").performClick()
            waitForIdle()
            onNodeWithText("High").performClick()
            waitForIdle()
            assertEquals(Constants.OMT_QUALITY_HIGH, read().projectionSettings.omtOutputs.single().omtQuality)
        }
    }

    @Test
    fun `picking a resolution writes both dimensions`() {
        card(withOutputs()) { read ->
            onNodeWithText("1920×1080").performClick()
            waitForIdle()
            onNodeWithText("3840×2160", substring = true).performClick()
            waitForIdle()
            val output = read().projectionSettings.omtOutputs.single()
            assertEquals(3840, output.omtWidth)
            assertEquals(2160, output.omtHeight)
        }
    }

    @Test
    fun `picking a frame rate writes it back`() {
        card(withOutputs()) { read ->
            onNodeWithText("30").performClick()
            waitForIdle()
            onNodeWithText("60").performClick()
            waitForIdle()
            assertEquals(60, read().projectionSettings.omtOutputs.single().omtFps)
        }
    }

    @Test
    fun `Identify asks for this output, by index`() {
        val identified = mutableListOf<Int>()
        card(withOutputs(ScreenAssignment(), ScreenAssignment()), identified = identified) { _ ->
            onAllNodesWithText("Identify")[1].performClick()
            waitForIdle()
            assertEquals(listOf(1), identified)
        }
    }

    @Test
    fun `receivers and the network name are shown, and nobody watching says so`() {
        card(withOutputs(), receivers = 2, address = "HOST (Lyrics)") { _ ->
            onNodeWithText("2 receiving").assertExists()
            onNodeWithText("Receivers list this output as HOST (Lyrics)").assertExists()
        }
        card(withOutputs()) { _ ->
            onNodeWithText("No receivers").assertExists()
            onNodeWithText("Receivers list this output as", substring = true).assertDoesNotExist()
        }
    }
}
