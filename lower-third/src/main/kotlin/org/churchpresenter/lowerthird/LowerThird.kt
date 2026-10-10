@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.churchpresenter.lowerthird

import org.churchpresenter.sharedui.composables.goLiveKeyTarget
import java.awt.Window
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import org.churchpresenter.sharedui.utils.PreviewOutput
import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.churchpresenter.atem.AtemState
import androidx.compose.runtime.produceState
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.atem.AtemClient
import org.churchpresenter.lowerthird.render.LottieRenderCache
import org.churchpresenter.atem.AtemUploadStatus
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.lowerthird.render.isLottieFile
import java.io.File
import androidx.compose.runtime.State

internal const val LOWER_THIRD_ATEM_REACHABLE_POLL_MS = 30_000L
internal const val LOWER_THIRD_ATEM_UNREACHABLE_POLL_MS = 10_000L
internal const val LOWER_THIRD_UPLOAD_ERROR_DISPLAY_MS = 8000L
internal const val LOWER_THIRD_COMPOSITION_LOAD_SETTLE_MS = 3000L
internal const val LOWER_THIRD_DEFAULT_FRAME_RATE = 30f
internal const val LOWER_THIRD_MILLIS_PER_SECOND_F = 1000f
internal const val LOWER_THIRD_PREVIEW_SETTLE_MS = 800L
internal const val LOWER_THIRD_ASPECT_EPSILON = 0.01f
internal const val LOWER_THIRD_MAX_FIT_SCALE = 1.01f
internal val LOWER_THIRD_LIST_ROW_HEIGHT = 32.dp

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LowerThirdTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
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
    /** The preset on air, by name, or null when none is; the Go Live key does not play it again. */
    liveLowerThirdName: String? = null,
    /**
     * Asks whether to delete a preset, calling `onConfirmed` if the answer is yes. A Swing dialog
     * by default; a test answers it itself, since a real one cannot open headless.
     */
    confirmRemove: (message: String, title: String, onConfirmed: () -> Unit) -> Unit = ::confirmWithSwingDialog,
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
    /** The output the preview stands for; 1920x1080 when the app has not said. */
    previewOutput: PreviewOutput = FallbackLowerThirdPreview,
    /** The app's picker for which output the preview stands for; drawn above the preview. */
    outputPicker: @Composable (Modifier) -> Unit = {},
    /**
     * Starts the background render of one preset's cache entries. Defaults to the process-wide
     * [LottieRenderCache], whose jobs outlive this composition by design so the cache still gets
     * warm; a test passes its own, or every preset it shows keeps rendering through later tests.
     */
    preRender: (File, AtemSettings) -> Unit = { file, atem -> LottieRenderCache.ensureForFile(file, atem) },
) {
    val lottieFolder = appSettings.streamingSettings.lowerThirdFolder
    val ui = remember { LowerThirdUiState(appSettings.atemSettings.detectedClipMaxFrames) }
    WatchLowerThirdFolder(lottieFolder, ui)
    val lottieFilesOrNullState = produceLowerThirdFiles(lottieFolder, ui.refreshKey)
    val lottieFiles = lottieFilesOrNullState.value.orEmpty()

    // Pre-render ATEM uploads in the background for every lottie file as soon as it
    // appears (generator save, file drop, edit) — Send to ATEM then streams a ready file
    LaunchedEffect(lottieFiles, appSettings.atemSettings) {
        lottieFiles.forEach { preRender(it, appSettings.atemSettings) }
    }
    val scope = rememberCoroutineScope()
    // Sticky: true once the current host/port has responded at least once. Gates whether the ATEM
    // controls are shown at all — they appear only after a real response and stay shown if the
    // device later drops; reset (hidden again) only when the IP/port changes.
    val atemEverConnectedState = remember(appSettings.atemSettings.host, appSettings.atemSettings.port) {
        mutableStateOf(false)
    }
    // Status of an API/Companion-triggered upload, so the same bar reflects those too
    val remoteUploadState = AtemUploadStatus.state.collectAsState()

    val jsonContent = remember(ui.selectedFile) {
        val f = ui.selectedFile ?: return@remember ""
        if (!f.exists()) return@remember ""
        f.readText()
    }

    val compositionState = rememberLottieComposition(jsonContent) {
        LottieCompositionSpec.JsonString(jsonContent.ifBlank { "{}" })
    }

    // True while composition is loading — prevents flashing warning triangle during async load
    val isCompositionLoadingState = remember(jsonContent) { mutableStateOf(jsonContent.isNotBlank()) }

    val density = LocalDensity.current
    val onSettingsChangeState = rememberUpdatedState(onSettingsChange)
    val windowState = LocalMainWindowState.current
    val isMaximized = windowState?.placement != WindowPlacement.Floating
    val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout

    val listWidthPxState = remember(currentLayout.lowerThirdListWidthDp, isMaximized) {
        mutableStateOf(with(density) { currentLayout.lowerThirdListWidthDp.dp.toPx() })
    }

    // Remembered, keyed on everything it holds: a new scope on every recomposition would hand the
    // pieces new lambdas each time, and a click handler keyed on its lambda would restart.
    val tabScope = remember(
        appSettings, selectedLowerThirdItem, selectedLowerThirdItemVersion, onSettingsChange, onAddToSchedule, onGoLive,
        onOpenLottieGen, queryAtemState, probeAtemReachable, ui, lottieFolder, lottieFilesOrNullState, scope,
        atemEverConnectedState, remoteUploadState, jsonContent, compositionState, isCompositionLoadingState, density,
        onSettingsChangeState, isMaximized, listWidthPxState, previewOutput, outputPicker, confirmRemove,
    ) {
        LowerThirdTabScope(
            LowerThirdTabInputs(
                appSettings = appSettings,
                selection = LowerThirdSelection(selectedLowerThirdItem, selectedLowerThirdItemVersion),
                actions = LowerThirdActions(onAddToSchedule, onGoLive, onOpenLottieGen, confirmRemove),
                atem = LowerThirdAtemAccess(queryAtemState, probeAtemReachable),
                preview = LowerThirdPreview(previewOutput, outputPicker),
            ),
            LowerThirdTabState(
                ui = ui,
                lottieFolder = lottieFolder,
                lottieFilesOrNullState = lottieFilesOrNullState,
                atemEverConnectedState = atemEverConnectedState,
                remoteUploadState = remoteUploadState,
                animation = LowerThirdAnimation(jsonContent, compositionState, isCompositionLoadingState),
            ),
            LowerThirdWindow(scope, density, onSettingsChangeState, isMaximized, listWidthPxState),
        )
    }
    tabScope.LowerThirdAtemEffects()
    tabScope.LowerThirdSelectionEffects()
    tabScope.LowerThirdAtemDialog()
    val canGoLive = tabScope.canPlay && tabScope.selectedFile?.nameWithoutExtension != liveLowerThirdName
    tabScope.LowerThirdBody(modifier.goLiveKeyTarget(enabled = canGoLive, onGoLive = tabScope::goLive))
}

