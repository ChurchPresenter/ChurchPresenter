@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.controlin.ControlSettings
import org.churchpresenter.controlin.ControlStatus
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.RemoteEvent
import org.churchpresenter.server.RemoteEventType
import org.churchpresenter.server.TunnelStatus
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every dialog opened through the frame it is given: the window it asks for, and that closing that
 * window runs the dialog's own dismiss. The frame here draws the content in place and records what
 * was asked for -- a headless test cannot open the real `DialogWindow` -- and the dialogs' bodies
 * have suites of their own; this is the part around them.
 */
class DialogFramesTest {

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
    private fun opens(title: String, resizable: Boolean? = null, dialog: @Composable (Frames) -> Unit) =
        runComposeUiTest {
            val frames = Frames()
            setContent { MaterialTheme { dialog(frames) } }
            waitForIdle()
            val spec = frames.only()
            assertEquals(title, spec.title)
            resizable?.let { assertEquals(it, spec.resizable) }
            spec.onClose()
            waitForIdle()
            assertEquals(1, frames.dismissed, "closing the window dismisses the dialog")
        }

    /** A dialog given isVisible = false asks for no window at all. */
    private fun ComposeUiTest.asksForNothing(dialog: @Composable (Frames) -> Unit) {
        val frames = Frames()
        setContent { MaterialTheme { dialog(frames) } }
        waitForIdle()
        assertTrue(frames.opened.isEmpty())
    }

    @Test
    fun `the message dialog`() = opens("Message") { f ->
        MessageDialog(true, emptyList(), {}, null, {}, {}, f.dismiss, frame = f.frame)
    }

    @Test
    fun `the props dialog`() = opens("Props") { f ->
        PropsDialog(true, emptyList(), {}, emptySet(), { _, _ -> }, { null }, f.dismiss, frame = f.frame)
    }

    @Test
    fun `the macros dialog`() = opens("Macros") { f ->
        MacrosDialog(true, emptyList(), emptyList(), {}, {}, f.dismiss, frame = f.frame)
    }

    @Test
    fun `the clear groups dialog`() = opens("Clear groups") { f ->
        ClearGroupsDialog(true, emptyList(), {}, {}, f.dismiss, frame = f.frame)
    }

    @Test
    fun `the control dialog stops listening for a trigger as it closes`() {
        var cancelled = 0
        opens("MIDI & OSC") { f ->
            ControlDialog(
                isVisible = true,
                settings = ControlSettings(),
                data = ControlPanelData(ControlStatus()),
                actions = ControlPanelActions(onCancelLearn = { cancelled++ }),
                onDismiss = f.dismiss,
                frame = f.frame,
            )
        }
        assertEquals(1, cancelled)
    }

    @Test
    fun `the add label dialog, which does not resize`() = opens("Add Label", resizable = false) { f ->
        AddLabelDialog(true, f.dismiss, { _, _, _ -> }, frame = f.frame)
    }

    @Test
    fun `the STT settings dialog`() = opens("STT Display Settings", resizable = false) { f ->
        STTSettingsDialog(AppSettings(), {}, f.dismiss, frame = f.frame)
    }

    @Test
    fun `the share your story dialog`() = opens("Share Your Story", resizable = false) { f ->
        ShareYourStoryDialog(true, {}, f.dismiss, frame = f.frame)
    }

    @Test
    fun `the keyboard shortcuts dialog, which resizes`() = opens("Keyboard Shortcuts", resizable = true) { f ->
        KeyboardShortcutsDialog(true, AppSettings(), {}, onDismiss = f.dismiss, frame = f.frame)
    }

    @Test
    fun `the customize theme dialog`() = opens("Customize Theme") { f ->
        CustomizeThemeDialog(
            isVisible = true,
            currentTheme = ThemeMode.LIGHT,
            initial = ThemeCustomizationChoice(
                useCustomColors = false, accentHex = "#3F7FBF", dark = false, fontFamily = "", fontScale = 1f,
            ),
            onApply = {},
            onDismiss = f.dismiss,
            frame = f.frame,
        )
    }

