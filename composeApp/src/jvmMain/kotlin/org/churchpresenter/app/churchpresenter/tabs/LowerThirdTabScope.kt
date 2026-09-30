package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import churchpresenter.composeapp.generated.resources.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.atem.AtemClient
import org.churchpresenter.app.churchpresenter.server.LottieRenderCache
import org.churchpresenter.atem.AtemUploadStatus
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import io.github.alexzhirkevich.compottie.LottieComposition

/** The state the Lower Third tab remembers for as long as it is composed. */
@Stable
internal class LowerThirdUiState(detectedClipMaxFrames: List<Int>) {
    var refreshKey by mutableStateOf(0)
    var animJob by mutableStateOf<Job?>(null)
    var selectedFile by mutableStateOf<File?>(null)
    val animatedProgress = Animatable(0f)
    var isPlaying by mutableStateOf(false)
    var atemReachable by mutableStateOf(false)
    var showAtemDialog by mutableStateOf(false)
    var atemIsClip by mutableStateOf(false)
    var atemSlot by mutableStateOf(0)
    /** Upload click in progress. */
    var atemBusy by mutableStateOf(false)
    /** Cache render progress; 1f is ready. */
    var atemPrepareProgress by mutableStateOf(1f)
    /** Upload progress; null is idle. */
    var atemProgress by mutableStateOf<Float?>(null)
    var atemError by mutableStateOf<String?>(null)
    var atemSlots by mutableStateOf<List<AtemMediaSlot>>(emptyList())
    var atemClipMaxFrames by mutableStateOf(detectedClipMaxFrames)
    var atemSlotsLoading by mutableStateOf(false)
    var atemSlotsError by mutableStateOf<String?>(null)
    var atemDetectedFps by mutableStateOf<Double?>(null)
    var resolvedSelection by mutableStateOf<Pair<String, Int>?>(null)
}

/**
 * Everything the Lower Third tab's pieces read, for one composition: its parameters, the state it
 * remembers, and the playback and ATEM upload actions.
 */
@Suppress("LongParameterList")
internal class LowerThirdTabScope(
    val appSettings: AppSettings,
    val selectedLowerThirdItem: ScheduleItem.LowerThirdItem?,
    val selectedLowerThirdItemVersion: Int,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    val onAddToSchedule: (
        presetId: String,
        presetLabel: String,
        pauseAtFrame: Boolean,
        pauseDurationMs: Long,
    ) -> Unit,
    val onGoLive: (
        jsonContent: String,
        pauseAtFrame: Boolean,
        pauseFrame: Float,
        pauseDurationMs: Long,
        presetName: String,
    ) -> Unit,
    val onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit,
    val queryAtemState: suspend (host: String, port: Int) -> AtemState,
    val probeAtemReachable: suspend (host: String, port: Int) -> Boolean,
    val ui: LowerThirdUiState,
    val lottieFolder: String,
    lottieFilesOrNullState: State<List<File>?>,
    val scope: CoroutineScope,
    atemEverConnectedState: MutableState<Boolean>,
    remoteUploadState: State<AtemUploadStatus.Status?>,
    val jsonContent: String,
    compositionState: State<LottieComposition?>,
    isCompositionLoadingState: MutableState<Boolean>,
    val density: Density,
    val onSettingsChangeState: State<((AppSettings) -> AppSettings) -> Unit>,
    val isMaximized: Boolean,
    listWidthPxState: MutableState<Float>,
) {
    val lottieFilesOrNull by lottieFilesOrNullState
    val lottieFiles get() = lottieFilesOrNull.orEmpty()
    var atemEverConnected by atemEverConnectedState
    val remoteUpload by remoteUploadState
    val composition by compositionState

    /** True while the composition is loading, so the warning triangle does not flash during the async load. */
    var isCompositionLoading by isCompositionLoadingState
    var listWidthPx by listWidthPxState
    val listWidthDp get() = with(density) { listWidthPx.toDp() }
    val atemConfigured get() = appSettings.atemSettings.host.isNotBlank()
    val canPlay get() = composition != null && jsonContent.isNotBlank()
    val animatedProgress get() = ui.animatedProgress
    var refreshKey by ui::refreshKey
    var animJob by ui::animJob
    var selectedFile by ui::selectedFile
    var isPlaying by ui::isPlaying
    var atemReachable by ui::atemReachable
    var showAtemDialog by ui::showAtemDialog
    var atemIsClip by ui::atemIsClip
    var atemSlot by ui::atemSlot
    var atemBusy by ui::atemBusy
    var atemPrepareProgress by ui::atemPrepareProgress
    var atemProgress by ui::atemProgress
    var atemError by ui::atemError
    var atemSlots by ui::atemSlots
    var atemClipMaxFrames by ui::atemClipMaxFrames
    var atemSlotsLoading by ui::atemSlotsLoading
    var atemSlotsError by ui::atemSlotsError
    var atemDetectedFps by ui::atemDetectedFps
    var resolvedSelection by ui::resolvedSelection

    fun totalDurationMs(): Long =
        (
            (composition?.durationFrames ?: 0f) /
                (composition?.frameRate ?: LOWER_THIRD_DEFAULT_FRAME_RATE) *
                LOWER_THIRD_MILLIS_PER_SECOND_F
            )
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
                val cached = LottieRenderCache.prepare(jsonContent, variant).await()
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
                delay(LOWER_THIRD_PREVIEW_SETTLE_MS)
                AtemUploadStatus.clear(id)
                if (closeDialogOnSuccess) showAtemDialog = false
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
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

    @OptIn(ExperimentalFoundationApi::class)
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
}