/** Watches the lower-third folder and bumps [LowerThirdUiState.refreshKey] when a `.json` in it changes. */
@Composable
private fun WatchLowerThirdFolder(lottieFolder: String, ui: LowerThirdUiState) {
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
                        // Interruptible: a plain take() ignores cancellation, so leaving the tab
                        // would hold this thread and the watch open until the folder next changed.
                        val key = runInterruptible { watchService.take() }
                        if (touchesJson(key.pollEvents())) {
                            withContext(Dispatchers.Main) { ui.refreshKey++ }
                        }
                        if (!key.reset()) break
                    }
                } finally {
                    watchService.close()
                }
            } catch (_: java.nio.file.ClosedWatchServiceException) {} catch (_: InterruptedException) {}
        }
    }
}

@Composable
private fun produceLowerThirdFiles(lottieFolder: String, refreshKey: Int): State<List<File>?> {
    //
    // Off the composition thread, and null while it is still reading: deciding whether a .json is a
    // Lottie means reading the whole of it, so this is the folder's full weight in bytes. It used to
    // be a plain `remember`, which did all of that inline on every folder change. Null is also what
    // makes "still looking" tellable from "nothing there" in the list below -- "no lower thirds" is
    // a verdict about the folder, and until the read finishes it would be an unearned one.
    return produceState(null, lottieFolder, refreshKey) {
        value = null
        value = withContext(Dispatchers.IO) {
            if (lottieFolder.isEmpty()) {
                emptyList()
            } else {
                File(lottieFolder).takeIf { it.exists() && it.isDirectory }
                    ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
                    ?.sortedBy { it.nameWithoutExtension.lowercase() } ?: emptyList()
            }
        }
    }
}

/** The preview box, which has to fit the panel whatever the output's shape. */
internal const val LOWER_THIRD_PREVIEW_TAG = "lower_third_preview"

/** The confirmation a preset's delete button asks, as a Swing yes/no dialog over the active window. */
internal fun confirmWithSwingDialog(message: String, title: String, onConfirmed: () -> Unit) {
    SwingUtilities.invokeLater {
        val result = JOptionPane.showConfirmDialog(
            Window.getWindows().firstOrNull { it.isActive },
            message, title,
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        )
        if (result == JOptionPane.YES_OPTION) onConfirmed()
    }
}

/** Whether a batch of folder events touched a `.json` file; an overflow names no file and does not count. */
internal fun touchesJson(events: List<WatchEvent<*>>): Boolean = events.any { event ->
    event.kind() != StandardWatchEventKinds.OVERFLOW && event.context().toString().lowercase().endsWith(".json")
}
