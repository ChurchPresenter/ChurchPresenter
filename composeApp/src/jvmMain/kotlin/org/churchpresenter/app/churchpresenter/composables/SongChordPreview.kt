package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.preview
import org.churchpresenter.resources.generated.resources.song_chords_in_key
import org.churchpresenter.resources.generated.resources.song_chords_used
import org.churchpresenter.resources.generated.resources.song_insert_chord
import org.churchpresenter.resources.generated.resources.song_key
import org.churchpresenter.resources.generated.resources.song_transpose_down
import org.churchpresenter.resources.generated.resources.song_transpose_reset
import org.churchpresenter.resources.generated.resources.song_transpose_up
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.songchords.ChordSegment
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.songchords.SongSectionWordGroup
import org.churchpresenter.songchords.SongSectionWords
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

private const val CHORD_SPACING_RATIO = 0.42f

/** How a section reads in the preview — the colour tells verses from choruses at a glance. */
enum class SongSectionKind { VERSE, CHORUS, BRIDGE, TAG }

/** A section of the song as the preview draws it: a label, and its lines already split into runs. */
data class PreviewSection(
    val label: String,
    val kind: SongSectionKind,
    val lines: List<List<ChordSegment>>,
)

/** What the footer counts. */
data class SongStats(val sections: Int, val lines: Int, val words: Int)

/**
 * Which kind of section a header names.
 *
 * Matched on the section words the song format itself uses, in every language at once — see
 * [SongSectionWords], which the wrapping and importing sides read too, so a song written with
 * Polish or Russian markers colours like the English one it would import to.
 *
 * Only the four kinds that have an ink of their own are distinguished. Everything else — an intro,
 * an instrumental, a pre-chorus, an unrecognised name — reads as a verse, which is what an
 * unlabelled block is anyway.
 */
fun sectionKindOf(label: String): SongSectionKind = when (SongSectionWords.groupOf(label)) {
    SongSectionWordGroup.CHORUS -> SongSectionKind.CHORUS
    SongSectionWordGroup.BRIDGE -> SongSectionKind.BRIDGE
    SongSectionWordGroup.TAG -> SongSectionKind.TAG
    else -> SongSectionKind.VERSE
}

/**
 * Splits raw lyric text into the sections the preview draws.
 *
 * A header line starts a section and everything after it belongs to that section until the next
 * header — the same rule `SongsViewModel.splitLyricsIntoSections` presents by, so the preview shows
 * what will actually go on screen. Blank lines are separators only; they neither start a section nor
 * appear in one, so a song written without blank lines between its verses still reads as verses.
 */
fun buildPreviewSections(
    text: String,
    steps: Int = 0,
    flats: Boolean = false,
    showChords: Boolean = true,
): List<PreviewSection> {
    val out = mutableListOf<PreviewSection>()
    var label = ""
    val body = mutableListOf<String>()

    fun flush() {
        if (label.isBlank() && body.isEmpty()) return
        out.add(
            PreviewSection(
                label = label,
                kind = sectionKindOf(label),
                lines = body.map { ChordTransposer.parseLine(it, steps, flats, showChords) },
            )
        )
        body.clear()
    }

    text.lines().forEach { line ->
        if (ChordTransposer.isSectionHeader(line)) {
            flush()
            label = line.trim().let { it.substring(1, it.length - 1) }.trim()
        } else if (line.isNotBlank()) {
            body.add(line)
        }
    }
    flush()
    return out
}

/** Counts what the footer reports. Words are counted after chords come off, not before. */
fun songStatsOf(sections: List<PreviewSection>): SongStats {
    val lines = sections.sumOf { it.lines.size }
    val words = sections.sumOf { section ->
        section.lines.sumOf { line ->
            line.joinToString("") { it.text }.split(Regex("\\s+")).count { it.isNotBlank() }
        }
    }
    return SongStats(sections.size, lines, words)
}

/**
 * Ink for each section kind.
 *
 * A legend — verse, chorus, bridge, tag — so these do not follow the theme *accent*; a legend whose
 * colours move with the accent stops being one. They do follow light and dark, which is why they are
 * theme tokens rather than literals here: the pair that holds its contrast on a light ground is not
 * the pair that holds it on a dark one, and the theme is the one place that knows which is in force.
 */
internal object SectionInk {
    @Composable
    internal fun of(kind: SongSectionKind): Color = with(MaterialTheme.semantic) {
        when (kind) {
            SongSectionKind.VERSE -> chordVerse
            SongSectionKind.CHORUS -> chordChorus
            SongSectionKind.BRIDGE -> chordBridge
            SongSectionKind.TAG -> chordTag
        }
    }
}

/**
 * A section's name as a coloured chip with a rule running off it — verse amber, chorus purple,
 * bridge green, tag red.
 *
 * Shared by the song editor's preview and the Songs tab's list so a section is recognised the same
 * way in both; [label] is the bare name, with any `[]`/`{}` already off it.
 */
