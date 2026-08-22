package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.content_announcements
import churchpresenter.composeapp.generated.resources.media
import churchpresenter.composeapp.generated.resources.obs_mode_lower_third
import churchpresenter.composeapp.generated.resources.pictures
import churchpresenter.composeapp.generated.resources.presentation
import churchpresenter.composeapp.generated.resources.songs
import churchpresenter.composeapp.generated.resources.stage_monitor_position_center
import churchpresenter.composeapp.generated.resources.stage_monitor_position_middle_left
import churchpresenter.composeapp.generated.resources.stage_monitor_position_middle_right
import churchpresenter.composeapp.generated.resources.stage_monitor_position_top_center
import churchpresenter.composeapp.generated.resources.stage_monitor_quadrant_clock
import churchpresenter.composeapp.generated.resources.stage_monitor_quadrant_next
import churchpresenter.composeapp.generated.resources.stage_monitor_quadrant_notes
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_bottom_center
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_bottom_left
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_bottom_right
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_full_screen
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_none
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_top_left
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_top_right
import churchpresenter.composeapp.generated.resources.tab_canvas
import churchpresenter.composeapp.generated.resources.tab_dictionary
import churchpresenter.composeapp.generated.resources.tab_qa
import churchpresenter.composeapp.generated.resources.tab_stt
import churchpresenter.composeapp.generated.resources.tab_web
import churchpresenter.composeapp.generated.resources.stage_monitor_layout_classic
import churchpresenter.composeapp.generated.resources.stage_monitor_layout_left_right
import churchpresenter.composeapp.generated.resources.stage_monitor_layout_quad
import churchpresenter.composeapp.generated.resources.stage_monitor_layout_three_rows
import churchpresenter.composeapp.generated.resources.stage_monitor_layout_top_bottom
import churchpresenter.composeapp.generated.resources.stage_monitor_layout_top_four_below
import churchpresenter.composeapp.generated.resources.stage_monitor_layout_top_three_below
import churchpresenter.composeapp.generated.resources.stage_monitor_layout_top_two_below
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_count
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_n
import org.churchpresenter.app.churchpresenter.composables.TvScreenBox
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorLayout
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.settings.toStyleZone
import org.jetbrains.compose.resources.stringResource

private const val VARIANT_CARD_WIDTH = 132
private const val VARIANT_GRID_HEIGHT = 80
private const val FIRST_CELL_ALPHA = 0.75f
private const val BEZEL_ALPHA = 0.38f
private const val PLAIN_CELL_ALPHA = 0.40f

@Composable
internal fun layoutLabel(layout: StageMonitorLayout): String = when (layout) {
    StageMonitorLayout.CLASSIC -> stringResource(Res.string.stage_monitor_layout_classic)
    StageMonitorLayout.TOP_BOTTOM -> stringResource(Res.string.stage_monitor_layout_top_bottom)
    StageMonitorLayout.LEFT_RIGHT -> stringResource(Res.string.stage_monitor_layout_left_right)
    StageMonitorLayout.TOP_TWO_BELOW -> stringResource(Res.string.stage_monitor_layout_top_two_below)
    StageMonitorLayout.THREE_ROWS -> stringResource(Res.string.stage_monitor_layout_three_rows)
    StageMonitorLayout.QUAD -> stringResource(Res.string.stage_monitor_layout_quad)
    StageMonitorLayout.TOP_THREE_BELOW -> stringResource(Res.string.stage_monitor_layout_top_three_below)
    StageMonitorLayout.TOP_FOUR_BELOW -> stringResource(Res.string.stage_monitor_layout_top_four_below)
}

/** Slots are ordered, not positional — where Zone 3 sits depends on the layout drawing it. */
@Composable
internal fun slotLabel(slot: StageMonitorStyleZone): String =
    stringResource(Res.string.stage_monitor_zone_n, StageMonitorStyleZone.entries.indexOf(slot) + 1)

