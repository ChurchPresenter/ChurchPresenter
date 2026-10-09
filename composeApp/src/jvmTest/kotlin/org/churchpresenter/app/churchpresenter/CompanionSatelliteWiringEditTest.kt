package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CompanionSatelliteWiring] when the operator edits a connection: an edit is an explicit action,
 * so it connects even though the connection does not auto-connect. Port 0 is one no socket can use,
 * so the client reports an error at once and nothing is dialled.
 */
@OptIn(ExperimentalTestApi::class)
class CompanionSatelliteWiringEditTest {

    @Test
    fun `editing a connection connects it even without auto-connect`() = runComposeUiTest {
        val viewModel = CompanionSatelliteViewModel()
        val reconciled = mutableMapOf<String, CompanionSatelliteSettings>()
        val original = CompanionSatelliteSettings(
            id = "sat-1", host = "127.0.0.1", port = 0, autoConnect = false, showInTab = true,
        )
        var settings by mutableStateOf(AppSettings(companionSatelliteConnections = listOf(original)))
        try {
            setContent { CompanionSatelliteWiring(settings, viewModel, reconciled) }
            waitForIdle()
            assertTrue(viewModel.connectionStates.keys.none { it.connectionId == "sat-1" }, "seen, not yet edited")
            assertEquals(original, reconciled["sat-1"])

            val edited = original.copy(name = "Front desk")
            settings = AppSettings(companionSatelliteConnections = listOf(edited))
            waitUntil { viewModel.connectionStates.keys.any { it.connectionId == "sat-1" } }
            assertEquals(edited, reconciled["sat-1"])
        } finally {
            viewModel.dispose()
        }
    }
}
