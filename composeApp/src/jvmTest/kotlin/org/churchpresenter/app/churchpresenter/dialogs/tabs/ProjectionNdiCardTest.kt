@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val READY = NdiRuntimeStatus.Ready("6.1.1", "/usr/local/lib/libndi.dylib")

/**
 * The NDI outputs card of the Projection settings tab: what each control writes back to settings.
 *
 * Rendering a composable is enough for JaCoCo to call it covered, which is exactly the trap this
 * suite exists to avoid — every control here is clicked and the resulting settings asserted, so a
 * dropdown wired to the wrong field fails rather than passes.
 */
class ProjectionNdiCardTest {

    /**
     * Renders the card over mutable settings and hands [body] the test, plus a way to read what the
     * card wrote. The status is pinned, so nothing depends on whether this machine has NDI.
     */
    private fun card(
        initial: AppSettings = AppSettings(),
        status: NdiRuntimeStatus = READY,
        receivers: Int = 0,
        identified: MutableList<Int> = mutableListOf(),
        body: ComposeUiTest.(read: () -> AppSettings) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        setContent {
            Surface {
                Box(Modifier.fillMaxSize()) {
                    var state by remember { mutableStateOf(initial) }
                    NdiOutputsCard(
                        settings = state,
                        onSettingsChange = { transform ->
                            state = transform(state)
                            current = state
                        },
                        contentGroup = emptyList(),
                        backgroundGroup = emptyList(),
                        displayModes = listOf("Full screen" to Constants.DISPLAY_MODE_FULLSCREEN),
                        songLangModes = emptyList(),
                        translationDisplays = emptyList(),
                        translationNames = emptyList(),
                        status = status,
                        receiverCount = { receivers },
                        onIdentifyNdi = { identified += it },
                    )
                }
            }
        }
        waitForIdle()
        body { current }
    }

    private fun oneOutput(output: ScreenAssignment = ScreenAssignment()) =
        AppSettings(projectionSettings = ProjectionSettings(ndiOutputs = listOf(output)))

    // ── The runtime states ──────────────────────────────────────────────────────

    @Test
    fun `with no runtime there is nothing to configure and a way to get one`() {
        card(status = NdiRuntimeStatus.NotInstalled) { _ ->
            onNodeWithText("NDI Runtime not installed").assertExists()
            onNodeWithText("Get the NDI Runtime").assertExists()
            onAllNodesWithText("Add Output").fetchSemanticsNodes().let {
                assertTrue(it.isEmpty(), "there is no point adding an output with no runtime to send it")
            }
        }
    }

    @Test
    fun `a ready runtime shows its version and offers to add an output`() {
        card { _ ->
            onNodeWithText("NDI Runtime 6.1.1").assertExists()
            onNodeWithText("Add Output").assertExists()
        }
    }

    @Test
    fun `a runtime that will not load names the path it tried`() {
        card(status = NdiRuntimeStatus.LoadFailed("/opt/ndi/libndi.so.6")) { _ ->
            onAllNodesWithText("/opt/ndi/libndi.so.6", substring = true).fetchSemanticsNodes().let {
                assertTrue(it.isNotEmpty(), "the operator needs to know which file failed")
            }
        }
    }

    @Test
    fun `an unsupported processor says so rather than reading as not installed`() {
        card(status = NdiRuntimeStatus.UnsupportedCpu) { _ ->
            onAllNodesWithText("SSE4.2", substring = true).fetchSemanticsNodes().let {
                assertTrue(it.isNotEmpty())
            }
        }
    }

    @Test
    fun `the trademark line is shown whether or not a runtime is installed`() {
        // It is a licence term, not a status message.
        for (status in listOf(READY, NdiRuntimeStatus.NotInstalled)) {
            card(status = status) { _ ->
                onAllNodesWithText("NDI®", substring = true).fetchSemanticsNodes().let {
                    assertTrue(it.isNotEmpty(), "missing for $status")
                }
            }
        }
    }

    // ── Adding and removing outputs ─────────────────────────────────────────────

    @Test
    fun `Add Output adds one`() {
        card { read ->
            onNodeWithText("Add Output").performClick()
            waitForIdle()
            assertEquals(1, read().projectionSettings.ndiOutputs.size)
        }
    }

    @Test
    fun `Remove asks first, and the output survives a cancel`() {
        card(oneOutput()) { read ->
            onNodeWithText("Remove").performClick()
            waitForIdle()
            onNodeWithText("Cancel").performClick()
            waitForIdle()
            assertEquals(1, read().projectionSettings.ndiOutputs.size)
        }
    }

    @Test
    fun `confirming Remove removes it`() {
        card(oneOutput()) { read ->
            onNodeWithText("Remove").performClick()
            waitForIdle()
            // The dialog's own Remove is the second node with that label.
            onAllNodesWithText("Remove")[1].performClick()
            waitForIdle()
            assertTrue(read().projectionSettings.ndiOutputs.isEmpty())
        }
    }

    // ── The per-output controls ─────────────────────────────────────────────────

