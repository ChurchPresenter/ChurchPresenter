@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.omt.OmtRuntimeStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val READY = OmtRuntimeStatus.Ready("/app/omt/libomt.dylib", bundled = true)
private const val CAMERA = "BOOTH (Camera 1)"
private const val GRAPHICS = "BOOTH (Graphics)"

/**
 * The Canvas's OMT source panel: what it offers, and what each control writes to the layer.
 *
 * The network is a parameter — no fixture can put a sender on it — and so is the library's status,
 * so the panel is driven whole with nothing loaded. The looks are a millisecond apart rather than a
 * second, which is the only way the "none found" state is reachable inside the suite's time budget.
 */
class SourcePropertiesOmtTest {

    private fun omt(address: String = "", preview: Boolean = false) =
        SceneSource.OmtSource(id = "omt", name = "OMT", sourceAddress = address, preview = preview)

    private fun panel(
        initial: SceneSource.OmtSource = omt(),
        status: OmtRuntimeStatus = READY,
        network: List<String> = emptyList(),
        looks: AtomicInteger = AtomicInteger(),
        body: ComposeUiTest.(read: () -> SceneSource.OmtSource) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        setContent {
            Surface {
                Column {
                    var state by remember { mutableStateOf(initial) }
                    OmtProperties(
                        source = state,
                        onUpdate = { updated ->
                            state = updated as SceneSource.OmtSource
                            current = state
                        },
                        status = status,
                        discover = { looks.incrementAndGet(); network },
                        lookStepMs = 1,
                    )
                }
            }
        }
        waitForIdle()
        body { current }
    }

    @Test
    fun `with no library the panel says so and offers nothing`() {
        panel(status = OmtRuntimeStatus.LoadFailed("/x")) { _ ->
            onNodeWithText("OMT sources need the OMT library", substring = true).assertExists()
            onNodeWithText("Refresh Sources").assertDoesNotExist()
            onNode(hasSetTextAction()).assertDoesNotExist()
        }
    }

    @Test
    fun `an empty network says so once it has looked`() {
        val looks = AtomicInteger()
        panel(looks = looks) { _ ->
            waitUntil(timeoutMillis = 2_000) { looks.get() >= 5 }
            waitForIdle()
            onNodeWithText("No OMT sources found on the network").assertExists()
        }
    }

    @Test
    fun `choosing a discovered source stores it as the layer's address`() {
        panel(network = listOf(CAMERA, GRAPHICS)) { read ->
            waitUntil(timeoutMillis = 2_000) { onAllNodesWithText("SOURCE").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("SOURCE").performClick()
            waitForIdle()
            onNodeWithText(GRAPHICS).performClick()
            waitForIdle()
            assertEquals(GRAPHICS, read().sourceAddress)
        }
    }

    @Test
    fun `Refresh looks again once the last look has finished`() {
        val looks = AtomicInteger()
        panel(looks = looks) { _ ->
            waitUntil(timeoutMillis = 2_000) { looks.get() >= 5 }
            waitForIdle()
            onNodeWithText("Refresh Sources").performClick()
            waitUntil(timeoutMillis = 2_000) { looks.get() >= 10 }
        }
    }

    @Test
    fun `a typed address is committed on Done, not per keystroke`() {
        panel(omt(address = CAMERA)) { read ->
            onNode(hasSetTextAction()).performTextReplacement("omt://10.0.0.5:6400")
            waitForIdle()
            assertEquals(CAMERA, read().sourceAddress, "not until Done")
            onNode(hasSetTextAction()).performImeAction()
            waitForIdle()
            assertEquals("omt://10.0.0.5:6400", read().sourceAddress)
        }
    }

    @Test
    fun `the preview stream is switched on from its checkbox`() {
        panel { read ->
            onNodeWithText("Preview stream (1/8 size)").performClick()
            waitForIdle()
            assertTrue(read().preview)
        }
    }

    @Test
    fun `a configured source discovery cannot see is still offered, and only once`() {
        assertEquals(listOf(GRAPHICS, CAMERA), omtSourceChoices(listOf(GRAPHICS), CAMERA))
        assertEquals(listOf(CAMERA, GRAPHICS), omtSourceChoices(listOf(CAMERA, GRAPHICS), CAMERA))
        assertEquals(listOf(CAMERA), omtSourceChoices(listOf(CAMERA), ""))
        assertEquals(emptyList(), omtSourceChoices(emptyList(), ""))
    }
}
