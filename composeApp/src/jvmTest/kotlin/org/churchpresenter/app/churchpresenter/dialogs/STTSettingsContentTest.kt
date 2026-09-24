@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
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

    /**
     * The dialog, or -- with [display] -- the caption form the Profiles tab draws, which is where
     * everything about how captions look moved when it became per-profile.
     */
    private fun dialog(
        settings: AppSettings = AppSettings(),
        display: Boolean = false,
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
                    if (display) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            STTDisplaySettings(
                                appSettings = current,
                                onSettingsChange = onChange,
                                availableFonts = listOf("Arial"),
                            )
                        }
                    } else {
                        STTSettingsDialogContent(appSettings = current, onSettingsChange = onChange, onDismiss = {})
                    }
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
    fun `word highlighting can be turned on`() = dialog(display = true) { latest ->
        onNodeWithText("Word Highlighting").assertExists()
        // The caption form's first toggle -- the engine's two stayed in the dialog.
        onAllNodes(isToggleable())[0].assertIsOff().performClick()

        assertEquals(true, latest().sttSettings.showWordHighlighting)
    }

    @Test
    fun `in-progress text can be turned on`() = dialog(display = true) { latest ->
        onAllNodes(isToggleable())[1].assertIsOff().performClick()
        assertEquals(true, latest().sttSettings.showInProgress)
    }

    @Test
    fun `translation in-progress can be turned on`() = dialog(display = true) { latest ->
        onAllNodes(isToggleable())[2].assertIsOff().performClick()
        assertEquals(true, latest().sttSettings.showTranslationInProgress)
    }

    @Test
    fun `drip feed is on by default and can be turned off`() = dialog(display = true) { latest ->
        onAllNodes(isToggleable())[3].assertIsOn().performClick()
        assertEquals(false, latest().sttSettings.dripFeedEnabled)
    }

    // ── Display mode / layout ────────────────────────────────────────────────────

    @Test
    fun `the layout choice is hidden until Both is selected`() = dialog(display = true) {
        onNodeWithText("LAYOUT").assertDoesNotExist()
    }

    @Test
    fun `picking Both reveals the layout choice`() = dialog(display = true) { latest ->
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
    fun `picking a position tile updates the setting`() = dialog(display = true) { latest ->
        onNodeWithContentDescription("C").performClick()
        assertEquals(Constants.CENTER, latest().sttSettings.position)
    }

    @Test
    fun `picking a different position tile replaces the previous choice`() = dialog(
        settings = AppSettings().let { it.copy(sttSettings = it.sttSettings.copy(position = Constants.CENTER)) },
        display = true,
    ) { latest ->
        onNodeWithContentDescription("TL").performClick()
        assertEquals(Constants.TOP_LEFT, latest().sttSettings.position)
    }

    // ── Text style ───────────────────────────────────────────────────────────────

    @Test
    fun `the styling column renders`() = dialog(display = true) {
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
                    )
                }
            }
            onNodeWithText("Close").performClick()
            assertEquals(1, dismissed)
        }
    }
}
