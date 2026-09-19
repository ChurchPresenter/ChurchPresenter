@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.model.CalendarDocument
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