    @Test
    fun `a remote request's window stays on top, and closing it denies the request`() = runComposeUiTest {
        val frames = Frames()
        setContent {
            MaterialTheme {
                RemoteEventDialog(
                    event = RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "Amazing Grace"),
                    onAllow = {}, onAllowForSession = {}, onAllowPermanently = {},
                    onBlockForSession = {}, onBlockPermanently = {},
                    onDeny = frames.dismiss,
                    frame = frames.frame,
                )
            }
        }
        waitForIdle()
        val spec = frames.only()
        assertTrue(spec.alwaysOnTop && !spec.resizable)
        spec.onClose()
        assertEquals(1, frames.dismissed)
    }

    @Test
    fun `the presentation remote dialog`() = opens("Remote Control", resizable = false) { f ->
        PresentationRemoteDialog(
            settings = AppSettings(), onSettingsChange = {}, serverUrl = "http://192.168.1.2:8080",
            apiKeyEnabled = false, apiKey = "", tunnelStatus = TunnelStatus.Idle, tunnelUrl = "",
            presentationDisplayUrl = "", onPresentationDisplayUrlChanged = {},
            onStartTunnel = {}, onStopTunnel = {}, onDismiss = f.dismiss, frame = f.frame,
        )
    }

    @Test
    fun `the Q and A remote dialog`() = opens("Sharing & Remote Access", resizable = false) { f ->
        QARemoteDialog(
            serverUrl = "http://192.168.1.2:8080", qaDisplayUrl = "", onQaDisplayUrlChanged = {},
            apiKeyEnabled = false, apiKey = "", tunnelStatus = TunnelStatus.Idle, tunnelUrl = "",
            onStartTunnel = {}, onStopTunnel = {}, qaSettings = QASettings(), onSettingsChange = {},
            onDismiss = f.dismiss, frame = f.frame,
        )
    }

    @Test
    fun `the about dialog`() = opens("About Church Presenter", resizable = false) { f ->
        AboutDialog(true, f.dismiss, AppSettings(), frame = f.frame)
    }

    @Test
    fun `the add website dialog`() = opens("Add Website", resizable = false) { f ->
        AddWebsiteDialog(f.dismiss, { _, _ -> }, frame = f.frame)
    }

    @Test
    fun `the options dialog`() = opens("Options", resizable = true) { f ->
        OptionsDialog(
            isVisible = true,
            theme = ThemeMode.LIGHT,
            settingsManager = SettingsManager(),
            companionServer = CompanionServer(),
            remoteClientManager = RemoteClientManager(),
            onDismiss = f.dismiss,
            initialSettings = AppSettings(),
            frame = f.frame,
        )
    }

    @Test
    fun `the contact dialog`() = opens("Contact Us", resizable = false) { f ->
        ContactUsDialog(true, f.dismiss, frame = f.frame, submit = { _, _, _, _, _ -> SendStatus.Idle })
    }

    @Test
    fun `a contact message that is sent closes the dialog after its confirmation`() = runComposeUiTest {
        val frames = Frames()
        var sent: List<String>? = null
        setContent {
            MaterialTheme {
                ContactUsDialog(true, frames.dismiss, initialTypeKey = "bugReport", frame = frames.frame,
                    submit = { type, name, _, message, _ ->
                        sent = listOf(type, name, message)
                        SendStatus.Sent
                    },
                )
            }
        }
        onAllNodes(hasSetTextAction())[0].performTextInput("A Church")
        onAllNodes(hasSetTextAction())[2].performTextInput("Something's broken")
        onNodeWithText("Send").performClick()
        waitUntil { sent != null }
        assertEquals(listOf("bugReport", "A Church", "Something's broken"), sent)
        mainClock.advanceTimeBy(2_000)
        waitUntil { frames.dismissed == 1 }
    }

    @Test
    fun `a contact message that fails says so and keeps the dialog open`() = runComposeUiTest {
        val frames = Frames()
        setContent {
            MaterialTheme {
                ContactUsDialog(true, frames.dismiss, frame = frames.frame,
                    submit = { _, _, _, _, texts -> SendStatus.Error(texts.network) },
                )
            }
        }
        onAllNodes(hasSetTextAction())[0].performTextInput("A Church")
        onAllNodes(hasSetTextAction())[2].performTextInput("Hello")
        onNodeWithText("Send").performClick()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        assertEquals(0, frames.dismissed)
    }

    @Test
    fun `a hidden dialog asks for no window`() = runComposeUiTest {
        asksForNothing { f ->
            MessageDialog(false, emptyList(), {}, null, {}, {}, f.dismiss, frame = f.frame)
            PropsDialog(false, emptyList(), {}, emptySet(), { _, _ -> }, { null }, f.dismiss, frame = f.frame)
            MacrosDialog(false, emptyList(), emptyList(), {}, {}, f.dismiss, frame = f.frame)
            ClearGroupsDialog(false, emptyList(), {}, {}, f.dismiss, frame = f.frame)
            AddLabelDialog(false, f.dismiss, { _, _, _ -> }, frame = f.frame)
            ShareYourStoryDialog(false, {}, f.dismiss, frame = f.frame)
            KeyboardShortcutsDialog(false, AppSettings(), {}, onDismiss = f.dismiss, frame = f.frame)
            AboutDialog(false, f.dismiss, AppSettings(), frame = f.frame)
            ContactUsDialog(false, f.dismiss, frame = f.frame)
            RemoteEventDialog(
                event = null, onAllow = {}, onAllowForSession = {}, onAllowPermanently = {},
                onBlockForSession = {}, onBlockPermanently = {}, onDeny = {}, frame = f.frame,
            )
        }
    }
}
