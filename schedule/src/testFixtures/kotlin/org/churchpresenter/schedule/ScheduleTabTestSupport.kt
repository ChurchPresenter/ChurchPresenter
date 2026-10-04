@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import org.churchpresenter.core.models.songs.SongItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.ScheduleServiceLink
import org.churchpresenter.calendar.model.UpcomingLoad
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import java.io.File
import java.nio.file.Files
import java.time.LocalTime

/** Writes schedule items the way the app does, defaults included. */
private val itemsJsonFormat = Json { encodeDefaults = true }

/**
 * Harness and fixtures shared by the `ScheduleTab` test classes.
 *
 * The tab is driven through a real [ScheduleViewModel] — the same one the app builds — so what is
 * exercised is the wiring between the two: which view-model call a button makes, and what the list
 * renders from the resulting state. The view model's own rules (undo history, move semantics,
 * remote following) are already covered by the `ScheduleViewModel*` suites, so nothing here
 * re-tests those; these tests assert the schedule the operator ends up with.
 *
 * `user.home` is isolated per test because the view model resolves its autosave path at
 * construction and `newSchedule()` deletes that file.
 */

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
internal class ScheduleReports {
    val presenting = mutableListOf<Presenting>()
    val clicked = mutableListOf<ScheduleItem>()
    val presented = mutableListOf<ScheduleItem>()
    val editedLabels = mutableListOf<ScheduleItem.LabelItem>()
    val selectionChanges = mutableListOf<String?>()
    var addLabelRequests = 0
    var addWebsiteRequests = 0
    val zoomChanges = mutableListOf<Int>()
    val legacyRowActionChanges = mutableListOf<Boolean>()
    val toolbarButtonToggles = mutableListOf<ScheduleToolbarButton>()
    val toolbarIconSizeChanges = mutableListOf<ScheduleToolbarIconSize>()
    /** Each Load now that went through, and whether it asked for the Schedule to be replaced. */
    val loadNowChoices = mutableListOf<Boolean>()
    var saveToCalendarRequests = 0
    var addToCalendarRequests = 0

    /**
     * The action set the tab hands its parent, so the menu and keyboard paths — which never touch a
     * button in this tab — can be driven the way `MainDesktop` drives them.
     */
    var actions: ScheduleTabActions? = null
}

/**
 * Builds a real [ScheduleViewModel] under an isolated `user.home`, seeds it with [seed], composes
 * `ScheduleTab` over it, and runs [block].
 *
 * The view model is created before composition and passed in, so a test can seed it without racing
 * the tab's first frame — and so `block` can read the schedule back from the same instance the tab
 * is driving.
 */
