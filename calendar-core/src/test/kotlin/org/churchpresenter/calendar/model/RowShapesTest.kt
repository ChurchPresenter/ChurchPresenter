package org.churchpresenter.calendar.model

import org.churchpresenter.calendar.model.RowSamples.EVERY_KIND
import org.churchpresenter.calendar.model.RowSamples.clip
import org.churchpresenter.calendar.model.RowSamples.deck
import org.churchpresenter.calendar.model.RowSamples.pictures
import org.churchpresenter.calendar.model.RowSamples.scene
import org.churchpresenter.calendar.model.RowSamples.song
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The per-type passes every row goes through, over **every** kind of row there is.
 *
 * Each of these is a `when` over the whole sealed hierarchy, and each is written once per type by
 * hand. A kind added to `ScheduleItem` compiles here without being thought about — a song copied
 * with a fresh id and a scene copied without one look the same in the planner until the second one
 * is pasted twice. [EVERY_KIND] is what makes that visible: the list is the hierarchy, so a new
 * type shows up as a failure in whichever pass forgot it.
 */
class RowShapesTest {

    @Test
    fun `the sample covers every kind of row there is`() {
        // The JVM's own record of the sealed hierarchy -- Kotlin's `sealedSubclasses` needs
        // kotlin-reflect, which this module does not ship.
        val sealed = ScheduleItem::class.java.permittedSubclasses.map { it.simpleName }.toSet()

        assertEquals(sealed, EVERY_KIND.map { it::class.simpleName }.toSet())
    }

    @Test
    fun `every kind of row takes a fresh id`() {
        EVERY_KIND.forEach { row ->
            val copy = row.withNewId()

            assertNotEquals(row.id, copy.id, "${row::class.simpleName} kept its id")
            assertEquals(row.displayText, copy.displayText, "${row::class.simpleName} lost its text")
        }
    }

    @Test
    fun `a cue's payload is re-keyed with the cue`() {
        val cue = ScheduleItem.CueItem(
            id = "c", action = CueAction.PROJECT, payload = ScheduleItem.SceneItem("s", "scene-1", "Welcome"),
        )

        val copy = cue.withNewId() as ScheduleItem.CueItem

        assertNotEquals("s", copy.payload?.id, "the payload would otherwise share the original's id")
    }

    @Test
    fun `every kind of row can be shown under another name`() {
        EVERY_KIND.forEach { row ->
            assertEquals("Renamed", row.renamed("Renamed").displayText, row::class.simpleName)
        }
    }

    // ── What a cue may point at ─────────────────────────────────────────────────

    @Test
    fun `structure, a lower third and an off-screen slot are not things a cue can project`() {
        val notProjectable = EVERY_KIND.filterNot { it.isProjectableByCue() }.map { it::class.simpleName }

        assertEquals(setOf("LabelItem", "LowerThirdItem", "CueItem", "MinistryItem"), notProjectable.toSet())
    }

    @Test
    fun `only something with a run to play through takes a play count`() {
        val repeatable = EVERY_KIND.filter { it.canPlayRepeatedly() }.map { it::class.simpleName }

        assertEquals(setOf("PictureItem", "PresentationItem", "MediaItem", "AnnouncementItem"), repeatable.toSet())
    }

    @Test
    fun `a timer has no run to repeat`() {
        val timer = ScheduleItem.AnnouncementItem(id = "t", text = "", isTimer = true, timerMinutes = 5)

        assertTrue(!timer.canPlayRepeatedly())
    }

    // ── The name a preset is offered under ──────────────────────────────────────

    @Test
    fun `a preset is named after the item, not after the row`() {
        assertEquals("Sunrise", suggestedPresetName(pictures()))
        assertEquals("Sermon", suggestedPresetName(deck()))
        assertEquals("Welcome", suggestedPresetName(clip()))
        assertEquals("Bible with Background", suggestedPresetName(scene()))
        assertEquals("1 - Song", suggestedPresetName(song()), "anything else is its row text")
    }

    @Test
    fun `an announcement is named after its first line and a timer after its readout`() {
        val announcement = ScheduleItem.AnnouncementItem(id = "a", text = "Welcome\nTo church")
        val timer = ScheduleItem.AnnouncementItem(id = "t", text = "", isTimer = true, timerMinutes = 5)

        assertEquals("Welcome", suggestedPresetName(announcement))
        assertEquals(timer.displayText, suggestedPresetName(timer))
    }

    @Test
    fun `a name keeps its punctuation and loses everything else`() {
        assertEquals("Pre-Service Loop (10 min)", cleanPresetName("  Pre-Service   Loop (10 min) 🎬 "))
        assertEquals("Welcome", cleanPresetName("🎬 Welcome"))
        assertEquals("", cleanPresetName("🎬"))
    }

    // ── Ids that have to be unique ──────────────────────────────────────────────

    @Test
    fun `a file with two rows under one id is opened with that fixed`() {
        val document = CalendarDocument(
            services = listOf(
                PlannedService(
                    id = "svc", date = "2026-09-20", name = "Sunday", startTime = "10:00",
                    items = listOf(song("dup"), song("dup"), song("other")),
                    plannedSeconds = mapOf("dup" to 300),
                    timing = mapOf("dup" to RowTiming(startAt = "10:00")),
                )
            )
        )

        val fixed = document.withUniqueRowIds().services.single()

        assertEquals(3, fixed.items.map { it.id }.toSet().size, "three rows, three ids")
        // The estimate and the timing cannot be split between them, so the copy takes the same.
        assertEquals(2, fixed.plannedSeconds.size)
        assertEquals(2, fixed.timing.size)
    }

    @Test
    fun `a file whose ids are already unique is opened untouched`() {
        val service = PlannedService(
            id = "svc", date = "2026-09-20", name = "Sunday", startTime = "10:00",
            items = listOf(song("a"), song("b")),
        )
        val document = CalendarDocument(services = listOf(service))

        assertEquals(document, document.withUniqueRowIds())
    }

    @Test
    fun `copied rows carry their estimates and timings to their new ids`() {
        val rows = listOf(song("a"), song("b"))

        val copied = copiedRows(
            rows,
            plannedSeconds = mapOf("a" to 300),
            timing = mapOf("b" to RowTiming(startAt = "10:05")),
        )

        assertEquals(setOf("a", "b"), setOf("a", "b") - copied.items.map { it.id }.toSet())
        assertEquals(300, copied.plannedSeconds[copied.items[0].id])
        assertEquals("10:05", copied.timing[copied.items[1].id]?.startAt)
    }

    @Test
    fun `a section heading is a label row in the section's own color`() {
        val section = sectionItem("Worship", "#5B9DF5")

        assertEquals("Worship", section.text)
        assertEquals("#5B9DF5", section.backgroundColor)
        assertTrue(section.id.isNotEmpty())
    }

    @Test
    fun `a preset goes into the run of show under the name it was saved as`() {
        val preset = ItemPreset(id = "p", name = "Welcome loop", item = scene())

        val row = preset.asRow()

        assertEquals("Welcome loop", row.displayText, "and not `Scene: …`")
        assertNotEquals(scene().id, row.id)
    }
}
