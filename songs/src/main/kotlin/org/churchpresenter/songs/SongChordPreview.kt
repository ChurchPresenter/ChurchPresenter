package org.churchpresenter.songs

import org.churchpresenter.presenter.ChordLine
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.composables.SongSectionKind
import org.churchpresenter.sharedui.composables.sectionKindOf
import org.churchpresenter.sharedui.composables.SectionLabelRow
import org.churchpresenter.sharedui.composables.ZoneLabel
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
import androidx.compose.foundation.rememberScrollState
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.preview
import org.churchpresenter.strings.generated.resources.song_chords_used
import org.churchpresenter.strings.generated.resources.song_insert_chord
import org.churchpresenter.strings.generated.resources.song_transpose_down
import org.churchpresenter.strings.generated.resources.song_transpose_reset
import org.churchpresenter.strings.generated.resources.song_transpose_up
import org.churchpresenter.songchords.ChordSegment
import org.churchpresenter.songchords.ChordTransposer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import org.churchpresenter.strings.generated.resources.song_transpose
import org.churchpresenter.strings.generated.resources.song_key_up
import org.churchpresenter.strings.generated.resources.song_key_down
import org.churchpresenter.strings.generated.resources.song_chords_in
import org.churchpresenter.icons.generated.resources.ic_remove
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_add
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import org.churchpresenter.sharedui.composables.ConditionalTooltipArea

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
                modifier = Modifier
                    .fillMaxWidth()
                    .guideTarget(GuideTargets.SONG_CHORD_PALETTE)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                            IconRes.drawable.ic_remove,
                            stringResource(Res.string.song_key_down),
                            onKeyDown,
                        ),
                        up = StepAction(IconRes.drawable.ic_add, stringResource(Res.string.song_key_up), onKeyUp),
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
                IconRes.drawable.ic_arrow_down,
                stringResource(Res.string.song_transpose_down),
                onTransposeDown,
            ),
            up = StepAction(IconRes.drawable.ic_arrow_up, stringResource(Res.string.song_transpose_up), onTransposeUp),
        )
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
internal fun TooltipWrapper(tooltip: String, content: @Composable () -> Unit) {
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
