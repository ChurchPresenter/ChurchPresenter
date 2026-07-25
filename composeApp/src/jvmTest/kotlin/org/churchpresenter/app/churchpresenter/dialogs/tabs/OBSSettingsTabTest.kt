package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The OBS settings tab is a gated form: the enable switch reveals the connection fields, which write
 * straight back into [AppSettings]. These tests drive the actual controls — the toggle and the host/
 * port fields — and assert the settings they produce, rather than only that the tab renders (which
 * would light up JaCoCo without ever running the onCheckedChange / onValueChange lambdas).
 */
@OptIn(ExperimentalTestApi::class)
class OBSSettingsTabTest {

    /** Renders the tab over live [AppSettings] state so control changes round-trip back into it. */
    private fun runTab(block: ComposeUiTest.(get: () -> AppSettings) -> Unit) =
        runComposeUiTest {
            var current = AppSettings()
            val obsManager = OBSWebSocketManager()
            setContent {
                MaterialTheme {
                    var state by remember { mutableStateOf(current) }
                    OBSSettingsTab(
                        settings = state,
                        onSettingsChange = { transform -> state = transform(state); current = state },
                        obsManager = obsManager,
                    )
                }
            }
            block { current }
        }

    @Test
    fun `enabling OBS flips the flag and reveals the connection fields`() = runTab { get ->
        // Only the enable switch is present while OBS is off; the host field shows once it's on.
        onNodeWithText("localhost").assertDoesNotExist()
        onNode(isToggleable()).performClick()

        assertTrue(get().obsSettings.enabled, "the enable switch must set obsSettings.enabled")
        onNodeWithText("localhost").assertExists("the host field must appear once OBS is enabled")
    }

    @Test
    fun `editing host and port writes them back into settings`() = runTab { get ->
        onNode(isToggleable()).performClick() // enable to reveal the fields

        onNodeWithText("localhost").performTextReplacement("obs-box.local")
        assertEquals("obs-box.local", get().obsSettings.host, "the host field must update obsSettings.host")

        onNodeWithText("4455").performTextReplacement("9001")
        assertEquals(9001, get().obsSettings.port, "the port field must parse and update obsSettings.port")
    }
}
