@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.controlin.ControlSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test

class HiddenDialogsTest {

    @Test
    fun `a hidden dialog with the app's own frame draws nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Text("host")
                MessageDialog(false, emptyList(), {}, null, {}, {}, {})
                PropsDialog(false, emptyList(), {}, emptySet(), { _, _ -> }, { null }, {})
                MacrosDialog(false, emptyList(), emptyList(), {}, {}, {})
                ClearGroupsDialog(false, emptyList(), {}, {}, {})
                AddLabelDialog(false, {}, { _, _, _ -> })
                KeyboardShortcutsDialog(false, AppSettings(), {}, onDismiss = {})
                AboutDialog(false, {}, AppSettings(), TEST_IDENTITY)
                ContactUsDialog(false, {}, identity = TEST_IDENTITY)
                ControlDialog(false, ControlSettings(), ControlPanelData(), ControlPanelActions(), {})
                CustomizeThemeDialog(false, ThemeMode.LIGHT, defaultChoice(false), {}, {})
                MemoryMonitorWindow(false, ThemeMode.LIGHT, {})
                KonamiEasterEggDialog(false, {})
                RemoteEventDialog(
                    event = null, onAllow = {}, onAllowForSession = {}, onAllowPermanently = {},
                    onBlockForSession = {}, onBlockPermanently = {}, onDeny = {},
                )
            }
        }
        waitForIdle()
        onNodeWithText("host").assertExists()
        onNodeWithText("Keyboard Shortcuts").assertDoesNotExist()
        onNodeWithText("Customize Theme").assertDoesNotExist()
    }
}
