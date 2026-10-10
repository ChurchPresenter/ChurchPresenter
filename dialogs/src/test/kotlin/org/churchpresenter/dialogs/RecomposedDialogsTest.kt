@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import org.churchpresenter.controlin.ControlSettings
import org.churchpresenter.server.RemoteEvent
import org.churchpresenter.server.RemoteEventType
import org.churchpresenter.server.TunnelStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class RecomposedDialogsTest {

    private val titles = mutableListOf<String>()

    private val inPlace: DialogFrame = { spec, content ->
        titles += spec.title
        Box(Modifier.size(900.dp, 1100.dp)) { content() }
    }

    /**
     * Draws [dialog], then recomposes around it three ways: its host again with nothing changed, the
     * main window it centers on replaced, and its own inputs changed ([changed] turns true).
     */
    private fun recomposes(title: String, dialog: @Composable (changed: Boolean, pass: Int) -> Unit) =
        runComposeUiTest {
            var pass by mutableStateOf(0)
            var window by mutableStateOf(WindowState(size = DpSize(1200.dp, 800.dp)))
            var changed by mutableStateOf(false)
            setContent {
                CompositionLocalProvider(LocalMainWindowState provides window) {
                    MaterialTheme { dialog(changed, pass) }
                }
            }
            waitForIdle()
            pass++
            waitForIdle()
            window = WindowState(size = DpSize(1600.dp, 1000.dp))
            waitForIdle()
            changed = true
            waitForIdle()
            assertEquals(title, titles.last())
        }

    @Test
    fun `the message dialog`() = recomposes("Message") { changed, _ ->
        MessageDialog(true, emptyList(), {}, null, {}, {}, if (changed) ({}) else DISMISS, frame = inPlace)
    }

    @Test
    fun `the props dialog`() = recomposes("Props") { changed, _ ->
        PropsDialog(true, emptyList(), {}, if (changed) setOf("p") else emptySet(), { _, _ -> }, { null }, {}, inPlace)
    }

    @Test
    fun `the macros dialog`() = recomposes("Macros") { changed, _ ->
        MacrosDialog(true, emptyList(), emptyList(), {}, {}, {}, if (changed) ({}) else null, inPlace)
    }

    @Test
    fun `the clear groups dialog`() = recomposes("Clear groups") { changed, _ ->
        ClearGroupsDialog(true, emptyList(), {}, {}, if (changed) ({}) else DISMISS, inPlace)
    }

    @Test
    fun `the control dialog`() = recomposes("MIDI & OSC") { changed, _ ->
        ControlDialog(
            true, ControlSettings(oscInPort = if (changed) 9000 else 0), ControlPanelData(), ControlPanelActions(),
            {}, inPlace,
        )
    }

    @Test
    fun `the add label dialog`() = recomposes("Add Label") { changed, _ ->
        AddLabelDialog(true, {}, { _, _, _ -> }, existingText = if (changed) "Live" else "", frame = inPlace)
    }

    @Test
    fun `the STT settings dialog`() = recomposes("STT Display Settings") { changed, _ ->
        STTSettingsDialog(if (changed) AppSettings(theme = "DARK") else AppSettings(), {}, {}, inPlace)
    }

    @Test
    fun `the keyboard shortcuts dialog`() = recomposes("Keyboard Shortcuts") { changed, _ ->
        KeyboardShortcutsDialog(true, AppSettings(), {}, devMode = changed, onDismiss = {}, frame = inPlace)
    }

    @Test
    fun `the customize theme dialog`() = recomposes("Customize Theme") { changed, _ ->
        CustomizeThemeDialog(
            true, if (changed) ThemeMode.DARK else ThemeMode.LIGHT, defaultChoice(false), {}, {}, inPlace,
        )
    }

    @Test
    fun `the remote event dialog`() = recomposes("Remote API Request (3 pending)") { changed, _ ->
        RemoteEventDialog(
            event = RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "Amazing Grace"),
            queueSize = if (changed) 3 else 1,
            onAllow = {}, onAllowForSession = {}, onAllowPermanently = {},
            onBlockForSession = {}, onBlockPermanently = {}, onDeny = {},
            frame = inPlace,
        )
    }

    @Test
    fun `the presentation remote dialog`() = recomposes("Remote Control") { changed, _ ->
        PresentationRemoteDialog(
            settings = AppSettings(), onSettingsChange = {}, serverUrl = SERVER,
            apiKeyEnabled = changed, apiKey = "key", tunnelStatus = TunnelStatus.Idle, tunnelUrl = "",
            presentationDisplayUrl = "", onPresentationDisplayUrlChanged = {},
            onStartTunnel = {}, onStopTunnel = {}, onDismiss = {}, frame = inPlace,
        )
    }

    @Test
    fun `the Q and A remote dialog`() = recomposes("Sharing & Remote Access") { changed, _ ->
        QARemoteDialog(
            serverUrl = SERVER, qaDisplayUrl = "", onQaDisplayUrlChanged = {},
            apiKeyEnabled = changed, apiKey = "key", tunnelStatus = TunnelStatus.Idle, tunnelUrl = "",
            onStartTunnel = {}, onStopTunnel = {}, qaSettings = QASettings(), onSettingsChange = {},
            onDismiss = {}, frame = inPlace,
        )
    }

    @Test
    fun `the about dialog`() = recomposes("About Church Presenter") { changed, _ ->
        AboutDialog(true, {}, AppSettings(), TEST_IDENTITY, if (changed) ThemeMode.DARK else ThemeMode.LIGHT, inPlace)
    }

    @Test
    fun `the contact dialog`() = recomposes("Contact Us") { changed, _ ->
        ContactUsDialog(
            true, {}, initialTypeKey = if (changed) "bugReport" else null, identity = TEST_IDENTITY, frame = inPlace,
            submit = { _, _, _, _, _ -> SendStatus.Idle },
        )
    }

    @Test
    fun `the memory monitor opens a tool window of its declared size, and closing it closes the monitor`() =
        runComposeUiTest {
            var spec: ToolWindowSpec? = null
            var closed = 0
            var theme by mutableStateOf(ThemeMode.LIGHT)
            setContent {
                MemoryMonitorWindow(true, theme, { closed++ }) { asked, content ->
                    spec = asked
                    Box(Modifier.size(asked.size)) { content() }
                }
            }
            waitForIdle()
            theme = ThemeMode.DARK
            waitForIdle()
            val opened = checkNotNull(spec)
            assertEquals("Memory Monitor", opened.title)
            assertEquals(DpSize(MEMORY_MONITOR_WINDOW_WIDTH, MEMORY_MONITOR_WINDOW_HEIGHT), opened.size)
            opened.onClose()
            assertEquals(1, closed)
        }

    @Test
    fun `a dialog's window opens at the size it declares`() = runComposeUiTest {
        var spec: DialogFrameSpec? = null
        setContent {
            AboutDialog(true, {}, AppSettings(), TEST_IDENTITY, ThemeMode.LIGHT) { asked, content ->
                spec = asked
                Box(Modifier.size(900.dp, 1100.dp)) { content() }
            }
        }
        waitForIdle()
        assertEquals(DpSize(ABOUT_DIALOG_WIDTH, ABOUT_DIALOG_HEIGHT), checkNotNull(spec).state.size)
    }

    private companion object {
        const val SERVER = "http://192.168.1.2:8080"
        val DISMISS: () -> Unit = {}
    }
}
