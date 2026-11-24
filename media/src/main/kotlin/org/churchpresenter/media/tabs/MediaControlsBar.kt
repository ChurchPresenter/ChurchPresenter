@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.churchpresenter.media.tabs

import org.churchpresenter.media.viewmodel.MediaViewModel
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.churchpresenter.settings.OutputScaleMode
import org.churchpresenter.sharedui.utils.icon
import org.churchpresenter.sharedui.utils.label
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.launch
import org.churchpresenter.theme.components.AppDropdownMenu
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.button.CircularButton
import org.churchpresenter.hosanna.composables.button.IconToggleButton
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import androidx.compose.ui.graphics.vector.rememberVectorPainter

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
        MediaTransport(viewModel)

        // (Elapsed / total time now flank the seek bar below, so the combined time is
        // no longer shown here.)

        // Divider
        Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

        MediaLoopControls(viewModel)

        MediaScaleButton(viewModel)

        // Divider
        Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

        MediaSubtitlesButton(viewModel)

        // Divider
        Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

        MediaVolume(viewModel)
    }
}

/** Raised keys, Play the biggest and lit. */
@Composable
private fun MediaTabScope.MediaTransport(viewModel: MediaViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tooltip(text = stringResource(Res.string.media_seek_backward)) {
            IconButton(
                icon = painterResource(IconRes.drawable.ic_fast_rewind),
                contentDescription = stringResource(Res.string.media_seek_backward),
                onClick = { viewModel.position.seekBackward() },
                size = IconButtonSize.Small,
                hierarchy = ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
                enabled = viewModel.isLoaded,
            )
        }
        MediaPlayKey(viewModel)
        Tooltip(text = stringResource(Res.string.stop)) {
            IconButton(
                icon = painterResource(IconRes.drawable.ic_stop),
                contentDescription = stringResource(Res.string.stop),
                onClick = { viewModel.stop() },
                size = IconButtonSize.Small,
                hierarchy = ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
                enabled = viewModel.isLoaded,
            )
        }
        Tooltip(text = stringResource(Res.string.media_seek_forward)) {
            IconButton(
                icon = painterResource(IconRes.drawable.ic_fast_forward),
                contentDescription = stringResource(Res.string.media_seek_forward),
                onClick = { viewModel.position.seekForward() },
                size = IconButtonSize.Small,
                hierarchy = ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
                enabled = viewModel.isLoaded,
            )
        }
    }
}

@Composable
private fun MediaTabScope.MediaPlayKey(viewModel: MediaViewModel) {
    Tooltip(text = stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play)) {
        CircularButton(
            icon = painterResource(
                if (viewModel.isPlaying) IconRes.drawable.ic_pause else IconRes.drawable.ic_play
            ),
            contentDescription = stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play),
            onClick = { viewModel.togglePlayPause() },
            size = IconButtonSize.Large,
            hierarchy = ButtonHierarchy.Primary,
            intent = ButtonIntent.Default,
            enabled = viewModel.isLoaded,
        )
    }
}

/** The loop key, and while looping the count of repeats beside it. */
@Composable
private fun MediaTabScope.MediaLoopControls(viewModel: MediaViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val loopLabel = stringResource(if (viewModel.isLooping) Res.string.loop_on else Res.string.loop_off)
        Tooltip(text = loopLabel) {
            IconToggleButton(
                icon = painterResource(IconRes.drawable.ic_refresh),
                contentDescription = loopLabel,
                checked = viewModel.isLooping,
                onCheckedChange = { viewModel.looping.toggleLooping() },
                size = IconButtonSize.Small,
                hierarchy = ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
                enabled = viewModel.isLoaded,
            )
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
                    onValueChange = { viewModel.looping.setLoopCount(it) }
                )
            }
        }
    }
}

