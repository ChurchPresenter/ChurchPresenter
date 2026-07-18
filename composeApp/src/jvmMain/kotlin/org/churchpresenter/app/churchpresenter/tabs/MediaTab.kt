package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.serialization.json.Json
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.clear_recents
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_fast_forward
import churchpresenter.composeapp.generated.resources.ic_fast_rewind
import churchpresenter.composeapp.generated.resources.ic_folder
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_star
import churchpresenter.composeapp.generated.resources.ic_star_filled
import churchpresenter.composeapp.generated.resources.ic_stop
import churchpresenter.composeapp.generated.resources.ic_volume_off
import churchpresenter.composeapp.generated.resources.ic_volume_up
import churchpresenter.composeapp.generated.resources.media_audio_continues
import churchpresenter.composeapp.generated.resources.media_files_filter
import churchpresenter.composeapp.generated.resources.media_load
import churchpresenter.composeapp.generated.resources.media_local_file
import churchpresenter.composeapp.generated.resources.media_mute
import churchpresenter.composeapp.generated.resources.media_network_url
import churchpresenter.composeapp.generated.resources.media_no_source
import churchpresenter.composeapp.generated.resources.media_now_playing
import churchpresenter.composeapp.generated.resources.media_now_presenting
import churchpresenter.composeapp.generated.resources.media_preview
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
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.TabGoLiveButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.SharedVideoOutputDisplay
import org.churchpresenter.app.churchpresenter.composables.SoftwareVideoPlayer
import org.churchpresenter.app.churchpresenter.composables.VideoPlayer
import org.churchpresenter.app.churchpresenter.composables.isVlcArchMismatch
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.isVlcLoadFailed
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.presenterAspectRatio
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlinx.coroutines.launch

private object RecentMediaFiles {
    private const val MAX = 10
    private val file = java.io.File(System.getProperty("user.home"), ".churchpresenter/recent_media_files.json")
    private val pinnedFile = java.io.File(System.getProperty("user.home"), ".churchpresenter/pinned_media_files.json")
    val paths = androidx.compose.runtime.mutableStateListOf<String>()
    val pinned = androidx.compose.runtime.mutableStateListOf<String>()

    init { load() }

    fun add(path: String) {
        paths.remove(path)
        paths.add(0, path)
        while (paths.size > MAX) paths.removeLast()
        save()
    }

    fun togglePin(path: String) {
        if (path in pinned) pinned.remove(path)
        else { pinned.remove(path); pinned.add(0, path) }
        savePinned()
    }

    fun clear() {
        val keep = paths.filter { it in pinned }
        paths.clear(); paths.addAll(keep); save()
    }

    private fun load() {
        try { if (file.exists()) { val json = Json { ignoreUnknownKeys = true }; val list = json.decodeFromString<List<String>>(file.readText()); paths.clear(); paths.addAll(list.take(MAX)) } } catch (_: Exception) {}
        try { if (pinnedFile.exists()) { val json = Json { ignoreUnknownKeys = true }; val list = json.decodeFromString<List<String>>(pinnedFile.readText()); pinned.clear(); pinned.addAll(list) } } catch (_: Exception) {}
    }

    private fun save() {
        try { file.parentFile?.mkdirs(); val json = Json { encodeDefaults = true }; file.writeText(json.encodeToString(paths.toList())) } catch (_: Exception) {}
    }

