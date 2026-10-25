@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.calendar.CalendarCloudSync
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.sync.PairedDevice
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.PresetDocument
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.UpcomingLoad
import org.churchpresenter.calendar.ui.CalendarApp
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.server.CalendarEnrollment
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.serverui.CalendarEnrollQrContent
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.serverui.ServerSettingsTab
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test

/** The Calendar Manager: planning ahead, a pre-service loop that runs itself, loading, printing, phones. */
class CalendarDemoTour {

    private val companion = CompanionServer(shutdownGraceMs = 0)

    @Test
    fun calendar() {
        TestSingletons.latchToTestHome()
        val folder = File(System.getProperty("user.home"), "demo-calendar").apply { deleteRecursively(); mkdirs() }
        seed(folder)
        val sync = CalendarSyncService(
            folder,
            File(LIBRARY, "Songs"),
            settings = { syncSettings },
            saveSettings = {},
            initialDevices = SAMPLE_PHONES,
        )
        recordTour(
            "calendar",
            tab = "Calendar Manager",
            liveLabel = "Plan ahead",
            steps = 12,
            heading = "The " to "",
            settings = ::threeScreens,
            panel = { settings, change ->
                if (screen.value == "qr") {
                    Box(Modifier.size(QR_W.dp, QR_H.dp)) { CalendarEnrollQrContent(SAMPLE_ENROLLMENT, onDismiss = {}) }
                } else if (screen.value == "server") {
                    Box(Modifier.size(SERVER_W.dp, SERVER_H.dp)) {
                        ServerSettingsTab(settings, change, companion, remember { RemoteClientManager() }, sync)
                    }
                } else {
                    Box(Modifier.size(CALENDAR_W.dp, CALENDAR_H.dp)) {
                        CalendarApp(
                            storeFolder = folder,
                            songFolder = File(LIBRARY, "Songs"),
                            host = CalendarHost(
                                loadIntoSchedule = { items, timing, replace, _, start ->
                                    loadInto(items, timing, replace, start)
                                },
                                // Sync shown switched on, so the header offers Invite a phone.
                                cloudSync = CalendarCloudSync(enabled = { true }, setEnabled = {}),
                            ),
                            today = TODAY,
                            now = { NOW },
                            watchStoreFolder = false,
                        )
                    }
                }
            },
        ) {
            loadInto = { items, timing, replace, start ->
                val actions = schedule()
                if (replace) actions.clearSchedule()
                actions.setServiceStart(start)
                items.forEach { actions.addRow(it, timing[it.id]) }
            }
            calendarTour()
        }
        companion.stop()
    }

    private fun DemoTour.calendarTour() {
        say("intro")
        intro("Plan ahead, run the pre-service loop by itself, plan from phones.")
        say("why")
        caption("Planning, in the app", "No more typing it all in on Sunday")
        say("open")
        val button = find(hasContentDescription("Calendar Manager")) { it.right < dp(720f) && it.top < dp(100f) }
        step("Calendar Manager", "Help menu, or the Schedule toolbar", button, numbered = true) {}
        openPanel("Calendar", CALENDAR_W, CALENDAR_H)
        // The calendar's sheets open centered on the whole window: drawn over the calendar, like an alert.
        menusInPlace = false
        say("window")
        point("Month, day, run of show", "Colored by service type", hasText("November 2026", substring = true))
        say("saved")
        point("Saved as you go", "No Save button", hasText("All changes saved"))
        planning()
        loop()
        repeating()
        loading()
        phones()
        say("getting-started")
        caption("Getting started", "Presets, one Sunday, a template, copies, a phone")
        say("outro")
        outro()
    }

