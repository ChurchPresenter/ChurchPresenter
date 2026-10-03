@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.model.PdfExportSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The window's less travelled paths: a calendar with no readable copy at all, a folder the
 * operator gave up looking for, a remembered export folder that is not a folder any more, and a
 * reference typed with the Bible's own book name.
 */
class WindowEdgesTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    @Test
    fun `a calendar with no readable copy says it was lost, not recovered`() {
        val folder = Files.createTempDirectory("calendar-lost").toFile()
        try {
            File(folder, "calendar.json").writeText("{ not json")
            withCalendarFolder(folder) {
                awaitText("The calendar could not be read")
                assertTrue(shows("Neither calendar.json nor any of its backups"))
                assertTrue(!shows("Recovered from a backup"))
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a cancelled folder locate leaves the row as it was`() {
        val pictures = ScheduleItem.PictureItem("p", "/gone/pictures", "Lobby", 4)
        var asked: String? = null
        withCalendar(
            documentWith(service(items = listOf(pictures), planned = emptyMap())),
            host = CalendarHost(locateFolder = { asked = it.path; null }),
        ) { folder ->
            awaitText("1 row needs attention")
            onAllNodesWithContentDescription("Folder not found", substring = true).onFirst().performClick()
            waitForIdle()

            assertEquals(File("/gone/pictures").path, asked, "it opened where the folder was")
            val row = stored(folder).services.single().items.single() as ScheduleItem.PictureItem
            assertEquals("/gone/pictures", row.folderPath)
        }
    }

    @Test
    fun `a remembered export folder that is no longer a folder is not offered`() {
        val notAFolder = Files.createTempFile("calendar-export", ".txt").toFile()
        var offered: File? = notAFolder
        try {
            val document = documentWith(service()).let {
                it.copy(preferences = it.preferences.copy(pdfExport = PdfExportSettings(lastFolder = notAFolder.path)))
            }
            withCalendar(document, host = CalendarHost(chooseExportFile = { _, folder -> offered = folder; null })) {
                awaitText("Amazing Grace")
                clickFirst("Export PDF")
                waitUntil("the save dialog was asked") { offered != notAFolder }
                assertNull(offered)
            }
        } finally {
            notAFolder.delete()
        }
    }

    @Test
    fun `a reference typed with the Bible's own book name is settled without asking the app`() {
        var asked = false
        withCalendar(
            documentWith(service(items = emptyList(), planned = emptyMap())),
            host = CalendarHost(bibleBooks = { BIBLE_BOOKS }, resolveBookId = { asked = true; null }),
        ) { folder ->
            awaitText("Sunday Morning")
            clickFirst("Add song, verse or section")
            awaitText("Songs")
            clickFirst("Bible")
            awaitText("Genesis")
            typeIntoFirstField("psalms 2:1")
            awaitText("psalms 2:1")
            clickReference("psalms 2:1")

            val row = stored(folder).services.single().items.single() as ScheduleItem.BibleVerseItem
            assertEquals(19, row.bookId)
            assertTrue(!asked, "the name matched the Bible's own")
        }
    }

    private fun ComposeUiTest.clickReference(reference: String) {
        onAllNodes(hasText(reference, ignoreCase = true) and !hasSetTextAction()).onLast().performClick()
        waitForIdle()
    }
}
