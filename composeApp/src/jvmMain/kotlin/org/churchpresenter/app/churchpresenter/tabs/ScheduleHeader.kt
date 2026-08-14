package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.shape.RoundedCornerShape
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.utils.label
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
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
import churchpresenter.composeapp.generated.resources.planning_center_import_title
import churchpresenter.composeapp.generated.resources.schedule
import churchpresenter.composeapp.generated.resources.schedule_density_compact
import churchpresenter.composeapp.generated.resources.schedule_density_detailed
import churchpresenter.composeapp.generated.resources.schedule_density_normal
import churchpresenter.composeapp.generated.resources.schedule_item_count
import churchpresenter.composeapp.generated.resources.tooltip_redo
import churchpresenter.composeapp.generated.resources.tooltip_redo_unbound
import churchpresenter.composeapp.generated.resources.tooltip_undo
import churchpresenter.composeapp.generated.resources.tooltip_undo_unbound
import churchpresenter.composeapp.generated.resources.schedule_add_files
import churchpresenter.composeapp.generated.resources.tooltip_add_label
import churchpresenter.composeapp.generated.resources.tooltip_clear_schedule
import churchpresenter.composeapp.generated.resources.tooltip_schedule_zoom_in
import churchpresenter.composeapp.generated.resources.tooltip_schedule_zoom_out
import churchpresenter.composeapp.generated.resources.tooltip_new_schedule
import churchpresenter.composeapp.generated.resources.tooltip_open_schedule
import churchpresenter.composeapp.generated.resources.tooltip_save_schedule
import org.churchpresenter.app.churchpresenter.composables.ConditionalTooltipArea
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.churchpresenter.app.churchpresenter.utils.DroppedFileAction
import org.churchpresenter.app.churchpresenter.utils.IMAGE_EXTENSIONS
import org.churchpresenter.app.churchpresenter.utils.ScheduleDensity
import org.churchpresenter.app.churchpresenter.utils.classifyDroppedFile
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

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
    onClearSchedule: () -> Unit
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

            PillGroup {
                Text(
                    text = stringResource(Res.string.schedule_item_count, itemCount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            PillGroup {
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_remove),
                    text = stringResource(Res.string.tooltip_schedule_zoom_out),
                    onClick = onZoomOut,
                    enabled = canZoomOut,
                    buttonSize = 24.dp,
                    iconSize = 13.dp,
                    iconTint = if (canZoomOut) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_add),
                    text = stringResource(Res.string.tooltip_schedule_zoom_in),
                    onClick = onZoomIn,
                    enabled = canZoomIn,
                    buttonSize = 24.dp,
                    iconSize = 13.dp,
                    iconTint = if (canZoomIn) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {

            PillGroup {

                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_add),
                    text = stringResource(Res.string.tooltip_new_schedule),
                    onClick = onNewSchedule,
                    buttonSize = 26.dp,
                    iconSize = 14.dp,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_folder),
                    text = stringResource(Res.string.tooltip_open_schedule),
                    onClick = onOpenSchedule,
                    buttonSize = 26.dp,
                    iconSize = 14.dp,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_save),
                    text = stringResource(Res.string.tooltip_save_schedule),
                    onClick = onSaveSchedule,
                    buttonSize = 26.dp,
                    iconSize = 14.dp,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_delete),
                    text = stringResource(Res.string.tooltip_clear_schedule),
                    onClick = onClearSchedule,
                    buttonSize = 26.dp,
                    iconSize = 14.dp,
                    iconTint = MaterialTheme.colorScheme.error
                )
                PillDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {

                val shortcuts = LocalShortcuts.current
                val undoKeys = shortcuts.label(ShortcutAction.UNDO)
                val redoKeys = shortcuts.label(ShortcutAction.REDO)
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_undo),
                    text = if (undoKeys.isEmpty()) stringResource(Res.string.tooltip_undo_unbound)
                           else stringResource(Res.string.tooltip_undo, undoKeys),
                    onClick = onUndo,
                    modifier = Modifier.testTag(ScheduleToolbarTags.UNDO),
                    enabled = canUndo,
                    buttonSize = 26.dp,
                    iconSize = 14.dp,
                    iconTint = if (canUndo) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_redo),
                    text = if (redoKeys.isEmpty()) stringResource(Res.string.tooltip_redo_unbound)
                           else stringResource(Res.string.tooltip_redo, redoKeys),
                    onClick = onRedo,
                    modifier = Modifier.testTag(ScheduleToolbarTags.REDO),
                    enabled = canRedo,
                    buttonSize = 26.dp,
                    iconSize = 14.dp,
                    iconTint = if (canRedo) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
                }
                PillDivider()
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_label),
                    text = stringResource(Res.string.tooltip_add_label),
                    onClick = onAddLabel,
                    buttonSize = 26.dp,
                    iconSize = 14.dp,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TooltipIconButton(
                    painter = rememberVectorPainter(Icons.Default.CloudDownload),
                    text = stringResource(Res.string.planning_center_import_title),
                    onClick = onImportPlanningCenter,
                    buttonSize = 26.dp,
                    iconSize = 14.dp,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun PillGroup(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
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
            offset = DpOffset((-8).dp, 0.dp)
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
        IconButton(onClick = onClick, modifier = modifier.size(buttonSize), colors = colors) {
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
    val bg = if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
             else MaterialTheme.colorScheme.surfaceContainerHigh
    val strokeWidthPx = with(LocalDensity.current) { 1.dp.toPx() }
    val cornerRadiusPx = with(LocalDensity.current) { 8.dp.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .hoverable(interactionSource)
            .clip(shape)
            .background(bg, shape)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(width = strokeWidthPx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
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

internal fun handleDroppedFiles(files: List<File>, viewModel: ScheduleViewModel) {
    for (file in files) {
        if (file.isDirectory) {

            val imageCount = file.listFiles()?.count { child ->
                child.isFile && child.extension.lowercase() in IMAGE_EXTENSIONS
            } ?: 0
            if (imageCount > 0) {
                viewModel.addPicture(file.absolutePath, file.name, imageCount)
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
            DroppedFileAction.NONE -> {}
        }
    }
}
