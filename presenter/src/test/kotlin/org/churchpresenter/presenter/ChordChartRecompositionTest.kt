@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.presenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.churchpresenter.songchords.ChordSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordChartRecompositionTest {

    private fun ComposeUiTest.shows(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the chart follows new lines, a new key and a new look`() = runComposeUiTest {
        var lines by mutableStateOf(listOf("[G]one two"))
        var steps by mutableStateOf(0)
        var colour by mutableStateOf(Color.White)
        var size by mutableStateOf(20.sp)
        setContent {
            MaterialTheme {
                ChordChart(
                    lines = lines,
                    textColor = colour,
                    chordColor = colour,
                    fontSize = size,
                    steps = steps,
                    textStyle = TextStyle(),
                )
            }
        }
        waitForIdle()
        assertTrue(shows("one two"))

        lines = listOf("[Intro]", "[Cm] [Bb]", "[Bridge]", "[F]three [Bb]four[C][F]")
        waitForIdle()
        assertTrue(shows("Intro"))
        assertTrue(shows("three"))

        steps = -2
        colour = Color.Yellow
        size = 30.sp
        waitForIdle()
        assertTrue(shows("three"))

        lines = listOf("[Outro]")
        waitForIdle()
        assertTrue(shows("Outro"))
    }

    @Test
    fun `a chord line follows its segments and whether chords show`() = runComposeUiTest {
        var segments by mutableStateOf(listOf(ChordSegment("G", "de"), ChordSegment("C", "livered")))
        var showChords by mutableStateOf(true)
        setContent { MaterialTheme { ChordLine(segments = segments, showChords = showChords) } }
        waitForIdle()
        assertTrue(shows("livered"))

        showChords = false
        waitForIdle()
        assertTrue(!shows("G"))

        segments = listOf(ChordSegment("", "words "), ChordSegment("D", ""), ChordSegment("", " "))
        showChords = true
        waitForIdle()
        assertTrue(shows("words"))
    }

    @Test
    fun `trailing chords with nothing written collapse to the words alone`() =
        assertEquals(
            listOf(ChordSegment("", "words")),
            collapseTrailingChords(listOf(ChordSegment("", "words"), ChordSegment("", ""), ChordSegment("", " "))),
        )

    @Test
    fun `a line of no words is left as it is`() {
        val chordsOnly = listOf(ChordSegment("G", ""), ChordSegment("C", " "))
        assertEquals(chordsOnly, collapseTrailingChords(chordsOnly))
    }
}
