package org.churchpresenter.schedule

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import org.churchpresenter.strings.generated.resources.edit_label
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.strings.generated.resources.tooltip_row_actions
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.calendar.model.RowClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import org.churchpresenter.sharedui.composables.finalPassCombinedClickable
import org.churchpresenter.sharedui.utils.label
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_edit
import org.churchpresenter.icons.generated.resources.ic_play
import org.churchpresenter.icons.generated.resources.ic_note
import org.churchpresenter.strings.generated.resources.tooltip_note
import org.churchpresenter.strings.generated.resources.tooltip_go_live
import org.churchpresenter.strings.generated.resources.tooltip_move_down
import org.churchpresenter.strings.generated.resources.tooltip_move_up
import org.churchpresenter.strings.generated.resources.tooltip_remove
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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

const val SCHEDULE_ROW_CARD_TAG = "schedule_row_card"

/** The coloured bar down the left edge of a row — a label's colour, or the selection. */
internal const val SCHEDULE_ROW_ACCENT_TAG = "schedule_row_accent"

/** Where the accent bar sits: [ACCENT_START] in from the card's left edge, [ACCENT_WIDTH] wide. */
internal val ACCENT_START = 3.dp
internal val ACCENT_WIDTH = 3.dp

/** Its inset from the card's top and bottom, keeping it clear of the border's rounded corners. */
internal val ACCENT_INSET = 4.dp

internal const val SCHEDULE_ROW_ACTIONS_TAG = "schedule_row_actions"

/** The type chip at a row's start, the row's `CenterVertically` reference. */
internal const val SCHEDULE_ROW_TYPE_CHIP_TAG = "schedule_row_type_chip"

/** The type icon inside that 26dp chip. */
internal val SCHEDULE_TYPE_ICON_SIZE = 16.dp

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
    onEditLabel: () -> Unit,
    hasActions: Boolean = false,
    onEditActions: () -> Unit = {},
) {
    val actionSize = if (isSection) SECTION_ACTION_BUTTON_SIZE else ACTION_BUTTON_SIZE
    val actionIcon = if (isSection) SECTION_ACTION_ICON_SIZE else ACTION_ICON_SIZE

    @Composable
    fun removeButton() {
        ScheduleRowActionButton(
            painter = painterResource(IconRes.drawable.ic_close),
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
        painter = painterResource(IconRes.drawable.ic_arrow_up),
        text = stringResource(Res.string.tooltip_move_up),
        onClick = onMoveUp,
        buttonSize = actionSize,
        iconSize = actionIcon,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ScheduleRowActionButton(
        painter = painterResource(IconRes.drawable.ic_arrow_down),
        text = stringResource(Res.string.tooltip_move_down),
        onClick = onMoveDown,
        buttonSize = actionSize,
        iconSize = actionIcon,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ScheduleRowActionButton(
        painter = painterResource(IconRes.drawable.ic_note),
        text = stringResource(Res.string.tooltip_note),
        onClick = onToggleNote,
        buttonSize = actionSize,
        iconSize = actionIcon,
        iconTint = if (note.isNotEmpty() || noteExpanded) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (!isSection && LocalShowControlEnabled.current) {
        ScheduleRowActionButton(
            painter = rememberVectorPainter(Icons.Outlined.Bolt),
            text = stringResource(Res.string.tooltip_row_actions),
            onClick = onEditActions,
            modifier = Modifier.testTag(SCHEDULE_ROW_ACTIONS_BUTTON_TAG),
            buttonSize = actionSize,
            iconSize = actionIcon,
            iconTint = if (hasActions) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (!removeFirst) removeButton()

    if (isSection) {
        ScheduleRowActionButton(
            painter = painterResource(IconRes.drawable.ic_edit),
            text = stringResource(Res.string.edit_label),
            onClick = onEditLabel,
            modifier = Modifier.padding(start = 2.dp),
            buttonSize = actionSize,
            iconSize = SECTION_ACTION_ICON_SIZE,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        ScheduleRowActionButton(
            painter = painterResource(IconRes.drawable.ic_play),
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
    onNoteChanged: (String) -> Unit = {},
    /** What this row does when it goes live -- see `docs/SHOW_CONTROL.md`, Cue actions. */
    actions: List<Action> = emptyList(),
    /** Every row of the schedule, for the actions that name one. */
    rows: List<ScheduleItem> = emptyList(),
    onActionsChanged: (List<Action>) -> Unit = {},
) {
    var editingActions by remember(item.id) { mutableStateOf(false) }
    val interactionSource = remember(item.id) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val actionsAlpha by animateFloatAsState(if (hovered) 1f else 0f, label = "scheduleRowActionsAlpha")

    var noteExpanded by remember(item.id) { mutableStateOf(false) }
    var noteText by remember(item.id) { mutableStateOf(note) }
    LaunchedEffect(note) { if (noteText != note) noteText = note }

    val isSection = item is ScheduleItem.LabelItem
    val colors = scheduleRowColors(item, isSelected)
    val cardBg = colors.card

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
                .border(1.dp, colors.border, CARD_SHAPE)
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
                ScheduleRowDragHandle(item, isSection, dragHandleModifier)

                ScheduleRowTitle(
                    item = item,
                    look = ScheduleRowTitleLook(density, isSelected, timing, clock, colors.sectionText),
                    legacyRowActions = legacyRowActions,
                    note = note,
                    noteExpanded = noteExpanded,
                    actionsAlpha = actionsAlpha,
                    cardBg = cardBg,
                    onSelect = onSelect,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onToggleNote = { noteExpanded = !noteExpanded },
                    onRemove = onRemove,
                    onPresent = onPresent,
                    onEditLabel = onEditLabel,
                    modifier = Modifier.weight(1f),
                    hasActions = actions.isNotEmpty(),
                    onEditActions = { editingActions = true },
                )
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
                    hasActions = actions.isNotEmpty(),
                    onEditActions = { editingActions = true },
                )
            }

            ScheduleRowFooter(
                RowNote(note, noteExpanded, noteText), { noteText = it }, onNoteChanged, { noteExpanded = it },
                if (LocalShowControlEnabled.current) actions else emptyList(), rows,
                onEditActions = { editingActions = true },
            )
        }

        ScheduleRowAccent(colors.accent)
    }
    if (editingActions) {
        RowActionsDialog(item, actions, rows, onSave = onActionsChanged, onDismiss = { editingActions = false })
    }
}

/**
 * The buttons that fade in over the right-hand end of a row while the pointer is on it.
 *
 * [alpha] is the hover animation, and the row is still clickable underneath at any alpha — the
 * overlay only draws; it does not take the pointer.
 */
@Composable
internal fun BoxScope.ScheduleRowHoverActions(
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
    hasActions: Boolean = false,
    onEditActions: () -> Unit = {},
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
        onEditLabel = onEditLabel,
        hasActions = hasActions,
        onEditActions = onEditActions,
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
    hasActions: Boolean = false,
    onEditActions: () -> Unit = {},
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
            onEditLabel = onEditLabel,
            hasActions = hasActions,
            onEditActions = onEditActions,
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

internal const val SELECTED_CARD_TINT_DARK = 0.18f
internal const val SELECTED_CARD_TINT_LIGHT = 0.10f
