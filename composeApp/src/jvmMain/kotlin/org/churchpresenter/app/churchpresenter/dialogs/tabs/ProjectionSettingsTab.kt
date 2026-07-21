package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.rememberScrollState
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
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import org.churchpresenter.app.churchpresenter.models.Scene
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
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
import churchpresenter.composeapp.generated.resources.browser_source_enabled
import churchpresenter.composeapp.generated.resources.browser_source_require_api_key
import churchpresenter.composeapp.generated.resources.browser_source_uses_server_api_key
import churchpresenter.composeapp.generated.resources.copy_url_transparent
import churchpresenter.composeapp.generated.resources.copy_url_black_bg
import churchpresenter.composeapp.generated.resources.remove
import churchpresenter.composeapp.generated.resources.content_announcements
import churchpresenter.composeapp.generated.resources.tab_canvas
import churchpresenter.composeapp.generated.resources.tab_dictionary
import churchpresenter.composeapp.generated.resources.content_bible
import churchpresenter.composeapp.generated.resources.content_bible_background
import churchpresenter.composeapp.generated.resources.content_background_layered_tooltip
import churchpresenter.composeapp.generated.resources.content_media
import churchpresenter.composeapp.generated.resources.content_pictures
import churchpresenter.composeapp.generated.resources.content_songs
import churchpresenter.composeapp.generated.resources.content_songs_background
import churchpresenter.composeapp.generated.resources.content_streaming
import churchpresenter.composeapp.generated.resources.content_outputs
import churchpresenter.composeapp.generated.resources.content_outputs_for
import churchpresenter.composeapp.generated.resources.content_outputs_enabled_short
import churchpresenter.composeapp.generated.resources.content_outputs_enabled_subtitle
import churchpresenter.composeapp.generated.resources.content_outputs_quick_select
import churchpresenter.composeapp.generated.resources.content_outputs_select_all
import churchpresenter.composeapp.generated.resources.content_outputs_clear_all
import churchpresenter.composeapp.generated.resources.content_outputs_section_content
import churchpresenter.composeapp.generated.resources.content_outputs_section_backgrounds
import churchpresenter.composeapp.generated.resources.content_outputs_done
import churchpresenter.composeapp.generated.resources.detected_screens
import churchpresenter.composeapp.generated.resources.dev_window_label
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_lower_third_horizontal
import churchpresenter.composeapp.generated.resources.display_lower_third_vertical
import churchpresenter.composeapp.generated.resources.display_stage_monitor
import churchpresenter.composeapp.generated.resources.display_mode
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.key_output
import churchpresenter.composeapp.generated.resources.key_output_none
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.lower_third_height
import churchpresenter.composeapp.generated.resources.media_vlc_install
import churchpresenter.composeapp.generated.resources.media_vlc_load_failed
import churchpresenter.composeapp.generated.resources.media_vlc_required
import churchpresenter.composeapp.generated.resources.presenter_windows_count
import churchpresenter.composeapp.generated.resources.projection_simulate_outputs
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
import churchpresenter.composeapp.generated.resources.browser_source_fps
import churchpresenter.composeapp.generated.resources.browser_source_resolution
import churchpresenter.composeapp.generated.resources.browser_source_website_snapshot_tooltip
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
import org.churchpresenter.app.churchpresenter.composables.isVlcLoadFailed
import org.churchpresenter.app.churchpresenter.composables.listVlcAudioDevices
import org.churchpresenter.app.churchpresenter.composables.recheckVlcAvailability
import org.churchpresenter.app.churchpresenter.composables.vlcCustomPath
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.DevFlags
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
    scenes: List<Scene> = emptyList()
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
    // Dev convenience: mirrors main.kt's devWindowedFallback — on a single-monitor dev machine
    // with no DeckLink device, main.kt opens an extra windowed "dev" output at assignment slot 0.
    // Without this, that window would have no row here to configure it.
    val devWindowedFallback = (!BuildConfig.IS_RELEASE || DevFlags.forceDevWindow) && realWindowCount == 0
    val devWindowCount = proj.devWindowCount.coerceAtLeast(1)
    val presenterWindowCount = realWindowCount + if (devWindowedFallback) devWindowCount else 0

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
    val canvasLabel = stringResource(Res.string.tab_canvas)
    val songLaLabel = stringResource(Res.string.projection_content_song_la)
    val bibleBackgroundLabel = stringResource(Res.string.content_bible_background)
    val songsBackgroundLabel = stringResource(Res.string.content_songs_background)
    val backgroundLayeredTooltip = stringResource(Res.string.content_background_layered_tooltip)

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
        ContentCol(canvasLabel, { it.showCanvas }, { a, v -> a.copy(showCanvas = v) }),
        ContentCol("Q&A", { it.showQA }, { a, v -> a.copy(showQA = v) }),
        ContentCol("STT", { it.showSTT }, { a, v -> a.copy(showSTT = v) }),
        ContentCol(dictionaryLabel, { it.showDictionary }, { a, v -> a.copy(showDictionary = v) }),
        ContentCol("Background", { it.showFullscreenBackground }, { a, v -> a.copy(showFullscreenBackground = v) }),
        ContentCol("Lower Third Background", { it.showLowerThirdBackground }, { a, v -> a.copy(showLowerThirdBackground = v) }),
        ContentCol(bibleBackgroundLabel, { it.showBibleBackground }, { a, v -> a.copy(showBibleBackground = v) }, tooltip = backgroundLayeredTooltip),
        ContentCol(songsBackgroundLabel, { it.showSongsBackground }, { a, v -> a.copy(showSongsBackground = v) }, tooltip = backgroundLayeredTooltip),
    )
    // Split for the Content Outputs dialog: the last four toggles are the layered backgrounds,
    // everything before them is regular content. Bible/Songs language modes are handled
    // separately (dropdowns, not booleans).
    val backgroundGroup = contentCols.takeLast(4)
    val contentGroup = contentCols.dropLast(4)

    val fullScreenLabel = stringResource(Res.string.display_fullscreen)
    val lowerThirdLabel = stringResource(Res.string.display_lower_third_horizontal)
    val lowerThirdVerticalLabel = stringResource(Res.string.display_lower_third_vertical)
    val stageMonitorLabel = stringResource(Res.string.display_stage_monitor)
    val displayModes = listOf(
        fullScreenLabel to Constants.DISPLAY_MODE_FULLSCREEN,
        lowerThirdLabel to Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
        lowerThirdVerticalLabel to Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL,
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
                        assignment = assignment,
                        contentGroup = contentGroup,
                        backgroundGroup = backgroundGroup,
                        bibleLabel = bibleLabel,
                        songsLabel = songsLabel,
                        bibleLangModes = bibleLangModes,
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Switch(
                                checked = output.browserSourceEnabled,
                                onCheckedChange = { checked ->
                                    val updated = output.copy(browserSourceEnabled = checked)
                                    onSettingsChange { s ->
                                        s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                    }
                                }
                            )
                            Text(
                                text = stringResource(Res.string.browser_source_enabled),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

                // Dim (not disable) the rest of this card's controls when the output is off, so
                // it's obvious at a glance which outputs are inactive — the controls underneath
                // still work normally if the output is re-enabled.
                Column(modifier = Modifier.alpha(if (output.browserSourceEnabled) 1f else 0.5f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        @OptIn(ExperimentalMaterial3Api::class)
                        Column(modifier = Modifier.width(langDropdownWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                                Text(
                                    text = stringResource(Res.string.display_mode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            var displayModeExpanded by remember { mutableStateOf(false) }
                            OutlinedButton(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { displayModeExpanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = displayModes.find { it.second == output.displayMode }?.first ?: fullScreenLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                                            val updated = output.copy(displayMode = modeValue)
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
                                    text = stringResource(Res.string.browser_source_resolution),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            var resolutionExpanded by remember { mutableStateOf(false) }
                            OutlinedButton(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { resolutionExpanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${output.browserSourceWidth}\u00d7${output.browserSourceHeight}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                            DropdownMenu(
                                expanded = resolutionExpanded,
                                onDismissRequest = { resolutionExpanded = false }
                            ) {
                                listOf(1280 to 720, 1920 to 1080, 2560 to 1440, 3840 to 2160).forEach { (w, h) ->
                                    DropdownMenuItem(
                                        text = { Text("$w\u00d7$h", style = MaterialTheme.typography.bodySmall) },
                                        onClick = {
                                            resolutionExpanded = false
                                            val updated = output.copy(browserSourceWidth = w, browserSourceHeight = h)
                                            onSettingsChange { s ->
                                                s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                                Text(
                                    text = stringResource(Res.string.browser_source_fps),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            var fpsExpanded by remember { mutableStateOf(false) }
                            OutlinedButton(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { fpsExpanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = output.browserSourceFps.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                            DropdownMenu(
                                expanded = fpsExpanded,
                                onDismissRequest = { fpsExpanded = false }
                            ) {
                                listOf(10, 15, 24, 30, 60).forEach { fps ->
                                    DropdownMenuItem(
                                        text = { Text(fps.toString(), style = MaterialTheme.typography.bodySmall) },
                                        onClick = {
                                            fpsExpanded = false
                                            val updated = output.copy(browserSourceFps = fps)
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
                        // Content Outputs — opens a modal listing every content type + background.
                        Column(modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomStart) {
                                Text(
                                    text = stringResource(Res.string.content_outputs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            var showContentDialog by remember { mutableStateOf(false) }
                            val enabledCount = contentOutputsEnabledCount(output, contentGroup, backgroundGroup)
                            val totalCount = 2 + contentGroup.size + backgroundGroup.size
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
                            if (showContentDialog) {
                                ContentOutputsDialog(
                                    title = stringResource(Res.string.content_outputs_for, stringResource(Res.string.browser_source_output_label, i + 1)),
                                    assignment = output,
                                    contentGroup = contentGroup,
                                    backgroundGroup = backgroundGroup,
                                    bibleLabel = bibleLabel,
                                    songsLabel = songsLabel,
                                    bibleLangModes = bibleLangModes,
                                    songLangModes = songLangModes,
                                    webDeckLinkTooltip = stringResource(Res.string.projection_web_decklink_tooltip),
                                    webSnapshotTooltip = stringResource(Res.string.browser_source_website_snapshot_tooltip),
                                    isBrowserSource = true,
                                    onApply = { updated ->
                                        onSettingsChange { s ->
                                            s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                        }
                                    },
                                    onDismiss = { showContentDialog = false }
                                )
                            }
                        }
                    }
                }
                } // end alpha-dimmed Column
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
                        text = if (isVlcLoadFailed) stringResource(Res.string.media_vlc_load_failed) else stringResource(Res.string.media_vlc_install),
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

/**
 * One toggleable content type shown in the Content Outputs dialog. Getter/setter operate on a
 * [ScreenAssignment] (a physical screen assignment or a browser-source output — both share the type).
 */
data class ContentCol(
    val label: String,
    val getter: (ScreenAssignment) -> Boolean,
    val setter: (ScreenAssignment, Boolean) -> ScreenAssignment,
    val enabled: (ScreenAssignment) -> Boolean = { true },
    val tooltip: String? = null
)

/**
 * Count of enabled content types for the "N of M enabled" summary: Bible and Songs count when their
 * language mode isn't Off, plus every boolean content/background toggle that's on.
 */
private fun contentOutputsEnabledCount(
    a: ScreenAssignment,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>
): Int {
    var n = 0
    if (a.bibleMode != Constants.SONG_LANG_OFF) n++
    if (a.songMode != Constants.SONG_LANG_OFF) n++
    (contentGroup + backgroundGroup).forEach { if (it.getter(a)) n++ }
    return n
}

@Composable
private fun ContentOutputsSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp
    )
}

/**
 * A single boolean content toggle rendered as a rounded "chip" — checkbox + label, the whole chip
 * clickable. Wrapped in a tooltip when one is provided; the weight modifier is applied to the
 * outermost node so the two-column grid lines up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentToggleCell(
    modifier: Modifier,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    tooltip: String?,
    onCheckedChange: (Boolean) -> Unit,
) {
    // The weight modifier MUST sit on a plain layout node (the Box) that is a direct child of the
    // parent Row. Putting weight on a TooltipBox instead does not participate in the Row's weight
    // distribution and starves the sibling cell of width — that was the bug that hid every item
    // paired after a tooltipped one (Pictures/Presentation after Song LA, Songs Background after
    // Bible Background).
    Box(modifier = modifier) {
        val cell: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.35f))
                    .clickable(enabled = enabled) { onCheckedChange(!checked) }
                    .padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (tooltip != null) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(tooltip) } },
                state = rememberTooltipState(),
                modifier = Modifier.fillMaxWidth()
            ) { cell() }
        } else {
            cell()
        }
    }
}

/** Bible/Songs language-mode chip — label + a compact dropdown (Off / 1 / 2 / Both). */
@Composable
private fun ContentLangCell(
    modifier: Modifier,
    label: String,
    modes: List<Pair<String, String>>,
    currentMode: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = modes.find { it.first == currentMode }?.second ?: modes.first().second
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Box {
            OutlinedButton(
                shape = RoundedCornerShape(6.dp),
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = currentLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                modes.forEach { (value, l) ->
                    DropdownMenuItem(
                        text = { Text(l, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        }
                    )
                }
            }
        }
    }
}

/** Renders one content/background toggle, applying the Web-on-DeckLink / Web-snapshot tooltip rules. */
@Composable
private fun ContentOutputsToggle(
    modifier: Modifier,
    col: ContentCol,
    assignment: ScreenAssignment,
    isBrowserSource: Boolean,
    webDeckLinkTooltip: String,
    webSnapshotTooltip: String,
    onApply: (ScreenAssignment) -> Unit,
) {
    val isWeb = col.label == "Web"
    val webDisabledOnDeckLink = !isBrowserSource && isWeb && assignment.targetType == "decklink"
    val enabled = col.enabled(assignment) && !webDisabledOnDeckLink
    val checked = col.getter(assignment) && !webDisabledOnDeckLink
    val tooltip = when {
        webDisabledOnDeckLink -> webDeckLinkTooltip
        isWeb && isBrowserSource -> webSnapshotTooltip
        else -> col.tooltip
    }
    ContentToggleCell(
        modifier = modifier,
        label = col.label,
        checked = checked,
        enabled = enabled,
        tooltip = tooltip,
        onCheckedChange = { v -> onApply(col.setter(assignment, v)) }
    )
}

/**
 * Modal listing every content type + background for one output (a physical screen or browser
 * source). Replaces the old horizontally-scrolling per-row checkbox grid. Bible/Songs stay as
 * language dropdowns; everything else is a boolean toggle. Changes apply live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentOutputsDialog(
    title: String,
    assignment: ScreenAssignment,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>,
    bibleLabel: String,
    songsLabel: String,
    bibleLangModes: List<Pair<String, String>>,
    songLangModes: List<Pair<String, String>>,
    webDeckLinkTooltip: String,
    webSnapshotTooltip: String,
    isBrowserSource: Boolean,
    onApply: (ScreenAssignment) -> Unit,
    onDismiss: () -> Unit,
) {
    val total = 2 + contentGroup.size + backgroundGroup.size
    val enabled = contentOutputsEnabledCount(assignment, contentGroup, backgroundGroup)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(560.dp),
        shape = RoundedCornerShape(12.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(Res.string.content_outputs_enabled_subtitle, enabled, total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.content_outputs_done),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick select
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContentOutputsSectionHeader(stringResource(Res.string.content_outputs_quick_select))
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        shape = RoundedCornerShape(6.dp),
                        onClick = {
                            var a = assignment
                            (contentGroup + backgroundGroup).forEach { a = it.setter(a, true) }
                            a = a.copy(
                                bibleMode = if (a.bibleMode == Constants.SONG_LANG_OFF) Constants.SONG_LANG_BOTH else a.bibleMode,
                                songMode = if (a.songMode == Constants.SONG_LANG_OFF) Constants.SONG_LANG_BOTH else a.songMode
                            )
                            onApply(a)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(stringResource(Res.string.content_outputs_select_all), style = MaterialTheme.typography.labelSmall) }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        shape = RoundedCornerShape(6.dp),
                        onClick = {
                            var a = assignment
                            (contentGroup + backgroundGroup).forEach { a = it.setter(a, false) }
                            a = a.copy(
                                bibleMode = Constants.SONG_LANG_OFF,
                                songMode = Constants.SONG_LANG_OFF,
                                songLookAhead = false
                            )
                            onApply(a)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(stringResource(Res.string.content_outputs_clear_all), style = MaterialTheme.typography.labelSmall) }
                }

                // Content
                ContentOutputsSectionHeader(stringResource(Res.string.content_outputs_section_content))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentLangCell(
                        modifier = Modifier.weight(1f),
                        label = bibleLabel,
                        modes = bibleLangModes,
                        currentMode = assignment.bibleMode,
                        onSelect = { value -> onApply(assignment.copy(bibleMode = value)) }
                    )
                    ContentLangCell(
                        modifier = Modifier.weight(1f),
                        label = songsLabel,
                        modes = songLangModes,
                        currentMode = assignment.songMode,
                        onSelect = { value ->
                            val updated = if (value == Constants.SONG_LANG_OFF)
                                assignment.copy(songMode = value, songLookAhead = false)
                            else
                                assignment.copy(songMode = value)
                            onApply(updated)
                        }
                    )
                }
                contentGroup.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { col ->
                            ContentOutputsToggle(Modifier.weight(1f), col, assignment, isBrowserSource, webDeckLinkTooltip, webSnapshotTooltip, onApply)
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }

                // Backgrounds
                ContentOutputsSectionHeader(stringResource(Res.string.content_outputs_section_backgrounds))
                backgroundGroup.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { col ->
                            ContentOutputsToggle(Modifier.weight(1f), col, assignment, isBrowserSource, webDeckLinkTooltip, webSnapshotTooltip, onApply)
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                Text(stringResource(Res.string.content_outputs_done), style = MaterialTheme.typography.labelSmall)
            }
        }
    )
}

