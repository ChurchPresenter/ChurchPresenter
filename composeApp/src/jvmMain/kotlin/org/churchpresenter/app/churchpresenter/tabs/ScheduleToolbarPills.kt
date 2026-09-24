package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.ic_folder
import churchpresenter.composeapp.generated.resources.ic_label
import churchpresenter.composeapp.generated.resources.ic_redo
import churchpresenter.composeapp.generated.resources.ic_remove
import churchpresenter.composeapp.generated.resources.ic_save
import churchpresenter.composeapp.generated.resources.ic_undo
import churchpresenter.composeapp.generated.resources.open_calendar_manager
import churchpresenter.composeapp.generated.resources.planning_center_import_title
import churchpresenter.composeapp.generated.resources.schedule_density_compact
import churchpresenter.composeapp.generated.resources.schedule_density_detailed
import churchpresenter.composeapp.generated.resources.schedule_density_normal
import churchpresenter.composeapp.generated.resources.tooltip_add_label
import churchpresenter.composeapp.generated.resources.tooltip_clear_schedule
import churchpresenter.composeapp.generated.resources.tooltip_new_schedule
import churchpresenter.composeapp.generated.resources.tooltip_open_schedule
import churchpresenter.composeapp.generated.resources.tooltip_redo
import churchpresenter.composeapp.generated.resources.tooltip_redo_unbound
import churchpresenter.composeapp.generated.resources.tooltip_save_schedule
import churchpresenter.composeapp.generated.resources.tooltip_schedule_zoom_in
import churchpresenter.composeapp.generated.resources.tooltip_schedule_zoom_out
import churchpresenter.composeapp.generated.resources.tooltip_undo
import churchpresenter.composeapp.generated.resources.tooltip_undo_unbound
import org.churchpresenter.app.churchpresenter.composables.ToolbarKey
import org.churchpresenter.app.churchpresenter.composables.ToolbarKeyStyle
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.utils.label
import org.churchpresenter.app.churchpresenter.utils.ScheduleDensity
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.sunken

// The pills of the Schedule header's two rows -- each group of its toolbar, and the zoom pill --
// drawn by ScheduleHeader in the order TOOLBAR_GROUPS gives them.

internal fun ScheduleToolbarButton.shownIn(hiddenButtons: Set<String>) = name !in hiddenButtons

