package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
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
import churchpresenter.composeapp.generated.resources.media_now_presenting
import churchpresenter.composeapp.generated.resources.ic_fast_forward
import churchpresenter.composeapp.generated.resources.ic_fast_rewind
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_volume_off
import churchpresenter.composeapp.generated.resources.ic_volume_up
import churchpresenter.composeapp.generated.resources.media_mute
import churchpresenter.composeapp.generated.resources.media_no_source
import churchpresenter.composeapp.generated.resources.media_now_playing
import churchpresenter.composeapp.generated.resources.media_preview
import churchpresenter.composeapp.generated.resources.media_seek_backward
import churchpresenter.composeapp.generated.resources.media_seek_forward
import churchpresenter.composeapp.generated.resources.media_select_file
import churchpresenter.composeapp.generated.resources.media_select_to_begin
import churchpresenter.composeapp.generated.resources.media_source
import churchpresenter.composeapp.generated.resources.media_unmute
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import org.churchpresenter.app.churchpresenter.composables.VideoPlayer
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun MediaTab(
    modifier: Modifier = Modifier,
    viewModel: MediaViewModel = remember { MediaViewModel() },
    scheduleViewModel: ScheduleViewModel? = null,
    presenterManager: PresenterManager? = null
) {
    val focusRequester = remember { FocusRequester() }
    var volumeExpanded by remember { mutableStateOf(false) }


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
        Text(
            text = stringResource(Res.string.media_source),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider()

        // Single source row: file picker + URL field + Load button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Local file picker
            Button(
                onClick = {
                    SwingUtilities.invokeLater {
                        val chooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.FILES_ONLY
                            dialogTitle = "Select Video File"
                            fileFilter = FileNameExtensionFilter(
                                "Video Files",
                                "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm", "m4v"
                            )
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val file = chooser.selectedFile
                            viewModel.loadMedia(file.absolutePath, Constants.MEDIA_TYPE_LOCAL)
                        }
                    }
                }
            ) {
                Text(
                    text = stringResource(Res.string.media_select_file),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                text = if (viewModel.isLoaded) viewModel.mediaTitle
                       else stringResource(Res.string.media_no_source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                                        style = MaterialTheme.typography.labelSmall,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (presenterManager != null) {
                    Button(
                        onClick = {
                            presenterManager.setPresentingMode(Presenting.MEDIA)
                            presenterManager.setShowPresenterWindow(true)
                        },
                        enabled = viewModel.isLoaded,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.go_live),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (scheduleViewModel != null && viewModel.isLoaded) {
                    Button(
                        onClick = {
                            scheduleViewModel.addMedia(
                                mediaUrl = viewModel.mediaUrl,
                                mediaTitle = viewModel.mediaTitle,
                                mediaType = viewModel.mediaType
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.add_to_schedule),
                            style = MaterialTheme.typography.labelSmall
                        )
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


        // ── Preview panel ───────────────────────────────────────────────
        Text(
            text = stringResource(Res.string.media_preview),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        val isPresenting = presenterManager?.presentingMode?.value == Presenting.MEDIA
                && presenterManager.showPresenterWindow.value

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isPresenting -> {
                    // Presenter window is active — don't run a second player here
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