@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a schedule row's parts actually land, at each density.
 *
 * Both invariants here are about geometry rather than content, and both were broken in the redesign
 * in ways no existing test could see: every suite asserted on what a row *says*, and a row that says
 * the right thing in the wrong place passes all of them.
 *
 *  * **The title sits on the row's centreline.** The content column is stretched to whichever
 *    sibling is taller, and at Compact that is the action-button row — a single ~20dp title line
 *    against ~30dp of buttons — so a top-aligned title sat visibly high in its own card. Normal and
 *    Detailed masked it: their detail lines make the content column the taller sibling, so there is
 *    no slack for it to sit at the top of.
 *  * **The hover scrim covers the card.** The action strip's gradient is its own background, so it
 *    is exactly as tall as the strip. Left at the buttons' ~30dp inside a 54dp Detailed card, it was
 *    a band floating in the middle with the card's own background showing above and below it.
 *
 * The type chip is the reference for the centreline: it is laid out `CenterVertically` in the row,
 * so its centre *is* the row's centre, and comparing against it needs no font metrics — which is
 * what makes this safe across the three target platforms. The tolerance is 2dp-ish in pixels rather
 * than exact, because a text line's own centre and a 26dp box's centre round differently.
 */
class ScheduleTabRowLayoutTest {

    private companion object {
        /** Compact / Normal / Detailed, as `scheduleDensityFor` resolves them. */
        const val COMPACT = 70
        const val NORMAL = 100
        const val DETAILED = 150
    }

    /** A song row's own type chip, which is `CenterVertically` and so marks the row's centreline. */
    private fun ComposeUiTest.chipCentreY(): Float =
        onNodeWithText("♪").fetchSemanticsNode().boundsInRoot.center.y

    private fun ComposeUiTest.titleCentreY(): Float =
        onNodeWithText("Amazing Grace", substring = true).fetchSemanticsNode().boundsInRoot.center.y

    private fun ComposeUiTest.assertTitleOnCentreline(density: String) {
        val chip = chipCentreY()
        val title = titleCentreY()

        assertTrue(
            abs(chip - title) <= 3f,
            "$density: the title must sit on the row's centreline, not float above it " +
                "(chip centre $chip, title centre $title)",
        )
    }

    @Test
    fun `the title is vertically centred at Compact`() =
        scheduleTab(itemZoomPercent = COMPACT,
            seed = { addSong(songNumber = 42, title = "Amazing Grace", songbook = "Hymnal") }) { _,
            _ ->
            // The regression: with the action buttons taller than the single title line, the
            // stretched content column left the title at the top of its own card.
            assertTitleOnCentreline("Compact")
        }

    @Test
    fun `the type chip stays on the card's centreline at every density`() {
        listOf(COMPACT to "Compact", NORMAL to "Normal", DETAILED to "Detailed").forEach { (percent, name) ->
            scheduleTab(itemZoomPercent = percent,
                seed = { addSong(songNumber = 42, title = "Amazing Grace", songbook = "Hymnal") }) { _,
                _ ->
                // The chip is the row's own alignment made visible: it is laid out
                // `CenterVertically`, so if it drifts off the card's centre the row's height is
                // coming from somewhere other than its content -- which is exactly what the
                // intrinsic-height fix changed. Cheap insurance that the fix did not trade the
                // Compact bug for a Detailed one.
                val card = onNodeWithTag(SCHEDULE_ROW_CARD_TAG).fetchSemanticsNode().boundsInRoot
                val chip = onNodeWithText("\u266a").fetchSemanticsNode().boundsInRoot

                assertTrue(
                    abs(chip.center.y - card.center.y) <= 3f,
                    "$name: the type chip must stay on the card's centreline " +
                        "(card centre ${card.center.y}, chip centre ${chip.center.y})",
                )
            }
        }
    }

    @Test
    fun `the hover action strip covers the whole card at every density`() {
        listOf(COMPACT to "Compact", NORMAL to "Normal", DETAILED to "Detailed").forEach { (percent, name) ->
            scheduleTab(itemZoomPercent = percent,
                seed = { addSong(songNumber = 42, title = "Amazing Grace", songbook = "Hymnal") }) { _,
                _ ->
                val card = onNodeWithTag(SCHEDULE_ROW_CARD_TAG).fetchSemanticsNode().boundsInRoot
                val actions = onNodeWithTag(SCHEDULE_ROW_ACTIONS_TAG).fetchSemanticsNode().boundsInRoot

                // The strip carries the scrim as its background, so its height IS the scrim's. It
                // sits inside the card's vertical padding, which is 4/7/9dp a side by density --
                // hence "most of the card" rather than all of it. At the buttons' own ~30dp inside
                // a 54dp+ Detailed card this was nowhere close.
                assertTrue(
                    actions.height >= card.height * 0.6f,
                    "$name: the scrim must cover the card behind it, not float as a band in the " +
                        "middle (card ${card.height}px, strip ${actions.height}px)",
                )
                assertTrue(
                    abs(actions.center.y - card.center.y) <= 3f,
                    "$name: and it must stay centred on the card (card centre ${card.center.y}, " +
                        "strip centre ${actions.center.y})",
                )
            }
        }
    }

