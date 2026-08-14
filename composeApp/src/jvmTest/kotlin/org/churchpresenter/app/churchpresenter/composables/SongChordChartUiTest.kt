@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongChordChartUiTest {

    private fun chart(
        lines: List<String>,
        steps: Int = 0,
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                ChordChart(
                    lines = lines,
                    textColor = Color.White,
                    chordColor = Color.Cyan,
                    fontSize = 20.sp,
                    steps = steps,
                )
            }
        }
        block()
    }

    // Substring: a run's text carries the spacing it was written with — a section's name leading a
    // line is drawn as "Intro  ", not "Intro".
    private fun ComposeUiTest.shows(text: String): Boolean =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    // ── The chart ───────────────────────────────────────────────────────────────

    @Test
    fun `a chord is drawn over the words it lands on`() = chart(listOf("[G]one two [C]three")) {
        assertTrue(shows("G"))
        assertTrue(shows("C"))
        assertTrue(shows("one two "))
        assertTrue(shows("three"))
    }

    @Test
    fun `a line without chords is drawn as plain words`() = chart(listOf("just some words")) {
        assertTrue(shows("just some words"))
    }

    @Test
    fun `a chord inside a word does not break the word apart`() = chart(listOf("de[C]livered")) {
        // The word stays two runs, but both are present and the chord sits with them.
        assertTrue(shows("de"))
        assertTrue(shows("livered"))
        assertTrue(shows("C"))
    }

    @Test
    fun `chords past the last word are gathered into one run`() = chart(listOf("some words[Ab][Gsus][G]")) {
        assertTrue(shows("Ab Gsus G"), "the trailing chords are drawn as a single run")
    }

    @Test
    fun `a row of chords with no words is written along the line`() = chart(listOf("[Cm] [Bb] [Ab] [G]")) {
        listOf("Cm", "Bb", "Ab", "G").forEach { assertTrue(shows(it), "$it is on the line") }
    }

    @Test
    fun `a header names the row of chords it introduces`() =
        chart(listOf("[Intro]", "[Cm] [Bb]")) {
            assertTrue(shows("Intro"), "the section's name leads the row")
            assertTrue(shows("Cm"))
            assertTrue(shows("Bb"))
        }

    @Test
    fun `a header with words under it labels that line instead`() =
        chart(listOf("[Intro]", "[G]one two")) {
            assertTrue(shows("Intro"))
            assertTrue(shows("one two"))
        }

    @Test
    fun `a header with nothing after it still draws`() = chart(listOf("[Intro]")) {
        assertTrue(shows("Intro"))
    }

    @Test
    fun `two headers in a row each draw`() = chart(listOf("[Intro]", "[Tag]")) {
        assertTrue(shows("Intro"))
        assertTrue(shows("Tag"))
    }

    @Test
    fun `the chart is transposed on the way to the screen`() = chart(listOf("[G]one"), steps = 2) {
        assertTrue(shows("A"), "G moved up two")
        assertTrue(!shows("G"), "and the old chord is gone")
    }

    @Test
    fun `a chart in a flat key is spelled with flats`() = chart(listOf("[G]one"), steps = 1) {
        assertTrue(shows("Ab"), "the key it lands in is written with flats")
    }

    // ── The editor's preview pane ───────────────────────────────────────────────

    private class PreviewReports {
        var inserted: String? = null
        var steps = 0
    }

    private fun preview(
        text: String,
        showChords: Boolean = true,
        steps: Int = 0,
        block: ComposeUiTest.(PreviewReports) -> Unit,
    ) = runComposeUiTest {
        val reports = PreviewReports().also { it.steps = steps }
        setContent {
            MaterialTheme {
                SongChordPreview(
                    text = text,
                    showChords = showChords,
                    steps = steps,
                    onTransposeUp = { reports.steps++ },
                    onTransposeDown = { reports.steps-- },
                    onTransposeReset = { reports.steps = 0 },
                    onInsertChord = { reports.inserted = it },
                )
            }
        }
        block(reports)
    }

    @Test
    fun `the pane names itself and the key the song is in`() =
        preview("[Verse 1]\n[G]one two") { _ ->
            assertTrue(shows("PREVIEW"))
            assertTrue(shows("KEY"))
            assertTrue(shows("G"))
        }

    @Test
    fun `each section is labelled`() = preview("[Verse 1]\none\n{Chorus}\ntwo") { _ ->
        assertTrue(shows("VERSE 1"))
        assertTrue(shows("CHORUS"))
    }

    @Test
    fun `an unlabelled block shows no chip`() = preview("just a line") { _ ->
        assertTrue(shows("just a line"))
        assertTrue(!shows("VERSE"))
    }

    @Test
    fun `the palette offers the seven chords of the key`() = preview("[G]one") { _ ->
        listOf("Am", "Bm", "C", "D", "Em", "F#dim").forEach {
            assertTrue(shows(it), "$it belongs to the key of G")
        }
    }

    @Test
    fun `the palette says how many of its chords the song uses`() = preview("[G]one [C]two") { _ ->
        assertTrue(shows("2 used"))
    }

    @Test
    fun `tapping a palette chord asks for it to be inserted`() = preview("[G]one") { reports ->
        onNodeWithText("Em").performClick()
        waitForIdle()
        assertEquals("Em", reports.inserted)
    }

    @Test
    fun `the two steps move the song`() = preview("[G]one") { reports ->
        onNodeWithText("+").performClick()
        waitForIdle()
        assertEquals(1, reports.steps)

        onNodeWithText("−").performClick()
        waitForIdle()
        assertEquals(0, reports.steps)
    }

    @Test
    fun `a song left at its own key offers nothing to reset`() = preview("[G]one", steps = 0) { _ ->
        assertTrue(!shows("+0 — reset"))
    }

    @Test
    fun `a transposed song offers to go back`() = preview("[G]one", steps = 2) { reports ->
        onNodeWithText("+2 — reset").performClick()
        waitForIdle()
        assertEquals(0, reports.steps)
    }

    @Test
    fun `transposing down is shown with its sign`() = preview("[G]one", steps = -2) { _ ->
        assertTrue(shows("-2 — reset"))
    }

    @Test
    fun `with chords off the words are shown alone`() = preview("[G]one two", showChords = false) { _ ->
        assertTrue(shows("one two"))
        assertTrue(!shows("KEY"), "there is no key to report when chords are not shown")
    }

    @Test
    fun `a song naming no chords offers no palette`() = preview("[Verse 1]\njust words") { _ ->
        assertTrue(shows("just words"))
        assertTrue(!shows("0 used") || shows("CHORDS IN C"))
    }
}
