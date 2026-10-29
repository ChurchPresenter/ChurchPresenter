@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assume.assumeTrue
import kotlin.test.Test

/**
 * The mobile app's Calendar mode: joining the church computer's calendar, then planning on the phone
 * — the month, a new service, its run of show and timing, and copying it ahead. All of it is the mobile
 * app's own screens (see [DemoPhone]); the computer's side is the Calendar Manager tour's.
 */
class CalendarModeDemoTour {

    @Test
    fun calendarMode() {
        assumeTrue("ChurchPresenter-Mobile is not checked out beside this repo", MOBILE_ROOT.isDirectory)
        recordTour(
            "calendar-mode",
            tab = "Calendar Mode",
            liveLabel = "Planned on the phone",
            steps = 9,
            heading = "Mobile " to "",
            panel = { _, _ ->
                Box(Modifier.size(PANEL_W.dp, PANEL_H.dp), contentAlignment = Alignment.Center) {
                    DemoPhone(phone.value)
                    DemoSheet(sheet.value)
                }
            },
        ) { calendarTour() }
    }

    private fun DemoTour.calendarTour() {
        say("intro")
        intro("Plan services on your phone, all week.", establish = false)
        show("screenshots/mode-picker/calendar-selected")
        openPanel("Phone", PANEL_W, PANEL_H)
        say("why")
        panelNote("Planning, not projecting", "Plans reach the church computer", phoneScreen())
        segment("mode")
        say("mode")
        panelNote("Calendar", "Then Continue", screenBox(CALENDAR_CARD), numbered = true)
        // Behind the sheets that follow, the phone is in Calendar mode, not still choosing one.
        show("screenshots/calendar/run-of-show-empty")
        segment("join")
        join()
        segment("month")
        month()
        segment("run")
        runOfShow()
        segment("copy")
        say("copy")
        show("screenshots/calendar-copy/weekly")
        panelNote("Copy ahead", "Weekly, every two weeks, monthly", phoneScreen(), numbered = true)
        say("synced")
        show("screenshots/calendar/run-of-show-synced")
        panelNote("The same plan everywhere", "Every enrolled phone", phoneScreen())
        segment("end")
        closePanel()
        say("outro")
        outro()
    }

    /** Sync with the church computer: by invite, or asked for over the church Wi-Fi. */
    private fun DemoTour.join() {
        say("desktop-first")
        show("screenshots/calendar-sync/desktop-off")
        panelNote("Switched on at the computer first", "See the Calendar Manager tour", sheetScreen())
        say("sync")
        show("screenshots/calendar-sync/idle")
        panelNote("Sync with the church computer", "Two ways to join", sheetScreen(), numbered = true)
        say("invite")
        panelNote("An invite", "Invite a phone, then scan", screenBox(INVITE_TEXT, sheetScreen()))
        say("ask")
        panelNote("Ask over the church WiFi", "On the church network", screenBox(ASK_BUTTON, sheetScreen()), numbered = true)
        say("waiting")
        show("screenshots/calendar-sync/waiting")
        panelNote("The same code on both", "The computer allows the phone", sheetScreen())
        say("scan")
        show("screenshots/calendar-sync/scan-qr")
        panelNote("Scan to finish", "The computer's QR code", sheetScreen())
        say("enrolled")
        show("screenshots/calendar-sync/enrolled")
        panelNote("Syncing", "Stop syncing is always there", sheetScreen())
    }

    /** The month, its sync line and service types, an empty day, and a new service. */
    private fun DemoTour.month() {
        say("month")
        show("screenshots/calendar/month")
        panelNote("The month", "Services on each day", sheetScreen(), numbered = true)
        say("status")
        show("screenshots/calendar/month-header-synced")
        panelNote("The sync line", "Last synced, and next", sheetScreen())
        say("types")
        show("screenshots/calendar/month")
        panelNote("Service types", "A color for each", screenBox(SERVICE_TYPES, sheetScreen()))
        say("empty-day")
        show("screenshots/calendar/day-empty")
        panelNote("An empty day", "Add a service, or copy last week's", sheetScreen())
        say("new")
        show("screenshots/calendar-service/new")
        panelNote("New service", "Blank, or from a template", sheetScreen(), numbered = true)
    }

    /** The run of show, adding to it, one item's timing, and arming the cues. */
    private fun DemoTour.runOfShow() {
        say("run")
        show("screenshots/calendar/run-of-show")
        panelNote("Run of show", "In order, in sections", phoneScreen(), numbered = true)
        say("add")
        panelNote("Add items", "Songs, Bible, sections, ministry, presets", screenBox(ADD_BUTTON))
        say("item")
        show("screenshots/calendar-row/song")
        panelNote("One item", "Its length and its timing", sheetScreen(), numbered = true)
        say("timing")
        show("screenshots/calendar-timing/cued")
        panelNote("Starts, Repeats, At end", "Cued, a loop, then what", sheetScreen())
        say("loop")
        show("screenshots/calendar-timing/looping")
        panelNote("An announcement loop", "After the item before, loops, then blanks", sheetScreen())
        say("armed")
        show("screenshots/calendar/run-of-show")
        panelNote("Armed", "The timed cues run by themselves", screenBox(ARMED), numbered = true)
    }

    /** A whole screen goes on the phone; a sheet floats over it, large enough to read. */
    private fun DemoTour.show(shot: String) {
        clearNote()
        if (isSheet(shot)) {
            sheet.value = shot
        } else {
            sheet.value = ""
            phone.value = shot
        }
        ui.waitForIdle()
        pause(0.3)
    }
}

private val phone = mutableStateOf("")
private val sheet = mutableStateOf("")

// Where things sit on the screens, as fractions of their width and height.
private val CALENDAR_CARD = box(0.03f, 0.57f, 0.97f, 0.75f)
private val INVITE_TEXT = box(0.03f, 0.5f, 0.97f, 0.74f)
private val ASK_BUTTON = box(0.03f, 0.76f, 0.97f, 0.93f)
private val SERVICE_TYPES = box(0.02f, 0.77f, 0.98f, 0.99f)
private val ADD_BUTTON = box(0.03f, 0.92f, 0.2f, 0.99f)
private val ARMED = box(0.76f, 0.03f, 0.97f, 0.09f)

private const val PANEL_W = 1500f
private const val PANEL_H = 900f
