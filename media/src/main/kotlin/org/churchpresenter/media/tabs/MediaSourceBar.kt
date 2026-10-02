@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.churchpresenter.media.tabs

import org.churchpresenter.media.viewmodel.MediaViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_to_schedule
import org.churchpresenter.strings.generated.resources.save_preset
import org.churchpresenter.strings.generated.resources.clear
import org.churchpresenter.strings.generated.resources.clear_recents
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_folder
import org.churchpresenter.strings.generated.resources.media_load
import org.churchpresenter.strings.generated.resources.media_no_source
import org.churchpresenter.strings.generated.resources.media_now_playing
import org.churchpresenter.strings.generated.resources.media_select_file
import org.churchpresenter.strings.generated.resources.media_url_placeholder
import org.churchpresenter.strings.generated.resources.play
import org.churchpresenter.strings.generated.resources.recent
import org.churchpresenter.sharedui.composables.AddToScheduleButton
import org.churchpresenter.sharedui.composables.SavePresetButton
import org.churchpresenter.sharedui.composables.GoLiveButton
import org.churchpresenter.sharedui.composables.SegmentedButton
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.Constants
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
import org.churchpresenter.sharedui.composables.RecentChip
import org.churchpresenter.sharedui.composables.topBarCard

/** The top card: the source bar, the recent files, and the playback controls. */
@Composable
internal fun MediaTabScope.MediaTopCard(viewModel: MediaViewModel) {
    Column(modifier = Modifier.fillMaxWidth().topBarCard()) {
        MediaSourceBar(viewModel)

        MediaRecentBar(viewModel)

        MediaControlsBar(viewModel)
    }
}

@Composable
private fun MediaTabScope.MediaSourceBar(viewModel: MediaViewModel) {
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
                MediaLocalSource(viewModel, Modifier.weight(1f))
            }
            Constants.MEDIA_TYPE_URL -> {
                MediaUrlSource(viewModel, Modifier.weight(1f))
            }
        }
        // The "now playing" label travels with the two action buttons as one group, so the
        // status and the controls it describes wrap together rather than splitting across
        // lines at a narrow width.
        MediaSourceActions(viewModel)
    }
}

/** Choose a file, and the name of the one loaded. */
@Composable
private fun MediaTabScope.MediaLocalSource(viewModel: MediaViewModel, modifier: Modifier) {
    RaisedButton(
        onClick = {
            scope.launch {
                val f = fileChooser.chooseSingle(
                    path = Path(appSettings.mediaStorageDirectory),
                    title = selectFileLabel,
                    filters = listOf(FileNameExtensionFilter(
                        mediaFilesLabel,
                        "mp4",
                        "mov",
                        "avi",
                        "mkv",
                        "wmv",
                        "flv",
                        "webm",
                        "m4v",
                        "mp3",
                        "wav",
                        "flac",
                        "aac",
                        "ogg",
                        "wma",
                        "m4a",
                        "aiff",
                        "opus",
                    )),
                    selectDirectory = false
                )
                if (f != null) {
                    val ext = f.extension.lowercase()
                    val type = if (ext in Constants.AUDIO_EXTENSIONS) {
                        Constants.MEDIA_TYPE_AUDIO
                    } else {
                        Constants.MEDIA_TYPE_LOCAL
                    }
                    if (presenterManager?.presentingMode?.value == Presenting.MEDIA) {
                        presenterManager.requestClearDisplay()
                    }
                    viewModel.loadMedia(f.absolutePathString(), type)
                    RecentMediaFiles.add(f.absolutePathString())
                }
            }
        },
        modifier = Modifier.height(32.dp),
        shape = AppShape(7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    ) {
        Icon(painterResource(IconRes.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            stringResource(Res.string.media_select_file),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
        )
    }
    Text(
        text = if (viewModel.isLoaded && viewModel.mediaType != Constants.MEDIA_TYPE_URL) viewModel.mediaTitle
               else stringResource(Res.string.media_no_source),
        style = MaterialTheme.typography.bodySmall,
        color = if (viewModel.isLoaded && viewModel.mediaType != Constants.MEDIA_TYPE_URL)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** The address field and Load. */
@Composable
private fun MediaTabScope.MediaUrlSource(viewModel: MediaViewModel, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(42.dp)
            .sunken(AppShape(8.dp), elevationPalette())
            .hoverTint(AppShape(8.dp)),
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
                        Text(
                            stringResource(Res.string.media_url_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
        shape = AppShape(7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    ) {
        Text(
            stringResource(Res.string.media_load),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** "Now playing", Save preset, Add to Schedule and Go Live, which wrap together. */
@Composable
private fun MediaTabScope.MediaSourceActions(viewModel: MediaViewModel) {
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

@Composable
private fun MediaTabScope.MediaRecentBar(viewModel: MediaViewModel) {
    // ── Recent files bar ──────────────────────────────────────────
    val recentOrdered = RecentMediaFiles.pinned +
        RecentMediaFiles.paths.filter { it !in RecentMediaFiles.pinned }
    if (recentOrdered.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(Res.string.recent),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            TooltipArea(
                tooltip = {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = MaterialTheme.shapes.extraSmall,
                        tonalElevation = 4.dp,
                    ) {
                        Text(
                            stringResource(Res.string.clear_recents),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                tooltipPlacement = TooltipPlacement.ComponentRect(
                    anchor = Alignment.BottomCenter,
                    offset = DpOffset(0.dp, 4.dp),
                )
            ) {
                KeyIconButton(onClick = { RecentMediaFiles.clear() }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        painterResource(IconRes.drawable.ic_close),
                        contentDescription = stringResource(Res.string.clear),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                lazyItems(recentOrdered) { path ->
                    val isPinned = path in RecentMediaFiles.pinned
                    val isActive = viewModel.isLoaded && viewModel.mediaUrl == path
                    val displayName = if (path.startsWith("http://") ||
                        path.startsWith("https://") ||
                        path.startsWith("rtsp://")) path else java.io.File(path).name
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
                            if (presenterManager?.presentingMode?.value == Presenting.MEDIA) {
                                presenterManager.requestClearDisplay()
                            }
                            viewModel.loadMedia(path, type)
                            RecentMediaFiles.add(path)
                        },
                        onTogglePin = { RecentMediaFiles.togglePin(path) },
                    )
                }
            }
        }
    }
}
