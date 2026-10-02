@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.sharedui.utils.sharedScaleMode
import org.churchpresenter.sharedui.utils.ScaleButtonContent
import org.churchpresenter.sharedui.utils.scaleButtonLabel
import org.churchpresenter.sharedui.utils.withMediaScaleEverywhere
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import org.churchpresenter.theme.components.RaisedIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_fast_forward
import org.churchpresenter.icons.generated.resources.ic_fast_rewind
import org.churchpresenter.icons.generated.resources.ic_pause
import org.churchpresenter.icons.generated.resources.ic_play
import org.churchpresenter.icons.generated.resources.ic_refresh
import org.churchpresenter.icons.generated.resources.ic_stop
import org.churchpresenter.icons.generated.resources.ic_subtitles
import org.churchpresenter.icons.generated.resources.ic_volume_off
import org.churchpresenter.icons.generated.resources.ic_volume_up
import org.churchpresenter.strings.generated.resources.loop_off
import org.churchpresenter.strings.generated.resources.loop_on
import org.churchpresenter.strings.generated.resources.media_loop_count
import org.churchpresenter.strings.generated.resources.media_loop_count_tooltip
import org.churchpresenter.strings.generated.resources.media_mute
import org.churchpresenter.strings.generated.resources.media_subtitles
import org.churchpresenter.strings.generated.resources.media_subtitles_files
import org.churchpresenter.strings.generated.resources.media_subtitles_load_file
import org.churchpresenter.strings.generated.resources.media_seek_backward
import org.churchpresenter.strings.generated.resources.media_seek_forward
import org.churchpresenter.strings.generated.resources.media_unmute
import org.churchpresenter.strings.generated.resources.pause
import org.churchpresenter.strings.generated.resources.play
import org.churchpresenter.strings.generated.resources.stop
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.sharedui.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.settings.OutputScaleMode
import org.churchpresenter.sharedui.utils.icon
import org.churchpresenter.sharedui.utils.label
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButtonColors

/** Transport, loop, scale, subtitles and volume. */
@Composable
internal fun MediaTabScope.MediaControlsBar(viewModel: MediaViewModel) {
    // ── Playback controls bar ─────────────────────────────────────
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
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
            .copy(alpha = if (viewModel.isLoaded) 1f else MEDIA_DISABLED_TRANSPORT_ALPHA)
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

        MediaTransport(viewModel, keyColors, litKeyColors)

        // (Elapsed / total time now flank the seek bar below, so the combined time is
        // no longer shown here.)

        // Divider
        Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

        MediaLoopControls(viewModel, keyColors, litKeyColors)

        MediaScaleButton(viewModel, keyColors, litKeyColors)

        // Divider
        Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

        MediaSubtitlesButton(viewModel, keyColors, litKeyColors)

        // Divider
        Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

        MediaVolume(viewModel, keyColors)
    }
}

