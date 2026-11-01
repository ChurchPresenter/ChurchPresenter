@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.screenshot.LIBRARY
import org.churchpresenter.calendar.CalendarCloudSync
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.sync.SyncOutcome
import org.churchpresenter.calendar.ui.CalendarApp
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.dialogs.RemoteEventDialogContent
import org.churchpresenter.dialogs.resolveRemoteEventPresentation
import org.churchpresenter.server.CalendarEnrollment
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.server.CalendarSyncStatus
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.RemoteEvent
import org.churchpresenter.server.RemoteEventType
import org.churchpresenter.serverui.CalendarEnrollQrContent
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.serverui.ServerSettingsTab
import org.churchpresenter.settings.CalendarSyncSettings
import org.junit.Assume.assumeTrue
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test

/**
 * The mobile app's Calendar mode, as one path a viewer can follow: choosing it, joining the church
 * computer (by invite, or asked for over the Wi-Fi), planning a Sunday on the phone and copying it
 * ahead, and the computer's Calendar Manager holding the same plan. The phone is the mobile app's own
 * screens (see [DemoPhone]); the computer's side is the real app's, beside it whenever both take part.
 */
class CalendarModeDemoTour {
    private val companion = CompanionServer(shutdownGraceMs = 0)

    @Test
    fun calendarMode() {
        assumeTrue("ChurchPresenter-Mobile is not checked out beside this repo", MOBILE_ROOT.isDirectory)
        TestSingletons.latchToTestHome()
        val folder = File(LIBRARY, "CalendarMode").apply { deleteRecursively(); mkdirs() }
        File(folder, "calendar.json").writeText(
            Json { encodeDefaults = true }.encodeToString(CalendarDocument.serializer(), plan()),
        )
        val sync = CalendarSyncService(
            folder,
            File(LIBRARY, "Songs"),
            settings = { SYNC_SETTINGS },
            saveSettings = {},
            initialStatus = CalendarSyncStatus.Synced(
                Instant.now().minusSeconds(SYNCED_AGO_S).toString(),
                SyncOutcome(0L, 0, 0, 0),
            ),
        )
        try {
            recordTour(
                "calendar-mode",
                tab = "Calendar Mode",
                liveLabel = "Planned on the phone",
                steps = 12,
                heading = "Mobile " to "",
                // The Calendar Manager's schedule button showing, which a new install hides; sync on.
                settings = {
                    it.copy(
                        hiddenScheduleButtons = emptySet(),
                        calendarSync = SYNC_SETTINGS,
                        calendarStorageDirectory = folder.path,
                    )
                },
                panel = { settings, change ->
                    Box(Modifier.size(PANEL_W.dp, PANEL_H.dp), contentAlignment = Alignment.Center) {
                        when (desk.value) {
                            "" -> Phone()
                            SERVER -> Box(Modifier.size(SERVER_W.dp, SERVER_H.dp)) {
                                ServerSettingsTab(settings, change, companion, remember { RemoteClientManager() }, sync)
                            }
                            CALENDAR -> Box(Modifier.size(CALENDAR_W.dp, CALENDAR_H.dp)) { DeskCalendar(folder) }
                            else -> Pair()
                        }
                    }
                },
            ) { calendarTour() }
        } finally {
            companion.stop()
        }
    }

    private fun DemoTour.calendarTour() {
        say("intro")
        intro("Plan services on your phone, all week.", establish = false)
        show("screenshots/mode-picker/calendar-selected")
        openPanel("Phone", PANEL_W, PANEL_H)
        say("why")
        panelNote("How will you present?", "The first time you open the app", phoneScreen())
        segment("mode")
        say("mode")
        panelNote("Calendar", "Tap it", screenBox(CALENDAR_CARD), numbered = true)
        say("continue")
        panelNote("Continue", "Tap it", screenBox(CONTINUE))
        show("screenshots/demo/home-not-enrolled")
        say("home")
        panelNote("Not joined yet", "The cloud is crossed out", screenBox(CLOUD_NOT_ENROLLED))
        segment("join")
        join()
        segment("month")
        month()
        segment("run")
        runOfShow()
        segment("copy")
        copyAhead()
        segment("synced")
        closePanel()
        desk.value = CALENDAR
        openPanel("Calendar Manager", PANEL_W, PANEL_H)
        say("synced")
        point("The same Sunday Morning", "On the church computer", hasText("Sunday Morning"))
        segment("end")
        closePanel()
        say("outro")
        outro()
    }

