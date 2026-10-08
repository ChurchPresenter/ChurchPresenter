package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.ndi_resolution
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.detected_screens
import org.churchpresenter.strings.generated.resources.identify_screen
import org.churchpresenter.strings.generated.resources.key_output
import org.churchpresenter.strings.generated.resources.key_output_none
import org.churchpresenter.strings.generated.resources.output_profile_picker_tooltip
import org.churchpresenter.strings.generated.resources.presenter_windows_count
import org.churchpresenter.strings.generated.resources.projection_decklink_io_conflict_tooltip
import org.churchpresenter.strings.generated.resources.projection_simulate_outputs
import org.churchpresenter.strings.generated.resources.projection_target_display
import org.churchpresenter.strings.generated.resources.screen
import org.churchpresenter.canvas.DeckLinkManager
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.isScreenUnused

/**
 * Where a key can go: nowhere, any display but the primary, or a DeckLink port. A monitor marked
 * unused is left out -- a key is never sent to one -- though it keeps its number, so the displays
 * after it are numbered as the Display menu numbers them.
 */
@Composable
internal fun rememberKeyOutputOptions(
    screenDevicesAll: List<DetectedScreen>,
    proj: ProjectionSettings,
): List<DisplayOption> {
    val noneLabel = stringResource(Res.string.key_output_none)
return remember(screenDevicesAll, noneLabel, proj.screenNames, proj.unusedScreens) {
    val opts = mutableListOf(DisplayOption(label = noneLabel,
        targetDisplay = Constants.KEY_TARGET_NONE, targetType = Constants.TARGET_TYPE_SCREEN))
    // Numbered among every non-primary display, so an unused one left out keeps its number.
    screenDevicesAll.filterNot { it.isPrimary }.forEachIndexed { position, screen ->
        if (proj.isScreenUnused(screen.key)) return@forEachIndexed
        val keyDisplayNum = position + 1
        val named = proj.screenName(screen.key)
        opts.add(DisplayOption(
            label = displayLabel(named, keyDisplayNum, screen),
            shortLabel = displayShortLabel(named, keyDisplayNum, screen),
            targetDisplay = screen.index, targetType = Constants.TARGET_TYPE_SCREEN,
            boundsX = screen.boundsX,
            boundsY = screen.boundsY,
            boundsW = screen.boundsW,
            boundsH = screen.boundsH
        ))
    }
    if (DeckLinkManager.isAvailable()) {
        DeckLinkManager.listDevices().forEachIndexed { di, device ->
            opts.add(DisplayOption(
                label = "DeckLink ${di + 1}: ${device.name}",
                shortLabel = "DK${di + 1}: ${device.name}",
                targetDisplay = device.index, targetType = Constants.TARGET_TYPE_DECKLINK
            ))
        }
    }
    opts.toList()
}
}

/**
 * The button naming where an output goes -- outlined in red when it is a DeckLink port also in use
 * as an input, with a tooltip saying so -- and the menu of [options] it opens.
 */
@Composable
internal fun OutputTargetDropdown(
    options: List<DisplayOption>,
    current: DisplayOption,
    conflict: Boolean,
    onPick: (DisplayOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    @OptIn(ExperimentalMaterial3Api::class)
    if (conflict) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                PlainTooltip { Text(stringResource(Res.string.projection_decklink_io_conflict_tooltip)) }
            },
            state = rememberTooltipState()
        ) {
            KeyButton(
                shape = AppShape(6.dp),
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Text(
                    text = current.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        KeyButton(
            shape = AppShape(6.dp),
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = current.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label, style = MaterialTheme.typography.bodySmall) },
                onClick = {
                    expanded = false
                    onPick(option)
                }
            )
        }
    }
}

/** How many screens were found and how many output windows that makes, the dev stepper, and Identify. */
@Composable
internal fun ScreenAssignmentInfoRow(
    detectedScreens: Int,
    presenterWindowCount: Int,
    devWindowedFallback: Boolean,
    devWindowCount: Int,
    onIdentifyScreen: () -> Unit,
    onDevWindowCount: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(Res.string.detected_screens, detectedScreens),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.presenter_windows_count, presenterWindowCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        // Dev-only: simulate several independent output windows on a single-monitor machine.
        // Only meaningful in the dev fallback (no real display/DeckLink output exists).
        if (devWindowedFallback) {
            NumberSettingsTextField(
                label = stringResource(Res.string.projection_simulate_outputs),
                initialText = devWindowCount,
                range = 1..8,
                onValueChange = onDevWindowCount,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        RaisedButton(shape = AppShape(6.dp), onClick = onIdentifyScreen) {
            Text(
                text = stringResource(Res.string.identify_screen),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * The table's column headings: Display, Key Output, Output profile and, in the dev fallback,
 * Resolution. Each sits in a fixed-height, bottom-aligned box so their bottoms line up above the divider.
 */
@Composable
internal fun ScreenAssignmentColumnHeaders(
    devWindowedFallback: Boolean,
    screenLabelWidth: Dp,
    displayDropdownWidth: Dp,
    resolutionCellWidth: Dp,
) {
    val langDropdownWidth = 95.dp
    val contentLabelHeight = 32.dp
    // Header row: Screen label + Display + Key Output + Output profile.
    // Every label sits in a fixed-height, bottom-aligned Box so all labels' bottoms line up
    // right above the divider.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(screenLabelWidth))
        Box(
            modifier = Modifier.width(displayDropdownWidth).height(contentLabelHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = stringResource(Res.string.projection_target_display),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(
            modifier = Modifier.width(displayDropdownWidth).height(contentLabelHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = stringResource(Res.string.key_output),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(
            modifier = Modifier.width(langDropdownWidth).height(contentLabelHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = stringResource(Res.string.output_profile_picker_tooltip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Dev fallback only: a simulated window has no monitor to take its size from, so the
        // operator sets it -- per row, so several differently-shaped outputs can be simulated at
        // once. A real display's size is its own and there is nothing here to choose.
        if (devWindowedFallback) {
            Box(
                modifier = Modifier.width(resolutionCellWidth).height(contentLabelHeight),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = stringResource(Res.string.ndi_resolution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
