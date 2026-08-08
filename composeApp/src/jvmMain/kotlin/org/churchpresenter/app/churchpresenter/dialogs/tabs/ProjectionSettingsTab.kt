package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import churchpresenter.composeapp.generated.resources.tab_qa
import churchpresenter.composeapp.generated.resources.tab_stt
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
import churchpresenter.composeapp.generated.resources.content_outputs_preview
import churchpresenter.composeapp.generated.resources.content_outputs_preview_empty
import churchpresenter.composeapp.generated.resources.content_outputs_preview_translations
import churchpresenter.composeapp.generated.resources.content_outputs_section_content
import churchpresenter.composeapp.generated.resources.content_outputs_section_backgrounds
import churchpresenter.composeapp.generated.resources.content_outputs_done
import churchpresenter.composeapp.generated.resources.content_bible_translations_header
import churchpresenter.composeapp.generated.resources.content_bible_translations_all
import churchpresenter.composeapp.generated.resources.content_bible_translations_enabled
import churchpresenter.composeapp.generated.resources.content_bible_translations_footer
import churchpresenter.composeapp.generated.resources.content_bible_translation_portion_ot_nt
import churchpresenter.composeapp.generated.resources.content_bible_translation_portion_nt
import churchpresenter.composeapp.generated.resources.content_bible_translation_portion_ot
import churchpresenter.composeapp.generated.resources.content_bible_translations_more
import churchpresenter.composeapp.generated.resources.content_bible_translations_all_selected
import churchpresenter.composeapp.generated.resources.content_bible_translations_count_enabled
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.song_language_primary
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
import churchpresenter.composeapp.generated.resources.projection_content_background
import churchpresenter.composeapp.generated.resources.projection_content_lt_background
import churchpresenter.composeapp.generated.resources.projection_content_song_la
import churchpresenter.composeapp.generated.resources.projection_content_web
import churchpresenter.composeapp.generated.resources.projection_content_song_la_tooltip
import churchpresenter.composeapp.generated.resources.projection_content_stt_tooltip
import churchpresenter.composeapp.generated.resources.projection_position_help
import churchpresenter.composeapp.generated.resources.projection_target_display
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.screen
import churchpresenter.composeapp.generated.resources.screen_assignment
import churchpresenter.composeapp.generated.resources.screen_col_label
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
import org.churchpresenter.app.churchpresenter.data.Bible
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
import org.churchpresenter.app.churchpresenter.composables.LabeledSwitch

/**
 * One physical display, reduced to what this tab needs of it: its index in the device list (which is
 * what gets stored as a `targetDisplay`), whether it is the primary monitor, and its bounds.
 */
data class DetectedScreen(
    val index: Int,
    val isPrimary: Boolean,
    val boundsX: Int = Int.MIN_VALUE,
    val boundsY: Int = Int.MIN_VALUE,
    val boundsW: Int = 0,
    val boundsH: Int = 0
)

/**
 * One row of the Bible Translations picker: [code] is the file stem (also the selection key,
 * matching [BibleSettings.translationList] order), [title] and [portion] come from
 * [Bible.readTranslationSummary]'s cheap header-only read and fall back to [code] / blank when a
 * file can't be read.
 */
data class BibleTranslationDisplay(
    val code: String,
    val title: String,
    val portion: String,
)

/**
 * The real display list, read from AWT.
 *
 * Enumerating screens needs a windowing system, so this one call is what [ProjectionSettingsTab]
 * takes as a parameter rather than doing inline: it is the only part of the tab that cannot run
 * without a display, and hoisting it lets everything built on top of it — slot allocation, the
 * target and key-output menus, the whole assignment grid — be exercised against a stand-in list.
 */
fun detectScreensFromAwt(): List<DetectedScreen> {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val primary = environment.defaultScreenDevice
    return environment.screenDevices.mapIndexed { index, device ->
        val bounds = device.defaultConfiguration.bounds
        DetectedScreen(
            index = index,
            isPrimary = device == primary,
            boundsX = bounds.x,
            boundsY = bounds.y,
            boundsW = bounds.width,
            boundsH = bounds.height
        )
    }
}

