@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import org.churchpresenter.ui.initialPassClickable
import org.churchpresenter.ui.finalPassClickable
import org.churchpresenter.ui.AddToScheduleButton
import org.churchpresenter.ui.GoLiveButton
import org.churchpresenter.ui.LabeledRadioButton
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.FilledIconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import java.awt.Cursor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Warning
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.ic_pause
import org.churchpresenter.resources.generated.resources.ic_play
import org.churchpresenter.resources.generated.resources.add_to_schedule
import org.churchpresenter.resources.generated.resources.atem_loading_slots
import org.churchpresenter.resources.generated.resources.atem_slot_empty
import org.churchpresenter.resources.generated.resources.atem_slot_in_use
import org.churchpresenter.resources.generated.resources.atem_slot_named
import org.churchpresenter.resources.generated.resources.atem_slot_unnamed
import org.churchpresenter.resources.generated.resources.atem_mode_clip
import org.churchpresenter.resources.generated.resources.atem_mode_still
import org.churchpresenter.resources.generated.resources.atem_aspect_mismatch
import org.churchpresenter.resources.generated.resources.atem_clip_capacity_info
import org.churchpresenter.resources.generated.resources.atem_clip_too_long
import org.churchpresenter.resources.generated.resources.atem_golive_key
import org.churchpresenter.resources.generated.resources.atem_unreachable
import org.churchpresenter.resources.generated.resources.atem_upscale_notice
import org.churchpresenter.resources.generated.resources.atem_preparing
import org.churchpresenter.resources.generated.resources.atem_quick_clip_tooltip
import org.churchpresenter.resources.generated.resources.atem_quick_still_tooltip
import org.churchpresenter.resources.generated.resources.atem_ready
import org.churchpresenter.resources.generated.resources.atem_send_to_atem
import org.churchpresenter.resources.generated.resources.atem_slot
import org.churchpresenter.resources.generated.resources.atem_slots_error
import org.churchpresenter.resources.generated.resources.atem_upload
import org.churchpresenter.resources.generated.resources.atem_upload_error
import org.churchpresenter.resources.generated.resources.atem_uploading_image
import org.churchpresenter.resources.generated.resources.atem_uploading_video
import org.churchpresenter.resources.generated.resources.atem_processing
import org.churchpresenter.resources.generated.resources.atem_upload_mode
import org.churchpresenter.resources.generated.resources.atem_uploading
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import org.churchpresenter.resources.generated.resources.cancel
import org.churchpresenter.resources.generated.resources.confirm_delete
import org.churchpresenter.resources.generated.resources.confirm_delete_file
import org.churchpresenter.resources.generated.resources.go_live
import org.churchpresenter.resources.generated.resources.ic_close
import org.churchpresenter.resources.generated.resources.ic_key
import org.churchpresenter.resources.generated.resources.ic_upload
import org.churchpresenter.resources.generated.resources.lottie_no_presets
import org.churchpresenter.resources.generated.resources.lottie_select_preset
import org.churchpresenter.resources.generated.resources.pause
import org.churchpresenter.resources.generated.resources.play
import org.churchpresenter.resources.generated.resources.tooltip_remove
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import javax.swing.JOptionPane
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.atem.formatAtemFps
import org.churchpresenter.atem.AtemClient
import org.churchpresenter.companionserver.LottieRenderCache
import org.churchpresenter.atem.AtemUploadStatus
import org.churchpresenter.companionserver.LowerThirdSequencer
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.ui.presenterAspectRatio
import org.churchpresenter.ui.formatAspectRatio
import org.churchpresenter.ui.presenterScreenBounds
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.resources.generated.resources.generate_lower_third
import org.churchpresenter.resources.generated.resources.aspect_ratio_mismatch
import org.churchpresenter.companionserver.isLottieFile
import org.churchpresenter.theme.semantic
import java.awt.Window
import java.io.File
import javax.swing.SwingUtilities

