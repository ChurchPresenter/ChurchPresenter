@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.OmtProperties
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.omt.OmtRuntimeStatus
import kotlin.test.Test

private val READY = OmtRuntimeStatus.Ready("/app/omt/libomt.dylib", bundled = true)

/**
 * The Canvas's OMT source panel with the library loaded — the state the Canvas tab's own suite
 * cannot reach, since no library is loaded where the suite runs (its `source_omt` image is the
 * "library could not be loaded" state instead). The network is pinned, so the list is the same on
 * every machine.
 */
class CanvasOmtSourceScreenshotTest {

    private companion object {
        const val SECTION = "canvasOmtSource"
        val PANEL_WIDTH = 280.dp
    }

    private fun shoot(name: String, source: SceneSource.OmtSource, network: List<String>) =
        captureComponent(SECTION, name) {
            Column(Modifier.width(PANEL_WIDTH)) {
                OmtProperties(
                    source = source,
                    onUpdate = {},
                    status = READY,
                    discover = { network },
                    lookStepMs = 1,
                )
            }
        }

    @Test
    fun `a source chosen from what discovery found`() = shoot(
        "source_chosen",
        SceneSource.OmtSource(id = "o", name = "OMT", sourceAddress = "BOOTH-PC (Camera 1)"),
        network = listOf("BOOTH-PC (Camera 1)", "BOOTH-PC (Graphics)", "FOYER (Welcome loop)"),
    )

    @Test
    fun `a typed address on the preview stream`() = shoot(
        "address_preview",
        SceneSource.OmtSource(id = "o", name = "OMT", sourceAddress = "omt://10.0.0.5:6400", preview = true),
        network = emptyList(),
    )
}