@Composable
fun ProjectionSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    companionServer: CompanionServer,
    onIdentifyScreen: () -> Unit = {},
    onIdentifyBrowserSource: (Int) -> Unit = {},
    scenes: List<Scene> = emptyList(),
    detectScreens: () -> List<DetectedScreen> = ::detectScreensFromAwt
) {
    val scope = rememberCoroutineScope()
    val proj = settings.projectionSettings

    // Detect physical screens; exclude the primary monitor from presenter targets.
    val screenDevicesAll = remember { detectScreens() }
    val detectedScreens = screenDevicesAll.size
    val deckLinkDeviceCount = remember { if (DeckLinkManager.isAvailable()) DeckLinkManager.listDevices().size else 0 }
    val realWindowCount = screenDevicesAll.count { !it.isPrimary } + deckLinkDeviceCount
    // Dev convenience: mirrors main.kt's devWindowedFallback — on a single-monitor dev machine
    // with no DeckLink device, main.kt opens an extra windowed "dev" output at assignment slot 0.
    // Without this, that window would have no row here to configure it.
    val devWindowedFallback = (!BuildConfig.IS_RELEASE || DevFlags.forceDevWindow) && realWindowCount == 0
    val devWindowCount = proj.devWindowCount.coerceAtLeast(1)
    val presenterWindowCount = realWindowCount + if (devWindowedFallback) devWindowCount else 0

    // Extend the assignments list and resolve any unassigned (-1 auto) to actual non-primary displays.
    val nonPrimaryDevices = remember(screenDevicesAll) {
        screenDevicesAll.filter { !it.isPrimary }
    }
    LaunchedEffect(presenterWindowCount, nonPrimaryDevices) {
        var changed = false
        val assignments = proj.screenAssignments.toMutableList()
        while (assignments.size < presenterWindowCount) {
            val npIdx = assignments.size
            val device = nonPrimaryDevices.getOrNull(npIdx)
            assignments.add(ScreenAssignment(
                targetDisplay = device?.index ?: Constants.KEY_TARGET_NONE,
                targetBoundsX = device?.boundsX ?: Int.MIN_VALUE,
                targetBoundsY = device?.boundsY ?: Int.MIN_VALUE,
                targetBoundsW = device?.boundsW ?: 0,
                targetBoundsH = device?.boundsH ?: 0
            ))
            changed = true
        }
        for (idx in assignments.indices) {
            // Only resolve auto (-1) to actual display; preserve none (-2)
            if (assignments[idx].targetDisplay == -1) {
                val device = nonPrimaryDevices.getOrNull(idx)
                if (device != null) {
                    assignments[idx] = assignments[idx].copy(
                        targetDisplay = device.index,
                        targetBoundsX = device.boundsX,
                        targetBoundsY = device.boundsY,
                        targetBoundsW = device.boundsW,
                        targetBoundsH = device.boundsH
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
        for (screen in screenDevicesAll) {
            if (screen.isPrimary) continue
            options.add(
                DisplayOption(
                    label = "Display $displayNum (${screen.boundsW}x${screen.boundsH} @ ${screen.boundsX},${screen.boundsY})",
                    shortLabel = "D$displayNum (${screen.boundsW}x${screen.boundsH})",
                    targetDisplay = screen.index,
                    targetType = "screen",
                    boundsX = screen.boundsX,
                    boundsY = screen.boundsY,
                    boundsW = screen.boundsW,
                    boundsH = screen.boundsH
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
    val webLabel = stringResource(Res.string.projection_content_web)
    val qaLabel = stringResource(Res.string.tab_qa)
    val sttLabel = stringResource(Res.string.tab_stt)
    val sttTooltip = stringResource(Res.string.projection_content_stt_tooltip)
    val songLaLabel = stringResource(Res.string.projection_content_song_la)
    val backgroundLabel = stringResource(Res.string.projection_content_background)
    val ltBackgroundLabel = stringResource(Res.string.projection_content_lt_background)
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
        ContentCol(webLabel, { it.showWebsite }, { a, v -> a.copy(showWebsite = v) }, isWeb = true),
        ContentCol(canvasLabel, { it.showCanvas }, { a, v -> a.copy(showCanvas = v) }),
        ContentCol(qaLabel, { it.showQA }, { a, v -> a.copy(showQA = v) }),
        ContentCol(sttLabel, { it.showSTT }, { a, v -> a.copy(showSTT = v) }, tooltip = sttTooltip),
        ContentCol(dictionaryLabel, { it.showDictionary }, { a, v -> a.copy(showDictionary = v) }),
        ContentCol(backgroundLabel, { it.showFullscreenBackground }, { a, v -> a.copy(showFullscreenBackground = v) }),
        ContentCol(ltBackgroundLabel, { it.showLowerThirdBackground }, { a, v -> a.copy(showLowerThirdBackground = v) }),
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
    val lang1Label = stringResource(Res.string.screen_lang_language_1)
    val lang2Label = stringResource(Res.string.screen_lang_language_2)
    // translationNames stays the plain file-stem list: it's what every count/index computation
    // below keys off (selection indices, "N of M" summaries) and must line up 1:1 with
    // translationList()'s order regardless of whether a title could be read.
    val translationNames = settings.bibleSettings.translationList()
        .map { it.fileName.substringBeforeLast('.') }
    // Richer per-row display info for the Bible Translations picker only. Reads just the header
    // block of each .spb (title + which book IDs are present) via Bible.readTranslationSummary --
    // not the full verse parse -- so this stays cheap even with many translations installed.
    val otNtPortionLabel = stringResource(Res.string.content_bible_translation_portion_ot_nt)
    val ntPortionLabel = stringResource(Res.string.content_bible_translation_portion_nt)
    val otPortionLabel = stringResource(Res.string.content_bible_translation_portion_ot)
    val translationStack = settings.bibleSettings.translations
    val storageDirectory = settings.bibleSettings.storageDirectory
    // What the picker shows until the headers have been read: one row per configured translation,
    // each named by its file stem. Same shape and length as the finished list, so the picker's row
    // count and the positions a selection stores are right from the first frame.
    val unreadTranslationDisplays = remember(translationStack, storageDirectory) {
        translationNames.map { BibleTranslationDisplay(code = it, title = it, portion = "") }
    }
    // Reading a header is file I/O and has no business happening during composition. Kept null while
    // in flight -- and reset to null whenever the stack changes -- so a list read for the previous
    // stack is never shown against the current one, which would put the wrong number of rows in the
    // picker and misalign the positions a selection is stored as.
    val readTranslationDisplays by produceState<List<BibleTranslationDisplay>?>(
        initialValue = null,
        translationStack, storageDirectory, otNtPortionLabel, ntPortionLabel, otPortionLabel,
    ) {
        value = null
        value = withContext(Dispatchers.IO) {
            settings.bibleSettings.translationList().map { t ->
                val code = t.fileName.substringBeforeLast('.')
                val path = if (storageDirectory.isNotEmpty()) File(storageDirectory, t.fileName).absolutePath else t.fileName
                val summary = Bible.readTranslationSummary(path)
                val portion = when {
                    summary?.hasOldTestament == true && summary.hasNewTestament -> otNtPortionLabel
                    summary?.hasNewTestament == true -> ntPortionLabel
                    summary?.hasOldTestament == true -> otPortionLabel
                    else -> ""
                }
                BibleTranslationDisplay(
                    code = code,
                    title = summary?.title?.takeIf { it.isNotBlank() } ?: code,
                    portion = portion,
                )
            }
        }
    }
    val translationDisplays = readTranslationDisplays ?: unreadTranslationDisplays
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
                        LabeledSwitch(
                            checked = output.browserSourceEnabled,
                            onCheckedChange = { checked ->
                                val updated = output.copy(browserSourceEnabled = checked)
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                }
                            },
                            label = stringResource(Res.string.browser_source_enabled),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            spacing = 4.dp,
                        )
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
                                Text(stringResource(Res.string.remove), color = MaterialTheme.colorScheme.error)
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
                                val browserSourceLabel = stringResource(Res.string.browser_source_output_label, i + 1)
                                ContentOutputsDialog(
                                    title = stringResource(Res.string.content_outputs_for, browserSourceLabel),
                                    screenLabel = browserSourceLabel,
                                    assignment = output,
                                    contentGroup = contentGroup,
                                    backgroundGroup = backgroundGroup,
                                    bibleLabel = bibleLabel,
                                    songsLabel = songsLabel,
                                    translationNames = translationNames,
                                    translationDisplays = translationDisplays,
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
    val tooltip: String? = null,
    /** Marks the Web column — its label is localized, so it can't be identified by text. */
    val isWeb: Boolean = false
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
private fun ContentOutputsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = modifier,
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

/**
 * Test tags for the per-output Bible translation picker.
 *
 * Every part of this cell names itself with derived text — the trigger shows a summary of the
 * current selection, and each row shows a code and title read out of the .spb file — so a caption
 * is not a stable way to address any of them. These tags name what a control *is* instead.
 */
internal object TranslationPickerTags {
    /** The collapsed trigger segment that opens the picker. */
    const val TRIGGER = "contentOutputs_bibleTranslationTrigger"

    /** The master on/off row at the top of the open picker. */
    const val MASTER = "contentOutputs_bibleTranslationMaster"

    /** The row for the translation at stack position [index]. */
    fun row(index: Int) = "contentOutputs_bibleTranslationRow_$index"
}

/**
 * Bible content cell: a compact two-segment trigger button (on/off, then the current translation
 * pick) that opens a floating panel with the full translation list -- collapsed by default rather
 * than always showing the full picker inline, so it reads the same as any other content-outputs
 * row until the operator actually needs to change translations.
 */
@Composable
private fun ContentTranslationCell(
    modifier: Modifier,
    label: String,
    /** The configured stack, in order; selection indices below refer to this order. */
    translations: List<BibleTranslationDisplay>,
    showing: Boolean,
    selected: List<Int>,
    onShowingChange: (Boolean) -> Unit,
    onSelectedChange: (List<Int>) -> Unit,
    /**
     * Turns this output's Bible content on AND sets its selection, atomically.
     *
     * [onShowingChange] and [onSelectedChange] each round-trip through the caller's own
     * `assignment.copy(...)` closure. Calling two of them back to back in one handler -- as
     * "turn on and select everything" needs -- has both read the *same* pre-click `assignment`
     * snapshot, since Compose does not recompose between two synchronous calls in one handler; the
     * second call's `.copy(...)` then overwrites the first's change instead of building on it. This
     * callback exists so callers can apply both fields in a single `assignment.copy(...)`.
     */
    onShowAndSelect: (List<Int>) -> Unit,
) {
    // Which translations this output actually shows, as positions that exist in the stack it is being
    // shown against. Everything below counts, labels, ticks and writes from this rather than from
    // `selected`, so a position past the end of the stack -- left in a settings file written before
    // the stack edits started remapping selections, or by hand -- is ignored consistently. Counting
    // one used to make the menu claim "2 of 3 translations enabled" over a single ticked row, while
    // the preview chip beside it named just the one.
    //
    // A stored selection that has been emptied this way shows nothing, not everything: it named
    // translations that have gone, which is not the same statement as the empty "all of them", and
    // reading it as "all" would put every language on a screen deliberately narrowed to one. That is
    // the same call TranslationStackEdits makes when a remap leaves an output with nothing.
    val tickedPositions = if (selected.isEmpty()) translations.indices.toList()
                          else selected.filter { it in translations.indices }
    val allSelected = tickedPositions.size == translations.size
    val enabledCount = if (!showing) 0 else tickedPositions.size
    val selectAll: () -> Unit = { onShowAndSelect(emptyList()) }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    var dropdownOpen by remember { mutableStateOf(false) }

    val selectionCount = tickedPositions.size
    val primaryIndex = tickedPositions.minOrNull() ?: 0
    // null while off, not just when there's nothing configured: otherwise this kept previewing
    // the last-selected translation's code/portion after Bible was switched off for this output,
    // instead of reflecting that nothing is actually showing right now.
    val primaryInfo = if (showing) translations.getOrNull(primaryIndex) else null
    // No fallback to `label` here: with zero translations configured there is nothing to name in
    // this segment, and falling back to `label` would repeat the left segment's own text.
    val allTranslationsSelected = selectionCount > 1 && selectionCount == translations.size
    val primaryLabel = when {
        primaryInfo == null -> ""
        allTranslationsSelected -> stringResource(Res.string.content_bible_translations_all_selected)
        else -> primaryInfo.code
    }
    val secondaryLabel = when {
        primaryInfo == null -> ""
        allTranslationsSelected -> stringResource(Res.string.content_bible_translations_count_enabled, selectionCount)
        selectionCount > 1 -> stringResource(Res.string.content_bible_translations_more, selectionCount - 1)
        else -> primaryInfo.portion
    }

    Box(modifier = modifier) {
        // Collapsed trigger: the left segment is a status indicator only (on/off lives on the
        // master row's checkbox inside the dropdown below); the whole rest of the button opens
        // that dropdown, regardless of whether Bible content is currently on or off, so a
        // translation can be picked before switching it on.
        val triggerShape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .alpha(if (showing) 1f else 0.55f)
                .clip(triggerShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Always clickable, even with zero translations configured: the master on/off
                    // row now lives inside this dropdown (not on the collapsed trigger), so gating
                    // this on translations.isNotEmpty() would leave no way at all to reach it in
                    // that case.
                    .clickable { dropdownOpen = true }
                    // Tagged rather than found by caption: this segment's text is a derived summary
                    // ("All Bibles", a code, "+N more") that changes with the selection, and a
                    // single-selection caption repeats the code its own menu row shows.
                    .testTag(TranslationPickerTags.TRIGGER)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // fill = true (the default): the label column claims all the leftover width so
                // the chevron lands flush against the button's trailing edge instead of sitting
                // right after however wide the label happens to be.
                Column(modifier = Modifier.weight(1f)) {
                    if (primaryLabel.isNotEmpty()) {
                        Text(
                            text = primaryLabel,
                            style = MaterialTheme.typography.labelMedium,
                            // Monospace suits a file-stem code like "kjv1769" but not a plain
                            // phrase like "All Bibles".
                            fontFamily = if (allTranslationsSelected) FontFamily.Default else FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (secondaryLabel.isNotEmpty()) {
                        Text(
                            text = secondaryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    // Otherwise unlabelled when there's nothing configured to name (primary/
                    // secondary text both blank) -- this is also what tests target to open the
                    // dropdown in that case.
                    contentDescription = stringResource(Res.string.content_bible_translations_header),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        DropdownMenu(
            expanded = dropdownOpen,
            onDismissRequest = { dropdownOpen = false },
            modifier = Modifier.width(320.dp),
            // Style the menu's own surface directly rather than nesting a second background
            // inside it -- DropdownMenu's default container has its own vertical inset around
            // whatever content() renders, which showed through as a visible band above and below
            // an inner Column that tried to draw its own separately-shaped/colored background.
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
        ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContentOutputsSectionHeader(stringResource(Res.string.content_bible_translations_header))
            Spacer(modifier = Modifier.weight(1f))
            // Plain clickable Text pills rather than Button/OutlinedButton: those enforce a 58dp
            // minWidth floor that, in this narrow card, starved whichever pill measured last down
            // to ~0dp and made its label wrap one letter per line.
            Text(
                text = stringResource(Res.string.content_bible_translations_all),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .clickable(onClick = selectAll)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(Res.string.clear),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                    .clickable(onClick = { onShowingChange(false) })
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(color = dividerColor)

        // Master "Bible" row
        val masterCheckShape = RoundedCornerShape(5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (enabledCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f) else Color.Transparent)
                // triStateToggleable (not plain clickable) so this still publishes the
                // ToggleableState semantics TriStateCheckbox used to -- tests locate this
                // control via isToggleable().
                .triStateToggleable(
                    state = when {
                        !showing -> ToggleableState.Off
                        allSelected -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    },
                    onClick = { if (showing) onShowingChange(false) else selectAll() },
                )
                .testTag(TranslationPickerTags.MASTER)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(Res.string.content_bible_translations_enabled, enabledCount, translations.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(masterCheckShape)
                    .background(if (enabledCount > 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.dp, if (enabledCount > 0) Color.Transparent else MaterialTheme.colorScheme.outline, masterCheckShape),
                contentAlignment = Alignment.Center,
            ) {
                if (allSelected && showing) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(11.dp))
                } else if (enabledCount > 0) {
                    Box(modifier = Modifier.size(width = 8.dp, height = 2.dp).background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(1.dp)))
                }
            }
        }

        // Nothing to choose between with a single translation -- the row above already says
        // whether this output shows it. Shown regardless of `showing`: picking translations must
        // work whether or not Bible content is currently switched on for this output.
        if (translations.size > 1) {
            HorizontalDivider(color = dividerColor)
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                translations.forEachIndexed { index, info ->
                    val selectedIn = index in tickedPositions
                    // Off means every row reads as unticked, matching the master row's own
                    // checkbox -- the underlying selection is still remembered in `selected`,
                    // just not shown as active while Bible is off for this output.
                    val ticked = showing && selectedIn
                    val toggle: () -> Unit = {
                        if (!showing) {
                            // Starting fresh from off, a click means "show just this one" -- it must
                            // NOT fold in whatever `selected` happened to hold before Bible was
                            // switched off. `selected` is frequently the empty-list "all" sentinel
                            // at that point (e.g. right after "Clear", which only flips `showing`),
                            // and building the new selection from "all indices" + this one collapses
                            // straight back to that same sentinel -- every row re-selecting itself
                            // the moment any one of them was clicked.
                            onShowAndSelect(listOf(index))
                        } else if (!selectedIn) {
                            val next = (tickedPositions + index).distinct().sorted()
                            // Everything ticked is stored as "all", so a translation added later
                            // shows up here too rather than needing to be ticked on every output.
                            onSelectedChange(if (next.size == translations.size) emptyList() else next)
                        } else {
                            val next = tickedPositions.filterNot { it == index }
                            if (next.isEmpty()) {
                                // Unchecking the last remaining translation would store the same
                                // empty list that means "all" everywhere else in this cell -- next
                                // render, every row would read back as ticked again. Turning Bible
                                // off instead is what "nothing selected" actually means, and leaves
                                // `selected` untouched (every "turn on" path already resets it, so
                                // nothing is lost).
                                onShowingChange(false)
                            } else {
                                onSelectedChange(next)
                            }
                        }
                    }
                    val chipShape = RoundedCornerShape(6.dp)
                    val rowCheckShape = RoundedCornerShape(5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (ticked) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f) else Color.Transparent)
                            .clickable(onClick = toggle)
                            // Tagged by stack position, which is what a selection actually stores;
                            // the code and title beside it are file-derived and repeat elsewhere.
                            .testTag(TranslationPickerTags.row(index))
                            .padding(start = 30.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            // Fixed width (not just a minimum): a longer code like "kjv1769"
                            // must not push its row's title column further right than every
                            // other row's, which is what a min-only width let happen.
                            modifier = Modifier
                                .width(58.dp)
                                .height(26.dp)
                                .clip(chipShape)
                                .background(if (ticked) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (ticked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant, chipShape)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = info.code,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (ticked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // fill = true (the default): the title column claims all the leftover
                        // width so the checkbox lands flush against the row's trailing edge
                        // instead of sitting right after however wide the title happens to be.
                        // Safe here (unlike the header pills earlier) because this wraps plain
                        // Text with maxLines=1 + ellipsis, not a component with a mandatory
                        // min-width that could be squeezed to zero.
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = info.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (ticked) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (ticked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // PRIMARY sits on its own line with the portion rather than competing
                            // with the title for width -- a long title (e.g. "King James Version")
                            // was getting cut to "King James V..." to make room for the tag.
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (info.portion.isNotEmpty()) {
                                    Text(
                                        text = info.portion,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                                if (index == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.song_language_primary),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(rowCheckShape)
                                .background(if (ticked) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .border(1.dp, if (ticked) Color.Transparent else MaterialTheme.colorScheme.outline, rowCheckShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ticked) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = dividerColor)
        Text(
            text = stringResource(Res.string.content_bible_translations_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
        } // DropdownMenu
    } // outer Box
}

/**
 * Songs content cell: a collapsed trigger (current mode + chevron) that opens a floating panel
 * listing every mode -- styled to match [ContentTranslationCell] so the two cells read as one
 * family rather than two different pickers side by side. Unlike Bible's checklist, mode selection
 * is single-choice (Off counts as a mode, not a separate on/off dimension), so each row is a plain
 * radio-style pick that both selects and closes the panel.
 */
@Composable
private fun ContentLangCell(
    modifier: Modifier,
    label: String,
    modes: List<Pair<String, String>>,
    currentMode: String,
    onSelect: (String) -> Unit,
) {
    var dropdownOpen by remember { mutableStateOf(false) }
    val currentLabel = modes.find { it.first == currentMode }?.second ?: modes.first().second
    val isOff = currentMode == Constants.SONG_LANG_OFF
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Box(modifier = modifier) {
        // Left segment is a plain label, not a click target -- matching Bible's trigger, where
        // only the value/chevron side opens anything.
        val triggerShape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .alpha(if (isOff) 0.55f else 1f)
                .clip(triggerShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        ) {
            Row(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { dropdownOpen = true }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        DropdownMenu(
            expanded = dropdownOpen,
            onDismissRequest = { dropdownOpen = false },
            modifier = Modifier.width(220.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
        ) {
            ContentOutputsSectionHeader(
                text = label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            )
            HorizontalDivider(color = dividerColor)
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                modes.forEach { (value, modeLabel) ->
                    val isSelected = value == currentMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f) else Color.Transparent)
                            .clickable {
                                dropdownOpen = false
                                onSelect(value)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // Radio, not checkbox: modes are mutually exclusive (a single pick, Off
                        // included), unlike Bible's independently-toggleable translations.
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Monitor mock summarising what an output actually shows: every enabled content type (and the
 * Bible/Songs language mode) is drawn as a chip inside a 16:9 screen, so the operator can read the
 * result at a glance instead of scanning a long checkbox list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentOutputsMonitorPreview(
    modifier: Modifier,
    screenLabel: String,
    assignment: ScreenAssignment,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>,
    bibleLabel: String,
    songsLabel: String,
    translationNames: List<String>,
    songLangModes: List<Pair<String, String>>,
) {
    val bibleListFormat = stringResource(Res.string.content_outputs_preview_translations)
    val chips = buildList {
        if (assignment.showBible) {
            // An empty selection means every translation, so the whole stack is shown.
            val shownNames = if (assignment.bibleTranslations.isEmpty()) translationNames
                              else assignment.bibleTranslations.filter { it in translationNames.indices }.map { translationNames[it] }
            add(
                if (translationNames.size > 1 && shownNames.isNotEmpty())
                    bibleListFormat.format(bibleLabel, shownNames.joinToString(", "))
                else bibleLabel
            )
        }
        if (assignment.songMode != Constants.SONG_LANG_OFF) {
            val mode = songLangModes.find { it.first == assignment.songMode }?.second
            add(if (mode != null) "$songsLabel · $mode" else songsLabel)
        }
        (contentGroup + backgroundGroup).forEach { if (it.getter(assignment)) add(it.label) }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContentOutputsSectionHeader(stringResource(Res.string.content_outputs_preview))
        Spacer(modifier = Modifier.height(8.dp))
        // Bezel
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(6.dp)
        ) {
            // 16:9 is the MINIMUM height — with many content types enabled the chips need more
            // room, and a hard aspectRatio would clip them out of sight.
            val screenMinHeight = maxWidth * 9f / 16f
            // Screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = screenMinHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black)
                    .padding(8.dp)
            ) {
                Text(
                    text = screenLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (chips.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.content_outputs_preview_empty),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        chips.forEach { chip ->
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        // Stand
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(8.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier
                .width(88.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
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
    val isWeb = col.isWeb
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
    screenLabel: String,
    assignment: ScreenAssignment,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>,
    bibleLabel: String,
    songsLabel: String,
    translationNames: List<String>,
    translationDisplays: List<BibleTranslationDisplay>,
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
        modifier = Modifier.width(840.dp),
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
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
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
                                bibleMode = Constants.SONG_LANG_BOTH,
                                // An empty list means "every translation" -- Select All must reset
                                // this too, or a translation deselected earlier stays deselected
                                // even though the button says "all".
                                bibleTranslations = emptyList(),
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
                    ContentTranslationCell(
                        modifier = Modifier.weight(1f),
                        label = bibleLabel,
                        translations = translationDisplays,
                        showing = assignment.showBible,
                        selected = assignment.bibleTranslations,
                        onShowingChange = { on ->
                            onApply(
                                assignment.copy(
                                    bibleMode = if (on) Constants.SONG_LANG_BOTH else Constants.SONG_LANG_OFF,
                                ),
                            )
                        },
                        onSelectedChange = { next -> onApply(assignment.copy(bibleTranslations = next)) },
                        onShowAndSelect = { next ->
                            onApply(
                                assignment.copy(
                                    bibleMode = Constants.SONG_LANG_BOTH,
                                    bibleTranslations = next,
                                ),
                            )
                        },
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

            // Live summary of what this output actually shows, drawn inside a monitor mock.
            ContentOutputsMonitorPreview(
                modifier = Modifier.width(280.dp),
                screenLabel = screenLabel,
                assignment = assignment,
                contentGroup = contentGroup,
                backgroundGroup = backgroundGroup,
                bibleLabel = bibleLabel,
                songsLabel = songsLabel,
                translationNames = translationNames,
                songLangModes = songLangModes,
            )
          }
        },
        confirmButton = {
            Button(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                Text(stringResource(Res.string.content_outputs_done), style = MaterialTheme.typography.labelSmall)
            }
        }
    )
}

