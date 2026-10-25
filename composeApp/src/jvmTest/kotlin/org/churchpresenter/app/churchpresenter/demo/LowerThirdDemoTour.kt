@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.App as LottieGenApp
import org.churchpresenter.lottiegen.band.BibleLottieGenApp
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.serverui.ServerSettingsTab
import java.io.File
import kotlin.test.Test
import org.churchpresenter.app.churchpresenter.screenshot.LIBRARY
import org.churchpresenter.app.churchpresenter.screenshot.library

/** The Lower Third tour: a speaker's lower third designed in the generator, saved, and put live. */
class LowerThirdDemoTour {

    @Test
    fun lowerThird() {
        library()
        val folder = File(LIBRARY, "Lower Thirds")
        folder.listFiles { f -> f.name.startsWith(SPEAKER) }?.forEach { it.delete() }
        recordTour(
            "lower-third",
            tab = "Lower Third",
            liveLabel = "Introduce a speaker",
            steps = 11,
            settings = {
                it.copy(serverSettings = it.serverSettings.copy(port = SERVER_PORT, serverHost = "192.168.1.42"))
            },
            panel = { settings, change ->
                if (generator.value == "server") {
                    Box(Modifier.size(600.dp, 800.dp)) {
                        ServerSettingsTab(settings, change, companion, RemoteClientManager())
                    }
                    return@recordTour
                }
                Box(Modifier.size(1200.dp, 800.dp)) {
                    if (generator.value == "band") {
                        BibleLottieGenApp(outputDir = File(LIBRARY, "Bands"), onFileSaved = null, embedded = true)
                    } else {
                        LottieGenApp(outputDir = folder, canvasWidth = 1920, canvasHeight = 1080, embedded = true)
                    }
                }
            },
        ) { lowerThirdTour() }
        companion.stop()
    }

    private val companion = CompanionServer(shutdownGraceMs = 0)

    private fun DemoTour.lowerThirdTour() {
        say("intro")
        intro("Introduce speakers with animated name banners.")
        say("open-tab")
        openTab("Lower Third", "Animated banners for names and titles")
        say("list")
        note("Your lower thirds", "Every one you've made, ready to use", text("Guest Speaker"))
        say("generate")
        step("Generate a new one", "Opens the lower third generator", find(hasText("Generate"))) {}
        generator.value = "speaker"
        openPanel("Lottie Lower Third Generator", 1200f, 800f)
        holdPreview()
        design()
        save()
        closePanel()
        goLiveWithIt()
        band()
        triggers()
        say("schedule")
        step(
            "Add it to the schedule",
            "Bring the speaker up at the right moment",
            find(hasContentDescription("Add to Schedule")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("outro")
        outro()
    }

    /** Canvas, style, text, text style, colors, shape and timing — the generator, top to bottom. */
    private fun DemoTour.design() {
        say("canvas")
        panelNote("Canvas", "Matches your screen: 1920 × 1080", panelItem(hasText("Canvas")))
        say("style")
        panelNote(
            "Pick a style",
            "Fourteen animated designs",
            panelItem(hasText("Style 1", substring = true)),
            click = true,
        )
        say("style-pick")
        panelNote(
            "Banner",
            "The preview plays it straight away",
            panelItem(hasText("Style 4", substring = true)),
            click = true,
            numbered = true,
        )
        say("alignment")
        panelNote("Alignment", "Left, center or right", panelItem(hasText("Left")))
        val fields = panelItems(hasSetTextAction())
        say("name")
        panelEdit("Name", "The speaker's name", fields[0], SPEAKER)
        say("info")
        panelEdit("Info", "Their role, or a short title", fields[1], "Guest Speaker · Hope City Church")
        say("detail")
        panelNote("Detail", "An optional third line", fields[2])
        say("text-style")
        panelNote(
            "Text style",
            "Font, sizes, weight and capitals",
            panelItem(hasText("Text Style")),
            click = true,
            numbered = true,
        )
        say("colors")
        panelNote("Colors", "Text, accent, background and border", panelReveal(hasText("Accent")), numbered = true)
        say("save-colors")
        panelNote("Save Colors", "Reuse your church's colors on every one", panelReveal(hasText("Save Colors")))
        say("shape")
        panelNote(
            "Shape",
            "Corners, border, background and shadow",
            panelReveal(hasText("Shape")),
            click = true,
            numbered = true,
        )
        say("timing")
        panelNote(
            "Timing",
            "How fast it animates, and how long it stays",
            panelReveal(hasText("Timing")),
            click = true,
            numbered = true,
        )
    }

    /** Pauses the generator's preview where the banner is fully on screen. */
    private fun DemoTour.holdPreview() {
        panelScrub(panelItem(hasContentDescription("Pause")), panelItem(hasText("0%")), 0.6f)
    }

    /** Save Lower Third: the file for the tab, and the speaker kept in the Library. */
    private fun DemoTour.save() {
        say("save")
        panelNote(
            "Save Lower Third",
            "Adds it to the Lower Third tab",
            panelReveal(hasText("Save Lower Third")),
            click = true,
            numbered = true,
        )
        waitFor("the lower third is saved", 30_000L) {
            File(LIBRARY, "Lower Thirds").listFiles { f -> f.name.startsWith(SPEAKER) }?.isNotEmpty() == true
        }
        say("library")
        panelNote("Saved for this speaker", "Click their name to bring their look back", panelReveal(hasText(SPEAKER)))
        say("batch")
        panelNote("Batch Import", "A whole list of names at once", panelReveal(hasText("Batch Import")))
    }

    private fun DemoTour.goLiveWithIt() {
        say("pick")
        val made = reveal { find(hasText(SPEAKER, substring = true)) { it.left > dp(380f) } }
        step("There it is", "Click it to preview", made)
        say("go-live")
        goLive(sub = "Plays the animation on the projector")
        say("live")
        showLive(sub = "$SPEAKER, introduced")
    }

    /** Settings → Server lists a trigger address for every lower third, for a Stream Deck or Companion. */
    private fun DemoTour.triggers() {
        companion.start(SERVER_PORT, "192.168.1.42")
        waitFor("the server is running", 15_000L) { companion.isRunning.value }
        say("triggers")
        generator.value = "server"
        openPanel("Settings — Server", 600f, 800f)
        say("triggers-list")
        panelNote(
            "Lower third triggers",
            "A web address that puts each one live",
            panelReveal(hasText("Lower third triggers")),
            numbered = true,
        )
        say("triggers-use")
        panelNote(
            "Use them anywhere",
            "Stream Deck, Bitfocus Companion or a script",
            panelReveal(hasText(SPEAKER, substring = true)),
        )
        closePanel()
    }

    /** Bible verses and song lyrics have a generator of their own: bands whose text is filled in live. */
    private fun DemoTour.band() {
        say("band")
        generator.value = "band"
        openPanel("Lower Third Generator — Bible & Songs", 1200f, 800f)
        runCatching { panelScrub(panelItem(hasContentDescription("Pause")), panelItem(hasText("0%")), 0.6f) }
        say("band-style")
        panelNote("Band", "Dozens of band designs", panelItem(hasText("Band")))
        say("band-layout")
        panelNote("Layout", "Where the verse and reference sit", panelReveal(hasText("Layout")))
        say("band-save")
        panelNote("Save", "Choose it in Settings → Bible or Songs", panelReveal(hasText("Save")))
        closePanel()
    }
}

private const val SPEAKER = "Pastor David Miller"
private const val SERVER_PORT = 48_766

/** Which generator the panel shows: "speaker" (names and titles) or "band" (Bible and songs). */
private val generator = mutableStateOf("speaker")
