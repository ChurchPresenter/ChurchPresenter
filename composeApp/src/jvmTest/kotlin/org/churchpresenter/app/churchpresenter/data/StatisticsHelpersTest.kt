package org.churchpresenter.app.churchpresenter.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two pure helpers behind the statistics screen and its CCLI export: how the activity chart
 * picks its bucket size from the selected range, and how a CSV field is quoted. A wrong boundary
 * makes a two-year report render as 100+ weekly bars; an unescaped quote or comma corrupts the CSV
 * a license report is built from.
 */
class StatisticsHelpersTest {

    private val day = 86_400_000L

    @Test
    fun `short ranges bucket by week`() {
        assertEquals(ActivityGranularity.WEEKLY, activityGranularityFor(day))
        assertEquals(ActivityGranularity.WEEKLY, activityGranularityFor(90 * day), "90 days is the weekly ceiling")
    }

    @Test
    fun `medium ranges bucket by month`() {
        assertEquals(ActivityGranularity.MONTHLY,
            activityGranularityFor(91 * day),
            "just past 90 days flips to monthly")
        assertEquals(ActivityGranularity.MONTHLY, activityGranularityFor(730 * day), "two years is the monthly ceiling")
    }

    @Test
    fun `long ranges bucket by year`() {
        assertEquals(ActivityGranularity.YEARLY, activityGranularityFor(731 * day))
        assertEquals(ActivityGranularity.YEARLY, activityGranularityFor(3650 * day))
    }

    @Test
    fun `csvQuote wraps a plain field in quotes`() {
        assertEquals("\"Amazing Grace\"", csvQuote("Amazing Grace"))
        assertEquals("\"\"", csvQuote(""))
    }

    @Test
    fun `csvQuote doubles embedded quotes`() {
        assertEquals("\"She said \"\"hi\"\"\"", csvQuote("She said \"hi\""))
    }

    @Test
    fun `csvQuote leaves commas and newlines to be protected by the surrounding quotes`() {
        assertEquals("\"Bach, J.S.\"", csvQuote("Bach, J.S."))
        assertEquals("\"line1\nline2\"", csvQuote("line1\nline2"))
    }
}
