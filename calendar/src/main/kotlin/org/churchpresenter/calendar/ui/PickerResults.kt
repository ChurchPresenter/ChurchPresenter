package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import org.churchpresenter.calendar.generated.resources.calendar_preview_toggle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import org.churchpresenter.calendar.generated.resources.calendar_edit_song
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_no_results
import org.churchpresenter.calendar.generated.resources.calendar_duration_hint
import org.churchpresenter.calendar.generated.resources.calendar_duration
import org.churchpresenter.calendar.generated.resources.calendar_ministry_hint
import org.churchpresenter.calendar.generated.resources.calendar_ministry_who
import org.churchpresenter.calendar.generated.resources.calendar_ministry_what
import org.churchpresenter.calendar.generated.resources.calendar_pick_ministry
import org.churchpresenter.calendar.generated.resources.calendar_ministry_new
import org.churchpresenter.calendar.generated.resources.calendar_ministry_empty_hint
import org.churchpresenter.calendar.generated.resources.calendar_ministry_detail_hint
import org.churchpresenter.calendar.generated.resources.calendar_ministry_add
import org.churchpresenter.calendar.generated.resources.calendar_pick_add_tip
import org.churchpresenter.calendar.generated.resources.calendar_pick_empty_hint
import org.churchpresenter.calendar.generated.resources.calendar_pick_reference
import org.churchpresenter.calendar.generated.resources.calendar_presets_empty
import org.churchpresenter.calendar.generated.resources.calendar_presets_empty_sub
import org.churchpresenter.calendar.generated.resources.calendar_section_new
import org.churchpresenter.calendar.generated.resources.calendar_songs_loading
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.SectionStyle
import org.churchpresenter.calendar.model.parseReference
import org.churchpresenter.calendar.model.sectionItem
import org.churchpresenter.calendar.model.toScheduleItem
import org.churchpresenter.calendar.model.asRow
import org.churchpresenter.calendar.model.parseDuration
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.elevationPalette

private val RESULT_ICON = 24.dp
private val ADD_BADGE = 21.dp
private const val DEFAULT_SECTION_COLOR = "#5B9DF5"

