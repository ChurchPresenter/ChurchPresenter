@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleEngineSettings
import org.churchpresenter.settings.STTSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class STTSettingsDialogContentTest {

    @OptIn(ExperimentalTestApi::class)
    /** The dialog. How captions look is per profile now, edited on the Profiles tab's Live captions page. */
    private fun sttDialog(
        sttSettings: STTSettings = STTSettings(),
        bibleEngineSettings: BibleEngineSettings = BibleEngineSettings(),
        block: ComposeUiTest.(get: () -> AppSettings, dismissCount: () -> Int) -> Unit,
    ) = runComposeUiTest {
        var current = AppSettings(sttSettings = sttSettings, bibleEngineSettings = bibleEngineSettings)
        var dismissCount = 0
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                val onChange: ((AppSettings) -> AppSettings) -> Unit = { transform ->
                    state = transform(state)
                    current = state
                }
                STTSettingsDialogContent(
                    appSettings = state,
                    onSettingsChange = onChange,
                    onDismiss = { dismissCount++ },
                )
            }
        }
        block({ current }, { dismissCount })
    }

    // ── Scripture detection (Bible Lookup Engine) ───────────────────────────────────────────────────

    @Test
    fun `the detect scripture checkbox toggles the engine and reveals or hides the help-dev row`() =
        sttDialog { get, _ ->
            assertEquals(true, get().bibleEngineSettings.enabled, "on by default")
            onNodeWithText("Help Dev", substring = true).assertIsDisplayed()

            onAllNodes(isToggleable())[0].performClick()
            waitForIdle()
            assertEquals(false, get().bibleEngineSettings.enabled)
            onNodeWithText("Help Dev", substring = true).assertDoesNotExist()

            onAllNodes(isToggleable())[0].performClick()
            waitForIdle()
            assertEquals(true, get().bibleEngineSettings.enabled)
            onNodeWithText("Help Dev", substring = true).assertIsDisplayed()
        }

    @Test
    fun `the help-dev checkbox toggles independently while the engine stays enabled`() = sttDialog { get, _ ->
        onAllNodes(isToggleable())[1].performClick()
        waitForIdle()
        assertEquals(true, get().bibleEngineSettings.helpDevMode)
        assertEquals(true, get().bibleEngineSettings.enabled, "the engine checkbox must be untouched")
    }

    @Test
    @Suppress("MaxLineLength")
    fun `the host and port fields appear only when the engine is enabled and not local, and the port field ignores non-numeric text`() =
        sttDialog(bibleEngineSettings = BibleEngineSettings(enabled = true, runLocal = false)) { get, _ ->
            onNode(hasSetTextAction() and hasText("localhost")).performTextReplacement("192.168.1.5")
            waitForIdle()
            assertEquals("192.168.1.5", get().bibleEngineSettings.host)

            onNode(hasSetTextAction() and hasText("8766")).performTextReplacement("9000")
            waitForIdle()
            assertEquals(9000, get().bibleEngineSettings.port)

            onNode(hasSetTextAction() and hasText("9000")).performTextReplacement("abc")
            waitForIdle()
            assertEquals(9000, get().bibleEngineSettings.port, "non-numeric text must be ignored, not stored as 0")
        }

    @Test
    fun `with the engine disabled, the help-dev row and host or port fields are both hidden`() = sttDialog(
        bibleEngineSettings = BibleEngineSettings(enabled = false, runLocal = false),
    ) { _, _ ->
        onNodeWithText("Help Dev", substring = true).assertDoesNotExist()
        onNodeWithText("localhost").assertDoesNotExist()
    }

    @Test
    fun `with the engine enabled but running locally, the host and port fields stay hidden`() = sttDialog { _, _ ->
        onNodeWithText("localhost").assertDoesNotExist()
    }

    // ── Close ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the close button invokes onDismiss`() = sttDialog { _, dismissCount ->
        onNodeWithText("Close").performClick()
        waitForIdle()
        assertEquals(1, dismissCount())
    }
}

// ── Locators local to this file ────────────────────────────────────────────────────────────────────
