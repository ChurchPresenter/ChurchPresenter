@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.utils.sharedScaleMode
import org.churchpresenter.app.churchpresenter.utils.ScaleButtonContent
import org.churchpresenter.app.churchpresenter.utils.scaleButtonLabel
import org.churchpresenter.app.churchpresenter.utils.withMediaScaleEverywhere
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import org.churchpresenter.theme.components.RaisedIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.save_preset
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.clear_recents
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_fast_forward
import churchpresenter.composeapp.generated.resources.ic_fast_rewind
import churchpresenter.composeapp.generated.resources.ic_folder
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_refresh
import churchpresenter.composeapp.generated.resources.ic_stop
import churchpresenter.composeapp.generated.resources.ic_subtitles
import churchpresenter.composeapp.generated.resources.ic_volume_off
import churchpresenter.composeapp.generated.resources.ic_volume_up
import churchpresenter.composeapp.generated.resources.loop_off
import churchpresenter.composeapp.generated.resources.loop_on
import churchpresenter.composeapp.generated.resources.media_audio_continues
import churchpresenter.composeapp.generated.resources.media_files_filter
import churchpresenter.composeapp.generated.resources.media_load
import churchpresenter.composeapp.generated.resources.media_loop_count
import churchpresenter.composeapp.generated.resources.media_loop_count_tooltip
import churchpresenter.composeapp.generated.resources.media_local_file
import churchpresenter.composeapp.generated.resources.media_mute
import churchpresenter.composeapp.generated.resources.media_network_url
import churchpresenter.composeapp.generated.resources.media_no_source
import churchpresenter.composeapp.generated.resources.media_now_playing
import churchpresenter.composeapp.generated.resources.media_now_presenting
import churchpresenter.composeapp.generated.resources.media_subtitles
import churchpresenter.composeapp.generated.resources.media_subtitles_files
import churchpresenter.composeapp.generated.resources.media_subtitles_load_file
import churchpresenter.composeapp.generated.resources.media_seek_backward
import churchpresenter.composeapp.generated.resources.media_seek_forward
import churchpresenter.composeapp.generated.resources.media_select_file
import churchpresenter.composeapp.generated.resources.media_select_to_begin
import churchpresenter.composeapp.generated.resources.media_unmute
import churchpresenter.composeapp.generated.resources.media_url_placeholder
import churchpresenter.composeapp.generated.resources.media_vlc_arch_mismatch
import churchpresenter.composeapp.generated.resources.media_vlc_install
import churchpresenter.composeapp.generated.resources.media_vlc_load_failed
import churchpresenter.composeapp.generated.resources.media_vlc_required
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import churchpresenter.composeapp.generated.resources.recent
import churchpresenter.composeapp.generated.resources.stop
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import org.churchpresenter.app.churchpresenter.LocalWentLive
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.SavePresetButton
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputPicker
import org.churchpresenter.app.churchpresenter.composables.rememberPreviewOutput
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.SharedVideoOutputDisplay
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.SoftwareVideoPlayer
import org.churchpresenter.app.churchpresenter.composables.VideoPlayer
import org.churchpresenter.app.churchpresenter.composables.isVlcArchMismatch
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.isVlcLoadFailed
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.followerMediaUrl
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.settings.OutputScaleMode
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.utils.contentScale
import org.churchpresenter.app.churchpresenter.utils.icon
import org.churchpresenter.app.churchpresenter.utils.label
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlinx.coroutines.launch
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken
import org.churchpresenter.app.churchpresenter.composables.RecentChip

private const val HANDLE_VISIBLE_ALPHA = 0.01f

