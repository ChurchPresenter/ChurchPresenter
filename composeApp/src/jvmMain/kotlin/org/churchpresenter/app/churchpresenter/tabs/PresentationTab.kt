package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.PresentationSlidesLoaded
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Surface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res as AppRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.clear
import churchpresenter.composeapp.generated.resources.ic_close
import org.churchpresenter.strings.generated.resources.select_presentation_file
import org.churchpresenter.strings.generated.resources.media_vlc_required
import org.churchpresenter.strings.generated.resources.slide_number
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import org.churchpresenter.app.churchpresenter.LocalWentLive
import org.churchpresenter.app.churchpresenter.composables.HIDDEN_TILE_ALPHA
import org.churchpresenter.app.churchpresenter.composables.HiddenBadge
import org.churchpresenter.app.churchpresenter.composables.SlideshowHideToggle
import org.churchpresenter.app.churchpresenter.composables.focusRescuePressHook
import org.churchpresenter.app.churchpresenter.composables.rememberFocusLostRescue
import org.churchpresenter.app.churchpresenter.composables.isVlcArchMismatch
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.isVlcLoadFailed
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.PresentationRemoteDialog
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Window as AwtWindow
import java.io.File
import org.churchpresenter.app.churchpresenter.server.TunnelStatus

internal const val PRESENTATION_MILLIS_PER_SECOND = 1000
internal const val PRESENTATION_MAX_AUTO_SCROLL_SECONDS = 30
internal const val PRESENTATION_MIN_TRANSITION_MS = 100
internal const val PRESENTATION_MAX_TRANSITION_MS = 2000

/**
 * Whether [event] means "go back a slide" in this tab.
 *
 * The tab's own previous binding *or* the global clicker binding: a clicker sends Page Up, which
 * `MainDesktop` also claims while a presentation is live, but only then — with the tab focused and
 * nothing presenting, this handler is the one that has to answer it.
 */
internal fun goesBack(shortcuts: ShortcutMap, event: KeyEvent): Boolean =
    shortcuts.matches(ShortcutAction.PRESENTATION_PREVIOUS, event) ||
        shortcuts.matches(ShortcutAction.CLICKER_PREVIOUS, event)

