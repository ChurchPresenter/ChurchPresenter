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
import org.churchpresenter.theme.AppShape
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
import churchpresenter.composeapp.generated.resources.Res as AppRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.preview
import org.churchpresenter.strings.generated.resources.song_chords_used
import org.churchpresenter.strings.generated.resources.song_insert_chord
import org.churchpresenter.strings.generated.resources.song_transpose_down
import org.churchpresenter.strings.generated.resources.song_transpose_reset
import org.churchpresenter.strings.generated.resources.song_transpose_up
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.songchords.ChordSegment
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.songchords.SongSectionWordGroup
import org.churchpresenter.songchords.SongSectionWords
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import org.churchpresenter.strings.generated.resources.song_transpose
import org.churchpresenter.strings.generated.resources.song_key_up
import org.churchpresenter.strings.generated.resources.song_key_down
import org.churchpresenter.strings.generated.resources.song_insert_named_chord
import org.churchpresenter.strings.generated.resources.song_chords_in
import org.churchpresenter.strings.generated.resources.song_chord_type
import org.churchpresenter.strings.generated.resources.song_chord_root
import org.churchpresenter.strings.generated.resources.song_chord_build
import org.churchpresenter.strings.generated.resources.song_chord_major
import org.churchpresenter.strings.generated.resources.song_chord_prefer_flats
import org.churchpresenter.strings.generated.resources.song_chord_prefer_sharps
import churchpresenter.composeapp.generated.resources.ic_remove
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_add
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.PaddingValues

private const val CHORD_SPACING_RATIO = 0.42f

/** How a section reads in the preview — the colour tells verses from choruses at a glance. */
enum class SongSectionKind { VERSE, CHORUS, BRIDGE, TAG }

/**
 * A section of the song as the preview draws it: a label, and its lines already split into runs.
 *
 * [slideIndex] and [slideCount] mirror `LyricSection`'s — a section broken by a manual `[---]`
 * arrives here as several slides carrying the same label, so the preview shows what will go on
 * screen rather than what was typed.
 */
data class PreviewSection(
    val label: String,
    val kind: SongSectionKind,
    val lines: List<List<ChordSegment>>,
    val slideIndex: Int = 0,
    val slideCount: Int = 1,
)

/**
 * What the footer counts.
 *
 * [sections] counts sections, not slides: a chorus broken by a manual `[---]` is drawn as two
 * [PreviewSection]s so the preview shows what will go on screen, but it is still one chorus and
 * counting it as two would tell the person editing that they had written a section they had not.
 */
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
 *
 * A manual break (`[---]`) ends a slide without ending the section, so the section comes back as
 * several [PreviewSection]s carrying the same label and numbered among themselves — again matching
 * what the presenter will do with it.
 */
fun buildPreviewSections(
    text: String,
    steps: Int = 0,
    flats: Boolean = false,
    showChords: Boolean = true,
): List<PreviewSection> {
    val out = mutableListOf<PreviewSection>()
    var label = ""
    var slideOfSection = 0
    val body = mutableListOf<String>()

    fun flush() {
        if (label.isBlank() && body.isEmpty()) return
        out.add(
            PreviewSection(
                label = label,
                kind = sectionKindOf(label),
                lines = body.map { ChordTransposer.parseLine(it, steps, flats, showChords) },
                slideIndex = slideOfSection++,
            )
        )
        body.clear()
    }

    text.lines().forEach { line ->
        if (ChordTransposer.isSectionHeader(line)) {
            flush()
            slideOfSection = 0
            label = line.trim().let { it.substring(1, it.length - 1) }.trim()
        } else if (ChordTransposer.isSlideBreak(line)) {
            if (body.isNotEmpty()) flush()
        } else if (ChordTransposer.isBackgroundDirective(line)) {
            // Configuration, not words — the panel above shows what it says.
        } else if (line.isNotBlank()) {
            body.add(line)
        }
    }
    flush()
    // A section's slide count is only known once the section has ended, so it is filled in
    // afterwards: a run of slides begins at index 0 and lasts until the next one that does.
    val counts = IntArray(out.size)
    var runStart = 0
    out.forEachIndexed { index, section ->
        if (section.slideIndex == 0 && index > 0) {
            for (i in runStart until index) counts[i] = index - runStart
            runStart = index
        }
    }
    for (i in runStart until out.size) counts[i] = out.size - runStart
    return out.mapIndexed { index, section -> section.copy(slideCount = counts[index]) }
}

