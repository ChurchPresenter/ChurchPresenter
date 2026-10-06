package org.churchpresenter.lowerthird

import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.sharedui.utils.FallbackOutputSize
import org.churchpresenter.sharedui.utils.PreviewOutput
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
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.atem_upload_failed
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.lottiegen.lottie.LottieTextShaping
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.atem.AtemClient
import org.churchpresenter.lowerthird.render.LottieRenderCache
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
/** The schedule item the tab was asked to show, and the count bumped on every click of one. */
internal class LowerThirdSelection(val item: ScheduleItem.LowerThirdItem?, val version: Int)

/** What the tab's own buttons hand back to the app. */
internal class LowerThirdActions(
    val onAddToSchedule: (presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) -> Unit,
    val onGoLive: (
        jsonContent: String,
        pauseAtFrame: Boolean,
        pauseFrame: Float,
        pauseDurationMs: Long,
        presetName: String,
    ) -> Unit,
    val onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit,
    val confirmRemove: (message: String, title: String, onConfirmed: () -> Unit) -> Unit,
)

/** How the tab reaches the ATEM: its media-pool state, and whether it answers at all. */
internal class LowerThirdAtemAccess(
    val queryAtemState: suspend (host: String, port: Int) -> AtemState,
    val probeAtemReachable: suspend (host: String, port: Int) -> Boolean,
)

/** 1920x1080 and shown everywhere: what the preview stands for when the app has said nothing. */
internal val FallbackLowerThirdPreview = PreviewOutput(
    key = "",
    label = "",
    size = FallbackOutputSize,
    showsMode = true,
    assignment = ScreenAssignment(),
)

/** The output the preview stands for, and the app's picker for it. */
internal class LowerThirdPreview(val output: PreviewOutput, val picker: @Composable (Modifier) -> Unit)

/** What the app hands the tab. */
internal class LowerThirdTabInputs(
    val appSettings: AppSettings,
    val selection: LowerThirdSelection,
    val actions: LowerThirdActions,
    val atem: LowerThirdAtemAccess,
    val preview: LowerThirdPreview,
)

/** The animation open in the tab: its JSON, its parsed composition, and whether that is still loading. */
internal class LowerThirdAnimation(
    val jsonContent: String,
    val compositionState: State<LottieComposition?>,
    val isCompositionLoadingState: MutableState<Boolean>,
)

/** What the tab's composition holds: its UI state, the folder and its files, the ATEM flags, the animation. */
internal class LowerThirdTabState(
    val ui: LowerThirdUiState,
    val lottieFolder: String,
    val lottieFilesOrNullState: State<List<File>?>,
    val atemEverConnectedState: MutableState<Boolean>,
    val remoteUploadState: State<AtemUploadStatus.Status?>,
    val animation: LowerThirdAnimation,
)

/** The window the tab is drawn in: where its work runs, its density, the layout it saves to, the list width. */
internal class LowerThirdWindow(
    val scope: CoroutineScope,
    val density: Density,
    val onSettingsChangeState: State<((AppSettings) -> AppSettings) -> Unit>,
    val isMaximized: Boolean,
    val listWidthPxState: MutableState<Float>,
)

/**
 * Everything the tab's pieces read and act on. Stable: what it exposes is snapshot state or an input
 * it is rebuilt for (the tab remembers it keyed on all of them), so a piece handed the same scope can
 * skip.
 */
@Stable
internal class LowerThirdTabScope(inputs: LowerThirdTabInputs, state: LowerThirdTabState, window: LowerThirdWindow) {
    val appSettings = inputs.appSettings
    val selectedLowerThirdItem = inputs.selection.item
    val selectedLowerThirdItemVersion = inputs.selection.version
    val onAddToSchedule = inputs.actions.onAddToSchedule
    val onGoLive = inputs.actions.onGoLive
    val onOpenLottieGen = inputs.actions.onOpenLottieGen
    val confirmRemove = inputs.actions.confirmRemove
    val queryAtemState = inputs.atem.queryAtemState
    val probeAtemReachable = inputs.atem.probeAtemReachable
    val previewOutput = inputs.preview.output
    val outputPicker = inputs.preview.picker
    private val ui = state.ui
    val lottieFolder = state.lottieFolder
    private val lottieFilesOrNullState = state.lottieFilesOrNullState
    private val atemEverConnectedState = state.atemEverConnectedState
    private val remoteUploadState = state.remoteUploadState
    val jsonContent = state.animation.jsonContent
    private val compositionState = state.animation.compositionState
    private val isCompositionLoadingState = state.animation.isCompositionLoadingState
    val scope = window.scope
    val density = window.density
    val onSettingsChangeState = window.onSettingsChangeState
    val isMaximized = window.isMaximized
    private val listWidthPxState = window.listWidthPxState

