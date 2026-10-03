package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.server.registerBrowserSourceFrames
import org.churchpresenter.app.churchpresenter.presenter.liveMerges
import org.churchpresenter.app.churchpresenter.presenter.mergeHostIndex
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key as composeKey
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.ndi_output_numbered
import org.churchpresenter.strings.generated.resources.omt_output_numbered
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.getBrowserSourceOutput
import org.churchpresenter.settings.getNdiOutput
import org.churchpresenter.settings.getOmtOutput
import org.churchpresenter.app.churchpresenter.presenter.BrowserSourceVideoRenderer
import org.churchpresenter.app.churchpresenter.presenter.NdiManager
import org.churchpresenter.app.churchpresenter.presenter.OmtManager
import org.churchpresenter.app.churchpresenter.presenter.OffscreenOutputContext
import org.churchpresenter.app.churchpresenter.presenter.OffscreenOutputKind
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.State
import org.churchpresenter.settings.ResolvedMerge
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.omt.OmtRuntimeStatus

/**
 * The virtual outputs, which open no window and so are never seen by PresenterWindows.kt: Browser
 * Source, NDI and OMT, one renderer each per configured output.
 */
@Composable
internal fun AppRootState.VirtualOutputs(effectiveAppSettings: AppSettings) {
    val browserSourceServerUrlState = companionServer.serverUrl.collectAsState()
    // Profiles merging their outputs into one picture: each merged output follows its first.
    val outputMergesState = rememberUpdatedState(
        remember(appSettings.projectionSettings) { appSettings.projectionSettings.liveMerges() },
    )
    appSettings.projectionSettings.browserSourceOutputs.indices.forEach { i ->
        composeKey(i) {
            BrowserSourceOutput(i, effectiveAppSettings, browserSourceServerUrlState, outputMergesState)
        }
    }
    // NDI outputs. Registered here beside the Browser Source block above and for the same reason:
    // both are virtual outputs that open no window, so PresenterWindows.kt never sees them. The
    // runtime is brought up once, keyed on the configured path so an operator who points the app at
    // a different install does not have to restart it.
    LaunchedEffect(appSettings.projectionSettings.ndiRuntimePath) {
        // Off the composition's dispatcher: bringing the runtime up is a `Native.load` plus an
        // initialize, and doing that inline would stall the first frame of the app on every launch
        // of a machine that has NDI installed.
        withContext(Dispatchers.IO) {
            NdiManager.ensureStarted(appSettings.projectionSettings.ndiRuntimePath)
        }
    }
    val ndiStatus by NdiManager.status.collectAsState()
    appSettings.projectionSettings.ndiOutputs.indices.forEach { i ->
        composeKey(i) {
            NdiOutput(i, effectiveAppSettings, browserSourceServerUrlState, outputMergesState, ndiStatus)
        }
    }
    // OMT outputs, beside NDI's and in the same shape. The library ships with the app, so unlike
    // NDI's runtime it is normally found; the path setting only overrides the bundled copy. Keyed on
    // the path so a library that failed to load is retried when the operator points elsewhere; once
    // one is loaded it stays for the run, and so does the discovery server it was started with.
    LaunchedEffect(appSettings.projectionSettings.omtLibraryPath) {
        withContext(Dispatchers.IO) {
            OmtManager.ensureStarted(
                customPath = appSettings.projectionSettings.omtLibraryPath,
                discoveryServer = appSettings.projectionSettings.omtDiscoveryServer,
            )
        }
    }
    val omtStatus by OmtManager.status.collectAsState()
    appSettings.projectionSettings.omtOutputs.indices.forEach { i ->
        composeKey(i) {
            OmtOutput(i, effectiveAppSettings, browserSourceServerUrlState, outputMergesState, omtStatus)
        }
    }
}

@Composable
private fun AppRootState.BrowserSourceOutput(
    i: Int,
    effectiveAppSettings: AppSettings,
    browserSourceServerUrlState: State<String>,
    outputMergesState: State<Map<String, ResolvedMerge>>,
) {
    val appSettingsState = rememberUpdatedState(effectiveAppSettings)
    val screenAssignmentState = rememberUpdatedState(
        appSettings.projectionSettings.getBrowserSourceOutput(i)
    )
    val effectiveModeState = remember {
        derivedStateOf {
            effectiveOutputMode(
                presenterManager.browserSourceLocks.value,
                // A merged output shows what its picture's first output shows.
                mergeHostIndex(outputMergesState.value, Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, i),
                presenterManager.presentingMode.value,
            )
        }
    }
    val qaDisplayUrlState = rememberUpdatedState(qaDisplayUrl)
    val bsOutput = appSettings.projectionSettings.getBrowserSourceOutput(i)
    val renderer = remember(
        i,
        bsOutput.browserSourceWidth,
        bsOutput.browserSourceHeight,
        bsOutput.browserSourceFps
    ) {
        BrowserSourceVideoRenderer(
            OffscreenOutputContext(
                presenterManager, appSettingsState, screenAssignmentState, effectiveModeState,
                outputIndex = i,
                sttManager = sttManager,
                mediaViewModel = mediaViewModel,
                qaDisplayUrlState = qaDisplayUrlState,
                serverUrlState = browserSourceServerUrlState,
            ),
            width = bsOutput.browserSourceWidth,
            height = bsOutput.browserSourceHeight,
            fps = bsOutput.browserSourceFps,
        )
    }
    LaunchedEffect(renderer) {
        renderer.start(this)
        companionServer.registerBrowserSourceFrames(i, renderer.frames)
    }
    DisposableEffect(renderer) {
        onDispose { renderer.stop() }
    }
}

