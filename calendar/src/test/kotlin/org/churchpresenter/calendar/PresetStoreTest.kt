package org.churchpresenter.calendar

import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PresetDocument
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresetStoreTest {

    private val folder: File = Files.createTempDirectory("calendar-presets").toFile()
    private val now: Instant = Instant.parse("2026-09-20T10:00:00Z")
    private var saves = 0

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun store(at: Instant = now) = PresetStore(folder, now = { at }, onSaved = { saves++ })

    private fun song(id: String) = ScheduleItem.SongItem(
        id = id, songNumber = 1, title = "Song $id", songbook = "", songId = "",
    )

    private fun preset(id: String, name: String, savedAt: String = "2026-09-01T00:00:00Z") =
        ItemPreset(id = id, name = name, item = song(id), savedAt = savedAt)

    @Test
    fun `a preset is saved under its trimmed name with the instant it was saved`() {
        val saved = store().add("  Countdown ", song("a"))

        assertEquals("Countdown", saved.name)
        assertEquals("2026-09-20T10:00:00Z", saved.savedAt)
        assertEquals(listOf(saved), store().load().presets)
        assertEquals(1, saves)
    }

    @Test
    fun `saving a preset under a name already taken replaces it`() {
        val store = store()
        store.add("Countdown", song("a"))

        store.add("countdown", song("b"))

        val only = store.load().presets.single()
        assertEquals("b", only.item.id)
    }

    @Test
    fun `removing a preset leaves a tombstone so it does not come back from another machine`() {
        val store = store()
        val saved = store.add("Countdown", song("a"))

        store.remove(saved.id)

        val document = store.load()
        assertTrue(document.presets.isEmpty())
        assertEquals("2026-09-20T10:00:00Z", document.deletedPresets[saved.id])
    }

    @Test
    fun `a file that does not parse opens as empty`() {
        File(folder, PRESET_FILE).writeText("{ not json")

        assertEquals(PresetDocument(), store().load())
    }

    @Test
    fun `a stamp written as a local date-time is read as an instant`() {
        store().save(PresetDocument(presets = listOf(preset("a", "Old", savedAt = "2026-08-01T09:30:00"))))

        val expected = LocalDateTime.parse("2026-08-01T09:30:00").atZone(ZoneId.systemDefault()).toInstant()
        assertEquals(expected.toString(), store().load().presets.single().savedAt)
    }

    @Test
    fun `reloading against an identical file writes nothing`() {
        val store = store()
        val known = PresetDocument(presets = listOf(preset("a", "One")))
        store.save(known)
        saves = 0

        assertEquals(known, store.reloadMerging(known))

        assertEquals(0, saves)
    }

    @Test
    fun `what the other machine saved is folded in and the merge written back`() {
        val store = store()
        val known = PresetDocument(presets = listOf(preset("a", "Mine")))
        store.save(PresetDocument(presets = listOf(preset("b", "Theirs"))))
        saves = 0

        val merged = store.reloadMerging(known)

        assertEquals(setOf("a", "b"), merged.presets.map { it.id }.toSet())
        assertEquals(1, saves, "the file lacked ours, so the merge is written")
        assertEquals(merged, store.load())
    }

    @Test
    fun `a file that already holds everything is not written again`() {
        val store = store()
        val known = PresetDocument(presets = listOf(preset("a", "Mine")))
        store.save(PresetDocument(presets = listOf(preset("a", "Mine"), preset("b", "Theirs"))))
        saves = 0

        val merged = store.reloadMerging(known)

        assertEquals(2, merged.presets.size)
        assertEquals(0, saves)
    }
}