    @Test
    fun `the enable switch writes back`() {
        card(oneOutput()) { read ->
            onNodeWithText("Enabled").performClick()
            waitForIdle()
            assertFalse(read().projectionSettings.ndiOutputs.single().ndiEnabled)
        }
    }

    @Test
    fun `typing a name writes it back`() {
        card(oneOutput()) { read ->
            onNodeWithText("NDI Output 1").performTextInput("Lyrics")
            waitForIdle()
            assertEquals("Lyrics", read().projectionSettings.ndiOutputs.single().ndiName)
        }
    }

    @Test
    fun `picking fill and key writes the stored mode, not the label`() {
        card(oneOutput()) { read ->
            onNodeWithText("Alpha (transparent)").performClick()
            waitForIdle()
            onNodeWithText("Fill + Key").performClick()
            waitForIdle()
            assertEquals(Constants.NDI_MODE_FILL_AND_KEY, read().projectionSettings.ndiOutputs.single().ndiMode)
        }
    }

    @Test
    fun `picking fill writes the fill mode`() {
        card(oneOutput()) { read ->
            onNodeWithText("Alpha (transparent)").performClick()
            waitForIdle()
            onNodeWithText("Fill only").performClick()
            waitForIdle()
            assertEquals(Constants.NDI_MODE_FILL, read().projectionSettings.ndiOutputs.single().ndiMode)
        }
    }

    @Test
    fun `picking a resolution writes both dimensions`() {
        card(oneOutput()) { read ->
            onNodeWithText("1920×1080").performClick()
            waitForIdle()
            onNodeWithText("3840×2160").performClick()
            waitForIdle()
            val output = read().projectionSettings.ndiOutputs.single()
            assertEquals(3840, output.ndiWidth)
            assertEquals(2160, output.ndiHeight)
        }
    }

    @Test
    fun `picking a frame rate writes it back`() {
        card(oneOutput()) { read ->
            onNodeWithText("30").performClick()
            waitForIdle()
            onNodeWithText("60").performClick()
            waitForIdle()
            assertEquals(60, read().projectionSettings.ndiOutputs.single().ndiFps)
        }
    }

    @Test
    fun `the configured runtime path is shown`() {
        val configured = AppSettings(projectionSettings = ProjectionSettings(ndiRuntimePath = "/opt/ndi"))
        card(configured) { _ -> onNodeWithText("/opt/ndi").assertExists() }
    }

    @Test
    fun `the runtime path is chosen rather than typed`() {
        // Read-only with a folder picker beside it, the shape the VLC path on this same tab uses: a
        // path typed halfway is a path that does not exist, and the field is not the place to find
        // that out. The picker itself is never clicked here — it opens a native chooser.
        card { _ ->
            // No SetText action at all — the field cannot be typed into, rather than accepting text
            // that goes nowhere.
            onNodeWithText("NDI Runtime folder").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
            onNodeWithText("Choose Folder").assertExists()
        }
    }

    @Test
    fun `Identify asks for this output, by index`() {
        val identified = mutableListOf<Int>()
        val two = AppSettings(
            projectionSettings = ProjectionSettings(
                ndiOutputs = listOf(ScreenAssignment(ndiName = "First"), ScreenAssignment(ndiName = "Second")),
            ),
        )
        card(two, identified = identified) { _ ->
            // The second output's button, so a hard-coded 0 would pass by accident.
            onAllNodesWithText("Identify")[1].performClick()
            waitForIdle()
        }
        assertEquals(listOf(1), identified)
    }

    @Test
    fun `every output offers Identify`() {
        val two = AppSettings(
            projectionSettings = ProjectionSettings(ndiOutputs = listOf(ScreenAssignment(), ScreenAssignment())),
        )
        card(two) { _ -> onAllNodesWithText("Identify").assertCountEquals(2) }
    }

    // ── The receiver count ──────────────────────────────────────────────────────

    @Test
    fun `an output nobody is watching says so`() {
        // Otherwise an unsubscribed NDI source looks exactly like a broken one.
        card(oneOutput(), receivers = 0) { _ -> onNodeWithText("No receivers").assertExists() }
    }

    @Test
    fun `the receiver count follows the runtime rather than freezing at composition`() {
        // Reported from a real install: the card said "No receivers" with a receiver attached,
        // because the count was read once during composition and nothing invalidated it again.
        var live = 0
        runComposeUiTest {
            val settings = oneOutput()
            setContent {
                Surface {
                    Box(Modifier.fillMaxSize()) {
                        NdiOutputsCard(
                            settings = settings,
                            onSettingsChange = {},
                            contentGroup = emptyList(),
                            backgroundGroup = emptyList(),
                            displayModes = listOf("Full screen" to Constants.DISPLAY_MODE_FULLSCREEN),
                            songLangModes = emptyList(),
                            translationDisplays = emptyList(),
                            translationNames = emptyList(),
                            status = READY,
                            receiverCount = { live },
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithText("No receivers").assertExists()

            live = 2
            // No settings change, no click — only the runtime's own answer moved, which is exactly
            // how it happens in practice when someone opens a receiver.
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("2 receiving").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun `an output with receivers counts them`() {
        card(oneOutput(), receivers = 3) { _ -> onNodeWithText("3 receiving").assertExists() }
    }
}
