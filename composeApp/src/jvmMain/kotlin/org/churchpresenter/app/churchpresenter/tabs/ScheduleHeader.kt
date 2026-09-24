package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Tune
import org.churchpresenter.theme.components.RaisedCheckbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.ic_folder
import churchpresenter.composeapp.generated.resources.ic_label
import churchpresenter.composeapp.generated.resources.ic_redo
import churchpresenter.composeapp.generated.resources.ic_save
import churchpresenter.composeapp.generated.resources.ic_undo
import churchpresenter.composeapp.generated.resources.ic_zoom_in
import churchpresenter.composeapp.generated.resources.open_calendar_manager
import churchpresenter.composeapp.generated.resources.planning_center_import_title
import churchpresenter.composeapp.generated.resources.schedule
import churchpresenter.composeapp.generated.resources.schedule_item_count
import churchpresenter.composeapp.generated.resources.schedule_option_item_count
import churchpresenter.composeapp.generated.resources.schedule_option_zoom
import churchpresenter.composeapp.generated.resources.schedule_show_buttons_under_title
import churchpresenter.composeapp.generated.resources.tooltip_schedule_options
import churchpresenter.composeapp.generated.resources.tooltip_redo_unbound
import churchpresenter.composeapp.generated.resources.tooltip_undo_unbound
import churchpresenter.composeapp.generated.resources.schedule_add_files
import churchpresenter.composeapp.generated.resources.tooltip_add_label
import churchpresenter.composeapp.generated.resources.tooltip_clear_schedule
import churchpresenter.composeapp.generated.resources.tooltip_new_schedule
import churchpresenter.composeapp.generated.resources.tooltip_open_schedule
import churchpresenter.composeapp.generated.resources.tooltip_save_schedule
import org.churchpresenter.app.churchpresenter.composables.ConditionalTooltipArea
import org.churchpresenter.app.churchpresenter.composables.ToolbarKey
import org.churchpresenter.app.churchpresenter.composables.ToolbarKeyStyle
import org.churchpresenter.app.churchpresenter.utils.DroppedFileAction
import org.churchpresenter.app.churchpresenter.utils.IMAGE_EXTENSIONS
import org.churchpresenter.app.churchpresenter.utils.ScheduleDensity
import org.churchpresenter.app.churchpresenter.utils.classifyDroppedFile
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.raised

private const val MENU_OFFSET_DP = 8
private const val DASH_ON_PX = 6f
private const val DASH_OFF_PX = 4f

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleHeader(
    itemCount: Int,
    density: ScheduleDensity,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    canZoomOut: Boolean,
    canZoomIn: Boolean,
    onNewSchedule: () -> Unit,
    onOpenSchedule: () -> Unit,
    onSaveSchedule: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddLabel: () -> Unit,
    onImportPlanningCenter: () -> Unit,
    onOpenCalendar: () -> Unit,
    onClearSchedule: () -> Unit,
    canClear: Boolean = true,
    legacyRowActions: Boolean = false,
    onLegacyRowActionsChange: (Boolean) -> Unit = {},
    hiddenButtons: Set<String> = emptySet(),
    onToggleButton: (ScheduleToolbarButton) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.schedule),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (ScheduleToolbarButton.ITEM_COUNT.shownIn(hiddenButtons)) {
                // The count sits in a sunken pill: it is a readout, not something to press.
                Text(
                    text = stringResource(Res.string.schedule_item_count, itemCount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .sunken(CircleShape, elevationPalette())
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (ScheduleToolbarButton.ZOOM.shownIn(hiddenButtons)) {
                ScheduleZoomPill(density, canZoomOut, canZoomIn, onZoomOut, onZoomIn)
            }
            ScheduleOptionsButton(
                legacyRowActions = legacyRowActions,
                onLegacyRowActionsChange = onLegacyRowActionsChange,
                hiddenButtons = hiddenButtons,
                onToggleButton = onToggleButton
            )
        }

        if (scheduleToolbarVisible(hiddenButtons)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            PillGroup {
                ScheduleFileButtons(
                    hiddenButtons, onNewSchedule, onOpenSchedule, onSaveSchedule, onClearSchedule, canClear,
                )
                if (scheduleToolbarDividerVisible(0, hiddenButtons)) PillDivider()
                ScheduleHistoryButtons(hiddenButtons, canUndo, canRedo, onUndo, onRedo)
                if (scheduleToolbarDividerVisible(1, hiddenButtons)) PillDivider()
                SchedulePlanningButtons(hiddenButtons, onAddLabel, onImportPlanningCenter, onOpenCalendar)
            }
        }
        }
    }
}

