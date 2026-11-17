package org.churchpresenter.schedule

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.calendar.model.RowClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.sharedui.composables.initialPassCombinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_drag_dots
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.theme.isDarkScheme
import org.churchpresenter.sharedui.utils.Utils
import androidx.compose.ui.graphics.lerp

/** The grip a row is dragged by, and the chip naming what kind of row it is -- or its [thumbnail]. */
@Composable
internal fun ScheduleRowDragHandle(
    item: ScheduleItem,
    isSection: Boolean,
    dragHandleModifier: Modifier,
    thumbnail: ImageBitmap?,
    density: ScheduleDensity,
    /** Thumbnails are on: a chip takes a tile's width too, so every title starts at one x. */
    thumbnailSlot: Boolean,
) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .then(dragHandleModifier)
        ) {
            Icon(
                painter = painterResource(IconRes.drawable.ic_drag_dots),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .width(4.dp)
                    .height(16.dp)
            )
            if (!isSection) {
                val (chipBg, chipFg) = scheduleChipColors(scheduleItemPaletteIndex(item))
                if (thumbnail != null) {
                    ScheduleRowThumbnail(item, thumbnail, density, chipBg, chipFg)
                } else Box(
                    modifier = if (thumbnailSlot) Modifier.width(density.thumbnailWidth()) else Modifier,
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .testTag(SCHEDULE_ROW_TYPE_CHIP_TAG)
                            .size(26.dp)
                            .background(chipBg, AppShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = scheduleItemIcon(item),
                            contentDescription = null,
                            tint = chipFg,
                            modifier = Modifier.size(SCHEDULE_TYPE_ICON_SIZE)
                        )
                    }
                }
            }
        }
}

/** A row's thumbnail tile (#1050). */
internal const val SCHEDULE_ROW_THUMBNAIL_TAG = "schedule_row_thumbnail"

/** How wide the thumbnail tile is at each density; it is always 16:9. */
internal fun ScheduleDensity.thumbnailWidth(): Dp = when (this) {
    ScheduleDensity.EXTRA_COMPACT -> 34.dp
    ScheduleDensity.COMPACT -> 40.dp
    ScheduleDensity.NORMAL -> 56.dp
    ScheduleDensity.DETAILED -> 72.dp
    ScheduleDensity.EXTRA_DETAILED -> 96.dp
}

private const val THUMBNAIL_ASPECT = 16f / 9f
private val THUMBNAIL_BADGE_SIZE = 14.dp
private val THUMBNAIL_BADGE_ICON_SIZE = 10.dp

/**
 * [thumbnail] cropped to a 16:9 tile, with the row's type in a small badge in its corner so the
 * kind still reads at a glance -- the chip it stands in for says nothing else.
 */
@Composable
private fun ScheduleRowThumbnail(
    item: ScheduleItem,
    thumbnail: ImageBitmap,
    density: ScheduleDensity,
    chipBg: Color,
    chipFg: Color,
) {
    val width = density.thumbnailWidth()
    Box(
        modifier = Modifier
            .testTag(SCHEDULE_ROW_THUMBNAIL_TAG)
            .size(width, width / THUMBNAIL_ASPECT)
            .clip(AppShape(5.dp))
            .background(Color.Black)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), AppShape(5.dp))
    ) {
        Image(
            bitmap = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(THUMBNAIL_BADGE_SIZE)
                .background(chipBg, AppShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = scheduleItemIcon(item),
                contentDescription = null,
                tint = chipFg,
                modifier = Modifier.size(THUMBNAIL_BADGE_ICON_SIZE)
            )
        }
    }
}

/** What the title line draws an item with: its density, selection, timing and a label's ink. */
internal class ScheduleRowTitleLook(
    val density: ScheduleDensity,
    val isSelected: Boolean,
    val timing: RowTiming,
    val clock: RowClock?,
    val sectionText: Color,
)

