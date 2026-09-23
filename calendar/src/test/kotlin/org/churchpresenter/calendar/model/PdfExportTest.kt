package org.churchpresenter.calendar.model

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfExportTest {

    private val folder: File = Files.createTempDirectory("pdf-export").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun heading(id: String, text: String) =
        ScheduleItem.LabelItem(id = id, text = text, textColor = "#FFFFFF", backgroundColor = "#5B9DF5")

    private fun song(id: String, title: String) =
        ScheduleItem.SongItem(id = id, songNumber = 0, title = title, songbook = "Hymns")

    private val cue = ScheduleItem.CueItem(id = "c", action = "BLANK", label = "Blank the screens")

    private val service = PlannedService(
        id = "svc",
        date = "2026-09-27",
        name = "Sunday Morning",
        startTime = "10:00",
        items = listOf(
            heading("h1", "Pre-Service"),
            song("pre", "Countdown Loop"),
            heading("h2", "Worship"),
            song("a", "Amazing Grace"),
            cue,
            song("b", "Be Thou My Vision"),
        ),
        plannedSeconds = mapOf("pre" to 420, "a" to 300, "b" to 240),
    )

    private fun export(
        audience: PdfAudience,
        settings: PdfExportSettings = PdfExportSettings(),
    ): Pair<String, PDDocument> {
        val target = File(folder, "${audience.name}.pdf")
        exportRunOfShowPdf(service, target, "Sep 27", font = { null }, settings = settings, audience = audience)
        val document = PDDocument.load(target)
        return PDFTextStripper().getText(document) to document
    }

    private fun textOf(audience: PdfAudience, settings: PdfExportSettings = PdfExportSettings()): String =
        export(audience, settings).let { (text, document) -> document.close(); text }

    private fun imagesIn(settings: PdfExportSettings): Int = export(PdfAudience.STAFF, settings).let { (_, document) ->
        document.use { it.getPage(0).resources.xObjectNames.count() }
    }

    private fun png(): File = File(folder, "logo.png").also {
        ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", it)
    }

    @Test
    fun `the staff copy keeps every row with its clock time and length`() {
        val text = textOf(PdfAudience.STAFF)

        assertTrue("PRE-SERVICE" in text)
        assertTrue("Countdown Loop" in text)
        assertTrue("Blank the screens" in text)
        assertTrue("10:07" in text, "Amazing Grace starts after seven minutes of countdown")
        assertTrue("5:00" in text)
        assertTrue("16:00" in text, "the total length is in the heading")
    }

    @Test
    fun `the public copy has only the start time, and none of the staff-only rows or cues`() {
        val text = textOf(PdfAudience.PUBLIC)

        assertTrue("10:00" in text, "the start time")
        assertTrue("Amazing Grace" in text)
        assertTrue("Be Thou My Vision" in text)
        assertFalse("10:07" in text, "no row times")
        assertFalse("5:00" in text, "no lengths")
        assertFalse("16:00" in text, "no total")
        assertFalse("PRE-SERVICE" in text)
        assertFalse("Countdown Loop" in text)
        assertFalse("Blank the screens" in text)
    }

    @Test
    fun `the letterhead carries the church name and every address line`() {
        val settings = PdfExportSettings(churchName = "Grace Chapel", churchAddress = "1 Main St\n\nSpringfield")

        val text = textOf(PdfAudience.PUBLIC, settings)

        assertTrue("Grace Chapel" in text)
        assertTrue("1 Main St" in text)
        assertTrue("Springfield" in text)
        assertTrue(text.indexOf("Grace Chapel") < text.indexOf("Sunday Morning"), "above the service")
    }

    @Test
    fun `a logo is drawn onto the first page`() {
        assertEquals(1, imagesIn(PdfExportSettings(logoPath = png().absolutePath)))
    }

    @Test
    fun `a logo that is missing or not an image is left out rather than failing the export`() {
        val notAnImage = File(folder, "logo.png.txt").apply { writeText("not a picture") }

        assertEquals(0, imagesIn(PdfExportSettings(logoPath = File(folder, "gone.png").absolutePath)))
        assertEquals(0, imagesIn(PdfExportSettings(logoPath = notAnImage.absolutePath)))
    }

    @Test
    fun `staff rows are every row, public rows drop cues and staff-only sections`() {
        val settings = PdfExportSettings(staffOnlySections = listOf("pre-service"))

        assertEquals(service.items, settings.rowsFor(service.items, PdfAudience.STAFF))
        assertEquals(
            listOf("h2", "a", "b"),
            settings.rowsFor(service.items, PdfAudience.PUBLIC).map { it.id },
            "matched regardless of case, until the next heading",
        )
    }

    @Test
    fun `rows before any heading are public`() {
        val rows = listOf(song("x", "Welcome"), heading("h", "Pre-Service"), song("y", "Hidden"))

        assertEquals(listOf("x"), PdfExportSettings().rowsFor(rows, PdfAudience.PUBLIC).map { it.id })
    }

    @Test
    fun `a section is switched to staff-only and back`() {
        val on = PdfExportSettings(staffOnlySections = emptyList()).withStaffOnly("Word", true)
        assertTrue(on.isStaffOnly("WORD"))

        val off = on.withStaffOnly("word", false)
        assertFalse(off.isStaffOnly("Word"))
        assertEquals(emptyList(), off.staffOnlySections)
    }

    @Test
    fun `renaming a section carries its staff-only switch with it`() {
        val renamed = PdfExportSettings().withSectionRenamed("pre-service", "Before")

        assertTrue(renamed.isStaffOnly("Before"))
        assertFalse(renamed.isStaffOnly("Pre-Service"))
        assertTrue(renamed.isStaffOnly("Post-Service"), "the others are untouched")
    }

    @Test
    fun `new settings default to the staff copy with pre- and post-service staff-only`() {
        val settings = PdfExportSettings()

        assertEquals(PdfAudience.STAFF, settings.lastAudience)
        assertEquals(listOf("Pre-Service", "Post-Service"), settings.staffOnlySections)
    }
}
