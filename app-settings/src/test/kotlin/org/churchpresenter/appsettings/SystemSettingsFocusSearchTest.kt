package org.churchpresenter.appsettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.settings.ProjectionSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The General card's "Focus search when opening a tab" switch (#798): it flips the setting and
 * keeps the key bindings stored beside it. Its own class because `SystemSettingsTabTest` is at
 * detekt's size limit.
 */
@OptIn(ExperimentalTestApi::class)
class SystemSettingsFocusSearchTest {

    @Test
    fun `the focus-search switch flips the setting and keeps the key bindings`() = runComposeUiTest {
        var applied: AppSettings? = null
        val initial = AppSettings(
            projectionSettings = ProjectionSettings(hideCursorOnOutputs = false, overlayEndClearsDisplay = false),
            keyboardShortcutSettings = KeyboardShortcutSettings(
                overrides = mapOf("UNDO" to emptyList()),
                focusSearchOnTabOpen = false,
            ),
        )
        setContent {
            MaterialTheme {
                SystemSettingsTab(settings = initial, onSettingsChange = { transform -> applied = transform(initial) })
            }
        }

        onNode(isToggleable() and hasText("Focus search when opening a tab", substring = true))
            .performScrollTo().performClick()
        waitForIdle()

        assertEquals(true, applied?.keyboardShortcutSettings?.focusSearchOnTabOpen, "off turns on")
        assertEquals(initial.keyboardShortcutSettings.overrides, applied?.keyboardShortcutSettings?.overrides)
    }
}