@OptIn(ExperimentalTestApi::class)
internal fun scheduleTab(
    itemZoomPercent: Int = 100,
    /** The legacy card layout — buttons on their own line under the title, always visible. */
    legacyRowActions: Boolean = false,
    /** Toolbar buttons turned off from the panel's options menu, by [ScheduleToolbarButton] name. */
    hiddenToolbarButtons: Set<String> = emptySet(),
    /** How large the toolbar's icons are drawn, as chosen from the options menu. */
    toolbarIconSize: ScheduleToolbarIconSize = ScheduleToolbarIconSize.SMALL,
    /** Constrains the panel, for the layout tests that need it narrow enough to wrap. */
    width: Dp? = null,
    seed: ScheduleViewModel.() -> Unit = {},
    /** Null keeps the plain MaterialTheme every other test composes under; set to shoot a theme. */
    themeMode: ThemeMode? = null,
    /** The clock the live row's behind/ahead badge is reckoned from; pinned by the shot that shows it. */
    clock: () -> LocalTime = { LocalTime.now() },
    /** The calendar's next service to load itself, announced under Add Files. */
    upcomingServiceLoad: UpcomingLoad? = null,
    /** The planned service the rows came from, and whether they have changed since. */
    scheduleService: ScheduleServiceLink? = null,
    /** Whether the tab is offered a calendar to add a hand-built Schedule to, as the app does. */
    offerAddToCalendar: Boolean = false,
    block: ComposeUiTest.(vm: ScheduleViewModel, reports: ScheduleReports) -> Unit,
) {
    val realHome = System.getProperty("user.home")
    val tempHome: File = Files.createTempDirectory("cp-schedule-tab").toFile()
    System.setProperty("user.home", tempHome.absolutePath)
    val vm = ScheduleViewModel(clock = clock)
    try {
        vm.seed()
        val reports = ScheduleReports()
        val body: ComposeUiTest.() -> Unit = {
            setContent {
                ThemedForTest(themeMode) {
                    Box(modifier = if (width != null) Modifier.width(width) else Modifier) {
                    ScheduleTab(
                        scheduleViewModel = vm,
                        itemZoomPercent = itemZoomPercent,
                        onItemZoomChange = { reports.zoomChanges += it },
                        legacyRowActions = legacyRowActions,
                        onLegacyRowActionsChange = { reports.legacyRowActionChanges += it },
                        hiddenToolbarButtons = hiddenToolbarButtons,
                        onToggleToolbarButton = { reports.toolbarButtonToggles += it },
                        toolbarIconSize = toolbarIconSize,
                        onToolbarIconSizeChange = { reports.toolbarIconSizeChanges += it },
                        onPresenting = { reports.presenting += it },
                        onItemClick = { reports.clicked += it },
                        onEditLabel = { reports.editedLabels += it },
                        onSelectedItemChanged = { reports.selectionChanges += it },
                        onActionsReady = { reports.actions = it },
                        onAddLabel = { reports.addLabelRequests++ },
                        onPresentSong = { reports.presented += it },
                        onPresentBible = { reports.presented += it },
                        onPresentWebsite = { reports.presented += it },
                        onPresentAnnouncement = { reports.presented += it },
                        onPresentMedia = { reports.presented += it },
                        onPresentLowerThird = { reports.presented += it },
                        onPresentDictionary = { reports.presented += it },
                        upcomingServiceLoad = upcomingServiceLoad,
                        onLoadServiceNow = { reports.loadNowChoices += it },
                        scheduleService = scheduleService,
                        onSaveScheduleToCalendar = { reports.saveToCalendarRequests++ },
                        onAddScheduleToCalendar =
                            if (offerAddToCalendar) ({ reports.addToCalendarRequests += 1 }) else null,
                    )
                    }
                }
            }
            block(vm, reports)
        }
        runComposeUiTest(block = body)
    } finally {
        runCatching { vm.dispose() }
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }
}

/**
 * The registered [ScheduleTabActions], once the tab's `LaunchedEffect` has published them.
 *
 * Registration happens in an effect rather than during composition, so the wait is on the effect
 * having run — `waitForIdle` returns on that positive signal, not on a duration.
 */
internal fun ComposeUiTest.registeredActions(reports: ScheduleReports): ScheduleTabActions {
    waitForIdle()
    return requireNotNull(reports.actions) { "the tab must publish its actions to the parent" }
}

// ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

/**
 * Writes an autosave the tab will offer to restore, into the temp `user.home` the harness has
 * already installed. Call from `seed`, which runs after the view model is built (so the path is
 * fixed) and before the first frame (so the prompt is decided against a file that exists).
 *
 * Plain JSON rather than the encrypted form: the encryption helpers are on a private companion, and
 * `restoreAutoSave` falls back to the raw text when decryption fails, which is the branch this
 * takes. What the file contains is beside the point here — `ScheduleAutoSaveTest` covers the
 * restore itself; these tests are about the dialog in front of it.
 */
internal fun plantAutoSave(vararg titles: String) {
    val items = titles.mapIndexed { i, title ->
        ScheduleItem.SongItem(id = "auto-$i", songNumber = i + 1, title = title, songbook = "Hymnal")
    }
    // Serialized through the real ScheduleItem serializer so the polymorphic discriminator is
    // whatever the model actually declares, rather than a string this fixture guesses at.
    val itemsJson = itemsJsonFormat
        .encodeToString(ListSerializer(ScheduleItem.serializer()), items)
    val file = File(System.getProperty("user.home"), ".churchpresenter/autosave_schedule.tmp")
    file.parentFile.mkdirs()
    file.writeText("""{"version":2,"items":$itemsJson,"notes":{}}""")
}

