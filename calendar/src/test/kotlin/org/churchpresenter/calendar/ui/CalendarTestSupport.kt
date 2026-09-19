@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PresetDocument
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongLibrary
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

/**
 * The Calendar Manager opened on a real `calendar.json`, the way the app opens it.
 *
 * A folder on disk rather than a fake store: [CalendarState] writes on every change, and half of
 * what is worth asserting here — a service that survives being added, a row whose planned length
 * is saved — is only true because a file was written and read back.
 */
internal fun withCalendar(
    document: CalendarDocument = CalendarDocument(),
    host: CalendarHost = CalendarHost(),
    songFolder: File? = null,
    body: ComposeUiTest.(folder: File) -> Unit,
) {
    val folder = Files.createTempDirectory("calendar-ui").toFile()
    try {
        if (document != CalendarDocument()) {
            File(folder, "calendar.json").writeText(
                Json { encodeDefaults = true }.encodeToString(CalendarDocument.serializer(), document)
            )
        }
        runComposeUiTest {
            setContent {
                AppThemeWrapper(theme = ThemeMode.LIGHT) {
                    // Unconfined so the store read finishes inline: the window is under test, not
                    // the thread it loads on.
                    CalendarApp(
                        storeFolder = folder,
                        songFolder = songFolder,
                        host = host,
                        io = Dispatchers.Unconfined,
                        today = TODAY,
                        onClose = {},
                    )
                }
            }
            waitForIdle()
            body(folder)
        }
    } finally {
        folder.deleteRecursively()
    }
}

/** The window with the app's own song editor supplied, as ChurchPresenter supplies it. */
internal fun withCalendarEditor(
    document: CalendarDocument,
    songFolder: File,
    songEditor: @Composable (SongEditRequest) -> Unit,
    body: ComposeUiTest.() -> Unit,
) {
    val folder = Files.createTempDirectory("calendar-editor").toFile()
    try {
        File(folder, "calendar.json").writeText(
            Json { encodeDefaults = true }.encodeToString(CalendarDocument.serializer(), document)
        )
        runComposeUiTest {
            setContent {
                AppThemeWrapper(theme = ThemeMode.LIGHT) {
                    CalendarApp(
                        storeFolder = folder,
                        songFolder = songFolder,
                        host = CalendarHost(),
                        songEditor = songEditor,
                        io = Dispatchers.Unconfined,
                        today = TODAY,
                        onClose = {},
                    )
                }
            }
            waitForIdle()
            body()
        }
    } finally {
        folder.deleteRecursively()
    }
}

/** The window opened on a folder that already holds a `calendar.json`, whatever state it is in. */
internal fun withCalendarFolder(folder: File, body: ComposeUiTest.() -> Unit) {
    runComposeUiTest {
        setContent {
            AppThemeWrapper(theme = ThemeMode.LIGHT) {
                CalendarApp(
                    storeFolder = folder,
                    songFolder = null,
                    host = CalendarHost(),
                    io = Dispatchers.Unconfined,
                    today = TODAY,
                    onClose = {},
                )
            }
        }
        waitForIdle()
        body()
    }
}

/**
 * A song folder with [songs] in it, written as the `.song` files the picker actually reads.
 *
 * The picker reads a folder rather than being handed a list, so a test that wants song results has
 * to put songs on a disk -- which is also what makes "the library is still loading" a real state.
 */
internal fun songFolderWith(vararg songs: SongItem): File {
    val folder = Files.createTempDirectory("calendar-songs").toFile()
    val library = SongLibrary(folder)
    songs.forEach { library.writeNew(it) }
    return folder
}

internal fun libraSong(number: String, title: String, songbook: String = "Hymns") = SongItem(
    number = number,
    title = title,
    songbook = songbook,
    lyrics = listOf("[Verse 1]", "Line one", "Line two", "", "{Chorus}", "Sing it again"),
)

/** Presets on disk, as `presets.json` beside the calendar -- what the picker's Presets tab lists. */
internal fun seedPresets(folder: File, vararg presets: ItemPreset) {
    PresetStore(folder).save(PresetDocument(presets = presets.toList()))
}

/** A fixed day, so a month grid and a service's "today" never depend on when the suite runs. */
internal val TODAY: LocalDate = LocalDate.of(2026, 9, 20)

