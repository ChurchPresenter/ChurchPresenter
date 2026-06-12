package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import org.churchpresenter.app.churchpresenter.composables.initialPassClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Warning
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.atem_loading_slots
import churchpresenter.composeapp.generated.resources.atem_slot_empty
import churchpresenter.composeapp.generated.resources.atem_slot_in_use
import churchpresenter.composeapp.generated.resources.atem_slot_named
import churchpresenter.composeapp.generated.resources.atem_slot_unnamed
import churchpresenter.composeapp.generated.resources.atem_mode_clip
import churchpresenter.composeapp.generated.resources.atem_mode_still
import churchpresenter.composeapp.generated.resources.atem_aspect_mismatch
import churchpresenter.composeapp.generated.resources.atem_clip_capacity_info
import churchpresenter.composeapp.generated.resources.atem_clip_too_long
import churchpresenter.composeapp.generated.resources.atem_golive_dsk
import churchpresenter.composeapp.generated.resources.atem_unreachable
import churchpresenter.composeapp.generated.resources.atem_upscale_notice
import churchpresenter.composeapp.generated.resources.atem_preparing
import churchpresenter.composeapp.generated.resources.atem_quick_clip_tooltip
import churchpresenter.composeapp.generated.resources.atem_quick_still_tooltip
import churchpresenter.composeapp.generated.resources.atem_ready
import churchpresenter.composeapp.generated.resources.atem_send_to_atem
import churchpresenter.composeapp.generated.resources.atem_slot
import churchpresenter.composeapp.generated.resources.atem_slots_error
import churchpresenter.composeapp.generated.resources.atem_upload
import churchpresenter.composeapp.generated.resources.atem_upload_error
import churchpresenter.composeapp.generated.resources.atem_upload_mode
import churchpresenter.composeapp.generated.resources.atem_uploading
import org.churchpresenter.app.churchpresenter.server.AtemMediaSlot
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.confirm_delete
import churchpresenter.composeapp.generated.resources.confirm_delete_file
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_cast
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.lottie_no_presets
import churchpresenter.composeapp.generated.resources.lottie_select_preset
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import churchpresenter.composeapp.generated.resources.tooltip_remove
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
import org.churchpresenter.app.churchpresenter.composables.ImageIconButton
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.formatAtemFps
import org.churchpresenter.app.churchpresenter.server.AtemClient
import org.churchpresenter.app.churchpresenter.server.AtemRenderCache
import org.churchpresenter.app.churchpresenter.server.LowerThirdSequencer
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.presenterAspectRatio
import org.churchpresenter.app.churchpresenter.utils.formatAspectRatio
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.generate_lower_third
import churchpresenter.composeapp.generated.resources.aspect_ratio_mismatch
import org.churchpresenter.app.churchpresenter.viewmodel.isLottieFile
import java.awt.Window
import java.io.File
import javax.swing.SwingUtilities

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LowerThirdTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    selectedLowerThirdItem: ScheduleItem.LowerThirdItem? = null,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: (presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) -> Unit = { _, _, _, _ -> },
    onGoLive: (jsonContent: String, pauseAtFrame: Boolean, pauseFrame: Float, pauseDurationMs: Long) -> Unit = { _, _, _, _ -> },
    onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit = { _, _ -> }
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
        lottieFiles.forEach { AtemRenderCache.ensureForFile(it, appSettings.atemSettings) }
    }

    val scope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<Job?>(null) }

    var selectedFile by remember { mutableStateOf<File?>(null) }
    val animatedProgress = remember { Animatable(0f) }
    var isPlaying by remember { mutableStateOf(false) }

    // ATEM upload dialog state
    val atemConfigured = appSettings.atemSettings.host.isNotBlank()
    var atemReachable by remember { mutableStateOf(false) }
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
            atemReachable = AtemClient.isReachable(host, port)
            delay(if (atemReachable) 30_000L else 10_000L)
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

    // Fetch media pool slot info + FPS when dialog opens or mode toggles
    LaunchedEffect(showAtemDialog, atemIsClip) {
        if (!showAtemDialog) return@LaunchedEffect
        atemSlotsLoading = true
        atemSlotsError = null
        try {
            val state = withContext(Dispatchers.IO) {
                AtemClient(appSettings.atemSettings.host, appSettings.atemSettings.port).queryState()
            }
            atemSlots = if (atemIsClip) state.clipSlots else state.stillSlots
            atemDetectedFps = state.fps
            if (state.clipMaxFrames.isNotEmpty()) atemClipMaxFrames = state.clipMaxFrames
            atemReachable = true
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
    LaunchedEffect(selectedLowerThirdItem) {
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
    LaunchedEffect(jsonContent) { if (jsonContent.isNotBlank()) { delay(3000); isCompositionLoading = false } }

    // Reset when file changes
    LaunchedEffect(selectedFile) {
        animJob?.cancel()
        animJob = null
        animatedProgress.snapTo(0f)
        isPlaying = false
    }

    fun totalDurationMs(): Long =
        ((composition?.durationFrames ?: 0f) / (composition?.frameRate ?: 30f) * 1000f)
            .toLong().coerceAtLeast(1L)

    // Cache variant for an ATEM upload. Frame count comes from the lottie JSON itself
    // (same source as background pre-generation) so both hit the same key.
    // Quick upload passes useDetectedFps=false so it always hits the pre-generated cache.
    fun atemVariant(isClip: Boolean, useDetectedFps: Boolean = true): AtemRenderCache.Variant {
        val s = appSettings.atemSettings
        if (!isClip) return AtemRenderCache.Variant(clip = false, width = s.renderWidth, height = s.renderHeight)
        val fps = (if (useDetectedFps) atemDetectedFps else null) ?: s.clipFps
        val frames = AtemRenderCache.clipFrameCount(jsonContent, fps)
            ?: ((totalDurationMs() / 1000.0) * fps).toInt().coerceAtLeast(1)
        return AtemRenderCache.Variant(true, s.renderWidth, s.renderHeight, fps, frames)
    }

    // Kick off (or attach to) cache preparation when the dialog opens or its mode changes,
    // and mirror the render progress into the dialog
    LaunchedEffect(showAtemDialog, atemIsClip, jsonContent, atemDetectedFps) {
        if (!showAtemDialog || jsonContent.isBlank()) return@LaunchedEffect
        val variant = atemVariant(atemIsClip)
        AtemRenderCache.prepare(jsonContent, variant)
        AtemRenderCache.progressFlow(jsonContent, variant).collect { atemPrepareProgress = it }
    }

    /**
     * Render-from-cache + upload, shared by the dialog's Upload button and the
     * quick-upload buttons. [variant] decides still vs clip and the fps/frame count.
     */
    fun startAtemUpload(variant: AtemRenderCache.Variant, slot: Int, closeDialogOnSuccess: Boolean) {
        val presetName = selectedFile?.nameWithoutExtension ?: ""
        val atemSettings = appSettings.atemSettings
        atemBusy = true
        atemError = null
        scope.launch {
            try {
                // Awaits the background render when it isn't done yet;
                // instant when the cache file already exists
                val cached = AtemRenderCache.prepare(jsonContent, variant).await()
                atemProgress = 0f
                val client = AtemClient(atemSettings.host, atemSettings.port)
                withContext(Dispatchers.IO) { client.connect() }
                try {
                    withContext(Dispatchers.IO) {
                        AtemRenderCache.Reader(cached).use { reader ->
                            if (!variant.clip) {
                                client.uploadStillEncoded(slot, reader.nextFrame(), presetName) { p ->
                                    atemProgress = p
                                }
                            } else {
                                client.uploadClipEncoded(
                                    slot, reader.frameCount, presetName,
                                    nextFrame = { reader.nextFrame() }
                                ) { p -> atemProgress = p }
                            }
                        }
                    }
                } finally {
                    client.disconnect()
                }
                atemReachable = true
                atemProgress = 1f
                delay(800)
                if (closeDialogOnSuccess) showAtemDialog = false
            } catch (e: Exception) {
                atemError = e.message ?: "Upload failed"
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
    val windowState = LocalMainWindowState.current
    val isMaximized = windowState?.placement != WindowPlacement.Floating
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
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp)),
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
                        RadioButton(selected = !atemIsClip, onClick = {
                            atemIsClip = false
                            atemSlot = appSettings.atemSettings.defaultStillSlot
                        })
                        Text(stringResource(Res.string.atem_mode_still), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = atemIsClip, onClick = {
                            atemIsClip = true
                            atemSlot = appSettings.atemSettings.defaultClipSlot
                        })
                        Text(stringResource(Res.string.atem_mode_clip), style = MaterialTheme.typography.bodyMedium)
                    }

                    // Warn when the design doesn't match the ATEM frame: aspect mismatches
                    // get centered with side bars, smaller designs get upscaled (soft look)
                    val canvasSize = remember(jsonContent) { AtemRenderCache.lottieCanvasSize(jsonContent) }
                    if (canvasSize != null) {
                        val (cw, ch) = canvasSize
                        val s = appSettings.atemSettings
                        val designAspect = cw.toFloat() / ch
                        val frameAspect = s.renderWidth.toFloat() / s.renderHeight
                        if (kotlin.math.abs(designAspect - frameAspect) > 0.01f) {
                            Text(
                                stringResource(
                                    Res.string.atem_aspect_mismatch,
                                    "${cw}×${ch}", "${s.renderWidth}×${s.renderHeight}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFC107)
                            )
                        }
                        val fitScale = minOf(s.renderWidth.toFloat() / cw, s.renderHeight.toFloat() / ch)
                        if (fitScale > 1.01f) {
                            Text(
                                stringResource(
                                    Res.string.atem_upscale_notice,
                                    "${cw}×${ch}",
                                    String.format(java.util.Locale.US, "%.1f", fitScale),
                                    "${s.renderWidth}×${s.renderHeight}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFC107)
                            )
                        }
                    }

                    // Slot — dropdown when slots are loaded, text field fallback
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.atem_slot), style = MaterialTheme.typography.labelMedium)
                        when {
                            atemSlotsLoading -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(stringResource(Res.string.atem_loading_slots), style = MaterialTheme.typography.bodySmall)
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
                                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
                                    onValueChange = { it.toIntOrNull()?.let { v -> atemSlot = (v - 1).coerceAtLeast(0) } },
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
                        if (atemClipTooLong && atemSlotCapacity != null) {
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
                            LinearProgressIndicator(progress = { atemPrepareProgress }, modifier = Modifier.fillMaxWidth())
                        }
                        else -> {
                            Text(
                                stringResource(Res.string.atem_ready),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50)
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
                ) {
                    Text(stringResource(Res.string.atem_upload))
                }
            },
            dismissButton = {
                TextButton(
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
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
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    items(lottieFiles) { file ->
                        val isSelected = selectedFile?.absolutePath == file.absolutePath
                        val confirmTitle = stringResource(Res.string.confirm_delete)
                        val confirmMsg = stringResource(Res.string.confirm_delete_file, file.name)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = file.nameWithoutExtension,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                                        .initialPassClickable {
                                            selectedFile = file
                                            isPlaying = false
                                        }
                                )
                                Icon(
                                    painter = painterResource(Res.drawable.ic_close),
                                    contentDescription = stringResource(Res.string.tooltip_remove),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .offset(y = 1.dp)
                                        .initialPassClickable {
                                            SwingUtilities.invokeLater {
                                                val result = JOptionPane.showConfirmDialog(
                                                    Window.getWindows().firstOrNull { it.isActive },
                                                    confirmMsg,
                                                    confirmTitle,
                                                    JOptionPane.YES_NO_OPTION,
                                                    JOptionPane.WARNING_MESSAGE
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
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onOpenLottieGen(appSettings.streamingSettings.lowerThirdFolder) {
                        scope.launch { refreshKey++ }
                    }
                },
                modifier = Modifier.width(200.dp).padding(8.dp).align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
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
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val newWidthDp = with(density) { listWidthPx.toDp().value.toInt() }
                            onSettingsChangeState.value { s ->
                                if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(lowerThirdListWidthDp = newWidthDp))
                                else s.copy(windowedLayout = s.windowedLayout.copy(lowerThirdListWidthDp = newWidthDp))
                            }
                        }
                    ) { _, dragAmount ->
                        listWidthPx = (listWidthPx + dragAmount)
                            .coerceIn(
                                with(density) { 100.dp.toPx() },
                                with(density) { 600.dp.toPx() }
                            )
                    }
                }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = selectedFile?.nameWithoutExtension ?: stringResource(Res.string.lottie_select_preset),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedFile != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            // Aspect ratio mismatch warning
            val comp = composition
            if (comp != null && comp.width > 0 && comp.height > 0) {
                val lottieAR = comp.width / comp.height
                val screenBounds = presenterScreenBounds()
                val screenAR = screenBounds.width.toFloat() / screenBounds.height.toFloat()
                if (kotlin.math.abs(lottieAR - screenAR) > 0.05f) {
                    Text(
                        text = stringResource(
                            Res.string.aspect_ratio_mismatch,
                            comp.width.toInt(), comp.height.toInt(), formatAspectRatio(comp.width.toInt(), comp.height.toInt()),
                            screenBounds.width, screenBounds.height, formatAspectRatio(screenBounds.width, screenBounds.height)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Controls row — moved to top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause
                Tooltip(stringResource(if (isPlaying) Res.string.pause else Res.string.play)) {
                    IconButton(
                        onClick = {
                            if (canPlay) {
                                if (isPlaying) {
                                    val job = animJob
                                    animJob = null
                                    isPlaying = false
                                    job?.cancel()
                                } else if (animatedProgress.value >= 1f) {
                                    scope.launch {
                                        animatedProgress.snapTo(0f)
                                        startPlaying()
                                    }
                                } else {
                                    startPlaying()
                                }
                            }
                        },
                        enabled = canPlay,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Add to Schedule
                val hasFile = selectedFile != null
                Tooltip(stringResource(Res.string.add_to_schedule)) {
                    IconButton(
                        onClick = {
                            val file = selectedFile ?: return@IconButton
                            onAddToSchedule(
                                file.nameWithoutExtension,
                                file.nameWithoutExtension,
                                false,
                                0L
                            )
                        },
                        enabled = hasFile,
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

                // Go Live — with the DSK toggle on, routes through the sequencer so the
                // ATEM DSK cuts on, the animation plays, and the DSK cuts off at the end.
                // (Schedule items and the mobile remote keep the direct path.)
                Tooltip(stringResource(Res.string.go_live)) {
                    IconButton(
                        onClick = {
                            val atemSettings = appSettings.atemSettings
                            if (atemSettings.goLiveDsk && atemConfigured) {
                                val durationMs = AtemRenderCache.lottieDurationMs(jsonContent) ?: totalDurationMs()
                                val name = selectedFile?.nameWithoutExtension ?: ""
                                scope.launch {
                                    val dskError = LowerThirdSequencer.run(
                                        name = name,
                                        json = jsonContent,
                                        durationMs = durationMs,
                                        pauseAtFrame = false,
                                        pauseDurationMs = 0L,
                                        keyer = atemSettings.dskIndex,
                                        atem = atemSettings
                                    )
                                    if (dskError != null) atemError = dskError
                                }
                            } else {
                                onGoLive(jsonContent, false, -1f, 0L)
                            }
                        },
                        enabled = canPlay,
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

                // Send to ATEM — only shown when ATEM is configured; greyed out while
                // the device is unreachable (reachability poll above)
                if (atemConfigured) {
                    val atemButtonColors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    val unreachableTooltip = stringResource(Res.string.atem_unreachable, appSettings.atemSettings.host)

                    // Toggle: Go Live drives the ATEM DSK sequence (mirrors the
                    // switch in ATEM settings — same persisted setting)
                    val goLiveDsk = appSettings.atemSettings.goLiveDsk
                    Tooltip(stringResource(Res.string.atem_golive_dsk)) {
                        IconButton(
                            onClick = {
                                onSettingsChangeState.value { s ->
                                    s.copy(atemSettings = s.atemSettings.copy(goLiveDsk = !s.atemSettings.goLiveDsk))
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (goLiveDsk) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (goLiveDsk) MaterialTheme.colorScheme.onTertiary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        ) {
                            Text("DSK", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (appSettings.atemSettings.quickUpload) {
                        // Quick upload: one press → default slot, no dialog
                        val stillSlot = appSettings.atemSettings.defaultStillSlot
                        val clipSlot = appSettings.atemSettings.defaultClipSlot
                        val quickEnabled = canPlay && !atemBusy && atemReachable

                        // Capacity pre-flight for the clip button: block a doomed upload up front
                        val quickClipVariant = if (jsonContent.isNotBlank())
                            atemVariant(isClip = true, useDetectedFps = false) else null
                        val quickClipCapacity = appSettings.atemSettings.detectedClipMaxFrames.getOrNull(clipSlot)
                        val quickClipTooLong = quickClipVariant != null && quickClipCapacity != null &&
                            quickClipVariant.frameCount > quickClipCapacity

                        Tooltip(
                            if (!atemReachable) unreachableTooltip
                            else stringResource(Res.string.atem_quick_still_tooltip, stillSlot + 1)
                        ) {
                            IconButton(
                                onClick = {
                                    startAtemUpload(
                                        atemVariant(isClip = false, useDetectedFps = false),
                                        stillSlot, closeDialogOnSuccess = false
                                    )
                                },
                                enabled = quickEnabled,
                                colors = atemButtonColors
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                        Tooltip(
                            when {
                                !atemReachable -> unreachableTooltip
                                quickClipTooLong && quickClipVariant != null && quickClipCapacity != null -> {
                                    val secs = String.format(java.util.Locale.US, "%.1f", quickClipCapacity / quickClipVariant.fps)
                                    stringResource(
                                        Res.string.atem_clip_too_long,
                                        quickClipVariant.frameCount, clipSlot + 1, quickClipCapacity, secs
                                    )
                                }
                                else -> stringResource(Res.string.atem_quick_clip_tooltip, clipSlot + 1)
                            }
                        ) {
                            IconButton(
                                onClick = {
                                    quickClipVariant?.let {
                                        startAtemUpload(it, clipSlot, closeDialogOnSuccess = false)
                                    }
                                },
                                enabled = quickEnabled && !quickClipTooLong,
                                colors = atemButtonColors
                            ) {
                                Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        val atemTooltip = if (atemReachable) stringResource(Res.string.atem_send_to_atem)
                            else unreachableTooltip
                        Tooltip(atemTooltip) {
                            IconButton(
                                onClick = {
                                    atemSlot = if (atemIsClip) appSettings.atemSettings.defaultClipSlot
                                               else appSettings.atemSettings.defaultStillSlot
                                    atemError = null
                                    atemProgress = null
                                    showAtemDialog = true
                                },
                                enabled = canPlay && !atemBusy && atemReachable,
                                colors = atemButtonColors
                            ) {
                                Text("A", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // ATEM upload progress bar (shown while uploading)
            val progress = atemProgress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val err = atemError
            if (err != null) {
                Text(
                    stringResource(Res.string.atem_upload_error, err),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Lottie preview — weight(1f) fills remaining space, aspectRatio inside
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(presenterAspectRatio())
                        .fillMaxSize()
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (canPlay) {
                        Image(
                            painter = rememberLottiePainter(composition = composition, progress = { animatedProgress.value }),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (selectedFile != null && isCompositionLoading) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    } else if (selectedFile != null) {
                        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
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
