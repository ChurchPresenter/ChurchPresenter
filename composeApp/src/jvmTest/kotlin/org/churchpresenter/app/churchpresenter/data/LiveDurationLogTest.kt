package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
