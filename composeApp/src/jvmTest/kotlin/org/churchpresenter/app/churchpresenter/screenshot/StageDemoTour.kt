@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.churchpresenter.profiles.ProfilesSettingsTab
import kotlin.test.Test

/** The stage monitor: its profile, layouts and zones, what goes where, its styling, and it live. */
class StageDemoTour {

    @Test
    fun stage() = recordTour(
        "stage",
        tab = "Stage Monitor",
        liveLabel = "Set up the stage monitor",
        steps = 10,
        heading = "The " to "",
        settings = ::threeScreens,
        panel = { settings, change ->
            Box(Modifier.size(SETTINGS_W.dp, SETTINGS_H.dp)) { ProfilesSettingsTab(settings, change) }
        },
    ) { stageTour() }

    private fun DemoTour.stageTour() {
        say("intro")
        intro("What the people on stage see, and how to set it up.")
        say("what")
        note("The stage monitor", "Time, next item, notes and chords", stageTile())
        setup()
        layout()
        whatGoesWhere()
        features()
        say("style")
        val style = hasText("Zone background") or hasText("Font")
        point("Each zone's own style", "Font, size, color, background", style)
        closePanel()
        say("messages")
        openTab("Announcements", "A note for the stage")
        val toStage = hasContentDescription("Send to Stage Monitor")
        val send = runCatching { find(toStage) { it.left > dp(380f) } }.getOrNull()
        if (send != null) {
            note("Send to Stage Monitor", "Messages and timers, for the stage only", send)
        } else {
            caption("Send to Stage Monitor", "Messages and timers, for the stage only")
        }
        live()
        say("outro")
        outro()
    }

    private fun DemoTour.setup() {
        say("setup")
        step("Settings", "Then Profiles", find(hasContentDescription("Settings", substring = true)), numbered = true) {}
        openPanel("Settings — Profiles", SETTINGS_W, SETTINGS_H)
        menusInPlace = true
        // The stage's own profile, from the list down the left.
        panelItems(hasText("Stage", substring = true)).minBy { it.bounds.left }.node.performClick()
        ui.waitForIdle()
        panelNote("The stage profile", "A profile like any other", panelItems(hasText("PROFILES")).first())
        say("mode")
        point("Display mode: Stage monitor", "On the General page", hasText("Stage monitor"))
        say("outputs")
        panelNote("Outputs", "Which screen it goes to", rail("Outputs"), click = true)
    }

    private fun DemoTour.layout() {
        say("stage-page")
        panelNote("Stage layout", "Under Stage Monitor", rail("Stage layout"), click = true, numbered = true)
        quietPanelClick(hasText("Advanced"))
        quietPanelClick(hasText("Open all"))
        say("layout")
        point("Layout", "Two to five zones, two ways each", hasText("Arrangement") or hasText("LAYOUT"))
        quietPanelClick(hasText("Top / 2 below"))
        say("zones")
        point("Numbered zones", "Shown on the preview", hasText("Zones") or hasText("Zone 1"))
        say("sizes")
        val divider = hasText("Even out") or hasText("Drag a divider", substring = true)
        point("Drag a divider", "Even out to start again", divider)
    }

    private fun DemoTour.whatGoesWhere() {
        say("what-goes-where")
        val where = hasText("WHAT GOES WHERE") or hasText("What goes where")
        point("What goes where", "A zone for each kind of content", where)
        say("full-screen")
        point("Full Screen", "The whole stage monitor", hasText("Full Screen"))
        say("none")
        point("Or None", "Not on the stage monitor", hasText("None") or hasText("Zone 3"))
        say("stranded")
        val stranded = hasText("Not shown on this layout", substring = true)
        point("Nothing goes missing", "What a layout no longer shows", stranded)
    }

    private fun DemoTour.features() {
        say("clock")
        point("Clock", "The time right now", hasText("Clock"))
        say("next")
        point("Next", "What comes after this", hasText("Next"))
        say("notes")
        point("Presenter Notes", "From your slides", hasText("Presenter Notes"))
        say("chords")
        point("Show Chords", "For the band, never the congregation", hasText("Show Chords") or hasText("Chord color"))
        say("metronome")
        point("Metronome", "In the position you choose", hasText("Metronome Position"))
    }

    /** A song verse live, on the projector and on the stage monitor. */
    private fun DemoTour.live() {
        say("live")
        openTab("Songs", "Put a song live")
        quietClick(text("Amazing Grace"))
        step("Click a verse", "Ready in the preview", text("Amazing grace! how sweet the sound"))
        goLive(sub = "Sends the verse to the projector and the stage")
        say("live-stage")
        note("On the stage monitor", "The verse, what's next, the time", stageTile())
    }

    /**
     * The stage monitor's tile in the previews down the right: its header and the picture under it,
     * so the tooltip sits below the picture rather than over it.
     */
    private fun DemoTour.stageTile(): Target {
        val header = findAll(hasContentDescription("Hide this preview"))[2]
        val b = header.bounds
        return Target(header.node, Rect(b.left, b.top, b.right, b.bottom + b.width * PICTURE_SHARE))
    }
}

// The real Settings window opens at this size (OptionsDialog).
private const val SETTINGS_W = 1400f
private const val SETTINGS_H = 900f

/** A 16:9 preview picture is this much of its tile's width tall. */
private const val PICTURE_SHARE = 9f / 16f
