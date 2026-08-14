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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.browser_source_website_snapshot_tooltip
import churchpresenter.composeapp.generated.resources.content_bible
import churchpresenter.composeapp.generated.resources.content_outputs
import churchpresenter.composeapp.generated.resources.content_outputs_enabled_short
import churchpresenter.composeapp.generated.resources.content_outputs_for
import churchpresenter.composeapp.generated.resources.content_songs
import churchpresenter.composeapp.generated.resources.detected_screens
import churchpresenter.composeapp.generated.resources.dev_window_label
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_mode
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.key_output
import churchpresenter.composeapp.generated.resources.key_output_none
import churchpresenter.composeapp.generated.resources.lower_third_height
import churchpresenter.composeapp.generated.resources.presenter_windows_count
import churchpresenter.composeapp.generated.resources.projection_decklink_io_conflict_tooltip
import churchpresenter.composeapp.generated.resources.projection_simulate_outputs
import churchpresenter.composeapp.generated.resources.projection_target_display
import churchpresenter.composeapp.generated.resources.projection_web_decklink_tooltip
import churchpresenter.composeapp.generated.resources.screen
import churchpresenter.composeapp.generated.resources.screen_assignment
import churchpresenter.composeapp.generated.resources.screen_col_label
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.models.Scene
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The "Screen assignment" card of the Projection settings tab: which physical display or DeckLink
 * device each output drives, and the per-output content toggles.
 *
 * Split out of ProjectionSettingsTab.kt's single 1,390-line composable. The `remember`-backed
 * values ([screenDevicesAll], [displayOptions]) are passed in rather than recomputed here, so the
 * parent keeps ownership of them and recomposition behaves exactly as it did inline.
 */
