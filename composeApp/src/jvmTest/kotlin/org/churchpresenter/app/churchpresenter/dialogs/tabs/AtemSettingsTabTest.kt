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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Blackmagic ATEM settings tab exposes three broadcast toggles (downstream key, quick upload,
 * go-live key) and numeric connection fields, all writing back into [AppSettings]. These drive the
 * real controls and assert the settings they produce — not just that the tab renders, which would
 * mark it covered without ever running the onCheckedChange / onValueChange lambdas.
 */
@OptIn(ExperimentalTestApi::class)
class AtemSettingsTabTest {

    private fun runTab(block: ComposeUiTest.(get: () -> AppSettings) -> Unit) = runComposeUiTest {
        var current = AppSettings()
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                AtemSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state); current = state },
                )
            }
        }
        block { current }
    }

    @Test
    fun `each broadcast toggle flips its flag`() = runTab { get ->
        val atem = get().atemSettings
        assertTrue(!atem.useDownstreamKey && !atem.quickUpload && !atem.goLiveKey, "all three start off")

        val toggles = onAllNodes(isToggleable()).fetchSemanticsNodes().size
        assertEquals(3, toggles, "downstream key, quick upload, go-live key")
        for (i in 0 until toggles) onAllNodes(isToggleable())[i].performClick()

        val after = get().atemSettings
        assertTrue(after.useDownstreamKey, "the downstream-key switch must set useDownstreamKey")
        assertTrue(after.quickUpload, "the quick-upload switch must set quickUpload")
        assertTrue(after.goLiveKey, "the go-live-key switch must set goLiveKey")
    }

    @Test
    fun `editing the ATEM port writes it back into settings`() = runTab { get ->
        assertEquals(9910, get().atemSettings.port, "default port precondition")
        onNodeWithText("9910").performTextReplacement("9920")
        assertEquals(9920, get().atemSettings.port, "the port field must parse and update atemSettings.port")
    }
}
