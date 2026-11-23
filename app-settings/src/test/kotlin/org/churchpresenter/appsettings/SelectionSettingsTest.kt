@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.appsettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertTrue

class SelectionSettingsTest {
    @Test
    fun `system settings toggles selection projection and opens the shortcut editor`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        var opened = false
        setContent {
            MaterialTheme {
                SelectionSettings(settings, { transform -> settings = transform(settings) }, { opened = true })
            }
        }
        onNode(isToggleable()).performClick()
        assertTrue(settings.selectionGoesLive)
        onNodeWithText("Configure keyboard shortcuts").performClick()
        assertTrue(opened)
    }
}
