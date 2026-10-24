@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import org.churchpresenter.appsettings.SystemSettingsTab
import kotlin.test.Test

/** A first look around the main window: the schedule, the tabs, going live, the previews and the tools. */
class OverviewDemoTour {

    @Test
    fun overview() = recordTour(
        "overview",
        tab = "ChurchPresenter",
        liveLabel = "A first look",
        steps = 6,
        heading = "" to "",
        // Every schedule button showing, the Calendar Manager's too, which a new install hides.
        settings = { it.copy(hiddenScheduleButtons = emptySet()) },
        panel = { settings, change ->
            Box(Modifier.size(SYSTEM_W.dp, SYSTEM_H.dp)) { SystemSettingsTab(settings, change) }
        },
    ) { overviewTour() }

    private fun DemoTour.overviewTour() {
        say("intro")
        intro("Find your way around the main window.")
        say("layout")
        tour(
            stop("The schedule", "Your service, in order", find(hasText("Schedule")) { it.right < dp(720f) }),
            stop("The tabs", "Everything you can show", tab("Bible")),
            stop("Every screen", "What the congregation sees", button("Hide this preview")),
        )
        say("schedule")
        note("Collapse the schedule", "More room when you need it", button("Collapse Schedule"))
        tabs()
        goingLive()
        screens()
        say("background")
        note("Background", "Behind your words", button("Background"))
        say("settings")
        step("Settings", "Screens, profiles, Bibles, server", button("Settings"), numbered = true) {}
        openPanel("Settings — System", SYSTEM_W, SYSTEM_H)
        say("storage")
        val folders = panelItem(hasText("Bibles", substring = true))
        panelNote("Bibles and songs folders", "Safe through every update", folders)
        closePanel()
        say("remote")
        note("Android and iOS apps", "For the people you trust", button("Settings"))
        say("remote-what")
        note("Help run the service", "You approve their changes", button("Settings"))
        say("standalone")
        note("Standalone", "The phone presents on its own", button("Hide this preview"))
        planning()
        highlights()
        say("outro")
        outro()
    }

    private fun DemoTour.tabs() {
        say("tabs")
        note("The tabs", "A different kind of content each", tab("Bible"))
        say("tabs-word")
        tour(stop("Bible", "Any passage in seconds", tab("Bible")), stop("Songs", "Your song library", tab("Songs")))
        say("tabs-media")
        tour(
            stop("Pictures", "Your photos", tab("Pictures")),
            stop("Presentation", "Your slides", tab("Presentation")),
            stop("Media", "Videos and music", tab("Media")),
        )
        say("tabs-more")
        tour(
            stop("Lower Third", "A name banner", tab("Lower Third")),
            stop("Announcements", "Messages and countdowns", tab("Announcements")),
        )
        say("tabs-rest")
        tour(
            stop("Web", "Any web page", tab("Web")),
            stop("Canvas", "Screens you design", tab("Canvas")),
            stop("Dictionary", "Hebrew and Greek", tab("Dictionary")),
        )
        say("tab-visibility")
        step("Tab Visibility", "Show or hide tabs", button("Tab Visibility"), numbered = true)
        // Closed again by the same button, so the menu doesn't stay over the next steps.
        quietClick(button("Tab Visibility"))
    }

    private fun DemoTour.goingLive() {
        say("pattern")
        type("Choose what to show", "It appears in the preview first", searchField(), "John 3:16", enter = true)
        say("go-live")
        goLive(sub = "Sends it to the projector")
        say("live")
        showLive(sub = "John 3:16 on the big screen")
        say("add-to-schedule")
        note(
            "Add to Schedule",
            "Plan the service ahead",
            find(hasContentDescription("Add to Schedule")) { it.left > dp(380f) && it.top < dp(140f) },
        )
    }

    /** The previews down the right, and the buttons above them. */
    private fun DemoTour.screens() {
        say("preview")
        note("Live preview", "What every screen shows", button("Hide this preview"))
        say("screen")
        note("Screen and profile", "The look of that screen", find(hasText("Screen 1")))
        say("swap")
        note("Swap output profile", "A different look", button("Swap output profile"))
        say("lock")
        note("Lock", "Keep a screen on its tab", button("Lock screen to current tab"))
        say("clear")
        step("Clear Display", "Takes everything off the screen", button("Clear Display"))
        say("displays")
        note("Toggle Presenter Displays", "The projector windows, on or off", button("Toggle Presenter Displays"))
        say("preview-settings")
        note("Preview Settings", "Which screens show here", button("Preview Settings"))
    }

    private fun DemoTour.planning() {
        say("planning")
        note("Planning Center", "Import a service plan", button("Import from Planning Center"))
        say("calendar")
        note("Calendar Manager", "Services planned weeks ahead", button("Calendar Manager"))
        say("song-library")
        note("Song Library Manager", "In the Help menu", tab("Songs"))
    }

    /** What there isn't room to show, each said over the part of the window it belongs to. */
    private fun DemoTour.highlights() {
        say("more")
        note("There's much more", "A few highlights", button("Settings"))
        say("more-languages")
        note("35 languages, 12 themes", "And it all works offline", button("Settings"))
        say("more-bible")
        note("Up to six translations", "And it can follow the preacher", tab("Bible"))
        say("more-outputs")
        note("Every screen its own look", "Projectors, TVs, DeckLink, NDI, OBS", button("Hide this preview"))
        say("more-stage")
        note("Stage monitor", "Time, next item, notes and chords", button("Hide this preview"))
        say("more-integrations")
        note("ATEM, OBS, Companion", "And Stream Deck", button("Settings"))
        say("more-reporting")
        note("CCLI reporting", "A quick export", tab("Songs"))
        say("more-link")
        note("Link a second copy", "An overflow room or another campus", button("Hide this preview"))
        say("wick")
        note("Wick, the built-in helper", "Turn it on from the Help menu", button("Settings"))
    }

    private fun stop(title: String, body: String, target: Target) = Triple(title, body, target)

    private fun DemoTour.button(name: String) = find(hasContentDescription(name, substring = true))
}

private const val SYSTEM_W = 900f
private const val SYSTEM_H = 640f
