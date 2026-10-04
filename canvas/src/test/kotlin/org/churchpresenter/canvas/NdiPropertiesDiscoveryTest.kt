@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.ndi.FakeNdiLibrary
import org.churchpresenter.ndi.NdiBandwidth
import org.churchpresenter.ndi.NdiFinder
import org.churchpresenter.ndi.NdiReceiver
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.ndi.NdiSourceInfo
import org.churchpresenter.omt.OmtReceiver
import org.churchpresenter.omt.OmtRuntimeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NdiPropertiesDiscoveryTest {

    /** A loaded NDI runtime; nothing here receives, so the rest answers as an unloaded library. */
    private object ReadyNdi : NetworkInputs {
        override val ndiStatus: StateFlow<NdiRuntimeStatus> = MutableStateFlow(NdiRuntimeStatus.Ready("6.0", "/fake"))
        override fun createNdiFinder(): NdiFinder? = null
        override fun createNdiReceiver(source: NdiSourceInfo, bandwidth: NdiBandwidth, receiverName: String): NdiReceiver? =
            null
        override val omtStatus: StateFlow<OmtRuntimeStatus> = MutableStateFlow(OmtRuntimeStatus.NotInstalled)
        override fun discoverOmtSources(): List<String> = emptyList()
        override fun createOmtReceiver(address: String, preview: Boolean): OmtReceiver? = null
    }

    private fun panel(
        onNetwork: List<NdiSourceInfo>,
        start: SceneSource.NdiSource = SceneSource.NdiSource(id = "n", name = "NDI"),
        block: ComposeUiTest.(get: () -> SceneSource.NdiSource) -> Unit,
    ) = runComposeUiTest {
        val lib = FakeNdiLibrary().apply { discoverable += onNetwork }
        val directory = NdiSourceDirectory { NdiFinder(lib) }
        var current = start
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(start) }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NdiProperties(state, { updated ->
                        state = updated as SceneSource.NdiSource
                        current = updated
                    }, inputs = ReadyNdi, directory = directory)
                }
            }
        }
        waitForIdle()
        block { current }
    }

    private val camera = NdiSourceInfo("STUDIO (Camera 1)", "10.0.0.5:5961")
    private val graphics = NdiSourceInfo("GFX (Lower thirds)", "10.0.0.6:5961")

    @Test
    fun `what discovery finds is offered, and choosing one points the layer at it`() =
        panel(listOf(camera, graphics), SceneSource.NdiSource(id = "n", name = "NDI", sourceName = camera.name)) { get ->
            waitUntil(timeoutMillis = 5_000) { renderedText().contains("SOURCE") }
            chooseFromDropdown(camera.name, graphics.name)

            assertEquals("GFX (Lower thirds)", get().sourceName)
            assertEquals("10.0.0.6:5961", get().sourceAddress)
        }

    @Test
    fun `an empty network says so once it has looked`() = panel(emptyList()) { _ ->
        waitUntil(timeoutMillis = 5_000) { renderedText().any { "No NDI sources found" in it } }
        onNodeWithText("No NDI sources found", substring = true).assertExists()
    }

    @Test
    fun `refreshing looks again`() = panel(emptyList()) { _ ->
        waitUntil(timeoutMillis = 5_000) { renderedText().any { "No NDI sources found" in it } }

        onNodeWithText("Refresh Sources").performClick()
        waitForIdle()

        onNodeWithText("Refresh Sources").assertExists()
    }

    @Test
    fun `the low-bandwidth stream can be asked for`() = panel(listOf(camera)) { get ->
        onNodeWithText("Low bandwidth (proxy stream)").performClick()
        waitForIdle()

        assertTrue(get().lowBandwidth)
    }
}
