@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.appsettings.SystemSettingsTab
import org.churchpresenter.converter.ui.App
import org.churchpresenter.converter.ui.ConverterTab
import org.churchpresenter.dialogs.RemoteEventDialogContent
import org.churchpresenter.helper.ui.WickIntro
import org.churchpresenter.planningcenter.PlanningCenterClient
import org.churchpresenter.planningcenter.ui.PlanningCenterImportDialog
import org.churchpresenter.planningcenter.ui.PlanningCenterImportServices
import org.churchpresenter.server.RemoteEvent
import org.churchpresenter.server.RemoteEventType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.PlanningCenterSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.factoryOutputProfile
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.songlibrary.ui.SongLibraryApp
import org.churchpresenter.statistics.CCLIReportContent
import org.churchpresenter.statistics.StatisticsManager
import org.churchpresenter.theme.ThemeMode
import java.io.File
import kotlin.test.Test

/** A first look around the main window: the schedule, the tabs, going live, the previews and the tools. */
class OverviewDemoTour {

    @Test
    fun overview() {
        TestSingletons.latchToTestHome()
        AppPreviewStatisticsScreenshotTest.seedSixMonths()
        mockkObject(PlanningCenterClient)
        try {
            samplePlan()
            recordTour(
                "overview",
                tab = "ChurchPresenter",
                liveLabel = "A first look",
                steps = 8,
                heading = "" to "",
                settings = ::threeScreens,
                panel = { settings, change -> Panel(settings, change) },
            ) { overviewTour() }
        } finally {
            unmockkObject(PlanningCenterClient)
        }
    }

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
        settingsAndPhones()
        planning()
        highlights()
        say("outro")
        outro()
    }

    /** Each tab opened as it is named, then back to the Bible. */
    private fun DemoTour.tabs() {
        say("tabs")
        note("The tabs", "A different kind of content each", tab("Bible"))
        say("tabs-word")
        tour(
            stop("Bible", "Any passage in seconds", tab("Bible")),
            stop("Songs", "Your song library", tab("Songs")),
            click = true,
        )
        say("tabs-media")
        tour(
            stop("Pictures", "Your photos", tab("Pictures")),
            stop("Presentation", "Your slides", tab("Presentation")),
            stop("Media", "Videos and music", tab("Media")),
            click = true,
        )
        say("tabs-more")
        tour(
            stop("Lower Third", "A name banner", tab("Lower Third")),
            stop("Announcements", "Messages and countdowns", tab("Announcements")),
            click = true,
        )
        say("tabs-rest")
        tour(
            stop("Web", "Any web page", tab("Web")),
            stop("Canvas", "Screens you design", tab("Canvas")),
            stop("Dictionary", "Hebrew and Greek", tab("Dictionary")),
            click = true,
        )
        say("tab-visibility")
        step("Tab Visibility", "Show or hide tabs", button("Tab Visibility"), numbered = true)
        // Closed again by the same button, so the menu doesn't stay over the next steps.
        quietClick(button("Tab Visibility"))
        quietClick(tab("Bible"))
        say("columns")
        note("Resize panels and columns", "Drag the edge", find(hasText("CHAPTER")) { it.left > dp(380f) })
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
        say("three-screens")
        val tiles = findAll(hasContentDescription("Hide this preview"))
        tour(
            stop("Projector", "Full screen", tiles[0]),
            stop("Lower thirds", "Names and titles only", tiles[1]),
            stop("Stage monitor", "Time, next item, notes", tiles[2]),
        )
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

    private fun DemoTour.settingsAndPhones() {
        say("settings")
        step("Settings", "Screens, profiles, Bibles, server", button("Settings"), numbered = true) {}
        show("system", "Settings — System", SYSTEM_W, SYSTEM_H)
        say("storage")
        val folders = panelItem(hasText("Bibles", substring = true))
        panelNote("Bibles and songs folders", "Safe through every update", folders)
        closePanel()
        say("remote")
        caption("Android and iOS apps", "For the people you trust")
        show("permission", "Allow this device?", PERMISSION_W, PERMISSION_H)
        say("remote-what")
        val asker = panelItem(hasText("Sound desk iPad", substring = true))
        panelNote("You stay in charge", "Allow, trust or deny", asker)
        closePanel()
        say("standalone")
        caption("Standalone", "The phone presents on its own")
    }

    private fun DemoTour.planning() {
        say("planning")
        val import = button("Import from Planning Center")
        step("Import from Planning Center", "A service plan, songs and all", import) {}
        show("pco", "Import from Planning Center", PCO_W, PCO_H)
        // The next plan opens on its own, items and all.
        waitFor("the plan's items have loaded", PANEL_WAIT_MS) { panelItems(hasText("Amazing Grace")).isNotEmpty() }
        panelNote("The plan's items", "Songs, readings, announcements", panelItem(hasText("Amazing Grace")))
        closePanel()
        say("calendar")
        note("Calendar Manager", "Services planned weeks ahead", button("Calendar Manager"))
        say("song-library")
        show("library", "Song Library Manager", LIBRARY_W, LIBRARY_H)
        waitFor("the library has loaded", PANEL_WAIT_MS) { panelItems(hasText("TITLE")).isNotEmpty() }
        panelNote("Song Library Manager", "Every song in one grid", panelItem(hasText("TITLE")))
        closePanel()
        say("converter")
        show("converter", "Song and Bible Converter", CONVERTER_W, CONVERTER_H)
        panelNote("The converter", "From other programs, PDF, PowerPoint, Word", panelItems(hasText("Songs")).first())
        closePanel()
    }

    /** What there isn't room to show stays a caption; what has a window opens it. */
    private fun DemoTour.highlights() {
        say("more")
        caption("There's much more", "A few highlights")
        say("more-languages")
        caption("35 languages, 12 themes", "And it all works offline")
        say("more-bible")
        caption("Up to six translations", "And it can follow the preacher")
        say("more-outputs")
        note("Every screen its own look", "Projectors, TVs, DeckLink, NDI, OBS", button("Hide this preview"))
        say("more-stage")
        val stage = findAll(hasContentDescription("Hide this preview"))[2]
        note("Stage monitor", "Time, next item, notes and chords", stage)
        say("more-integrations")
        caption("ATEM, OBS, Companion", "And Stream Deck")
        say("more-reporting")
        show("ccli", "CCLI Report", CCLI_W, CCLI_H)
        panelNote("CCLI report", "Most used songs, ready to export", panelItems(hasText("Amazing Grace")).first())
        closePanel()
        say("more-link")
        caption("Link a second copy", "An overflow room or another campus")
        say("wick")
        show("wick", "Meet Wick", WICK_W, WICK_H)
        val wick = panelItems(hasText("Wick", substring = true)).first()
        panelNote("Wick, the built-in helper", "Turn it on from the Help menu", wick)
        closePanel()
    }

    /** Puts [screen] in the panel and opens it as a window called [title]. */
    private fun DemoTour.show(screen: String, title: String, w: Float, h: Float) {
        panel.value = screen
        menusInPlace = screen in IN_PLACE_MENUS
        openPanel(title, w, h)
    }

    private fun stop(title: String, body: String, target: Target) = Triple(title, body, target)

    private fun DemoTour.button(name: String) = find(hasContentDescription(name, substring = true))
}

