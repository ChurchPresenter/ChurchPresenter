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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.ChorusRepeat
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.icons.generated.resources.ic_note
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.song_chorus_repeat
import org.churchpresenter.strings.generated.resources.song_chorus_repeat_off
import org.churchpresenter.strings.generated.resources.song_chorus_repeat_on
import org.churchpresenter.strings.generated.resources.song_chorus_repeat_settings
import org.churchpresenter.strings.generated.resources.song_notes
import org.churchpresenter.strings.generated.resources.song_section_sung
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedCheckbox
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/** Test tags of the controls below; a section's checkbox is [SECTION_SUNG_TAG] plus its key. */
const val SECTION_SUNG_TAG = "songSectionSung:"
const val CHORUS_REPEAT_TAG = "songChorusRepeat"
const val SONG_NOTES_TAG = "songNotes"

/** How far a section the song leaves out is faded, unselected and selected. */
internal const val SKIPPED_ALPHA = 0.45f
internal const val SKIPPED_SELECTED_ALPHA = 0.8f

/** Lines of a song's notes shown before the card is opened. */
private const val NOTES_COLLAPSED_LINES = 2

/**
 * What the lyrics panel changes about the open song without its editor: whether a written section
 * is sung, and whether the chorus repeats. Each saves the song; null where the library cannot be
 * written, an Instance Link follower's.
 */
internal class SongArrangementActions(
    val onSectionSungChange: (song: SongItem, key: String, sung: Boolean) -> Unit,
    val onChorusRepeatChange: (song: SongItem, value: ChorusRepeat) -> Unit,
)

/** [this] with the written section [key] sung or left out. */
internal fun SongItem.withSectionSung(key: String, sung: Boolean): SongItem =
    copy(skippedSections = if (sung) skippedSections - key else (skippedSections + key).distinct())

/**
 * The box beside a written section's label: ticked while the song sings it. Disabled, but still
 * showing the song's choice, when [onSungChange] is null.
 */
@Composable
internal fun SectionSungCheckbox(
    sectionKey: String,
    label: String,
    sung: Boolean,
    onSungChange: ((Boolean) -> Unit)?,
) {
    val description = stringResource(Res.string.song_section_sung, label)
    RaisedCheckbox(
        checked = sung,
        onCheckedChange = onSungChange,
        enabled = onSungChange != null,
        modifier = Modifier.size(20.dp)
            .semantics { contentDescription = description }
            .testTag(SECTION_SUNG_TAG + sectionKey),
    )
}

/** The song's own chorus rule, in the lyrics panel's action bar. */
@Composable
internal fun ChorusRepeatField(value: ChorusRepeat, onValueChange: (ChorusRepeat) -> Unit) {
    val labels = mapOf(
        ChorusRepeat.SETTINGS to stringResource(Res.string.song_chorus_repeat_settings),
        ChorusRepeat.ON to stringResource(Res.string.song_chorus_repeat_on),
        ChorusRepeat.OFF to stringResource(Res.string.song_chorus_repeat_off),
    )
    DropdownSettingsField(
        value = labels.getValue(value),
        options = labels.values.toList(),
        onValueChange = { picked -> labels.entries.firstOrNull { it.value == picked }?.let { onValueChange(it.key) } },
        label = stringResource(Res.string.song_chorus_repeat),
        modifier = Modifier.testTag(CHORUS_REPEAT_TAG),
    )
}

/**
 * The open song's notes, above its lyrics: two lines until clicked, then all of them. For the
 * operator only — nothing here reaches an output.
 */
@Composable
internal fun SongNotesCard(notes: String) {
    var expanded by rememberSaveable(notes) { mutableStateOf(false) }
    val notesLabel = stringResource(Res.string.song_notes)
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
