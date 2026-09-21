package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolverTest {

    private val today = LocalDate.of(2026, 9, 20)
    private val songs = listOf(
        SongItem(number = "42", title = "Here I Am to Worship", songbook = "Hymnal"),
        SongItem(number = "7", title = "Be Thou My Vision", songbook = "Hymnal"),
        SongItem(number = "8", title = "Be Thou My Vision", songbook = "Other"),
    )
    private val presetItem = ScheduleItem.PictureItem("pic", "/Users/x/welcome", "Welcome loop", 24)
    private val presets = listOf(ItemPreset("p1", "Welcome slideshow", presetItem))
    private val resolver = Resolver(songs, presets, today)

    private fun remote(vararg rows: RemoteRow, id: String = "svc") = RemoteService(
        id = id,
        date = "2026-09-27",
        startTime = "10:00",
        name = "Sunday Morning",
        rows = rows.toList(),
        updatedAt = "2026-09-21T00:00:00Z",
    )

    @Test
    fun `a song resolves by id, then by exact title, and keeps its row id`() {
        val service = resolver.resolve(remote(
            RemoteRow.Song("a", "whatever", songId = "Hymnal::42"),
            RemoteRow.Song("b", "42 - Here I Am to Worship"),
        ), local = null)!!.service

        val a = assertIs<ScheduleItem.SongItem>(service.items[0])
        assertEquals("a", a.id)
        assertEquals(42, a.songNumber)
        assertEquals("Hymnal::42", a.songId)
        assertEquals("Hymnal::42", (service.items[1] as ScheduleItem.SongItem).songId)
        assertTrue(service.unresolvedRows.isEmpty())
    }

    @Test
    fun `an ambiguous or unknown song is kept and flagged, never guessed`() {
        val resolved = resolver.resolve(remote(
            RemoteRow.Song("a", "Be Thou My Vision"),
            RemoteRow.Song("b", "Not In Library"),
        ), local = null)!!

        assertEquals(setOf("a", "b"), resolved.unresolved.keys)
        assertEquals(Resolver.UNRESOLVED_SONG, resolved.unresolved["a"])
        val kept = assertIs<ScheduleItem.SongItem>(resolved.service.items[1])
        assertEquals("Not In Library", kept.title)
        assertEquals("", kept.songId)
    }

    @Test
    fun `a reference becomes a verse row, an unreadable one a flagged placeholder`() {
        val resolved = resolver.resolve(remote(
            RemoteRow.Bible("a", "Psalms 100:1-5"),
            RemoteRow.Bible("b", "not a reference"),
        ), local = null)!!

        val verse = assertIs<ScheduleItem.BibleVerseItem>(resolved.service.items[0])
        assertEquals("a", verse.id)
        assertEquals(100, verse.chapter)
        assertEquals("1-5", verse.verseRange)
        assertIs<ScheduleItem.MinistryItem>(resolved.service.items[1])
        assertEquals(Resolver.UNRESOLVED_REFERENCE, resolved.unresolved["b"])
    }

    @Test
    fun `a book number from the phone makes the reference language-proof, a bad one is ignored`() {
        val resolved = resolver.resolve(remote(
            RemoteRow.Bible("a", "Иоанна 3:16", bookId = 43),
            RemoteRow.Bible("b", "John 3:16", bookId = 0),
            RemoteRow.Bible("c", "John 3:16", bookId = 999),
        ), local = null)!!

        val russian = assertIs<ScheduleItem.BibleVerseItem>(resolved.service.items[0])
        assertEquals(43, russian.bookId)
        assertEquals("Иоанна", russian.bookName)
        assertEquals("Иоанна 3:16", russian.displayText)
        assertEquals(0, assertIs<ScheduleItem.BibleVerseItem>(resolved.service.items[1]).bookId)
        assertEquals(0, assertIs<ScheduleItem.BibleVerseItem>(resolved.service.items[2]).bookId)
    }

    @Test
    fun `a preset reference is expanded from the local preset, never from the wire`() {
        val resolved = resolver.resolve(remote(
            RemoteRow.Preset("a", "Anything", presetId = "p1"),
            RemoteRow.Preset("b", "Gone", presetId = "missing"),
        ), local = null)!!

        val picture = assertIs<ScheduleItem.PictureItem>(resolved.service.items[0])
        assertEquals("a", picture.id)
        assertEquals("/Users/x/welcome", picture.folderPath)
        assertIs<ScheduleItem.MinistryItem>(resolved.service.items[1])
        assertEquals(Resolver.UNRESOLVED_PRESET, resolved.unresolved["b"])
    }

    @Test
    fun `a ref comes back only from the local copy of the same service`() {
        val local = PlannedService(
            id = "svc", date = "2026-09-27", name = "Sunday", startTime = "10:00",
            items = listOf(ScheduleItem.WebsiteItem("w", "https://church.example/live", "Stream")),
        )

        val kept = resolver.resolve(remote(RemoteRow.Ref("w", "Renamed", RemoteKind.WEBSITE)), local)!!
        val dropped = resolver.resolve(remote(RemoteRow.Ref("w", "Stream", RemoteKind.WEBSITE)), local = null)!!

        val site = assertIs<ScheduleItem.WebsiteItem>(kept.service.items.single())
        assertEquals("https://church.example/live", site.url)
        assertTrue(dropped.service.items.isEmpty())
        assertEquals(1, dropped.dropped)
    }

    @Test
    fun `sections take the color when valid and the default otherwise`() {
        val service = resolver.resolve(remote(
            RemoteRow.Section("a", "Worship", "#5B9DF5"),
            RemoteRow.Section("b", "Word", "javascript:alert(1)"),
        ), local = null)!!.service

        assertEquals("#5B9DF5", (service.items[0] as ScheduleItem.LabelItem).backgroundColor)
        assertTrue((service.items[1] as ScheduleItem.LabelItem).backgroundColor.matches(Regex("#[0-9A-Fa-f]{6}")))
    }

    @Test
    fun `the header is validated and stamped`() {
        assertNull(resolver.resolve(remote().copy(id = "bad id"), null))
        assertNull(resolver.resolve(remote().copy(date = "2020-01-01"), null))
        assertNull(resolver.resolve(remote().copy(startTime = "10 AM"), null))

        val hostile = remote().copy(name = "‮" + "x".repeat(500), kind = "party", updatedAt = "yesterday")
        val service = resolver.resolve(hostile, null)!!.service
        assertEquals(WireLimits.NAME_CHARS, service.name.length)
        assertEquals(ServiceKind.SUNDAY.id, service.kind)
        assertEquals("", service.updatedAt)
    }

    @Test
    fun `lengths and timing are clamped and only kept for surviving rows`() {
        val service = resolver.resolve(remote(RemoteRow.Ministry("a", "Violin")).copy(
            plannedSeconds = mapOf("a" to 999_999, "ghost" to 5),
            timing = mapOf(
                "a" to RowTiming(startAt = "9am", repeats = 500, atEnd = "explode", runSeconds = -3),
                "ghost" to RowTiming(repeats = 0),
            ),
        ), null)!!.service

        assertEquals(mapOf("a" to WireLimits.MAX_PLANNED_SECONDS), service.plannedSeconds)
        val timing = service.timing.getValue("a")
        assertEquals("", timing.startAt)
        assertEquals(WireLimits.MAX_REPEATS, timing.repeats)
        assertEquals(RowEnd.HOLD, timing.atEnd)
        assertEquals(0, timing.runSeconds)
        assertNull(service.timing["ghost"])
    }

    @Test
    fun `duplicate and malformed row ids are dropped, rows are capped`() {
        val rows = List(WireLimits.ROWS_PER_SERVICE + 10) { RemoteRow.Ministry("r$it", "Item") } +
            RemoteRow.Ministry("r1", "Duplicate") + RemoteRow.Ministry("bad id", "Malformed")

        val resolved = resolver.resolve(remote(*rows.toTypedArray()), null)!!

        assertEquals(WireLimits.ROWS_PER_SERVICE, resolved.service.items.size)
        assertEquals(12, resolved.dropped)
    }

    @Test
    fun `hostile input never throws and never creates a row that goes on screen`() {
        val random = Random(20260920)
        val onScreen = setOf(
            ScheduleItem.PictureItem::class, ScheduleItem.PresentationItem::class, ScheduleItem.MediaItem::class,
            ScheduleItem.WebsiteItem::class, ScheduleItem.CueItem::class, ScheduleItem.LowerThirdItem::class,
            ScheduleItem.SceneItem::class, ScheduleItem.DictionaryItem::class,
        )
        val junk = { "\u0000‮<script>../../etc/passwd" + random.nextBytes(64).decodeToString() }
        repeat(200) {
            val rows = List(random.nextInt(0, 12)) { i ->
                when (random.nextInt(6)) {
                    0 -> RemoteRow.Section("r$i", junk(), junk())
                    1 -> RemoteRow.Song("r$i", junk(), junk(), junk(), junk())
                    2 -> RemoteRow.Bible("r$i", junk())
                    3 -> RemoteRow.Ministry("r$i", junk(), junk())
                    4 -> RemoteRow.Preset("r$i", junk(), junk(), junk())
                    else -> RemoteRow.Ref(
                        "r$i",
                        junk(),
                        listOf(RemoteKind.CUE, RemoteKind.WEBSITE, junk()).random(random),
                    )
                }
            }
            val hostile = remote(*rows.toTypedArray()).copy(name = junk(), kind = junk(), seriesId = junk())
            val resolved = resolver.resolve(hostile, local = null)
            assertNotNull(resolved)
            for (item in resolved.service.items) {
                assertTrue(item::class !in onScreen, "${item::class.simpleName} came from the wire")
                assertTrue(item.displayText.none { it.isISOControl() })
            }
        }
    }
}
