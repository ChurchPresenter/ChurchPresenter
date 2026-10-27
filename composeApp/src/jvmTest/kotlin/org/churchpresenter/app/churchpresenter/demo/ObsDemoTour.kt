@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.churchpresenter.obs.OBSSettingsTab
import org.churchpresenter.obs.OBSWebSocketManager
import org.churchpresenter.obs.obsSceneFor
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import kotlin.math.abs
import kotlin.test.Test

/** Settings → OBS: connected to OBS, a scene for each kind of content, and the switches as it goes live. */
class ObsDemoTour {
    @Test
    fun obs() {
        DemoObs(listOf(CAMERA, LYRICS, SCRIPTURE)).use { fake ->
            port = fake.boundPort
            try {
                recordTour(
                    "obs",
                    tab = "OBS",
                    liveLabel = "Sunday stream",
                    steps = 6,
                    heading = "" to "",
                    settings = { s -> s.copy(obsSettings = s.obsSettings.copy(port = fake.boundPort)) },
                    panel = { settings, change ->
                        latest = settings
                        Box(Modifier.size(PANEL_W.dp, PANEL_H.dp)) { OBSSettingsTab(settings, change, obs) }
                    },
                ) { obsTour(fake) }
            } finally {
                obs.disconnect()
            }
        }
    }

    private val obs = OBSWebSocketManager()
    private var latest: AppSettings? = null
    private var port = 0

    private fun DemoTour.obsTour(fake: DemoObs) {
        say("intro")
        intro("The right scene, switched for you.")
        say("why")
        caption("Lyrics, scripture, camera", "Switched as the content changes")
        say("obs-side")
        caption("In OBS", "Tools → WebSocket Server Settings: turn it on")
        say("settings")
        note("Settings → OBS", "Scene switching", settingsButton())
        openPanel("Settings — OBS", PANEL_W, PANEL_H)
        connect()
        mappings()
        closePanel()
        goingLive(fake)
        say("outro")
        outro()
    }

    private fun DemoTour.connect() {
        say("enable")
        val enable = seek(hasText("Connect to OBS Studio"))
        panelNote("Connect to OBS", "Turn it on", switchBeside(enable), click = true, numbered = true)
        say("host")
        point("Host", "localhost, or the OBS computer's address", hasText("Host"))
        say("password")
        point("Password", "The one OBS shows, or empty", hasText("Password"))
        say("connect")
        panelNote("Connect", "To OBS", seek(hasText("Connect")), click = true, numbered = true)
        waitFor("OBS to answer", OBS_WAIT_MS) { obs.status.value == OBSWebSocketManager.ConnectionStatus.CONNECTED }
        pause(0.4)
    }

    private fun DemoTour.mappings() {
        say("mappings")
        point("Scene Mappings", "Spelled as they are in OBS", hasText("Scene Mappings"))
        say("songs")
        panelEdit("Songs", "Your lyrics scene", fieldBeside("Songs"), LYRICS)
        say("bible")
        panelEdit("Bible", "Your scripture scene", fieldBeside("Bible"), SCRIPTURE)
        say("default")
        panelEdit("Default Scene", "Everything else", fieldBeside("Default Scene"), CAMERA)
    }

    /** A song, a verse and a clear, with OBS switched to each one's scene. */
    private fun DemoTour.goingLive(fake: DemoObs) {
        runCatching {
            quietClick(tab("Songs"))
            quietClick(text("Amazing Grace"))
            quietClick(text("Amazing grace! how sweet the sound"))
        }
        say("song-live")
        goLive(sub = "OBS switches to $LYRICS")
        switchedTo(fake, Presenting.LYRICS, LYRICS)
        runCatching {
            quietClick(tab("Bible"))
            val search = searchField()
            search.node.performTextReplacement("John 3:16")
            search.node.performKeyInput { pressKey(Key.Enter) }
            ui.waitForIdle()
        }
        giveKeyboardBack()
        say("bible-live")
        goLive(sub = "OBS switches to $SCRIPTURE")
        switchedTo(fake, Presenting.BIBLE, SCRIPTURE)
        say("clear")
        val clear = find(hasContentDescription("Clear Display"))
        step("Clear Display", "OBS goes back to $CAMERA", clear, live = true) {
            clear.node.performClick()
            ui.waitForIdle()
            settle()
        }
        giveKeyboardBack()
        switchedTo(fake, Presenting.NONE, CAMERA)
    }

    /**
     * What the app's own wiring does when [mode] goes live, which the recording does not run: OBS is
     * asked for that content's scene, and the stand-in must have received [scene].
     */
    private fun DemoTour.switchedTo(fake: DemoObs, mode: Presenting, scene: String) {
        latest?.let { s -> obsSceneFor(mode, s.obsSettings)?.let(obs::setScene) }
        waitFor("OBS to switch to $scene", OBS_WAIT_MS) { fake.scenes.lastOrNull() == scene }
    }

    /** The text field on [label]'s line, right of it. */
    private fun DemoTour.fieldBeside(label: String): Target {
        val at = seek(hasText(label)).bounds
        return panelItems(hasSetTextAction())
            .filter { abs(it.bounds.center.y - at.center.y) < at.height * 2 && it.bounds.left > at.left }
            .minBy { it.bounds.left }
    }

    /** The switch on [label]'s line: the toggle furthest right of it. */
    private fun DemoTour.switchBeside(label: Target): Target =
        panelItems(isToggleable())
            .filter { abs(it.bounds.center.y - label.bounds.center.y) < label.bounds.height * 2 }
            .maxByOrNull { it.bounds.left } ?: label

    private fun DemoTour.settingsButton() =
        find(hasContentDescription("Settings", substring = true)) { it.top < dp(TOP_BAR_DP) }
}

private const val LYRICS = "Lyrics"
private const val SCRIPTURE = "Scripture"
private const val CAMERA = "Camera"
private const val OBS_WAIT_MS = 15_000L
private const val TOP_BAR_DP = 60f
private const val PANEL_W = 1000f
private const val PANEL_H = 800f