/** Counts what the footer reports. Words are counted after chords come off, not before. */
fun songStatsOf(sections: List<PreviewSection>): SongStats {
    val lines = sections.sumOf { it.lines.size }
    val words = sections.sumOf { section ->
        section.lines.sumOf { line ->
            line.joinToString("") { it.text }.split(Regex("\\s+")).count { it.isNotBlank() }
        }
    }
    return SongStats(sections.count { it.slideIndex == 0 }, lines, words)
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
fun SectionLabelRow(
    label: String,
    modifier: Modifier = Modifier,
    slideIndex: Int = 0,
    slideCount: Int = 1,
) {
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
                .background(ink.copy(alpha = 0.16f), AppShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
        // Which slide of the section this is, shown only when there is more than one — otherwise
        // every unsplit verse in the library would carry a "1/1" that tells nobody anything. Digits
        // and a slash, so there is nothing here to translate.
        if (slideCount > 1) {
            ZoneLabel(text = "${slideIndex + 1}/$slideCount")
        }
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
 * The right-hand pane of the song editor: the song as the band will read it, the transpose steps
 * that rewrite its chords, and the chords of a chosen key to insert from.
 *
 * The preview draws the text exactly as written, so what is inserted is what is shown. [songKey]
 * only decides which chords the palette and the picker offer; it never shifts the song. Moving the
 * song is [onTransposeUp]/[onTransposeDown], which rewrite the text itself, and [transposed] is
 * how far that has gone this session, for the reset chip.
 *
 * Rendering-only — every edit leaves through a callback, so the pane holds no state of its own
 * beyond the picker's selection and can be driven straight from a test.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongChordPreview(
    text: String,
    showChords: Boolean,
    songKey: String,
    transposed: Int,
    onKeyUp: () -> Unit,
    onKeyDown: () -> Unit,
    onTransposeUp: () -> Unit,
    onTransposeDown: () -> Unit,
    onTransposeReset: () -> Unit,
    onInsertChord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flats = ChordTransposer.prefersFlats(songKey)
    val sections = remember(text, showChords) { buildPreviewSections(text, 0, flats, showChords) }
    val used = remember(text) { ChordTransposer.chordsIn(text).toSet() }
    val palette = remember(songKey) { ChordTransposer.diatonicChords(songKey) }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {

        PreviewHeader(showChords, transposed, onTransposeUp, onTransposeDown, onTransposeReset)
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
                        SectionLabelRow(
                            section.label,
                            modifier = Modifier.padding(bottom = 3.dp),
                            slideIndex = section.slideIndex,
                            slideCount = section.slideCount,
                        )
                    }
                    section.lines.forEach { line -> ChordLine(line, showChords) }
                }
            }
        }

        // The chords of the chosen key, and any other chord, to insert from.
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
                    Stepper(
                        label = stringResource(Res.string.song_chords_in),
                        value = songKey,
                        down = StepAction(
                            AppRes.drawable.ic_remove,
                            stringResource(Res.string.song_key_down),
                            onKeyDown,
                        ),
                        up = StepAction(AppRes.drawable.ic_add, stringResource(Res.string.song_key_up), onKeyUp),
                    )
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val insertLabel = stringResource(Res.string.song_insert_chord)
                    palette.forEach { chord ->
                        TooltipWrapper(tooltip = insertLabel) {
                            ChordChip(chord, highlighted = chord in used, onClick = { onInsertChord(chord) })
                        }
                    }
                }
                ChordPicker(songKey = songKey, flats = flats, onInsertChord = onInsertChord)
            }
        }
    }
}

