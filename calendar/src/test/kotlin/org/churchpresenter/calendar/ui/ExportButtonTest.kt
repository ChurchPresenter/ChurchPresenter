@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PdfAudience
import org.churchpresenter.calendar.model.PdfExportSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportButtonTest {

    private val plan = service(
        items = listOf(heading("h"), song("a", "Amazing Grace"), song("b", "Be Thou My Vision")),
        planned = mapOf("a" to 300, "b" to 240),
    )

    private fun withExport(
        document: CalendarDocument = documentWith(plan),
        body: ComposeUiTest.(target: File, folder: File) -> Unit,
    ) {
        val target = Files.createTempFile("run-of-show", ".pdf").toFile().apply { delete() }
        try {
            val host = CalendarHost(chooseExportFile = { _, _ -> target }, pdfFont = { null })
            withCalendar(document, host = host) { folder ->
                awaitText("Amazing Grace")
                body(target, folder)
            }
        } finally {
            target.delete()
        }
    }

    private fun ComposeUiTest.written(target: File): String {
        waitUntil("the export was written") { target.length() > 0 }
        return PDDocument.load(target).use { PDFTextStripper().getText(it) }
    }

    private fun ComposeUiTest.pick(copy: String) {
        clickIcon("Choose which copy to export")
        clickLast(copy)
    }

    @Test
    fun `the button names the staff copy until another is picked`() = withExport { _, _ ->
        assertTrue(shows("Staff"))
        assertFalse(shows("Public"))
    }

    @Test
    fun `the left half exports the staff copy, with row times and lengths`() = withExport { target, _ ->
        clickFirst("Export PDF")

        val text = written(target)
        assertTrue("10:05" in text, "Be Thou My Vision's clock time")
        assertTrue("5:00" in text)
    }

    @Test
    fun `the menu offers both copies with what each holds`() = withExport { _, _ ->
        clickIcon("Choose which copy to export")

        assertTrue(shows("Order of service for the congregation"))
        assertTrue(shows("The full run of show"))
    }

    @Test
    fun `picking the public copy exports it and makes it the default`() = withExport { target, folder ->
        pick("Public")

        val text = written(target)
        assertTrue("Be Thou My Vision" in text)
        assertFalse("10:05" in text, "no row times")
        assertFalse("5:00" in text, "no lengths")
        assertEquals(PdfAudience.PUBLIC, CalendarStore(folder).load().document.preferences.pdfExport.lastAudience)
        assertTrue(shows("Public"), "the button now names it")
    }

    @Test
    fun `the left half exports whichever copy was picked last`() = withExport(
        document = documentWith(plan).let { doc ->
            val publicByDefault = PdfExportSettings(lastAudience = PdfAudience.PUBLIC)
            doc.copy(preferences = doc.preferences.copy(pdfExport = publicByDefault))
        },
    ) { target, _ ->
        clickFirst("Export PDF")

        assertFalse("5:00" in written(target))
    }

    @Test
    fun `picking the copy already chosen exports without rewriting the preference`() = withExport { target, folder ->
        pick("Church staff")

        assertTrue("5:00" in written(target))
        assertEquals(PdfAudience.STAFF, CalendarStore(folder).load().document.preferences.pdfExport.lastAudience)
    }
}