/**
 * Zone count tabs over the arrangements offering that many, each drawn as a miniature of itself.
 *
 * Picking a count moves to its first arrangement, so the cards below always belong to the tab
 * above them even when the layout in force has a different number of zones.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StageMonitorLayoutPicker(
    layout: StageMonitorLayout,
    onPick: (StageMonitorLayout) -> Unit,
) {
    val counts = StageMonitorLayout.zoneCounts()
    val activeCount = layout.slots.size
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(30.dp)) {
        counts.forEachIndexed { index, count ->
            SegmentedButton(
                selected = count == activeCount,
                onClick = { StageMonitorLayout.withZoneCount(count).firstOrNull()?.let(onPick) },
                shape = segmentedItemShape(index = index, count = counts.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                icon = {},
            ) {
                Text(
                    stringResource(Res.string.stage_monitor_zone_count, count),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StageMonitorLayout.withZoneCount(activeCount).forEach { variant ->
            LayoutVariantCard(variant = variant, selected = variant == layout, onPick = { onPick(variant) })
        }
    }
}

@Composable
private fun LayoutVariantCard(
    variant: StageMonitorLayout,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(VARIANT_CARD_WIDTH.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onPick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        TvScreenBox(
            modifier = Modifier.fillMaxWidth().height(VARIANT_GRID_HEIGHT.dp),
            bezelColor = stageMonitorBezelColor(),
            screenColor = Color.Black,
        ) {
            LayoutMiniature(variant = variant, modifier = Modifier.fillMaxSize())
        }
        Text(
            text = layoutLabel(variant),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The layout's own rows and weights, drawn small — the same grid the monitor will draw.
 *
 * The zones are always white on the black screen, whichever card is picked and whatever the theme:
 * a theme colour here reads as "off" on one of the two, and the picked card is already said by its
 * border and label.
 */
@Composable
internal fun LayoutMiniature(
    variant: StageMonitorLayout,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        variant.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(row.weight),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                row.cells.forEach { cell ->
                    // The first slot is drawn brighter so a card says which corner Zone 1 is.
                    val alpha = if (cell.slot == variant.slots.first()) FIRST_CELL_ALPHA else PLAIN_CELL_ALPHA
                    Box(
                        modifier = Modifier
                            .weight(cell.weight)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}

@Composable
internal fun OffGridZoneChip(
    label: String,
    contents: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = contents.ifBlank { "—" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
internal fun contentTypeLabel(type: StageMonitorContentType): String = when (type) {
    StageMonitorContentType.BIBLE -> stringResource(Res.string.bible)
    StageMonitorContentType.SONGS -> stringResource(Res.string.songs)
    StageMonitorContentType.PRESENTATION -> stringResource(Res.string.presentation)
    StageMonitorContentType.PRESENTATION_NOTES -> stringResource(Res.string.stage_monitor_quadrant_notes)
    StageMonitorContentType.PICTURES -> stringResource(Res.string.pictures)
    StageMonitorContentType.MEDIA -> stringResource(Res.string.media)
    StageMonitorContentType.LOWER_THIRD -> stringResource(Res.string.obs_mode_lower_third)
    StageMonitorContentType.WEB -> stringResource(Res.string.tab_web)
    StageMonitorContentType.STT -> stringResource(Res.string.tab_stt)
    StageMonitorContentType.CANVAS -> stringResource(Res.string.tab_canvas)
    StageMonitorContentType.QA -> stringResource(Res.string.tab_qa)
    StageMonitorContentType.DICTIONARY -> stringResource(Res.string.tab_dictionary)
    StageMonitorContentType.CLOCK -> stringResource(Res.string.stage_monitor_quadrant_clock)
    StageMonitorContentType.ANNOUNCEMENT_TEXT -> stringResource(Res.string.content_announcements)
    StageMonitorContentType.NEXT -> stringResource(Res.string.stage_monitor_quadrant_next)
}

@Composable
internal fun zoneLabel(zone: StageMonitorZone): String = when (zone) {
    StageMonitorZone.FULL_SCREEN -> stringResource(Res.string.stage_monitor_zone_full_screen)
    StageMonitorZone.NONE -> stringResource(Res.string.stage_monitor_zone_none)
    else -> slotLabel(zone.toStyleZone() ?: StageMonitorStyleZone.A)
}

@Composable
internal fun metronomePositionLabel(position: MetronomePosition): String = when (position) {
    MetronomePosition.NONE -> stringResource(Res.string.stage_monitor_zone_none)
    MetronomePosition.TOP_LEFT -> stringResource(Res.string.stage_monitor_zone_top_left)
    MetronomePosition.TOP_CENTER -> stringResource(Res.string.stage_monitor_position_top_center)
    MetronomePosition.TOP_RIGHT -> stringResource(Res.string.stage_monitor_zone_top_right)
    MetronomePosition.MIDDLE_LEFT -> stringResource(Res.string.stage_monitor_position_middle_left)
    MetronomePosition.CENTER -> stringResource(Res.string.stage_monitor_position_center)
    MetronomePosition.MIDDLE_RIGHT -> stringResource(Res.string.stage_monitor_position_middle_right)
    MetronomePosition.BOTTOM_LEFT -> stringResource(Res.string.stage_monitor_zone_bottom_left)
    MetronomePosition.BOTTOM_CENTER -> stringResource(Res.string.stage_monitor_zone_bottom_center)
    MetronomePosition.BOTTOM_RIGHT -> stringResource(Res.string.stage_monitor_zone_bottom_right)
}

/** A bezel that stays visible against the settings surface in both themes. */
@Composable
internal fun stageMonitorBezelColor(): Color =
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = BEZEL_ALPHA)
