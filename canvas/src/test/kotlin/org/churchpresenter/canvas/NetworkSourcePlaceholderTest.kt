@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test

/**
 * What an NDI or OMT layer draws when it has no picture: a layer pointed at nothing says so, and one
 * pointed at a source it cannot reach names that source.
 *
 * Both go through the app's real shared caches, which in a test have no library loaded — which is
 * exactly the "cannot reach it" case. The "waiting" and picture states need a connected receiver
 * and are covered through the caches' own suites instead.
 */
class NetworkSourcePlaceholderTest {

    @Test
    fun `an OMT layer pointed at nothing says so`() = runComposeUiTest {
        setContent { MaterialTheme { SceneSourceRenderer(SceneSource.OmtSource(id = "o", name = "OMT")) } }
        onNodeWithText("No OMT source selected").assertExists()
    }

    @Test
    fun `an OMT layer that cannot connect names its address`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.OmtSource(id = "o", name = "OMT", sourceAddress = "HOST (Cam)"))
            }
        }
        waitUntil(timeoutMillis = 2_000) {
            onAllNodes(hasText("OMT: HOST (Cam)")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `an NDI layer pointed at nothing says so, through the same placeholder`() = runComposeUiTest {
        setContent { MaterialTheme { SceneSourceRenderer(SceneSource.NdiSource(id = "n", name = "NDI")) } }
        onNodeWithText("No NDI source selected").assertExists()
    }

    @Test
    fun `an NDI layer that cannot connect names its source`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.NdiSource(id = "n", name = "NDI", sourceName = "HOST (Slides)"))
            }
        }
        waitUntil(timeoutMillis = 2_000) {
            onAllNodes(hasText("NDI: HOST (Slides)")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `an NDI layer with only an address names the address`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.NdiSource(id = "n", name = "NDI", sourceAddress = "10.0.0.5:5961"))
            }
        }
        waitUntil(timeoutMillis = 2_000) {
            onAllNodes(hasText("NDI: 10.0.0.5:5961")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