@Composable
private fun Panel(settings: AppSettings, change: ((AppSettings) -> AppSettings) -> Unit) {
    when (panel.value) {
        "system" -> Box(Modifier.size(SYSTEM_W.dp, SYSTEM_H.dp)) { SystemSettingsTab(settings, change) }
        "permission" -> Box(Modifier.size(PERMISSION_W.dp, PERMISSION_H.dp)) { PermissionPrompt() }
        "pco" -> Box(Modifier.size(PCO_W.dp, PCO_H.dp)) { SampleImport() }
        "library" -> Box(Modifier.size(LIBRARY_W.dp, LIBRARY_H.dp)) { SongLibraryApp(File(LIBRARY, "Songs")) }
        "converter" -> Box(Modifier.size(CONVERTER_W.dp, CONVERTER_H.dp)) { App(initialTab = ConverterTab.SONGS) }
        "ccli" -> Box(Modifier.size(CCLI_W.dp, CCLI_H.dp)) {
            val stats = remember { StatisticsManager() }
            CCLIReportContent(
                theme = ThemeMode.DARK,
                statisticsManager = stats,
                onDismiss = {},
                today = AppPreviewStatisticsScreenshotTest.FIXED_TODAY,
            )
        }
        else -> Box(Modifier.size(WICK_W.dp, WICK_H.dp)) { WickIntro(onDone = {}, animate = false) }
    }
}

