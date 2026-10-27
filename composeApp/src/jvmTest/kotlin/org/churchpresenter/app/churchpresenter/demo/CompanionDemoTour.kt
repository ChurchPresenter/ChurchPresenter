@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import java.net.ServerSocket
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
import org.churchpresenter.companionsatellite.CompanionConnectionStatus
import org.churchpresenter.companionsurface.CompanionSatelliteSettingsTab
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.serverui.ServerSettingsTab
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import kotlin.math.abs
import kotlin.test.Test

/** Bitfocus Companion both ways: a Stream Deck driving the app, and Companion's buttons inside it. */
class CompanionDemoTour {
    @Test
    fun companion() {
        DemoCompanion().use { companion ->
            recordTour(
                "companion",
                tab = "Companion and Stream Deck",
                liveLabel = "From the buttons",
                steps = 6,
                heading = "" to "",
                settings = { s -> demoSettings(s, companion.port) },
                companion = surfaces,
                panel = { settings, change ->
                    latest = settings
                    when (panel.value) {
                        "server" -> Box(Modifier.size(SERVER_W.dp, SERVER_H.dp)) {
                            ServerSettingsTab(settings, change, server, RemoteClientManager())
                        }
                        "steps" -> Box(Modifier.size(CARD_W.dp, STEPS_H.dp)) { CompanionStepsCard() }
                        else -> Box(Modifier.size(SATELLITE_W.dp, SATELLITE_H.dp)) {
                            CompanionSatelliteSettingsTab(settings, change, surfaces)
                        }
                    }
                },
            ) { companionTour() }
            server.stop()
            surfaces.dispose()
        }
    }

    private val server = CompanionServer(shutdownGraceMs = 0)
    private val surfaces = CompanionSatelliteViewModel()
    private val panel = mutableStateOf("server")
    private var latest: AppSettings? = null

    private fun DemoTour.companionTour() {
        say("intro")
        intro("Your Stream Deck and ChurchPresenter, working together.")
        verseLive()
        say("what")
        caption("Bitfocus Companion", "The free app behind the Stream Deck")
        say("two-ways")
        caption("Two ways", "The Stream Deck runs ChurchPresenter, and ChurchPresenter shows its buttons")
        streamDeckSide()
        satelliteSide()
        say("outro")
        outro()
    }

    /** John 3:16 on the screen, off camera, so the Stream Deck's Clear has something to clear. */
    private fun DemoTour.verseLive() {
        runCatching {
            val search = searchField()
            search.node.performTextReplacement("John 3:16")
            search.node.performKeyInput { pressKey(Key.Enter) }
            ui.waitForIdle()
            goLiveButton().node.performClick()
            ui.waitForIdle()
            settle()
        }
        pause(0.2)
    }

    /** Settings → Server: a trigger for each lower third, to paste into a Companion button. */
    private fun DemoTour.streamDeckSide() {
        server.start(SERVER_PORT, SERVER_HOST)
        waitFor("the server is running", SERVER_WAIT_MS) { server.isRunning.value }
        say("server")
        note("Settings → Server", "The Stream Deck side", settingsButton())
        panel.value = "server"
        openPanel("Settings — Server", SERVER_W, SERVER_H)
        say("server-on")
        point("Companion server", "Lets Companion reach ChurchPresenter", hasText("Companion Server"))
        say("triggers")
        panelNote(
            "Lower third triggers",
            "A trigger for each one",
            panelReveal(hasText("Lower third triggers")),
            numbered = true,
        )
        say("takedown")
        point("Hide Lower Third", "And Clear Display beside it", hasText("Hide Lower Third"))
        say("go-live")
        val welcome = seek(hasText("Welcome")).bounds
        val goLive = panelItems(hasText("Go Live")).minBy { abs(it.bounds.center.y - welcome.center.y) }
        // Not clicked: a copy shows nothing, and a click in the panel takes the main window's keyboard,
        // which it reports in a red bar.
        panelNote("Go Live", "Copies Welcome's trigger", goLive, numbered = true)
        say("plus-key")
        val plusKey = panelItems(hasText("+ Key")).minBy { abs(it.bounds.center.y - welcome.center.y) }
        panelNote("+ Key", "Also turns on the ATEM key", plusKey)
        closePanel()
        companionSteps()
    }

    /** What is done in Companion with the copied trigger, step by step. */
    private fun DemoTour.companionSteps() {
        panel.value = "steps"
        openPanel("In Companion", CARD_W, STEPS_H)
        say("companion-connection")
        panelNote("Add a connection", "Generic: HTTP Requests", panelItem(hasText(STEP_CONNECTION)), numbered = true)
        say("companion-button")
        panelNote("Pick a button", "Its action: HTTP POST", panelItem(hasText(STEP_BUTTON)))
        say("companion-paste")
        panelNote("Paste it", "Into the URL box", panelItem(hasText(STEP_PASTE)))
        say("press")
        panelNote("Press it", "Welcome goes on the screen", panelItem(hasText(STEP_PRESS)))
        closePanel()
    }

