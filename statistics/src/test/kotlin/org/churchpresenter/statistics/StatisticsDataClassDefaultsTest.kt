package org.churchpresenter.statistics

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Default values for the statistics `data class`es. The defaults are not decorative: they are what
 * a field decodes to when an older statistics log is missing a field a newer build added —
 * [StatisticsFileFormatTest] pins that at the file-format level, and this pins the same contract
 * for the classes themselves.
 */
class StatisticsDataClassDefaultsTest {

    @Test
    fun `a song display entry with only its number given still has a blank title and a zero count`() {
        val entry = SongDisplayEntry(songNumber = 12)

        assertEquals(SongDisplayEntry(12, "", "", 0), entry)
    }

    @Test
    fun `a verse display entry with only its chapter given still has a blank book and a zero count`() {
        val entry = VerseDisplayEntry(chapter = 3)

        assertEquals(VerseDisplayEntry("", "", 3, 0, 0), entry)
    }

    @Test
    fun `a song play event with only its title given still has a zero timestamp`() {
        val event = SongPlayEvent(title = "Amazing Grace")

        assertEquals(SongPlayEvent(0, "Amazing Grace", "", "", 0L), event)
    }

    @Test
    fun `a verse play event with only its book given still has a zero timestamp`() {
        val event = VersePlayEvent(bookName = "Genesis")

        assertEquals(VersePlayEvent("", "Genesis", 0, 0, 0L), event)
    }
}
