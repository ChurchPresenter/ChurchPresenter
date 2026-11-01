package org.churchpresenter.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.ic_edit
import org.churchpresenter.icons.generated.resources.ic_note
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.song_notes_edit
import org.churchpresenter.strings.generated.resources.song_notes_tag
import org.churchpresenter.strings.generated.resources.song_section_cue
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.keyboardFocusRing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/** Test tag of the open song's notes card. */
const val SONG_NOTES_TAG = "songNotes"

/** Test tag prefix of a section's cue chip in the lyrics panel, followed by the section key. */
const val SECTION_CUE_TAG = "songSectionCue:"

/** How far a section the song leaves out is faded, unselected and selected. */
internal const val SKIPPED_ALPHA = 0.45f
internal const val SKIPPED_SELECTED_ALPHA = 0.8f

private const val NOTES_FILL_ALPHA = 0.08f
private const val NOTES_RING_ALPHA = 0.2f
private const val NOTES_TAG_ALPHA = 0.16f
private const val NOTES_EDIT_ALPHA = 0.7f
private val NotesShape = AppShape(10.dp)
private val TagShape = AppShape(5.dp)

/**
 * The open song's notes, above its lyrics: one line until clicked, then all of them, with a pencil
 * that opens the song editor. For the operator only — nothing here reaches an output.
 */
@Composable
internal fun SongNotesCard(notes: String, onEdit: () -> Unit) {
    var expanded by rememberSaveable(notes) { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, bottom = 4.dp)
            .clip(NotesShape)
            .background(accent.copy(alpha = NOTES_FILL_ALPHA))
            .border(1.dp, accent.copy(alpha = NOTES_RING_ALPHA), NotesShape)
            .testTag(SONG_NOTES_TAG)
            .clickable { expanded = !expanded }
            .padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stringResource(Res.string.song_notes_tag).uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            color = accent,
            modifier = Modifier.padding(top = 1.dp)
                .background(accent.copy(alpha = NOTES_TAG_ALPHA), TagShape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            text = notes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val editLabel = stringResource(Res.string.song_notes_edit)
        Box(
            modifier = Modifier.size(22.dp, 20.dp)
                .keyboardFocusRing(TagShape)
                .clip(TagShape)
                .clickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(IconRes.drawable.ic_edit),
                contentDescription = editLabel,
                tint = accent.copy(alpha = NOTES_EDIT_ALPHA),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** A section's cue, beside its label in the lyrics panel. */
@Composable
internal fun SectionCueChip(key: String, cue: String, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.widthIn(max = 220.dp)
            .background(accent.copy(alpha = NOTES_TAG_ALPHA), TagShape)
            .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
            .testTag(SECTION_CUE_TAG + key),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_note),
            contentDescription = stringResource(Res.string.song_section_cue),
            tint = accent,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = cue,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
