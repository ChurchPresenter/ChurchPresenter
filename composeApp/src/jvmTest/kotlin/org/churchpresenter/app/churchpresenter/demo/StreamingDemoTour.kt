@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import org.churchpresenter.liveoutput.settings.DetectedScreen
import org.churchpresenter.liveoutput.settings.FfmpegStatus
import org.churchpresenter.liveoutput.settings.ProjectionSettingsTab
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.omt.OmtRuntimeStatus
import kotlin.math.abs
import kotlin.test.Test

/** Settings → Projection: the outputs a livestream takes the words from — Browser Source, NDI, OMT, DeckLink. */
class StreamingDemoTour {
    @Test
    fun streaming() = recordTour(
        "streaming",
        tab = "Streaming Outputs",
        liveLabel = "On the stream",
        steps = 6,
        heading = "" to "",
        settings = { threeScreens(it) },
        panel = { settings, change ->
            Box(Modifier.size(PANEL_W.dp, PANEL_H.dp)) {
                ProjectionSettingsTab(
                    settings = settings,
                    onSettingsChange = change,
                    serverUrl = SERVER_URL,
                    // What a streaming computer has: one projector, the NDI Runtime, OMT built in.
                    detectScreens = { SCREENS },
                    ndiStatus = { NdiRuntimeStatus.Ready(NDI_VERSION, "") },
                    ndiReceiverCount = { 1 },
                    omtStatus = { OmtRuntimeStatus.Ready("", bundled = true) },
                    omtReceiverCount = { 0 },
                    omtAddressOf = { "" },
                    ffmpegProbe = { FfmpegStatus(available = true, path = "", bundled = true) },
                    vlcProbe = { false },
                    audioDeviceProbe = { emptyList() },
                    vlcRecheck = { false },
                    isRelease = true,
                )
            }
        },
    ) { streamingTour() }

    private fun DemoTour.streamingTour() {
        say("intro")
        intro("Lyrics and scripture on your livestream.")
        say("why")
        caption("More than the projector", "The stream shows the words too")
        say("ways")
        caption("Four ways", "Browser Source, NDI, OMT and DeckLink")
        say("settings")
        note("Settings → Projection", "Every output in one place", settingsButton())
        openPanel("Settings — Projection", PANEL_W, PANEL_H)
        browserSource()
        ndi()
        say("omt")
        point("OMT Outputs", "Open source, nothing to install", hasText("OMT Outputs"))
        say("decklink")
        point("Screen Assignment", "A DeckLink card is listed here, like a screen", hasText("Screen Assignment"))
        closePanel()
        say("outro")
        outro()
    }

    private fun DemoTour.browserSource() {
        say("browser")
        point("Browser Source Outputs", "The simplest way into OBS", hasText("Browser Source Outputs"))
        say("browser-add")
        val addBrowser = inSection(BROWSER, NDI, hasText("Add Output"))
        panelNote("Add Output", "One for the stream", addBrowser, click = true, numbered = true)
        say("browser-name")
        panelEdit("Name", "Like Stream", inSection(BROWSER, NDI, hasSetTextAction()), "Stream")
        say("browser-profile")
        val profile = inSection(BROWSER, NDI, hasText("Default"))
        panelNote("Profile", "Lower Third, over the camera", profile)
        // Picked through the settings, as the menu would: an open menu takes the main window's
        // keyboard, which it reports in a red bar that cannot be dismissed while the menu is open.
        changeSettings({ s ->
            val proj = s.projectionSettings
            s.copy(
                projectionSettings = proj.copy(
                    browserSourceOutputs = proj.browserSourceOutputs.map { it.copy(activeProfileId = LOWER_THIRD_ID) },
                ),
            )
        })
        pause(0.4)
        say("browser-server")
        caption("The companion server", "On, under Settings → Server")
        say("browser-copy")
        point("Copy URL for Transparent Background", "See-through behind the words", hasText(COPY_TRANSPARENT))
        say("browser-obs")
        caption("In OBS", "Add a Browser Source, and paste it in")
    }

    private fun DemoTour.ndi() {
        say("ndi")
        point("NDI Outputs", "Over your network: OBS, vMix, a switcher", hasText(NDI))
        say("ndi-runtime")
        point("NDI Runtime", "Installed, and ready", hasText("NDI Runtime $NDI_VERSION"))
        say("ndi-add")
        val addNdi = inSection(NDI, OMT, hasText("Add Output"))
        panelNote("Add Output", "Named as your switcher sees it", addNdi, click = true, numbered = true)
        runCatching { inSection(NDI, OMT, hasSetTextAction()).node.performTextReplacement("Lyrics") }
        runCatching { inSection(NDI, OMT, hasText("Apply")).node.performClick() }
        ui.waitForIdle()
        giveKeyboardBack()
        pause(0.3)
        say("ndi-mode")
        point("Alpha", "Real transparency, already keyed", hasText("Alpha (transparent)"))
        say("ndi-receivers")
        point("1 receiving", "Who is watching it", hasText("1 receiving"))
    }

    /** The first [m] between the section headed [from] and the one headed [to], scrolled into view. */
    private fun DemoTour.inSection(from: String, to: String, m: SemanticsMatcher): Target {
        seek(hasText(from))
        val top = panelItems(hasText(from)).first().bounds.top
        val bottom = panelItems(hasText(to)).firstOrNull()?.bounds?.top ?: Float.MAX_VALUE
        return panelItems(m).filter { it.bounds.top > top && it.bounds.top < bottom }
            .minByOrNull { abs(it.bounds.top - top) } ?: seek(m)
    }

    private fun DemoTour.settingsButton() =
        find(hasContentDescription("Settings", substring = true)) { it.top < dp(TOP_BAR_DP) }
}

private val SCREENS = listOf(
    DetectedScreen(index = 0, isPrimary = true, boundsX = 0, boundsY = 0, boundsW = 1920, boundsH = 1080),
    DetectedScreen(index = 1, isPrimary = false, boundsX = 1920, boundsY = 0, boundsW = 1920, boundsH = 1080),
)

private const val BROWSER = "Browser Source Outputs"
private const val LOWER_THIRD_ID = "demo-lower-third"
private const val NDI = "NDI Outputs"
private const val OMT = "OMT Outputs"
private const val COPY_TRANSPARENT = "Copy URL for Transparent Background"
private const val NDI_VERSION = "6.1.1"
private const val SERVER_URL = "http://192.168.1.42:8765"
private const val TOP_BAR_DP = 60f
private const val PANEL_W = 1200f
private const val PANEL_H = 860f