/** The forward counterpart of [goesBack]. */
internal fun goesForward(shortcuts: ShortcutMap, event: KeyEvent): Boolean =
    shortcuts.matches(ShortcutAction.PRESENTATION_NEXT, event) ||
        shortcuts.matches(ShortcutAction.CLICKER_NEXT, event)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PresentationTab(
    modifier: Modifier = Modifier,
    /** The hosting AWT window — the focus-lost rescue banner uses it to force window focus
     *  back when AWT's focus tracking wedges (window active per macOS, but windowGainedFocus
     *  never delivered). */
    hostWindow: AwtWindow? = null,
    appSettings: AppSettings,
    onAddToSchedule: ((filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit)? = null,
    /** Save preset, to the left of Add to Schedule: the same file, kept for the Calendar Manager. */
    onSavePreset: ((filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit)? = null,
    /** Instance Link Controller mode — non-null only when connected and controlling. Every go-live
     *  (including slide navigation) sends via PROJECT rather than the narrower SELECT_SLIDE: the
     *  primary only has slide bytes cached for a presentation it has itself loaded/added to its own
     *  schedule, so a Controller has no reliable way to target an already-live presentation by id. */
    onInstanceLinkSendProject: ((ScheduleItem) -> Unit)? = null,
    /** Instance Link Controller mode — advance/retreat whatever the primary currently has live, no
     *  id needed. Non-null only when connected and controlling. */
    onInstanceLinkSendNextSlide: (() -> Unit)? = null,
    onInstanceLinkSendPreviousSlide: (() -> Unit)? = null,
    /** Fetches one slide's raw JPEG bytes from the Instance Link primary by presentation id + index —
     *  non-null only while connected. Used when a mirrored schedule item's filePath doesn't resolve
     *  on this machine (e.g. a network drive mounted differently, or not mounted at all, here). */
    instanceLinkFetchPresentationSlideBytes: (suspend (id: String, index: Int) -> ByteArray?)? = null,
    selectedPresentationItem: ScheduleItem.PresentationItem? = null,
    /**
     * Bumped by the caller on every schedule click, so clicking the *same* item twice re-runs the
     * effect below. Keyed on the item alone, an unchanged item is an unchanged key and the second
     * click does nothing.
     */
    selectedPresentationItemVersion: Int = 0,
    presenterManager: PresenterManager? = null,
    onSlidesLoaded: PresentationSlidesLoaded? = null,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    viewModel: PresentationViewModel = remember { PresentationViewModel(appSettings) },
    tunnelStatus: TunnelStatus = TunnelStatus.Idle,
    tunnelUrl: String = "",
    serverUrl: String = "",
    presentationDisplayUrl: String = "",
    onPresentationDisplayUrlChanged: (String) -> Unit = {},
    onStartTunnel: () -> Unit = {},
    onStopTunnel: () -> Unit = {},
    presentationFrozen: Boolean = false,
    onFreezeToggle: () -> Unit = {},
    onClearPresentation: () -> Unit = {},
    /**
     * Whether VLC is usable, which decides only whether the missing-VLC banner is offered for a deck
     * containing video.
     *
     * A parameter rather than a read of the global in `VideoPlayer.kt`, which caches its answer in a
     * process-wide field — otherwise the banner's presence would depend on whether the machine running
     * the tests has VLC installed. Same seam `WebTab` uses for `cefInitialized`; the default is the
     * real check, so callers see no change.
     */
    vlcAvailable: Boolean = isVlcAvailable,
    /**
     * Which *reason* the banner gives, for the same reason [vlcAvailable] is a parameter.
     *
     * These were left reading the process-wide fields when [vlcAvailable] was hoisted, so the
     * banner's presence was deterministic while its wording was not: `isVlcArchMismatch` and
     * `isVlcLoadFailed` both derive from the global `isVlcAvailable`, so the detail line said
     * "install VLC" on a machine that has it and "failed to load" on one that does not. A test
     * pinning the text passed locally and failed on CI. `MediaTab` already took all three.
     */
    vlcArchMismatch: Boolean = isVlcArchMismatch,
    vlcLoadFailed: Boolean = isVlcLoadFailed,
) {
    val scope = rememberCoroutineScope()
    val showRemoteDialogState = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // Focus-lost rescue (banner + window-focus watch + AWT heal) — the full, hands-on
    // verified machinery lives in composables/FocusLostRescue.kt; shared with Bible/Songs.
    val focusRescue = rememberFocusLostRescue(
        hostWindow = hostWindow,
        focusRequester = focusRequester,
        active = viewModel.slideFiles.isNotEmpty(),
    )
    val shortcuts = LocalShortcuts.current
    val wentLive = LocalWentLive.current
    val presentationFileDialogTitle = stringResource(Res.string.select_presentation_file)
    // Remembered, keyed on everything it holds: a new scope on every recomposition would hand the
    // pieces new lambdas each time, and a click handler keyed on its lambda would restart.
    val tab = remember(
        hostWindow, appSettings, onAddToSchedule, onSavePreset, onInstanceLinkSendProject, onInstanceLinkSendNextSlide,
        onInstanceLinkSendPreviousSlide, instanceLinkFetchPresentationSlideBytes, selectedPresentationItem,
        selectedPresentationItemVersion, presenterManager, onSlidesLoaded, onSettingsChange, tunnelStatus,
        tunnelUrl, serverUrl, presentationDisplayUrl, onPresentationDisplayUrlChanged, onStartTunnel, onStopTunnel,
        presentationFrozen, onFreezeToggle, onClearPresentation, vlcAvailable, vlcArchMismatch, vlcLoadFailed, scope,
        focusRequester, focusRescue, shortcuts, wentLive, presentationFileDialogTitle, showRemoteDialogState
    ) {
        PresentationTabScope(
            hostWindow = hostWindow,
            appSettings = appSettings,
            onAddToSchedule = onAddToSchedule,
            onSavePreset = onSavePreset,
            onInstanceLinkSendProject = onInstanceLinkSendProject,
            onInstanceLinkSendNextSlide = onInstanceLinkSendNextSlide,
            onInstanceLinkSendPreviousSlide = onInstanceLinkSendPreviousSlide,
            instanceLinkFetchPresentationSlideBytes = instanceLinkFetchPresentationSlideBytes,
            selectedPresentationItem = selectedPresentationItem,
            selectedPresentationItemVersion = selectedPresentationItemVersion,
            presenterManager = presenterManager,
            onSlidesLoaded = onSlidesLoaded,
            onSettingsChange = onSettingsChange,
            tunnelStatus = tunnelStatus,
            tunnelUrl = tunnelUrl,
            serverUrl = serverUrl,
            presentationDisplayUrl = presentationDisplayUrl,
            onPresentationDisplayUrlChanged = onPresentationDisplayUrlChanged,
            onStartTunnel = onStartTunnel,
            onStopTunnel = onStopTunnel,
            presentationFrozen = presentationFrozen,
            onFreezeToggle = onFreezeToggle,
            onClearPresentation = onClearPresentation,
            vlcAvailable = vlcAvailable,
            vlcArchMismatch = vlcArchMismatch,
            vlcLoadFailed = vlcLoadFailed,
            scope = scope,
            focusRequester = focusRequester,
            focusRescue = focusRescue,
            shortcuts = shortcuts,
            wentLive = wentLive,
            presentationFileDialogTitle = presentationFileDialogTitle,
            showRemoteDialogState = showRemoteDialogState,
        )
    }
    with(tab) {
        PresentationLoadEffects(viewModel)
        PresentationLiveEffects(viewModel)
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag("presentation_root")
                .focusRequester(focusRequester)
                .onFocusChanged { focusRescue.onFocusChanged(it.hasFocus) }
                .focusRescuePressHook(focusRescue)
                .focusable()
                .onKeyEvent { keyEvent -> handleKey(viewModel, keyEvent) }
        ) {
            PresentationTopBar(viewModel)
            PresentationBody(viewModel)
        }

        if (showRemoteDialog) {
            PresentationRemoteDialog(
                settings = appSettings,
                onSettingsChange = onSettingsChange,
                serverUrl = serverUrl,
                apiKeyEnabled = appSettings.serverSettings.apiKeyEnabled,
                apiKey = appSettings.serverSettings.apiKey,
                tunnelStatus = tunnelStatus,
                tunnelUrl = tunnelUrl,
                presentationDisplayUrl = presentationDisplayUrl,
                onPresentationDisplayUrlChanged = onPresentationDisplayUrlChanged,
                onStartTunnel = onStartTunnel,
                onStopTunnel = onStopTunnel,
                onDismiss = { showRemoteDialog = false }
            )
        }
    }
}

/** Dismissible warning shown when the loaded deck has embedded video but VLC isn't installed —
 *  the slide itself already degrades gracefully to a static poster (see EmbeddedVideoDecoder),
 *  this only explains why to the operator. Resets per deck load via the caller's `remember` key. */
@Composable
internal fun VlcMissingBanner(detail: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.media_vlc_required), style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            KeyIconButton(onClick = onDismiss) {
                Icon(painterResource(AppRes.drawable.ic_close), contentDescription = stringResource(Res.string.clear))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SlideThumbnail(
    slide: ImageBitmap?,
    slideNumber: Int,
    buildCount: Int = 0,
    isSelected: Boolean,
    isHidden: Boolean = false,
    onToggleHidden: () -> Unit = {},
    onClick: () -> Unit,
    onDoubleClick: () -> Unit = {}
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .hoverLift(AppShape(8.dp))
            .clip(AppShape(8.dp))
            .border(2.dp, borderColor, AppShape(8.dp))
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (slide != null) Image(
                bitmap = slide,
                contentDescription = stringResource(Res.string.slide_number, slideNumber),
                modifier = Modifier.fillMaxSize().alpha(if (isHidden) HIDDEN_TILE_ALPHA else 1f),
                contentScale = ContentScale.Fit
            )
            if (isHidden) HiddenBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
            // Build-step badge: the slide animates in N click steps.
            if (buildCount > 0) {
                Text(
                    text = buildCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), AppShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.slide_number, slideNumber),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                },
                maxLines = 1
            )
            SlideshowHideToggle(hidden = isHidden, position = slideNumber - 1, onToggle = onToggleHidden)
        }
    }
}

/** The open deck as the schedule row that identifies it -- the same shape Add to Schedule and Save Preset build. */
internal fun presentationRow(file: File, slideCount: Int): ScheduleItem.PresentationItem =
    ScheduleItem.PresentationItem(
        id = java.util.UUID.randomUUID().toString(),
        filePath = file.absolutePath,
        fileName = file.nameWithoutExtension,
        slideCount = slideCount,
        fileType = file.extension.lowercase(),
    )
