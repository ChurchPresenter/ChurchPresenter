package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.churchpresenter.songchords.ChordSegment
import org.churchpresenter.songchords.ChordTransposer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

private const val CHORD_SPACING_RATIO = 0.42f

/**
 * A chord chart drawn to a caller's own type and colour — the stage monitor's, whose zone styling
 * has nothing to do with the app's theme.
 *
 * Takes lines as written, chord markers still in. [chordColor] separates the chords from the words
 * so a player can find them at a glance from across a platform.
 */
@Composable
fun ChordChart(
    lines: List<String>,
    textColor: Color,
    chordColor: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val flats = remember(lines, steps) {
        ChordTransposer.prefersFlats(
            (ChordTransposer.pitchOf(ChordTransposer.detectKey(lines.joinToString("\n"))) ?: 0) + steps
        )
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            // A header among the chart's lines names chords that came from somewhere else — an
            // intro folded onto the verse it leads into. It labels the row it introduces, so it is
            // drawn on that row rather than above it, and the verse below keeps its own space.
            val header = ChordTransposer.isSectionHeader(line)
            val name = if (header) line.trim().let { it.substring(1, it.length - 1) }.trim() else null
            val chartLine = if (header) {
                lines.getOrNull(index + 1)?.takeIf { !ChordTransposer.isSectionHeader(it) }
            } else {
                line
            }
            val segments = chartLine
                ?.let { ChordTransposer.parseLine(it, steps, flats, showChords = true) }
                ?: emptyList()

            // A row with no words — an intro, a turnaround — has nothing for its chords to sit
            // over, so stacking them above empty space just spends two lines saying one thing.
            // Those chords run along the line instead, after the section's name where there is one.
            if (segments.all { it.text.isBlank() }) {
                InlineChordRow(
                    name = name,
                    chords = segments.map { it.chord }.filter { it.isNotBlank() },
                    textColor = textColor,
                    chordColor = chordColor,
                    fontSize = fontSize,
                    textStyle = textStyle,
                )
            } else {
                ChordLine(
                    segments = if (name == null) segments else listOf(ChordSegment("", "$name  ")) + segments,
                    showChords = true,
                    textColor = textColor,
                    chordColor = chordColor,
                    fontSize = fontSize,
                    textStyle = textStyle,
                )
            }
            index += if (header && chartLine != null) 2 else 1
        }
    }
}

/**
 * Gathers the chords written past the last word into a single run, so they can be drawn as one
 * piece starting where the words end.
 *
 * Left as separate segments each of them takes a column of its own, and every column is at least as
 * wide as the chord in it — which walks the run to the right and stretches the line well past the
 * text. As one run they start at the end of the last word, which is where they were written.
 */
internal fun collapseTrailingChords(segments: List<ChordSegment>): List<ChordSegment> {
    val lastWord = segments.indexOfLast { it.text.isNotBlank() }
    if (lastWord == -1 || lastWord == segments.lastIndex) return segments
    val trailing = segments.drop(lastWord + 1).map { it.chord }.filter { it.isNotBlank() }
    val kept = segments.take(lastWord + 1)
    return if (trailing.isEmpty()) kept else kept + ChordSegment(trailing.joinToString(" "), "")
}

/**
 * A row of chords with no words beneath them, written along the line rather than above it.
 *
 * [name] is the section it came from where there is one, set as ordinary text so it reads as the
 * start of the line; the chords keep the chart's own chord colour and monospaced face, so they
 * still scan as chords next to it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineChordRow(
    name: String?,
    chords: List<String>,
    textColor: Color,
    chordColor: Color,
    fontSize: TextUnit,
    textStyle: TextStyle,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(fontSize.value.times(CHORD_SPACING_RATIO).dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (!name.isNullOrBlank()) {
            Text(
                text = name,
                fontSize = fontSize,
                color = textColor,
                softWrap = false,
                style = textStyle.copy(lineHeight = fontSize * 1.5f),
            )
        }
        chords.forEach { chord ->
            Text(
                text = chord,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize * 0.82f,
                fontWeight = FontWeight.Bold,
                color = chordColor,
                softWrap = false,
                style = textStyle.copy(lineHeight = fontSize * 1.5f),
                modifier = Modifier.align(Alignment.Bottom),
            )
        }
    }
}

/**
 * One lyric line: each chord stacked directly over the run of words it lands on.
 *
 * A chord wider than the run beneath it normally widens the column, which is right between words —
 * it keeps two chords from colliding. Mid-word it is wrong: it opens a gap inside the word, so
 * `de[C]livered` reads as two words. A chord landing inside a word is therefore drawn without
 * contributing width, overhanging to the right instead of pushing the rest of the word away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordLine(
    segments: List<ChordSegment>,
    showChords: Boolean,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    chordColor: Color = MaterialTheme.colorScheme.primary,
    fontSize: TextUnit = 14.sp,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val prepared = remember(segments) { collapseTrailingChords(segments) }
    FlowRow(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        prepared.forEachIndexed { index, segment ->
            // Chords past the last word have nothing to sit over and would otherwise stretch the
            // line to their own width, which is width the words could have used. They hang off the
            // end instead, so the line measures as wide as what is being sung.
            val trailing = index > 0 && index == prepared.lastIndex && segment.text.isEmpty()
            val continuesWord = trailing || (
                index > 0 &&
                    prepared[index - 1].text.lastOrNull()?.isWhitespace() == false &&
                    segment.text.firstOrNull()?.isWhitespace() == false
                )
            Column(horizontalAlignment = Alignment.Start) {
                if (showChords) {
                    Text(
                        text = segment.chord,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize * 0.82f,
                        fontWeight = FontWeight.Bold,
                        color = chordColor,
                        softWrap = false,
                        style = textStyle.copy(lineHeight = fontSize * 1.1f),
                        // Three cases. A trailing run is hung by its right edge, so it finishes
                        // where the words finish instead of running out past them — that recovers
                        // the width a projected line needs. A chord inside a word overhangs to the
                        // right so it cannot split the word. Everything else keeps a gap after it,
                        // which is what stops one chord touching the next where the syllables
                        // beneath them are narrow.
                        modifier = when {
                            trailing -> Modifier.width(0.dp)
                                .wrapContentWidth(align = Alignment.End, unbounded = true)
                            continuesWord -> Modifier.width(0.dp)
                                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                            else -> Modifier.padding(end = 7.dp)
                        },
                    )
                }
                Text(
                    text = segment.text,
                    fontSize = fontSize,
                    color = textColor,
                    softWrap = false,
                    style = textStyle.copy(lineHeight = fontSize * if (showChords) 1.5f else 1.25f),
                )
            }
        }
    }
}