@Composable
internal fun ScreenAssignmentCard(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onIdentifyScreen: () -> Unit,
    scenes: List<Scene>,
    screenDevicesAll: List<DetectedScreen>,
    detectedScreens: Int,
    devWindowCount: Int,
    devWindowedFallback: Boolean,
    presenterWindowCount: Int,
    numScreens: Int,
    screenAssignments: List<ScreenAssignment>,
    displayOptions: List<DisplayOption>,
    noneLabel: String,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>,
    displayModes: List<Pair<String, String>>,
    songLangModes: List<Pair<String, String>>,
    translationDisplays: List<BibleTranslationDisplay>,
    translationNames: List<String>,
) {
    val proj = settings.projectionSettings
    val bibleLabel = stringResource(Res.string.content_bible)
    val songsLabel = stringResource(Res.string.content_songs)
    val fullScreenLabel = stringResource(Res.string.display_fullscreen)
    val langDropdownWidth = 95.dp
    val contentLabelHeight = 32.dp

SettingsSection(title = stringResource(Res.string.screen_assignment)) {

    // Detected screens info + simulate stepper + Identify button
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
                onValueChange = { count ->
                    onSettingsChange { s ->
                        s.copy(projectionSettings = s.projectionSettings.copy(devWindowCount = count))
                    }
                },
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(shape = RoundedCornerShape(6.dp), onClick = { onIdentifyScreen() }) {
            Text(
                text = stringResource(Res.string.identify_screen),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Grid table — screens are rows (left), content types are columns (top)
    // Wide enough for the longest label ("Dev Window") to stay on a single line
    val screenLabelWidth = 90.dp
    val displayDropdownWidth = 100.dp

    // Header row: Screen label + Display + Key Output + Display Mode + Content Outputs.
    // Every label sits in a fixed-height, bottom-aligned Box so all labels' bottoms line up
    // right above the divider.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(screenLabelWidth))
        Box(modifier = Modifier.width(displayDropdownWidth).height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
            Text(
                text = stringResource(Res.string.projection_target_display),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(modifier = Modifier.width(displayDropdownWidth).height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
            Text(
                text = stringResource(Res.string.key_output),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(modifier = Modifier.width(langDropdownWidth).height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
            Text(
                text = stringResource(Res.string.display_mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f).height(contentLabelHeight), contentAlignment = Alignment.BottomStart) {
            Text(
                text = stringResource(Res.string.content_outputs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

    // One row per screen
    for (i in 0 until numScreens) {
        val assignment = screenAssignments[i]
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Screen label — the dev-window fallback always occupies slot 0
            Text(
                text = if (devWindowedFallback && i == 0) {
                    stringResource(Res.string.dev_window_label)
                } else {
                    stringResource(Res.string.screen_col_label, i + 1)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.width(screenLabelWidth)
            )

            // Display target dropdown
            Box(modifier = Modifier.width(displayDropdownWidth), contentAlignment = Alignment.Center) {
                var dropdownExpanded by remember { mutableStateOf(false) }
                // Match by type+index first for DeckLink (no bounds), then by bounds for screens
                val currentOption = displayOptions.find {
                    it.targetType == assignment.targetType &&
                    it.targetDisplay == assignment.targetDisplay &&
                    it.targetType == "decklink"
                } ?: displayOptions.find {
                    it.targetType == assignment.targetType &&
                    it.boundsX == assignment.targetBoundsX && it.boundsY == assignment.targetBoundsY &&
                    it.boundsW == assignment.targetBoundsW && it.boundsH == assignment.targetBoundsH
                } ?: displayOptions.find {
                    it.targetDisplay == assignment.targetDisplay && it.targetType == assignment.targetType
                } ?: displayOptions.first()

                val hasInputConflict = currentOption.targetType == "decklink" && currentOption.targetDisplay >= 0 &&
                    (DeckLinkManager.isInputActive(currentOption.targetDisplay) ||
                     DeckLinkManager.isInputConfigured(currentOption.targetDisplay, scenes))

                @OptIn(ExperimentalMaterial3Api::class)
                if (hasInputConflict) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(stringResource(Res.string.projection_decklink_io_conflict_tooltip)) } },
                        state = rememberTooltipState()
                    ) {
                        OutlinedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { dropdownExpanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                text = currentOption.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        shape = RoundedCornerShape(6.dp),
                        onClick = { dropdownExpanded = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = currentOption.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    displayOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                dropdownExpanded = false
                                val updated = assignment.copy(
                                    targetDisplay = option.targetDisplay,
                                    targetType = option.targetType,
                                    targetBoundsX = option.boundsX,
                                    targetBoundsY = option.boundsY,
                                    targetBoundsW = option.boundsW,
                                    targetBoundsH = option.boundsH
                                )
                                onSettingsChange { s ->
                                    var newProj = s.projectionSettings.withAssignment(i, updated)
                                    if (option.targetDisplay >= 0) {
                                        val isDeckLink = option.targetType == "decklink"
                                        for (j in 0 until numScreens) {
                                            val other = newProj.getAssignment(j)
                                            // Clear from other primary displays that target the same output
                                            val primaryMatch = if (isDeckLink) {
                                                j != i && other.targetType == "decklink" && other.targetDisplay == option.targetDisplay
                                            } else {
                                                j != i && option.boundsX != Int.MIN_VALUE &&
                                                other.targetBoundsX == option.boundsX && other.targetBoundsY == option.boundsY &&
                                                other.targetBoundsW == option.boundsW && other.targetBoundsH == option.boundsH
                                            }
                                            if (primaryMatch) {
                                                newProj = newProj.withAssignment(j, other.copy(
                                                    targetDisplay = Constants.KEY_TARGET_NONE, targetType = "screen",
                                                    targetBoundsX = Int.MIN_VALUE, targetBoundsY = Int.MIN_VALUE, targetBoundsW = 0, targetBoundsH = 0
                                                ))
                                            }
                                            // Clear from key outputs that target the same output
                                            val otherLatest = newProj.getAssignment(j)
                                            val keyMatch = if (isDeckLink) {
                                                otherLatest.keyTargetType == "decklink" && otherLatest.keyTargetDisplay == option.targetDisplay
                                            } else {
                                                option.boundsX != Int.MIN_VALUE &&
                                                otherLatest.keyTargetBoundsX == option.boundsX && otherLatest.keyTargetBoundsY == option.boundsY &&
                                                otherLatest.keyTargetBoundsW == option.boundsW && otherLatest.keyTargetBoundsH == option.boundsH
                                            }
                                            if (keyMatch) {
                                                newProj = newProj.withAssignment(j, otherLatest.copy(
                                                    keyTargetDisplay = Constants.KEY_TARGET_NONE, keyTargetType = "screen",
                                                    keyTargetBoundsX = Int.MIN_VALUE, keyTargetBoundsY = Int.MIN_VALUE, keyTargetBoundsW = 0, keyTargetBoundsH = 0
                                                ))
                                            }
                                        }
                                    }
                                    s.copy(projectionSettings = newProj)
                                }
                            }
                        )
                    }
                }
            }

            // Key output target dropdown (None + display options)
            Box(modifier = Modifier.width(displayDropdownWidth), contentAlignment = Alignment.Center) {
                var keyExpanded by remember { mutableStateOf(false) }
                val noneLabel = stringResource(Res.string.key_output_none)

                data class KeyOutputOption(
                    val label: String,
                    val shortLabel: String = label,
                    val targetDisplay: Int,
                    val targetType: String,
                    val boundsX: Int = Int.MIN_VALUE,
                    val boundsY: Int = Int.MIN_VALUE,
                    val boundsW: Int = 0,
                    val boundsH: Int = 0
                )
                val keyOutputOptions = remember(screenDevicesAll, noneLabel) {
                    val opts = mutableListOf(KeyOutputOption(label = noneLabel, targetDisplay = Constants.KEY_TARGET_NONE, targetType = "screen"))
                    var keyDisplayNum = 1
                    for (screen in screenDevicesAll) {
                        if (screen.isPrimary) continue
                        opts.add(KeyOutputOption(
                            label = "Display $keyDisplayNum (${screen.boundsW}x${screen.boundsH} @ ${screen.boundsX},${screen.boundsY})",
                            shortLabel = "D$keyDisplayNum (${screen.boundsW}x${screen.boundsH})",
                            targetDisplay = screen.index, targetType = "screen",
                            boundsX = screen.boundsX, boundsY = screen.boundsY, boundsW = screen.boundsW, boundsH = screen.boundsH
                        ))
                        keyDisplayNum++
                    }
                    if (DeckLinkManager.isAvailable()) {
                        DeckLinkManager.listDevices().forEachIndexed { di, device ->
                            opts.add(KeyOutputOption(
                                label = "DeckLink ${di + 1}: ${device.name}",
                                shortLabel = "DK${di + 1}: ${device.name}",
                                targetDisplay = device.index, targetType = "decklink"
                            ))
                        }
                    }
                    opts.toList()
                }

                // Match by type+index first for DeckLink (no bounds), then by bounds for screens
                val currentKeyOption = keyOutputOptions.find {
                    it.targetType == assignment.keyTargetType &&
                    it.targetDisplay == assignment.keyTargetDisplay &&
                    it.targetType == "decklink"
                } ?: keyOutputOptions.find {
                    it.targetType == assignment.keyTargetType &&
                    it.boundsX == assignment.keyTargetBoundsX && it.boundsY == assignment.keyTargetBoundsY &&
                    it.boundsW == assignment.keyTargetBoundsW && it.boundsH == assignment.keyTargetBoundsH
                } ?: keyOutputOptions.find {
                    it.targetDisplay == assignment.keyTargetDisplay && it.targetType == assignment.keyTargetType
                } ?: keyOutputOptions.first()

                val hasKeyInputConflict = currentKeyOption.targetType == "decklink" && currentKeyOption.targetDisplay >= 0 &&
                    (DeckLinkManager.isInputActive(currentKeyOption.targetDisplay) ||
                     DeckLinkManager.isInputConfigured(currentKeyOption.targetDisplay, scenes))

                @OptIn(ExperimentalMaterial3Api::class)
                if (hasKeyInputConflict) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(stringResource(Res.string.projection_decklink_io_conflict_tooltip)) } },
                        state = rememberTooltipState()
                    ) {
                        OutlinedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { keyExpanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                text = currentKeyOption.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        shape = RoundedCornerShape(6.dp),
                        onClick = { keyExpanded = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = currentKeyOption.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                DropdownMenu(
                    expanded = keyExpanded,
                    onDismissRequest = { keyExpanded = false }
                ) {
                    keyOutputOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                keyExpanded = false
                                val updated = assignment.copy(
                                    keyTargetDisplay = option.targetDisplay,
                                    keyTargetType = option.targetType,
                                    keyTargetBoundsX = option.boundsX,
                                    keyTargetBoundsY = option.boundsY,
                                    keyTargetBoundsW = option.boundsW,
                                    keyTargetBoundsH = option.boundsH
                                )
                                onSettingsChange { s ->
                                    var newProj = s.projectionSettings.withAssignment(i, updated)
                                    if (option.targetDisplay >= 0) {
                                        val isDeckLink = option.targetType == "decklink"
                                        for (j in 0 until numScreens) {
                                            val other = newProj.getAssignment(j)
                                            // Clear from other primary displays that target the same output
                                            val primaryMatch = if (isDeckLink) {
                                                j != i && other.targetType == "decklink" && other.targetDisplay == option.targetDisplay
                                            } else {
                                                j != i && option.boundsX != Int.MIN_VALUE &&
                                                other.targetBoundsX == option.boundsX && other.targetBoundsY == option.boundsY &&
                                                other.targetBoundsW == option.boundsW && other.targetBoundsH == option.boundsH
                                            }
                                            if (primaryMatch) {
                                                newProj = newProj.withAssignment(j, other.copy(
                                                    targetDisplay = Constants.KEY_TARGET_NONE, targetType = "screen",
                                                    targetBoundsX = Int.MIN_VALUE, targetBoundsY = Int.MIN_VALUE, targetBoundsW = 0, targetBoundsH = 0
                                                ))
                                            }
                                            // Clear from other key outputs that target the same output
                                            val otherLatest = newProj.getAssignment(j)
                                            val keyMatch = if (isDeckLink) {
                                                j != i && otherLatest.keyTargetType == "decklink" && otherLatest.keyTargetDisplay == option.targetDisplay
                                            } else {
                                                j != i && option.boundsX != Int.MIN_VALUE &&
                                                otherLatest.keyTargetBoundsX == option.boundsX && otherLatest.keyTargetBoundsY == option.boundsY &&
                                                otherLatest.keyTargetBoundsW == option.boundsW && otherLatest.keyTargetBoundsH == option.boundsH
                                            }
                                            if (keyMatch) {
                                                newProj = newProj.withAssignment(j, otherLatest.copy(
                                                    keyTargetDisplay = Constants.KEY_TARGET_NONE, keyTargetType = "screen",
                                                    keyTargetBoundsX = Int.MIN_VALUE, keyTargetBoundsY = Int.MIN_VALUE, keyTargetBoundsW = 0, keyTargetBoundsH = 0
                                                ))
                                            }
                                        }
                                        // Also clear if same slot's primary display targets the same output
                                        val self = newProj.getAssignment(i)
                                        val selfMatch = if (isDeckLink) {
                                            self.targetType == "decklink" && self.targetDisplay == option.targetDisplay
                                        } else {
                                            option.boundsX != Int.MIN_VALUE &&
                                            self.targetBoundsX == option.boundsX && self.targetBoundsY == option.boundsY &&
                                            self.targetBoundsW == option.boundsW && self.targetBoundsH == option.boundsH
                                        }
                                        if (selfMatch) {
                                            newProj = newProj.withAssignment(i, self.copy(
                                                targetDisplay = Constants.KEY_TARGET_NONE, targetType = "screen",
                                                targetBoundsX = Int.MIN_VALUE, targetBoundsY = Int.MIN_VALUE, targetBoundsW = 0, targetBoundsH = 0
                                            ))
                                        }
                                    }
                                    s.copy(projectionSettings = newProj)
                                }
                            }
                        )
                    }
                }
            }

            // Display mode dropdown (fixed column)
            @OptIn(ExperimentalMaterial3Api::class)
            Box(modifier = Modifier.width(langDropdownWidth), contentAlignment = Alignment.Center) {
                var displayModeExpanded by remember { mutableStateOf(false) }
                OutlinedButton(
                    shape = RoundedCornerShape(6.dp),
                    onClick = { displayModeExpanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayModes.find { it.second == assignment.displayMode }?.first ?: fullScreenLabel,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DropdownMenu(
                    expanded = displayModeExpanded,
                    onDismissRequest = { displayModeExpanded = false }
                ) {
                    displayModes.forEach { (label, modeValue) ->
                        DropdownMenuItem(
                            text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                displayModeExpanded = false
                                val updated = assignment.copy(displayMode = modeValue)
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.withAssignment(i, updated))
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Content Outputs — opens a modal listing every content type + background.
            // Replaces the old horizontally-scrolling checkbox grid.
            var showContentDialog by remember { mutableStateOf(false) }
            val enabledCount = contentOutputsEnabledCount(assignment, contentGroup, backgroundGroup)
            val totalCount = 2 + contentGroup.size + backgroundGroup.size
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                OutlinedButton(
                    shape = RoundedCornerShape(6.dp),
                    onClick = { showContentDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.content_outputs_enabled_short, enabledCount, totalCount),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (showContentDialog) {
                val screenLabel = if (devWindowedFallback && i == 0)
                    stringResource(Res.string.dev_window_label)
                else
                    stringResource(Res.string.screen_col_label, i + 1)
                ContentOutputsDialog(
                    title = stringResource(Res.string.content_outputs_for, screenLabel),
                    screenLabel = screenLabel,
                    assignment = assignment,
                    contentGroup = contentGroup,
                    backgroundGroup = backgroundGroup,
                    bibleLabel = bibleLabel,
                    songsLabel = songsLabel,
                    translationNames = translationNames,
                    translationDisplays = translationDisplays,
                    songLangModes = songLangModes,
                    webDeckLinkTooltip = stringResource(Res.string.projection_web_decklink_tooltip),
                    webSnapshotTooltip = stringResource(Res.string.browser_source_website_snapshot_tooltip),
                    isBrowserSource = false,
                    onApply = { updated ->
                        onSettingsChange { s ->
                            s.copy(projectionSettings = s.projectionSettings.withAssignment(i, updated))
                        }
                    },
                    onDismiss = { showContentDialog = false }
                )
            }

        } // end data Row

        if (i < numScreens - 1) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Lower third height
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.lower_third_height),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NumberSettingsTextField(
            initialText = proj.lowerThirdHeightPercent,
            onValueChange = { value ->
                onSettingsChange { s ->
                    s.copy(projectionSettings = s.projectionSettings.copy(lowerThirdHeightPercent = value))
                }
            },
            range = 10..60
        )
    }

}
}