/** The zoom pill: `-`, the density's name, `+`. */
@Composable
internal fun ScheduleZoomPill(
    density: ScheduleDensity,
    canZoomOut: Boolean,
    canZoomIn: Boolean,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
) {
    Row(
        modifier = Modifier
            .sunken(RoundedCornerShape(10.dp), elevationPalette())
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(
            painter = painterResource(Res.drawable.ic_remove),
            text = stringResource(Res.string.tooltip_schedule_zoom_out),
            onClick = onZoomOut,
            enabled = canZoomOut,
            buttonSize = 24.dp,
            iconSize = 13.dp,
            style = ToolbarKeyStyle.RAISED,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        val compactName = stringResource(Res.string.schedule_density_compact)
        val normalName = stringResource(Res.string.schedule_density_normal)
        val detailedName = stringResource(Res.string.schedule_density_detailed)

        val densityName = when (density) {
            ScheduleDensity.EXTRA_COMPACT, ScheduleDensity.COMPACT -> compactName
            ScheduleDensity.NORMAL -> normalName
            ScheduleDensity.DETAILED, ScheduleDensity.EXTRA_DETAILED -> detailedName
        }
        Box(
            modifier = Modifier.padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            // The three names are laid out invisibly so the pill keeps one width whichever is shown.
            listOf(compactName, normalName, detailedName).forEach { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alpha(0f).clearAndSetSemantics {}
                )
            }
            Text(
                text = densityName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
        ToolbarButton(
            painter = painterResource(Res.drawable.ic_add),
            text = stringResource(Res.string.tooltip_schedule_zoom_in),
            onClick = onZoomIn,
            enabled = canZoomIn,
            buttonSize = 24.dp,
            iconSize = 13.dp,
            style = ToolbarKeyStyle.RAISED,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** New, Open, Save and Clear — the first group of the toolbar pill. */
@Composable
internal fun ScheduleFileButtons(
    hiddenButtons: Set<String>,
    onNewSchedule: () -> Unit,
    onOpenSchedule: () -> Unit,
    onSaveSchedule: () -> Unit,
    onClearSchedule: () -> Unit,
    canClear: Boolean = true,
) {
    if (ScheduleToolbarButton.NEW.shownIn(hiddenButtons)) {
        ToolbarButton(
            painterResource(Res.drawable.ic_add), stringResource(Res.string.tooltip_new_schedule), onNewSchedule,
        )
    }
    if (ScheduleToolbarButton.OPEN.shownIn(hiddenButtons)) {
        ToolbarButton(
            painterResource(Res.drawable.ic_folder), stringResource(Res.string.tooltip_open_schedule), onOpenSchedule,
        )
    }
    if (ScheduleToolbarButton.SAVE.shownIn(hiddenButtons)) {
        ToolbarButton(
            painterResource(Res.drawable.ic_save), stringResource(Res.string.tooltip_save_schedule), onSaveSchedule,
        )
    }
    if (ScheduleToolbarButton.CLEAR.shownIn(hiddenButtons)) {
        ToolbarButton(
            painter = painterResource(Res.drawable.ic_delete),
            text = stringResource(Res.string.tooltip_clear_schedule),
            onClick = onClearSchedule,
            enabled = canClear,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/** Undo and Redo, with the bound shortcut in the tooltip when there is one. */
@Composable
internal fun ScheduleHistoryButtons(
    hiddenButtons: Set<String>,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shortcuts = LocalShortcuts.current
        val undoKeys = shortcuts.label(ShortcutAction.UNDO)
        val redoKeys = shortcuts.label(ShortcutAction.REDO)
        if (ScheduleToolbarButton.UNDO.shownIn(hiddenButtons)) {
            ToolbarButton(
                painter = painterResource(Res.drawable.ic_undo),
                text = if (undoKeys.isEmpty()) stringResource(Res.string.tooltip_undo_unbound)
                       else stringResource(Res.string.tooltip_undo, undoKeys),
                onClick = onUndo,
                modifier = Modifier.testTag(ScheduleToolbarTags.UNDO),
                enabled = canUndo,
            )
        }
        if (ScheduleToolbarButton.REDO.shownIn(hiddenButtons)) {
            ToolbarButton(
                painter = painterResource(Res.drawable.ic_redo),
                text = if (redoKeys.isEmpty()) stringResource(Res.string.tooltip_redo_unbound)
                       else stringResource(Res.string.tooltip_redo, redoKeys),
                onClick = onRedo,
                modifier = Modifier.testTag(ScheduleToolbarTags.REDO),
                enabled = canRedo,
            )
        }
    }
}

/** Add Label, the Planning Center import and the Calendar Manager — the last group of the pill. */
@Composable
internal fun SchedulePlanningButtons(
    hiddenButtons: Set<String>,
    onAddLabel: () -> Unit,
    onImportPlanningCenter: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    if (ScheduleToolbarButton.ADD_LABEL.shownIn(hiddenButtons)) {
        ToolbarButton(
            painterResource(Res.drawable.ic_label), stringResource(Res.string.tooltip_add_label), onAddLabel,
        )
    }
    if (ScheduleToolbarButton.PLANNING_CENTER.shownIn(hiddenButtons)) {
        ToolbarButton(
            painter = rememberVectorPainter(Icons.Default.CloudDownload),
            text = stringResource(Res.string.planning_center_import_title),
            onClick = onImportPlanningCenter,
        )
    }
    if (ScheduleToolbarButton.CALENDAR.shownIn(hiddenButtons)) {
        ToolbarButton(
            painter = rememberVectorPainter(Icons.Default.CalendarMonth),
            text = stringResource(Res.string.open_calendar_manager),
            onClick = onOpenCalendar,
        )
    }
}

/** One toolbar icon at the toolbar's size: flat in its strip, lit on hover, dimmed when disabled. */
@Composable
private fun ToolbarButton(
    painter: Painter,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = 26.dp,
    iconSize: Dp = 14.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: ToolbarKeyStyle = ToolbarKeyStyle.FLAT,
) {
    ToolbarKey(
        painter = painter,
        text = text,
        onClick = onClick,
        modifier = modifier,
        style = style,
        enabled = enabled,
        buttonSize = buttonSize,
        iconSize = iconSize,
        tint = tint,
    )
}