    /** A new service, and the picker's five tabs. */
    private fun DemoTour.planning() {
        say("add-service")
        quietPanelClick(hasText("29"))
        panelNote("Add service", "Plan a new one", seek(hasText("Add service")), click = true, numbered = true)
        // The sheet fades in after the click; wait for it rather than point at nothing.
        runCatching {
            waitFor("the new-service sheet", SHEET_WAIT_MS) {
                runCatching { dialogPopupItem(hasText("Start time", ignoreCase = true)) }.isSuccess
            }
        }
        say("service-details")
        val details = hasText("Start time", ignoreCase = true) or hasText("Name", ignoreCase = true)
        sheet("Name, start time, type", "Sunday, midweek or special", details)
        say("start-from")
        val from = hasText("Blank service") or hasText("Start from", ignoreCase = true)
        sheet("Start from", "Blank, a template, or a copy", from)
        closeSheet()
        quietPanelClick(hasText("Today"))
        say("add-items")
        val add = hasText("Add song, verse or section")
        panelNote("Add song, verse or section", "Five tabs", seek(add), click = true, numbered = true)
        say("add-song")
        sheet("Songs", "By title or number", hasText("Songs"), tab = true)
        say("add-bible")
        sheetClick(hasText("Bible"))
        sheet("Bible", "Type a reference, or browse", hasText("Bible"), tab = true)
        say("add-section")
        sheetClick(hasText("Section"))
        sheet("Section", "Pre-Service, Worship, Word…", hasText("Section"), tab = true)
        say("add-ministry")
        sheetClick(hasText("Ministry"))
        sheet("Ministry", "Planned and timed, never on screen", hasText("Ministry"), tab = true)
        say("add-preset")
        sheetClick(hasText("Presets"))
        sheet("Presets", "Reusable pieces of a service", hasText("Presets"), tab = true)
        closeSheet()
        closePanel()
        say("presets-how")
        // A preset is made on its own tab: here, the Pictures tab's Save preset.
        quietClick(tab("Pictures"))
        inSchedule(
            "Save preset",
            "On Pictures, Presentation, Media, Announcements, Canvas",
            hasContentDescription("Save preset"),
            tabArea = true,
        )
        quietClick(tab("Bible"))
        openPanel("Calendar", CALENDAR_W, CALENDAR_H)
        say("durations")
        point("How long each row runs", "Measured over real services", hasText("5:00"))
        say("lay-out")
        point(
            "Time every row",
            "Each starts as the one before ends",
            hasContentDescription("Time every row", substring = true) or hasText("Run clock"),
        )
        say("reorder")
        point("Drag, or the arrows", "Change the order", hasContentDescription("Move down"))
        say("attention")
        val attention = hasText("needs attention", substring = true) or hasText("need attention", substring = true)
        point("Items that need attention", "Click to fix", attention)
    }

    /** The pre-service loop: the slideshow from −30, the countdown from −5, armed. */
    private fun DemoTour.loop() {
        say("loop-intro")
        point("A pre-service loop", "Slideshow from −30, countdown from −5", hasText("Sunday announcements"))
        say("timing")
        closeSheet()
        panelNote("Its timing", "Four settings", seek(hasText("Sunday announcements")), click = true, numbered = true)
        runCatching {
            waitFor("the row's timing panel", SHEET_WAIT_MS) {
                runCatching { dialogPopupItem(hasText("Starts", ignoreCase = true)) }.isSuccess
            }
        }
        say("starts")
        sheet("Starts", "Cued, after previous, on time, or minutes before", hasText("Starts", ignoreCase = true))
        say("runs")
        sheet("Runs", "Its own length, or yours", hasText("Runs", ignoreCase = true))
        say("repeats")
        sheet("Repeats: Loop", "Until something else goes live", hasText("Repeats", ignoreCase = true))
        say("at-end")
        sheet("At end", "Hold, next item, or blank", hasText("At end", ignoreCase = true))
        say("timing-summary")
        val summary = hasText("Starts on its own", substring = true)
        sheet("In plain words", "Starts at 9:30, loops until cued, then holds", summary)
        closeSheet()
        say("chips")
        point("Timing at a glance", "−30, Loop, Next, After previous", hasText("Loop"))
        say("countdown")
        point("The countdown", "Starts at −5, then next", hasText("Countdown · 5 min"))
        say("post-service")
        point("A post-service loop", "After previous, or a clock time", hasText("Welcome video") or hasText("CLOSING"))
        say("armed")
        point("Armed", "Turns the timed cues on", hasText("Armed"))
        say("when-fire")
        caption("Only in the Schedule, and armed", "Skipped, never cutting anyone off")
        say("run-clock")
        point("Run clock", "Five minutes at a time", hasText("9:20 AM") or hasText("Run clock"))
    }

