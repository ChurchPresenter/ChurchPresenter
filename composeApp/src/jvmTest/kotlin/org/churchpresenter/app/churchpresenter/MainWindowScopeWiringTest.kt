@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.churchpresenter.calendar.ServiceAutoLoader
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.controlin.ControlMapping
import org.churchpresenter.controlin.Trigger
import org.churchpresenter.controlin.TriggerKinds
import org.churchpresenter.liveoutput.clearFromOperator
import org.churchpresenter.schedule.ActionChoices
import org.churchpresenter.server.DrawingFrame
import org.churchpresenter.server.PendingDrawingRequest
import org.churchpresenter.server.RemoteEventType
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.server.TunnelStatus
import org.churchpresenter.settings.CalendarSyncSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.theme.ThemeCustomization
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MainWindowScopeWiringTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var root: AppRootState
    private lateinit var win: MainWindowState
    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        dir = Files.createTempDirectory("cp-main-window-scope").toFile()
        root = AppRootState(
            object : ApplicationScope {
                override fun exitApplication() = Unit
            },
            scope,
            secondaryDisplays = { emptyList() },
        )
        win = MainWindowState(calendarSyncInitiallyOn = false)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun windowScope(): MainWindowScope {
        val cueHost = root.calendarCueHost()
        return MainWindowScope(
            root = root,
            win = win,
            frame = object : FrameWindowScope {
                override val window: ComposeWindow get() = error("no window in a headless test")
            },
            bannerModifier = Modifier,
            calendarSync = CalendarSyncService(dir, null, { CalendarSyncSettings() }, {}),
            effectiveAppSettings = root.appSettings,
            tunnelStatus = TunnelStatus.Idle,
            tunnelUrl = null,
            onThemeCustomizationChange = { _: ThemeCustomization -> },
            calendarFolder = dir,
            cueHost = cueHost,
            serviceAutoLoader = ServiceAutoLoader(document = { CalendarDocument() }, host = cueHost),
        )
    }

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
    }.toByteArray()

    private fun ask(client: String, title: String = "Sketch"): PendingDrawingRequest {
        val (request, _) = root.companionServer.drawings.ask(DrawingFrame(client, png(), "#FFFFFF", title))
        root.companionServer.onDrawingRequest.tryEmit(request)
        return request
    }

    @Test
    fun `a drawing from an unknown device waits for the operator, and Allow puts it live`() = runComposeUiTest {
        setContent { MaterialTheme { windowScope().RemoteDrawingRequests() } }
        waitForIdle()

        val request = ask("stranger")
        waitUntil(timeoutMillis = 5_000) { win.remoteEventQueue.isNotEmpty() }
        val (event, allow, _) = win.remoteEventQueue.single()
        assertEquals(RemoteEventType.PROJECT, event.type)
        assertEquals("Sketch", event.title)

        allow()
        waitUntil(timeoutMillis = 5_000) { request.decision.isCompleted }
        assertTrue(request.decision.let { runBlocking { it.await() } })
        waitUntil(timeoutMillis = 5_000) { root.presenterManager.slideContent.value == Presenting.DRAWING }
        allow()

        root.companionServer.drawingFrames.value = DrawingFrame("stranger", png(), "#000000", "Sketch")
        waitForIdle()
        root.companionServer.onDrawingEnd.tryEmit("stranger")
        waitUntil(timeoutMillis = 5_000) { root.presenterManager.clearDisplayRequested.value }
        waitUntil(timeoutMillis = 5_000) { win.remoteEventQueue.isEmpty() }
    }

    @Test
    fun `a trusted device's drawing goes up at once, and a blocked one's is refused`() = runComposeUiTest {
        win.sessionAllowedClients += "booth-ipad"
        win.sessionBlockedClients += "blocked"
        setContent { MaterialTheme { windowScope().RemoteDrawingRequests() } }
        waitForIdle()

        val trusted = ask("booth-ipad", title = " ")
        waitUntil(timeoutMillis = 5_000) { trusted.decision.isCompleted }
        assertTrue(trusted.decision.let { runBlocking { it.await() } })
        waitUntil(timeoutMillis = 5_000) { win.remoteActivityNotifications.isNotEmpty() }
        assertEquals("Drawing", win.remoteActivityNotifications.single().title)

        val blocked = ask("blocked")
        waitUntil(timeoutMillis = 5_000) { blocked.decision.isCompleted }
        assertFalse(blocked.decision.let { runBlocking { it.await() } })
        assertTrue(win.remoteEventQueue.isEmpty())
    }

    @Test
    fun `the window's show control and control effects run against the root`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                with(windowScope()) {
                    ShowControlEffects()
                    ControlInEffects()
                }
            }
        }
        waitForIdle()

        root.showRunner.run(listOf(Action.Wait(60.0)), "row")
        assertTrue(root.showRunner.isRunning("row"))
        root.presenterManager.clearFromOperator()
        waitUntil(timeoutMillis = 5_000) { !root.showRunner.isRunning("row") }
    }

    @Test
    fun `nothing on air before or after sends no show event`() {
        assertEquals(emptyList(), liveOutputEvents(emptySet(), emptySet()))
    }

    @Test
    fun `a control mapping runs its actions under its own key`() {
        val mapping = ControlMapping(
            id = "pad1",
            trigger = Trigger(kind = TriggerKinds.MIDI_NOTE),
            actions = listOf(Action.Wait(60.0)),
        )
        root.runControlMapping(mapping)
        assertTrue(root.showRunner.isRunning("control:pad1"))
    }

    @Test
    fun `the row-action editor is offered the lower thirds found in the settings' folder`() = runComposeUiTest {
        File(dir, "Pastor.json").writeText("{}")
        root.appSettings = root.appSettings.copy(
            streamingSettings = root.appSettings.streamingSettings.copy(lowerThirdFolder = dir.absolutePath),
        )
        var choices: ActionChoices? = null
        setContent { MaterialTheme { choices = windowScope().rememberActionChoices() } }

        waitUntil(timeoutMillis = 5_000) { choices?.lowerThirds == listOf("Pastor") }
        assertNotNull(choices).onOpen()
    }
}
