package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.audio_output
import churchpresenter.composeapp.generated.resources.audio_output_default
import churchpresenter.composeapp.generated.resources.audio_output_device
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.content_announcements
import churchpresenter.composeapp.generated.resources.content_bible
import churchpresenter.composeapp.generated.resources.content_media
import churchpresenter.composeapp.generated.resources.content_pictures
import churchpresenter.composeapp.generated.resources.content_songs
import churchpresenter.composeapp.generated.resources.content_streaming
import churchpresenter.composeapp.generated.resources.detected_screens
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.display_stage_monitor
import churchpresenter.composeapp.generated.resources.display_mode
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.key_output
import churchpresenter.composeapp.generated.resources.key_output_none
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.lower_third_height
import churchpresenter.composeapp.generated.resources.media_vlc_install
import churchpresenter.composeapp.generated.resources.media_vlc_required
import churchpresenter.composeapp.generated.resources.presenter_windows_count
import churchpresenter.composeapp.generated.resources.projection_position_help
import churchpresenter.composeapp.generated.resources.projection_target_display
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.screen
import churchpresenter.composeapp.generated.resources.screen_assignment
import churchpresenter.composeapp.generated.resources.screen_col_label
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.vlc_browse
import churchpresenter.composeapp.generated.resources.vlc_custom_path
import churchpresenter.composeapp.generated.resources.vlc_path_hint
import churchpresenter.composeapp.generated.resources.vlc_path_invalid
import churchpresenter.composeapp.generated.resources.window_position
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.detectVlcInstallPath
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.listVlcAudioDevices
import org.churchpresenter.app.churchpresenter.composables.recheckVlcAvailability
import org.churchpresenter.app.churchpresenter.composables.vlcCustomPath
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.ScreenAssignment
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Composable
fun ProjectionSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onIdentifyScreen: () -> Unit = {},
    scenes: List<org.churchpresenter.app.churchpresenter.models.Scene> = emptyList()
) {
    val scope = rememberCoroutineScope()
    val proj = settings.projectionSettings

    // Detect physical screens; exclude the primary monitor from presenter targets.
    val screenDevicesAll = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    }
    val primaryDevice = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    }
    val detectedScreens = screenDevicesAll.size
    val deckLinkDeviceCount = remember { if (DeckLinkManager.isAvailable()) DeckLinkManager.listDevices().size else 0 }
    val presenterWindowCount = (screenDevicesAll.count { it != primaryDevice } + deckLinkDeviceCount).coerceAtLeast(0)

    // Extend the assignments list and resolve any unassigned (-1 auto) to actual non-primary displays.
    val nonPrimaryDevices = remember(screenDevicesAll, primaryDevice) {
        screenDevicesAll.filter { it != primaryDevice }
    }
    LaunchedEffect(presenterWindowCount, nonPrimaryDevices) {
        var changed = false
        val assignments = proj.screenAssignments.toMutableList()
        while (assignments.size < presenterWindowCount) {
            val npIdx = assignments.size
            val device = nonPrimaryDevices.getOrNull(npIdx)
            val deviceIdx = if (device != null) screenDevicesAll.indexOf(device) else Constants.KEY_TARGET_NONE
            val bounds = device?.defaultConfiguration?.bounds
            assignments.add(ScreenAssignment(
                targetDisplay = deviceIdx,
                targetBoundsX = bounds?.x ?: Int.MIN_VALUE,
                targetBoundsY = bounds?.y ?: Int.MIN_VALUE,
                targetBoundsW = bounds?.width ?: 0,
                targetBoundsH = bounds?.height ?: 0
            ))
            changed = true
        }
        for (idx in assignments.indices) {
            // Only resolve auto (-1) to actual display; preserve none (-2)
            if (assignments[idx].targetDisplay == -1) {
                val device = nonPrimaryDevices.getOrNull(idx)
                if (device != null) {
                    val deviceIdx = screenDevicesAll.indexOf(device)
                    val bounds = device.defaultConfiguration.bounds
                    assignments[idx] = assignments[idx].copy(
                        targetDisplay = deviceIdx,
                        targetBoundsX = bounds.x,
                        targetBoundsY = bounds.y,
                        targetBoundsW = bounds.width,
                        targetBoundsH = bounds.height
                    )
                } else {
                    // No physical display available for this slot (e.g. DeckLink-only) — set to None
                    assignments[idx] = assignments[idx].copy(targetDisplay = Constants.KEY_TARGET_NONE)
                }
                changed = true
            }
        }
        if (changed) {
            onSettingsChange { s ->
                s.copy(projectionSettings = s.projectionSettings.copy(screenAssignments = assignments))
            }
        }
    }

    val numScreens = presenterWindowCount
    val screenAssignments = (0 until numScreens).map { proj.getAssignment(it) }

    // Build display target options: None + non-primary physical displays + DeckLink devices
    data class DisplayOption(
        val label: String,
        val shortLabel: String = label,
        val targetDisplay: Int,  // -2 = none, 0+ = display/device index
        val targetType: String,  // "screen" or "decklink"
        val boundsX: Int = Int.MIN_VALUE,
        val boundsY: Int = Int.MIN_VALUE,
        val boundsW: Int = 0,
        val boundsH: Int = 0
    )

    val noneLabel = stringResource(Res.string.key_output_none)
    val displayOptions = remember(screenDevicesAll, noneLabel) {
        val options = mutableListOf<DisplayOption>()
        options.add(DisplayOption(label = noneLabel, targetDisplay = Constants.KEY_TARGET_NONE, targetType = "screen"))
        // Add physical displays, skipping the primary monitor
        var displayNum = 1
        for (idx in screenDevicesAll.indices) {
            if (screenDevicesAll[idx] == primaryDevice) continue
            val bounds = screenDevicesAll[idx].defaultConfiguration.bounds
            options.add(
                DisplayOption(
                    label = "Display $displayNum (${bounds.width}x${bounds.height} @ ${bounds.x},${bounds.y})",
                    shortLabel = "D$displayNum (${bounds.width}x${bounds.height})",
                    targetDisplay = idx,
                    targetType = "screen",
                    boundsX = bounds.x,
                    boundsY = bounds.y,
                    boundsW = bounds.width,
                    boundsH = bounds.height
                )
            )
            displayNum++
        }
        // Add DeckLink devices if available
        if (DeckLinkManager.isAvailable()) {
            DeckLinkManager.listDevices().forEachIndexed { i, device ->
                options.add(
                    DisplayOption(
                        label = "DeckLink ${i + 1}: ${device.name}",
                        shortLabel = "DK${i + 1}: ${device.name}",
                        targetDisplay = device.index,
                        targetType = "decklink"
                    )
                )
            }
        }
        options.toList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(5.dp)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Screen Assignment Grid ──────────────────────────────────────────
        SectionHeader(stringResource(Res.string.screen_assignment))
        Spacer(modifier = Modifier.height(4.dp))

        // Detected screens info + Identify button
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
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { onIdentifyScreen() }) {
                Text(
                    text = stringResource(Res.string.identify_screen),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Grid table — screens are rows (left), content types are columns (top)
        val screenLabelWidth = 58.dp
        val displayDropdownWidth = 100.dp
        val displayModeColWidth = 70.dp

        val bibleLabel = stringResource(Res.string.content_bible)
        val songsLabel = stringResource(Res.string.content_songs)
        val picturesLabel = stringResource(Res.string.content_pictures)
        val mediaLabel = stringResource(Res.string.content_media)
        val streamingLabel = stringResource(Res.string.content_streaming)
        val announcementsLabel = stringResource(Res.string.content_announcements)
        val fullScreenLabel = stringResource(Res.string.display_fullscreen)
        val lowerThirdLabel = stringResource(Res.string.display_lower_third)
        val stageMonitorLabel = stringResource(Res.string.display_stage_monitor)

        val cellWidth = 82.dp

        data class ContentCol(
            val label: String,
            val getter: (ScreenAssignment) -> Boolean,
            val setter: (ScreenAssignment, Boolean) -> ScreenAssignment
        )

        val contentCols = listOf(
            ContentCol(bibleLabel, { it.showBible }, { a, v -> a.copy(showBible = v) }),
            ContentCol(songsLabel, { it.showSongs && !it.songLookAhead }, { a, v ->
                if (v) a.copy(showSongs = true, songLookAhead = false)
                else a.copy(showSongs = false, songLookAhead = false)
            }),
            ContentCol("Song with Look Ahead", { it.songLookAhead }, { a, v ->
                if (v) a.copy(showSongs = true, songLookAhead = true)
                else a.copy(showSongs = false, songLookAhead = false)
            }),
            ContentCol(picturesLabel, { it.showPictures }, { a, v -> a.copy(showPictures = v) }),
            ContentCol(mediaLabel, { it.showMedia }, { a, v -> a.copy(showMedia = v) }),
            ContentCol(streamingLabel, { it.showStreaming }, { a, v -> a.copy(showStreaming = v) }),
            ContentCol(announcementsLabel, { it.showAnnouncements }, { a, v -> a.copy(showAnnouncements = v) }),
            ContentCol("Web", { it.showWebsite }, { a, v -> a.copy(showWebsite = v) }),
            ContentCol("Background", { it.showFullscreenBackground }, { a, v -> a.copy(showFullscreenBackground = v) }),
            ContentCol("Lower Third Background", { it.showLowerThirdBackground }, { a, v -> a.copy(showLowerThirdBackground = v) }),
        )

        val displayModes = listOf(
            fullScreenLabel to Constants.DISPLAY_MODE_FULLSCREEN,
            lowerThirdLabel to Constants.DISPLAY_MODE_LOWER_THIRD,
            stageMonitorLabel to Constants.DISPLAY_MODE_STAGE_MONITOR
        )

        val contentScrollState = rememberScrollState()

        // Header row: Screen label + Display + Key Output + content columns + display mode
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(screenLabelWidth))
            Text(
                text = stringResource(Res.string.projection_target_display),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(displayDropdownWidth)
            )
            Text(
                text = stringResource(Res.string.key_output),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(displayDropdownWidth)
            )
            Row(modifier = Modifier.weight(1f).horizontalScroll(contentScrollState)) {
                contentCols.forEach { col ->
                    Text(
                        text = col.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(cellWidth)
                    )
                }
            }
            Text(
                text = stringResource(Res.string.display_mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(displayModeColWidth * 3)
            )
        }

        // Sub-header for display mode columns
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(screenLabelWidth + displayDropdownWidth * 2))
            Spacer(modifier = Modifier.weight(1f))
            displayModes.forEach { (modeLabel, _) ->
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(displayModeColWidth)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        // One row per screen
        for (i in 0 until numScreens) {
            val assignment = screenAssignments[i]

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Screen label
                Text(
                    text = stringResource(Res.string.screen_col_label, i + 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("Also used for input — may not work on devices without simultaneous I/O") } },
                            state = rememberTooltipState()
                        ) {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.ui.graphics.Color(0xFFFF8888)),
                                border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFFF8888))
                            ) {
                                Text(
                                    text = currentOption.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentOption.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                        for (idx in screenDevicesAll.indices) {
                            if (screenDevicesAll[idx] == primaryDevice) continue
                            val bounds = screenDevicesAll[idx].defaultConfiguration.bounds
                            opts.add(KeyOutputOption(
                                label = "Display $keyDisplayNum (${bounds.width}x${bounds.height} @ ${bounds.x},${bounds.y})",
                                shortLabel = "D$keyDisplayNum (${bounds.width}x${bounds.height})",
                                targetDisplay = idx, targetType = "screen",
                                boundsX = bounds.x, boundsY = bounds.y, boundsW = bounds.width, boundsH = bounds.height
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
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("Also used for input — may not work on devices without simultaneous I/O") } },
                            state = rememberTooltipState()
                        ) {
                            OutlinedButton(
                                onClick = { keyExpanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.ui.graphics.Color(0xFFFF8888)),
                                border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFFF8888))
                            ) {
                                Text(
                                    text = currentKeyOption.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { keyExpanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentKeyOption.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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

                // Scrollable content: checkboxes only
                @OptIn(ExperimentalMaterial3Api::class)
                Row(modifier = Modifier.weight(1f).horizontalScroll(contentScrollState)) {
                    contentCols.forEach { col ->
                        val isWebOnDeckLink = col.label == "Web" && assignment.targetType == "decklink"
                        Box(modifier = Modifier.width(cellWidth), contentAlignment = Alignment.Center) {
                            if (isWebOnDeckLink) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                    tooltip = { PlainTooltip { Text("Web browser cannot render on DeckLink outputs") } },
                                    state = rememberTooltipState()
                                ) {
                                    Checkbox(checked = false, enabled = false, onCheckedChange = {})
                                }
                            } else {
                                Checkbox(
                                    checked = col.getter(assignment),
                                    onCheckedChange = { checked ->
                                        val updated = col.setter(assignment, checked)
                                        onSettingsChange { s ->
                                            s.copy(projectionSettings = s.projectionSettings.withAssignment(i, updated))
                                        }
                                    }
                                )
                            }
                        }
                    }
                } // end scrollable Row

                // Radio buttons for display mode (outside scroll)
                displayModes.forEach { (_, modeValue) ->
                    Box(modifier = Modifier.width(displayModeColWidth), contentAlignment = Alignment.Center) {
                        RadioButton(
                            selected = assignment.displayMode == modeValue,
                            onClick = {
                                val updated = assignment.copy(displayMode = modeValue)
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.withAssignment(i, updated))
                                }
                            }
                        )
                    }
                }

            } // end data Row

            if (i < numScreens - 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
            }
        }

        // Scrollbar for content columns
        Row {
            Spacer(modifier = Modifier.width(screenLabelWidth + displayDropdownWidth * 2))
            Box(modifier = Modifier.weight(1f)) {
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(contentScrollState),
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    style = LocalScrollbarStyle.current.copy(
                        thickness = 10.dp,
                        unhoverColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        hoverColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            Spacer(modifier = Modifier.width(displayModeColWidth * 3))
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

        Spacer(modifier = Modifier.height(8.dp))

        // ── Audio Output ────────────────────────────────────────────────────
        SectionHeader(stringResource(Res.string.audio_output))
        Spacer(modifier = Modifier.height(4.dp))

        var vlcDetected by remember { mutableStateOf(isVlcAvailable) }
        var vlcPathText by remember { mutableStateOf(proj.vlcPath.ifBlank { detectVlcInstallPath() }) }
        var vlcPathError by remember { mutableStateOf(false) }

        if (vlcDetected) {
            val audioDevices = remember(vlcDetected) { listVlcAudioDevices() }
            val defaultLabel = stringResource(Res.string.audio_output_default)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.audio_output_device),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box {
                    var expanded by remember { mutableStateOf(false) }
                    val currentDevice = audioDevices.find { it.id == proj.audioOutputDeviceId }
                    val currentLabel = currentDevice?.description ?: defaultLabel

                    OutlinedButton(onClick = { expanded = true }) {
                        Text(
                            text = currentLabel,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        // System default option
                        DropdownMenuItem(
                            text = { Text(defaultLabel, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                expanded = false
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.copy(audioOutputDeviceId = ""))
                                }
                            }
                        )
                        // VLC-detected devices
                        audioDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.description, style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    expanded = false
                                    onSettingsChange { s ->
                                        s.copy(projectionSettings = s.projectionSettings.copy(audioOutputDeviceId = device.id))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.media_vlc_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(Res.string.media_vlc_install),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Custom VLC path picker
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.vlc_custom_path),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = vlcPathText,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(stringResource(Res.string.vlc_path_hint), style = MaterialTheme.typography.bodySmall) },
                isError = vlcPathError,
                supportingText = if (vlcPathError) {{ Text(stringResource(Res.string.vlc_path_invalid)) }} else null,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                scope.launch {
                    val file = FileChooser.platformInstance.chooseSingle(
                        path = Path(vlcPathText),
                        title = "Select VLC installation directory",
                        selectDirectory = true,
                        filters = emptyList()
                    )
                    if (file != null) {
                        val selectedPath = file.absolutePathString()
                        vlcPathText = selectedPath
                        vlcCustomPath = selectedPath
                        onSettingsChange { s ->
                            s.copy(projectionSettings = s.projectionSettings.copy(vlcPath = selectedPath))
                        }
                        val detected = recheckVlcAvailability()
                        vlcDetected = detected
                        vlcPathError = !detected && selectedPath.isNotBlank()
                    }
                }
            }) {
                Text(
                    text = stringResource(Res.string.vlc_browse),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Window Position Settings ────────────────────────────────────────
        SectionHeader(stringResource(Res.string.window_position))
        Spacer(modifier = Modifier.height(8.dp))

        // Visual representation box with position fields
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(180.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top position
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.top),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    NumberSettingsTextField(
                        initialText = proj.windowTop,
                        onValueChange = { value ->
                            onSettingsChange { s ->
                                s.copy(projectionSettings = s.projectionSettings.copy(windowTop = value))
                            }
                        },
                        range = 0..10000
                    )
                }

                // Middle row - Left and Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left position
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.left),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        NumberSettingsTextField(
                            initialText = proj.windowLeft,
                            onValueChange = { value ->
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.copy(windowLeft = value))
                                }
                            },
                            range = 0..10000
                        )
                    }

                    // Center indicator
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.screen),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Right position
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.right),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        NumberSettingsTextField(
                            initialText = proj.windowRight,
                            onValueChange = { value ->
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.copy(windowRight = value))
                                }
                            },
                            range = 0..10000
                        )
                    }
                }

                // Bottom position
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.bottom),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    NumberSettingsTextField(
                        initialText = proj.windowBottom,
                        onValueChange = { value ->
                            onSettingsChange { s ->
                                s.copy(projectionSettings = s.projectionSettings.copy(windowBottom = value))
                            }
                        },
                        range = 0..10000
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Help text
        Text(
            text = stringResource(Res.string.projection_position_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        )

    }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

