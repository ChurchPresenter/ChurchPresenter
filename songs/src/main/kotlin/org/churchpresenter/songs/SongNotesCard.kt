package org.churchpresenter.songs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.ic_note
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.song_comments
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/** Test tag of the open song's notes card. */
const val SONG_NOTES_TAG = "songNotes"

/** How far a section the song leaves out is faded, unselected and selected. */
internal const val SKIPPED_ALPHA = 0.45f
internal const val SKIPPED_SELECTED_ALPHA = 0.8f

/** Lines of a song's notes shown before the card is opened. */
private const val NOTES_COLLAPSED_LINES = 2

/**
 * The open song's comments, above its lyrics: two lines until clicked, then all of them. For the
 * operator only — nothing here reaches an output.
 */
@Composable
internal fun SongNotesCard(notes: String) {
    var expanded by rememberSaveable(notes) { mutableStateOf(false) }
    val notesLabel = stringResource(Res.string.song_comments)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = AppShape(6.dp),
        modifier = Modifier.fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, bottom = 6.dp)
            .testTag(SONG_NOTES_TAG)
            .clickable { expanded = !expanded },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(IconRes.drawable.ic_note),
                contentDescription = notesLabel,
                modifier = Modifier.size(16.dp).padding(top = 2.dp),
            )
            Text(
                text = notes,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else NOTES_COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
