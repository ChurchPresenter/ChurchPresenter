package org.churchpresenter.calendar.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.CueFeed
import org.churchpresenter.calendar.FiredCue
import org.churchpresenter.calendar.model.PreflightProblem
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The measured length beside the plan, and the pre-flight marks with their one-click fixes, as
 * they appear in the run of show and the row editor.
 */
@OptIn(ExperimentalTestApi::class)
class PreflightAndMeasuredTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    private fun clip(id: String, path: String) = ScheduleItem.MediaItem(id, path, "Welcome clip", "local")

    // ── Measured length ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a measured length that differs from the plan is offered, and one click adopts it`() =
        withCalendar(
            documentWith(service(items = listOf(song("a", "Amazing Grace")), planned = mapOf("a" to 300))),
            host = CalendarHost(measuredSeconds = { 312 }),
        ) { folder ->
            awaitText("usually 5:12")
            clickFirst("usually 5:12")
            waitForIdle()

            assertEquals(312, stored(folder).services.single().plannedSeconds["a"])
            assertFalse(shows("usually 5:12"), "once the plan agrees, the suggestion is gone")
        }

    @Test
    fun `the row editor offers the measured length among its choices`() =
        withCalendar(
            documentWith(service(items = listOf(song("a", "Amazing Grace")), planned = mapOf("a" to 300))),
            host = CalendarHost(measuredSeconds = { 312 }),
        ) { folder ->
            awaitText("Amazing Grace")
            clickFirst("Amazing Grace")
            awaitText("Editing")
            clickInSheet("usually 5:12", anchor = "Editing")
            waitForIdle()

            assertEquals(312, stored(folder).services.single().plannedSeconds["a"])
        }

    @Test
    fun `a song added with nothing typed takes its measured length, else the default`() {
        val songs = songFolderWith(libraSong("1", "Alpha Song"), libraSong("2", "Beta Song"))
        try {
            withCalendar(
                documentWith(service(items = emptyList(), planned = emptyMap())),
                host = CalendarHost(
                    itemRunSeconds = { if ((it as? ScheduleItem.SongItem)?.title == "Alpha Song") 200 else null },
                ),
                songFolder = songs,
            ) { folder ->
                awaitText("Sunday Morning")
                clickFirst("Add song, verse or section")
                awaitText("Songs")
                awaitText("Alpha Song")
                clickInSheetContaining("Alpha Song")
                waitForIdle()
                clickFirst("Add song, verse or section")
                awaitText("Songs")
                awaitText("Beta Song")
                clickInSheetContaining("Beta Song")
                waitForIdle()

                val service = stored(folder).services.single()
                val planned = service.items.map { service.plannedSeconds[it.id] }
                assertEquals(listOf(200, 270), planned, "measured first, the Defaults tab's song length otherwise")
            }
        } finally {
            songs.deleteRecursively()
        }
    }

    // ── Pre-flight ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a row whose file is gone is marked, counted, and fixed by locating the file`() {
        val found = File(System.getProperty("java.io.tmpdir"), "found-clip.mp4").apply { writeText("x") }
        try {
            withCalendar(
                documentWith(service(items = listOf(clip("m", "/gone/clip.mp4"), song("a")), planned = emptyMap())),
                host = CalendarHost(locateFile = { found }),
            ) { folder ->
                awaitText("1 row needs attention")
                onAllNodesWithContentDescription("File not found", substring = true).onFirst().performClick()
                waitForIdle()

                val row = stored(folder).services.single().items.first() as ScheduleItem.MediaItem
                assertEquals(found.absolutePath, row.mediaUrl)
                assertEquals("m", row.id, "the row is corrected, not replaced")
                assertFalse(shows("needs attention"))
            }
        } finally {
            found.delete()
        }
    }

    @Test
    fun `a cancelled locate leaves the row as it was`() =
        withCalendar(
            documentWith(service(items = listOf(clip("m", "/gone/clip.mp4")), planned = emptyMap())),
            host = CalendarHost(locateFile = { null }),
        ) { folder ->
            awaitText("1 row needs attention")
            onAllNodesWithContentDescription("File not found", substring = true).onFirst().performClick()
            waitForIdle()

            assertEquals("/gone/clip.mp4", (stored(folder).services.single().items.first() as ScheduleItem.MediaItem).mediaUrl)
            assertTrue(shows("1 row needs attention"))
        }

    @Test
    fun `a verse the Bible does not have opens the picker on the row, and the picker replaces`() =
        withCalendar(
            documentWith(
                service(
                    items = listOf(ScheduleItem.BibleVerseItem("v", "Nowhere", 1, 1, "", bookId = 0)),
                    planned = emptyMap(),
                )
            ),
            host = CalendarHost(bibleBooks = { BIBLE_BOOKS }),
        ) {
            awaitText("1 row needs attention")
            onAllNodesWithContentDescription("Book not in the primary Bible", substring = true).onFirst().performClick()
            awaitText("Editing")

            assertTrue(showsInSheet("Genesis", anchor = "Editing"), "the picker opened on the Bible tab")
        }

    @Test
    fun `several problems are counted together, and a cue is judged by its payload`() =
        withCalendar(
            documentWith(
                service(
                    items = listOf(
                        clip("m", "/gone/a.mp4"),
                        ScheduleItem.CueItem("c", CueAction.PROJECT, label = "Play", payload = clip("p", "/gone/b.mp4")),
                    ),
                    planned = emptyMap(),
                )
            ),
        ) {
            awaitText("2 rows need attention")
            assertEquals(2, onAllNodesWithContentDescription("File not found", substring = true).fetchSemanticsNodes().size)
        }

    @Test
    fun `a typed reference settles its book as it is added`() =
        withCalendar(
            documentWith(service(items = emptyList(), planned = emptyMap())),
            host = CalendarHost(
                bibleBooks = { BIBLE_BOOKS },
                resolveBookId = { name -> if (name.equals("Ps", ignoreCase = true)) 19 else null },
            ),
        ) { folder ->
            awaitText("Sunday Morning")
            clickFirst("Add song, verse or section")
            awaitText("Songs")
            clickFirst("Bible")
            awaitText("Genesis")
            typeIntoFirstField("Ps 2:3-5")
            awaitText("Ps 2:3-5")
            clickReferenceResult("Ps 2:3-5")

            val row = stored(folder).services.single().items.single() as ScheduleItem.BibleVerseItem
            assertEquals(19, row.bookId)
            assertEquals("Psalms 2:3-5", row.displayText, "the Bible's own spelling, from here on")
            assertFalse(shows("needs attention"))
        }

    @Test
    fun `a reference nobody recognises is kept as typed, and marked`() =
        withCalendar(
            documentWith(service(items = emptyList(), planned = emptyMap())),
            host = CalendarHost(bibleBooks = { BIBLE_BOOKS }),
        ) { folder ->
            awaitText("Sunday Morning")
            clickFirst("Add song, verse or section")
            awaitText("Songs")
            clickFirst("Bible")
            awaitText("Genesis")
            typeIntoFirstField("Nowhere 2:3")
            awaitText("Nowhere 2:3")
            clickReferenceResult("Nowhere 2:3")

            val row = stored(folder).services.single().items.single() as ScheduleItem.BibleVerseItem
            assertEquals(0, row.bookId)
            awaitText("1 row needs attention")
        }

    @Test
    fun `a cue whose song is gone is marked but has no fix from the cue`() {
        val songs = songFolderWith(libraSong("1", "Amazing Grace"))
        try {
            val missing = ScheduleItem.SongItem("gone", 7, "Nowhere", "Hymns", songId = "Hymns::7")
            val cue = ScheduleItem.CueItem("c", CueAction.PROJECT, label = "Play the song", payload = missing)
            withCalendar(
                documentWith(service(items = listOf(cue), planned = emptyMap())),
                songFolder = songs,
            ) {
                awaitText("1 row needs attention")
                val mark = onAllNodesWithContentDescription("Not in the song library", substring = true).onFirst()
                mark.performClick()
                waitForIdle()

                assertFalse(shows("Editing"), "a cue has no editor to open")
            }
        } finally {
            songs.deleteRecursively()
        }
    }

    @Test
    fun `every problem has words, with and without a fix`() = runComposeUiTest {
        setContent {
            AppThemeWrapper(theme = ThemeMode.LIGHT) {
                androidx.compose.foundation.layout.Column {
                    PreflightProblem.entries.forEach { ProblemMark(it, onFix = {}) }
                    ProblemMark(PreflightProblem.MISSING_FILE, onFix = null)
                }
            }
        }
        waitForIdle()
        listOf("File not found", "Folder not found", "Not in the song library", "Book not in", "Chapter not in", "Verse not in")
            .forEach {
                assertTrue(onAllNodesWithContentDescription(it, substring = true).fetchSemanticsNodes().isNotEmpty(), it)
            }
        assertEquals(
            7,
            onAllNodesWithContentDescription("not", substring = true, ignoreCase = true).fetchSemanticsNodes().size,
        )
        assertEquals(
            6,
            onAllNodesWithContentDescription("click to", substring = true).fetchSemanticsNodes().size,
            "the one with nothing to do only explains",
        )
    }

    /** The typed reference's own result row -- not the search field that holds the same text. */
    private fun ComposeUiTest.clickReferenceResult(reference: String) {
        onAllNodes(hasText(reference) and !hasSetTextAction()).onLast().performClick()
        waitForIdle()
    }

    // ── Skipped cues ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the toast says when a cue was skipped and why`() = runComposeUiTest {
        val cue = ScheduleItem.CueItem("c", CueAction.BLANK, label = "Blank at the end", absoluteTime = "09:45")
        setContent {
            AppThemeWrapper(theme = ThemeMode.LIGHT) {
                CueToast(event = FiredCue(cue, LocalTime.of(9, 45), skipped = true), onDismiss = {}, modifier = Modifier)
            }
        }
        waitForIdle()
        assertTrue(shows("Cue skipped"))
        assertTrue(shows("Something else was live"))
        assertTrue(shows("Blank at the end"))
    }

    @Test
    fun `the run of show marks a skipped cue rather than calling it fired`() {
        val cue = ScheduleItem.CueItem("c", CueAction.BLANK, label = "Blank", absoluteTime = "09:45")
        CueFeed.post(FiredCue(cue, LocalTime.of(9, 45), skipped = true))
        // On another day the clock is the stepped preview: it starts twenty minutes before the
        // service and each step is five, so two steps put it on the cue's own time.
        val tomorrow = service(date = TODAY.plusDays(1), items = listOf(cue), planned = emptyMap())
        withCalendar(documentWith(tomorrow)) {
            // The day after today, on the month grid.
            onAllNodesWithText(TODAY.plusDays(1).dayOfMonth.toString(), substring = false).onFirst().performClick()
            awaitText("Blank")
            repeat(2) {
                clickIcon("Run clock")
                waitForIdle()
            }

            assertTrue(onAllNodesWithText("Skipped", substring = true).fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodesWithText("Fired", substring = true).fetchSemanticsNodes().isEmpty())
        }
    }
}