@Composable
internal fun SongResults(
    songs: List<SongItem>,
    songsLoaded: Boolean,
    songBook: String?,
    query: String,
    onAdd: (List<ScheduleItem>) -> Unit,
    onEditSong: ((SongItem) -> Unit)?,
) {
    val scoped = remember(songs, songBook) { songs.filter { songBook == null || it.songbook == songBook } }
    val matches by remember(scoped, query) { derivedStateOf { matchSongs(scoped, query) } }
    val reference = remember(query) { parseReference(query) }

    when {
        !songsLoaded -> EmptyBody(stringResource(Res.string.calendar_songs_loading), "")
        matches.isEmpty() && reference == null ->
            EmptyBody(
                stringResource(Res.string.calendar_no_results),
                stringResource(Res.string.calendar_pick_empty_hint),
            )

        else -> ScrollableList(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // A reference typed into the song box is still a reference — offering it here saves
            // switching tabs to add the passage somebody just typed out in full.
            if (reference != null) {
                item(key = "reference") {
                    ResultRow(
                        title = reference.display,
                        subtitle = "",
                        badge = stringResource(Res.string.calendar_pick_reference),
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onAdd(listOf(reference.toScheduleItem())) },
                    )
                }
            }
            itemsIndexed(
                matches,
                key = { index, song -> song.sourceFile.ifBlank { "$index:${song.songId}" } },
            ) { _, song ->
                ResultRow(
                    title = if (song.number.isNotBlank()) "${song.number} - ${song.title}" else song.title,
                    subtitle = song.songbook,
                    onClick = { onAdd(listOf(song.toScheduleItem())) },
                    onEdit = if (onEditSong != null) {
                        { onEditSong(song) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
internal fun SectionResults(sections: List<SectionStyle>, query: String, onAdd: (List<ScheduleItem>) -> Unit) {
    val trimmed = query.trim()
    val matches = sections.filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) }
    val isNew = trimmed.isNotEmpty() && sections.none { it.name.equals(trimmed, ignoreCase = true) }

    if (matches.isEmpty() && !isNew) {
        EmptyBody(stringResource(Res.string.calendar_no_results), stringResource(Res.string.calendar_pick_empty_hint))
        return
    }
    ScrollableList(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(matches, key = { it.name }) { section ->
            ResultRow(
                title = section.name,
                subtitle = "",
                color = parseHex(section.colorHex),
                onClick = { onAdd(listOf(sectionItem(section.name, section.colorHex))) },
            )
        }
        if (isNew) {
            item(key = "new") {
                ResultRow(
                    title = trimmed,
                    subtitle = stringResource(Res.string.calendar_section_new),
                    color = parseHex(DEFAULT_SECTION_COLOR),
                    onClick = { onAdd(listOf(sectionItem(trimmed, DEFAULT_SECTION_COLOR))) },
                )
            }
        }
    }
}

/**
 * Something that happens up front and never on the outputs -- a poem, a solo, a prayer. The
 * search field holds what it is; [detail] is who, or a note; and the one result row adds it.
 * Nothing to search, because there is nothing to search in: the row is whatever was typed.
 */
@Composable
internal fun MinistryResults(
    title: String,
    onTitle: (String) -> Unit,
    detail: String,
    onDetail: (String) -> Unit,
    duration: String,
    onDuration: (String) -> Unit,
    onAdd: (List<ScheduleItem>) -> Unit,
) {
    val trimmed = title.trim()
    // Enter in any field adds what was typed, once there is a name -- the same as the footer.
    val enterAdds = Modifier.onPreviewKeyEvent { event ->
        val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
        if (trimmed.isNotEmpty() && event.type == KeyEventType.KeyDown && isEnter) {
            onAdd(listOf(ministryItem(trimmed, detail.trim())))
            true
        } else {
            false
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // One row: what, who, and how long -- the three things a slot on the plan is.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(WHAT_WEIGHT), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(stringResource(Res.string.calendar_ministry_what))
                CompactTextField(
                    value = title,
                    onValueChange = onTitle,
                    placeholder = stringResource(Res.string.calendar_ministry_hint),
                    focused = true,
                    modifier = Modifier.fillMaxWidth().then(enterAdds),
                )
            }
            Column(Modifier.weight(WHO_WEIGHT), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(stringResource(Res.string.calendar_ministry_who))
                CompactTextField(
                    value = detail,
                    onValueChange = onDetail,
                    placeholder = stringResource(Res.string.calendar_ministry_detail_hint),
                    modifier = Modifier.fillMaxWidth().then(enterAdds),
                )
            }
            Column(Modifier.width(DURATION_FIELD), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(stringResource(Res.string.calendar_duration))
                CompactTextField(
                    value = duration,
                    onValueChange = onDuration,
                    placeholder = stringResource(Res.string.calendar_duration_hint),
                    errorBorder = duration.isNotBlank() && parseDuration(duration) == null,
                    modifier = Modifier.fillMaxWidth().then(enterAdds),
                )
            }
        }
        if (trimmed.isEmpty()) {
            EmptyBody(
                stringResource(Res.string.calendar_pick_ministry),
                stringResource(Res.string.calendar_ministry_empty_hint),
            )
        } else {
            // A preview of the row as it will look; the footer's Add -- or Enter -- puts it on.
            ResultRow(
                title = trimmed,
                subtitle = detail.trim().ifEmpty { stringResource(Res.string.calendar_ministry_new) },
                badge = stringResource(Res.string.calendar_ministry_add),
                color = MaterialTheme.colorScheme.outline,
                onClick = { onAdd(listOf(ministryItem(trimmed, detail.trim()))) },
            )
        }
    }
}

/**
 * The saved presets, newest first, narrowed to one [kind] and to what the query matches -- the
 * preset's name, or the item's own text, so a scene preset renamed "Opener" is still found by
 * typing the scene's name.
 */
@Composable
internal fun PresetResults(
    presets: List<ItemPreset>,
    kind: PresetKind?,
    query: String,
    previewSources: PreviewSources,
    onAdd: (List<ScheduleItem>) -> Unit,
) {
    // The one preset opened to show what it puts on screen; opening another closes it.
    var expanded by remember { mutableStateOf<String?>(null) }
    if (presets.isEmpty()) {
        EmptyBody(
            stringResource(Res.string.calendar_presets_empty),
            stringResource(Res.string.calendar_presets_empty_sub),
        )
        return
    }
    val q = query.trim()
    val shown = presets.filter { preset ->
        (kind == null || presetKindOf(preset.item) == kind) &&
            (q.isEmpty() || preset.name.contains(q, true) || preset.item.displayText.contains(q, true))
    }
    if (shown.isEmpty()) {
        EmptyBody(stringResource(Res.string.calendar_no_results), "")
        return
    }
    ScrollableList(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        itemsIndexed(shown, key = { _, preset -> preset.id }) { _, preset ->
            val look = lookFor(preset.item)
            val open = expanded == preset.id
            Column {
                ResultRow(
                    title = preset.name,
                    subtitle = preset.item.displayText,
                    color = look.color,
                    onClick = { onAdd(listOf(preset.asRow())) },
                    expanded = open,
                    onToggle = { expanded = if (open) null else preset.id },
                )
                if (open) PresetPreview(item = preset.item, sources = previewSources)
            }
        }
    }
}

@Composable
internal fun EmptyBody(title: String, hint: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = scheme.onSurfaceVariant,
        )
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * One result — the design's row: an icon badge, a title with an optional badge, a second line, and
 * the add affordance on the right.
 */
@Composable
internal fun ResultRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: String? = null,
    color: Color? = null,
    onEdit: (() -> Unit)? = null,
    /** With [onToggle], the row carries a caret that opens a preview beneath it. */
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = color ?: scheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .raisedKey(SheetMetrics.cardRadius, elevationPalette().key, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier.size(RESULT_ICON).clip(RoundedCornerShape(7.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(scheme.primary.copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp),
                    ) {
                        Text(
                            text = badge.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = scheme.primary,
                        )
                    }
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onEdit != null) {
            SmallIconButton(
                icon = Icons.Filled.Edit,
                description = stringResource(Res.string.calendar_edit_song),
                onClick = onEdit,
                size = ADD_BADGE,
            )
        }
        if (onToggle != null) {
            SmallIconButton(
                icon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                description = stringResource(Res.string.calendar_preview_toggle),
                onClick = onToggle,
                size = ADD_BADGE,
            )
        }
        val addTip = stringResource(Res.string.calendar_pick_add_tip)
        Hint(addTip) {
            Box(
                Modifier.size(ADD_BADGE).clip(RoundedCornerShape(6.dp)).background(scheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = addTip,
                    tint = scheme.primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

private const val WHAT_WEIGHT = 2f
private const val WHO_WEIGHT = 1.4f
private val DURATION_FIELD = 92.dp
