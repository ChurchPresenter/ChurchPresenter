package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.calendar.model.RowClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.shape.RoundedCornerShape
import org.churchpresenter.app.churchpresenter.composables.finalPassCombinedClickable
import org.churchpresenter.app.churchpresenter.utils.label
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_drag_dots
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_check
import churchpresenter.composeapp.generated.resources.ic_note
import churchpresenter.composeapp.generated.resources.schedule_note_placeholder
import churchpresenter.composeapp.generated.resources.tooltip_note
import churchpresenter.composeapp.generated.resources.tooltip_note_clear
import churchpresenter.composeapp.generated.resources.tooltip_note_done
import churchpresenter.composeapp.generated.resources.tooltip_edit_label
import churchpresenter.composeapp.generated.resources.tooltip_go_live
import churchpresenter.composeapp.generated.resources.tooltip_move_down
import churchpresenter.composeapp.generated.resources.tooltip_move_up
import churchpresenter.composeapp.generated.resources.tooltip_remove
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.utils.ScheduleDensity
import org.churchpresenter.app.churchpresenter.utils.scheduleShowDetailLine
import org.churchpresenter.app.churchpresenter.viewmodel.scheduleItemGlyph
import org.churchpresenter.app.churchpresenter.viewmodel.scheduleItemPaletteIndex
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.RaisedFill
import androidx.compose.ui.graphics.lerp
private const val PALETTE_SIZE = 4
private const val GRADIENT_MIDPOINT = 0.35f

internal fun ScheduleDensity.rowPadding(): Dp = when (this) {
    ScheduleDensity.EXTRA_COMPACT -> 2.dp
    ScheduleDensity.COMPACT -> 4.dp
    ScheduleDensity.NORMAL -> 7.dp
    ScheduleDensity.DETAILED -> 9.dp
    ScheduleDensity.EXTRA_DETAILED -> 13.dp
}

internal fun ScheduleDensity.rowMinHeight(): Dp = when (this) {
    ScheduleDensity.EXTRA_COMPACT -> 26.dp
    ScheduleDensity.COMPACT -> 32.dp
    ScheduleDensity.NORMAL -> 42.dp
    ScheduleDensity.DETAILED -> 54.dp
    ScheduleDensity.EXTRA_DETAILED -> 68.dp
}

@Composable
internal fun scheduleChipColors(paletteIndex: Int): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (paletteIndex % PALETTE_SIZE) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        2 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.errorContainer to scheme.onErrorContainer
    }
}

internal const val SCHEDULE_ROW_CARD_TAG = "schedule_row_card"

/** The coloured bar down the left edge of a row — a label's colour, or the selection. */
internal const val SCHEDULE_ROW_ACCENT_TAG = "schedule_row_accent"

/** Where the accent bar sits: [ACCENT_START] in from the card's left edge, [ACCENT_WIDTH] wide. */
private val ACCENT_START = 3.dp
private val ACCENT_WIDTH = 3.dp

/** Its inset from the card's top and bottom, keeping it clear of the border's rounded corners. */
private val ACCENT_INSET = 4.dp

internal const val SCHEDULE_ROW_ACTIONS_TAG = "schedule_row_actions"

/** The legacy layout's action line — its own tag so a test can tell the two layouts apart. */
internal const val SCHEDULE_ROW_LEGACY_ACTIONS_TAG = "schedule_row_legacy_actions"

/**
 * The card's action buttons, in the one place both layouts take them from: the hover overlay
 * pinned over the title's right-hand end, and the legacy line under the title.
 *
 * [removeFirst] is what the legacy line asks for — remove alone at the start, everything else
 * pushed to the end, as it sat before the hover overlay replaced it.
 */