    /** Settings → Companion Satellite, then the buttons in their own tab and in the sidebar. */
    private fun DemoTour.satelliteSide() {
        say("satellite")
        note("Settings → Companion Satellite", "The other way around", settingsButton())
        panel.value = "satellite"
        openPanel("Settings — Companion Satellite", SATELLITE_W, SATELLITE_H)
        say("host")
        panelEdit("Host", "The computer that runs Companion", fieldBeside("Host"), "127.0.0.1")
        // Rows and columns are shown once a placement is ticked.
        quietPanelClick(hasText("Tab"))
        say("grid")
        panelEdit("Rows and columns", "To match your Stream Deck", placementFields("Tab")[0], "2")
        placementFields("Tab")[1].node.performTextReplacement("4")
        ui.waitForIdle()
        pause(0.3)
        say("show-in")
        point("Show in", "Its own tab, or a sidebar", hasText("Show in"))
        say("connect")
        val connect = seek(hasText("Connect"))
        panelNote("Connect", "Companion sees one more Stream Deck", connect, click = true, numbered = true)
        waitFor("Companion to answer", SERVER_WAIT_MS) {
            surfaces.connectionStates.values.any { it.status == CompanionConnectionStatus.CONNECTED }
        }
        pause(0.4)
        closePanel()
        say("companion-tab")
        openTab("Companion", "Your Stream Deck's buttons")
        waitFor("the buttons to arrive") { hasNode(hasContentDescription("Clear")) }
        pause(0.6)
        say("click")
        val clear = find(hasContentDescription("Clear"))
        step("Clear", "Just as the real button does", clear, live = true) {
            clear.node.performClick()
            ui.waitForIdle()
            // The real button's action is Companion's, sent back to the app; here the app clears as it would.
            runCatching { find(hasContentDescription("Clear Display")).node.performClick() }
            ui.waitForIdle()
            settle()
        }
        sidebar()
        say("page")
        caption("Which page", "Set in Companion, under Surfaces")
    }

    /** The same connection in the right sidebar: ticked and sized on the settings page, then shown. */
    private fun DemoTour.sidebar() {
        say("sidebar-open")
        note("Settings → Companion Satellite", "Once more", settingsButton())
        openPanel("Settings — Companion Satellite", SATELLITE_W, SATELLITE_H)
        say("sidebar-tick")
        val right = seek(hasText("Right sidebar"))
        panelNote("Right sidebar", "Or the left, or both", right, click = true, numbered = true)
        say("sidebar-grid")
        panelEdit("A smaller grid", "Two rows of four", placementFields("Right sidebar")[0], "2")
        placementFields("Right sidebar")[1].node.performTextReplacement("4")
        ui.waitForIdle()
        pause(0.3)
        // The app connects a placement as it is ticked; the recording has no wiring to do it.
        latest?.companionSatelliteConnections?.firstOrNull()?.let { surfaces.connectAll(it) }
        closePanel()
        waitFor("the sidebar's buttons") { findAll(hasContentDescription("Walk-in")).size > 1 }
        pause(0.4)
        say("sidebar")
        val walkIn = findAll(hasContentDescription("Walk-in")).maxBy { it.bounds.left }
        note("In the sidebar", "Right beside the live preview", walkIn)
    }

    /** The gear at the end of the tab bar, which opens Settings. */
    private fun DemoTour.settingsButton() =
        find(hasContentDescription("Settings", substring = true)) { it.top < dp(TOP_BAR_DP) }

    /** The text field on [label]'s line, right of it. */
    private fun DemoTour.fieldBeside(label: String): Target {
        val at = panelItems(hasText(label)).first().bounds
        return panelItems(hasSetTextAction())
            .filter { abs(it.bounds.center.y - at.center.y) < at.height && it.bounds.left > at.left }
            .minBy { it.bounds.left }
    }

    /** A placement's rows and columns fields, left to right, on the line of its checkbox [label]. */
    private fun DemoTour.placementFields(label: String): List<Target> {
        val at = panelItems(hasText(label)).first().bounds
        return panelItems(hasSetTextAction())
            .filter { abs(it.bounds.center.y - at.center.y) < at.height * 2 && it.bounds.left > at.right }
            .sortedBy { it.bounds.left }
    }
}

private fun demoSettings(s: AppSettings, port: Int): AppSettings = s.copy(
    // Room in the tab bar for the Companion tab, which appears once a connection is shown in it.
    hiddenTabs = s.hiddenTabs + setOf("WEB", "CANVAS", "DICTIONARY", "CROSSWORD"),
    serverSettings = s.serverSettings.copy(port = SERVER_PORT, serverHost = SERVER_HOST),
    companionSatelliteConnections = listOf(
        CompanionSatelliteSettings(id = "demo", deviceId = "churchpresenter-booth", port = port),
    ),
)

/** The server's usual port, so the addresses look as they will at church; another when it is taken. */
private val SERVER_PORT: Int = runCatching { ServerSocket(DEFAULT_SERVER_PORT).use { it.localPort } }
    .getOrDefault(FALLBACK_SERVER_PORT)
private const val DEFAULT_SERVER_PORT = 8765
private const val FALLBACK_SERVER_PORT = 48_767
private const val TOP_BAR_DP = 60f
private const val SERVER_HOST = "192.168.1.42"
private const val SERVER_WAIT_MS = 15_000L
private const val SERVER_W = 600f
private const val SERVER_H = 800f
private const val SATELLITE_W = 1200f
private const val SATELLITE_H = 720f

/** What is done in Companion, as four numbered lines. */
@Composable
private fun CompanionStepsCard() {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        listOf(STEP_CONNECTION, STEP_BUTTON, STEP_PASTE, STEP_PRESS).forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
                Text(step, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private const val STEP_CONNECTION = "Connections: add Generic: HTTP Requests"
private const val STEP_BUTTON = "Pick a button, and add the action HTTP POST"
private const val STEP_PASTE = "Paste what you copied into its URL box"
private const val STEP_PRESS = "Press the button: Welcome goes on the screen"
private const val CARD_W = 1000f
private const val STEPS_H = 400f
