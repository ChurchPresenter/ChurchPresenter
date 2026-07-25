package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The background settings tab is one of the largest pure-View surfaces in the app. Beyond composing,
 * this drives its primary control — the default-background type dropdown — and asserts the setting it
 * produces, so the onTypeSelected path is actually run rather than only rendered.
 */
@OptIn(ExperimentalTestApi::class)
class BackgroundSettingsTabTest {

    private fun runTab(block: ComposeUiTest.(get: () -> AppSettings) -> Unit) = runComposeUiTest {
        var current = AppSettings()
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                BackgroundSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state); current = state },
                )
            }
        }
        block { current }
    }

    @Test
    fun `the tab composes and shows its first section`() = runTab {
        onNodeWithText("Default Background", substring = true)
            .assertExists("the default-background section must render when the tab opens")
    }

    @Test
    fun `choosing the transparent background type updates the setting`() = runTab { get ->
        assertEquals(Constants.BACKGROUND_COLOR, get().backgroundSettings.defaultBackgroundType, "default is a colour")

        // The type dropdown shows "Color" (as does the colour picker below it); the dropdown is first.
        onAllNodesWithText("Color").onFirst().performClick()
        onNodeWithText("Transparent").performClick()

        assertEquals(
            Constants.BACKGROUND_TRANSPARENT,
            get().backgroundSettings.defaultBackgroundType,
            "picking Transparent must set the default background type",
        )
    }

    @Test
    fun `editing the default colour through the picker dialog updates the setting`() = runTab { get ->
        assertEquals("#000000", get().backgroundSettings.defaultBackgroundColor, "default is black")

        // The colour field (tagged) opens a dialog whose only text field is the hex input.
        onNodeWithTag("bg_defaultColor").performClick()
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("#123456")
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertTrue(
            get().backgroundSettings.defaultBackgroundColor.equals("#123456", ignoreCase = true),
            "the picked hex must become the default background colour, was ${get().backgroundSettings.defaultBackgroundColor}",
        )
    }
}
