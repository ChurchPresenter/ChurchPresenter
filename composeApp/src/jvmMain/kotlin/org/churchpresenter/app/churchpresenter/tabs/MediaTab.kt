@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.media_audio_continues
import churchpresenter.composeapp.generated.resources.media_files_filter
import churchpresenter.composeapp.generated.resources.media_local_file
import churchpresenter.composeapp.generated.resources.media_network_url
import churchpresenter.composeapp.generated.resources.media_no_source
import churchpresenter.composeapp.generated.resources.media_now_presenting
import churchpresenter.composeapp.generated.resources.media_select_file
import churchpresenter.composeapp.generated.resources.media_select_to_begin
import churchpresenter.composeapp.generated.resources.media_vlc_arch_mismatch
import churchpresenter.composeapp.generated.resources.media_vlc_install
import churchpresenter.composeapp.generated.resources.media_vlc_load_failed
import churchpresenter.composeapp.generated.resources.media_vlc_required
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import org.churchpresenter.app.churchpresenter.LocalWentLive
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputPicker
import org.churchpresenter.app.churchpresenter.composables.rememberPreviewOutput
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.SharedVideoOutputDisplay
import org.churchpresenter.app.churchpresenter.composables.SoftwareVideoPlayer
import org.churchpresenter.app.churchpresenter.composables.VideoPlayer
import org.churchpresenter.app.churchpresenter.composables.isVlcArchMismatch
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.isVlcLoadFailed
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.utils.contentScale
import org.churchpresenter.app.churchpresenter.utils.label
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource

private const val HANDLE_VISIBLE_ALPHA = 0.01f

/** Upper bound of the loop-count field; 0 means repeat forever. */
internal const val MEDIA_MAX_LOOP_COUNT = 99
internal const val MEDIA_DISABLED_TRANSPORT_ALPHA = 0.38f
internal val MEDIA_TRANSPORT_KEY_SIZE = 30.dp
internal val MEDIA_PLAY_KEY_SIZE = 38.dp
internal val MEDIA_VOLUME_SLIDER_WIDTH = 150.dp

/**
 * Whether the tab composes the hidden VLC players behind its preview. Always true in the app.
 *
 * The tab's tests turn it off: on a machine with VLC installed those players really open the test's
 * made-up URL, and VLC's error for it pauses the view model at a moment of its own choosing, partway
 * through a test. The same reason `vlcAvailable` is a parameter, one level further in.
 */
internal val LocalMediaVlcPlayers = staticCompositionLocalOf { true }

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
        MediaVlcUnavailable(modifier, vlcArchMismatch, vlcLoadFailed)
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
    val state = remember { MediaTabState() }
    val selectFileLabel = stringResource(Res.string.media_select_file)
    val mediaFilesLabel = stringResource(Res.string.media_files_filter)

    val shortcuts = LocalShortcuts.current
    val wentLive = LocalWentLive.current
    // Remembered, keyed on everything it holds: a new scope on every recomposition would hand the
    // pieces new lambdas each time, and a click handler keyed on its lambda would restart.
    val tab = remember(
        appSettings, onSettingsChange, onAddToSchedule, onSavePreset, presenterManager, onInstanceLinkSendProject,
        viewModel, state, scope, sourceTypeItems, selectFileLabel, mediaFilesLabel, shortcuts, wentLive
    ) {
        MediaTabScope(
            appSettings = appSettings,
            onSettingsChange = onSettingsChange,
            onAddToSchedule = onAddToSchedule,
            onSavePreset = onSavePreset,
            presenterManager = presenterManager,
            onInstanceLinkSendProject = onInstanceLinkSendProject,
            viewModel = viewModel,
            state = state,
            scope = scope,
            sourceTypeItems = sourceTypeItems,
            selectFileLabel = selectFileLabel,
            mediaFilesLabel = mediaFilesLabel,
            shortcuts = shortcuts,
            wentLive = wentLive,
        )
    }
    tab.MediaTabEffects(selectedMediaItem, selectedMediaItemVersion, instanceLinkMediaStreamUrl, focusRequester)

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent -> tab.handleKey(keyEvent) }
    ) {
        tab.MediaTopCard()
        // Seek bar and preview share one card.
        tab.MediaPreviewCard(Modifier.weight(1f))
    }
}

/** What the tab shows instead when VLC is missing, the wrong architecture, or failed to load. */
@Composable
private fun MediaVlcUnavailable(modifier: Modifier, vlcArchMismatch: Boolean, vlcLoadFailed: Boolean) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = if (vlcArchMismatch || vlcLoadFailed) Icons.Default.Warning else Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (vlcArchMismatch || vlcLoadFailed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                }
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
}

