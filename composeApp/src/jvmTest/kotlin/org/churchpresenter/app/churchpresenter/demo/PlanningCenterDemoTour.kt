@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.churchpresenter.app.churchpresenter.ScheduleActions
import org.churchpresenter.app.churchpresenter.screenshot.LIBRARY
import org.churchpresenter.app.churchpresenter.utils.countDeckSlides
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.planningcenter.PlanningCenterClient
import org.churchpresenter.planningcenter.PlanningCenterPrimaryBible
import org.churchpresenter.planningcenter.ui.PlanningCenterImportDialog
import org.churchpresenter.planningcenter.ui.PlanningCenterImportServices
import org.churchpresenter.settings.PlanningCenterSettings
import org.churchpresenter.settings.SettingsManager
import java.io.File
import java.util.UUID
import kotlin.test.Test

/** Import from Planning Center: connect once, then a Sunday plan into the schedule, songs and all. */
class PlanningCenterDemoTour {
    @Test
    fun planningCenter() {
        mockkObject(PlanningCenterClient)
        try {
            samplePlan()
            recordTour(
                "planning-center",
                tab = "Planning Center",
                liveLabel = "Sunday Service",
                steps = 6,
                heading = "" to "",
                // The import reads the song library and the Bible from the saved settings, as the app has them.
                settings = { s -> s.also { SettingsManager().saveSettings(it) } },
                panel = { _, _ ->
                    val size = if (connected.value) IMPORT else CONNECT
                    Box(Modifier.size(size.first.dp, size.second.dp)) { Import() }
                },
            ) { planningCenterTour() }
        } finally {
            unmockkObject(PlanningCenterClient)
        }
    }

    private fun DemoTour.planningCenterTour() {
        scheduleOf = { schedule() }
        say("intro")
        intro("Your service plan, straight into the schedule.")
        say("what")
        caption("No typing it twice", "ChurchPresenter reads the plan for you")
        connect()
        pickPlan()
        items()
        importPlan()
        say("outro")
        outro()
    }

    private fun DemoTour.connect() {
        say("import-button")
        val button = find(hasContentDescription("Import from Planning Center", substring = true))
        step("Import from Planning Center", "Under the schedule", button, numbered = true) {}
        openPanel("Import from Planning Center", CONNECT.first, CONNECT.second)
        say("connect")
        panelNote("Connect to Planning Center", "Only the first time", seek(hasText("Connect to Planning Center")))
        say("browser")
        caption("In your browser", "Sign in, and allow ChurchPresenter to read your plans")
        closePanel()
        connected.value = true
        openPanel("Import from Planning Center", IMPORT.first, IMPORT.second)
        waitFor("the plan to load", PANEL_WAIT_MS) { panelItems(hasText("Amazing Grace")).isNotEmpty() }
        say("connected")
        point("Connected", "Read only: nothing changes in Planning Center", hasText("Connected as", substring = true))
    }

    private fun DemoTour.pickPlan() {
        say("service-type")
        point("Service Type", "Like Sunday", hasText("Service Type", ignoreCase = true))
        say("plan")
        point("The plan", "This week's", hasText(PLAN_TITLE, substring = true))
    }

    private fun DemoTour.items() {
        say("items")
        point("Plan Items", "In the plan's own order", hasText("Plan Items"))
        say("headers")
        point("Worship", "Headers become labels", hasText("Worship"))
        say("matched")
        point("✓ Matched", "Already in your library", hasText("✓ Matched"))
        say("add-song")
        point("Add Song", "Opens the song editor with its lyrics", hasText("Add Song"))
        say("scripture")
        val reading = hasText("23:1-3", substring = true)
        waitFor("the reading to be found", PANEL_WAIT_MS) { panelItems(reading).isNotEmpty() }
        point("Psalm 23", "Found in your Bible", reading)
        say("announcement")
        point("Welcome", "Comes in as an announcement", hasText("Welcome and announcements"))
        say("files")
        val files = seek(hasText("file(s)", substring = true))
        panelNote("3 files", "Click to see them", files, click = true, numbered = true)
        say("unsupported")
        point("A Word document", "Crossed out: it can't be shown", hasText(NOTES))
    }

    private fun DemoTour.importPlan() {
        // The plan becomes the whole schedule, so what it brought in is all that is there.
        schedule().clearSchedule()
        say("import")
        val import = seek(hasText("Import Selected"))
        // Downloads land in the app's data folder under the home folder, which here is the recorder's
        // own, inside the repository: pointed at the shared demo library so no personal path shows.
        val home = System.getProperty("user.home")
        System.setProperty("user.home", LIBRARY.path)
        try {
            panelNote("Import Selected", "Into the schedule", import, click = true, numbered = true)
            waitFor("the import to finish", PANEL_WAIT_MS) {
                hasNode(hasText(SERMON, substring = true)) { it.left < dp(SCHEDULE_DP) }
            }
        } finally {
            System.setProperty("user.home", home)
        }
        closePanel()
        say("schedule")
        note("Your schedule", "In the order you planned", find(hasText("Worship")) { it.left < dp(SCHEDULE_DP) })
    }
}