/** Whether the autosave the tab was offered is still on disk. */
internal fun autoSaveExists(): Boolean =
    File(System.getProperty("user.home"), ".churchpresenter/autosave_schedule.tmp").exists()


/** A service order with one of each item type the row renderer draws differently. */
internal fun ScheduleViewModel.seedService() {
    addLabel("Welcome", "#FFFFFF", "#203040")
    addSong(songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")
    addBibleVerse(
        bookName = "John", chapter = 3, verseNumber = 16,
        verseText = "For God so loved the world.",
    )
    addWebsite(url = "https://example.org", title = "Notices")
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object ScheduleLabel {
    const val TITLE = "Schedule"
    const val NEW = "New Schedule"
    // Undo/Redo are located by tag, not by label — see [taggedButton].
    const val UNDO = ScheduleToolbarTags.UNDO
    const val REDO = ScheduleToolbarTags.REDO
    const val ADD_LABEL = "Add Label"
    const val CLEAR = "Clear Schedule"
    const val ZOOM_IN = "Zoom In"
    const val ZOOM_OUT = "Zoom Out"
    const val DROP_HINT = "Drag files here to add to schedule"
    const val MOVE_UP = "Move Up"
    const val MOVE_DOWN = "Move Down"
    const val GO_LIVE = "Go Live"
    const val REMOVE = "Remove"
    const val NOTE = "Note"
    const val EDIT_LABEL = "Edit Label"
    const val NOTE_SAVE = "Save note"
    const val NOTE_CLEAR = "Clear note"
}

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}

/**
 * One row of every kind the Schedule holds, with the paths and URLs the app-preview library uses —
 * shared by this module's shots and the website's `previewApp/schedule_*` export in the app.
 */
fun ScheduleViewModel.seedEveryItemType() {
    addLabel("Welcome", "#FFFFFF", "#203040")
    addSong(songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")
    addBibleVerse(
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = "For God so loved the world, that he gave his only begotten Son.",
    )
    // Paths and URLs here are drawn on screen — the schedule row prints the path of every
    // file-backed item at detailed density — and these shots are exported for the website. They
    // therefore match the app-preview fixture's library root and naming rather than standing in
    // as `/decks/…` placeholders, so two images of the same app do not disagree about where a
    // church keeps its files. See the LIBRARY note in AppPreviewSupport.kt.
    addPresentation(
        filePath = "/Users/Shared/ChurchPresenter/Decks/Sermon.pptx",
        fileName = "Sermon.pptx",
        slideCount = 24,
        fileType = "pptx",
    )
    addPicture(
        folderPath = "/Users/Shared/ChurchPresenter/Gallery",
        folderName = "Gallery",
        imageCount = 12,
    )
    addMedia(
        mediaUrl = "/Users/Shared/ChurchPresenter/Media/Welcome Loop.mp4",
        mediaTitle = "Welcome Loop",
        mediaType = "video",
    )
    addLowerThird(
        presetId = "lt-1",
        presetLabel = "Guest speaker",
        pauseAtFrame = true,
        pauseDurationMs = 4000,
    )
    addAnnouncement(text = "Fellowship lunch after the service")
    addWebsite(url = "https://churchpresenter.org/notices", title = "Notices")
    addScene(sceneId = "scene-1", sceneName = "Countdown scene")
    addDictionary(
        number = "H2617",
        word = "חֶסֶד",
        transliteration = "chesed",
        definition = "steadfast love",
    )
    // A ministry item only ever reaches the Schedule from a phone; the row shows what it
    // is and who, and cannot be presented.
    addRow(ScheduleItem.MinistryItem("ministry", "Violin", "Jake"), null)
}
