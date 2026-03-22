package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentRecomposeScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_cast
import churchpresenter.composeapp.generated.resources.ic_fast_forward
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.ic_fast_rewind
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_volume_off
import churchpresenter.composeapp.generated.resources.ic_volume_up
import churchpresenter.composeapp.generated.resources.media_mute
import churchpresenter.composeapp.generated.resources.media_no_source
import churchpresenter.composeapp.generated.resources.media_now_playing
import churchpresenter.composeapp.generated.resources.media_now_presenting
import churchpresenter.composeapp.generated.resources.media_preview
import churchpresenter.composeapp.generated.resources.media_seek_backward
import churchpresenter.composeapp.generated.resources.media_seek_forward
import churchpresenter.composeapp.generated.resources.media_audio_continues
import churchpresenter.composeapp.generated.resources.media_files_filter
import churchpresenter.composeapp.generated.resources.media_load
import churchpresenter.composeapp.generated.resources.media_local_file
import churchpresenter.composeapp.generated.resources.media_network_url
import churchpresenter.composeapp.generated.resources.media_select_file
import churchpresenter.composeapp.generated.resources.media_select_to_begin

import churchpresenter.composeapp.generated.resources.media_url_placeholder
import churchpresenter.composeapp.generated.resources.media_vlc_arch_mismatch
import churchpresenter.composeapp.generated.resources.media_vlc_install
import churchpresenter.composeapp.generated.resources.media_vlc_required
import churchpresenter.composeapp.generated.resources.media_unmute
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.VideoPlayer
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.composables.isVlcArchMismatch
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
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
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.launch
import kotlin.io.path.Path
import kotlin.io.path.extension