    private fun DemoTour.repeating() {
        say("repeat")
        quietPanelClick(hasContentDescription("Copy this service to another date, or repeat it"))
        sheet("Repeat", "Once, weekly, every 2 weeks, monthly", hasText("Weekly"), tab = true)
        sheetClick(hasText("Weekly"))
        say("series")
        val times = hasText("How many times", ignoreCase = true)
        sheet("How many times", "Each copy is its own service", times, tab = true)
        closeSheet()
        say("template")
        quietPanelClick(hasContentDescription("Save this run of show as a template"))
        sheet("Sections, or items too", "Timings and presets included", hasText("Items"), tab = true)
        closeSheet()
    }

    private fun DemoTour.loading() {
        say("load")
        panelNote("Load into Schedule", "Replace, or add to the end", seek(hasText("Load into Schedule")), click = true)
        sheetClick(hasText("Replace and load"))
        say("auto-load")
        point("Load the service automatically", "5 minutes ahead, up to 2 hours", hasText("Turn on"))
        say("auto-load-where")
        quietPanelClick(hasText("Calendar settings"))
        sheetClick(hasText("Defaults"))
        sheet("Calendar settings → Defaults", "And how far ahead", hasText("Load the service automatically"))
        closeSheet()
        say("pdf")
        point("Export PDF", "Public or staff copy", hasText("Export PDF"))
        closePanel()
        say("schedule")
        note(
            "Loaded into the Schedule",
            "Every row, timings and all",
            find(hasText("Schedule")) { it.right < dp(720f) },
        )
        say("schedule-times")
        inSchedule("Length and start time", "Bold: starts on its own", hasText("9:55 AM", substring = true))
        say("schedule-badges")
        inSchedule(
            "What it will do",
            "Loops, plays twice, then next item",
            hasText("Loops") or hasText("then next item"),
        )
        say("schedule-cues")
        caption("Fired, or skipped", "Start a skipped cue by hand")
        say("schedule-drift")
        caption("Ahead, behind, or on plan", "On the row that's live")
        say("schedule-notice")
        // What the Schedule tab shows before an automatic load: the next service and when it loads.
        demoUpcomingLoad.value = UpcomingLoad("sunday-1", "Sunday Morning", LocalDateTime.now().plusMinutes(10))
        ui.waitForIdle()
        inSchedule("Loading Sunday Morning in 10 min", "Load now, or let it load itself", hasText("Load now"))
        demoUpcomingLoad.value = null
    }

    /** Points at [m] in the sheet open over the calendar, or says the line over a caption. */
    private fun DemoTour.sheet(heading: String, sub: String, m: SemanticsMatcher, tab: Boolean = false) {
        val below = if (tab) tabsTop() else Float.NEGATIVE_INFINITY
        val target = runCatching { dialogPopupItem(m, topmost = tab, below = below) }.getOrNull()
        if (target != null) {
            panelNote(heading, sub, target)
        } else {
            File(System.getenv("CP_DEMO_OUT"), "missed.txt").appendText("$heading\n")
            caption(heading, sub)
        }
    }

    /**
     * Where a sheet's own tabs and buttons start: under its search field when it has one (the
     * picker), else under its title -- so a label is never matched in the window behind the sheet.
     */
    private fun DemoTour.tabsTop(): Float = runCatching {
        val search = hasText("Search songs", substring = true)
        ui.onAllNodes(search).fetchSemanticsNodes().first().boundsInWindow.top
    }.getOrElse { Float.NEGATIVE_INFINITY }

    private fun DemoTour.sheetClick(m: SemanticsMatcher) {
        // A pointer press, not a semantics click: the picker's tabs answer only to the pointer.
        runCatching { dialogPopupItem(m, topmost = true, below = tabsTop()).node.performTouchInput { click() } }
        ui.waitForIdle()
        pause(SHEET_CLICK_HOLD)
    }

