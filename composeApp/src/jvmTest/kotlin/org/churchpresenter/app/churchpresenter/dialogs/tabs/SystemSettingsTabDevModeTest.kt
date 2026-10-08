package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.settings.ProjectionSettings
import kotlin.test.Test
import org.churchpresenter.profiles.LocalSettingsDevMode

/** The System tab's Dev mode only card: preview mode and the test event, and only in dev mode. */
@OptIn(ExperimentalTestApi::class)
class SystemSettingsTabDevModeTest {

    /** Settings with analytics [enabled] and every other switch off, so no other one is on. */
    private fun analytics(enabled: Boolean) = AppSettings(
        analyticsReportingEnabled = enabled,
        projectionSettings = ProjectionSettings(hideCursorOnOutputs = false, overlayEndClearsDisplay = false),
        keyboardShortcutSettings = KeyboardShortcutSettings(focusSearchOnTabOpen = false),
    )

    @Test
    fun `in dev mode a card of its own holds preview mode and the test event, with its note`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSettingsDevMode provides true) {
                    SystemSettingsTab(settings = analytics(true))
                }
            }
        }

        onNodeWithTag(DEV_MODE_CARD_TAG).assertExists()
        onAllNodesWithText("Dev mode only").onFirst().assertExists()
        onAllNodesWithText("Preview mode").onFirst().assertExists()
        onNode(hasText("Send test event") and hasClickAction())
            .assertExists("the test-event button must be offered, not just its label")
            .assertIsEnabled()
        onAllNodesWithText("Visible to developers only — hidden in released installer builds.").onFirst()
            .assertExists("the note explaining why the button is there must render with it")
    }

    @Test
    fun `with reporting off the test event cannot be sent, and outside dev mode there is no card`() =
        runComposeUiTest {
            var devMode by mutableStateOf(true)
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalSettingsDevMode provides devMode) {
                        SystemSettingsTab(settings = analytics(false))
                    }
                }
            }
            onNode(hasText("Send test event") and hasClickAction()).assertIsNotEnabled()

            devMode = false
            waitForIdle()
            onAllNodesWithTag(DEV_MODE_CARD_TAG).assertCountEquals(0)
            onAllNodesWithText("Send test event").assertCountEquals(0)
            onAllNodesWithText("Preview mode").assertCountEquals(0)
        }
}