@Composable
fun MediaTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings = AppSettings(),
    onAddToSchedule: ((mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit)? = null,
    selectedMediaItem: ScheduleItem.MediaItem? = null,
    presenterManager: PresenterManager? = null
) {
    val scope = rememberCoroutineScope()
    // Check if VLC is available
    if (!isVlcAvailable) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (isVlcArchMismatch) "⚠️" else "🎬", style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(Res.string.media_vlc_required),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = if (isVlcArchMismatch)
                    stringResource(Res.string.media_vlc_arch_mismatch)
                else
                    stringResource(Res.string.media_vlc_install),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Consume the app-scope MediaViewModel provided by main.kt via CompositionLocalProvider.
    // Do NOT create a local instance — that would disconnect MediaTab controls from MediaPresenter.
    val viewModel = LocalMediaViewModel.current ?: return
    val focusRequester = remember { FocusRequester() }
    var volumeExpanded by remember { mutableStateOf(false) }

    // Source type selector state
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

    // React to schedule item selection
    LaunchedEffect(selectedMediaItem) {
        selectedMediaItem?.let {
            if (presenterManager?.presentingMode?.value == Presenting.MEDIA) {
                presenterManager.setPresentingMode(Presenting.NONE)
            }
            // Sync source type selector with loaded item
            when (it.mediaType) {
                Constants.MEDIA_TYPE_URL -> {
                    selectedSourceType = Constants.MEDIA_TYPE_URL
                    urlInput = it.mediaUrl
                }
                else -> selectedSourceType = Constants.MEDIA_TYPE_LOCAL
            }
            viewModel.loadMediaFromSchedule(url = it.mediaUrl, title = it.mediaTitle, type = it.mediaType)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when {
                        keyEvent.key == Key.Escape && presenterManager != null -> {
                            viewModel.pause()
                            presenterManager.setPresentingMode(Presenting.NONE)
                            true
                        }
                        viewModel.isLoaded && keyEvent.key == Key.Spacebar -> {
                            viewModel.togglePlayPause()
                            true
                        }
                        viewModel.isLoaded && keyEvent.key == Key.M -> {
                            viewModel.toggleMute()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Source row ──────────────────────────────────────────────────
        val selectFileLabel = stringResource(Res.string.media_select_file)
        val mediaFilesLabel = stringResource(Res.string.media_files_filter)
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source type toggle
            SegmentedButton(
                items = sourceTypeItems,
                selectedValue = selectedSourceType,
                onValueChange = { selectedSourceType = it },
                buttonWidth = 90.dp,
                buttonHeight = 36.dp,
                fontSize = MaterialTheme.typography.labelMedium.fontSize
            )

            // Per-type input controls
            when (selectedSourceType) {
                Constants.MEDIA_TYPE_LOCAL -> {
                    // Local file picker button
                    Button(
                        onClick = {
                            scope.launch {
                                val file = FileChooser.platformInstance.chooseSingle(
                                    path = Path(appSettings.mediaStorageDirectory),
                                    title = selectFileLabel,
                                    filters = listOf(
                                        FileNameExtensionFilter(
                                            mediaFilesLabel,
                                            "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm", "m4v",
                                            "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "aiff", "opus"
                                        )
                                    ),
                                    selectDirectory = false
                                )
                                if (file != null) {
                                    val ext = file.extension.lowercase()
                                    val type = if (ext in Constants.AUDIO_EXTENSIONS)
                                        Constants.MEDIA_TYPE_AUDIO else Constants.MEDIA_TYPE_LOCAL
                                    if (presenterManager?.presentingMode?.value == Presenting.MEDIA) {
                                        presenterManager.setPresentingMode(Presenting.NONE)
                                    }
                                    viewModel.loadMedia(file.absolutePathString(), type)
                                }
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(Res.string.media_select_file),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(
                        text = if (viewModel.isLoaded && viewModel.mediaType != Constants.MEDIA_TYPE_URL)
                            viewModel.mediaTitle
                        else stringResource(Res.string.media_no_source),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Constants.MEDIA_TYPE_URL -> {
                    // Network URL text field + Load button
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = {
                            Text(
                                text = stringResource(Res.string.media_url_placeholder),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).heightIn(max = 52.dp)
                    )
                    Button(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                if (presenterManager?.presentingMode?.value == Presenting.MEDIA) {
                                    presenterManager.setPresentingMode(Presenting.NONE)
                                }
                                viewModel.loadMedia(urlInput.trim(), Constants.MEDIA_TYPE_URL)
                            }
                        },
                        enabled = urlInput.isNotBlank()
                    ) {
                        Text(
                            text = stringResource(Res.string.media_load),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

            }
        }

        // Now playing label
        if (viewModel.isLoaded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.media_now_playing),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = viewModel.mediaTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ── Playback controls ───────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transport controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.seekBackward() },
                    enabled = viewModel.isLoaded
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fast_rewind),
                        contentDescription = stringResource(Res.string.media_seek_backward),
                        modifier = Modifier.size(28.dp),
                        tint = if (viewModel.isLoaded) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    enabled = viewModel.isLoaded,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (viewModel.isLoaded) {
                                if (viewModel.isPlaying) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            } else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Icon(
                        painter = painterResource(
                            if (viewModel.isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play
                        ),
                        contentDescription = stringResource(
                            if (viewModel.isPlaying) Res.string.pause else Res.string.play
                        ),
                        modifier = Modifier.size(28.dp),
                        tint = if (viewModel.isLoaded) Color.White
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                IconButton(
                    onClick = { viewModel.seekForward() },
                    enabled = viewModel.isLoaded
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fast_forward),
                        contentDescription = stringResource(Res.string.media_seek_forward),
                        modifier = Modifier.size(28.dp),
                        tint = if (viewModel.isLoaded) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                if (viewModel.duration > 0) {
                    Text(
                        text = "${viewModel.formatTime(viewModel.currentPosition)} / ${viewModel.formatTime(viewModel.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Volume button with popup slider
                Box {
                    IconButton(
                        onClick = { volumeExpanded = !volumeExpanded },
                        enabled = viewModel.isLoaded
                    ) {
                        Icon(
                            painter = painterResource(
                                if (viewModel.isMuted || viewModel.volume == 0f) Res.drawable.ic_volume_off
                                else Res.drawable.ic_volume_up
                            ),
                            contentDescription = stringResource(
                                if (viewModel.isMuted) Res.string.media_unmute else Res.string.media_mute
                            ),
                            modifier = Modifier.size(24.dp),
                            tint = if (viewModel.isLoaded) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    if (volumeExpanded) {
                        Popup(
                            alignment = Alignment.BottomCenter,
                            offset = IntOffset(100, 60),
                            onDismissRequest = { volumeExpanded = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                tonalElevation = 8.dp,
                                shadowElevation = 8.dp,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.toggleMute() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                if (viewModel.isMuted || viewModel.volume == 0f) Res.drawable.ic_volume_off
                                                else Res.drawable.ic_volume_up
                                            ),
                                            contentDescription = stringResource(
                                                if (viewModel.isMuted) Res.string.media_unmute else Res.string.media_mute
                                            ),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Slider(
                                        value = if (viewModel.isMuted) 0f else viewModel.volume,
                                        onValueChange = { viewModel.setVolume(it) },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.width(160.dp)
                                    )
                                    Text(
                                        text = "${(viewModel.effectiveVolume * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.width(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onAddToSchedule != null) {
                    IconButton(
                        onClick = {
                            onAddToSchedule(viewModel.mediaUrl, viewModel.mediaTitle, viewModel.mediaType)
                        },
                        enabled = viewModel.isLoaded,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_playlist_add), contentDescription = stringResource(Res.string.add_to_schedule), modifier = Modifier.size(20.dp))
                    }
                }

                if (presenterManager != null) {
                    IconButton(
                        onClick = {
                            presenterManager.setPresentingMode(Presenting.MEDIA)
                            presenterManager.setShowPresenterWindow(true)
                            viewModel.play()
                        },
                        enabled = viewModel.isLoaded,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_cast), contentDescription = stringResource(Res.string.go_live), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Seek slider
        if (viewModel.duration > 0) {
            Slider(
                value = viewModel.currentPosition.toFloat(),
                onValueChange = { viewModel.seekTo(it.toLong()) },
                valueRange = 0f..viewModel.duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        } else if (viewModel.isLoaded && viewModel.isPlaying) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }


        // ── Preview / Screen content panel ──────────────────────────────
        val isPresenting = presenterManager?.presentingMode?.value == Presenting.MEDIA
                && presenterManager.showPresenterWindow.value

        if (viewModel.isLoaded && viewModel.isAudioFile) {
            // Hidden VLCJ player to drive audio playback while on this tab.
            // When the user switches away, MainDesktop's hidden player takes over.
            VideoPlayer(
                viewModel = viewModel,
                modifier = Modifier.size(0.dp)
            )

            // Audio file: info text
            Text(
                text = stringResource(Res.string.media_audio_continues),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))
        } else {
            // Video file or no media: show video preview
            Text(
                text = stringResource(Res.string.media_preview),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(presenterAspectRatio())
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isPresenting -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📽️", style = MaterialTheme.typography.displayMedium)
                                Text(
                                    text = stringResource(Res.string.media_now_presenting),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = viewModel.mediaTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        viewModel.isLoaded -> {
                            VideoPlayer(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🎬", style = MaterialTheme.typography.displayMedium)
                                Text(
                                    text = stringResource(Res.string.media_no_source),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = stringResource(Res.string.media_select_to_begin),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}