    private fun DemoTour.phones() {
        say("phones")
        caption("Plan from phones", "The worship leader, the pastor, from home")
        say("sync-on")
        screen.value = "server"
        openPanel("Settings — Server", SERVER_W, SERVER_H)
        point("Calendar sync", "Switch it on here", hasText("Calendar sync"))
        say("relay")
        val relay = hasText("Plan services from the mobile app", substring = true)
        point("Through the relay", "No account, no church WiFi needed", relay)
        closePanel()
        say("sync-calendar")
        screen.value = "calendar"
        openPanel("Calendar", CALENDAR_W, CALENDAR_H)
        quietPanelClick(hasText("Calendar settings"))
        sheetClick(hasText("Defaults"))
        val calendarSwitch = hasText("Sync with the cloud and mobile devices")
        sheet("In the calendar's settings too", "Sync with the cloud and mobile devices", calendarSwitch)
        closeSheet()
        say("secure")
        caption("Private, even from us", "Encrypted before it leaves; the key travels by QR code")
        say("invite")
        point("Invite a phone", "In the calendar's header", hasText("Invite a phone"))
        closePanel()
        say("invite-qr")
        screen.value = "qr"
        openPanel("Invite a phone", QR_W, QR_H)
        point("Scan this code", "Works for 15 minutes, from anywhere", hasText("Scan this code", substring = true))
        closePanel()
        say("enrolled")
        screen.value = "server"
        openPanel("Settings — Server", SERVER_W, SERVER_H)
        val phone = hasText("Pastor Mark's iPhone", substring = true) or hasText("Phones")
        point("Enrolled phones", "Revoke when a volunteer moves on", phone)
        say("mobile")
        caption("On the phone", "See the month, plan, build the run of show")
        say("sync-timing")
        caption("Every five minutes", "At startup, and on Sync now")
        closePanel()
    }

    /** Points at [m] in the Schedule panel, or says the line over a caption when it isn't there. */
    private fun DemoTour.inSchedule(heading: String, sub: String, m: SemanticsMatcher, tabArea: Boolean = false) {
        val target = runCatching {
            if (tabArea) find(m) { it.left > dp(380f) } else find(m) { it.right < dp(720f) && it.top > dp(100f) }
        }.getOrNull()
        if (target != null) note(heading, sub, target) else caption(heading, sub)
    }

    /** Closes whatever sheet is open over the calendar, by its × (labeled Close), or Cancel or Done. */
    private fun DemoTour.closeSheet() {
        repeat(2) {
            runCatching {
                val close = ui.onAllNodes(hasContentDescription("Close") or hasText("Cancel") or hasText("Done"))
                close[close.fetchSemanticsNodes().size - 1].performClick()
            }
            ui.waitForIdle()
        }
    }

    private fun seed(folder: File) {
        File(folder, "calendar.json").writeText(
            Json { encodeDefaults = true }.encodeToString(CalendarDocument.serializer(), month()),
        )
        PresetStore(folder).save(PresetDocument(presets = listOf(ANNOUNCEMENTS, COUNTDOWN, WELCOME).map {
            ItemPreset(id = "preset-${it.id}", name = it.displayText, item = it)
        }))
    }
}

/** Loads a planned service into the app's schedule, as the app's own host does; set once the tour has it. */
private var loadInto: (List<ScheduleItem>, Map<String, RowTiming>, Boolean, String?) -> Unit = { _, _, _, _ -> }

/** Which window the panel shows: the calendar, or Settings → Server. */
private val screen = mutableStateOf("calendar")

private val syncSettings = org.churchpresenter.settings.CalendarSyncSettings(enabled = true)

private val TODAY: LocalDate = LocalDate.of(2026, 11, 1)
private val NOW: LocalTime = LocalTime.of(9, 20)

private val ANNOUNCEMENTS = ScheduleItem.PictureItem(
    id = "announcements",
    folderPath = File(LIBRARY, "Gallery").absolutePath,
    folderName = "Gallery",
    imageCount = 12,
    displayText = "Sunday announcements",
)
private val COUNTDOWN = ScheduleItem.AnnouncementItem(
    id = "countdown",
    text = "",
    isTimer = true,
    timerMinutes = 5,
    timerExpiredText = "Welcome!",
    displayText = "Countdown · 5 min",
)
private val WELCOME = ScheduleItem.MediaItem(
    id = "welcome",
    mediaUrl = File(LIBRARY, "Media/Welcome Loop.mp4").absolutePath,
    mediaTitle = "Welcome video",
    mediaType = "video",
)

private fun label(id: String, text: String, color: String) =
    ScheduleItem.LabelItem(id = id, text = text, textColor = "#FFFFFF", backgroundColor = color)

