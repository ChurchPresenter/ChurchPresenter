package org.churchpresenter.calendar.sync

import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.songs.SongItem
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogSyncTest {

    private val folder: File = Files.createTempDirectory("catalog-sync").toFile()
    private val relay = FakeRelay().also { it.desktopToken = "desk-token" }
    private val envelope = Envelope(ByteArray(Envelope.KEY_BYTES) { 3 })
    private val sealing = Sealing(envelope, "inst")
    private val client = RelayClient("https://relay.example", "inst", "install-A", relay)
    private val store = CatalogSyncStore(folder)
    private val json = Json { ignoreUnknownKeys = true }

    private var today = LocalDate.of(2026, 9, 20)
    private var now = Instant.parse("2026-09-20T12:00:00Z")
    private var songs = listOf(
        SongItem(number = "42", title = "Here I Am to Worship", songbook = "Hymnal", secondaryTitle = "Вот я, Господь"),
        SongItem(number = "7", title = "Never Sung", songbook = "Hymnal"),
        SongItem(number = "1", title = "Shout", songbook = "Songs of Praise"),
    )
    private val seconds: (SongItem) -> Int? = { if (it.number == "42") 270 else null }

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun sync() = CatalogSync(store, client, sealing, { songs }, seconds, { today }, { now })

    private fun opened(id: String): CatalogRecord {
        val bytes = envelope.open(relay.records.getValue(id).box, "inst", id)!!
        return json.decodeFromString(CatalogRecord.serializer(), bytes.decodeToString())
    }

    @Test
    fun `every songbook goes to the relay once, sealed, with lengths and second titles`() {
        assertEquals(2, sync().push("desk-token"))

        assertEquals(setOf("catalog:Hymnal-cf49adf4", "catalog:Songs_of_Praise-45987dd6"), relay.records.keys)
        val hymnal = opened("catalog:Hymnal-cf49adf4")
        assertEquals("Hymnal", hymnal.songbook)
        assertEquals(
            listOf(CatalogSong("7", "Never Sung"), CatalogSong("42", "Here I Am to Worship", 270, "Вот я, Господь")),
            hymnal.songs,
        )
        assertEquals("2028-12-20", relay.records.getValue("catalog:Hymnal-cf49adf4").keepUntil)
        // The relay only ever sees the box.
        assertFalse("Worship" in relay.records.getValue("catalog:Hymnal-cf49adf4").box)

        // A second pass with nothing changed writes nothing.
        val callsBefore = relay.calls.size
        assertEquals(0, sync().push("desk-token"))
        assertEquals(callsBefore, relay.calls.size)
    }

    @Test
    fun `after Start over the new instance is sent every songbook, though nothing changed`() {
        assertEquals(2, sync().push("desk-token"))
        now = now.plusSeconds(60)

        val fresh = Sealing(envelope, "inst-new")
        val pushedAgain = CatalogSync(store, client, fresh, { songs }, seconds, { today }, { now }).push("desk-token")

        assertEquals(2, pushedAgain, "the new instance's relay holds none of them")
        assertEquals("inst-new", store.load().instanceId)
    }

    @Test
    fun `a changed book is written again, but not within a day of its last push`() {
        sync().push("desk-token")
        songs = songs + SongItem(number = "43", title = "How Great Thou Art", songbook = "Hymnal")

        now = now.plusSeconds(6 * 3_600)
        assertEquals(0, sync().push("desk-token"))
        assertEquals(2, opened("catalog:Hymnal-cf49adf4").songs.size)

        now = now.plusSeconds(19 * 3_600)
        assertEquals(1, sync().push("desk-token"))
        assertEquals(3, opened("catalog:Hymnal-cf49adf4").songs.size)
    }

    @Test
    fun `a book that is gone is deleted from the relay, and a big one is split into parts`() {
        sync().push("desk-token")
        songs = List(4_500) { SongItem(number = "${it + 1}", title = "Song ${it + 1}", songbook = "Big") }
        now = now.plusSeconds(2 * 24 * 3_600)

        val changed = sync().push("desk-token")

        assertEquals(5, changed)
        assertEquals(
            setOf("catalog:Big-b8d16159", "catalog:Big-b8d16159:1", "catalog:Big-b8d16159:2"),
            relay.records.keys,
        )
        assertEquals(setOf("catalog:Hymnal-cf49adf4", "catalog:Songs_of_Praise-45987dd6"), relay.tombstones.keys)
        assertEquals(2_000, opened("catalog:Big-b8d16159").songs.size)
        assertEquals(500, opened("catalog:Big-b8d16159:2").songs.size)
        assertEquals(2, opened("catalog:Big-b8d16159:2").part)
    }

    @Test
    fun `a part whose keep-until is running out is rewritten even though nothing changed`() {
        sync().push("desk-token")
        val before = relay.records.getValue("catalog:Hymnal-cf49adf4").keepUntil
        today = today.plusYears(2)
        now = now.plusSeconds(2L * 366 * 24 * 3_600)

        assertEquals(2, sync().push("desk-token"))
        assertTrue(relay.records.getValue("catalog:Hymnal-cf49adf4").keepUntil > before)
    }

    @Test
    fun `an empty library is never mistaken for a library with no songs`() {
        sync().push("desk-token")
        songs = emptyList()

        assertEquals(0, sync().push("desk-token"))
        assertEquals(2, relay.records.size)
        assertTrue(relay.tombstones.isEmpty())
    }

    @Test
    fun `the memory of what was pushed survives on disk and starts empty when unreadable`() {
        sync().push("desk-token")
        assertEquals(2, store.load().pushed.size)
        store.file.writeText("{ not json")
        assertEquals(0, store.load().pushed.size)
        assertNull(store.load().pushed["catalog:Hymnal-cf49adf4"])
    }
}