@Composable
private fun MediaTabScope.MediaPreviewCard(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            .bibleListCard()
    ) {
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
        val isPresenting =
            presenterManager?.presentingMode?.value == Presenting.MEDIA && presenterManager.showPresenterWindow.value

        val vlcPlayers = LocalMediaVlcPlayers.current
        if (viewModel.isLoaded && viewModel.isAudioFile) {
            if (vlcPlayers) VideoPlayer(viewModel = viewModel, modifier = Modifier.size(0.dp))
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.media_audio_continues),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            if (viewModel.isLoaded && vlcPlayers) {
                SoftwareVideoPlayer(viewModel = viewModel, modifier = Modifier.size(0.dp))
            }


            // The shape of the output this media actually goes out on. Media can be routed to
            // several differently-shaped outputs at once, so which one the preview stands for is
            // the operator's to say -- the picker draws nothing until there is more than one.
            MediaOutputPreview(isPresenting, Modifier.weight(1f))
        }
    }
}

/** The output picker and the preview of what goes out, in the output's own shape. */
@Composable
private fun MediaTabScope.MediaOutputPreview(isPresenting: Boolean, modifier: Modifier) {
    val previewOutput = rememberPreviewOutput(appSettings, Constants.PREVIEW_TAB_MEDIA, Presenting.MEDIA)
    PreviewOutputPicker(
        settings = appSettings,
        tabId = Constants.PREVIEW_TAB_MEDIA,
        mode = Presenting.MEDIA,
        onSettingsChange = onSettingsChange,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
    )
    Box(
        modifier = modifier.fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(previewOutput.size.aspectRatio)
                .background(Color.Black, AppShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isPresenting -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = Color.White.copy(alpha = 0.6f),
                    )
                    Text(
                        stringResource(Res.string.media_now_presenting),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        viewModel.mediaTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                viewModel.isLoaded -> SharedVideoOutputDisplay(
                    modifier = Modifier.fillMaxSize(),
                    contentScale = appSettings.mediaScaleMode.contentScale,
                )
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = Color.White.copy(alpha = 0.4f),
                    )
                    Text(
                        stringResource(Res.string.media_no_source),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Text(
                        stringResource(Res.string.media_select_to_begin),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

/** The dark tooltip bubble the media transport controls hover. */
@Composable
internal fun TransportTooltip(text: String) {
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
                .seekGestures(duration, onSeek) { dragging = it },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackH = 5.dp.toPx()
                val cy = size.height / 2f
                val top = cy - trackH / 2f
                val radius = CornerRadius(trackH / 2f, trackH / 2f)
                // Base track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, trackH),
                    cornerRadius = radius,
                )
                // Buffered (loaded-ahead) region
                if (bufferedFraction > 0f) {
                    drawRoundRect(
                        color = bufferedColor,
                        topLeft = Offset(0f, top),
                        size = Size(size.width * bufferedFraction, trackH),
                        cornerRadius = radius,
                    )
                }
                // Played region — teal gradient
                if (playedFraction > 0f) {
                    val playedW = size.width * playedFraction
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(playedStart, primary),
                            startX = 0f,
                            endX = playedW.coerceAtLeast(trackH),
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(playedW, trackH),
                        cornerRadius = radius
                    )
                }
                // Hover handle — fades/scales in only on hover or drag
                if (handleAlpha > HANDLE_VISIBLE_ALPHA) {
                    val hx = (size.width * playedFraction).coerceIn(0f, size.width)
                    drawCircle(
                        color = handleColor.copy(alpha = handleAlpha),
                        radius = 6.dp.toPx() * handleScale,
                        center = Offset(hx, cy),
                    )
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


/** Tap or drag anywhere on the track to seek; [onDragging] follows whether a drag is under way. */
private fun Modifier.seekGestures(duration: Long, onSeek: (Long) -> Unit, onDragging: (Boolean) -> Unit): Modifier =
    pointerInput(duration) {
        detectTapGestures { offset ->
            if (duration > 0 && size.width > 0) {
                onSeek((offset.x / size.width * duration).toLong().coerceIn(0L, duration))
            }
        }
    }
    .pointerInput(duration) {
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                onDragging(true)
                if (duration > 0 && size.width > 0) {
                    onSeek((offset.x / size.width * duration).toLong().coerceIn(0L, duration))
                }
            },
            onDragEnd = { onDragging(false) },
            onDragCancel = { onDragging(false) },
            onHorizontalDrag = { change, _ ->
                if (duration > 0 && size.width > 0) {
                    onSeek((change.position.x / size.width * duration).toLong().coerceIn(0L, duration))
                }
            }
        )
    }