/** What the operator sees when a phone asks to add a reading to the schedule. */
@Composable
private fun PermissionPrompt() = RemoteEventDialogContent(
    event = RemoteEvent(
        type = RemoteEventType.ADD_TO_SCHEDULE,
        title = "Psalm 23:1-3",
        detail = "The LORD is my shepherd; I shall not want.",
        clientId = "a4f19c72",
        clientLabel = "Sound desk iPad",
    ),
    actionLabel = "Add to Schedule",
    typeIcon = Icons.Filled.CalendarMonth,
    typeAccent = MaterialTheme.colorScheme.primary,
    bodyTitle = "Psalm 23:1-3",
    remaining = 0,
    showAllowPermanently = true,
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

/** The Planning Center import, connected and drawn in place, over [samplePlan]. */
@Composable
private fun SampleImport() = PlanningCenterImportDialog(
    isVisible = true,
    settings = PlanningCenterSettings(
        accessToken = "demo",
        refreshToken = "demo",
        tokenExpiresAtEpochMs = Long.MAX_VALUE,
        connectedPersonName = "Sam Taylor",
        defaultServiceTypeId = "st-1",
    ),
    services = PlanningCenterImportServices(clientId = "demo", clientSecret = "demo"),
    window = { _, content -> content() },
    editSong = { _, _, _, _ -> },
    onDismiss = {},
    onTokensRefreshed = { _, _, _ -> },
    onAddSong = { _, _, _, _ -> },
    onAddLabel = { _, _, _ -> },
    onAddPresentation = { _, _, _, _ -> },
    onAddPicture = { _, _, _ -> },
    onAddMedia = { _, _, _ -> },
    onAddAnnouncement = {},
    onAddBibleVerse = { _, _, _, _, _, _ -> },
    onConnected = { _, _, _, _ -> },
    onDisconnect = {},
)

/** A Sunday service in Planning Center, answered without the network. */
private fun samplePlan() {
    coEvery { PlanningCenterClient.listServiceTypes(any(), any()) } returns
        PlanningCenterClient.ServiceTypesOutcome.Success(listOf(PlanningCenterClient.ServiceType("st-1", "Sunday")))
    coEvery { PlanningCenterClient.listUpcomingPlans(any(), any(), any()) } returns
        PlanningCenterClient.PlansOutcome.Success(
            listOf(
                PlanningCenterClient.Plan("plan-1", PLAN_TITLE, "Sunday"),
                PlanningCenterClient.Plan("plan-2", "Evening Prayer", "Sunday"),
            ),
        )
    coEvery { PlanningCenterClient.getPlanItems(any(), any(), any(), any()) } returns
        PlanningCenterClient.PlanItemsOutcome.Success(
            listOf(
                planItem("h1", "Worship", "header"),
                planItem("s1", "Amazing Grace", "song", song = "Amazing Grace"),
                planItem("s2", "Come Thou Fount Of Every Blessing", "song", song = "Come Thou Fount Of Every Blessing"),
                planItem("i1", "Welcome and announcements", "item"),
                planItem("h2", "Message", "header"),
                planItem("i2", "Scripture Reading", "item", description = "Psalms 23:1-3"),
                planItem("i3", "Sermon: The Good Shepherd", "item"),
                planItem("s3", "It Is Well With My Soul", "song", song = "It Is Well With My Soul"),
            ),
        )
    coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
        PlanningCenterClient.AttachmentsOutcome.Success(emptyList())
}

private fun planItem(id: String, title: String, type: String, song: String? = null, description: String = "") =
    PlanningCenterClient.PlanItem(
        id = id, title = title, description = description, itemType = type,
        sequence = 0, songTitle = song, songCcliNumber = null, songId = null, arrangementId = null,
    )

/** The projector, a second display showing only lower thirds, and a stage monitor. */
internal fun threeScreens(s: AppSettings): AppSettings {
    val proj = s.projectionSettings
    val lowerThird = OutputProfile(
        id = "demo-lower-third",
        name = "Lower Third",
        displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
    )
    val stage = OutputProfile(id = "demo-stage", name = "Stage", displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)
    return s.copy(
        // Every schedule button showing, the Calendar Manager's too, which a new install hides.
        hiddenScheduleButtons = emptySet(),
        projectionSettings = proj.copy(
            // No monitors under test: the panel previews this many stand-in windows, one per screen.
            devWindowCount = 3,
            outputProfiles = proj.outputProfiles.ifEmpty { listOf(factoryOutputProfile()) } + lowerThird + stage,
            screenAssignments = proj.screenAssignments.take(1) +
                ScreenAssignment(activeProfileId = lowerThird.id) +
                ScreenAssignment(activeProfileId = stage.id),
        ),
    )
}

/** Which window the panel shows. */
private val panel = mutableStateOf("system")

/** The windows whose own drop-down menus are drawn where they open, not centered as alerts. */
private val IN_PLACE_MENUS = setOf("pco", "library", "converter", "ccli")

private const val PLAN_TITLE = "Sunday Service"
private const val PANEL_WAIT_MS = 30_000L
private const val SYSTEM_W = 900f
private const val SYSTEM_H = 640f
private const val PERMISSION_W = 500f
private const val PERMISSION_H = 290f
private const val PCO_W = 1000f
private const val PCO_H = 720f
private const val LIBRARY_W = 1300f
private const val LIBRARY_H = 800f
private const val CONVERTER_W = 1000f
private const val CONVERTER_H = 720f
private const val CCLI_W = 1200f
private const val CCLI_H = 700f
private const val WICK_W = 900f
private const val WICK_H = 620f