    val lottieFilesOrNull get() = lottieFilesOrNullState.value
    val lottieFiles get() = lottieFilesOrNull.orEmpty()
    var atemEverConnected
        get() = atemEverConnectedState.value
        set(value) {
            atemEverConnectedState.value = value
        }
    val remoteUpload get() = remoteUploadState.value
    val composition get() = compositionState.value

    /** True while the composition is loading, so the warning triangle does not flash during the async load. */
    var isCompositionLoading
        get() = isCompositionLoadingState.value
        set(value) {
            isCompositionLoadingState.value = value
        }
    var listWidthPx
        get() = listWidthPxState.value
        set(value) {
            listWidthPxState.value = value
        }
    val listWidthDp get() = with(density) { listWidthPx.toDp() }
    val atemConfigured get() = appSettings.atemSettings.host.isNotBlank()
    val canPlay get() = composition != null && jsonContent.isNotBlank()

    /** Whether the file's text is drawn as whole lines, as its generator's Text shaping asked. */
    val groupsText by lazy { LottieTextShaping.groupsText(jsonContent) }
    val animatedProgress get() = ui.animatedProgress
    var refreshKey
        get() = ui.refreshKey
        set(value) {
            ui.refreshKey = value
        }
    var animJob
        get() = ui.animJob
        set(value) {
            ui.animJob = value
        }
    var selectedFile
        get() = ui.selectedFile
        set(value) {
            ui.selectedFile = value
        }
    var isPlaying
        get() = ui.isPlaying
        set(value) {
            ui.isPlaying = value
        }
    var atemReachable
        get() = ui.atemReachable
        set(value) {
            ui.atemReachable = value
        }
    var showAtemDialog
        get() = ui.showAtemDialog
        set(value) {
            ui.showAtemDialog = value
        }
    var atemIsClip
        get() = ui.atemIsClip
        set(value) {
            ui.atemIsClip = value
        }
    var atemSlot
        get() = ui.atemSlot
        set(value) {
            ui.atemSlot = value
        }
    var atemBusy
        get() = ui.atemBusy
        set(value) {
            ui.atemBusy = value
        }
    var atemPrepareProgress
        get() = ui.atemPrepareProgress
        set(value) {
            ui.atemPrepareProgress = value
        }
    var atemProgress
        get() = ui.atemProgress
        set(value) {
            ui.atemProgress = value
        }
    var atemError
        get() = ui.atemError
        set(value) {
            ui.atemError = value
        }
    var atemSlots
        get() = ui.atemSlots
        set(value) {
            ui.atemSlots = value
        }
    var atemClipMaxFrames
        get() = ui.atemClipMaxFrames
        set(value) {
            ui.atemClipMaxFrames = value
        }
    var atemSlotsLoading
        get() = ui.atemSlotsLoading
        set(value) {
            ui.atemSlotsLoading = value
        }
    var atemSlotsError
        get() = ui.atemSlotsError
        set(value) {
            ui.atemSlotsError = value
        }
    var atemDetectedFps
        get() = ui.atemDetectedFps
        set(value) {
            ui.atemDetectedFps = value
        }
    var resolvedSelection
        get() = ui.resolvedSelection
        set(value) {
            ui.resolvedSelection = value
        }

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
                atemError = e.message ?: getString(Res.string.atem_upload_failed)
                uploadId?.let { AtemUploadStatus.fail(it, e.message) }
            } finally {
                atemProgress = null
                atemBusy = false
            }
        }
    }

    /** Deletes a preset's file, lets go of it if it was the one open, and rescans the folder. */
    fun removePreset(file: File) {
        file.delete()
        if (selectedFile?.absolutePath == file.absolutePath) selectedFile = null
        refreshKey++
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
