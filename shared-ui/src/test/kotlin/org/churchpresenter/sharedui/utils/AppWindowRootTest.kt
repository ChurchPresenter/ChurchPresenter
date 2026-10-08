package org.churchpresenter.sharedui.utils

import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertTrue

/** [AppWindowRoot]: the window's content is drawn, inside the clipboard that survives a failed paste. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class AppWindowRootTest {

    @Test
    fun `a window's content draws under the safe clipboard`() = runComposeUiTest {
        var clipboardSeen: Any? = null
        setContent {
            AppWindowRoot(theme = ThemeMode.LIGHT) {
                clipboardSeen = LocalClipboard.current
                Text("window content")
            }
        }

        onNodeWithText("window content").assertExists()
        assertTrue(clipboardSeen is SafeClipboard, "the content saw $clipboardSeen, not the safe clipboard")
    }
}
