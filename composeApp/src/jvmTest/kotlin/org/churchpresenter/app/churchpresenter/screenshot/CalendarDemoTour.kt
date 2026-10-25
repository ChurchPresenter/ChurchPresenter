@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.PresetDocument
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.ui.CalendarApp
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.serverui.ServerSettingsTab
import java.io.File
import java.time.LocalDate
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
        val sync = CalendarSyncService(folder, File(LIBRARY, "Songs"), settings = { syncSettings }, saveSettings = {})
        recordTour(
            "calendar",
            tab = "Calendar Manager",
            liveLabel = "Plan ahead",
            steps = 12,
            heading = "The " to "",
            settings = ::threeScreens,
            panel = { settings, change ->
                if (screen.value == "server") {
                    Box(Modifier.size(SERVER_W.dp, SERVER_H.dp)) {
                        ServerSettingsTab(settings, change, companion, remember { RemoteClientManager() }, sync)
                    }
                } else {
                    Box(Modifier.size(CALENDAR_W.dp, CALENDAR_H.dp)) {
                        CalendarApp(
                            storeFolder = folder,
                            songFolder = File(LIBRARY, "Songs"),
                            host = CalendarHost(loadIntoSchedule = { items, timing, replace, _, start ->
                                loadInto(items, timing, replace, start)
                            }),
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
        sheet("Songs", "By title or number", hasText("Songs"))
        say("add-bible")
        sheetClick(hasText("Bible"))
        sheet("Bible", "Type a reference, or browse", hasText("Bible"))
        say("add-section")
        sheetClick(hasText("Section"))
        sheet("Section", "Pre-Service, Worship, Word…", hasText("Section"))
        say("add-ministry")
        sheetClick(hasText("Ministry"))
        sheet("Ministry", "Planned and timed, never on screen", hasText("Ministry"))
        say("add-preset")
        sheetClick(hasText("Presets"))
        sheet("Presets", "Reusable pieces of a service", hasText("Sunday announcements") or hasText("Presets"))
        say("presets-how")
        caption("Save preset", "On Pictures, Presentation, Media, Announcements, Canvas")
        closeSheet()
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
        point("Rows that need attention", "Click to fix", hasText("need attention", substring = true))
    }

    /** The pre-service loop: the slideshow from −30, the countdown from −5, armed. */
    private fun DemoTour.loop() {
        say("loop-intro")
        point("A pre-service loop", "Slideshow from −30, countdown from −5", hasText("Sunday announcements"))
        say("timing")
        escape()
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
        val copy = hasContentDescription("Copy this service to another date, or repeat it")
        panelNote("Copy and repeat", "Weekly, every 2 weeks, monthly", seek(copy), click = true, numbered = true)
        sheet("Repeat", "Once, weekly, every 2 weeks, monthly", hasText("Weekly"))
        say("series")
        sheet("Each copy its own", "Or All in series", hasText("How many times") or hasText("Repeat"))
        closeSheet()
        say("template")
        val template = hasContentDescription("Save this run of show as a template")
        panelNote("Save as template", "Sections, or items too", seek(template), click = true)
        sheet("Sections, or items too", "Timings and presets included", hasText("Items") or hasText("Template name"))
        closeSheet()
    }

    private fun DemoTour.loading() {
        say("load")
        panelNote("Load into Schedule", "Replace, or add to the end", seek(hasText("Load into Schedule")), click = true)
        sheetClick(hasText("Replace and load"))
        say("auto-load")
        point("Load the service automatically", "5 minutes ahead, up to 2 hours", hasText("Turn on"))
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
        inSchedule("Length and start time", "Bold: starts on its own", hasText("9:30", substring = true))
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
        caption("Loading Sunday Morning in 10 min", "Load now, or let it load itself")
    }

    /** Points at [m] in the sheet open over the calendar, or says the line over a caption. */
    private fun DemoTour.sheet(heading: String, sub: String, m: SemanticsMatcher) {
        val target = runCatching { dialogPopupItem(m) }.getOrNull()
        if (target != null) {
            panelNote(heading, sub, target)
        } else {
            File(System.getenv("CP_DEMO_OUT"), "missed.txt").appendText("$heading\n")
            caption(heading, sub)
        }
    }

    private fun DemoTour.sheetClick(m: SemanticsMatcher) {
        // A pointer press, not a semantics click: the picker's tabs answer only to the pointer.
        runCatching { dialogPopupItem(m).node.performTouchInput { click() } }
        ui.waitForIdle()
    }

    private fun DemoTour.phones() {
        say("phones")
        caption("Plan from phones", "The worship leader, the pastor, from home")
        say("sync-on")
        screen.value = "server"
        openPanel("Settings — Server", SERVER_W, SERVER_H)
        point("Calendar on phones & tablets", "Sync the calendar with phones", hasText("Calendar on phones & tablets"))
        say("relay")
        point("Through the relay", "No account, no church WiFi needed", hasText("Sync the calendar with phones"))
        say("secure")
        caption("Private, even from us", "Encrypted before it leaves; the key travels by QR code")
        say("invite")
        point("Invite a phone", "Scan the code with the app", hasText("Invite a phone", substring = true))
        say("enrolled")
        point("Enrolled phones", "Revoke when a volunteer moves on", hasText("Phones") or hasText("Enrolled phones"))
        say("mobile")
        caption("On the phone", "See the month, plan, build the run of show")
        say("sync-timing")
        caption("Every five minutes", "At startup, and on Sync now")
        closePanel()
    }

    /** Points at [m] in the Schedule panel, or says the line over a caption when it isn't there. */
    private fun DemoTour.inSchedule(heading: String, sub: String, m: SemanticsMatcher) {
        val target = runCatching { find(m) { it.right < dp(720f) && it.top > dp(100f) } }.getOrNull()
        if (target != null) note(heading, sub, target) else caption(heading, sub)
    }

    /** Closes whatever sheet is open over the calendar: its own button, or Escape (the picker has only an ×). */
    private fun DemoTour.closeSheet() {
        sheetClick(hasText("Cancel") or hasText("Done"))
        escape()
    }

    private fun DemoTour.escape() {
        repeat(2) {
            runCatching {
                val roots = ui.onAllNodes(isRoot())
                roots[roots.fetchSemanticsNodes().size - 1].performKeyInput { pressKey(Key.Escape) }
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

private val syncSettings = org.churchpresenter.settings.CalendarSyncSettings()

internal val TODAY: LocalDate = LocalDate.of(2026, 11, 1)
internal val NOW: LocalTime = LocalTime.of(9, 20)

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
internal fun month(): CalendarDocument {
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
private const val CALENDAR_W = 1300f
private const val CALENDAR_H = 850f
private const val SERVER_W = 1100f
private const val SERVER_H = 760f
