@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.omt.OmtRuntimeStatus
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BLANK_OUTPUT_PROFILE_ID
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Projection pages driven the way the app drives them -- every optional argument given -- and
 * the NDI and OMT cards on the live managers' defaults, which read state and nothing native. The
 * profile picker's names fall back to the profile's id when it has none.
 */
class ProjectionCardDefaultsTest {

    @Test
    fun `a release build with every hook given asks each one for its own output`() {
        val identified = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var settings by remember { mutableStateOf(AppSettings()) }
                    ProjectionSettingsTab(
                        settings = settings,
                        onSettingsChange = { settings = it(settings) },
                        companionServer = CompanionServer(shutdownGraceMs = 0),
                        onIdentifyScreen = { identified += "screen" },
                        onIdentifyBrowserSource = { identified += "browser $it" },
                        onIdentifyNdi = { identified += "ndi $it" },
                        onIdentifyOmt = { identified += "omt $it" },
                        scenes = emptyList(),
                        detectScreens = { emptyList() },
                        ndiStatus = { NdiRuntimeStatus.NotInstalled },
                        ndiReceiverCount = { 0 },
                        omtStatus = { OmtRuntimeStatus.NotInstalled },
                        omtReceiverCount = { 0 },
                        omtAddressOf = { "" },
                        ffmpegProbe = { PINNED_FFMPEG },
                        vlcProbe = { false },
                        audioDeviceProbe = { emptyList() },
                        isRelease = true,
                    )
                }
            }
            waitForIdle()
            // A release build with no real screen offers no simulated Dev Window.
            onNodeWithText("Dev Window", substring = true).assertDoesNotExist()
            onNodeWithText("Camera Capture").performScrollTo().assertExists()
        }
        assertEquals(emptyList(), identified, "nothing is identified until asked")
    }

    @Test
    fun `left to its default hooks, every Identify button is safe to press`() = runComposeUiTest {
        val settings = AppSettings(
            projectionSettings = ProjectionSettings(
                browserSourceOutputs = listOf(ScreenAssignment()),
                ndiOutputs = listOf(ScreenAssignment()),
                omtOutputs = listOf(ScreenAssignment()),
            ),
        )
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(settings) }
                ProjectionSettingsTab(
                    settings = state,
                    onSettingsChange = { state = it(state) },
                    companionServer = CompanionServer(shutdownGraceMs = 0),
                    detectScreens = { emptyList() },
                    ndiStatus = { NdiRuntimeStatus.NotInstalled },
                    omtStatus = { OmtRuntimeStatus.NotInstalled },
                    ffmpegProbe = { PINNED_FFMPEG },
                    vlcProbe = { false },
                )
            }
        }
        waitForIdle()
        val identify = onAllNodesWithText("Identify")
        val count = identify.fetchSemanticsNodes().size
        (0 until count).forEach { i -> onAllNodesWithText("Identify")[i].performScrollTo().performClick() }
        waitForIdle()
        onNodeWithText("Camera Capture").performScrollTo().assertExists()
        assertTrue(count > 0, "the outputs offer Identify")
    }

    @Test
    fun `the NDI and OMT cards draw on the live managers' state by default`() = runComposeUiTest {
        val settings = AppSettings(
            projectionSettings = ProjectionSettings(
                ndiOutputs = listOf(ScreenAssignment()),
                omtOutputs = listOf(ScreenAssignment()),
            ),
        )
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(settings) }
                Column {
                    NdiOutputsCard(
                        settings = state,
                        onSettingsChange = { state = it(state) },
                        openUrl = {},
                        copyText = {},
                    )
                    OmtOutputsCard(settings = state, onSettingsChange = { state = it(state) }, recheck = { _, _ -> })
                }
            }
        }
        waitForIdle()
        onNodeWithText("NDI Outputs").assertExists()
        onNodeWithText("OMT Outputs").assertExists()
    }

    @Test
    fun `a profile with no name is shown by its id, and a merged output names who merged it`() = runComposeUiTest {
        val unnamed = OutputProfile(id = "p-unnamed", name = "")
        var merged by mutableStateOf<OutputProfile?>(null)
        setContent {
            MaterialTheme {
                OutputProfilePicker(
                    profiles = listOf(unnamed),
                    activeProfileId = "p-unnamed",
                    onPick = {},
                    mergedBy = merged,
                )
            }
        }
        onNodeWithText("p-unnamed").performClick()
        waitForIdle()
        // The button and the menu's item both name it by its id.
        onAllNodes(hasClickAction() and hasTextExactly("p-unnamed")).assertCountEquals(2)

        merged = OutputProfile(id = "wall", name = "")
        waitForIdle()
        onNodeWithText("wall", substring = true).assertExists()
    }

    @Test
    fun `a blank output names itself Blank`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                OutputProfilePicker(profiles = emptyList(), activeProfileId = BLANK_OUTPUT_PROFILE_ID, onPick = {})
            }
        }
        onNodeWithText("Blank").assertExists()
    }
}
