package org.churchpresenter.calendar

import org.churchpresenter.calendar.model.RowSamples.song
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** What has fired this session: the feed the Schedule marks its rows from and the window toasts. */
class CueFeedTest {

    @AfterTest
    fun clearTheFeed() = CueFeed.clear()

    @Test
    fun `the newest firing comes first`() {
        CueFeed.post(FiredCue(song("a"), LocalTime.of(9, 0)))
        CueFeed.post(FiredCue(song("b"), LocalTime.of(9, 5)))

        assertEquals(listOf("b", "a"), CueFeed.fired.value.map { it.row.id })
    }

    @Test
    fun `a Sunday's worth of firings does not grow the feed without bound`() {
        repeat(30) { CueFeed.post(FiredCue(song("r$it"), LocalTime.of(9, 0).plusMinutes(it.toLong()))) }

        assertEquals(12, CueFeed.fired.value.size)
        assertEquals("r29", CueFeed.fired.value.first().row.id, "the oldest are the ones dropped")
    }

    @Test
    fun `clearing forgets every firing`() {
        CueFeed.post(FiredCue(song(), LocalTime.of(9, 0)))

        CueFeed.clear()

        assertTrue(CueFeed.fired.value.isEmpty())
    }

    @Test
    fun `the same row firing again is told apart from the firing before it`() {
        val first = FiredCue(song(), LocalTime.of(9, 0))
        val again = FiredCue(song(), LocalTime.of(10, 0))

        assertNotEquals(first.key, again.key)
        assertEquals(first.key, FiredCue(song(), LocalTime.of(9, 0)).key)
    }

    @Test
    fun `a firing is not a skip unless it says so`() {
        assertFalse(FiredCue(song(), LocalTime.of(9, 0)).skipped)
        assertTrue(FiredCue(song(), LocalTime.of(9, 0), skipped = true).skipped)
        assertEquals(LocalTime.of(9, 0), FiredCue(song(), LocalTime.of(9, 0)).at)
    }
}
