package org.churchpresenter.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.shape.CircleShape
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_add
import org.churchpresenter.strings.generated.resources.schedule
import org.churchpresenter.strings.generated.resources.schedule_item_count
import org.churchpresenter.strings.generated.resources.schedule_add_files
import org.churchpresenter.sharedui.composables.ConditionalTooltipArea
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.keyboardFocusRing
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.raised
import org.churchpresenter.sharedui.composables.bibleListCardFill
import org.churchpresenter.sharedui.composables.topBarCard

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
    onToggleButton: (ScheduleToolbarButton) -> Unit = {},
    toolbarIconSize: ScheduleToolbarIconSize = ScheduleToolbarIconSize.SMALL,
    onToolbarIconSizeChange: (ScheduleToolbarIconSize) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Flush against the splitter on the right, like every other panel's cards.
            .topBarCard(end = 0.dp)
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
                onToggleButton = onToggleButton,
                toolbarIconSize = toolbarIconSize,
                onToolbarIconSizeChange = onToolbarIconSizeChange
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
                    hiddenButtons, toolbarIconSize,
                    onNewSchedule, onOpenSchedule, onSaveSchedule, onClearSchedule, canClear,
                )
                if (scheduleToolbarDividerVisible(0, hiddenButtons)) PillDivider()
                ScheduleHistoryButtons(hiddenButtons, toolbarIconSize, canUndo, canRedo, onUndo, onRedo)
                if (scheduleToolbarDividerVisible(1, hiddenButtons)) PillDivider()
                SchedulePlanningButtons(
                    hiddenButtons, toolbarIconSize, onAddLabel, onImportPlanningCenter, onOpenCalendar,
                )
            }
        }
        }
    }
}

/** The toolbar: one raised strip, its icons flat inside it. */
@Composable
internal fun PillGroup(content: @Composable () -> Unit) {
    // A raised strip that stays put; each icon in it rises into its own key under the pointer,
    // rather than the whole strip lifting as one.
    val palette = elevationPalette()
    FlowRow(
        modifier = Modifier
            .raised(AppShape(8.dp), palette.key, palette, lift = 2.dp)
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
    val shape = AppShape(8.dp)
    val borderColor = if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                       else MaterialTheme.colorScheme.outlineVariant
    val contentColor = if (hovered) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
    // The same fill as the cards around it; the accent wash on hover sits over it.
    val hoverWash = if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
    val strokeWidthPx = with(LocalDensity.current) { 1.dp.toPx() }
    val cornerRadiusPx = with(LocalDensity.current) { 8.dp.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .hoverable(interactionSource)
            .background(bibleListCardFill(), shape)
            .background(hoverWash, shape)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(
                        width = strokeWidthPx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON_PX, DASH_OFF_PX))
                    ),
                    cornerRadius = CornerRadius(cornerRadiusPx)
                )
            }
            .keyboardFocusRing(shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_add),
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
 *
 * Reads the disk: the app calls [readDroppedFiles] off the event thread and [addDroppedFiles] on it.
 */
internal fun handleDroppedFiles(files: List<File>, viewModel: ScheduleViewModel): List<String> =
    addDroppedFiles(readDroppedFiles(files), viewModel)

/** What one dropped file turns into, worked out with the disk so adding it needs none. */
internal sealed interface DroppedItem {
    val file: File

    /** A folder of pictures, and how many it holds. */
    data class PictureFolder(override val file: File, val imageCount: Int) : DroppedItem

    /** One picture, and how many pictures its folder holds. */
    data class Picture(override val file: File, val imageCount: Int) : DroppedItem

    /** A file whose kind is known from its extension alone. */
    data class ByType(override val file: File, val action: DroppedFileAction) : DroppedItem

    /** A file or folder the schedule cannot use. */
    data class Skipped(override val file: File) : DroppedItem
}

private fun imageCountIn(folder: File?): Int? =
    folder?.listFiles()?.count { child -> child.isFile && child.extension.lowercase() in IMAGE_EXTENSIONS }

/**
 * The disk half of a drop: which dropped folders hold pictures, and how many each picture's folder
 * holds. A folder on a network drive can take seconds to list, so this runs on IO
 * (CHURCH-PRESENTER-DESKTOP-JY).
 */
internal fun readDroppedFiles(files: List<File>): List<DroppedItem> = files.map { file ->
    if (file.isDirectory) {
        val imageCount = imageCountIn(file) ?: 0
        if (imageCount > 0) DroppedItem.PictureFolder(file, imageCount) else DroppedItem.Skipped(file)
    } else {
        when (val action = classifyDroppedFile(file.extension.lowercase())) {
            // The picture that was dropped, not its whole folder: a row for the folder presented
            // the folder's first picture, whichever one was dropped (#652).
            DroppedFileAction.PICTURE -> DroppedItem.Picture(file, imageCountIn(file.parentFile) ?: 1)
            DroppedFileAction.NONE -> DroppedItem.Skipped(file)
            else -> DroppedItem.ByType(file, action)
        }
    }
}

/** Adds what [readDroppedFiles] found, returning the names of those it could not use. */
internal fun addDroppedFiles(items: List<DroppedItem>, viewModel: ScheduleViewModel): List<String> {
    val skipped = mutableListOf<String>()
    for (item in items) {
        val file = item.file
        when (item) {
            is DroppedItem.PictureFolder -> viewModel.addPicture(file.absolutePath, file.name, item.imageCount)
            is DroppedItem.Picture -> viewModel.addSinglePicture(file, item.imageCount)
            is DroppedItem.Skipped -> skipped += file.name
            is DroppedItem.ByType -> addByType(file, item.action, viewModel)
        }
    }
    return skipped
}

private fun addByType(file: File, action: DroppedFileAction, viewModel: ScheduleViewModel) {
    val ext = file.extension.lowercase()
    when (action) {
        DroppedFileAction.PRESENTATION ->
            viewModel.addPresentation(file.absolutePath, file.nameWithoutExtension, 0, ext)
        DroppedFileAction.MEDIA ->
            viewModel.addMedia(file.absolutePath, file.nameWithoutExtension, "local")
        DroppedFileAction.LOWER_THIRD ->
            viewModel.addLowerThird(file.nameWithoutExtension, file.nameWithoutExtension, false, 0L)
        DroppedFileAction.PICTURE, DroppedFileAction.NONE -> Unit
    }
}