internal fun song(id: String, title: String = "Song $id") = ScheduleItem.SongItem(
    id = id, songNumber = 1, title = title, songbook = "Hymns", songId = "Hymns::1",
)

internal fun heading(id: String, text: String = "Worship") = ScheduleItem.LabelItem(
    id = id, text = text, textColor = "#FFFFFF", backgroundColor = "#5B9DF5",
)

internal fun service(
    id: String = "svc",
    name: String = "Sunday Morning",
    start: String = "10:00",
    date: LocalDate = TODAY,
    items: List<ScheduleItem> = listOf(heading("h"), song("a", "Amazing Grace")),
    planned: Map<String, Int> = mapOf("a" to 300),
    timing: Map<String, RowTiming> = emptyMap(),
) = PlannedService(
    id = id,
    date = date.toString(),
    name = name,
    startTime = start,
    items = items,
    plannedSeconds = planned,
    timing = timing,
)

internal fun documentWith(vararg services: PlannedService) = CalendarDocument(services = services.toList())

/** The books the picker's Bible tab offers, small enough to assert against. */
internal val BIBLE_BOOKS = listOf(
    CalendarBibleBook(bookId = 1, name = "Genesis", verseCounts = listOf(31, 25)),
    CalendarBibleBook(bookId = 19, name = "Psalms", verseCounts = listOf(6, 12)),
)

/** Clicks the first node holding [text] — the calendar draws a label in more than one pane. */
internal fun ComposeUiTest.clickFirst(text: String) {
    onAllNodesWithText(text, substring = true)[0].performClick()
    waitForIdle()
}

/** Clicks an icon button, which carries its label as a description rather than as text. */
internal fun ComposeUiTest.clickIcon(description: String) {
    onAllNodesWithContentDescription(description, substring = true)[0].performClick()
    waitForIdle()
}

/** Types into the first field on screen -- the picker's search box, or a sheet's first entry. */
internal fun ComposeUiTest.typeIntoFirstField(text: String) {
    onAllNodes(hasSetTextAction())[0].performTextInput(text)
    waitForIdle()
}

/** Clicks the last node holding [text] -- a sheet's own button, past the label that names it. */
internal fun ComposeUiTest.clickLast(text: String) {
    val nodes = onAllNodesWithText(text, substring = true, ignoreCase = true)
    nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
    waitForIdle()
}

/** Types into the field of whatever opened last, rather than a search box already on screen. */
internal fun ComposeUiTest.typeIntoLastField(text: String) {
    val fields = onAllNodes(hasSetTextAction())
    fields[fields.fetchSemanticsNodes().size - 1].performTextInput(text)
    waitForIdle()
}

/** Flips the last switch on screen -- a settings card's own control, which carries no label. */
internal fun ComposeUiTest.toggleSwitch() {
    val switches = onAllNodes(isToggleable())
    switches[switches.fetchSemanticsNodes().size - 1].performClick()
    waitForIdle()
}

/** Whether anything on screen shows [text]; the calendar repeats labels across panes. */
internal fun ComposeUiTest.shows(text: String): Boolean =
    onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

/** Waits for [text] to appear, so a test never asserts against a half-composed window. */
internal fun ComposeUiTest.awaitText(text: String) {
    waitUntil("\"$text\" on screen") {
        onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
    }
}

internal fun ComposeUiTest.clickText(text: String) {
    onNodeWithText(text, substring = true).performClick()
    waitForIdle()
}


/**
 * Clicks [text] **inside the open sheet**, which a plain match cannot do.
 *
 * A dialog is its own compose root drawn over the window, and the window behind it is a month grid
 * of day numbers — so "1" matches a chapter tile and a Sunday alike, and whichever the matcher
 * happens to order first decides what the test clicked. [anchor] is a label only the sheet draws;
 * the node sharing its root is the one meant.
 */
internal fun ComposeUiTest.clickInSheet(text: String, anchor: String = "All books") {
    val sheet = onAllNodesWithText(anchor, substring = true, ignoreCase = true)
        .fetchSemanticsNodes().first().root
    val nodes = onAllNodesWithText(text, substring = false)
    val index = nodes.fetchSemanticsNodes().indexOfFirst { it.root === sheet }
    check(index >= 0) { "\"$text\" is not in the open sheet" }
    nodes[index].performClick()
    waitForIdle()
}
