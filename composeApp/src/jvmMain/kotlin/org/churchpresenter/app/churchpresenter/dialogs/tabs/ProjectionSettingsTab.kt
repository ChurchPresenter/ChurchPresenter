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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_browser_source_output
import churchpresenter.composeapp.generated.resources.audio_output
import churchpresenter.composeapp.generated.resources.audio_output_default
import churchpresenter.composeapp.generated.resources.audio_output_device
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.confirm_delete
import churchpresenter.composeapp.generated.resources.browser_source_outputs
import churchpresenter.composeapp.generated.resources.browser_source_outputs_help
import churchpresenter.composeapp.generated.resources.browser_source_output_label
import churchpresenter.composeapp.generated.resources.browser_source_confirm_remove_message
import churchpresenter.composeapp.generated.resources.browser_source_require_api_key
import churchpresenter.composeapp.generated.resources.browser_source_uses_server_api_key
import churchpresenter.composeapp.generated.resources.copy_url_transparent
import churchpresenter.composeapp.generated.resources.copy_url_black_bg
import churchpresenter.composeapp.generated.resources.remove
import churchpresenter.composeapp.generated.resources.content_announcements
import churchpresenter.composeapp.generated.resources.tab_dictionary
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
import churchpresenter.composeapp.generated.resources.projection_content_song_la
import churchpresenter.composeapp.generated.resources.projection_content_song_la_tooltip
import churchpresenter.composeapp.generated.resources.projection_position_help
import churchpresenter.composeapp.generated.resources.projection_target_display
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.screen
import churchpresenter.composeapp.generated.resources.screen_assignment
import churchpresenter.composeapp.generated.resources.screen_col_label
import churchpresenter.composeapp.generated.resources.screen_lang_bible_1
import churchpresenter.composeapp.generated.resources.screen_lang_bible_2
import churchpresenter.composeapp.generated.resources.screen_lang_language_1
import churchpresenter.composeapp.generated.resources.screen_lang_language_2
import churchpresenter.composeapp.generated.resources.screen_lang_off
import churchpresenter.composeapp.generated.resources.song_language_both
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.vlc_browse
import churchpresenter.composeapp.generated.resources.vlc_custom_path
import churchpresenter.composeapp.generated.resources.vlc_path_hint
import churchpresenter.composeapp.generated.resources.projection_decklink_io_conflict_tooltip
import churchpresenter.composeapp.generated.resources.browser_source_content_unavailable_tooltip
import churchpresenter.composeapp.generated.resources.projection_web_decklink_tooltip
import churchpresenter.composeapp.generated.resources.vlc_path_invalid
import churchpresenter.composeapp.generated.resources.window_position
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.TvScreenBox
import org.churchpresenter.app.churchpresenter.composables.detectVlcInstallPath
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.listVlcAudioDevices
import org.churchpresenter.app.churchpresenter.composables.recheckVlcAvailability
import org.churchpresenter.app.churchpresenter.composables.vlcCustomPath
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Composable
fun ProjectionSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    companionServer: CompanionServer,
    onIdentifyScreen: () -> Unit = {},
    onIdentifyBrowserSource: (Int) -> Unit = {},
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
    val realWindowCount = screenDevicesAll.count { it != primaryDevice } + deckLinkDeviceCount
    val presenterWindowCount = realWindowCount

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

    // Content-type columns — shared by the per-hardware Screen Assignment grid (Card 1)
    // and the per-output Browser Source checkboxes (Card 1.5).
    val bibleLabel = stringResource(Res.string.content_bible)
    val songsLabel = stringResource(Res.string.content_songs)
    val picturesLabel = stringResource(Res.string.content_pictures)
    val mediaLabel = stringResource(Res.string.content_media)
    val streamingLabel = stringResource(Res.string.content_streaming)
    val announcementsLabel = stringResource(Res.string.content_announcements)
    val dictionaryLabel = stringResource(Res.string.tab_dictionary)
    val songLaLabel = stringResource(Res.string.projection_content_song_la)

    data class ContentCol(
        val label: String,
        val getter: (ScreenAssignment) -> Boolean,
        val setter: (ScreenAssignment, Boolean) -> ScreenAssignment,
        val enabled: (ScreenAssignment) -> Boolean = { true },
        val tooltip: String? = null
    )

    val songLaTooltip = stringResource(Res.string.projection_content_song_la_tooltip)
    val contentCols = listOf(
        ContentCol(songLaLabel, { it.songLookAhead }, { a, v ->
            if (v) a.copy(songMode = if (a.songMode == Constants.SONG_LANG_OFF) Constants.SONG_LANG_BOTH else a.songMode, songLookAhead = true)
            else a.copy(songLookAhead = false)
        }, enabled = { it.songMode != Constants.SONG_LANG_OFF }, tooltip = songLaTooltip),
        ContentCol(picturesLabel, { it.showPictures }, { a, v -> a.copy(showPictures = v) }),
        ContentCol(mediaLabel, { it.showMedia }, { a, v -> a.copy(showMedia = v) }),
        ContentCol(streamingLabel, { it.showStreaming }, { a, v -> a.copy(showStreaming = v) }),
        ContentCol(announcementsLabel, { it.showAnnouncements }, { a, v -> a.copy(showAnnouncements = v) }),
        ContentCol("Web", { it.showWebsite }, { a, v -> a.copy(showWebsite = v) }),
        ContentCol("Q&A", { it.showQA }, { a, v -> a.copy(showQA = v) }),
        ContentCol("STT", { it.showSTT }, { a, v -> a.copy(showSTT = v) }),
        ContentCol(dictionaryLabel, { it.showDictionary }, { a, v -> a.copy(showDictionary = v) }),
        ContentCol("Background", { it.showFullscreenBackground }, { a, v -> a.copy(showFullscreenBackground = v) }),
        ContentCol("Lower Third Background", { it.showLowerThirdBackground }, { a, v -> a.copy(showLowerThirdBackground = v) }),
    )

    val fullScreenLabel = stringResource(Res.string.display_fullscreen)
    val lowerThirdLabel = stringResource(Res.string.display_lower_third)
    val stageMonitorLabel = stringResource(Res.string.display_stage_monitor)
    val displayModes = listOf(
        fullScreenLabel to Constants.DISPLAY_MODE_FULLSCREEN,
        lowerThirdLabel to Constants.DISPLAY_MODE_LOWER_THIRD,
        stageMonitorLabel to Constants.DISPLAY_MODE_STAGE_MONITOR
    )

    // Shared Bible/Songs language-mode dropdown options — used by both the Screen Assignment
    // table (Card 1) and the Browser Source Outputs table (Card 1.5).
    val offLabel = stringResource(Res.string.screen_lang_off)
    val bothLabel = stringResource(Res.string.song_language_both)
    val bible1Label = stringResource(Res.string.screen_lang_bible_1)
    val bible2Label = stringResource(Res.string.screen_lang_bible_2)
    val lang1Label = stringResource(Res.string.screen_lang_language_1)
    val lang2Label = stringResource(Res.string.screen_lang_language_2)
    val bibleLangModes = listOf(Constants.SONG_LANG_OFF to offLabel, Constants.SONG_LANG_PRIMARY to bible1Label, Constants.SONG_LANG_SECONDARY to bible2Label, Constants.SONG_LANG_BOTH to bothLabel)
    val songLangModes = listOf(Constants.SONG_LANG_OFF to offLabel, Constants.SONG_LANG_PRIMARY to lang1Label, Constants.SONG_LANG_SECONDARY to lang2Label, Constants.SONG_LANG_BOTH to bothLabel)

    // Shared column widths — used by both the Screen Assignment table (Card 1) and the
    // Browser Source Outputs table (Card 1.5) so their columns line up the same way.
    val displayModeColWidth = 92.dp
    val langDropdownWidth = 95.dp
    val cellWidth = 82.dp
    // Reserves 2 lines of bodySmall (16.sp line height) so single-line labels (Bible, display
    // mode, etc.) sit flush with the bottom of the tallest label (e.g. "Pictures/Presentation",
    // which wraps to 2 lines) — keeping every checkbox/radio button in the row aligned.
    val contentLabelHeight = 32.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
    // ── Card 1: Screen Assignment ───────────────────────────────────────────
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
        val screenLabelWidth = 58.dp
        val displayDropdownWidth = 100.dp

        val contentScrollState = rememberScrollState()

        // Header row: Screen label + Display + Key Output + content columns + display mode.
        // Every label sits in a fixed-height, bottom-aligned Box so columns whose title wraps
        // to 2 lines (e.g. "Pictures/Presentation") don't push single-line titles out of
        // alignment — all labels' bottoms line up right above the divider.
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
            Row(modifier = Modifier.weight(1f).horizontalScroll(contentScrollState), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.width(langDropdownWidth).height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                    Text(
                        text = bibleLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(modifier = Modifier.width(langDropdownWidth).height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                    Text(
                        text = songsLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                contentCols.forEach { col ->
                    Box(modifier = Modifier.width(cellWidth).height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                        Text(
                            text = col.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Box(modifier = Modifier.width(displayModeColWidth * 3).height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                Text(
                    text = stringResource(Res.string.display_mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
                    maxLines = 1,
                    softWrap = false,
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
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text(stringResource(Res.string.projection_decklink_io_conflict_tooltip)) } },
                            state = rememberTooltipState()
                        ) {
                            OutlinedButton(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { dropdownExpanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8888)),
                                border = BorderStroke(1.dp, Color(0xFFFF8888))
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
                            shape = RoundedCornerShape(6.dp),
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
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text(stringResource(Res.string.projection_decklink_io_conflict_tooltip)) } },
                            state = rememberTooltipState()
                        ) {
                            OutlinedButton(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { keyExpanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8888)),
                                border = BorderStroke(1.dp, Color(0xFFFF8888))
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
                            shape = RoundedCornerShape(6.dp),
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

                // Scrollable content: Bible/Songs dropdowns + checkboxes
                @OptIn(ExperimentalMaterial3Api::class)
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(contentScrollState),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.width(langDropdownWidth), contentAlignment = Alignment.Center) {
                        var bibleModeExpanded by remember { mutableStateOf(false) }
                        OutlinedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { bibleModeExpanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = bibleLangModes.find { it.first == assignment.bibleMode }?.second ?: offLabel,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = bibleModeExpanded,
                            onDismissRequest = { bibleModeExpanded = false }
                        ) {
                            bibleLangModes.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        bibleModeExpanded = false
                                        onSettingsChange { s ->
                                            s.copy(projectionSettings = s.projectionSettings.withAssignment(i, assignment.copy(bibleMode = value)))
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.width(langDropdownWidth), contentAlignment = Alignment.Center) {
                        var songModeExpanded by remember { mutableStateOf(false) }
                        OutlinedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { songModeExpanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = songLangModes.find { it.first == assignment.songMode }?.second ?: offLabel,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = songModeExpanded,
                            onDismissRequest = { songModeExpanded = false }
                        ) {
                            songLangModes.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        songModeExpanded = false
                                        onSettingsChange { s ->
                                            val updated = if (value == Constants.SONG_LANG_OFF)
                                                assignment.copy(songMode = value, songLookAhead = false)
                                            else
                                                assignment.copy(songMode = value)
                                            s.copy(projectionSettings = s.projectionSettings.withAssignment(i, updated))
                                        }
                                    }
                                )
                            }
                        }
                    }
                    contentCols.forEach { col ->
                        val isWebOnDeckLink = col.label == "Web" && assignment.targetType == "decklink"
                        Box(modifier = Modifier.width(cellWidth), contentAlignment = Alignment.Center) {
                            if (isWebOnDeckLink) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                                    tooltip = { PlainTooltip { Text(stringResource(Res.string.projection_web_decklink_tooltip)) } },
                                    state = rememberTooltipState()
                                ) {
                                    Checkbox(checked = false, enabled = false, onCheckedChange = {})
                                }
                            } else {
                                val checkbox = @Composable {
                                    Checkbox(
                                        checked = col.getter(assignment),
                                        enabled = col.enabled(assignment),
                                        onCheckedChange = { checked ->
                                            val updated = col.setter(assignment, checked)
                                            onSettingsChange { s ->
                                                s.copy(projectionSettings = s.projectionSettings.withAssignment(i, updated))
                                            }
                                        }
                                    )
                                }
                                if (col.tooltip != null) {
                                    TooltipBox(
                                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                                        tooltip = { PlainTooltip { Text(col.tooltip) } },
                                        state = rememberTooltipState()
                                    ) { checkbox() }
                                } else {
                                    checkbox()
                                }
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

    }

    // ── Card 1.5: Browser Source Outputs (OBS/vMix overlay) ───────────────────
    SettingsSection(title = stringResource(Res.string.browser_source_outputs)) {
        Text(
            text = stringResource(Res.string.browser_source_outputs_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        val serverUrl by companionServer.serverUrl.collectAsState()
        val copyText: (String) -> Unit = { text ->
            java.awt.Toolkit.getDefaultToolkit().systemClipboard
                .setContents(java.awt.datatransfer.StringSelection(text), null)
        }

        proj.browserSourceOutputs.forEachIndexed { i, output ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var showRemoveConfirm by remember { mutableStateOf(false) }
                val overlayUrl = if (serverUrl.isNotBlank()) "$serverUrl${Constants.ENDPOINT_BROWSER_SOURCE}/${i + 1}" else null
                val apiKeyParam = if (output.browserSourceApiKeyRequired && settings.serverSettings.apiKey.isNotBlank())
                    "apiKey=${settings.serverSettings.apiKey}" else null
                fun urlWithBg(bg: String): String =
                    (overlayUrl ?: "") + "?" + listOfNotNull(apiKeyParam, "bg=$bg").joinToString("&")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.browser_source_output_label, i + 1),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (overlayUrl != null) {
                            Text(
                                text = overlayUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (overlayUrl != null) {
                            Button(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { copyText(urlWithBg("transparent")) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(stringResource(Res.string.copy_url_transparent), style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { copyText(urlWithBg("black")) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(stringResource(Res.string.copy_url_black_bg), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Button(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { onIdentifyBrowserSource(i) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(Res.string.identify_screen), style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { showRemoveConfirm = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(stringResource(Res.string.remove), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (showRemoveConfirm) {
                    AlertDialog(
                        onDismissRequest = { showRemoveConfirm = false },
                        title = { Text(stringResource(Res.string.confirm_delete)) },
                        text = {
                            Text(stringResource(Res.string.browser_source_confirm_remove_message, stringResource(Res.string.browser_source_output_label, i + 1)))
                        },
                        confirmButton = {
                            TextButton(
                                shape = RoundedCornerShape(6.dp),
                                onClick = {
                                    showRemoveConfirm = false
                                    onSettingsChange { s ->
                                        s.copy(projectionSettings = s.projectionSettings.removeBrowserSourceOutput(i))
                                    }
                                }
                            ) {
                                Text(stringResource(Res.string.remove), color = Color(0xFFE53935))
                            }
                        },
                        dismissButton = {
                            TextButton(shape = RoundedCornerShape(6.dp), onClick = { showRemoveConfirm = false }) {
                                Text(stringResource(Res.string.cancel))
                            }
                        }
                    )
                }

                val rowScrollState = rememberScrollState()
                Row(verticalAlignment = Alignment.Top) {
                    Row(modifier = Modifier.weight(1f).horizontalScroll(rowScrollState), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(modifier = Modifier.width(langDropdownWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                                Text(
                                    text = bibleLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            var bibleModeExpanded by remember { mutableStateOf(false) }
                            OutlinedButton(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { bibleModeExpanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = bibleLangModes.find { it.first == output.bibleMode }?.second ?: offLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                            DropdownMenu(
                                expanded = bibleModeExpanded,
                                onDismissRequest = { bibleModeExpanded = false }
                            ) {
                                bibleLangModes.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                        onClick = {
                                            bibleModeExpanded = false
                                            val updated = output.copy(bibleMode = value)
                                            onSettingsChange { s ->
                                                s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.width(langDropdownWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                                Text(
                                    text = songsLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            var songModeExpanded by remember { mutableStateOf(false) }
                            OutlinedButton(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { songModeExpanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = songLangModes.find { it.first == output.songMode }?.second ?: offLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                            DropdownMenu(
                                expanded = songModeExpanded,
                                onDismissRequest = { songModeExpanded = false }
                            ) {
                                songLangModes.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                        onClick = {
                                            songModeExpanded = false
                                            val updated = if (value == Constants.SONG_LANG_OFF)
                                                output.copy(songMode = value, songLookAhead = false)
                                            else
                                                output.copy(songMode = value)
                                            onSettingsChange { s ->
                                                s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        @OptIn(ExperimentalMaterial3Api::class)
                        contentCols.forEach { col ->
                            val isUnavailableInBrowserSource = col.label == mediaLabel || col.label == "Web"
                            Column(modifier = Modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                                    Text(
                                        text = col.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (isUnavailableInBrowserSource) {
                                    TooltipBox(
                                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                                        tooltip = { PlainTooltip { Text(stringResource(Res.string.browser_source_content_unavailable_tooltip)) } },
                                        state = rememberTooltipState()
                                    ) {
                                        Checkbox(checked = false, enabled = false, onCheckedChange = {})
                                    }
                                } else {
                                    Checkbox(
                                        checked = col.getter(output),
                                        enabled = col.enabled(output),
                                        onCheckedChange = { checked ->
                                            val updated = col.setter(output, checked)
                                            onSettingsChange { s ->
                                                s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        @OptIn(ExperimentalMaterial3Api::class)
                        Column(modifier = Modifier.width(langDropdownWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                                Text(
                                    text = stringResource(Res.string.browser_source_require_api_key),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                                tooltip = { PlainTooltip { Text(stringResource(Res.string.browser_source_uses_server_api_key)) } },
                                state = rememberTooltipState()
                            ) {
                                Checkbox(
                                    checked = output.browserSourceApiKeyRequired,
                                    onCheckedChange = { checked ->
                                        val updated = output.copy(browserSourceApiKeyRequired = checked)
                                        onSettingsChange { s ->
                                            s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(Res.string.display_mode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(displayModeColWidth * 3)
                        )
                        Row {
                            displayModes.forEach { (label, modeValue) ->
                                Column(modifier = Modifier.width(displayModeColWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    RadioButton(
                                        selected = output.displayMode == modeValue,
                                        onClick = {
                                            val updated = output.copy(displayMode = modeValue)
                                            onSettingsChange { s ->
                                                s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row {
                    Box(modifier = Modifier.weight(1f)) {
                        HorizontalScrollbar(
                            adapter = rememberScrollbarAdapter(rowScrollState),
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            style = LocalScrollbarStyle.current.copy(
                                thickness = 8.dp,
                                unhoverColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                hoverColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(displayModeColWidth * 3))
                }
            }
        }

        Button(
            shape = RoundedCornerShape(6.dp),
            onClick = {
                onSettingsChange { s ->
                    s.copy(projectionSettings = s.projectionSettings.addBrowserSourceOutput())
                }
            }
        ) {
            Text(stringResource(Res.string.add_browser_source_output), style = MaterialTheme.typography.labelSmall)
        }
    }

    // ── Card 2: Audio Output ─────────────────────────────────────────────────
    SettingsSection(title = stringResource(Res.string.audio_output)) {

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

                    OutlinedButton(shape = RoundedCornerShape(6.dp), onClick = { expanded = true }) {
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
            SettingsTextField(
                value = vlcPathText,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(stringResource(Res.string.vlc_path_hint), style = MaterialTheme.typography.bodySmall) },
                isError = vlcPathError,
                supportingText = if (vlcPathError) {{ Text(stringResource(Res.string.vlc_path_invalid)) }} else null,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                shape = RoundedCornerShape(6.dp),
                onClick = {
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

    }
    // ── Card 3: Window Position ──────────────────────────────────────────────
    SettingsSection(title = stringResource(Res.string.window_position)) {

        // Visual representation box with position fields
        Column(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top position
            NumberSettingsTextField(
                label = stringResource(Res.string.top),
                initialText = proj.windowTop,
                onValueChange = { value ->
                    onSettingsChange { s ->
                        s.copy(projectionSettings = s.projectionSettings.copy(windowTop = value))
                    }
                },
                range = 0..10000
            )

            // Middle row - Left, TV, Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left position
                NumberSettingsTextField(
                    label = stringResource(Res.string.left),
                    initialText = proj.windowLeft,
                    onValueChange = { value ->
                        onSettingsChange { s ->
                            s.copy(projectionSettings = s.projectionSettings.copy(windowLeft = value))
                        }
                    },
                    range = 0..10000
                )

                TvScreenBox(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(180.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.screen),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Right position
                NumberSettingsTextField(
                    label = stringResource(Res.string.right),
                    initialText = proj.windowRight,
                    onValueChange = { value ->
                        onSettingsChange { s ->
                            s.copy(projectionSettings = s.projectionSettings.copy(windowRight = value))
                        }
                    },
                    range = 0..10000
                )
            }

            // Bottom position
            NumberSettingsTextField(
                label = stringResource(Res.string.bottom),
                initialText = proj.windowBottom,
                onValueChange = { value ->
                    onSettingsChange { s ->
                        s.copy(projectionSettings = s.projectionSettings.copy(windowBottom = value))
                    }
                },
                range = 0..10000
            )
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
}



