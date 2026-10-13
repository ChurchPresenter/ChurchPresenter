package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresetMergeTest {

    private fun at(minute: Int): Instant = Instant.parse("2026-09-20T09:%02d:00Z".format(minute))

    private fun preset(id: String, name: String = "Welcome", saved: Instant = at(0)) = ItemPreset(
        id = id,
        name = name,
        item = ScheduleItem.SongItem(id, 1, "Song", "Hymns", "Hymns::1"),
        savedAt = storedInstant(saved),
    )

    private fun document(vararg presets: ItemPreset) = PresetDocument(presets = presets.toList())

    @Test
    fun `a preset saved on one machine arrives on the other`() {
        val here = document(preset("a"))
        val there = document(preset("a"), preset("b", name = "Offering"))

        assertEquals(setOf("a", "b"), here.mergedWith(there, at(10)).presets.map { it.id }.toSet())
    }

    @Test
    fun `saving the same name on both machines keeps the newer`() {
        val here = document(preset("laptop", saved = at(2)))
        val there = document(preset("desktop", saved = at(1)))

        assertEquals(listOf("laptop"), here.mergedWith(there, at(10)).presets.map { it.id })
    }

    @Test
    fun `a preset deleted on one machine does not come back from the other`() {
        val here = document(preset("a"), preset("b")).withoutPreset("b", at(5))
        val there = document(preset("a"), preset("b"))

        assertEquals(listOf("a"), here.mergedWith(there, at(10)).presets.map { it.id })
        assertEquals(listOf("a"), there.mergedWith(here, at(10)).presets.map { it.id })
    }

    @Test
    fun `a preset saved again after being deleted elsewhere stays`() {
        val here = document(preset("a")).withoutPreset("a", at(5))
        val there = document(preset("a", saved = at(6)))

        assertEquals(listOf("a"), here.mergedWith(there, at(10)).presets.map { it.id })
    }

    @Test
    fun `saving a preset again clears its tombstone`() {
        val gone = document(preset("a")).withoutPreset("a", at(5))

        assertTrue(gone.withPreset(preset("a", saved = at(6))).deletedPresets.isEmpty())
    }

    @Test
    fun `the merge is the same file from either side`() {
        val here = document(preset("x", name = "One", saved = at(1)), preset("y", name = "Two", saved = at(1)))
            .withoutPreset("z", at(3))
        val there = document(preset("y", name = "Two", saved = at(2)), preset("z", name = "Three"))

        assertEquals(here.mergedWith(there, at(10)), there.mergedWith(here, at(10)))
    }

    @Test
    fun `tombstones older than the lifetime are forgotten`() {
        val old = document().withoutPreset("a", at(0))
        val later = at(0).plus(TOMBSTONE_LIFETIME).plusSeconds(60)

        assertTrue(old.mergedWith(document(), later).deletedPresets.isEmpty())
    }

    @Test
    fun `a local date-time stamp from an older file reads as an instant`() {
        val zone = ZoneId.of("Europe/Warsaw")

        assertEquals("2026-09-20T07:30:00Z", normalizedStamp("2026-09-20T09:30:00", zone))
        assertEquals("2026-09-20T07:30:00Z", normalizedStamp("2026-09-20T07:30:00Z", zone))
        assertEquals("", normalizedStamp("yesterday", zone))
        assertEquals("", normalizedStamp("", zone))
    }
}
