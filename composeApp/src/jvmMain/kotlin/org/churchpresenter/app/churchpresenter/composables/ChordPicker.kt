package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.sharedui.composables.ZoneLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.songchords.ChordTransposer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.strings.generated.resources.song_insert_named_chord
import org.churchpresenter.strings.generated.resources.song_chord_type
import org.churchpresenter.strings.generated.resources.song_chord_root
import org.churchpresenter.strings.generated.resources.song_chord_build
import org.churchpresenter.strings.generated.resources.song_chord_major
import org.churchpresenter.strings.generated.resources.song_chord_prefer_flats
import org.churchpresenter.strings.generated.resources.song_chord_prefer_sharps
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.PaddingValues

/**
 * Any chord at all, built from a root and a type — for the chords a key's seven do not cover.
 *
 * Roots start out spelled the way [songKey] is written, so a pick in E flat reads A flat, not
 * G sharp; the ♯ / ♭ switch beside them spells them the other way, for a player who reads a
 * black key by the other name (issue #649).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChordPicker(songKey: String, flats: Boolean, onInsertChord: (String) -> Unit) {
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
            painter = painterResource(if (open) IconRes.drawable.ic_arrow_up else IconRes.drawable.ic_arrow_down),
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
internal fun ChordChip(text: String, highlighted: Boolean, onClick: () -> Unit) {
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