    @Test
    fun `every action button sits on the card's centreline`() {
        listOf(COMPACT to "Compact", NORMAL to "Normal", DETAILED to "Detailed").forEach { (percent, name) ->
            scheduleTab(itemZoomPercent = percent,
                seed = { addSong(songNumber = 42, title = "Amazing Grace", songbook = "Hymnal") }) { _,
                _ ->
                // The play button used to be 30dp against the others' 27dp, and Row's
                // CenterVertically did not rescue the mix: they came out sharing a bottom edge, so
                // the four small ones sat ~1.5dp low. Visible as a wobble along the strip at
                // Compact, where the card is barely taller than the buttons themselves.
                val card = onNodeWithTag(SCHEDULE_ROW_CARD_TAG).fetchSemanticsNode().boundsInRoot
                listOf(
                    ScheduleLabel.MOVE_UP, ScheduleLabel.MOVE_DOWN, ScheduleLabel.NOTE,
                    ScheduleLabel.REMOVE, ScheduleLabel.GO_LIVE,
                ).forEach { label ->
                    val button = onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
                    assertTrue(
                        abs(button.center.y - card.center.y) <= 1f,
                        "$name: $label must sit on the card's centreline " +
                            "(card ${card.center.y}, button ${button.center.y})",
                    )
                }
            }
        }
    }

    // ── A label is a heading, not a card ────────────────────────────────────────

    @Test
    fun `a label row is only as tall as its own text, at every density`() {
        listOf(COMPACT to "Compact", NORMAL to "Normal", DETAILED to "Detailed").forEach { (percent, name) ->
            scheduleTab(itemZoomPercent = percent, seed = {
                addSong(songNumber = 1, title = "First Song", songbook = "Hymnal")
                addLabel("WELCOME", "#FFFFFF", "#203040")
            }) { _, _ ->
                // It used to take the density's own row height and its full padding, so at
                // Detailed one word sat in a 70dp band as tall as the items it heads.
                val cards = onAllNodesWithTag(SCHEDULE_ROW_CARD_TAG).fetchSemanticsNodes().map { it.boundsInRoot }
                val song = cards[0]
                val label = cards[1]
                val text = onNodeWithText("WELCOME").fetchSemanticsNode().boundsInRoot

                assertTrue(
                    label.height < song.height,
                    "$name: a heading must be slimmer than the cards under it " +
                        "(label ${label.height}px, song ${song.height}px)",
                )
                assertTrue(
                    label.height - text.height <= 16f,
                    "$name: and no taller than its own text needs (label ${label.height}px, " +
                        "text ${text.height}px)",
                )
            }
        }
    }

    @Test
    fun `a label's own padding is even above and below its text`() =
        scheduleTab(itemZoomPercent = NORMAL, seed = { addLabel("WELCOME", "#FFFFFF", "#203040") }) { _, _ ->
            val card = onNodeWithTag(SCHEDULE_ROW_CARD_TAG).fetchSemanticsNode().boundsInRoot
            val text = onNodeWithText("WELCOME").fetchSemanticsNode().boundsInRoot

            val above = text.top - card.top
            val below = card.bottom - text.bottom

            assertTrue(abs(above - below) <= 1f,
                "the band must sit evenly around its text ($above above, $below below)")
        }

    // ── One note per item ───────────────────────────────────────────────────────

    @Test
    fun `a saved note is drawn once, not once per place that can draw it`() =
        scheduleTab(seed = {
            addSong(songNumber = 1, title = "First Song", songbook = "Hymnal")
            setNote(scheduleItems[0].id, "bring the second verse down")
        }) { _, _ ->
            // Two previews were drawn on the same `note.isNotEmpty() && !noteExpanded` condition --
            // an italic line under the title and the band below the card -- so every item carrying
            // a note showed it twice.
            assertEquals(
                1,
                onAllNodesWithText("bring the second verse down", substring = true).fetchSemanticsNodes().size,
                "a note belongs in one place on its row",
            )
        }

    @Test
    fun `a label row is spaced like every other row`() =
        scheduleTab(seed = {
            addSong(songNumber = 1, title = "First Song", songbook = "Hymnal")
            addLabel("WELCOME", "#FFFFFF", "#203040")
            addSong(songNumber = 2, title = "Second Song", songbook = "Hymnal")
        }) { _, _ ->
            val cards = onAllNodesWithTag(SCHEDULE_ROW_CARD_TAG).fetchSemanticsNodes().map { it.boundsInRoot }
            val above = cards[1].top - cards[0].bottom
            val below = cards[2].top - cards[1].bottom

            assertTrue(abs(above - below) <= 1f, "even above and below ($above / $below)")
            assertTrue(above <= 4f, "and no roomier than the gap between two ordinary rows ($above)")
        }
}
