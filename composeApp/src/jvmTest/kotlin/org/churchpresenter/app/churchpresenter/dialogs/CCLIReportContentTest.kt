@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three report bodies behind the CCLI dialog's tabs: songs, Bible and activity.
 *
 * This is the report a church files against its copyright licence, so what it counts matters more
 * than how it looks — the totals in each subtitle, the per-book aggregation on the Bible tab, the
 * busiest-period pick on the activity tab, and the rank the table assigns each row. Each of those is
 * derived, not passed through, so each is asserted against a fixture whose answer is known.
 *
 * See `CCLIReportDialogTestSupport` for why these drive the content composables rather than
 * `CCLIReportDialog` itself, and for what is consequently left uncovered.
 */
class CCLIReportContentTest {

    // ── Songs tab ───────────────────────────────────────────────────────────────

    @Test
    fun `with no songs the tab says there is no data rather than an empty table`() =
        reportContent({ SongsReportContent(emptyList()) {} }) {
            onNodeWithText(CcliLabel.NO_DATA).assertIsDisplayed()
            assertFalse(
                renderedText().contains("Title"),
                "the table header must not be drawn over an empty report",
            )
        }

    @Test
    fun `the songs subtitle counts unique titles and total plays separately`() =
        reportContent({
            SongsReportContent(listOf(song("Amazing Grace", count = 3), song("Be Thou My Vision", count = 4))) {}
        }) {
            onNodeWithText(CcliLabel.SONGS_CHART).assertIsDisplayed()
            // Two songs, but seven presentations between them.
            onNodeWithText(CcliLabel.songsSummary(unique = 2, plays = 7)).assertIsDisplayed()
        }

    @Test
    fun `each song row carries its rank, title, author, songbook, CCLI number and both dates`() {
        val first = JAN_2026
        val last = JAN_2026 + 5 * DAY_MS
        reportContent({
            SongsReportContent(
                listOf(
                    song(
                        "Amazing Grace",
                        count = 9,
                        author = "John Newton",
                        songbook = "Hymnal",
                        ccli = "22025",
                        firstUsed = first,
                        lastUsed = last,
                    ),
                ),
            ) {}
        }) {
            val table = tableText()
            listOf("1", "Amazing Grace", "John Newton", "Hymnal", "22025", "9", formatDate(first), formatDate(last))
                .forEach { assertTrue(table.contains(it), "the song row must show \"$it\"; showed $table") }
        }
    }

    @Test
    fun `rows are ranked from one in the order they were given`() =
        reportContent({
            SongsReportContent(
                listOf(song("Amazing Grace", count = 9), song("Be Thou My Vision", count = 4), song("Hoy", count = 1)),
            ) {}
        }) {
            val table = tableText()
            val ranks = listOf("1", "2", "3").map { table.indexOf(it) }
            assertTrue(ranks.none { it < 0 }, "every row must be numbered; showed $table")
            assertEquals(ranks.sorted(), ranks, "ranks must run 1, 2, 3 down the table")
        }

    @Test
    fun `a song with no author, songbook or CCLI number shows a dash in each`() =
        reportContent({
            SongsReportContent(listOf(song("Untitled Chorus", author = "", songbook = "", ccli = ""))) {}
        }) {
            assertEquals(
                3,
                tableText().count { it == CcliLabel.BLANK },
                "each of the three blank fields must render its own dash",
            )
        }

    @Test
    fun `the songs chart is capped at twelve entries however many were presented`() {
        val songs = (1..15).map { song("Song $it", count = 100 - it) }
        reportContent({ SongsReportContent(songs) {} }) {
            val charted = chartLabels()
            assertTrue(charted.contains("Song 12"), "the twelfth song belongs on the chart; charted $charted")
            assertFalse(charted.contains("Song 13"), "the thirteenth must be left off; charted $charted")
        }
    }

    // ── Bible tab ───────────────────────────────────────────────────────────────

    @Test
    fun `with no verses the tab says there is no data`() =
        reportContent({ BibleReportContent(emptyList()) {} }) {
            onNodeWithText(CcliLabel.NO_DATA).assertIsDisplayed()
        }

    @Test
    fun `the Bible subtitle counts unique verses and total plays separately`() =
        reportContent({
            BibleReportContent(listOf(verse("John", count = 2), verse("John", number = 17, count = 3))) {}
        }) {
            onNodeWithText(CcliLabel.BIBLE_CHART).assertIsDisplayed()
            onNodeWithText(CcliLabel.bibleSummary(unique = 2, plays = 5)).assertIsDisplayed()
        }

