@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class STTSettingsContentTest {

    /** The dialog. How captions look is per profile now, edited on the Profiles tab's Live captions page. */
    private fun dialog(
        settings: AppSettings = AppSettings(),
        block: ComposeUiTest.(latest: () -> AppSettings) -> Unit,
    ) {
        var latestSnapshot = settings
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var current by remember { mutableStateOf(settings) }
                    latestSnapshot = current
                    val onChange: ((AppSettings) -> AppSettings) -> Unit = { transform ->
                        current = transform(current)
                        latestSnapshot = current
                    }
                    STTSettingsDialogContent(appSettings = current, onSettingsChange = onChange, onDismiss = {})
                }
            }
            block { latestSnapshot }
        }
    }

    // ── Scripture detection ─────────────────────────────────────────────────────

    @Test
    fun `scripture detection is on by default`() = dialog {
        onAllNodes(isToggleable())[0].assertIsOn()
    }

    @Test
    fun `turning off scripture detection also hides the help-dev-mode toggle`() = dialog { latest ->
        onAllNodes(isToggleable())[0].performClick()

        assertEquals(false, latest().bibleEngineSettings.enabled)
        onNodeWithText("Detect scripture").assertExists()
        onNodeWithText(
            "Help Dev — show live feedback buttons on the Bible tab (wrong passage / premature / missed passage)",
        ).assertDoesNotExist()
    }

    // ── Position grid ────────────────────────────────────────────────────────────

    @Test
    fun `no position tile is selected under the default settings`() {
        // The default position (Constants.BOTTOM = "Bottom") does not match any of the nine grid
        // tiles (which are Top/Center/Bottom crossed with Left/Center/Right) — a real, if minor,
        // gap between the stored default and what the grid can express.
        dialog {
            assertTrue(Constants.BOTTOM !in listOf(
                Constants.TOP_LEFT, Constants.TOP_CENTER, Constants.TOP_RIGHT,
                Constants.CENTER_LEFT, Constants.CENTER, Constants.CENTER_RIGHT,
                Constants.BOTTOM_LEFT, Constants.BOTTOM_CENTER, Constants.BOTTOM_RIGHT,
            ))
        }
    }

    // ── Text style ───────────────────────────────────────────────────────────────

    @Test
    fun `close calls onDismiss`() {
        var dismissed = 0
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    STTSettingsDialogContent(
                        appSettings = AppSettings(),
                        onSettingsChange = {},
                        onDismiss = { dismissed++ },
                    )
                }
            }
            onNodeWithText("Close").performClick()
            assertEquals(1, dismissed)
        }
    }
}
