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
import churchpresenter.composeapp.generated.resources.loading
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
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.TvScreenBox
import org.churchpresenter.app.churchpresenter.composables.detectVlcInstallPath
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.isVlcLoadFailed
import org.churchpresenter.app.churchpresenter.composables.listVlcAudioDevices
import org.churchpresenter.app.churchpresenter.composables.ScanningRow
import org.churchpresenter.app.churchpresenter.composables.VlcAudioDevice
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

/** One selectable output target: None, a physical display, or a DeckLink device. */
internal data class DisplayOption(
    val label: String,
    val shortLabel: String = label,
    val targetDisplay: Int,  // -2 = none, 0+ = display/device index
    val targetType: String,  // "screen" or "decklink"
    val boundsX: Int = Int.MIN_VALUE,
    val boundsY: Int = Int.MIN_VALUE,
    val boundsW: Int = 0,
    val boundsH: Int = 0
)

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

    // Display target options are built below; the type is top-level so the extracted
    // ScreenAssignmentCard can take them as a parameter.

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
    // Reserves 2 lines of bodySmall (16.sp line height) so single-line labels (Bible, display
    // mode, etc.) sit flush with the bottom of the tallest label (e.g. "Pictures/Presentation",
    // which wraps to 2 lines) — keeping every checkbox/radio button in the row aligned.

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(end = SettingsScrollbarGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
    ScreenAssignmentCard(
        settings = settings,
        onSettingsChange = onSettingsChange,
        onIdentifyScreen = onIdentifyScreen,
        scenes = scenes,
        screenDevicesAll = screenDevicesAll,
        detectedScreens = detectedScreens,
        devWindowCount = devWindowCount,
        devWindowedFallback = devWindowedFallback,
        presenterWindowCount = presenterWindowCount,
        numScreens = numScreens,
        screenAssignments = screenAssignments,
        displayOptions = displayOptions,
        noneLabel = noneLabel,
        contentGroup = contentGroup,
        backgroundGroup = backgroundGroup,
        displayModes = displayModes,
        songLangModes = songLangModes,
        translationDisplays = translationDisplays,
        translationNames = translationNames,
    )

    BrowserSourceOutputsCard(
        settings = settings,
        onSettingsChange = onSettingsChange,
        companionServer = companionServer,
        onIdentifyBrowserSource = onIdentifyBrowserSource,
        contentGroup = contentGroup,
        backgroundGroup = backgroundGroup,
        displayModes = displayModes,
        songLangModes = songLangModes,
        translationDisplays = translationDisplays,
        translationNames = translationNames,
    )

    // ── Card 2: Audio Output ─────────────────────────────────────────────────
    SettingsSection(title = stringResource(Res.string.audio_output)) {

        var vlcDetected by remember { mutableStateOf(isVlcAvailable) }
        var vlcPathText by remember { mutableStateOf(proj.vlcPath) }
        var vlcPathError by remember { mutableStateOf(false) }

        // Both of these ask VLC itself — the slowest thing this dialog does, and it used to happen
        // inline in composition, so opening the Projection tab stalled on it.
        LaunchedEffect(Unit) {
            if (vlcPathText.isBlank()) {
                val detected = withContext(Dispatchers.IO) { detectVlcInstallPath() }
                if (vlcPathText.isBlank()) vlcPathText = detected
            }
        }

        if (vlcDetected) {
            // Null while VLC is being asked. The configured device's own name only exists in this
            // list, so an empty list would label it "Default" — a wrong answer, not a pending one.
            val audioDevices by produceState<List<VlcAudioDevice>?>(null, vlcDetected) {
                value = withContext(Dispatchers.IO) { listVlcAudioDevices() }
            }
            val defaultLabel = stringResource(Res.string.audio_output_default)

            if (audioDevices == null) ScanningRow(stringResource(Res.string.loading))
            else Row(
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
                    val currentDevice = audioDevices.orEmpty().find { it.id == proj.audioOutputDeviceId }
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
                        audioDevices.orEmpty().forEach { device ->
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
    SettingsScrollbar(scrollState)
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
