package org.churchpresenter.statistics

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatisticsActivityChartTest {

    private lateinit var home: File
    private var realHome: String? = null
    private lateinit var manager: StatisticsManager

    private val dayMs = 86_400_000L

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-stats-activity").toFile()
        System.setProperty("user.home", home.absolutePath)
        manager = StatisticsManager()
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun recordOneOfEach() {
        manager.recordSongDisplay(
            songId = "song-1", songNumber = 1, title = "Amazing Grace",
            songbook = "Hymnal", author = "John Newton",
        )
        manager.recordVerseDisplay("KJV", "John", 3, 16)
    }

    private fun activityOver(days: Long): List<ActivityPoint> {
        val now = System.currentTimeMillis()
        return manager.getActivityByPeriod(now - days * dayMs, now + dayMs)
    }

    @Test
    fun `a short range is bucketed by week`() {
        assertEquals(ActivityGranularity.WEEKLY, activityGranularityFor(30 * dayMs))
    }

    @Test
    fun `a range of several months is bucketed by month`() {
        assertEquals(ActivityGranularity.MONTHLY, activityGranularityFor(200 * dayMs))
    }

    @Test
    fun `a range of several years is bucketed by year`() {
        assertEquals(ActivityGranularity.YEARLY, activityGranularityFor(2000 * dayMs))
    }

    @Test
    fun `a weekly chart counts what was shown`() {
        recordOneOfEach()

        val points = activityOver(30)

        assertTrue(points.isNotEmpty())
        assertEquals(1, points.sumOf { it.songCount })
        assertEquals(1, points.sumOf { it.verseCount })
    }

    @Test
    fun `a monthly chart counts the same events`() {
        recordOneOfEach()

        val points = activityOver(200)

        assertTrue(points.isNotEmpty())
        assertEquals(1, points.sumOf { it.songCount })
        assertEquals(1, points.sumOf { it.verseCount })
    }

    @Test
    fun `a yearly chart counts the same events`() {
        recordOneOfEach()

        val points = activityOver(2000)

        assertTrue(points.isNotEmpty())
        assertEquals(1, points.sumOf { it.songCount })
        assertEquals(1, points.sumOf { it.verseCount })
    }

    @Test
    fun `every bucket is labelled so the chart can be read`() {
        recordOneOfEach()

        assertTrue(activityOver(30).all { it.label.isNotBlank() })
        assertTrue(activityOver(200).all { it.label.isNotBlank() })
        assertTrue(activityOver(2000).all { it.label.isNotBlank() })
    }

    @Test
    fun `a range with nothing in it charts zeroes rather than nothing`() {
        val points = activityOver(30)

        assertTrue(points.isNotEmpty(), "an empty period still has weeks to draw")
        assertEquals(0, points.sumOf { it.songCount })
        assertEquals(0, points.sumOf { it.verseCount })
    }

    @Test
    fun `events outside the range are not counted`() {
        recordOneOfEach()
        val longAgo = System.currentTimeMillis() - 900 * dayMs

        val points = manager.getActivityByPeriod(longAgo, longAgo + 30 * dayMs)

        assertEquals(0, points.sumOf { it.songCount })
        assertEquals(0, points.sumOf { it.verseCount })
    }

    @Test
    fun `several songs in the same period are added up`() {
        recordOneOfEach()
        manager.recordSongDisplay(
            songId = "song-2", songNumber = 2, title = "Be Thou My Vision",
            songbook = "Hymnal", author = "Dallan Forgaill",
        )

        assertEquals(2, activityOver(30).sumOf { it.songCount })
    }

    @Test
    fun `the weekly chart is capped so a long range cannot draw endlessly`() {
        val points = activityOver(90)

        assertTrue(points.size <= 52, "a chart with hundreds of columns is unreadable: ${points.size}")
    }
}