/** Raised keys, Play the biggest and lit. */
@Composable
private fun MediaTabScope.MediaTransport(
    viewModel: MediaViewModel,
    keyColors: IconButtonColors,
    litKeyColors: IconButtonColors,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TooltipArea(
            tooltip = { TransportTooltip(stringResource(Res.string.media_seek_backward)) },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 4.dp),
            )
        ) {
            RaisedIconButton(
                onClick = { viewModel.seekBackward() },
                enabled = viewModel.isLoaded,
                modifier = Modifier.size(MEDIA_TRANSPORT_KEY_SIZE),
                colors = keyColors
            ) {
                Icon(
                    painterResource(IconRes.drawable.ic_fast_rewind),
                    contentDescription = stringResource(Res.string.media_seek_backward),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        MediaPlayKey(viewModel, litKeyColors)
        TooltipArea(
            tooltip = { TransportTooltip(stringResource(Res.string.stop)) },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 4.dp),
            )
        ) {
            RaisedIconButton(
                onClick = { viewModel.stop() },
                enabled = viewModel.isLoaded,
                modifier = Modifier.size(MEDIA_TRANSPORT_KEY_SIZE),
                colors = keyColors
            ) {
                Icon(
                    painterResource(IconRes.drawable.ic_stop),
                    contentDescription = stringResource(Res.string.stop),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        TooltipArea(
            tooltip = { TransportTooltip(stringResource(Res.string.media_seek_forward)) },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 4.dp),
            )
        ) {
            RaisedIconButton(
                onClick = { viewModel.seekForward() },
                enabled = viewModel.isLoaded,
                modifier = Modifier.size(MEDIA_TRANSPORT_KEY_SIZE),
                colors = keyColors
            ) {
                Icon(
                    painterResource(IconRes.drawable.ic_fast_forward),
                    contentDescription = stringResource(Res.string.media_seek_forward),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaTabScope.MediaPlayKey(viewModel: MediaViewModel, litKeyColors: IconButtonColors) {
    TooltipArea(
        tooltip = { TransportTooltip(stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play)) },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        )
    ) {
        RaisedIconButton(
            onClick = { viewModel.togglePlayPause() },
            enabled = viewModel.isLoaded,
            modifier = Modifier.size(MEDIA_PLAY_KEY_SIZE),
            shape = CircleShape,
            colors = litKeyColors
        ) {
            Icon(
                painterResource(
                    if (viewModel.isPlaying) IconRes.drawable.ic_pause else IconRes.drawable.ic_play
                ),
                contentDescription = stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** The loop key, and while looping the count of repeats beside it. */
@Composable
private fun MediaTabScope.MediaLoopControls(
    viewModel: MediaViewModel,
    keyColors: IconButtonColors,
    litKeyColors: IconButtonColors,
) {
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
                modifier = Modifier.size(MEDIA_TRANSPORT_KEY_SIZE),
                colors = if (viewModel.isLooping) litKeyColors else keyColors
            ) {
                // TooltipArea is a hover popup and contributes no semantics, so without
                // this the button would have no name at all.
                Icon(
                    painterResource(IconRes.drawable.ic_refresh),
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
                    range = 0..MEDIA_MAX_LOOP_COUNT,
                    onValueChange = { viewModel.setLoopCount(it) }
                )
            }
        }
    }
}

/** Each click moves Fit → Fill → Stretch on every profile at once, like the Pictures tab's button. */
@Composable
private fun MediaTabScope.MediaScaleButton(
    viewModel: MediaViewModel,
    keyColors: IconButtonColors,
    litKeyColors: IconButtonColors,
) {
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
            modifier = Modifier.size(MEDIA_TRANSPORT_KEY_SIZE),
            colors = if (scaled) litKeyColors else keyColors
        ) {
            Icon(
                scaleMode.icon,
                contentDescription = scaleLabel,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Subtitles: off, one of the tracks VLC found, or a file of the operator's own. */
@Composable
private fun MediaTabScope.MediaSubtitlesButton(
    viewModel: MediaViewModel,
    keyColors: IconButtonColors,
    litKeyColors: IconButtonColors,
) {
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
                modifier = Modifier.size(MEDIA_TRANSPORT_KEY_SIZE),
                colors = if (subtitlesShowing) litKeyColors else keyColors
            ) {
                Icon(
                    painterResource(IconRes.drawable.ic_subtitles),
                    contentDescription = subtitlesLabel,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        DropdownMenu(expanded = subtitlesExpanded, onDismissRequest = { subtitlesExpanded = false }) {
            SubtitleMenuItems(
                state = SubtitleMenuState(
                    subtitlesVisible = viewModel.subtitlesVisible,
                    sidecars = viewModel.sidecarSubtitles,
                    embeddedTracks = viewModel.subtitleTracks,
                    selectedEmbeddedTrack = viewModel.selectedSubtitleTrack,
                ),
                actions = SubtitleMenuActions(
                    onTurnOff = viewModel::turnSubtitlesOff,
                    onSidecarEnabled = viewModel::setSidecarEnabled,
                    onSidecarOutputs = viewModel::setSidecarOutputs,
                    onSelectEmbedded = viewModel::selectSubtitleTrack,
                ),
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
                                FileNameExtensionFilter(
                                    subtitleFilesLabel, "srt", "vtt", "ass", "ssa", "sub",
                                )
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
}

/** A mute key with the slider beside it, both always in the bar. */
@Composable
private fun MediaTabScope.MediaVolume(viewModel: MediaViewModel, keyColors: IconButtonColors) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val muteLabel = stringResource(
            if (viewModel.isMuted) Res.string.media_unmute else Res.string.media_mute
        )
        TooltipArea(
            tooltip = { TransportTooltip(muteLabel) },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 4.dp),
            )
        ) {
            RaisedIconButton(
                onClick = { viewModel.toggleMute() },
                enabled = viewModel.isLoaded,
                modifier = Modifier.size(MEDIA_TRANSPORT_KEY_SIZE),
                colors = keyColors
            ) {
                Icon(
                    painter = painterResource(if (viewModel.isMuted ||
                        viewModel.volume == 0f) IconRes.drawable.ic_volume_off else IconRes.drawable.ic_volume_up),
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
            modifier = Modifier.width(MEDIA_VOLUME_SLIDER_WIDTH),
            trailingLabel = "${(viewModel.effectiveVolume * 100).toInt()}%"
        )
    }
}
