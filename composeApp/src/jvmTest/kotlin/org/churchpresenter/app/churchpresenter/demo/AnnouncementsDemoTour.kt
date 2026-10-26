@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.factoryOutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/** The Announcements tour: a message styled and placed on screen, then the four timers. */
class AnnouncementsDemoTour {

    @Test
    fun announcements() = recordTour(
        "announcements",
        tab = "Announcements",
        liveLabel = "Show an announcement",
        steps = 17,
        settings = ::withStageMonitor,
    ) { announcementsTour() }

    private fun DemoTour.announcementsTour() {
        say("intro")
        intro("Show messages and countdowns on the big screen.")
        say("open-tab")
        openTab("Announcements", "Messages, countdowns and clocks")
        message()
        look()
        say("go-live")
        goLive(sub = "Sends the announcement to the projector")
        say("live")
        showLive(sub = "The announcement slides in")
        say("preset")
        note(
            "Save a preset",
            "Keep this message, its look and its motion",
            find(hasContentDescription("Save preset")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("stage-message")
        val toStage = find(hasContentDescription("Send to Stage Monitor")) { it.left > dp(380f) && it.top < dp(140f) }
        step("Send to Stage Monitor", "A note only the pastor or worship team sees", toStage)
        timers()
        say("outro")
        outro()
    }

    /** The message itself, and how its text looks. */
    private fun DemoTour.message() {
        say("text")
        type("Type your message", "Anything the congregation should see", searchField(), MESSAGE)
        say("text-style")
        note("Text style", "Color, bold, italic, underline, outline", text("TEXT COLOR"))
        say("font")
        note("Font and size", "Any font on this computer", text("FONT TYPE"))
    }

    /** Where it sits, how it moves, and how many times. */
    private fun DemoTour.look() {
        say("position")
        step("Pick a position", "Nine places on the screen", find(hasContentDescription("Bottom Center")))
        say("background")
        note("Background", "A color behind the text, or none", text("BACKGROUND COLOR"))
        say("animation")
        step("Choose an animation", "Fade, or slide in from any side", text("ANIMATION"))
        say("animation-pick")
        step("Slide From Right", "It scrolls across the screen", menuItem("Slide From Right"))
        say("loop")
        note("Loop count", "How many times it plays — 0 means forever", text("LOOP COUNT"))
        say("speed")
        note("Speed", "How quickly it moves", text("Speed"))
    }

    /** The four timers: a countdown, a count-up, a count to a time, and a clock. */
    private fun DemoTour.timers() {
        say("timers")
        step("Timers", "A countdown for the start of the service", timerTab("Timer"))
        say("set-time")
        val minutes = findAll(hasContentDescription("Increase", substring = true)) { it.left > dp(380f) }[1]
        step("Set the time", "Hours, minutes and seconds", minutes) {
            repeat(5) {
                minutes.node.performClick()
                ui.waitForIdle()
            }
        }
        say("expired")
        val expired = find(hasSetTextAction()) { it.left > dp(380f) && it.top > dp(560f) }
        type("When it reaches zero", "Show a message instead", expired, "The service is beginning")
        say("timer-live")
        val live = find(hasContentDescription("Go Live")) { it.left > dp(380f) && it.top > dp(500f) }
        goLive(live, sub = "Puts the countdown on the projector")
        say("start")
        step("Start", "The countdown begins", find(hasContentDescription("Start")) { it.left > dp(380f) }) {
            find(hasContentDescription("Start")) { it.left > dp(380f) }.node.performClick()
            ui.waitForIdle()
            settle()
        }
        say("timer-on")
        showLive("Counting down", "Five minutes until the service")
        say("stage")
        note(
            "Stage Monitor",
            "Show the timer to the speaker only",
            find(hasContentDescription("Send to Stage Monitor") or hasContentDescription("Hide Announcement")) {
                it.left > dp(380f) && it.top > dp(400f)
            },
        )
        say("duration")
        step("Duration", "Counts up from zero — how long it's been", timerTab("Duration"))
        say("specific")
        step("Specific Time", "Counts down to a time of day", timerTab("Specific Time"))
        say("clock")
        step("Clock", "Shows the time right now", timerTab("Clock"))
        say("format")
        note("Clock format", "12 or 24 hour, with or without seconds", text("FORMAT"))
        say("schedule")
        step(
            "Add it to the schedule",
            "Every timer can be part of the service",
            find(hasContentDescription("Add to Schedule")) { it.left > dp(380f) && it.top > dp(500f) },
        )
        say("schedule-row")
        val row = reveal { findAll(hasText("Clock")) { it.right < dp(380f) }.last() }
        note("Added to your schedule", "Ready whenever you need it", row)
    }

    private fun DemoTour.timerTab(label: String) = find(hasText(label)) { it.left > dp(380f) && it.top > dp(250f) }
}

/** A second output used as a stage monitor, so the tab offers Send to Stage Monitor. */
private fun withStageMonitor(s: AppSettings): AppSettings {
    val proj = s.projectionSettings
    val stage = OutputProfile(id = "demo-stage", name = "Stage", displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)
    return s.copy(
        projectionSettings = proj.copy(
            outputProfiles = proj.outputProfiles.ifEmpty { listOf(factoryOutputProfile()) } + stage,
            screenAssignments = proj.screenAssignments.take(1) + ScreenAssignment(activeProfileId = stage.id),
        ),
    )
}

private const val MESSAGE = "Coffee and fellowship in the hall after the service"