    private fun savePinned() {
        try { pinnedFile.parentFile?.mkdirs(); val json = Json { encodeDefaults = true }; pinnedFile.writeText(json.encodeToString(pinned.toList())) } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings = AppSettings(),
    onAddToSchedule: ((mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit)? = null,
    selectedMediaItem: ScheduleItem.MediaItem? = null,
    presenterManager: PresenterManager? = null,
    /** Non-null while connected via Instance Link — builds the primary's /api/media/stream URL for
     *  a given schedule item id, used in place of a schedule item's local file path since that path
     *  only exists on the primary's disk. */
    instanceLinkMediaStreamUrl: ((itemId: String) -> String)? = null,
    /** Instance Link Controller mode — non-null only when connected and controlling. Sends via
     *  PROJECT; only remote URLs (youtube/vimeo) will actually play on the primary — a "local" file
     *  path only exists on this machine's disk, not the primary's, a known limitation. */
    onInstanceLinkSendProject: ((ScheduleItem) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    if (!isVlcAvailable) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (isVlcArchMismatch || isVlcLoadFailed) Icons.Default.Warning else Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = if (isVlcArchMismatch || isVlcLoadFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(stringResource(Res.string.media_vlc_required), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        isVlcArchMismatch -> stringResource(Res.string.media_vlc_arch_mismatch)
                        isVlcLoadFailed -> stringResource(Res.string.media_vlc_load_failed)
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
    var volumeExpanded by remember { mutableStateOf(false) }

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

    LaunchedEffect(selectedMediaItem) {
        selectedMediaItem?.let {
            if (presenterManager?.presentingMode?.value == Presenting.MEDIA) presenterManager.requestClearDisplay()
            when (it.mediaType) {
                Constants.MEDIA_TYPE_URL -> { selectedSourceType = Constants.MEDIA_TYPE_URL; urlInput = it.mediaUrl }
                else -> selectedSourceType = Constants.MEDIA_TYPE_LOCAL
            }
            // A mirrored schedule item's local path only exists on the primary's disk — stream it
            // over HTTP from there instead when following via Instance Link.
            val effectiveUrl = if (it.mediaType == Constants.MEDIA_TYPE_LOCAL && instanceLinkMediaStreamUrl != null) {
                instanceLinkMediaStreamUrl(it.id)
            } else {
                it.mediaUrl
            }
            viewModel.loadMediaFromSchedule(url = effectiveUrl, title = it.mediaTitle, type = it.mediaType)
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when {
                        keyEvent.key == Key.Escape && presenterManager != null -> { viewModel.pause(); presenterManager.requestClearDisplay(); true }
                        viewModel.isLoaded && keyEvent.key == Key.Spacebar -> { viewModel.togglePlayPause(); true }
                        viewModel.isLoaded && keyEvent.key == Key.M -> { viewModel.toggleMute(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        // ── Source bar ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    Button(
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
                    if (viewModel.isLoaded) {
                        Text(
                            text = stringResource(Res.string.media_now_playing),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Constants.MEDIA_TYPE_URL -> {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
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
                    Button(
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
                    IconButton(onClick = { RecentMediaFiles.clear() }, modifier = Modifier.size(20.dp)) {
                        Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.clear), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    lazyItems(recentOrdered) { path ->
                        val isPinned = path in RecentMediaFiles.pinned
                        val isActive = viewModel.isLoaded && viewModel.mediaUrl == path
                        val displayName = if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("rtsp://")) path else java.io.File(path).name
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .background(if (isActive) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(6.dp))
                                    .border(1.dp, if (isActive) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                    .clickable {
                                        val ext = java.io.File(path).extension.lowercase()
                                        val type = when {
                                            path.startsWith("http://") || path.startsWith("https://") || path.startsWith("rtsp://") -> Constants.MEDIA_TYPE_URL
                                            ext in Constants.AUDIO_EXTENSIONS -> Constants.MEDIA_TYPE_AUDIO
                                            else -> Constants.MEDIA_TYPE_LOCAL
                                        }
                                        if (presenterManager?.presentingMode?.value == Presenting.MEDIA) presenterManager.requestClearDisplay()
                                        viewModel.loadMedia(path, type)
                                        RecentMediaFiles.add(path)
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(displayName, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), maxLines = 1)
                            }
                            IconButton(onClick = { RecentMediaFiles.togglePin(path) }, modifier = Modifier.size(20.dp)) {
                                Icon(painterResource(if (isPinned) Res.drawable.ic_star_filled else Res.drawable.ic_star), contentDescription = null, modifier = Modifier.size(12.dp), tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        // ── Playback controls bar ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Transport (inner gap 4dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.media_seek_backward), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = { viewModel.seekBackward() }, enabled = viewModel.isLoaded, modifier = Modifier.size(30.dp)) {
                        Icon(painterResource(Res.drawable.ic_fast_rewind), contentDescription = stringResource(Res.string.media_seek_backward), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (viewModel.isLoaded) 0.7f else 0.35f))
                    }
                }
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        enabled = viewModel.isLoaded,
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(painterResource(if (viewModel.isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play), contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                }
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.stop), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = { viewModel.stop() }, enabled = viewModel.isLoaded, modifier = Modifier.size(30.dp)) {
                        Icon(painterResource(Res.drawable.ic_stop), contentDescription = stringResource(Res.string.stop), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (viewModel.isLoaded) 0.7f else 0.35f))
                    }
                }
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.media_seek_forward), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = { viewModel.seekForward() }, enabled = viewModel.isLoaded, modifier = Modifier.size(30.dp)) {
                        Icon(painterResource(Res.drawable.ic_fast_forward), contentDescription = stringResource(Res.string.media_seek_forward), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (viewModel.isLoaded) 0.7f else 0.35f))
                    }
                }
            }

            if (viewModel.duration > 0) {
                Text(
                    text = "${viewModel.formatTime(viewModel.currentPosition)} / ${viewModel.formatTime(viewModel.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }

            // Divider
            Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

            // Volume
            Box {
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(if (viewModel.isMuted) Res.string.media_unmute else Res.string.media_mute), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = { volumeExpanded = !volumeExpanded }, enabled = viewModel.isLoaded, modifier = Modifier.size(30.dp)) {
                        Icon(
                            painter = painterResource(if (viewModel.isMuted || viewModel.volume == 0f) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (viewModel.isLoaded) 0.7f else 0.35f)
                        )
                    }
                }
                if (volumeExpanded) {
                    Popup(
                        alignment = Alignment.BottomCenter,
                        offset = IntOffset(100, 60),
                        onDismissRequest = { volumeExpanded = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 8.dp, shadowElevation = 8.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                IconButton(onClick = { viewModel.toggleMute() }, modifier = Modifier.size(24.dp)) {
                                    Icon(painterResource(if (viewModel.isMuted || viewModel.volume == 0f) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up), contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Slider(value = if (viewModel.isMuted) 0f else viewModel.volume, onValueChange = { viewModel.setVolume(it) }, valueRange = 0f..1f, modifier = Modifier.width(160.dp))
                                Text("${(viewModel.effectiveVolume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Action buttons
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onAddToSchedule != null) {
                    AddToScheduleButton(
                        onClick = { onAddToSchedule(viewModel.mediaUrl, viewModel.mediaTitle, viewModel.mediaType) },
                        enabled = viewModel.isLoaded,
                        tooltipText = stringResource(Res.string.add_to_schedule)
                    )
                }
                if (presenterManager != null) {
                    TabGoLiveButton(
                        appSettings = appSettings,
                        presenterManager = presenterManager,
                        liveMode = Presenting.MEDIA,
                        isEnabled = { it.showMedia },
                        enabled = viewModel.isLoaded,
                        onGoLive = {
                            presenterManager.setPresentingMode(Presenting.MEDIA)
                            presenterManager.setShowPresenterWindow(true)
                            presenterManager.setCurrentMedia(viewModel.mediaUrl, viewModel.mediaType)
                            viewModel.play()
                            onInstanceLinkSendProject?.invoke(
                                ScheduleItem.MediaItem(
                                    id = java.util.UUID.randomUUID().toString(),
                                    mediaUrl = viewModel.mediaUrl,
                                    mediaTitle = viewModel.mediaTitle,
                                    mediaType = viewModel.mediaType
                                )
                            )
                        },
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Seek slider ───────────────────────────────────────────────
        if (viewModel.duration > 0) {
            Slider(
                value = viewModel.currentPosition.toFloat(),
                onValueChange = { viewModel.seekTo(it.toLong()) },
                valueRange = 0f..viewModel.duration.toFloat(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
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

            Text(
                text = stringResource(Res.string.media_preview),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp).padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .aspectRatio(presenterAspectRatio())
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
                        viewModel.isLoaded -> SharedVideoOutputDisplay(modifier = Modifier.fillMaxSize())
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