/** Each click moves Fit → Fill → Stretch on every profile at once, like the Pictures tab's button. */
@Composable
private fun MediaTabScope.MediaScaleButton(viewModel: MediaViewModel) {
    val shared = sharedScaleMode(appSettings.projectionSettings.outputProfiles) { it.mediaScaleMode }
    val scaleMode = shared ?: OutputScaleMode.FIT
    val scaled = shared != OutputScaleMode.FIT
    val scaleLabel = scaleButtonLabel(shared, scaleMode, ScaleButtonContent.MEDIA)
    Tooltip(text = scaleLabel) {
        IconToggleButton(
            icon = rememberVectorPainter(scaleMode.icon),
            contentDescription = scaleLabel,
            checked = scaled,
            onCheckedChange = {
                val next = if (shared == null) scaleMode else scaleMode.next()
                onSettingsChange { s -> s.withMediaScaleEverywhere(next) }
            },
            size = IconButtonSize.Small,
            hierarchy = ButtonHierarchy.Tertiary,
            intent = ButtonIntent.Neutral,
            enabled = viewModel.isLoaded,
        )
    }
}

/** Subtitles: off, one of the tracks VLC found, or a file of the operator's own. */
@Composable
private fun MediaTabScope.MediaSubtitlesButton(viewModel: MediaViewModel) {
    var subtitlesExpanded by remember { mutableStateOf(false) }
    val subtitlesLabel = stringResource(Res.string.media_subtitles)
    val subtitlesShowing = viewModel.subtitlesVisible
    val subtitleFilesLabel = stringResource(Res.string.media_subtitles_files)
    val subtitleFileTitle = stringResource(Res.string.media_subtitles_load_file)
    Box {
        Tooltip(text = subtitlesLabel) {
            IconButton(
                icon = painterResource(IconRes.drawable.ic_subtitles),
                contentDescription = subtitlesLabel,
                onClick = { subtitlesExpanded = true },
                size = IconButtonSize.Small,
                hierarchy = if (subtitlesShowing) ButtonHierarchy.Primary else ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
                enabled = viewModel.isLoaded && !viewModel.isAudioFile,
            )
        }
        AppDropdownMenu(expanded = subtitlesExpanded, onDismissRequest = { subtitlesExpanded = false }) {
            SubtitleMenuItems(
                state = SubtitleMenuState(
                    subtitlesVisible = viewModel.subtitlesVisible,
                    sidecars = viewModel.sidecarSubtitles,
                    embeddedTracks = viewModel.subtitleTracks,
                    selectedEmbeddedTrack = viewModel.selectedSubtitleTrack,
                ),
                actions = SubtitleMenuActions(
                    onTurnOff = viewModel.subtitles::turnSubtitlesOff,
                    onSidecarEnabled = viewModel.subtitles::setSidecarEnabled,
                    onSidecarOutputs = viewModel.subtitles::setSidecarOutputs,
                    onSelectEmbedded = viewModel.subtitles::selectSubtitleTrack,
                ),
                profiles = appSettings.projectionSettings.outputProfiles,
                loadFileLabel = subtitleFileTitle,
                onLoadFile = {
                    subtitlesExpanded = false
                    scope.launch {
                        // The video's own folder, where a subtitle for it almost always
                        // sits, rather than the top of the media library.
                        val beside = runCatching { Path(viewModel.mediaUrl).parent }.getOrNull()
                        val f = fileChooser.chooseSingle(
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
                        if (f != null) viewModel.subtitles.addSubtitleFile(f.absolutePathString())
                    }
                },
            )
        }
    }
}

/** A mute key with the slider beside it, both always in the bar. */
@Composable
private fun MediaTabScope.MediaVolume(viewModel: MediaViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val muteLabel = stringResource(
            if (viewModel.isMuted) Res.string.media_unmute else Res.string.media_mute
        )
        Tooltip(text = muteLabel) {
            IconButton(
                icon = painterResource(if (viewModel.isMuted ||
                    viewModel.volume == 0f) IconRes.drawable.ic_volume_off else IconRes.drawable.ic_volume_up),
                contentDescription = muteLabel,
                onClick = { viewModel.audio.toggleMute() },
                size = IconButtonSize.Small,
                hierarchy = ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
                enabled = viewModel.isLoaded,
            )
        }
        SlimSlider(
            value = if (viewModel.isMuted) 0f else viewModel.volume,
            onValueChange = { viewModel.audio.setVolume(it) },
            valueRange = 0f..1f,
            enabled = viewModel.isLoaded,
            modifier = Modifier.width(MEDIA_VOLUME_SLIDER_WIDTH),
            trailingLabel = "${(viewModel.effectiveVolume * 100).toInt()}%"
        )
    }
}