/**
 * The schedule sidebar's own options menu — the panel-local settings that have no place in the
 * global settings dialog because they describe this panel's layout and are switched while looking
 * at it. Follows the tab-visibility menu in `MainDesktop`: an icon button opening a
 * [DropdownMenu] of checkbox items, each applied immediately.
 */
@Composable
private fun ScheduleOptionsButton(
    legacyRowActions: Boolean,
    onLegacyRowActionsChange: (Boolean) -> Unit,
    hiddenButtons: Set<String>,
    onToggleButton: (ScheduleToolbarButton) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        // Styled as the tab-visibility button in `MainDesktop`, not as the toolbar strip's icons:
        // both open a checkbox DropdownMenu of panel-level options, so they read as the same
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.schedule_show_buttons_under_title)) },
                onClick = { onLegacyRowActionsChange(!legacyRowActions) },
                modifier = Modifier.testTag(ScheduleToolbarTags.OPTIONS_LEGACY_ACTIONS),
                leadingIcon = { RaisedCheckbox(checked = legacyRowActions, onCheckedChange = null) }
            )
            HorizontalDivider()
            ScheduleToolbarButton.entries.forEach { button ->
                val shown = button.name !in hiddenButtons
                DropdownMenuItem(
                    text = { Text(scheduleToolbarButtonLabel(button)) },
                    onClick = { onToggleButton(button) },
                    modifier = Modifier.testTag(button.menuTag),
                    leadingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RaisedCheckbox(checked = shown, onCheckedChange = null)
                            Icon(
                                painter = scheduleToolbarButtonPainter(button),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (button == ScheduleToolbarButton.CLEAR) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}

/** The same icon the toolbar button itself draws, so the menu entry is recognisable as that button. */
@Composable
private fun scheduleToolbarButtonPainter(button: ScheduleToolbarButton): Painter = when (button) {
    ScheduleToolbarButton.ITEM_COUNT -> rememberVectorPainter(Icons.AutoMirrored.Filled.List)
    ScheduleToolbarButton.ZOOM -> painterResource(Res.drawable.ic_zoom_in)
    ScheduleToolbarButton.NEW -> painterResource(Res.drawable.ic_add)
    ScheduleToolbarButton.OPEN -> painterResource(Res.drawable.ic_folder)
    ScheduleToolbarButton.SAVE -> painterResource(Res.drawable.ic_save)
    ScheduleToolbarButton.CLEAR -> painterResource(Res.drawable.ic_delete)
    ScheduleToolbarButton.UNDO -> painterResource(Res.drawable.ic_undo)
    ScheduleToolbarButton.REDO -> painterResource(Res.drawable.ic_redo)
    ScheduleToolbarButton.ADD_LABEL -> painterResource(Res.drawable.ic_label)
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
    ScheduleToolbarButton.CLEAR -> stringResource(Res.string.tooltip_clear_schedule)
    ScheduleToolbarButton.UNDO -> stringResource(Res.string.tooltip_undo_unbound)
    ScheduleToolbarButton.REDO -> stringResource(Res.string.tooltip_redo_unbound)
    ScheduleToolbarButton.ADD_LABEL -> stringResource(Res.string.tooltip_add_label)
    ScheduleToolbarButton.PLANNING_CENTER -> stringResource(Res.string.planning_center_import_title)
    ScheduleToolbarButton.CALENDAR -> stringResource(Res.string.open_calendar_manager)
}

/** The toolbar: one raised strip, its icons flat inside it. */
@Composable
internal fun PillGroup(content: @Composable () -> Unit) {
    // A raised strip that stays put; each icon in it rises into its own key under the pointer,
    // rather than the whole strip lifting as one.
    val palette = elevationPalette()
    FlowRow(
        modifier = Modifier
            .raised(RoundedCornerShape(8.dp), palette.key, palette, lift = 2.dp)
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) { content() }
}

@Composable
internal fun PillDivider() {
    VerticalDivider(
        modifier = Modifier.height(14.dp).padding(horizontal = 2.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ScheduleRowActionButton(
    painter: Painter,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,

    buttonSize: Dp = 30.dp,
    iconSize: Dp = 13.dp,
    iconTint: Color? = null,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors()
) {
    ConditionalTooltipArea(

        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.CenterStart,
            alignment = Alignment.CenterStart,
            offset = DpOffset((-MENU_OFFSET_DP).dp, 0.dp)
        ),
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        KeyIconButton(onClick = onClick, modifier = modifier.size(buttonSize), colors = colors) {
            Image(
                painter = painter,
                contentDescription = text,
                modifier = Modifier.size(iconSize),
                colorFilter = iconTint?.let { ColorFilter.tint(it) }
            )
        }
    }
}

@Composable
internal fun ScheduleAddFilesButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                       else MaterialTheme.colorScheme.outlineVariant
    val contentColor = if (hovered) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
    // A sunken well like the other inputs, rather than a lighter block on the schedule; the accent
    // wash on hover sits over the well.
    val hoverWash = if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
    val strokeWidthPx = with(LocalDensity.current) { 1.dp.toPx() }
    val cornerRadiusPx = with(LocalDensity.current) { 8.dp.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .hoverable(interactionSource)
            .sunken(shape, elevationPalette())
            .background(hoverWash, shape)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(width = strokeWidthPx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON_PX, DASH_OFF_PX))),
                    cornerRadius = CornerRadius(cornerRadiusPx)
                )
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_add),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = stringResource(Res.string.schedule_add_files),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Adds every file the schedule understands, and **names the ones it does not**.
 *
 * The return value is what stops an unusable drop being indistinguishable from a broken one. A file
 * whose extension means nothing here, and a folder with no pictures in it, both used to fall through
 * to nothing at all — no row, no message — which is how dropping onto the schedule came to be
 * reported as not working (#606, #623).
 *
 * The names come back rather than a count so the caller can say *which* file it could not take;
 * "one of these four did not work" is barely better than silence.
 */
internal fun handleDroppedFiles(files: List<File>, viewModel: ScheduleViewModel): List<String> {
    val skipped = mutableListOf<String>()
    for (file in files) {
        if (file.isDirectory) {

            val imageCount = file.listFiles()?.count { child ->
                child.isFile && child.extension.lowercase() in IMAGE_EXTENSIONS
            } ?: 0
            if (imageCount > 0) {
                viewModel.addPicture(file.absolutePath, file.name, imageCount)
            } else {
                skipped += file.name
            }
            continue
        }

        val ext = file.extension.lowercase()
        when (classifyDroppedFile(ext)) {
            DroppedFileAction.PRESENTATION ->
                viewModel.addPresentation(file.absolutePath, file.nameWithoutExtension, 0, ext)
            DroppedFileAction.MEDIA ->
                viewModel.addMedia(file.absolutePath, file.nameWithoutExtension, "local")
            DroppedFileAction.PICTURE -> {

                val parentFolder = file.parentFile
                val imageCount = parentFolder?.listFiles()?.count { child ->
                    child.isFile && child.extension.lowercase() in IMAGE_EXTENSIONS
                } ?: 1
                viewModel.addPicture(
                    parentFolder?.absolutePath ?: file.absolutePath,
                    parentFolder?.name ?: file.name,
                    imageCount
                )
            }
            DroppedFileAction.LOWER_THIRD ->
                viewModel.addLowerThird(file.nameWithoutExtension, file.nameWithoutExtension, false, 0L)
            DroppedFileAction.NONE -> skipped += file.name
        }
    }
    return skipped
}
