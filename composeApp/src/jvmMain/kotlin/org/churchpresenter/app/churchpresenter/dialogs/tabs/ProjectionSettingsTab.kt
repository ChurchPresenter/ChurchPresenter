package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.awt.GraphicsEnvironment
import java.awt.GraphicsDevice
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
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
import churchpresenter.composeapp.generated.resources.display_mode
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.lower_third_height
import churchpresenter.composeapp.generated.resources.num_screens_label
import churchpresenter.composeapp.generated.resources.presenter_windows_count
import churchpresenter.composeapp.generated.resources.projection_display_label
import churchpresenter.composeapp.generated.resources.projection_position_help
import churchpresenter.composeapp.generated.resources.projection_target_display
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.screen
import churchpresenter.composeapp.generated.resources.screen_assignment
import churchpresenter.composeapp.generated.resources.screen_col_label
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.window_position
import churchpresenter.composeapp.generated.resources.audio_output
import churchpresenter.composeapp.generated.resources.audio_output_default
import churchpresenter.composeapp.generated.resources.audio_output_device
import churchpresenter.composeapp.generated.resources.key_output
import churchpresenter.composeapp.generated.resources.key_output_none
import churchpresenter.composeapp.generated.resources.media_vlc_required
import churchpresenter.composeapp.generated.resources.media_vlc_install
import churchpresenter.composeapp.generated.resources.vlc_custom_path
import churchpresenter.composeapp.generated.resources.vlc_browse
import churchpresenter.composeapp.generated.resources.vlc_path_hint
import churchpresenter.composeapp.generated.resources.vlc_path_invalid
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.VlcAudioDevice
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.listVlcAudioDevices
import org.churchpresenter.app.churchpresenter.composables.detectVlcInstallPath
import org.churchpresenter.app.churchpresenter.composables.recheckVlcAvailability
import org.churchpresenter.app.churchpresenter.composables.vlcCustomPath
import org.churchpresenter.app.churchpresenter.utils.createFileChooser
import javax.swing.JFileChooser
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.ScreenAssignment
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProjectionSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onIdentifyScreen: () -> Unit = {}
) {
    val proj = settings.projectionSettings

    // Detect physical screens once; derive presenter window count automatically.
    val detectedScreens = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size
    }
    val presenterWindowCount = (detectedScreens - 1).coerceAtLeast(0)

    // Extend the assignments list and resolve any unassigned (-1 auto or -2 none) to actual displays.
    LaunchedEffect(presenterWindowCount) {
        var changed = false
        val assignments = proj.screenAssignments.toMutableList()
        while (assignments.size < presenterWindowCount) {
            assignments.add(ScreenAssignment(targetDisplay = assignments.size + 1))
            changed = true
        }
        for (idx in assignments.indices) {
            if (assignments[idx].targetDisplay < 0) {
                assignments[idx] = assignments[idx].copy(targetDisplay = idx + 1)
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

    // Build display target options: Auto + physical displays + DeckLink devices
    val screenDevices = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    }

    data class DisplayOption(
        val label: String,
        val targetDisplay: Int,  // -2 = none, 0+ = display/device index
        val targetType: String   // "screen" or "decklink"
    )

    val noneLabel = stringResource(Res.string.key_output_none)
    val displayOptions = remember(screenDevices, noneLabel) {
        val options = mutableListOf<DisplayOption>()
        options.add(DisplayOption(noneLabel, Constants.KEY_TARGET_NONE, "screen"))
        // Add physical displays (skip display 0 = main app screen)
        for (idx in 1 until screenDevices.size) {
            val bounds = screenDevices[idx].defaultConfiguration.bounds
            options.add(
                DisplayOption(
                    "Display $idx (${bounds.width}x${bounds.height})",
                    idx,
                    "screen"
                )
            )
        }
        // Add DeckLink devices if available
        if (DeckLinkManager.isAvailable()) {
            DeckLinkManager.listDevices().forEachIndexed { i, device ->
                options.add(
                    DisplayOption(
                        "DeckLink ${i + 1}: ${device.name}",
                        device.index,
                        "decklink"
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

        val cellWidth = 82.dp

        data class ContentCol(
            val label: String,
            val getter: (ScreenAssignment) -> Boolean,
            val setter: (ScreenAssignment, Boolean) -> ScreenAssignment
        )

        val contentCols = listOf(
            ContentCol(bibleLabel, { it.showBible }, { a, v -> a.copy(showBible = v) }),
            ContentCol(songsLabel, { it.showSongs }, { a, v -> a.copy(showSongs = v) }),
            ContentCol(picturesLabel, { it.showPictures }, { a, v -> a.copy(showPictures = v) }),
            ContentCol(mediaLabel, { it.showMedia }, { a, v -> a.copy(showMedia = v) }),
            ContentCol(streamingLabel, { it.showStreaming }, { a, v -> a.copy(showStreaming = v) }),
            ContentCol(announcementsLabel, { it.showAnnouncements }, { a, v -> a.copy(showAnnouncements = v) }),
        )

        val displayModes = listOf(
            fullScreenLabel to Constants.DISPLAY_MODE_FULLSCREEN,
            lowerThirdLabel to Constants.DISPLAY_MODE_LOWER_THIRD
        )

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
            contentCols.forEach { col ->
                Text(
                    text = col.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(cellWidth)
                )
            }
            Text(
                text = stringResource(Res.string.display_mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(displayModeColWidth * 2)
            )
        }

        // Sub-header for display mode columns
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(screenLabelWidth + displayDropdownWidth * 2 + cellWidth * contentCols.size))
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
                    val currentOption = displayOptions.find {
                        it.targetDisplay == assignment.targetDisplay && it.targetType == assignment.targetType
                    } ?: displayOptions.first()

                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = currentOption.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
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
                                        targetType = option.targetType
                                    )
                                    onSettingsChange { s ->
                                        var newProj = s.projectionSettings.withAssignment(i, updated)
                                        // If selecting a specific display, clear it from other assignments
                                        if (option.targetDisplay >= 0) {
                                            for (j in 0 until numScreens) {
                                                val other = newProj.getAssignment(j)
                                                // Clear from other primary displays
                                                if (j != i && other.targetDisplay == option.targetDisplay && other.targetType == option.targetType) {
                                                    newProj = newProj.withAssignment(j, other.copy(targetDisplay = Constants.KEY_TARGET_NONE, targetType = "screen"))
                                                }
                                                // Clear from key outputs (any slot including this one)
                                                if (other.keyTargetDisplay == option.targetDisplay && other.keyTargetType == option.targetType) {
                                                    newProj = newProj.withAssignment(j, newProj.getAssignment(j).copy(keyTargetDisplay = Constants.KEY_TARGET_NONE, keyTargetType = "screen"))
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
                        val targetDisplay: Int,
                        val targetType: String
                    )
                    val keyOutputOptions = remember(screenDevices, noneLabel) {
                        val opts = mutableListOf(KeyOutputOption(noneLabel, Constants.KEY_TARGET_NONE, "screen"))
                        for (idx in 1 until screenDevices.size) {
                            val bounds = screenDevices[idx].defaultConfiguration.bounds
                            opts.add(KeyOutputOption("Display $idx (${bounds.width}x${bounds.height})", idx, "screen"))
                        }
                        if (DeckLinkManager.isAvailable()) {
                            DeckLinkManager.listDevices().forEachIndexed { di, device ->
                                opts.add(KeyOutputOption("DeckLink ${di + 1}: ${device.name}", device.index, "decklink"))
                            }
                        }
                        opts.toList()
                    }

                    val currentKeyOption = keyOutputOptions.find {
                        it.targetDisplay == assignment.keyTargetDisplay && it.targetType == assignment.keyTargetType
                    } ?: keyOutputOptions.first()

                    OutlinedButton(
                        onClick = { keyExpanded = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = currentKeyOption.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
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
                                        keyTargetType = option.targetType
                                    )
                                    onSettingsChange { s ->
                                        var newProj = s.projectionSettings.withAssignment(i, updated)
                                        if (option.targetDisplay >= 0) {
                                            for (j in 0 until numScreens) {
                                                val other = newProj.getAssignment(j)
                                                // Clear from other primary displays
                                                if (j != i && other.targetDisplay == option.targetDisplay && other.targetType == option.targetType) {
                                                    newProj = newProj.withAssignment(j, other.copy(targetDisplay = Constants.KEY_TARGET_NONE, targetType = "screen"))
                                                }
                                                // Clear from other key outputs
                                                if (j != i && other.keyTargetDisplay == option.targetDisplay && other.keyTargetType == option.targetType) {
                                                    newProj = newProj.withAssignment(j, other.copy(keyTargetDisplay = Constants.KEY_TARGET_NONE, keyTargetType = "screen"))
                                                }
                                            }
                                            // Also clear if same slot's primary display matches
                                            val self = newProj.getAssignment(i)
                                            if (self.targetDisplay == option.targetDisplay && self.targetType == option.targetType) {
                                                newProj = newProj.withAssignment(i, self.copy(targetDisplay = Constants.KEY_TARGET_NONE, targetType = "screen"))
                                            }
                                        }
                                        s.copy(projectionSettings = newProj)
                                    }
                                }
                            )
                        }
                    }
                }

                // Checkbox cells for each content type
                contentCols.forEach { col ->
                    Box(modifier = Modifier.width(cellWidth), contentAlignment = Alignment.Center) {
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

                // Radio button cells for display mode
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

            }

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
                val chooser = createFileChooser {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Select VLC installation directory"
                    if (vlcPathText.isNotBlank()) {
                        currentDirectory = java.io.File(vlcPathText)
                    }
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val selectedPath = chooser.selectedFile.absolutePath
                    vlcPathText = selectedPath
                    vlcCustomPath = selectedPath
                    onSettingsChange { s ->
                        s.copy(projectionSettings = s.projectionSettings.copy(vlcPath = selectedPath))
                    }
                    val detected = recheckVlcAvailability()
                    vlcDetected = detected
                    vlcPathError = !detected && selectedPath.isNotBlank()
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