private fun song(number: Int, title: String) = ScheduleItem.SongItem(
    id = "song-$number", songNumber = number, title = title, songbook = "Hymnal", songId = "Hymnal::$number",
)

/** This morning's service, with the pre-service loop the tour explains, and a month around it. */
private fun month(): CalendarDocument {
    val rows = listOf(
        label("pre", "Pre-Service", "#64748B"),
        ANNOUNCEMENTS,
        COUNTDOWN,
        label("worship", "Worship", "#5B9DF5"),
        song(12, "Amazing Grace"),
        song(320, "Come Thou Fount Of Every Blessing"),
        label("word", "Word", "#E8A33D"),
        ScheduleItem.BibleVerseItem(
            id = "reading",
            bookName = "John",
            chapter = 3,
            verseNumber = 16,
            verseText = "For God so loved the world, that he gave his only begotten Son.",
            verseRange = "16-17",
        ),
        ScheduleItem.PresentationItem(
            id = "sermon",
            filePath = File(LIBRARY, "Decks/Sermon.pdf").absolutePath,
            fileName = "Sermon",
            slideCount = 6,
            fileType = "pdf",
        ),
        ScheduleItem.MinistryItem(id = "violin", title = "Violin solo", detail = "Josh"),
        label("response", "Response", "#22C55E"),
        song(455, "It Is Well With My Soul"),
        // Not in the library: the calendar flags it, for the line about items that need attention.
        song(999, "Shine, Jesus, Shine"),
        label("closing", "Closing", "#A855F7"),
        WELCOME,
    )
    val sunday = PlannedService(
        id = "sunday-1",
        date = TODAY.toString(),
        name = "Sunday Morning",
        startTime = "10:00",
        items = rows,
        plannedSeconds = mapOf(
            "song-12" to 300, "song-320" to 270, "reading" to 120, "sermon" to 1800, "violin" to 240,
            "song-455" to 300,
        ),
        timing = mapOf(
            "announcements" to RowTiming(startAt = "09:30", repeats = 0),
            "countdown" to RowTiming(startAt = "09:55", atEnd = RowEnd.NEXT),
            "song-12" to RowTiming(followsPrevious = true),
            "welcome" to RowTiming(followsPrevious = true, repeats = 0),
        ),
    )
    fun sundayOn(day: Int) = PlannedService(
        id = "sunday-$day", date = TODAY.withDayOfMonth(day).toString(), name = "Sunday Morning",
        startTime = "10:00", items = listOf(label("w$day", "Worship", "#5B9DF5"), song(12, "Amazing Grace")),
    )
    return CalendarDocument(
        services = listOf(
            sunday,
            sundayOn(8),
            sundayOn(15),
            sundayOn(22),
            PlannedService(
                id = "prayer", date = TODAY.withDayOfMonth(4).toString(), name = "Midweek Prayer",
                startTime = "19:00", kind = ServiceKind.MIDWEEK.id,
            ),
            PlannedService(
                id = "thanksgiving", date = TODAY.withDayOfMonth(26).toString(), name = "Thanksgiving Service",
                startTime = "18:30", kind = ServiceKind.SPECIAL.id,
            ),
        ),
    )
}

private const val SHEET_WAIT_MS = 5_000L
private const val SHEET_CLICK_HOLD = 0.1
private const val CALENDAR_W = 1300f
private const val CALENDAR_H = 850f

/** Three phones already enrolled, for the card's list. */
private val SAMPLE_PHONES = listOf(
    PairedDevice("phone-1", "Pastor Mark's iPhone", "2026-09-06T10:15:00Z", "2026-10-31T18:42:00Z"),
    PairedDevice("phone-2", "Worship leader · Sarah", "2026-09-13T09:02:00Z", "2026-11-01T07:58:00Z"),
    PairedDevice("phone-3", "Announcements · Dave", "2026-10-04T12:30:00Z", "2026-10-29T20:11:00Z"),
)
private const val QR_W = 460f
private const val QR_H = 620f
private val SAMPLE_ENROLLMENT = CalendarEnrollment(
    relayUrl = "https://relay.churchpresenter.org",
    instanceId = "demo-church",
    deviceId = "demo-phone",
    deviceToken = "demo-token",
    instanceKey = "demo-key",
)
private const val SERVER_W = 1100f
private const val SERVER_H = 760f