@Composable
private fun RowScope.ScheduleRowActionButtons(
    isSection: Boolean,
    note: String,
    noteExpanded: Boolean,
    removeFirst: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleNote: () -> Unit,
    onRemove: () -> Unit,
    onPresent: () -> Unit,
    onEditLabel: () -> Unit
) {
    val actionSize = if (isSection) SECTION_ACTION_BUTTON_SIZE else ACTION_BUTTON_SIZE
    val actionIcon = if (isSection) SECTION_ACTION_ICON_SIZE else ACTION_ICON_SIZE

    @Composable
    fun removeButton() {
        ScheduleRowActionButton(
            painter = painterResource(Res.drawable.ic_close),
            text = stringResource(Res.string.tooltip_remove),
            onClick = onRemove,
            buttonSize = actionSize,
            iconSize = actionIcon,
            iconTint = MaterialTheme.colorScheme.error
        )
    }

    if (removeFirst) {
        removeButton()
        Spacer(modifier = Modifier.weight(1f))
    }
    ScheduleRowActionButton(
        painter = painterResource(Res.drawable.ic_arrow_up),
        text = stringResource(Res.string.tooltip_move_up),
        onClick = onMoveUp,
        buttonSize = actionSize,
        iconSize = actionIcon,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ScheduleRowActionButton(
        painter = painterResource(Res.drawable.ic_arrow_down),
        text = stringResource(Res.string.tooltip_move_down),
        onClick = onMoveDown,
        buttonSize = actionSize,
        iconSize = actionIcon,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ScheduleRowActionButton(
        painter = painterResource(Res.drawable.ic_note),
        text = stringResource(Res.string.tooltip_note),
        onClick = onToggleNote,
        buttonSize = actionSize,
        iconSize = actionIcon,
        iconTint = if (note.isNotEmpty() || noteExpanded) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (!removeFirst) removeButton()

    if (isSection) {
        ScheduleRowActionButton(
            painter = painterResource(Res.drawable.ic_edit),
            text = stringResource(Res.string.tooltip_edit_label),
            onClick = onEditLabel,
            modifier = Modifier.padding(start = 2.dp),
            buttonSize = actionSize,
            iconSize = SECTION_ACTION_ICON_SIZE,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        ScheduleRowActionButton(
            painter = painterResource(Res.drawable.ic_play),
            text = stringResource(Res.string.tooltip_go_live),
            onClick = onPresent,
            modifier = Modifier.padding(start = 2.dp),
            iconSize = 15.dp,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ScheduleItemRow(
    item: ScheduleItem,
    timing: RowTiming = RowTiming.DEFAULT,
    /** When this row is expected to go live, reckoned across the schedule -- see `scheduleClocks`. */
    clock: RowClock? = null,
    dragHandleModifier: Modifier = Modifier,
    density: ScheduleDensity,
    /** Legacy layout: buttons on their own line under the title instead of the hover overlay. */
    legacyRowActions: Boolean = false,
    isSelected: Boolean,
    note: String,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onPresent: () -> Unit,
    onEditLabel: () -> Unit = {},
    onNoteChanged: (String) -> Unit = {}
) {
    val interactionSource = remember(item.id) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val actionsAlpha by animateFloatAsState(if (hovered) 1f else 0f, label = "scheduleRowActionsAlpha")

    var noteExpanded by remember(item.id) { mutableStateOf(false) }
    var noteText by remember(item.id) { mutableStateOf(note) }

    LaunchedEffect(note) {
        if (noteText != note) noteText = note
    }

    val isSection = item is ScheduleItem.LabelItem
    val sectionAccent = if (item is ScheduleItem.LabelItem) Utils.parseHexColor(item.textColor) else Color.Unspecified

    val cardBg = when {
        isSection -> Utils.parseHexColor(item.backgroundColor)
        isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    val sectionText = if (isSection) Utils.ensureContrast(sectionAccent, cardBg, minRatio = 7.0) else sectionAccent
    val cardBorder = when {
        isSection -> sectionAccent.copy(alpha = 0.35f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val leftAccent = when {
        isSection -> sectionAccent
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SCHEDULE_ROW_CARD_TAG)
                .hoverable(interactionSource)
                // Raised off the list in its own color: a touch lighter along the top, a soft
                // shadow, lifted further while selected and a step more under the pointer.
                .raised(
                    CARD_SHAPE,
                    RaisedFill(
                        top = lerp(cardBg, Color.White, CARD_TOP_LIFT),
                        bottom = cardBg,
                        ink = MaterialTheme.colorScheme.onSurface,
                        highlight = Color.Transparent,
                        glow = Color.Black,
                    ),
                    elevationPalette(),
                    hovered = hovered,
                    lift = if (isSelected) CARD_LIFT_SELECTED else CARD_LIFT,
                    moves = false,
                )
                .border(1.dp, cardBorder, CARD_SHAPE)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        // Where the accent used to stand, plus the gap after it: the bar is drawn
                        // over the card now, and this keeps the drag handle where it always was.
                        start = ACCENT_START + ACCENT_WIDTH + 5.dp,
                        end = 6.dp,

                        top = if (isSection) SECTION_ROW_PADDING else density.rowPadding(),
                        bottom = if (isSection) SECTION_ROW_PADDING else density.rowPadding()
                    )
                    .heightIn(min = if (isSection) 0.dp else density.rowMinHeight())

                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .then(dragHandleModifier)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_drag_dots),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .width(4.dp)
                            .height(16.dp)
                    )
                    if (!isSection) {
                        val (chipBg, chipFg) = scheduleChipColors(scheduleItemPaletteIndex(item))
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(chipBg, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = scheduleItemGlyph(item),
                                style = MaterialTheme.typography.bodyMedium,
                                color = chipFg
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {

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
                                color = sectionText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            ScheduleItemContent(
                                item = item,
                                density = density,
                                isSelected = isSelected,
                                timing = timing,
                                clock = clock,
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
                            onToggleNote = { noteExpanded = !noteExpanded },
                            onRemove = onRemove,
                            onPresent = onPresent,
                            onEditLabel = onEditLabel,
                        )
                    }
                }
            }

            if (legacyRowActions) {
                ScheduleRowLegacyActions(
                    isSection = isSection,
                    note = note,
                    noteExpanded = noteExpanded,
                    onSelect = onSelect,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onToggleNote = { noteExpanded = !noteExpanded },
                    onRemove = onRemove,
                    onPresent = onPresent,
                    onEditLabel = onEditLabel,
                )
            }

            if (note.isNotEmpty() && !noteExpanded) {
                ScheduleRowNoteChip(note = note, onEdit = { noteExpanded = true })
            }

            AnimatedVisibility(visible = noteExpanded) {
                ScheduleRowNoteEditor(
                    noteText = noteText,
                    onNoteTextChange = { noteText = it },
                    onCommit = onNoteChanged,
                    onClose = { noteExpanded = false },
                )
            }
        }

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
                    .background(leftAccent, RoundedCornerShape(2.dp))
            )
        }
    }
}

/**
 * The row's buttons on a line of their own — the pre-overlay layout, kept for anyone who prefers
 * it. The hover overlay draws the same [ScheduleRowActionButtons]; only the placement differs.
 */

/**
 * The buttons that fade in over the right-hand end of a row while the pointer is on it.
 *
 * [alpha] is the hover animation, and the row is still clickable underneath at any alpha — the
 * overlay only draws; it does not take the pointer.
 */
@Composable
private fun BoxScope.ScheduleRowHoverActions(
    isSection: Boolean,
    note: String,
    noteExpanded: Boolean,
    alpha: Float,
    cardBg: Color,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleNote: () -> Unit,
    onRemove: () -> Unit,
    onPresent: () -> Unit,
    onEditLabel: () -> Unit,
) {
Row(
    modifier = Modifier
        .align(Alignment.CenterEnd)
        .testTag(SCHEDULE_ROW_ACTIONS_TAG)
        .fillMaxHeight()
        .alpha(alpha)
        .background(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                GRADIENT_MIDPOINT to cardBg.copy(alpha = 0.82f),
                1f to cardBg.copy(alpha = 0.82f)
            )
        )
        .padding(start = 20.dp)
        .finalPassCombinedClickable(
            onClick = { onSelect() },
            onDoubleClick = if (!isSection) { { onPresent() } } else null
        ),
    horizontalArrangement = Arrangement.spacedBy(1.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    ScheduleRowActionButtons(
        isSection = isSection,
        note = note,
        noteExpanded = noteExpanded,
        removeFirst = false,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onToggleNote = onToggleNote,
        onRemove = onRemove,
        onPresent = onPresent,
        onEditLabel = onEditLabel
    )
}
}

@Composable
private fun ScheduleRowLegacyActions(
    isSection: Boolean,
    note: String,
    noteExpanded: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleNote: () -> Unit,
    onRemove: () -> Unit,
    onPresent: () -> Unit,
    onEditLabel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SCHEDULE_ROW_LEGACY_ACTIONS_TAG)
            .padding(start = 12.dp, end = 6.dp, bottom = 2.dp)
            .finalPassCombinedClickable(
                onClick = { onSelect() },
                onDoubleClick = if (!isSection) { { onPresent() } } else null
            ),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScheduleRowActionButtons(
            isSection = isSection,
            note = note,
            noteExpanded = noteExpanded,
            removeFirst = true,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onToggleNote = onToggleNote,
            onRemove = onRemove,
            onPresent = onPresent,
            onEditLabel = onEditLabel
        )
    }
}

/** A note that is written but not being edited: the text, and a pencil to open it. */
@Composable
private fun ScheduleRowNoteChip(note: String, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 38.dp, end = 8.dp, bottom = 7.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f).padding(top = 2.dp, bottom = 2.dp)
        )
        ScheduleRowActionButton(
            painter = painterResource(Res.drawable.ic_edit),
            text = stringResource(Res.string.tooltip_note),
            onClick = onEdit,
            iconSize = 11.dp,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
        )
    }
}