/** The pane's name, and the steps that move the whole song, with a reset once it has moved. */
@Composable
private fun PreviewHeader(
    showChords: Boolean,
    transposed: Int,
    onTransposeUp: () -> Unit,
    onTransposeDown: () -> Unit,
    onTransposeReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ZoneLabel(stringResource(Res.string.preview), modifier = Modifier.weight(1f))
        if (!showChords) return@Row
        if (transposed != 0) {
            Text(
                text = stringResource(
                    Res.string.song_transpose_reset,
                    if (transposed > 0) "+$transposed" else "$transposed",
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(7.dp))
                    .clickable(onClick = onTransposeReset)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Stepper(
            label = stringResource(Res.string.song_transpose),
            value = null,
            down = StepAction(
                AppRes.drawable.ic_arrow_down,
                stringResource(Res.string.song_transpose_down),
                onTransposeDown,
            ),
            up = StepAction(AppRes.drawable.ic_arrow_up, stringResource(Res.string.song_transpose_up), onTransposeUp),
        )
    }
}

/**
 * Any chord at all, built from a root and a type — for the chords a key's seven do not cover.
 *
 * Roots start out spelled the way [songKey] is written, so a pick in E flat reads A flat, not
 * G sharp; the ♯ / ♭ switch beside them spells them the other way, for a player who reads a
 * black key by the other name (issue #649).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordPicker(songKey: String, flats: Boolean, onInsertChord: (String) -> Unit) {
    var root by remember(songKey) { mutableStateOf(ChordTransposer.pitchOf(songKey) ?: 0) }
    var quality by remember { mutableStateOf(ChordTransposer.CHORD_QUALITIES.first()) }
    var useFlats by remember(songKey) { mutableStateOf(flats) }
    // Folded away until asked for: open, it is five rows of chips, which left the song itself two
    // lines of room. The key's own seven chords above cover most of what a song needs.
    var open by remember { mutableStateOf(false) }
    val chord = ChordTransposer.nameOf(root, useFlats) + quality

    Row(
        modifier = Modifier.clickable { open = !open }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ZoneLabel(stringResource(Res.string.song_chord_build))
        Icon(
            painter = painterResource(if (open) AppRes.drawable.ic_arrow_up else AppRes.drawable.ic_arrow_down),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
    }
    if (!open) return

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ZoneLabel(stringResource(Res.string.song_chord_root), modifier = Modifier.weight(1f))
            TooltipWrapper(tooltip = stringResource(Res.string.song_chord_prefer_sharps)) {
                ChordChip(SHARP_SIGN, highlighted = !useFlats, onClick = { useFlats = false })
            }
            TooltipWrapper(tooltip = stringResource(Res.string.song_chord_prefer_flats)) {
                ChordChip(FLAT_SIGN, highlighted = useFlats, onClick = { useFlats = true })
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            for (pitch in 0 until PITCH_CLASSES) {
                val name = ChordTransposer.nameOf(pitch, useFlats)
                ChordChip(name, highlighted = pitch == root, onClick = { root = pitch })
            }
        }
        ZoneLabel(stringResource(Res.string.song_chord_type), modifier = Modifier.padding(top = 3.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val major = stringResource(Res.string.song_chord_major)
            ChordTransposer.CHORD_QUALITIES.forEach { q ->
                ChordChip(qualityLabel(q, major), highlighted = q == quality, onClick = { quality = q })
            }
        }
        FilledTonalButton(
            onClick = { onInsertChord(chord) },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            modifier = Modifier.padding(top = 3.dp).height(30.dp),
        ) {
            Text(
                stringResource(Res.string.song_insert_named_chord, chord),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private const val PITCH_CLASSES = 12

// The accidentals as music writes them: the switch's own labels. The chord inserted is still
// spelled `#`/`b`, which is what the chord grammar reads.
private const val SHARP_SIGN = "♯"
private const val FLAT_SIGN = "♭"

/** How a chord type reads on its chip: the bare major triad by name, a flat as a real flat sign. */
internal fun qualityLabel(quality: String, major: String): String =
    if (quality.isEmpty()) major else quality.replace("b5", "♭5")

/** One chord on a chip: tinted when it is in the song, or when it is the picker's selection. */
@Composable
private fun ChordChip(text: String, highlighted: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = if (highlighted) primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .widthIn(min = 38.dp)
            .height(27.dp)
            .background(
                if (highlighted) primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                AppShape(7.dp),
            )
            .border(
                1.dp,
                if (highlighted) primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                AppShape(7.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
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

/** One step of a [Stepper]: its icon, its hover label, and what it does. */
private class StepAction(val icon: DrawableResource, val tooltip: String, val onClick: () -> Unit)

/** A labelled pair of steps, with the [value] they move between when there is one to show. */
@Composable
private fun Stepper(label: String, value: String?, down: StepAction, up: StepAction) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(accent.copy(alpha = 0.10f), AppShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), AppShape(8.dp))
            .padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
    ) {
        ZoneLabel(label, color = accent.copy(alpha = 0.85f))
        if (value != null) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 26.dp),
            )
        }
        StepButton(down, accent)
        StepButton(up, accent)
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
                shape = AppShape(6.dp),
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
private fun StepButton(action: StepAction, accent: Color) {
    TooltipWrapper(tooltip = action.tooltip) {
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 20.dp)
                .background(accent.copy(alpha = 0.16f), AppShape(5.dp))
                .clickable(onClick = action.onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(action.icon),
                contentDescription = action.tooltip,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