private const val ATEM_REACHABLE_POLL_MS = 30_000L
private const val ATEM_UNREACHABLE_POLL_MS = 10_000L
private const val UPLOAD_ERROR_DISPLAY_MS = 8000L
private const val COMPOSITION_LOAD_SETTLE_MS = 3000L
private const val DEFAULT_FRAME_RATE = 30f
private const val MILLIS_PER_SECOND_F = 1000f
private const val PREVIEW_SETTLE_MS = 800L
private const val ASPECT_EPSILON = 0.01f
private const val MAX_FIT_SCALE = 1.01f
private const val SELECTION_BAR_WIDTH = 4f

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
// Both pre-existing, and both baselined in `:composeApp` before this file moved.
//
// LongMethod: the tab is one long Compose tree — the preset list, the preview, the ATEM dialog and
// the quick-upload bar. Splitting it would move the length rather than remove it.
//
// TooGenericExceptionCaught: the two catches inside guard *injected* calls — `queryAtemState` is a
// parameter, and the upload path runs whatever the caller supplied. Narrowing to `IOException`
// would let a caller's other failure escape into the composition and take the tab down, which is
// the opposite of what these are for. See the comment at each site.
@Suppress("LongMethod", "TooGenericExceptionCaught")
@Composable
fun LowerThirdTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    /**
     * Whether the main window is maximised, which decides *which* saved panel width is used —
     * `maximizedLayout` or `windowedLayout`. A parameter rather than `LocalMainWindowState`, which
     * is a `:composeApp` composition local: this is the only thing the tab ever asked it, and a
     * boolean is also something a test can set.
     */
    isWindowMaximized: Boolean = true,
    selectedLowerThirdItem: ScheduleItem.LowerThirdItem? = null,
    /**
     * Bumped by the caller on every schedule click, so clicking the *same* item twice re-runs the
     * effect below. Keyed on the item alone, an unchanged item is an unchanged key and the second
     * click does nothing.
     */
    selectedLowerThirdItemVersion: Int = 0,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: (
        presetId: String,
        presetLabel: String,
        pauseAtFrame: Boolean,
        pauseDurationMs: Long,
    ) -> Unit = { _, _, _, _ -> },
    onGoLive: (
        jsonContent: String,
        pauseAtFrame: Boolean,
        pauseFrame: Float,
        pauseDurationMs: Long,
        presetName: String,
    ) -> Unit = { _, _, _, _, _ -> },
    onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit = { _, _ -> },
    /**
     * Reads the ATEM's media-pool state — what the upload dialog is built from.
     *
     * A parameter rather than a direct `AtemClient(...).queryState()` because that call is **UDP with
     * a 5s socket timeout**: there is no fast connection-refused, so every test that opened this
     * dialog would cost five seconds whether or not a switcher existed. That timeout, not the absence
     * of hardware, is what kept this tab capped at ~42%. The default is the real client, so callers
     * are unaffected; a test supplies a canned [AtemState] or throws to exercise the error path.
     */
    queryAtemState: suspend (host: String, port: Int) -> AtemState = { host, port ->
        AtemClient(host, port).queryState()
    },
    /**
     * Whether the switcher answers at all — polled on a loop, and what enables the upload buttons.
     *
     * Injected alongside [queryAtemState] because seaming only the state query is not enough: the
     * button that opens the dialog is disabled until this says the device is there, so a test could
     * never reach the dialog. Defaults to the real probe.
     */
    probeAtemReachable: suspend (host: String, port: Int) -> Boolean = { host, port ->
        AtemClient.isReachable(host, port)
    },
) {
    val lottieFolder = appSettings.streamingSettings.lowerThirdFolder
    var refreshKey by remember { mutableStateOf(0) }

    // Watch for external file changes (add/remove via file explorer, etc.)
    LaunchedEffect(lottieFolder) {
        if (lottieFolder.isEmpty()) return@LaunchedEffect
        val folder = File(lottieFolder)
        if (!folder.exists() || !folder.isDirectory) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val watchService = FileSystems.getDefault().newWatchService()
                folder.toPath().register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
                )
                try {
                    while (isActive) {
                        val key = watchService.take()
                        val hasJsonChange = key.pollEvents().any { event ->
                            event.kind() != StandardWatchEventKinds.OVERFLOW &&
                                event.context().toString().lowercase().endsWith(".json")
                        }
                        if (hasJsonChange) {
                            withContext(Dispatchers.Main) { refreshKey++ }
                        }
                        if (!key.reset()) break
                    }
                } finally {
                    watchService.close()
                }
            } catch (_: java.nio.file.ClosedWatchServiceException) {}
            catch (_: InterruptedException) {}
        }
    }

    // Build file list from user-chosen folder
    val lottieFiles = remember(lottieFolder, refreshKey) {
        if (lottieFolder.isEmpty()) emptyList()
        else File(lottieFolder).takeIf { it.exists() && it.isDirectory }
            ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
            ?.sortedBy { it.nameWithoutExtension.lowercase() } ?: emptyList()
    }

    // Pre-render ATEM uploads in the background for every lottie file as soon as it
    // appears (generator save, file drop, edit) — Send to ATEM then streams a ready file
    LaunchedEffect(lottieFiles, appSettings.atemSettings) {
        lottieFiles.forEach { LottieRenderCache.ensureForFile(it, appSettings.atemSettings, SkiaLottieFrameRenderer) }
    }

    val scope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<Job?>(null) }

    var selectedFile by remember { mutableStateOf<File?>(null) }
    val animatedProgress = remember { Animatable(0f) }
    var isPlaying by remember { mutableStateOf(false) }

    // ATEM upload dialog state
    val atemConfigured = appSettings.atemSettings.host.isNotBlank()
    var atemReachable by remember { mutableStateOf(false) }
    // Sticky: true once the current host/port has responded at least once. Gates whether the ATEM
    // controls are shown at all — they appear only after a real response and stay shown if the
    // device later drops; reset (hidden again) only when the IP/port changes.
    var atemEverConnected by remember(appSettings.atemSettings.host, appSettings.atemSettings.port) {
        mutableStateOf(false)
    }
    var showAtemDialog by remember { mutableStateOf(false) }

    // Reachability poll — one hello packet per cycle, only while this tab is composed.
    // Keyed on host/port so changing the IP in settings re-checks immediately,
    // no Test Connection required.
    LaunchedEffect(appSettings.atemSettings.host, appSettings.atemSettings.port) {
        val host = appSettings.atemSettings.host
        val port = appSettings.atemSettings.port
        if (host.isBlank()) {
            atemReachable = false
            return@LaunchedEffect
        }
        while (isActive) {
            val reachable = probeAtemReachable(host, port)
            atemReachable = reachable
            if (reachable) atemEverConnected = true
            delay(if (reachable) ATEM_REACHABLE_POLL_MS else ATEM_UNREACHABLE_POLL_MS)
        }
    }
    var atemIsClip by remember { mutableStateOf(false) }
    var atemSlot by remember { mutableStateOf(0) }
    var atemBusy by remember { mutableStateOf(false) }              // upload click in progress
    var atemPrepareProgress by remember { mutableStateOf(1f) }      // cache render progress, 1f = ready
    var atemProgress by remember { mutableStateOf<Float?>(null) }   // upload progress, null = idle
    var atemError by remember { mutableStateOf<String?>(null) }
    var atemSlots by remember { mutableStateOf<List<AtemMediaSlot>>(emptyList()) }
    var atemClipMaxFrames by remember { mutableStateOf(appSettings.atemSettings.detectedClipMaxFrames) }
    var atemSlotsLoading by remember { mutableStateOf(false) }
    var atemSlotsError by remember { mutableStateOf<String?>(null) }
    var atemDetectedFps by remember { mutableStateOf<Double?>(null) }
    // Status of an API/Companion-triggered upload, so the same bar reflects those too
    val remoteUpload by AtemUploadStatus.state.collectAsState()
    // Auto-dismiss a remote upload error after a while (success self-clears server-side)
    LaunchedEffect(remoteUpload?.error) {
        val errored = remoteUpload
        if (errored?.error != null) { delay(UPLOAD_ERROR_DISPLAY_MS); AtemUploadStatus.clear(errored.id) }
    }

    // Fetch media pool slot info + FPS when dialog opens or mode toggles
    LaunchedEffect(showAtemDialog, atemIsClip) {
        if (!showAtemDialog) return@LaunchedEffect
        atemSlotsLoading = true
        atemSlotsError = null
        try {
            val state = withContext(Dispatchers.IO) {
                queryAtemState(appSettings.atemSettings.host, appSettings.atemSettings.port)
            }
            atemSlots = if (atemIsClip) state.clipSlots else state.stillSlots
            atemDetectedFps = state.fps
            if (state.clipMaxFrames.isNotEmpty()) atemClipMaxFrames = state.clipMaxFrames
            atemReachable = true
            atemEverConnected = true
            // Snap to a valid slot if the configured default doesn't exist on this device
            if (atemSlots.isNotEmpty() && atemSlots.none { it.index == atemSlot }) {
                atemSlot = atemSlots.first().index
            }
        } catch (e: Exception) {
            atemSlotsError = e.message
            atemSlots = emptyList()
            atemReachable = false
        } finally {
            atemSlotsLoading = false
        }
    }

    // When a schedule item is clicked, find the matching file by name
    LaunchedEffect(selectedLowerThirdItem, selectedLowerThirdItemVersion) {
        val item = selectedLowerThirdItem ?: return@LaunchedEffect
        val file = lottieFiles.find { it.nameWithoutExtension == item.presetLabel || it.name == item.presetLabel }
            ?: lottieFiles.find { it.nameWithoutExtension == item.presetId }
        if (file != null) {
            selectedFile = file
            animJob?.cancel()
            animJob = null
            animatedProgress.snapTo(0f)
            isPlaying = false
        }
    }

    val jsonContent = remember(selectedFile) {
        val f = selectedFile ?: return@remember ""
        if (!f.exists()) return@remember ""
        f.readText()
    }

    val composition by rememberLottieComposition(key = jsonContent) {
        LottieCompositionSpec.JsonString(jsonContent.ifBlank { "{}" })
    }

    // True while composition is loading — prevents flashing warning triangle during async load
    var isCompositionLoading by remember(jsonContent) { mutableStateOf(jsonContent.isNotBlank()) }
    LaunchedEffect(composition) { if (composition != null) isCompositionLoading = false }
    LaunchedEffect(jsonContent) {
        if (jsonContent.isNotBlank()) {
            delay(COMPOSITION_LOAD_SETTLE_MS)
            isCompositionLoading = false
        }
    }

    // Reset when file changes
    LaunchedEffect(selectedFile) {
        animJob?.cancel()
        animJob = null
        animatedProgress.snapTo(0f)
        isPlaying = false
    }

    fun totalDurationMs(): Long =
        ((composition?.durationFrames ?: 0f) / (composition?.frameRate ?: DEFAULT_FRAME_RATE) * MILLIS_PER_SECOND_F)
            .toLong().coerceAtLeast(1L)

    // Cache variant for an ATEM upload. Frame count comes from the lottie JSON itself
    // (same source as background pre-generation) so both hit the same key.
    // Quick upload passes useDetectedFps=false so it always hits the pre-generated cache.
    fun atemVariant(isClip: Boolean, useDetectedFps: Boolean = true): LottieRenderCache.Variant {
        val s = appSettings.atemSettings
        val fps = (if (useDetectedFps) atemDetectedFps else null) ?: s.clipFps
        val fallbackFrames = ((totalDurationMs() / 1000.0) * fps).toInt().coerceAtLeast(1)
        return LottieRenderCache.atemVariant(jsonContent, s, isClip, fps, fallbackFrames)
    }

    // Kick off (or attach to) cache preparation when the dialog opens or its mode changes,
    // and mirror the render progress into the dialog
    LaunchedEffect(showAtemDialog, atemIsClip, jsonContent, atemDetectedFps) {
        if (!showAtemDialog || jsonContent.isBlank()) return@LaunchedEffect
        val variant = atemVariant(atemIsClip)
        LottieRenderCache.prepare(jsonContent, variant, SkiaLottieFrameRenderer)
        LottieRenderCache.progressFlow(jsonContent, variant).collect { atemPrepareProgress = it }
    }

    /**
     * Render-from-cache + upload, shared by the dialog's Upload button and the
     * quick-upload buttons. [variant] decides still vs clip and the fps/frame count.
     */
    fun startAtemUpload(variant: LottieRenderCache.Variant, slot: Int, closeDialogOnSuccess: Boolean) {
        val presetName = selectedFile?.nameWithoutExtension ?: ""
        val atemSettings = appSettings.atemSettings
        atemBusy = true
        atemError = null
        scope.launch {
            var uploadId: Long? = null
            try {
                // Awaits the background render when it isn't done yet;
                // instant when the cache file already exists
                val cached = LottieRenderCache.prepare(jsonContent, variant, SkiaLottieFrameRenderer).await()
                atemProgress = 0f
                // Publish to the shared status so the tab's upload bar shows the file +
                // slot for in-app uploads too (same source the API uploads use)
                val id = AtemUploadStatus.begin(presetName, variant.clip, slot + 1)
                uploadId = id
                val client = AtemClient(atemSettings.host, atemSettings.port)
                withContext(Dispatchers.IO) { client.connect() }
                try {
                    withContext(Dispatchers.IO) {
                        LottieRenderCache.Reader(cached).use { reader ->
                            val rasterW = atemSettings.renderWidth
                            val rasterH = atemSettings.renderHeight
                            if (!variant.clip) {
                                client.uploadStillEncoded(
                                    slot,
                                    reader.nextAtemFrame(rasterW, rasterH),
                                    presetName,
                                ) { p ->
                                    atemProgress = p
                                    AtemUploadStatus.progress(id, p)
                                }
                            } else {
                                client.uploadClipEncoded(
                                    slot, reader.frameCount, presetName,
                                    nextFrame = { reader.nextAtemFrame(rasterW, rasterH) }
                                ) { p -> atemProgress = p; AtemUploadStatus.progress(id, p) }
                                // Wait for the ATEM to finish ingesting the clip (surfaced as the
                                // "processing" phase) so the bar only completes once it's truly ready.
                                AtemUploadStatus.startProcessing(id)
                                atemProgress = 0f
                                client.awaitClipReady(slot, reader.frameCount) { p ->
                                    atemProgress = p
                                    AtemUploadStatus.progress(id, p)
                                }
                            }
                        }
                    }
                } finally {
                    client.disconnect()
                }
                atemReachable = true
                atemProgress = 1f
                AtemUploadStatus.complete(id)
                delay(PREVIEW_SETTLE_MS)
                AtemUploadStatus.clear(id)
                if (closeDialogOnSuccess) showAtemDialog = false
            } catch (e: Exception) {
                atemError = e.message ?: "Upload failed"
                uploadId?.let { AtemUploadStatus.fail(it, e.message) }
            } finally {
                atemProgress = null
                atemBusy = false
            }
        }
    }

    fun startPlaying() {
        val oldJob = animJob
        animJob = null
        isPlaying = true
        animJob = scope.launch {
            oldJob?.cancel()
            oldJob?.join()
            val durMs = totalDurationMs()
            val start = animatedProgress.value
            val segDur = (durMs * (1f - start)).toInt().coerceAtLeast(1)
            animatedProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = segDur, easing = LinearEasing)
            )
            isPlaying = false
        }
    }

    val canPlay = composition != null && jsonContent.isNotBlank()

    val density = LocalDensity.current
    val onSettingsChangeState = rememberUpdatedState(onSettingsChange)
    val isMaximized = isWindowMaximized
    val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout

    var listWidthPx by remember(currentLayout.lowerThirdListWidthDp, isMaximized) {
        mutableStateOf(with(density) { currentLayout.lowerThirdListWidthDp.dp.toPx() })
    }
    val listWidthDp = with(density) { listWidthPx.toDp() }

    @Composable
    fun Tooltip(text: String, content: @Composable () -> Unit) {
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 4.dp),
            ),
            content = content
        )
    }


    // ATEM upload dialog
    if (showAtemDialog) {
        // Pre-upload capacity check: an over-capacity clip upload is guaranteed to fail,
        // so block it up front instead of minutes into the transfer
        val atemClipFramesNeeded = if (atemIsClip) atemVariant(atemIsClip).frameCount else 0
        val atemSlotCapacity = atemClipMaxFrames.getOrNull(atemSlot)
        val atemClipTooLong = atemIsClip && atemSlotCapacity != null && atemClipFramesNeeded > atemSlotCapacity
        AlertDialog(
            onDismissRequest = { if (!atemBusy) showAtemDialog = false },
            title = { Text(stringResource(Res.string.atem_send_to_atem)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Mode selection
                    Text(stringResource(Res.string.atem_upload_mode), style = MaterialTheme.typography.labelMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LabeledRadioButton(
                            selected = !atemIsClip,
                            onClick = {
                                atemIsClip = false
                                atemSlot = appSettings.atemSettings.defaultStillSlot
                            },
                            label = stringResource(Res.string.atem_mode_still),
                        )
                        Spacer(Modifier.width(16.dp))
                        LabeledRadioButton(
                            selected = atemIsClip,
                            onClick = {
                                atemIsClip = true
                                atemSlot = appSettings.atemSettings.defaultClipSlot
                            },
                            label = stringResource(Res.string.atem_mode_clip),
                        )
                    }

                    // Warn when the design doesn't match the ATEM frame: aspect mismatches
                    // get centered with side bars, smaller designs get upscaled (soft look)
                    val canvasSize = remember(jsonContent) { LottieRenderCache.lottieCanvasSize(jsonContent) }
                    if (canvasSize != null) {
                        val (cw, ch) = canvasSize
                        val s = appSettings.atemSettings
                        val designAspect = cw.toFloat() / ch
                        val frameAspect = s.renderWidth.toFloat() / s.renderHeight
                        if (kotlin.math.abs(designAspect - frameAspect) > ASPECT_EPSILON) {
                            Text(
                                stringResource(
                                    Res.string.atem_aspect_mismatch,
                                    "${cw}×${ch}", "${s.renderWidth}×${s.renderHeight}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.semantic.warning
                            )
                        }
                        val fitScale = minOf(s.renderWidth.toFloat() / cw, s.renderHeight.toFloat() / ch)
                        if (fitScale > MAX_FIT_SCALE) {
                            Text(
                                stringResource(
                                    Res.string.atem_upscale_notice,
                                    "${cw}×${ch}",
                                    String.format(java.util.Locale.US, "%.1f", fitScale),
                                    "${s.renderWidth}×${s.renderHeight}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.semantic.warning
                            )
                        }
                    }

                    // Slot — dropdown when slots are loaded, text field fallback
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.atem_slot), style = MaterialTheme.typography.labelMedium)
                        when {
                            atemSlotsLoading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(
                                        stringResource(Res.string.atem_loading_slots),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            atemSlots.isNotEmpty() -> {
                                var slotExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = slotExpanded,
                                    onExpandedChange = { slotExpanded = !slotExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = atemSlotLabel(atemSlot, atemSlots),
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(slotExpanded) },
                                        singleLine = true,
                                        modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = slotExpanded,
                                        onDismissRequest = { slotExpanded = false }
                                    ) {
                                        atemSlots.forEach { slot ->
                                            DropdownMenuItem(
                                                text = { Text(atemSlotLabel(slot.index, atemSlots)) },
                                                onClick = { atemSlot = slot.index; slotExpanded = false }
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                // Manual entry fallback — displayed 1-based like ATEM Software Control
                                OutlinedTextField(
                                    value = (atemSlot + 1).toString(),
                                    onValueChange = { typed ->
                                    typed.toIntOrNull()?.let { v -> atemSlot = (v - 1).coerceAtLeast(0) }
                                },
                                    singleLine = true,
                                    modifier = Modifier.width(100.dp)
                                )
                                val slotsErr = atemSlotsError
                                if (slotsErr != null) {
                                    Text(
                                        stringResource(Res.string.atem_slots_error, slotsErr),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // Detected FPS + clip pool capacity (clips only)
                    if (atemIsClip) {
                        val detectedFps = atemDetectedFps
                        val fpsUsed = detectedFps ?: appSettings.atemSettings.clipFps
                        if (detectedFps != null || atemSlotCapacity != null) {
                            val parts = buildList {
                                if (detectedFps != null) add("${formatAtemFps(detectedFps)} fps")
                                if (atemSlotCapacity != null) {
                                    val secs = String.format(java.util.Locale.US, "%.1f", atemSlotCapacity / fpsUsed)
                                    add(
                                        stringResource(
                                            Res.string.atem_clip_capacity_info,
                                            atemClipFramesNeeded, atemSlotCapacity, secs
                                        )
                                    )
                                }
                            }
                            Text(
                                "ATEM: ${parts.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        if (atemClipTooLong) {
                            val maxSecs = String.format(java.util.Locale.US, "%.1f", atemSlotCapacity / fpsUsed)
                            Text(
                                stringResource(
                                    Res.string.atem_clip_too_long,
                                    atemClipFramesNeeded, atemSlot + 1, atemSlotCapacity, maxSecs
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Preparation / upload status
                    val p = atemProgress
                    when {
                        p != null -> {
                            Text(stringResource(Res.string.atem_uploading), style = MaterialTheme.typography.labelSmall)
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        }
                        atemPrepareProgress < 1f -> {
                            Text(stringResource(Res.string.atem_preparing), style = MaterialTheme.typography.labelSmall)
                            LinearProgressIndicator(
                                progress = { atemPrepareProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        else -> {
                            Text(
                                stringResource(Res.string.atem_ready),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.semantic.success
                            )
                        }
                    }
                    val e = atemError
                    if (e != null) {
                        Text(
                            stringResource(Res.string.atem_upload_error, e),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        startAtemUpload(atemVariant(atemIsClip), atemSlot, closeDialogOnSuccess = true)
                    },
                    enabled = !atemBusy && !atemClipTooLong
,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(Res.string.atem_upload))
                }
            },
            dismissButton = {
                TextButton(
                    shape = RoundedCornerShape(6.dp),
                    onClick = { showAtemDialog = false },
                    enabled = !atemBusy
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    Row(modifier = modifier.fillMaxSize()) {
        // Left column — file list (resizable) + generate button
        Column(
            modifier = Modifier
                .width(listWidthDp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val listState = rememberLazyListState()
            val accentColor = MaterialTheme.colorScheme.primary
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 8.dp)
                ) {
                    if (lottieFiles.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(Res.string.lottie_no_presets),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        items(lottieFiles) { file ->
                            val isSelected = selectedFile?.absolutePath == file.absolutePath
                            val confirmTitle = stringResource(Res.string.confirm_delete)
                            val confirmMsg = stringResource(Res.string.confirm_delete_file, file.name)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                                        else Color.Transparent
                                    )
                                    .drawBehind {
                                        if (isSelected) {
                                            drawRect(
                                                color = accentColor,
                                                size = Size(SELECTION_BAR_WIDTH, size.height),
                                            )
                                        }
                                    }
                                    .finalPassClickable { selectedFile = file; isPlaying = false }
                                    .padding(start = 12.dp, end = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = file.nameWithoutExtension,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_close),
                                        contentDescription = stringResource(Res.string.tooltip_remove),
                                        modifier = Modifier.size(14.dp).initialPassClickable {
                                            SwingUtilities.invokeLater {
                                                val result = JOptionPane.showConfirmDialog(
                                                    Window.getWindows().firstOrNull { it.isActive },
                                                    confirmMsg, confirmTitle,
                                                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
                                                )
                                                if (result == JOptionPane.YES_OPTION) {
                                                    file.delete()
                                                    if (selectedFile?.absolutePath == file.absolutePath) {
                                                selectedFile = null
                                            }
                                                    refreshKey++
                                                }
                                            }
                                        },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = {
                    onOpenLottieGen(appSettings.streamingSettings.lowerThirdFolder) {
                        scope.launch { refreshKey++ }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(Res.string.generate_lower_third), style = MaterialTheme.typography.labelMedium)
            }
        }

        HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

        // Drag handle — resize the list
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        listWidthPx = (listWidthPx + delta)
                            .coerceIn(
                                with(density) { 100.dp.toPx() },
                                with(density) { 600.dp.toPx() }
                            )
                    },
                    onDragStopped = {
                        val newWidthDp = with(density) { listWidthPx.toDp().value.toInt() }
                        onSettingsChangeState.value { s ->
                            if (isMaximized) {
                                s.copy(maximizedLayout = s.maximizedLayout.copy(lowerThirdListWidthDp = newWidthDp))
                            }
                            else s.copy(windowedLayout = s.windowedLayout.copy(lowerThirdListWidthDp = newWidthDp))
                        }
                    }
                )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // ── Title bar ─────────────────────────────────────────────
            val comp = composition
            val arMismatch = if (comp != null && comp.width > 0 && comp.height > 0) {
                val screenBounds = presenterScreenBounds()
                val screenAR = screenBounds.width.toFloat() / screenBounds.height.toFloat()
                if (kotlin.math.abs(comp.width / comp.height - screenAR) > 0.05f)
                    stringResource(
                        Res.string.aspect_ratio_mismatch,
                        comp.width.toInt(),
                        comp.height.toInt(),
                        formatAspectRatio(comp.width.toInt(), comp.height.toInt()),
                        screenBounds.width,
                        screenBounds.height,
                        formatAspectRatio(screenBounds.width, screenBounds.height),
                    )
                else null
            } else null
            // One bar: the preset name, then ATEM, then the Play · Add to Schedule · Go Live tail.
            // There is no second controls row — everything it held now sits here, which is the
            // shape the Pictures and Presentation headers use. FlowRow so the ATEM buttons wrap
            // rather than clip on a narrow panel; heightIn because the aspect-ratio warning adds
            // a second line beneath the name.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedFile?.nameWithoutExtension ?: stringResource(Res.string.lottie_select_preset),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight =
                                        if (selectedFile != null) FontWeight.Medium else FontWeight.Normal,
                                ),
                        color = if (selectedFile != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (arMismatch != null) {
                        Text(
                            text = arMismatch,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                }
                }

                // ATEM controls — deliberately LEFT of the primary actions so
                // Play/Pause · Add to Schedule · Go Live keep the canonical rightmost tail.
                if (atemConfigured && atemEverConnected) {
                    val atemButtonColors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    val unreachableTooltip = stringResource(Res.string.atem_unreachable, appSettings.atemSettings.host)
                    val goLiveKey = appSettings.atemSettings.goLiveKey
                    // One string for the tooltip and the button's name, so they cannot drift apart.
                    val goLiveKeyLabel = stringResource(Res.string.atem_golive_key)
                    Tooltip(goLiveKeyLabel) {
                        FilledIconButton(
                            onClick = {
                                onSettingsChangeState.value { s ->
                                    s.copy(atemSettings = s.atemSettings.copy(goLiveKey = !s.atemSettings.goLiveKey))
                                }
                            },
                            modifier = Modifier.size(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (goLiveKey) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (goLiveKey) MaterialTheme.colorScheme.onTertiary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                painterResource(Res.drawable.ic_key),
                                contentDescription = goLiveKeyLabel,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    if (appSettings.atemSettings.quickUpload) {
                        val stillSlot = appSettings.atemSettings.defaultStillSlot
                        val clipSlot = appSettings.atemSettings.defaultClipSlot
                        val quickEnabled = canPlay && !atemBusy && atemReachable
                        val quickClipVariant =
                            if (jsonContent.isNotBlank()) atemVariant(isClip = true, useDetectedFps = false)
                            else null
                        val quickClipCapacity = appSettings.atemSettings.detectedClipMaxFrames.getOrNull(clipSlot)
                        val quickClipTooLong = quickClipVariant != null && quickClipCapacity != null &&
                            quickClipVariant.frameCount > quickClipCapacity

                        val quickStillLabel = if (!atemReachable) unreachableTooltip
                            else stringResource(Res.string.atem_quick_still_tooltip, stillSlot + 1)
                        Tooltip(quickStillLabel) {
                            FilledIconButton(
                            onClick = {
                                startAtemUpload(
                                    atemVariant(isClip = false, useDetectedFps = false),
                                    stillSlot,
                                    closeDialogOnSuccess = false,
                                )
                            },
                                enabled = quickEnabled,
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = atemButtonColors,
                            ) {
                                Icon(
                                    Icons.Filled.Image,
                                    contentDescription = quickStillLabel,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        val quickClipLabel = when {
                            !atemReachable -> unreachableTooltip
                            quickClipTooLong -> {
                                val secs = String.format(
                                    java.util.Locale.US,
                                    "%.1f",
                                    quickClipCapacity / quickClipVariant.fps,
                                )
                                stringResource(
                                    Res.string.atem_clip_too_long,
                                    quickClipVariant.frameCount,
                                    clipSlot + 1,
                                    quickClipCapacity,
                                    secs,
                                )
                            }
                            else -> stringResource(Res.string.atem_quick_clip_tooltip, clipSlot + 1)
                        }
                        Tooltip(quickClipLabel) {
                            FilledIconButton(
                                onClick = {
                                quickClipVariant?.let { startAtemUpload(it, clipSlot, closeDialogOnSuccess = false) }
                            },
                                enabled = quickEnabled && !quickClipTooLong,
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = atemButtonColors,
                            ) {
                                Icon(
                                    Icons.Filled.Movie,
                                    contentDescription = quickClipLabel,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    } else {
                        val sendTooltip =
                            if (atemReachable) stringResource(Res.string.atem_send_to_atem) else unreachableTooltip
                        Tooltip(sendTooltip) {
                            FilledIconButton(
                                onClick = {
                                    atemSlot =
                                        if (atemIsClip) appSettings.atemSettings.defaultClipSlot
                                        else appSettings.atemSettings.defaultStillSlot
                                    atemError = null
                                    atemProgress = null
                                    showAtemDialog = true
                                },
                                enabled = canPlay && !atemBusy && atemReachable,
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = atemButtonColors
                            ) {
                                Icon(
                                    painterResource(Res.drawable.ic_upload),
                                    contentDescription = stringResource(Res.string.atem_send_to_atem),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                // Play / Pause
                Tooltip(stringResource(if (isPlaying) Res.string.pause else Res.string.play)) {
                    FilledIconButton(
                        onClick = {
                            if (canPlay) {
                                if (isPlaying) {
                                    val job = animJob; animJob = null; isPlaying = false; job?.cancel()
                                } else if (animatedProgress.value >= 1f) {
                                    scope.launch { animatedProgress.snapTo(0f); startPlaying() }
                                } else {
                                    startPlaying()
                                }
                            }
                        },
                        enabled = canPlay,
                        modifier = Modifier.size(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            painterResource(if (isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                            contentDescription = stringResource(if (isPlaying) Res.string.pause else Res.string.play),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                    // Add to Schedule
                    AddToScheduleButton(
                        onClick = {
                            val file = selectedFile ?: return@AddToScheduleButton
                            onAddToSchedule(file.nameWithoutExtension, file.nameWithoutExtension, false, 0L)
                        },
                        enabled = selectedFile != null,
                        tooltipText = stringResource(Res.string.add_to_schedule)
                    )

                    // Go Live
                    GoLiveButton(
                        onClick = {
                            val atemSettings = appSettings.atemSettings
                            if (atemSettings.goLiveKey && atemConfigured) {
                                val durationMs = LottieRenderCache.lottieDurationMs(jsonContent) ?: totalDurationMs()
                                val name = selectedFile?.nameWithoutExtension ?: ""
                                val useDsk = atemSettings.useDownstreamKey
                                scope.launch {
                                    val keyError = LowerThirdSequencer.run(
                                        name = name, json = jsonContent, durationMs = durationMs,
                                        pauseAtFrame = false, pauseDurationMs = 0L,
                                        mixEffect = if (useDsk) 0 else atemSettings.keyMixEffect,
                                        keyer = if (useDsk) atemSettings.dskIndex else atemSettings.keyIndex,
                                        atem = atemSettings, useDownstreamKey = useDsk
                                    )
                                    if (keyError != null) atemError = keyError
                                }
                            } else {
                                onGoLive(jsonContent, false, -1f, 0L, selectedFile?.nameWithoutExtension ?: "")
                            }
                        },
                        enabled = canPlay,
                        tooltipText = stringResource(Res.string.go_live)
                    )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── ATEM upload status ────────────────────────────────────
            val upload = remoteUpload
            if (upload != null && upload.error == null) {
                val uploadingMsg = if (upload.processing) stringResource(Res.string.atem_processing, upload.name)
                    else if (upload.clip) stringResource(Res.string.atem_uploading_video, upload.name, upload.slot)
                    else stringResource(Res.string.atem_uploading_image, upload.name, upload.slot)
                Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        uploadingMsg,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    LinearProgressIndicator(progress = { upload.progress }, modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            val err = upload?.error
            if (err != null) {
                Text(
                    stringResource(Res.string.atem_upload_error, err),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // ── Lottie preview ────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .aspectRatio(presenterAspectRatio())
                        .fillMaxSize()
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (canPlay) {
                        Image(
                            painter = rememberLottiePainter(
                                composition = composition,
                                progress = { animatedProgress.value },
                                fontManager = LottieFonts,
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (selectedFile != null && isCompositionLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    } else if (selectedFile != null) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun atemSlotLabel(index: Int, slots: List<AtemMediaSlot>): String {
    // Display 1-based to match ATEM Software Control's numbering (protocol is 0-based)
    val display = index + 1
    val slot = slots.find { it.index == index }
    return when {
        slot == null           -> stringResource(Res.string.atem_slot_unnamed, display)
        slot.name.isNotBlank() -> stringResource(Res.string.atem_slot_named, display, slot.name)
        slot.isUsed            -> stringResource(Res.string.atem_slot_in_use, display)
        else                   -> stringResource(Res.string.atem_slot_empty, display)
    }
}