    /** Calendar sync on at the computer; the phone's sheet; an invite; or the Wi-Fi ask, allowed. */
    private fun DemoTour.join() {
        closePanel()
        say("settings-open")
        val settings = find(hasContentDescription("Settings", substring = true))
        step("Settings", "On the church computer", settings, numbered = true) {}
        desk.value = SERVER
        openPanel("Settings — Server", PANEL_W, PANEL_H)
        say("desktop-first")
        point("Calendar sync", "Switch it on", hasText("Calendar sync"))
        closePanel()
        desk.value = ""
        openPanel("Phone", PANEL_W, PANEL_H)
        say("sync-open")
        panelNote("The cloud button", "Tap it", screenBox(CLOUD_NOT_ENROLLED), numbered = true)
        show("screenshots/calendar-sync/idle")
        say("sync")
        panelNote("Sync with the church computer", "Two ways to join", sheetScreen())
        closePanel()
        say("calendar-open")
        val manager = find(hasContentDescription("Calendar Manager")) {
            it.right < dp(TOOLBAR_RIGHT_DP) && it.top < dp(TOOLBAR_TOP_DP)
        }
        step("Calendar Manager", "On the church computer", manager, numbered = true) {}
        desk.value = CALENDAR
        openPanel("Calendar Manager", PANEL_W, PANEL_H)
        say("invite")
        point("Invite a phone", "In the calendar's header", hasText("Invite a phone"))
        closePanel()
        desk.value = QR
        openPanel("Computer and phone", PANEL_W, PANEL_H)
        say("invite-qr")
        val scan = screenBox(SCAN_IN_IDLE, sheetScreen())
        panelNote("Scan QR Code", "Point the camera at the computer", scan, numbered = true)
        say("ask")
        val ask = screenBox(ASK_IN_IDLE, sheetScreen())
        panelNote("Ask over the church WiFi instead", "No invite needed", ask, numbered = true)
        desk.value = PROMPT
        show("screenshots/calendar-sync/waiting")
        say("waiting")
        panelNote("A code on the phone", "482 913", sheetScreen())
        say("prompt")
        panelNote("The same code on the computer", "Check that they match", panelItem(hasText("482 913")))
        say("allow")
        val allow = runCatching { panelItem(hasContentDescription("Allow")) }.getOrNull()
        if (allow != null) {
            panelNote("Allow", "On the church computer", allow, numbered = true)
        } else {
            caption("Allow", "On the church computer")
        }
        desk.value = QR
        show("screenshots/calendar-sync/scan-qr")
        say("scan")
        panelNote("Scan QR Code", "To finish", screenBox(SCAN_IN_SCAN, sheetScreen()))
        desk.value = ""
        show("screenshots/calendar-sync/enrolled")
        say("enrolled")
        panelNote("Syncing", "Stop syncing is here", sheetScreen())
    }

    /** The month once joined: the sync line, the days, the types; a new Sunday from an empty day. */
    private fun DemoTour.month() {
        show("screenshots/demo/home-month")
        say("synced-home")
        panelNote("The sync line", "Last synced, and next", screenBox(SYNC_LINE))
        say("marked")
        panelNote("The selected day", "Its services, listed", screenBox(DAY_LIST))
        say("types")
        panelNote("Service types", "A color for each", screenBox(TYPES))
        say("tap-day")
        panelNote("The 13th", "Tap it", screenBox(DAY_13), numbered = true)
        show("screenshots/demo/home-empty-day")
        say("empty-day")
        panelNote("Add service", "Or copy last Sunday", screenBox(ADD_SERVICE))
        show("screenshots/calendar-service/new")
        say("new")
        panelNote("New service", "Name, time, type, start from", sheetScreen(), numbered = true)
        say("create")
        panelNote("Add service", "Tap it", screenBox(CREATE_SERVICE, sheetScreen()))
        show("screenshots/calendar/run-of-show-empty")
    }

    /** Adding to the run of show, one item's editor and its timing, Save, and Armed. */
    private fun DemoTour.runOfShow() {
        say("empty-run")
        panelNote("The plus button", "Tap it", screenBox(PLUS), numbered = true)
        show("screenshots/demo/add-sheet")
        say("add")
        panelNote("Five tabs", "Songs, Bible, Section, Ministry, Presets", screenBox(TABS))
        say("add-song")
        panelNote("Amazing Grace", "Tap its plus", screenBox(FIRST_SONG_PLUS), numbered = true)
        show("screenshots/calendar/run-of-show")
        say("filled")
        panelNote("Run of show", "In order, in sections", phoneScreen())
        say("open-item")
        panelNote("Amazing Grace", "Tap it", screenBox(FIRST_ROW), numbered = true)
        show("screenshots/calendar-row/song")
        say("item")
        panelNote("One item", "Its length and its timing", sheetScreen())
        say("timing")
        panelNote("Starts, Runs, Repeats, At end", "Cued, a loop, then what", screenBox(TIMING, sheetScreen()))
        say("save")
        panelNote("Save", "Tap it", screenBox(SAVE, sheetScreen()))
        show("screenshots/calendar/run-of-show")
        say("armed")
        panelNote("Armed", "The timed cues run by themselves", screenBox(ARMED), numbered = true)
    }

