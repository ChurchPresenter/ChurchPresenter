package org.churchpresenter.calendar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_pick_all_books
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_all
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_announcements
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_lower_thirds
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_media
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_other
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_presentations
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_scenes
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_slides
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_timers
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.elevationPalette

private val SCOPE_LIST_HEIGHT = 196.dp

@Composable
internal fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = elevationPalette()
    val fill = if (selected) palette.selected else palette.key
    Box(
        Modifier
            .height(26.dp)
            .raisedKey(RoundedCornerShape(7.dp), fill, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = fill.ink,
        )
    }
}

/** The kinds a preset can be, for narrowing the Presets tab -- one per content type it can hold. */
internal enum class PresetKind { SLIDES, PRESENTATIONS, MEDIA, TIMERS, ANNOUNCEMENTS, SCENES, LOWER_THIRDS, OTHER }

internal fun presetKindOf(item: ScheduleItem): PresetKind = when (item) {
    is ScheduleItem.PictureItem -> PresetKind.SLIDES
    is ScheduleItem.PresentationItem -> PresetKind.PRESENTATIONS
    is ScheduleItem.MediaItem -> PresetKind.MEDIA
    is ScheduleItem.AnnouncementItem -> if (item.isTimer) PresetKind.TIMERS else PresetKind.ANNOUNCEMENTS
    is ScheduleItem.SceneItem -> PresetKind.SCENES
    is ScheduleItem.LowerThirdItem -> PresetKind.LOWER_THIRDS
    else -> PresetKind.OTHER
}

@Composable
private fun presetKindLabel(kind: PresetKind): String = stringResource(
    when (kind) {
        PresetKind.SLIDES -> Res.string.calendar_preset_kind_slides
        PresetKind.PRESENTATIONS -> Res.string.calendar_preset_kind_presentations
        PresetKind.MEDIA -> Res.string.calendar_preset_kind_media
        PresetKind.TIMERS -> Res.string.calendar_preset_kind_timers
        PresetKind.ANNOUNCEMENTS -> Res.string.calendar_preset_kind_announcements
        PresetKind.SCENES -> Res.string.calendar_preset_kind_scenes
        PresetKind.LOWER_THIRDS -> Res.string.calendar_preset_kind_lower_thirds
        PresetKind.OTHER -> Res.string.calendar_preset_kind_other
    }
)

/**
 * The preset kind chips: `All` and one per kind that has a preset, each with its count. Only the
 * kinds present -- a row of eight chips for a library of three slideshows says nothing.
 */
@Composable
internal fun PresetKindScope(presets: List<ItemPreset>, selected: PresetKind?, onSelect: (PresetKind?) -> Unit) {
    val counts = remember(presets) { presets.groupingBy { presetKindOf(it.item) }.eachCount() }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PickChip(
            label = stringResource(Res.string.calendar_preset_kind_all) + "  " + presets.size,
            selected = selected == null,
        ) { onSelect(null) }
        PresetKind.entries.forEach { kind ->
            val count = counts[kind] ?: return@forEach
            PickChip(label = presetKindLabel(kind) + "  " + count, selected = selected == kind) {
                onSelect(if (selected == kind) null else kind)
            }
        }
    }
}

/** The song-book scope selector — a library of thousands is unusable without it. */
@Composable
internal fun SongBookScope(songs: List<SongItem>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val counts by remember(songs) {
        derivedStateOf { songs.groupingBy { it.songbook }.eachCount().toList().sortedBy { it.first } }
    }
    val scheme = MaterialTheme.colorScheme
    val label = selected ?: stringResource(Res.string.calendar_pick_all_books)
    val count = if (selected == null) songs.size else counts.firstOrNull { it.first == selected }?.second ?: 0

    Column {
        SettingCard(horizontalPadding = 10.dp, verticalPadding = 7.dp, modifier = Modifier.clickable { open = !open }) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = scheme.onSurfaceVariant,
            )
        }
        // `if (open) { ... }`, never an early `return` here: a bare return inside Column's inline
        // lambda is a *non-local* return out of a composable lambda, which leaves the composition's
        // group structure unbalanced. An early return at the top of a composable function body is
        // fine; one from inside a layout's content is not.
        if (open) {
            // A list, not a Column: capping a Column's height does not make it scroll, so a
            // library with more song books than fit simply lost the ones past the cap.
            ScrollableList(
                modifier = Modifier.padding(top = 4.dp).heightIn(max = SCOPE_LIST_HEIGHT),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "all") {
                    ScopeRow(stringResource(Res.string.calendar_pick_all_books), songs.size, selected == null) {
                        onSelect(null)
                        open = false
                    }
                }
                items(counts, key = { it.first }) { (name, number) ->
                    ScopeRow(name, number, selected == name) {
                        onSelect(name)
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeRow(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val selectedFill = elevationPalette().selected
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                // The chosen scope is a raised row, the way a chosen list row is everywhere else.
                if (selected) {
                    Modifier.raisedKey(RoundedCornerShape(7.dp), selectedFill, onClick = onClick)
                } else {
                    Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) selectedFill.ink else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = if (selected) selectedFill.ink else scheme.onSurfaceVariant,
        )
    }
}
