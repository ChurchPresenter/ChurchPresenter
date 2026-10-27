@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.appsettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.settings.ProjectionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The System page's Smooth web output switch: on in a fresh settings file, and what a click stores. */
class OffscreenWebRowTest {

    @Test
    fun `the switch shows the setting and flips it through the callback`() = runComposeUiTest {
        var applied: AppSettings? = null
        // Every other switch on the page off, so the one that is on is this one.
        val initial = AppSettings(
            analyticsReportingEnabled = false,
            projectionSettings = ProjectionSettings(hideCursorOnOutputs = false, overlayEndClearsDisplay = false),
            keyboardShortcutSettings = KeyboardShortcutSettings(focusSearchOnTabOpen = false),
        )
        setContent {
            MaterialTheme {
                SystemSettingsTab(settings = initial, onSettingsChange = { transform -> applied = transform(initial) })
            }
        }

        onAllNodes(isToggleable() and isOn()).assertCountEquals(1)
        onNode(isToggleable() and isOn()).performScrollTo().performClick()
        waitForIdle()

        assertEquals(false, applied?.projectionSettings?.offscreenWebEnabled, "clicking it turns it off")
    }

    @Test
    fun `smooth web output is on in a fresh settings file`() {
        assertTrue(AppSettings().projectionSettings.offscreenWebEnabled, "off-screen rendering is on unless turned off")
    }
}
