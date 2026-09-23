package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.Font
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The PDF a run of show is handed out on, and the re-keying every copy of one goes through. */
class ExportAndIdentityTest {

    private val folder: File = Files.createTempDirectory("calendar-export").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun song(id: String, title: String = "Amazing Grace") =
        ScheduleItem.SongItem(id = id, songNumber = 1, title = title, songbook = "Hymns", songId = "Hymns::1")

    private fun service(
        items: List<ScheduleItem> = listOf(
            ScheduleItem.LabelItem("h", "Worship", "#FFFFFF", "#5B9DF5"),
            song("a"),
            song("b", "Be Thou My Vision"),
        ),
        planned: Map<String, Int> = mapOf("a" to 300, "b" to 240),
        timing: Map<String, RowTiming> = mapOf("a" to RowTiming(startAt = "10:00", atEnd = RowEnd.NEXT)),
    ) = PlannedService(
        id = "svc", date = "2026-09-20", name = "Sunday Morning", startTime = "10:00",
        items = items, plannedSeconds = planned, timing = timing,
    )

    // ── The export ──────────────────────────────────────────────────────────────

    @Test
    fun `a run of show is written as a pdf`() {
        val target = File(folder, "run-of-show.pdf")

        exportRunOfShowPdf(service(), target, dateLabel = "Sunday 20 September", font = { null })

        assertTrue(target.isFile)
        assertTrue(target.length() > 0, "a file with nothing in it is not an export")
        assertEquals("%PDF", target.readBytes().take(4).map { it.toInt().toChar() }.joinToString(""))
    }

    @Test
    fun `font bytes that are not a font fall back, and Cyrillic is replaced rather than crashing`() {
        val target = File(folder, "cyrillic.pdf")
        val cyrillic = service(
            items = listOf(
                ScheduleItem.LabelItem("h", "Прославление", "#FFFFFF", "#5B9DF5"),
                song("a", "Великий Бог"),
                song("b", ""),
            ),
            planned = mapOf("a" to 300),
        ).copy(name = "Утреннее служение")

        exportRunOfShowPdf(cyrillic, target, dateLabel = "Воскресенье", font = { byteArrayOf(1, 2, 3) })

        assertTrue(target.isFile)
        assertTrue(target.length() > 0)
    }

    @Test
    fun `a script the embedded font has no glyph for is drawn from a system font rather than failing`() {
        // OpenSans, the face the app embeds, has no Tamil -- the export died on the first Tamil
        // title (Sentry CHURCH-PRESENTER-DESKTOP-7K), and then printed it as question marks.
        val openSans = assertNotNull(javaClass.getResourceAsStream("/fonts/OpenSans-Regular.ttf"))
            .use { it.readBytes() }
        val target = File(folder, "tamil.pdf")
        val title = "\u0BAA\u0BBE\u0B9F\u0BB2\u0BCD"
        val tamil = service(items = listOf(song("a", "$title Grace"))).copy(name = "Sunday")

        exportRunOfShowPdf(tamil, target, dateLabel = "Sunday", font = { openSans })

        val text = PDDocument.load(target).use { PDFTextStripper().getText(it) }
        assertTrue("Grace" in text, "what the font can draw is still text")
        val systemHasTamil = Font(Font.SANS_SERIF, Font.PLAIN, 1).canDisplayUpTo(title) == -1
        assertEquals(!systemHasTamil, "?" in text, "only a script no installed font has is marked")
    }

    @Test
    fun `an empty run of show still exports`() {
        val target = File(folder, "empty.pdf")

        exportRunOfShowPdf(
            service(items = emptyList(), planned = emptyMap(), timing = emptyMap()),
            target,
            dateLabel = "Sunday",
            font = { null },
        )

        assertTrue(target.isFile)
    }

    @Test
    fun `a long run of show runs onto more pages`() {
        val many = (1..60).map { song("s$it", "Song number $it") }
        val target = File(folder, "long.pdf")

        exportRunOfShowPdf(
            service(items = many, planned = many.associate { it.id to 180 }, timing = emptyMap()),
            target,
            dateLabel = "Sunday",
            font = { null },
        )

        assertTrue(target.length() > 2_000, "sixty rows do not fit on one page")
    }

    @Test
    fun `cues and headings are part of what is handed out`() {
        val target = File(folder, "cues.pdf")
        val rows = listOf(
            ScheduleItem.LabelItem("h", "Pre-Service", "#FFFFFF", "#4FD3E8"),
            ScheduleItem.CueItem(id = "c", action = CueAction.BLANK, absoluteTime = "09:55", label = "Blank"),
            song("a"),
        )

        exportRunOfShowPdf(
            service(items = rows, planned = mapOf("a" to 300), timing = emptyMap()),
            target,
            dateLabel = "Sunday",
            font = { null },
            use24Hour = false,
        )

        assertTrue(target.isFile)
    }

    // ── Re-keying ───────────────────────────────────────────────────────────────

    @Test
    fun `a copy shares no ids and keeps every estimate`() {
        val rows = listOf(song("a"), song("b"))

        val copy = copiedRows(
            rows,
            plannedSeconds = mapOf("a" to 300),
            timing = mapOf("b" to RowTiming(startAt = "10:05")),
        )

        assertFalse(copy.items.map { it.id }.any { it in setOf("a", "b") })
        assertEquals(300, copy.plannedSeconds[copy.items[0].id], "the estimate followed its row")
        assertEquals("10:05", copy.timing[copy.items[1].id]?.startAt, "and so did the timing")
    }

    @Test
    fun `a cue's payload is re-keyed with it`() {
        val cue = ScheduleItem.CueItem(
            id = "c", action = CueAction.PROJECT, payload = song("inner"),
        )

        val copied = cue.withNewId() as ScheduleItem.CueItem

        assertFalse(copied.id == "c")
        assertFalse((copied.payload as ScheduleItem.SongItem).id == "inner", "the payload too")
    }

    @Test
    fun `duplicate ids in a file are made unique on the way in`() {
        val document = CalendarDocument(
            services = listOf(
                service(items = listOf(song("same"), song("same", "Another song")), planned = mapOf("same" to 300)),
            ),
        )

        val cleaned = document.withUniqueRowIds()

        val ids = cleaned.services.single().items.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "a list keyed by id cannot hold two of one")
        // One id could only ever carry one estimate; each row that came out of it keeps that value
        // rather than one of them losing it.
        val estimates = cleaned.services.single().plannedSeconds
        assertEquals(setOf(300), estimates.values.toSet())
        assertTrue(ids.all { it in estimates }, "both rows carry it")
    }

    @Test
    fun `a file that is already sound is left alone`() {
        val document = CalendarDocument(services = listOf(service()))

        val cleaned = document.withUniqueRowIds()

        assertEquals(listOf("h", "a", "b"), cleaned.services.single().items.map { it.id })
    }
}