/** The row's title -- a label's name or an item's content -- with the hover actions over its end. */
@Composable
internal fun ScheduleRowTitle(
    item: ScheduleItem,
    look: ScheduleRowTitleLook,
    legacyRowActions: Boolean,
    note: String,
    noteExpanded: Boolean,
    actionsAlpha: Float,
    cardBg: Color,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleNote: () -> Unit,
    onRemove: () -> Unit,
    onPresent: () -> Unit,
    onEditLabel: () -> Unit,
    modifier: Modifier = Modifier,
    hasActions: Boolean = false,
    onEditActions: () -> Unit = {},
) {
    val isSection = item is ScheduleItem.LabelItem
        Box(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .initialPassCombinedClickable(
                        onClick = { onSelect() },
                        onDoubleClick = if (!isSection) { { onPresent() } } else null
                    ),

                verticalArrangement = Arrangement.Center
            ) {
                if (isSection) {
                    Text(
                        text = item.displayText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = look.sectionText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    ScheduleItemContent(
                        item = item,
                        density = look.density,
                        isSelected = look.isSelected,
                        timing = look.timing,
                        clock = look.clock,
                    )
                }
            }

            if (!legacyRowActions) {
                ScheduleRowHoverActions(
                    isSection = isSection,
                    note = note,
                    noteExpanded = noteExpanded,
                    alpha = actionsAlpha,
                    cardBg = cardBg,
                    onSelect = onSelect,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onToggleNote = onToggleNote,
                    onRemove = onRemove,
                    onPresent = onPresent,
                    onEditLabel = onEditLabel,
                    hasActions = hasActions,
                    onEditActions = onEditActions,
                )
            }
        }
}

@Composable
internal fun BoxScope.ScheduleRowAccent(leftAccent: Color) {
    // The accent marks the whole item, so it is sized from the card rather than from the
    // title line it used to sit in: the legacy action line, the note preview and the note
    // editor are all siblings of that line, and each one left it a short stub floating at the
    // top of a taller card. matchParentSize takes the card's real measured height every frame
    // -- so it follows the note editor open instead of jumping -- while adding no constraint
    // of its own, and it is declared last because the card paints an opaque background that an
    // accent underneath would be hidden by. An inert Box registers no pointer input, so hover
    // and click still reach the row.
    Box(
        modifier = Modifier
            .matchParentSize()
            .padding(start = ACCENT_START, top = ACCENT_INSET, bottom = ACCENT_INSET)
    ) {
        Box(
            modifier = Modifier
                .width(ACCENT_WIDTH)
                .fillMaxHeight()
                .testTag(SCHEDULE_ROW_ACCENT_TAG)
                .background(leftAccent, AppShape(2.dp))
        )
    }
}

/** A row's card, border and accent colors, and a label's ink -- a label in its own colors. */
internal class ScheduleRowColors(val card: Color, val sectionText: Color, val border: Color, val accent: Color)

@Composable
internal fun scheduleRowColors(item: ScheduleItem, isSelected: Boolean): ScheduleRowColors {
    val isSection = item is ScheduleItem.LabelItem
    val sectionAccent = if (item is ScheduleItem.LabelItem) Utils.parseHexColor(item.textColor) else Color.Unspecified

    val cardBg = when {
        isSection -> Utils.parseHexColor(item.backgroundColor)
        // Tinted toward the accent, and opaque so the raised gradient still reads.
        isSelected -> lerp(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.primary,
            if (isDarkScheme(MaterialTheme.colorScheme)) SELECTED_CARD_TINT_DARK else SELECTED_CARD_TINT_LIGHT,
        )
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    val sectionText = if (isSection) Utils.ensureContrast(sectionAccent, cardBg, minRatio = 7.0) else sectionAccent
    val cardBorder = when {
        isSection -> sectionAccent.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val leftAccent = when {
        isSection -> sectionAccent
        else -> Color.Transparent
    }
    return ScheduleRowColors(cardBg, sectionText, cardBorder, leftAccent)
}

/** A row's note as the card holds it: the saved [text], whether it is open, and what is being typed. */
internal data class RowNote(val text: String, val expanded: Boolean, val typing: String)
