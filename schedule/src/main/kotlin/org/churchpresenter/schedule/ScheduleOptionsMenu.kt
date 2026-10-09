package org.churchpresenter.schedule

import org.churchpresenter.strings.generated.resources.add_label
import org.churchpresenter.strings.generated.resources.menu_clear_schedule
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_add
import org.churchpresenter.icons.generated.resources.ic_delete
import org.churchpresenter.icons.generated.resources.ic_folder
import org.churchpresenter.icons.generated.resources.ic_label
import org.churchpresenter.icons.generated.resources.ic_redo
import org.churchpresenter.icons.generated.resources.ic_save
import org.churchpresenter.icons.generated.resources.ic_undo
import org.churchpresenter.icons.generated.resources.ic_zoom_in
import org.churchpresenter.strings.generated.resources.open_calendar_manager
import org.churchpresenter.strings.generated.resources.planning_center_import_title
import org.churchpresenter.strings.generated.resources.schedule_icon_size_large
import org.churchpresenter.strings.generated.resources.schedule_icon_size_medium
import org.churchpresenter.strings.generated.resources.schedule_icon_size_small
import org.churchpresenter.strings.generated.resources.schedule_option_icon_size
import org.churchpresenter.strings.generated.resources.schedule_option_item_count
import org.churchpresenter.strings.generated.resources.schedule_option_zoom
import org.churchpresenter.strings.generated.resources.schedule_show_buttons_under_title
import org.churchpresenter.strings.generated.resources.tooltip_schedule_options
import org.churchpresenter.strings.generated.resources.tooltip_redo_unbound
import org.churchpresenter.strings.generated.resources.tooltip_undo_unbound
import org.churchpresenter.strings.generated.resources.tooltip_new_schedule
import org.churchpresenter.strings.generated.resources.tooltip_open_schedule
import org.churchpresenter.strings.generated.resources.tooltip_save_schedule
import org.churchpresenter.sharedui.composables.ToolbarKey
import org.churchpresenter.sharedui.composables.ToolbarKeyStyle
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import org.churchpresenter.sharedui.composables.ContextMenu
import org.churchpresenter.sharedui.composables.ContextMenuDivider
import org.churchpresenter.sharedui.composables.SegmentedButton
import org.churchpresenter.sharedui.composables.SegmentedButtonItem
import org.churchpresenter.sharedui.composables.ToggleMenuHeader
import org.churchpresenter.sharedui.composables.ToggleMenuItem
import org.churchpresenter.sharedui.composables.ToggleMenuSectionLabel
import org.churchpresenter.strings.generated.resources.schedule_option_buttons
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val OPTIONS_MENU_WIDTH = 300.dp
/** Three segments across the menu's inner width. */
private val ICON_SIZE_SEGMENT_WIDTH = 90.dp
/** The last button of each toolbar group: view, file, history, extras. */
private val OPTIONS_GROUP_ENDS = setOf(
    ScheduleToolbarButton.ZOOM, ScheduleToolbarButton.CLEAR, ScheduleToolbarButton.REDO,
)

/**
 * The schedule sidebar's own options menu — the panel-local settings that have no place in the
 * global settings dialog because they describe this panel's layout and are switched while looking
 * at it. Follows the tab-visibility menu in `MainTabArea`: an icon button opening a [ContextMenu]
 * of [ToggleMenuItem]s, each applied immediately.
 */
@Composable
internal fun ScheduleOptionsButton(
    legacyRowActions: Boolean,
    onLegacyRowActionsChange: (Boolean) -> Unit,
    hiddenButtons: Set<String>,
    onToggleButton: (ScheduleToolbarButton) -> Unit,
    toolbarIconSize: ScheduleToolbarIconSize,
    onToolbarIconSizeChange: (ScheduleToolbarIconSize) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        // Styled as the tab-visibility button in `MainDesktop`, not as the toolbar strip's icons:
        // both open a toggle menu of panel-level options, so they read as the same
        // control -- a panel toggle, raised with an accent dot while its menu is open.
        ToolbarKey(
            painter = rememberVectorPainter(Icons.Default.Tune),
            text = stringResource(Res.string.tooltip_schedule_options),
            onClick = { expanded = true },
            modifier = Modifier.testTag(ScheduleToolbarTags.OPTIONS),
            style = ToolbarKeyStyle.PANEL_TOGGLE,
            open = expanded,
            buttonSize = 36.dp,
        )
        ContextMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            width = OPTIONS_MENU_WIDTH,
        ) {
            ScheduleIconSizeSection(
                toolbarIconSize, onToolbarIconSizeChange, legacyRowActions, onLegacyRowActionsChange,
            )
            ContextMenuDivider()
            val shownCount = ScheduleToolbarButton.entries.count { it.name !in hiddenButtons }
            ToggleMenuHeader(
                title = stringResource(Res.string.schedule_option_buttons),
                shown = shownCount,
                total = ScheduleToolbarButton.entries.size,
                onShowAll = {
                    ScheduleToolbarButton.entries.filter { it.name in hiddenButtons }.forEach(onToggleButton)
                },
            )
            ScheduleToolbarButton.entries.forEach { button ->
                ToggleMenuItem(
                    label = scheduleToolbarButtonLabel(button),
                    checked = button.name !in hiddenButtons,
                    onCheckedChange = { onToggleButton(button) },
                    modifier = Modifier.testTag(button.menuTag),
                    icon = scheduleToolbarButtonPainter(button),
                    accent = scheduleToolbarButtonAccent(button),
                )
                if (button in OPTIONS_GROUP_ENDS) ContextMenuDivider()
            }
        }
    }
}