/** The ATEM reachability poll, the remote-upload error dismissal and the media-pool fetch. */
@Composable
internal fun LowerThirdTabScope.LowerThirdAtemEffects() {
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
            delay(if (reachable) LOWER_THIRD_ATEM_REACHABLE_POLL_MS else LOWER_THIRD_ATEM_UNREACHABLE_POLL_MS)
        }
    }
    // Auto-dismiss a remote upload error after a while (success self-clears server-side)
    LaunchedEffect(remoteUpload?.error) {
        val errored = remoteUpload
        if (errored?.error != null) { delay(LOWER_THIRD_UPLOAD_ERROR_DISPLAY_MS); AtemUploadStatus.clear(errored.id) }
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
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            atemSlotsError = e.message
            atemSlots = emptyList()
            atemReachable = false
        } finally {
            atemSlotsLoading = false
        }
    }
}

/** Resolves a scheduled lower third to its file, tracks the composition load, and prepares the ATEM cache. */
@Composable
internal fun LowerThirdTabScope.LowerThirdSelectionEffects() {
    // When a schedule item is clicked, find the matching file by name.
    //
    // Keyed on the file list as well as the item, because the list arrives *after* the first
    // composition -- it is read off the UI thread. Without that key this ran once against an empty
    // list and never again, so an operator clicking a queued lower third got whatever was already
    // selected and went live with the wrong graphic.
    //
    // Resolved at most once per item, which is what the key alone would not give: the folder is
    // watched and rescanned whenever a file is added or removed, and re-running then would drag the
    // selection back onto the scheduled item after the operator had clicked something else. Clicking
    // the same schedule row again bumps `selectedLowerThirdItemVersion`, which is a new key and
    // deliberately does resolve again.
    LaunchedEffect(selectedLowerThirdItem, selectedLowerThirdItemVersion, lottieFiles) {
        val item = selectedLowerThirdItem ?: return@LaunchedEffect
        val resolveKey = item.id to selectedLowerThirdItemVersion
        if (resolvedSelection == resolveKey) return@LaunchedEffect
        val file = lottieFiles.find { it.nameWithoutExtension == item.presetLabel || it.name == item.presetLabel }
            ?: lottieFiles.find { it.nameWithoutExtension == item.presetId }
            // Not resolvable yet, or not at all -- either way leave the selection alone and let the
            // next list arrival try again.
            ?: return@LaunchedEffect
        resolvedSelection = resolveKey
        selectedFile = file
        animJob?.cancel()
        animJob = null
        animatedProgress.snapTo(0f)
        isPlaying = false
    }

    LaunchedEffect(composition) { if (composition != null) isCompositionLoading = false }
    LaunchedEffect(jsonContent) { if (jsonContent.isNotBlank()) {
        delay(LOWER_THIRD_COMPOSITION_LOAD_SETTLE_MS)
        isCompositionLoading = false
    } }

    // Reset when file changes
    LaunchedEffect(selectedFile) {
        animJob?.cancel()
        animJob = null
        animatedProgress.snapTo(0f)
        isPlaying = false
    }

    // Kick off (or attach to) cache preparation when the dialog opens or its mode changes,
    // and mirror the render progress into the dialog
    LaunchedEffect(showAtemDialog, atemIsClip, jsonContent, atemDetectedFps) {
        if (!showAtemDialog || jsonContent.isBlank()) return@LaunchedEffect
        val variant = atemVariant(atemIsClip)
        LottieRenderCache.prepare(jsonContent, variant)
        LottieRenderCache.progressFlow(jsonContent, variant).collect { atemPrepareProgress = it }
    }
}