@Composable
private fun AppRootState.NdiOutput(
    i: Int,
    effectiveAppSettings: AppSettings,
    browserSourceServerUrlState: State<String>,
    outputMergesState: State<Map<String, ResolvedMerge>>,
    ndiStatus: NdiRuntimeStatus,
) {
    val appSettingsState = rememberUpdatedState(effectiveAppSettings)
    val screenAssignmentState = rememberUpdatedState(
        appSettings.projectionSettings.getNdiOutput(i)
    )
    val effectiveModeState = remember {
        derivedStateOf {
            effectiveOutputMode(
                presenterManager.ndiLocks.value,
                // A merged output shows what its picture's first output shows.
                mergeHostIndex(outputMergesState.value, Constants.PREVIEW_OUTPUT_NDI, i),
                presenterManager.presentingMode.value,
            )
        }
    }
    val qaDisplayUrlState = rememberUpdatedState(qaDisplayUrl)
    val ndiOutput = appSettings.projectionSettings.getNdiOutput(i)
    val defaultName = stringResource(Res.string.ndi_output_numbered, i + 1)
    // Keyed on everything a sender is created with, because NDI has no way to change any of
    // them in place: a rename, a resize or a mode change is a new source on the network.
    // Known rough edge — the name is one of those keys and the settings field commits per
    // keystroke, so typing a name recreates the source once per character. Harmless while
    // nothing is receiving, and an operator names an output before a service rather than
    // during one, but it is churn rather than something anyone would design.
    val renderer = remember(
        i,
        ndiStatus,
        ndiOutput.ndiLabelOr(defaultName),
        ndiOutput.ndiWidth,
        ndiOutput.ndiHeight,
        ndiOutput.ndiFps,
        ndiOutput.ndiMode,
    ) {
        NdiManager.createRenderer(
            index = i,
            assignment = ndiOutput,
            context = OffscreenOutputContext(
                presenterManager = presenterManager,
                appSettingsState = appSettingsState,
                screenAssignmentState = screenAssignmentState,
                effectiveModeState = effectiveModeState,
                outputIndex = i,
                kind = OffscreenOutputKind.NDI,
                sttManager = sttManager,
                mediaViewModel = mediaViewModel,
                qaDisplayUrlState = qaDisplayUrlState,
                serverUrlState = browserSourceServerUrlState,
            ),
            screenAssignmentState = screenAssignmentState,
            name = ndiOutput.ndiLabelOr(defaultName),
        )
    }
    LaunchedEffect(renderer) { renderer?.start(this) }
    DisposableEffect(renderer) {
        onDispose {
            renderer?.stop()
            renderer?.let { NdiManager.release(i, it) }
        }
    }
}

@Composable
private fun AppRootState.OmtOutput(
    i: Int,
    effectiveAppSettings: AppSettings,
    browserSourceServerUrlState: State<String>,
    outputMergesState: State<Map<String, ResolvedMerge>>,
    omtStatus: OmtRuntimeStatus,
) {
    val appSettingsState = rememberUpdatedState(effectiveAppSettings)
    val screenAssignmentState = rememberUpdatedState(
        appSettings.projectionSettings.getOmtOutput(i)
    )
    val effectiveModeState = remember {
        derivedStateOf {
            effectiveOutputMode(
                presenterManager.omtLocks.value,
                // A merged output shows what its picture's first output shows.
                mergeHostIndex(outputMergesState.value, Constants.PREVIEW_OUTPUT_OMT, i),
                presenterManager.presentingMode.value,
            )
        }
    }
    val qaDisplayUrlState = rememberUpdatedState(qaDisplayUrl)
    val omtOutput = appSettings.projectionSettings.getOmtOutput(i)
    val defaultName = stringResource(Res.string.omt_output_numbered, i + 1)
    // Keyed on everything a sender is created with, as the NDI block is: OMT cannot change a
    // name, size, rate, mode or quality in place either.
    val renderer = remember(
        i,
        omtStatus,
        omtOutput.omtLabelOr(defaultName),
        omtOutput.omtWidth,
        omtOutput.omtHeight,
        omtOutput.omtFps,
        omtOutput.omtMode,
        omtOutput.omtQuality,
    ) {
        OmtManager.createRenderer(
            index = i,
            assignment = omtOutput,
            context = OffscreenOutputContext(
                presenterManager = presenterManager,
                appSettingsState = appSettingsState,
                screenAssignmentState = screenAssignmentState,
                effectiveModeState = effectiveModeState,
                outputIndex = i,
                kind = OffscreenOutputKind.OMT,
                sttManager = sttManager,
                mediaViewModel = mediaViewModel,
                qaDisplayUrlState = qaDisplayUrlState,
                serverUrlState = browserSourceServerUrlState,
            ),
            screenAssignmentState = screenAssignmentState,
            name = omtOutput.omtLabelOr(defaultName),
        )
    }
    LaunchedEffect(renderer) { renderer?.start(this) }
    DisposableEffect(renderer) {
        onDispose {
            renderer?.stop()
            renderer?.let { OmtManager.release(i, it) }
        }
    }
}

