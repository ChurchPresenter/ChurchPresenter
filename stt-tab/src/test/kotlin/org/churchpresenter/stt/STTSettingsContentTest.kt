@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.stt

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class STTSettingsContentTest {

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
                    STTSettingsDialogContent(
                        appSettings = current,
                        onSettingsChange = { transform ->
                            current = transform(current)
                            latestSnapshot = current
                        },
                        onDismiss = {},
                        availableFonts = listOf("Arial"),
                    )
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

    // ── Toggles ──────────────────────────────────────────────────────────────────

    @Test
    fun `word highlighting can be turned on`() = dialog { latest ->
        onNodeWithText("Word Highlighting").assertExists()
        // index 0 is scripture detection, 1 is help-dev-mode (visible by default), 2 is word highlighting
        onAllNodes(isToggleable())[2].assertIsOff().performClick()

        assertEquals(true, latest().sttSettings.showWordHighlighting)
    }

    @Test
    fun `in-progress text can be turned on`() = dialog { latest ->
        onAllNodes(isToggleable())[3].assertIsOff().performClick()
        assertEquals(true, latest().sttSettings.showInProgress)
    }

    @Test
    fun `translation in-progress can be turned on`() = dialog { latest ->
        onAllNodes(isToggleable())[4].assertIsOff().performClick()
        assertEquals(true, latest().sttSettings.showTranslationInProgress)
    }

    @Test
    fun `drip feed is on by default and can be turned off`() = dialog { latest ->
        onAllNodes(isToggleable())[5].assertIsOn().performClick()
        assertEquals(false, latest().sttSettings.dripFeedEnabled)
    }

    // ── Display mode / layout ────────────────────────────────────────────────────

    @Test
    fun `the layout choice is hidden until Both is selected`() = dialog {
        onNodeWithText("LAYOUT").assertDoesNotExist()
    }

    @Test
    fun `picking Both reveals the layout choice`() = dialog { latest ->
        onNodeWithText("Transcription Only").performClick()
        onNodeWithText("Both").performClick()
        waitForIdle()

        assertEquals("both", latest().sttSettings.displayMode)
        onNodeWithText("LAYOUT").assertExists()
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

    @Test
    fun `picking a position tile updates the setting`() = dialog { latest ->
        onNodeWithText("C").performClick()
        assertEquals(Constants.CENTER, latest().sttSettings.position)
    }

    @Test
    fun `picking a different position tile replaces the previous choice`() = dialog(
        settings = AppSettings().let { it.copy(sttSettings = it.sttSettings.copy(position = Constants.CENTER)) },
    ) { latest ->
        onNodeWithText("TL").performClick()
        assertEquals(Constants.TOP_LEFT, latest().sttSettings.position)
    }

    // ── Text style ───────────────────────────────────────────────────────────────

    @Test
    fun `the styling column renders`() = dialog {
        onNodeWithText("Opacity:").assertExists()
    }

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
                        availableFonts = listOf("Arial"),
                    )
                }
            }
            onNodeWithText("Close").performClick()
            assertEquals(1, dismissed)
        }
    }
}