@Composable
fun SectionLabelRow(label: String, modifier: Modifier = Modifier) {
    val ink = SectionInk.of(sectionKindOf(label))
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ZoneLabel(
            text = label,
            color = ink,
            modifier = Modifier
                .background(ink.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ZoneLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text.uppercase(),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.0.sp,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * The right-hand pane of the song editor: the song as the band will read it, a key readout that
 * transposes, and the chords of that key to insert from.
 *
 * Rendering-only — every edit leaves through [onInsertChord] or the transpose callbacks, so the
 * pane holds no state of its own and can be driven straight from a test.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongChordPreview(
    text: String,
    showChords: Boolean,
    steps: Int,
    onTransposeUp: () -> Unit,
    onTransposeDown: () -> Unit,
    onTransposeReset: () -> Unit,
    onInsertChord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceKey = remember(text) { ChordTransposer.detectKey(text) }
    val keyPitch = ChordTransposer.pitchOf(sourceKey) ?: 0
    // The key it lands in decides the spelling, so it is worked out before anything is named.
    val flats = ChordTransposer.prefersFlats(keyPitch + steps)
    val currentKey = ChordTransposer.nameOf(keyPitch + steps, flats)
    val sections = remember(text, steps, showChords, flats) {
        buildPreviewSections(text, steps, flats, showChords)
    }
    val used = remember(text, steps, flats) {
        ChordTransposer.chordsIn(text, steps, flats).toSet()
    }
    val palette = remember(currentKey) { ChordTransposer.diatonicChords(currentKey) }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {

        // Header: the pane's name, and the key it is being read in.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZoneLabel(stringResource(Res.string.preview), modifier = Modifier.weight(1f))
            if (showChords) {
                KeyStepper(
                    keyName = currentKey,
                    onUp = onTransposeUp,
                    onDown = onTransposeDown,
                )
                if (steps != 0) {
                    val label = stringResource(
                        Res.string.song_transpose_reset,
                        if (steps > 0) "+$steps" else "$steps",
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
                            .clickable(onClick = onTransposeReset)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // The song itself.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            // Without chord rows there is nothing to leave room for, so the whole chart closes up
            // and reads as a plain lyric sheet.
            verticalArrangement = Arrangement.spacedBy(if (showChords) 18.dp else 10.dp),
        ) {
            sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(if (showChords) 5.dp else 2.dp)) {
                    if (section.label.isNotBlank()) {
                        SectionLabelRow(section.label, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    section.lines.forEach { line -> ChordLine(line, showChords) }
                }
            }
        }

        // The chords of the current key, to insert from.
        if (showChords && palette.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZoneLabel(stringResource(Res.string.song_chords_in_key, currentKey))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(Res.string.song_chords_used, used.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val insertLabel = stringResource(Res.string.song_insert_chord)
                    palette.forEach { chord ->
                        val inSong = chord in used
                        val ink = if (inSong) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.onSurfaceVariant
                        TooltipWrapper(tooltip = insertLabel) {
                            Text(
                                text = chord,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ink,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .widthIn(min = 38.dp)
                                    .height(27.dp)
                                    .background(
                                        if (inSong) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        RoundedCornerShape(7.dp),
                                    )
                                    .border(
                                        1.dp,
                                        if (inSong) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(7.dp),
                                    )
                                    .clickable { onInsertChord(chord) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The line above the chart: what the song is in, what the player's hands are doing, and how fast.
 *
 * `Key` is the song as written, taken from its first chord. `Capo` and `Play` only appear with a
 * capo set, because `Play` is the whole point of one — a capo at 2 in G means F shapes, and the
 * shapes are what the player actually reads. Tempo appears whenever there is one.
 *
 * Returns null when there is nothing to say, so the caller can leave the row out entirely.
 */
fun songInfoOf(
    section: LyricSection,
    keyLabel: String,
    capoLabel: String,
    playLabel: String,
    bpmLabel: String,
): String? {
    val parts = mutableListOf<String>()
    if (section.chordLines.isNotEmpty()) {
        val key = ChordTransposer.detectKey(section.chordLines.joinToString("\n"))
        parts.add("$keyLabel $key")
        if (section.capo > 0) {
            val shapes = ChordTransposer.pitchOf(key)?.let { pitch ->
                val played = pitch - section.capo
                ChordTransposer.nameOf(played, ChordTransposer.prefersFlats(played))
            }
            parts.add("$capoLabel ${section.capo}")
            if (shapes != null) parts.add("$playLabel $shapes")
        }
    }
    if (section.bpm > 0) parts.add("${section.bpm} $bpmLabel")
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

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
private fun ChordLine(
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

/** The key readout, with the two steps that move the whole song. */
@Composable
private fun KeyStepper(keyName: String, onUp: () -> Unit, onDown: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
    ) {
        ZoneLabel(stringResource(Res.string.song_key), color = accent.copy(alpha = 0.85f))
        Text(
            text = keyName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 26.dp),
        )
        StepButton("−", stringResource(Res.string.song_transpose_down), accent, onDown)
        StepButton("+", stringResource(Res.string.song_transpose_up), accent, onUp)
    }
}

/** A plain hover label, shaped like the ones the tabs use. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TooltipWrapper(tooltip: String, content: @Composable () -> Unit) {
    ConditionalTooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = tooltip,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        content = content,
    )
}

@Composable
private fun StepButton(glyph: String, tooltip: String, accent: Color, onClick: () -> Unit) {
    TooltipWrapper(tooltip = tooltip) {
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 20.dp)
                .background(accent.copy(alpha = 0.16f), RoundedCornerShape(5.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = glyph, fontSize = 13.sp, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}