/** The schedule the import adds to, handed over by the tour once it is running. */
private var scheduleOf: () -> ScheduleActions = { ScheduleActions() }

/** Connected or not: the connect window first, then the import. */
private val connected = mutableStateOf(false)

/** The import, drawn in place, adding what it picks to the schedule as the app's own does. */
@Composable
private fun Import() = PlanningCenterImportDialog(
    isVisible = true,
    settings = if (connected.value) {
        PlanningCenterSettings(
            accessToken = "demo",
            refreshToken = "demo",
            tokenExpiresAtEpochMs = Long.MAX_VALUE,
            connectedPersonName = "Sam Taylor",
            defaultServiceTypeId = "st-1",
        )
    } else {
        PlanningCenterSettings()
    },
    services = PlanningCenterImportServices(
        clientId = "demo",
        clientSecret = "demo",
        // Full book names only: the abbreviation tables need a display, and the plan spells it out.
        detectScriptures = PlanningCenterPrimaryBible { null }::detect,
        countSlides = ::countDeckSlides,
    ),
    window = { _, content -> content() },
    editSong = { _, _, _, _ -> },
    onDismiss = {},
    onTokensRefreshed = { _, _, _ -> },
    onAddSong = { n, title, book, id -> scheduleOf().addSong(n, title, book, id) },
    onAddLabel = { text, fg, bg -> scheduleOf().addLabel(text, fg, bg) },
    onAddPresentation = { path, name, slides, type -> scheduleOf().addPresentation(path, name, slides, type) },
    onAddPicture = { path, name, count -> scheduleOf().addPicture(path, name, count) },
    onAddMedia = { url, title, type -> scheduleOf().addMedia(url, title, type, "") },
    onAddAnnouncement = { text ->
        scheduleOf().addAnnouncement(ScheduleItem.AnnouncementItem(id = UUID.randomUUID().toString(), text = text))
    },
    onAddBibleVerse = { book, chapter, verse, text, range, bookId ->
        scheduleOf().addBibleVerse(book, chapter, verse, text, range, bookId)
    },
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
                planItem("s4", "Fairest Lord Jesus", "song", song = "Fairest Lord Jesus"),
                planItem("i1", "Welcome and announcements", "item"),
                planItem("h2", "Message", "header"),
                planItem("i2", "Scripture Reading", "item", description = "Psalm 23:1-3"),
                planItem(SERMON_ID, SERMON, "item"),
                planItem("s3", "It Is Well With My Soul", "song", song = "It Is Well With My Soul"),
            ),
        )
    coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } answers {
        val itemId = arg<String>(3)
        val files = if (itemId == SERMON_ID) SERMON_FILES.keys.toList() else emptyList()
        PlanningCenterClient.AttachmentsOutcome.Success(files)
    }
    coEvery { PlanningCenterClient.resolveAttachmentDownloadUrl(any(), any(), any()) } answers {
        PlanningCenterClient.AttachmentUrlOutcome.Success("demo:" + arg<String>(1))
    }
    coEvery { PlanningCenterClient.downloadFile(any(), any(), any()) } answers {
        val id = arg<String>(0).removePrefix("demo:")
        val destination = arg<File>(1)
        val source = SERMON_FILES.entries.first { it.key.id == id }.value
        destination.parentFile.mkdirs()
        source.copyTo(destination, overwrite = true)
        PlanningCenterClient.FileDownloadOutcome.Success(destination)
    }
}

private fun planItem(id: String, title: String, type: String, song: String? = null, description: String = "") =
    PlanningCenterClient.PlanItem(
        id = id, title = title, description = description, itemType = type,
        sequence = 0, songTitle = song, songCcliNumber = null, songId = null, arrangementId = null,
    )

/** The sermon's files in Planning Center, and the library files that stand in for their downloads. */
private val SERMON_FILES = linkedMapOf(
    PlanningCenterClient.PlanAttachment("a1", "Good Shepherd.pptx") to File(LIBRARY, "Decks/Good Shepherd.pptx"),
    PlanningCenterClient.PlanAttachment("a2", "Shepherd's Field.png") to File(LIBRARY, "Gallery/09 Mountains.png"),
    PlanningCenterClient.PlanAttachment("a3", NOTES) to File(LIBRARY, "Decks/Sermon.pdf"),
)

private const val PLAN_TITLE = "Sunday Service"
private const val SERMON = "Sermon: The Good Shepherd"
private const val SERMON_ID = "i3"
private const val NOTES = "Sermon notes.docx"
private const val PANEL_WAIT_MS = 30_000L
private const val SCHEDULE_DP = 380f
private val CONNECT = 460f to 260f
private val IMPORT = 1000f to 760f