/**
 * The note being edited.
 *
 * [onCommit] is the row's `onNoteChanged`, and is called as the text changes rather than on close:
 * a note half-typed when the app goes down is still a note somebody wrote.
 */
@Composable
private fun ScheduleRowNoteEditor(
    noteText: String,
    onNoteTextChange: (String) -> Unit,
    onCommit: (String) -> Unit,
    onClose: () -> Unit,
) {
    val noteInteractionSource = remember { MutableInteractionSource() }
    val noteFieldFocused by noteInteractionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 38.dp, end = 8.dp, bottom = 7.dp)
            .sunken(
                RoundedCornerShape(7.dp),
                elevationPalette(),
                rim = if (noteFieldFocused) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
            .hoverTint(RoundedCornerShape(7.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = noteText,
            onValueChange = onNoteTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 3,
            interactionSource = noteInteractionSource,
            decorationBox = { innerTextField ->
                Box {
                    if (noteText.isEmpty()) {
                        Text(
                            stringResource(Res.string.schedule_note_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            }
        )
        TooltipIconButton(
            painter = painterResource(Res.drawable.ic_check),
            text = stringResource(Res.string.tooltip_note_done),
            onClick = {
                onCommit(noteText)
                onClose()
            },
            buttonSize = 32.dp,
            iconSize = 15.dp,
            iconTint = MaterialTheme.colorScheme.primary
        )
        TooltipIconButton(
            painter = painterResource(Res.drawable.ic_close),
            text = stringResource(Res.string.tooltip_note_clear),
            onClick = {
                onNoteTextChange("")
                onCommit("")
            },
            modifier = Modifier.padding(end = 4.dp),
            buttonSize = 32.dp,
            iconSize = 15.dp,
            iconTint = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
internal fun ScheduleItemContent(
    item: ScheduleItem,
    density: ScheduleDensity,
    isSelected: Boolean,
    /** How the row runs on its own, from the plan it was loaded from; the default says nothing. */
    timing: RowTiming = RowTiming.DEFAULT,
    /** When this row is expected to go live, reckoned across the schedule -- see `scheduleClocks`. */
    clock: RowClock? = null,
) {
    ScheduleRowTitleLine(item = item, isSelected = isSelected, timing = timing, clock = clock)

    if (!scheduleShowDetailLine(density.percent)) return

    ScheduleRowTimingLine(timing)
    ScheduleRowDetailLine(item = item, density = density)
    ScheduleRowKindChips(item = item, density = density)
}

private val CARD_LIFT = 2.dp
private val CARD_LIFT_SELECTED = 4.dp
private const val CARD_TOP_LIFT = 0.05f
