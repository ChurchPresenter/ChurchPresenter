@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.dialogs.DialogFrame
import org.churchpresenter.dialogs.DialogFrameSpec
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dialogs that stay in the app, opened through the frame they are given — the rest are in
 * `:dialogs`' `DialogFramesTest`.
 */
class AppDialogFramesTest {

    private class Frames {
        val opened = mutableListOf<DialogFrameSpec>()
        var dismissed = 0
        val frame: DialogFrame = { spec, content ->
            if (opened.none { it.title == spec.title }) opened += spec
            Box(Modifier.size(1000.dp, 1400.dp)) { content() }
        }
        val dismiss: () -> Unit = { dismissed++ }
        fun only(): DialogFrameSpec = opened.single()
    }

    /** Opens [dialog], checks the window it asked for is titled [title], then closes that window. */
    private fun opens(title: String, resizable: Boolean, dialog: @Composable (Frames) -> Unit) =
        runComposeUiTest {
            val frames = Frames()
            setContent { MaterialTheme { dialog(frames) } }
            waitForIdle()
            val spec = frames.only()
            assertEquals(title, spec.title)
            assertEquals(resizable, spec.resizable)
            spec.onClose()
            waitForIdle()
            assertEquals(1, frames.dismissed, "closing the window dismisses the dialog")
        }

    @Test
    fun `the share your story dialog`() = opens("Share Your Story", resizable = false) { f ->
        ShareYourStoryDialog(true, {}, f.dismiss, frame = f.frame)
    }

    @Test
    fun `the options dialog`() = opens("Options", resizable = true) { f ->
        OptionsDialog(
            isVisible = true,
            theme = ThemeMode.LIGHT,
            settingsManager = SettingsManager(),
            companionServer = CompanionServer(shutdownGraceMs = 0),
            remoteClientManager = RemoteClientManager(),
            onDismiss = f.dismiss,
            initialSettings = AppSettings(),
            frame = f.frame,
        )
    }

    @Test
    fun `a hidden share your story dialog asks for no window`() = runComposeUiTest {
        val frames = Frames()
        setContent { MaterialTheme { ShareYourStoryDialog(false, {}, frames.dismiss, frame = frames.frame) } }
        waitForIdle()
        assertTrue(frames.opened.isEmpty())
    }
}