/** Icon size as one segmented control, and the row-buttons choice as a switch under it. */
@Composable
private fun ScheduleIconSizeSection(
    toolbarIconSize: ScheduleToolbarIconSize,
    onToolbarIconSizeChange: (ScheduleToolbarIconSize) -> Unit,
    legacyRowActions: Boolean,
    onLegacyRowActionsChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ToggleMenuSectionLabel(stringResource(Res.string.schedule_option_icon_size))
        SegmentedButton(
            items = ScheduleToolbarIconSize.entries.map { size ->
                SegmentedButtonItem(size, scheduleToolbarIconSizeLabel(size), testTag = size.menuTag)
            },
            selectedValue = toolbarIconSize,
            onValueChange = onToolbarIconSizeChange,
            buttonWidth = ICON_SIZE_SEGMENT_WIDTH,
            buttonHeight = 34.dp,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ScheduleToolbarTags.OPTIONS_LEGACY_ACTIONS)
                .toggleable(
                    value = legacyRowActions,
                    role = Role.Switch,
                    onValueChange = onLegacyRowActionsChange,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.schedule_show_buttons_under_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            RaisedSwitch(checked = legacyRowActions, onCheckedChange = null)
        }
    }
}

/** Each group of buttons takes one hue, so the menu sorts itself the way the toolbar does. */
@Composable
private fun scheduleToolbarButtonAccent(button: ScheduleToolbarButton): Color = when (button) {
    ScheduleToolbarButton.ITEM_COUNT, ScheduleToolbarButton.ZOOM -> MaterialTheme.colorScheme.primary
    ScheduleToolbarButton.NEW, ScheduleToolbarButton.OPEN, ScheduleToolbarButton.SAVE ->
        MaterialTheme.semantic.success
    ScheduleToolbarButton.CLEAR -> MaterialTheme.colorScheme.error
    ScheduleToolbarButton.UNDO, ScheduleToolbarButton.REDO -> MaterialTheme.semantic.info
    ScheduleToolbarButton.ADD_LABEL -> MaterialTheme.colorScheme.tertiary
    ScheduleToolbarButton.PLANNING_CENTER -> MaterialTheme.semantic.contentPresentation
    ScheduleToolbarButton.CALENDAR -> MaterialTheme.semantic.contentCalendar
}

@Composable
private fun scheduleToolbarIconSizeLabel(size: ScheduleToolbarIconSize): String = when (size) {
    ScheduleToolbarIconSize.SMALL -> stringResource(Res.string.schedule_icon_size_small)
    ScheduleToolbarIconSize.MEDIUM -> stringResource(Res.string.schedule_icon_size_medium)
    ScheduleToolbarIconSize.LARGE -> stringResource(Res.string.schedule_icon_size_large)
}

/** The same icon the toolbar button itself draws, so the menu entry is recognisable as that button. */
@Composable
private fun scheduleToolbarButtonPainter(button: ScheduleToolbarButton): Painter = when (button) {
    ScheduleToolbarButton.ITEM_COUNT -> rememberVectorPainter(Icons.AutoMirrored.Filled.List)
    ScheduleToolbarButton.ZOOM -> painterResource(IconRes.drawable.ic_zoom_in)
    ScheduleToolbarButton.NEW -> painterResource(IconRes.drawable.ic_add)
    ScheduleToolbarButton.OPEN -> painterResource(IconRes.drawable.ic_folder)
    ScheduleToolbarButton.SAVE -> painterResource(IconRes.drawable.ic_save)
    ScheduleToolbarButton.CLEAR -> painterResource(IconRes.drawable.ic_delete)
    ScheduleToolbarButton.UNDO -> painterResource(IconRes.drawable.ic_undo)
    ScheduleToolbarButton.REDO -> painterResource(IconRes.drawable.ic_redo)
    ScheduleToolbarButton.ADD_LABEL -> painterResource(IconRes.drawable.ic_label)
    ScheduleToolbarButton.PLANNING_CENTER -> rememberVectorPainter(Icons.Default.CloudDownload)
    ScheduleToolbarButton.CALENDAR -> rememberVectorPainter(Icons.Default.CalendarMonth)
}

/**
 * The button's own name for the menu. Undo and Redo take the plain unbound wording rather than the
 * toolbar's shortcut-carrying tooltip: the menu names the button, it does not teach the shortcut.
 */
@Composable
private fun scheduleToolbarButtonLabel(button: ScheduleToolbarButton): String = when (button) {
    ScheduleToolbarButton.ITEM_COUNT -> stringResource(Res.string.schedule_option_item_count)
    ScheduleToolbarButton.ZOOM -> stringResource(Res.string.schedule_option_zoom)
    ScheduleToolbarButton.NEW -> stringResource(Res.string.tooltip_new_schedule)
    ScheduleToolbarButton.OPEN -> stringResource(Res.string.tooltip_open_schedule)
    ScheduleToolbarButton.SAVE -> stringResource(Res.string.tooltip_save_schedule)
    ScheduleToolbarButton.CLEAR -> stringResource(Res.string.menu_clear_schedule)
    ScheduleToolbarButton.UNDO -> stringResource(Res.string.tooltip_undo_unbound)
    ScheduleToolbarButton.REDO -> stringResource(Res.string.tooltip_redo_unbound)
    ScheduleToolbarButton.ADD_LABEL -> stringResource(Res.string.add_label)
    ScheduleToolbarButton.PLANNING_CENTER -> stringResource(Res.string.planning_center_import_title)
    ScheduleToolbarButton.CALENDAR -> stringResource(Res.string.open_calendar_manager)
}