    /** The copy sheet, weekly, four times. */
    private fun DemoTour.copyAhead() {
        say("copy-open")
        panelNote("The copy button", "Tap it", screenBox(COPY), numbered = true)
        show("screenshots/calendar-copy/weekly")
        say("copy")
        panelNote("Copy ahead", "Weekly, every two weeks, monthly", phoneScreen())
        say("create-copies")
        panelNote("Create 4", "The next four Sundays", screenBox(CREATE_COPIES), numbered = true)
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

/** The phone, with the sheet open on it floated over it. */
@Composable
private fun Phone(sheetWidth: Float? = null) {
    Box(contentAlignment = Alignment.Center) {
        DemoPhone(phone.value)
        if (sheetWidth == null) DemoSheet(sheet.value) else DemoSheet(sheet.value, maxWidth = sheetWidth)
    }
}

/** The computer's side — its QR code, or its question — beside the phone. */
@Composable
private fun Pair() {
    Row(horizontalArrangement = Arrangement.spacedBy(PAIR_GAP_DP.dp), verticalAlignment = Alignment.CenterVertically) {
        if (desk.value == QR) {
            Box(Modifier.size(QR_W.dp, QR_H.dp)) { CalendarEnrollQrContent(ENROLLMENT, onDismiss = {}) }
        } else {
            Box(Modifier.size(PROMPT_W.dp, PROMPT_H.dp)) { EnrollRequest() }
        }
        Phone(sheetWidth = PAIR_SHEET_W)
    }
}

/** The church computer's Calendar Manager, on the plan the phone made. */
@Composable
private fun DeskCalendar(folder: File) {
    CalendarApp(
        storeFolder = folder,
        songFolder = File(LIBRARY, "Songs"),
        host = CalendarHost(
            loadIntoSchedule = { _, _, _, _, _ -> },
            // Sync on, so the header offers Invite a phone.
            cloudSync = CalendarCloudSync(enabled = { true }, setEnabled = {}),
        ),
        today = TODAY,
        now = { NOW },
        watchStoreFolder = false,
    )
}

/** What the church computer asks when a phone on its Wi-Fi asks to join the calendar. */
@Composable
private fun EnrollRequest() {
    val event = RemoteEvent(
        type = RemoteEventType.CALENDAR_ENROLL,
        title = "Pastor's iPhone",
        detail = "482 913",
        clientId = "f2a8c113",
        clientLabel = "Pastor's iPhone",
    )
    // Labelled the way the app labels it (RemoteEventDialog), not with words of the tour's own.
    val shown = resolveRemoteEventPresentation(event, 1, isClientKnownAllowed = false, isClientKnownBlocked = false)
    RemoteEventDialogContent(
        event = event,
        actionLabel = shown.actionLabel,
        typeIcon = shown.typeIcon,
        typeAccent = shown.typeAccent,
        bodyTitle = shown.bodyTitle,
        remaining = 0,
        showAllowPermanently = false,
        isClientKnownAllowed = false,
        isClientKnownBlocked = false,
        isInstanceLinkFollower = false,
        onAllow = {},
        onAllowForSession = {},
        onAllowPermanently = {},
        onBlockForSession = {},
        onBlockPermanently = {},
        onDeny = {},
    )
}

private fun label(id: String, text: String, color: String) =
    ScheduleItem.LabelItem(id = id, text = text, textColor = "#FFFFFF", backgroundColor = color)

private fun song(number: Int, title: String) = ScheduleItem.SongItem(
    id = "song-$number", songNumber = number, title = title, songbook = "Hymnal", songId = "Hymnal::$number",
)

/** The phone's Sunday Morning, as the church computer holds it once synced. */
private fun plan(): CalendarDocument {
    val sunday = PlannedService(
        id = "s1",
        date = TODAY.toString(),
        name = "Sunday Morning",
        startTime = "10:00",
        items = listOf(
            label("worship", "Worship", "#8B5CF6"),
            song(42, "Amazing Grace"),
            song(108, "How Great Thou Art"),
            label("word", "Word", "#E8A33D"),
            ScheduleItem.BibleVerseItem(
                id = "reading",
                bookName = "John",
                chapter = 3,
                verseNumber = 16,
                verseText = "For God so loved the world, that he gave his only begotten Son.",
                verseRange = "16-17",
            ),
            ScheduleItem.MinistryItem(id = "welcome", title = "Welcome & notices", detail = "Anna"),
            ScheduleItem.AnnouncementItem(
                id = "countdown",
                text = "",
                isTimer = true,
                timerMinutes = 5,
                displayText = "Countdown",
            ),
        ),
        plannedSeconds = mapOf("song-42" to 270, "song-108" to 300, "reading" to 120, "welcome" to 300),
        timing = mapOf("song-42" to RowTiming()),
    )
    return CalendarDocument(services = listOf(sunday))
}

private val phone = mutableStateOf("")
private val sheet = mutableStateOf("")
private val desk = mutableStateOf("")

private const val SERVER = "server"
private const val CALENDAR = "calendar"
private const val QR = "qr"
private const val PROMPT = "prompt"

private val TODAY: LocalDate = LocalDate.of(2026, 9, 20)
private val NOW: LocalTime = LocalTime.of(9, 20)

private val SYNC_SETTINGS = CalendarSyncSettings(
    enabled = true,
    instanceId = "demo-calendar",
    desktopToken = "demoDesktopToken0123456789",
    instanceKey = "ZGVtby1jYWxlbmRhci1rZXktMDEyMzQ1Njc4OWFi",
)
private val ENROLLMENT = CalendarEnrollment(
    relayUrl = "https://relay.churchpresenter.org",
    instanceId = "demo-church",
    deviceId = "demo-phone",
    deviceToken = "demo-token",
    instanceKey = "demo-key",
)

// Where things sit on the screens, as fractions of their width and height.
private val CALENDAR_CARD = box(0.03f, 0.57f, 0.97f, 0.75f)
private val CONTINUE = box(0.03f, 0.77f, 0.97f, 0.85f)
private val CLOUD_NOT_ENROLLED = box(0.67f, 0.014f, 0.79f, 0.07f)
private val SCAN_IN_IDLE = box(0.0f, 0.36f, 1f, 0.53f)
private val ASK_IN_IDLE = box(0.0f, 0.74f, 1f, 0.92f)
private val SCAN_IN_SCAN = box(0.0f, 0.44f, 1f, 0.67f)
private val SYNC_LINE = box(0.42f, 0.012f, 0.99f, 0.1f)
private val DAY_LIST = box(0.02f, 0.49f, 0.98f, 0.6f)
private val TYPES = box(0.02f, 0.62f, 0.98f, 0.74f)
private val DAY_13 = box(0.05f, 0.275f, 0.16f, 0.325f)
private val ADD_SERVICE = box(0.02f, 0.67f, 0.98f, 0.8f)
private val CREATE_SERVICE = box(0.5f, 0.91f, 0.99f, 0.99f)
private val PLUS = box(0.02f, 0.92f, 0.17f, 0.99f)
private val TABS = box(0.0f, 0.11f, 1f, 0.18f)
private val FIRST_SONG_PLUS = box(0.86f, 0.18f, 0.99f, 0.28f)
private val FIRST_ROW = box(0.02f, 0.12f, 0.98f, 0.2f)
private val TIMING = box(0.0f, 0.28f, 1f, 0.76f)
private val SAVE = box(0.58f, 0.84f, 0.99f, 0.97f)
private val ARMED = box(0.8f, 0.02f, 0.98f, 0.08f)
private val COPY = box(0.15f, 0.92f, 0.3f, 0.99f)
private val CREATE_COPIES = box(0.5f, 0.92f, 0.99f, 0.99f)

private const val PANEL_W = 1500f
private const val PANEL_H = 900f
private const val SERVER_W = 1100f
private const val SERVER_H = 760f
private const val CALENDAR_W = 1300f
private const val CALENDAR_H = 850f
private const val QR_W = 460f
private const val QR_H = 620f
private const val PROMPT_W = 700f
private const val PROMPT_H = 560f
private const val PAIR_GAP_DP = 40
private const val PAIR_SHEET_W = 560f
private const val TOOLBAR_RIGHT_DP = 720f
private const val TOOLBAR_TOP_DP = 100f
private const val SYNCED_AGO_S = 120L
