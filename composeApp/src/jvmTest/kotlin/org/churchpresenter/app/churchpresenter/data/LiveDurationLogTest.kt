package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveDurationLogTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun log() = LiveDurationLog(temp.root.resolve("durations.json"))

    private fun song(number: Int = 1) = ScheduleItem.SongItem(
        id = "row-${number}-${Instant.now().nano}",
        songNumber = number,
        title = "Song $number",
        songbook = "Hymns",
        songId = "Hymns::$number",
    )

    private val start = Instant.parse("2026-09-20T10:00:00Z")

    private fun LiveDurationLog.record(item: ScheduleItem, seconds: Long) {
        wentLive(item, start)
        wentBlank(start.plusSeconds(seconds))
    }

    @Test
    fun `reports the median of what an item has taken`() {
        val log = log()
        val hymn = song()

        listOf(200L, 240L, 300L).forEach { log.record(hymn, it) }

        assertEquals(240, log.median(hymn))
    }

    @Test
    fun `one long reading does not move it much`() {
        val log = log()
        val hymn = song()

        listOf(240L, 250L, 260L, 3000L).forEach { log.record(hymn, it) }

        assertEquals(250, log.median(hymn), "the mean would be over 15 minutes")
    }

    @Test
    fun `anything under half a minute is not a measurement`() {
        val log = log()
        val hymn = song()

        listOf(5L, 29L).forEach { log.record(hymn, it) }

        assertNull(log.median(hymn), "stepping through a service is not the service")
    }

    @Test
    fun `an item left up after the service is ignored too`() {
        val log = log()
        val hymn = song()

        log.record(hymn, 4000L)

        assertNull(log.median(hymn))
    }

    @Test
    fun `a row going live ends the timing of the one before it`() {
        val log = log()
        val first = song(1)
        val second = song(2)

        log.wentLive(first, start)
        log.wentLive(second, start.plusSeconds(300))
        log.wentBlank(start.plusSeconds(700))

        assertEquals(300, log.median(first))
        assertEquals(400, log.median(second))
    }

    @Test
    fun `the same song in another service adds to the same reading`() {
        val log = log()

        log.record(song(7), 200L)
        log.record(song(7), 400L)

        // Different rows, different ids, one song: identity is the songbook and number.
        assertEquals(200, log.median(song(7)))
    }

    @Test
    fun `readings survive a restart`() {
        val hymn = song()
        log().record(hymn, 250L)

        assertEquals(250, log().median(hymn))
    }

    @Test
    fun `only the most recent readings are kept`() {
        val log = log()
        val hymn = song()

        // Twenty readings, the first eight of them long: keeping 12 drops those.
        repeat(8) { log.record(hymn, 600L) }
        repeat(12) { log.record(hymn, 120L) }

        assertEquals(120, log.median(hymn))
    }

    @Test
    fun `items with no stable identity are not learnt about`() {
        val heading = ScheduleItem.LabelItem(
            id = "label", text = "Worship", textColor = "#FFFFFF", backgroundColor = "#000000",
        )
        val log = log()

        log.record(heading, 300L)

        assertNull(log.median(heading))
    }

    @Test
    fun `the median of nothing is nothing`() {
        assertNull(LiveDurationLog.medianOf(emptyList()))
        assertNull(LiveDurationLog.medianOf(null))
        assertEquals(5, LiveDurationLog.medianOf(listOf(5)))
        assertEquals(5, LiveDurationLog.medianOf(listOf(5, 9)), "the lower of the middle two")
    }

    @Test
    fun `blanking with nothing live records nothing`() {
        val log = log()

        log.wentBlank(start)
        log.wentBlank(start.plusSeconds(600))

        assertNull(log.median(song()))
    }

    @Test
    fun `a file that cannot be read starts empty rather than failing`() {
        val file = temp.root.resolve("durations.json").apply { writeText("{ not json") }

        val log = LiveDurationLog(file)
        log.record(song(), 200L)

        assertEquals(200, log.median(song()))
        assertTrue(file.readText().contains("song:Hymns:1"), "the corrupt file is replaced by a good one")
    }

    @Test
    fun `identity is what the item is, never its row`() {
        val key = LiveDurationLog::durationKey
        assertEquals("song:Hymns:1:Hymns::1:song 1", key(song()))
        assertEquals(
            "media:/clips/welcome.mp4",
            key(
                ScheduleItem.MediaItem(
                    id = "a", mediaUrl = "/clips/welcome.mp4", mediaTitle = "Welcome", mediaType = "video",
                ),
            ),
        )
        assertEquals(
            "pictures:/photos/camp",
            key(ScheduleItem.PictureItem(id = "b", folderPath = "/photos/camp", folderName = "camp", imageCount = 20)),
        )
        assertEquals(
            "deck:/decks/sermon.pptx",
            key(
                ScheduleItem.PresentationItem(
                    id = "c", filePath = "/decks/sermon.pptx", fileName = "sermon.pptx",
                    slideCount = 9, fileType = "pptx",
                ),
            ),
        )
        assertEquals("scene:s1", key(ScheduleItem.SceneItem(id = "d", sceneId = "s1", sceneName = "Opener")))
        assertEquals("web:https://example.org", key(ScheduleItem.WebsiteItem(id = "e", url = "https://example.org")))
        assertEquals(
            "lower:lt1",
            key(
                ScheduleItem.LowerThirdItem(
                    id = "f", presetId = "lt1", presetLabel = "Name", pauseAtFrame = false, pauseDurationMs = 0L,
                ),
            ),
        )
    }

    @Test
    fun `three songs sharing a number in one book are three songs`() {
        val key = LiveDurationLog::durationKey
        val a = song(1).copy(title = "Great Is Thy Faithfulness")
        val b = song(1).copy(title = "Silent Night")
        assertTrue(key(a) != key(b), "the number alone is not an identity")
        val spaced = a.copy(id = "other-row", title = "  great is thy faithfulness ")
        assertEquals(key(a), key(spaced), "but case and spacing are")
    }

    @Test
    fun `the same thing sent again keeps its reading running`() {
        val log = log()
        val hymn = song(3)
        log.wentLive(hymn, start)
        // A section clicked, the row re-sent: the same song, still on screen.
        log.wentLive(hymn.copy(id = "another-row"), start.plusSeconds(20))
        log.wentLive(hymn.copy(id = "third-row"), start.plusSeconds(40))
        log.wentBlank(start.plusSeconds(90))

        assertEquals(90, log.median(hymn), "one reading of ninety seconds, not three too short to keep")
    }

    @Test
    fun `it can say whether a given thing is what is on screen`() {
        val log = log()
        val hymn = song(4)
        assertFalse(log.showing(hymn), "nothing is, yet")
        log.wentLive(hymn, start)
        assertTrue(log.showing(hymn.copy(id = "a-row-of-it")))
        assertFalse(log.showing(song(5)))
        // A verse has no duration key, but it is still distinguishable from the hymn before it.
        val verse = ScheduleItem.BibleVerseItem("v", "John", 3, 16, "For God so loved")
        log.wentLive(verse, start.plusSeconds(60))
        assertTrue(log.showing(verse))
        assertFalse(log.showing(hymn))
        log.wentBlank(start.plusSeconds(120))
        assertFalse(log.showing(verse))
    }

    @Test
    fun `a library song is measured as the row that identifies it`() {
        val row = SongItem(number = "7", title = "Amazing Grace", songbook = "Hymns").asDurationRow()

        val library = song(7).copy(title = "Amazing Grace")
        assertEquals(LiveDurationLog.durationKey(library), LiveDurationLog.durationKey(row))
        assertEquals(0, SongItem(number = "n/a", title = "Untitled", songbook = "Hymns").asDurationRow().songNumber)
    }
}