    @Test
    fun `the chart totals verses by book and ranks the books by that total`() =
        reportContent({
            BibleReportContent(
                listOf(
                    verse("John", number = 16, count = 2),
                    verse("Psalms", number = 1, count = 4),
                    verse("John", number = 17, count = 5),   // John now totals 7
                    verse("Acts", number = 2, count = 1),
                ),
            ) {}
        }) {
            assertEquals(
                listOf("John" to "7", "Psalms" to "4", "Acts" to "1"),
                chartRows(),
                "each book totals its own verses — John's 2 and 5 make 7 — and the books rank by that total, " +
                    "not by how many distinct verses each contributed",
            )
        }

    @Test
    fun `a verse row shows its reference as book chapter and verse`() =
        reportContent({ BibleReportContent(listOf(verse("John", chapter = 3, number = 16, count = 4))) {} }) {
            assertTrue(
                tableText().contains("John 3:16"),
                "the reference must read \"John 3:16\"; showed ${tableText()}",
            )
        }

    @Test
    fun `a verse recorded against no Bible shows a dash for it`() =
        reportContent({ BibleReportContent(listOf(verse("John", bible = ""))) {} }) {
            assertTrue(tableText().contains(CcliLabel.BLANK), "the missing Bible name must render as a dash")
        }

    // ── Activity tab ────────────────────────────────────────────────────────────

    @Test
    fun `with no activity points the tab says there is no data`() =
        reportContent({ ActivityContent(emptyList()) }) {
            onNodeWithText(CcliLabel.NO_DATA).assertIsDisplayed()
        }

    @Test
    fun `periods that exist but recorded nothing count as no data`() =
        reportContent({ ActivityContent(listOf(activity("Jan", 0, 0), activity("Feb", 0, 0))) }) {
            onNodeWithText(CcliLabel.NO_DATA)
                .assertIsDisplayed()
            assertFalse(
                renderedText().contains(CcliLabel.STAT_SONGS),
                "empty periods must not be dressed up as a report with zeroed stat cards",
            )
        }

    @Test
    fun `the stat cards total songs and verses across every period`() =
        reportContent({
            ActivityContent(listOf(activity("Jan", 3, 1), activity("Feb", 4, 6), activity("Mar", 0, 2)))
        }) {
            val shown = renderedText()
            assertTrue(shown.contains(CcliLabel.STAT_SONGS) && shown.contains("7"), "songs total 3 + 4 + 0 = 7")
            assertTrue(shown.contains(CcliLabel.STAT_VERSES) && shown.contains("9"), "verses total 1 + 6 + 2 = 9")
        }

    @Test
    fun `the busiest period is the one with the most songs and verses combined`() =
        reportContent({
            // February has fewer songs than January but the larger combined total.
            ActivityContent(listOf(activity("Jan", 6, 1), activity("Feb", 4, 6), activity("Mar", 1, 1)))
        }) {
            assertTrue(
                renderedText().contains("Feb (10)"),
                "February's 4 + 6 beats January's 6 + 1; showed ${renderedText()}",
            )
        }

    @Test
    fun `the chart is labelled with the span it covers and a legend for both bars`() =
        reportContent({
            ActivityContent(listOf(activity("Jan", 1, 1), activity("Feb", 2, 2), activity("Mar", 3, 3)))
        }) {
            onNodeWithText(CcliLabel.ACTIVITY_TITLE).assertIsDisplayed()
            assertTrue(
                renderedText().contains("Jan – Mar"),
                "the span runs from the first period to the last; showed ${renderedText()}",
            )
            onNodeWithText(CcliLabel.LEGEND_SONGS).assertIsDisplayed()
            onNodeWithText(CcliLabel.LEGEND_BIBLE).assertIsDisplayed()
        }

    @Test
    fun `beyond ten periods only every few-th x-axis label is drawn to avoid crowding`() =
        reportContent({
            ActivityContent((1..20).map { activity("P$it", songs = it, verses = 0) })
        }) {
            val shown = renderedText()
            assertTrue(shown.contains("P1"), "the first period's label always shows; showed $shown")
            assertTrue(shown.contains("P20"), "the last period's label always shows, whatever the step; showed $shown")
            assertFalse(shown.contains("P2"), "with 20 periods, labels in between the step are dropped; showed $shown")
        }
}
