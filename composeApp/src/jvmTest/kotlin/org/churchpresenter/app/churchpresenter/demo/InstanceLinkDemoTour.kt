@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.server.LiveStateDto
import org.churchpresenter.serverui.InstanceLinkDialogContent
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.serverui.ServerSettingsTab
import java.net.ServerSocket
import kotlin.math.abs
import kotlin.test.Test

/** Connect → Connect to Instance: a second computer following the main room's screens. */
class InstanceLinkDemoTour {
    @Test
    fun instanceLink() = try {
        record()
    } finally {
        server.stop()
    }

    private val server = CompanionServer(shutdownGraceMs = 0)
    private val showing = mutableStateOf("server")

    private fun record() = recordTour(
        "instance-link",
        tab = "Instance Link",
        liveLabel = "The overflow room",
        steps = 6,
        heading = "" to "",
        settings = { s -> s.copy(serverSettings = s.serverSettings.copy(port = SERVER_PORT, serverHost = HOST)) },
        panel = { settings, change ->
            if (showing.value == "server") {
                Box(Modifier.size(SERVER_W.dp, SERVER_H.dp)) {
                    ServerSettingsTab(settings, change, server, RemoteClientManager())
                }
                return@recordTour
            }
            Box(Modifier.size(PANEL_W.dp, PANEL_H.dp)) {
                InstanceLinkDialogContent(
                    isVisible = true,
                    settings = settings.instanceLink,
                    connectionStatus = status.value,
                    remoteLiveState = live.value,
                    remoteScheduleCount = if (status.value == InstanceLinkStatus.CONNECTED) SCHEDULE_COUNT else 0,
                    // A recording runs slower than the clock: the last update is always "just now".
                    lastMessageAtMs = lastMessage.value?.let { System.currentTimeMillis() },
                    // The stand-in main computer answers at once, with John 3:16 live.
                    onConnect = {
                        status.value = InstanceLinkStatus.CONNECTED
                        live.value = JOHN_3_16
                        lastMessage.value = System.currentTimeMillis()
                    },
                    onSave = {},
                    onDisconnect = { status.value = InstanceLinkStatus.DISCONNECTED },
                    onDismiss = {},
                )
            }
        },
    ) { instanceLinkTour() }

    private fun DemoTour.instanceLinkTour() {
        say("intro")
        intro("One person runs the service, and every room follows along.")
        say("why")
        caption("For every other room", "Overflow, fellowship hall, a second campus")
        mainComputer()
        say("open")
        caption("On the second computer", "Connect → Connect to Instance…")
        showing.value = "link"
        openPanel("Instance Link", PANEL_W, PANEL_H)
        say("host")
        panelEdit("Host", "The main computer's address", fieldBeside("Host"), HOST)
        runCatching { fieldBeside("Port").node.performTextReplacement(SERVER_PORT.toString()) }
        giveKeyboardBack()
        say("api-key")
        point("API Key", "The same key as the main computer", hasText("API Key"))
        say("role")
        point("Controlled", "Shows what the main computer shows", hasText("Controlled", substring = true))
        say("controller")
        point("Controller", "Drives the main computer's screens", hasText("Controller —", substring = true))
        say("layers")
        point("Follow these layers", "Slides, media, lower thirds and more", hasText("Follow these layers"))
        say("bible")
        point("Bible sync", "The same Bible, or just the same verse", hasText("Bible sync"))
        say("push")
        val push = seek(hasText("Allow adding items", substring = true))
        panelNote("Adding to the schedule", "From the second room", push, click = true, numbered = true)
        say("connect")
        panelNote("Connect", "Starts following", seek(hasText("Connect")), click = true, numbered = true)
        pause(0.4)
        say("connected")
        point("Connected", "What's live there, and its schedule", hasText("Primary schedule", substring = true))
        closePanel()
        mirrorVerse()
        say("live")
        showLive("It's live here too", "John 3:16, from the main room")
        say("autoconnect")
        openPanel("Instance Link", PANEL_W, PANEL_H)
        val auto = seek(hasText("Connect automatically on launch"))
        panelNote("Connect automatically", "Every week, by itself", auto, click = true, numbered = true)
        closePanel()
        say("outro")
        outro()
    }

    /** Settings → Server on the main computer: the companion server switched on, and its address. */
    private fun DemoTour.mainComputer() {
        say("primary")
        note("On the main computer", "Settings → Server", settingsButton())
        showing.value = "server"
        openPanel("Settings — Server", SERVER_W, SERVER_H)
        say("server-on")
        val switch = seek(hasContentDescription("Enable Server"))
        panelNote("Enable Server", "Turns the companion server on", switch, click = true, numbered = true)
        waitFor("the server to start", SERVER_WAIT_MS) { server.isRunning.value }
        pause(0.4)
        say("address")
        point("Its address", "Typed on the second computer", hasText("http://$HOST", substring = true))
        closePanel()
    }

    private fun DemoTour.settingsButton() =
        find(hasContentDescription("Settings", substring = true)) { it.top < dp(TOP_BAR_DP) }

    /** What the link puts on this computer's screen when the main room goes live: John 3:16. */
    private fun DemoTour.mirrorVerse() {
        runCatching {
            val search = searchField()
            search.node.performTextReplacement("John 3:16")
            search.node.performKeyInput { pressKey(Key.Enter) }
            ui.waitForIdle()
            goLiveButton().node.performClick()
            ui.waitForIdle()
            settle()
        }
        giveKeyboardBack()
        pause(0.2)
    }

    /** The text field on [label]'s line, right of it. */
    private fun DemoTour.fieldBeside(label: String): Target {
        val at = panelItems(hasText(label)).first().bounds
        return panelItems(hasSetTextAction())
            .filter { abs(it.bounds.center.y - at.center.y) < at.height * 2 && it.bounds.left > at.left }
            .minBy { it.bounds.left }
    }
}

private val status = mutableStateOf(InstanceLinkStatus.DISCONNECTED)
private val live = mutableStateOf<LiveStateDto?>(null)
private val lastMessage = mutableStateOf<Long?>(null)

private val JOHN_3_16 = LiveStateDto(contentType = "BIBLE", bookName = "John", chapter = 3, verseNumber = 16)

private const val HOST = "192.168.1.42"

/** The server's usual port, so the address looks as it will at church; another when it is taken. */
private val SERVER_PORT: Int = runCatching { ServerSocket(DEFAULT_SERVER_PORT).use { it.localPort } }
    .getOrDefault(FALLBACK_SERVER_PORT)
private const val DEFAULT_SERVER_PORT = 8765
private const val FALLBACK_SERVER_PORT = 48_768
private const val SERVER_WAIT_MS = 15_000L
private const val SERVER_W = 600f
private const val SERVER_H = 800f
private const val TOP_BAR_DP = 60f
private const val SCHEDULE_COUNT = 7
private const val PANEL_W = 760f
private const val PANEL_H = 820f