/** Upper bound of the loop-count field; 0 means repeat forever. */
private const val MAX_LOOP_COUNT = 99
private const val DISABLED_TRANSPORT_ALPHA = 0.38f
private val TRANSPORT_KEY_SIZE = 30.dp
private val PLAY_KEY_SIZE = 38.dp
private val VOLUME_SLIDER_WIDTH = 150.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((mediaUrl: String, mediaTitle: String, mediaType: String, subtitleUrl: String) -> Unit)? = null,
    /** Save preset, to the left of Add to Schedule: the same media, kept for the Calendar Manager. */
    onSavePreset: ((mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit)? = null,
    selectedMediaItem: ScheduleItem.MediaItem? = null,
    /**
     * Bumped by the caller on every schedule click, so clicking the *same* item twice re-runs the
     * effect below. Keyed on the item alone, an unchanged item is an unchanged key and the second
     * click does nothing.
     */
    selectedMediaItemVersion: Int = 0,
    presenterManager: PresenterManager? = null,
    /** Non-null while connected via Instance Link — builds the primary's /api/media/stream URL for
     *  a given schedule item id, used in place of a schedule item's local file path since that path
     *  only exists on the primary's disk. */
    instanceLinkMediaStreamUrl: ((itemId: String) -> String)? = null,
    /** Instance Link Controller mode — non-null only when connected and controlling. Sends via
     *  PROJECT; only remote URLs (youtube/vimeo) will actually play on the primary — a "local" file
     *  path only exists on this machine's disk, not the primary's, a known limitation. */
    onInstanceLinkSendProject: ((ScheduleItem) -> Unit)? = null,
    /**
     * Whether VLC is usable, and if not, why.
     *
     * Parameters rather than reads of the globals in `VideoPlayer.kt`, which cache their answer in a
     * process-wide field: what this tab renders would otherwise depend on whether the machine running
     * it happens to have VLC installed, so neither branch could be tested deterministically. Same seam
     * `WebTab` uses for `cefInitialized`. The defaults are the real checks, so callers see no change.
     */
    vlcAvailable: Boolean = isVlcAvailable,
    vlcArchMismatch: Boolean = isVlcArchMismatch,
    vlcLoadFailed: Boolean = isVlcLoadFailed,
) {
    val scope = rememberCoroutineScope()

    if (!vlcAvailable) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (vlcArchMismatch || vlcLoadFailed) Icons.Default.Warning else Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = if (vlcArchMismatch || vlcLoadFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(stringResource(Res.string.media_vlc_required), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        vlcArchMismatch -> stringResource(Res.string.media_vlc_arch_mismatch)
                        vlcLoadFailed -> stringResource(Res.string.media_vlc_load_failed)
                        else -> stringResource(Res.string.media_vlc_install)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val viewModel = LocalMediaViewModel.current ?: return
    val focusRequester = remember { FocusRequester() }

    val localFileLabel = stringResource(Res.string.media_local_file)
    val networkUrlLabel = stringResource(Res.string.media_network_url)
    val sourceTypeItems = remember(localFileLabel, networkUrlLabel) {
        listOf(
            SegmentedButtonItem(Constants.MEDIA_TYPE_LOCAL, localFileLabel),
            SegmentedButtonItem(Constants.MEDIA_TYPE_URL, networkUrlLabel)
        )
    }
    var selectedSourceType by remember { mutableStateOf(Constants.MEDIA_TYPE_LOCAL) }
    var urlInput by remember { mutableStateOf("") }
    val selectFileLabel = stringResource(Res.string.media_select_file)
    val mediaFilesLabel = stringResource(Res.string.media_files_filter)

    LaunchedEffect(selectedMediaItem, selectedMediaItemVersion) {
        selectedMediaItem?.let {
            // Blanked while the new file loads, so the output does not sit on the last frame of the
            // old one — and put back at the end of this block. Both halves matter: `setPresentingMode`
            // is the only thing that resets the clear flag, and it is not called again here because
            // the mode is already MEDIA. Presenting a media row from the schedule sets the mode in
            // the same handler that sets the item, so this effect always found itself "already
            // live", always blanked, and nothing ever turned it back on. (#602)
            val wasLive = presenterManager?.presentingMode?.value == Presenting.MEDIA
            if (wasLive) presenterManager.requestClearDisplay()
            when (it.mediaType) {
                Constants.MEDIA_TYPE_URL -> { selectedSourceType = Constants.MEDIA_TYPE_URL; urlInput = it.mediaUrl }
                else -> selectedSourceType = Constants.MEDIA_TYPE_LOCAL
            }
            // Local file when it resolves here, the primary's stream when it doesn't — see
            // followerMediaUrl.
            val effectiveUrl = followerMediaUrl(
                mediaType = it.mediaType,
                localUrl = it.mediaUrl,
                remoteStreamUrl = instanceLinkMediaStreamUrl?.invoke(it.id)
            )
            viewModel.loadMediaFromSchedule(
                url = effectiveUrl,
                title = it.mediaTitle,
                type = it.mediaType,
                subtitleUrl = it.subtitleUrl
            )
            // The other half of the blanking above: the new file is loaded, so show it.
            if (wasLive) presenterManager.setPresentingMode(Presenting.MEDIA)
            focusRequester.requestFocus()
        }
    }

    val shortcuts = LocalShortcuts.current
    val wentLive = LocalWentLive.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when {
                        // Clear Output is a global action, but it is claimed here too so the media
                        // is paused before the display clears — the root handler pauses via a
                        // nullable ViewModel reference that this tab already holds directly.
                        shortcuts.matches(ShortcutAction.CLEAR_OUTPUT, keyEvent) && presenterManager != null -> {
                            viewModel.pause(); presenterManager.requestClearDisplay(); true
                        }
                        viewModel.isLoaded && shortcuts.matches(ShortcutAction.MEDIA_PLAY_PAUSE, keyEvent) -> { viewModel.togglePlayPause(); true }
                        viewModel.isLoaded && shortcuts.matches(ShortcutAction.MEDIA_MUTE, keyEvent) -> { viewModel.toggleMute(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        // ── Source bar ────────────────────────────────────────────────
        // FlowRow rather than Row: Media carries a source-type SegmentedButton that neither the
        // Pictures nor the Presentation bar has, so at a narrow panel width the fixed content
        // overruns 48.dp of a single line and the action buttons would be clipped off the right
        // edge. Wrapping degrades instead. At any ordinary width this renders exactly the 48.dp
        // single-line bar those two tabs use.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            // The centre alignment matters: a bare spacedBy anchors the lines to the top of the
            // heightIn box, so the controls sat high in the bar instead of centred in it.
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            SegmentedButton(
                items = sourceTypeItems,
                selectedValue = selectedSourceType,
                onValueChange = { selectedSourceType = it },
                buttonWidth = 90.dp,
                buttonHeight = 32.dp,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )

            when (selectedSourceType) {
                Constants.MEDIA_TYPE_LOCAL -> {
                    RaisedButton(
                        onClick = {
                            scope.launch {
                                val f = FileChooser.platformInstance.chooseSingle(
                                    path = Path(appSettings.mediaStorageDirectory),
                                    title = selectFileLabel,
                                    filters = listOf(FileNameExtensionFilter(mediaFilesLabel, "mp4","mov","avi","mkv","wmv","flv","webm","m4v","mp3","wav","flac","aac","ogg","wma","m4a","aiff","opus")),
                                    selectDirectory = false
                                )
                                if (f != null) {
                                    val ext = f.extension.lowercase()
                                    val type = if (ext in Constants.AUDIO_EXTENSIONS) Constants.MEDIA_TYPE_AUDIO else Constants.MEDIA_TYPE_LOCAL
                                    if (presenterManager?.presentingMode?.value == Presenting.MEDIA) presenterManager.requestClearDisplay()
                                    viewModel.loadMedia(f.absolutePathString(), type)
                                    RecentMediaFiles.add(f.absolutePathString())
                                }
                            }
                        },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(7.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Icon(painterResource(Res.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(Res.string.media_select_file), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
                    }
                    Text(
                        text = if (viewModel.isLoaded && viewModel.mediaType != Constants.MEDIA_TYPE_URL) viewModel.mediaTitle
                               else stringResource(Res.string.media_no_source),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (viewModel.isLoaded && viewModel.mediaType != Constants.MEDIA_TYPE_URL)
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Constants.MEDIA_TYPE_URL -> {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .sunken(RoundedCornerShape(8.dp), elevationPalette())
                            .hoverTint(RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            BasicTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (urlInput.isEmpty()) {
                                        Text(stringResource(Res.string.media_url_placeholder), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                    RaisedButton(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                if (presenterManager?.presentingMode?.value == Presenting.MEDIA) presenterManager.requestClearDisplay()
                                val url = urlInput.trim()
                                viewModel.loadMedia(url, Constants.MEDIA_TYPE_URL)
                                RecentMediaFiles.add(url)
                            }
                        },
                        enabled = urlInput.isNotBlank(),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(7.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(Res.string.media_load), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
            // The "now playing" label travels with the two action buttons as one group, so the
            // status and the controls it describes wrap together rather than splitting across
            // lines at a narrow width.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (viewModel.isLoaded) {
                    Text(
                        text = stringResource(Res.string.media_now_playing),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (onSavePreset != null) {
                    SavePresetButton(
                        onClick = { onSavePreset(viewModel.mediaUrl, viewModel.mediaTitle, viewModel.mediaType) },
                        enabled = viewModel.isLoaded,
                        tooltipText = stringResource(Res.string.save_preset)
                    )
                }
                if (onAddToSchedule != null) {
                    AddToScheduleButton(
                        onClick = {
                            onAddToSchedule(
                                viewModel.mediaUrl, viewModel.mediaTitle, viewModel.mediaType, viewModel.subtitleUrl
                            )
                        },
                        enabled = viewModel.isLoaded,
                        tooltipText = stringResource(Res.string.add_to_schedule)
                    )
                }
                if (presenterManager != null) {
                    GoLiveButton(
                        onClick = {
                            presenterManager.setPresentingMode(Presenting.MEDIA)
                            presenterManager.setShowPresenterWindow(true)
                            presenterManager.setCurrentMedia(viewModel.mediaUrl, viewModel.mediaType)
                            viewModel.play()
                            wentLive(
                                ScheduleItem.MediaItem(
                                    id = java.util.UUID.randomUUID().toString(),
                                    mediaUrl = viewModel.mediaUrl,
                                    mediaTitle = viewModel.mediaTitle,
                                    mediaType = viewModel.mediaType,
                                    subtitleUrl = viewModel.subtitleUrl,
                                )
                            )
                            onInstanceLinkSendProject?.invoke(
                                ScheduleItem.MediaItem(
                                    id = java.util.UUID.randomUUID().toString(),
                                    mediaUrl = viewModel.mediaUrl,
                                    mediaTitle = viewModel.mediaTitle,
                                    mediaType = viewModel.mediaType,
                                    subtitleUrl = viewModel.subtitleUrl
                                )
                            )
                        },
                        enabled = viewModel.isLoaded,
                        tooltipText = stringResource(Res.string.go_live)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Recent files bar ──────────────────────────────────────────
        val recentOrdered = RecentMediaFiles.pinned + RecentMediaFiles.paths.filter { it !in RecentMediaFiles.pinned }
        if (recentOrdered.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(Res.string.recent), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.clear_recents), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    KeyIconButton(onClick = { RecentMediaFiles.clear() }, modifier = Modifier.size(20.dp)) {
                        Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.clear), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    lazyItems(recentOrdered) { path ->
                        val isPinned = path in RecentMediaFiles.pinned
                        val isActive = viewModel.isLoaded && viewModel.mediaUrl == path
                        val displayName = if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("rtsp://")) path else java.io.File(path).name
                        RecentChip(
                            name = displayName,
                            isActive = isActive,
                            isPinned = isPinned,
                            onOpen = {
                                val ext = java.io.File(path).extension.lowercase()
                                val type = when {
                                    path.startsWith("http://") || path.startsWith("https://") ||
                                        path.startsWith("rtsp://") -> Constants.MEDIA_TYPE_URL
                                    ext in Constants.AUDIO_EXTENSIONS -> Constants.MEDIA_TYPE_AUDIO
                                    else -> Constants.MEDIA_TYPE_LOCAL
                                }
                                if (presenterManager?.presentingMode?.value == Presenting.MEDIA) presenterManager.requestClearDisplay()
                                viewModel.loadMedia(path, type)
                                RecentMediaFiles.add(path)
                            },
                            onTogglePin = { RecentMediaFiles.togglePin(path) },
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        // ── Playback controls bar ─────────────────────────────────────
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 5.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            // The centre alignment matters: a bare spacedBy anchors the lines to the top of the
            // heightIn box, so the controls sat high in the bar instead of centred in it.
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            // One tint for every transport control, so the enabled/disabled ramp cannot drift
            // between the rewind, stop, forward and volume buttons.
            val transportTint = MaterialTheme.colorScheme.onSurface
                .copy(alpha = if (viewModel.isLoaded) 1f else DISABLED_TRANSPORT_ALPHA)
            val keyColors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = transportTint,
                disabledContentColor = transportTint,
            )
            val litKeyColors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContentColor = transportTint,
            )

            // Transport: raised keys, Play the biggest and lit
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.media_seek_backward), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    RaisedIconButton(
                        onClick = { viewModel.seekBackward() },
                        enabled = viewModel.isLoaded,
                        modifier = Modifier.size(TRANSPORT_KEY_SIZE),
                        colors = keyColors
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_fast_rewind),
                            contentDescription = stringResource(Res.string.media_seek_backward),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    RaisedIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        enabled = viewModel.isLoaded,
                        modifier = Modifier.size(PLAY_KEY_SIZE),
                        shape = CircleShape,
                        colors = litKeyColors
                    ) {
                        Icon(
                            painterResource(if (viewModel.isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                            contentDescription = stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.stop), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    RaisedIconButton(
                        onClick = { viewModel.stop() },
                        enabled = viewModel.isLoaded,
                        modifier = Modifier.size(TRANSPORT_KEY_SIZE),
                        colors = keyColors
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_stop),
                            contentDescription = stringResource(Res.string.stop),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.media_seek_forward), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    RaisedIconButton(
                        onClick = { viewModel.seekForward() },
                        enabled = viewModel.isLoaded,
                        modifier = Modifier.size(TRANSPORT_KEY_SIZE),
                        colors = keyColors
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_fast_forward),
                            contentDescription = stringResource(Res.string.media_seek_forward),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // (Elapsed / total time now flank the seek bar below, so the combined time is
            // no longer shown here.)

            // Divider
            Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

            // Loop: the button arms it, and the count beside it says how many repeats to play.
            // The count only appears while looping is on, so the bar stays as it was otherwise.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val loopLabel = stringResource(if (viewModel.isLooping) Res.string.loop_on else Res.string.loop_off)
                TooltipArea(
                    tooltip = { TransportTooltip(loopLabel) },
                    tooltipPlacement = TooltipPlacement.ComponentRect(
                        anchor = Alignment.BottomCenter,
                        offset = DpOffset(0.dp, 4.dp)
                    )
                ) {
                    RaisedIconButton(
                        onClick = { viewModel.toggleLooping() },
                        enabled = viewModel.isLoaded,
                        modifier = Modifier.size(TRANSPORT_KEY_SIZE),
                        colors = if (viewModel.isLooping) litKeyColors else keyColors
                    ) {
                        // TooltipArea is a hover popup and contributes no semantics, so without
                        // this the button would have no name at all.
                        Icon(
                            painterResource(Res.drawable.ic_refresh),
                            contentDescription = loopLabel,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (viewModel.isLooping) {
                    val loopCountHint = stringResource(Res.string.media_loop_count_tooltip)
                    TooltipArea(
                        tooltip = { TransportTooltip(loopCountHint) },
                        tooltipPlacement = TooltipPlacement.ComponentRect(
                            anchor = Alignment.BottomCenter,
                            offset = DpOffset(0.dp, 4.dp)
                        )
                    ) {
                        NumberSettingsTextField(
                            // Wide enough for the longest of the translated labels
                            // ("SCHLEIFEN", "TAKRORLAR") before it starts ellipsizing.
                            modifier = Modifier.width(96.dp),
                            label = stringResource(Res.string.media_loop_count),
                            initialText = viewModel.loopCount,
                            range = 0..MAX_LOOP_COUNT,
                            onValueChange = { viewModel.setLoopCount(it) }
                        )
                    }
                }
            }

            // Scale: each click moves Fit → Fill → Stretch on every profile at once -- see the
            // Pictures tab's button, which works the same way.
            val shared = sharedScaleMode(appSettings.projectionSettings.outputProfiles) { it.mediaScaleMode }
            val scaleMode = shared ?: OutputScaleMode.FIT
            val scaled = shared != OutputScaleMode.FIT
            val scaleLabel = scaleButtonLabel(shared, scaleMode, ScaleButtonContent.MEDIA)
            TooltipArea(
                tooltip = { TransportTooltip(scaleLabel) },
                tooltipPlacement = TooltipPlacement.ComponentRect(
                    anchor = Alignment.BottomCenter,
                    offset = DpOffset(0.dp, 4.dp)
                )
            ) {
                RaisedIconButton(
                    onClick = {
                        val next = if (shared == null) scaleMode else scaleMode.next()
                        onSettingsChange { s -> s.withMediaScaleEverywhere(next) }
                    },
                    enabled = viewModel.isLoaded,
                    modifier = Modifier.size(TRANSPORT_KEY_SIZE),
                    colors = if (scaled) litKeyColors else keyColors
                ) {
                    Icon(
                        scaleMode.icon,
                        contentDescription = scaleLabel,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Divider
            Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

            // Subtitles: off, one of the tracks VLC found, or a file of the operator's own.
            var subtitlesExpanded by remember { mutableStateOf(false) }
            val subtitlesLabel = stringResource(Res.string.media_subtitles)
            val subtitlesShowing = viewModel.subtitlesVisible
            val subtitleFilesLabel = stringResource(Res.string.media_subtitles_files)
            val subtitleFileTitle = stringResource(Res.string.media_subtitles_load_file)
            Box {
                TooltipArea(
                    tooltip = { TransportTooltip(subtitlesLabel) },
                    tooltipPlacement = TooltipPlacement.ComponentRect(
                        anchor = Alignment.BottomCenter,
                        offset = DpOffset(0.dp, 4.dp)
                    )
                ) {
                    RaisedIconButton(
                        onClick = { subtitlesExpanded = true },
                        enabled = viewModel.isLoaded && !viewModel.isAudioFile,
                        modifier = Modifier.size(TRANSPORT_KEY_SIZE),
                        colors = if (subtitlesShowing) litKeyColors else keyColors
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_subtitles),
                            contentDescription = subtitlesLabel,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                DropdownMenu(expanded = subtitlesExpanded, onDismissRequest = { subtitlesExpanded = false }) {
                    SubtitleMenuItems(
                        viewModel = viewModel,
                        profiles = appSettings.projectionSettings.outputProfiles,
                        loadFileLabel = subtitleFileTitle,
                        onLoadFile = {
                            subtitlesExpanded = false
                            scope.launch {
                                // The video's own folder, where a subtitle for it almost always
                                // sits, rather than the top of the media library.
                                val beside = runCatching { Path(viewModel.mediaUrl).parent }.getOrNull()
                                val f = FileChooser.platformInstance.chooseSingle(
                                    path = beside ?: Path(appSettings.mediaStorageDirectory),
                                    title = subtitleFileTitle,
                                    filters = listOf(
                                        FileNameExtensionFilter(subtitleFilesLabel, "srt", "vtt", "ass", "ssa", "sub")
                                    ),
                                    selectDirectory = false
                                )
                                // Added, not substituted: a second file is a second language.
                                if (f != null) viewModel.addSubtitleFile(f.absolutePathString())
                            }
                        },
                    )
                }
            }

            // Divider
            Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

            // Volume: a mute key with the slider beside it, both always in the bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val muteLabel = stringResource(
                    if (viewModel.isMuted) Res.string.media_unmute else Res.string.media_mute
                )
                TooltipArea(
                    tooltip = { TransportTooltip(muteLabel) },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    RaisedIconButton(
                        onClick = { viewModel.toggleMute() },
                        enabled = viewModel.isLoaded,
                        modifier = Modifier.size(TRANSPORT_KEY_SIZE),
                        colors = keyColors
                    ) {
                        Icon(
                            painter = painterResource(if (viewModel.isMuted || viewModel.volume == 0f) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up),
                            contentDescription = muteLabel,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                SlimSlider(
                    value = if (viewModel.isMuted) 0f else viewModel.volume,
                    onValueChange = { viewModel.setVolume(it) },
                    valueRange = 0f..1f,
                    enabled = viewModel.isLoaded,
                    modifier = Modifier.width(VOLUME_SLIDER_WIDTH),
                    trailingLabel = "${(viewModel.effectiveVolume * 100).toInt()}%"
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Seek bar ──────────────────────────────────────────────────
        if (viewModel.duration > 0) {
            MediaSeekBar(
                position = viewModel.currentPosition,
                duration = viewModel.duration,
                // No buffered-position feed from the player yet; wire this to VLC's cached
                // position later to reveal the loaded-ahead region.
                bufferedPosition = viewModel.currentPosition,
                onSeek = { viewModel.seekTo(it) },
                formatTime = { viewModel.formatTime(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            )
        } else if (viewModel.isLoaded && viewModel.isPlaying) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // ── Content area ──────────────────────────────────────────────
        val isPresenting = presenterManager?.presentingMode?.value == Presenting.MEDIA && presenterManager.showPresenterWindow.value

        if (viewModel.isLoaded && viewModel.isAudioFile) {
            VideoPlayer(viewModel = viewModel, modifier = Modifier.size(0.dp))
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.media_audio_continues), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            if (viewModel.isLoaded) SoftwareVideoPlayer(viewModel = viewModel, modifier = Modifier.size(0.dp))

            // The shape of the output this media actually goes out on. Media can be routed to
            // several differently-shaped outputs at once, so which one the preview stands for is
            // the operator's to say -- the picker draws nothing until there is more than one.
            val previewOutput = rememberPreviewOutput(appSettings, Constants.PREVIEW_TAB_MEDIA, Presenting.MEDIA)
            PreviewOutputPicker(
                settings = appSettings,
                tabId = Constants.PREVIEW_TAB_MEDIA,
                mode = Presenting.MEDIA,
                onSettingsChange = onSettingsChange,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .aspectRatio(previewOutput.size.aspectRatio)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isPresenting -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.White.copy(alpha = 0.6f))
                            Text(stringResource(Res.string.media_now_presenting), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
                            Text(viewModel.mediaTitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        viewModel.isLoaded -> SharedVideoOutputDisplay(
                            modifier = Modifier.fillMaxSize(),
                            contentScale = appSettings.mediaScaleMode.contentScale,
                        )
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.White.copy(alpha = 0.4f))
                            Text(stringResource(Res.string.media_no_source), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.6f))
                            Text(stringResource(Res.string.media_select_to_begin), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

/** The dark tooltip bubble the media transport controls hover. */
@Composable
private fun TransportTooltip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 4.dp
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Slim media seek bar: a 5px rounded track with a teal gradient played fill, a subtle grey
 * loaded-ahead (buffered) region, and elapsed/total times flanking it (elapsed bold-accented,
 * total muted). The white drag handle fades and scales in only on hover/drag, so the bar stays
 * clean at rest. Tap or drag anywhere on the track to seek.
 */
@Composable
private fun MediaSeekBar(
    position: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Long) -> Unit,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val playedFraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging
    val handleAlpha by animateFloatAsState(if (active) 1f else 0f, label = "seekHandleAlpha")
    val handleScale by animateFloatAsState(if (active) 1f else 0.35f, label = "seekHandleScale")

    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val bufferedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val playedStart = primary.copy(alpha = 0.65f)
    // Hoisted for the Canvas below, which cannot read the theme itself.
    val handleColor = MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = formatTime(position),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = primary,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 42.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .hoverable(interactionSource)
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        if (duration > 0 && size.width > 0) {
                            onSeek((offset.x / size.width * duration).toLong().coerceIn(0L, duration))
                        }
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            if (duration > 0 && size.width > 0) {
                                onSeek((offset.x / size.width * duration).toLong().coerceIn(0L, duration))
                            }
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            if (duration > 0 && size.width > 0) {
                                onSeek((change.position.x / size.width * duration).toLong().coerceIn(0L, duration))
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackH = 5.dp.toPx()
                val cy = size.height / 2f
                val top = cy - trackH / 2f
                val radius = CornerRadius(trackH / 2f, trackH / 2f)
                // Base track
                drawRoundRect(color = trackColor, topLeft = Offset(0f, top), size = Size(size.width, trackH), cornerRadius = radius)
                // Buffered (loaded-ahead) region
                if (bufferedFraction > 0f) {
                    drawRoundRect(color = bufferedColor, topLeft = Offset(0f, top), size = Size(size.width * bufferedFraction, trackH), cornerRadius = radius)
                }
                // Played region — teal gradient
                if (playedFraction > 0f) {
                    val playedW = size.width * playedFraction
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(playedStart, primary), startX = 0f, endX = playedW.coerceAtLeast(trackH)),
                        topLeft = Offset(0f, top),
                        size = Size(playedW, trackH),
                        cornerRadius = radius
                    )
                }
                // Hover handle — fades/scales in only on hover or drag
                if (handleAlpha > HANDLE_VISIBLE_ALPHA) {
                    val hx = (size.width * playedFraction).coerceIn(0f, size.width)
                    drawCircle(color = handleColor.copy(alpha = handleAlpha), radius = 6.dp.toPx() * handleScale, center = Offset(hx, cy))
                }
            }
        }
        Text(
            text = formatTime(duration),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 42.dp)
        )
    }
}
