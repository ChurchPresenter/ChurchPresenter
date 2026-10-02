package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.planning_center_connect
import org.churchpresenter.strings.generated.resources.planning_center_description
import org.churchpresenter.strings.generated.resources.planning_center_import_no_plans
import org.churchpresenter.strings.generated.resources.planning_center_import_title
import org.churchpresenter.strings.generated.resources.planning_center_status_connecting
import org.churchpresenter.strings.generated.resources.atem_status_error
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.sharedui.composables.cpColorToHex
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.app.churchpresenter.viewmodel.PlanningCenterImportViewModel
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.planningcenter.PlanningCenterAuthServer
import org.churchpresenter.planningcenter.PlanningCenterClient
import org.churchpresenter.settings.PlanningCenterSettings
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.utils.UrlOpener

/** The small window that connects to Planning Center before anything can be imported. */
@Composable
private fun PlanningCenterConnectWindow(
    theme: ThemeMode,
    onDismiss: () -> Unit,
    onConnected: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long, personName: String) -> Unit,
) {
    val mainWindowState = LocalMainWindowState.current
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val connectScope = rememberCoroutineScope()
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 460.dp, 260.dp),
            width = 460.dp,
            height = 260.dp
        ),
        title = stringResource(Res.string.planning_center_import_title)
    ) {
        AppWindowRoot(theme = theme) {
            PlanningCenterConnectDialogContent(
                isConnecting = isConnecting,
                connectionError = connectionError,
                onDismiss = onDismiss,
                onConnectClick = {
                    isConnecting = true
                    connectionError = null
                    connectScope.launch {
                        try {
                            connectToPlanningCenter(onConnected = onConnected, onError = { connectionError = it })
                        } finally {
                            isConnecting = false
                        }
                    }
                }
            )
        }
    }
}

/**
 * Lets the operator pick a Planning Center Services plan and import its songs (matched against
 * the local library, or added on the spot via [EditSongDialog]) and section headers (as schedule
 * labels) into the Schedule. Owns its own [PlanningCenterImportViewModel] (created here, never
 * passed elsewhere) and talks back to the host purely through typed callbacks — it never touches
 * `ScheduleViewModel`/`SongsViewModel` directly.
 */
@Composable
fun PlanningCenterImportDialog(
    isVisible: Boolean,
    theme: ThemeMode,
    settings: PlanningCenterSettings,
    onDismiss: () -> Unit,
    onTokensRefreshed: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long) -> Unit,
    onAddSong: (songNumber: Int, title: String, songbook: String, songId: String) -> Unit,
    onAddLabel: (text: String, textColor: String, backgroundColor: String) -> Unit,
    onAddPresentation: (filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit,
    onAddPicture: (folderPath: String, folderName: String, imageCount: Int) -> Unit,
    onAddMedia: (mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit,
    onAddAnnouncement: (text: String) -> Unit,
    onAddBibleVerse: (bookName: String, chapter: Int, verseNumber: Int, verseText: String,
        verseRange: String, bookId: Int) -> Unit,
    onConnected: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long, personName: String) -> Unit,
    onDisconnect: () -> Unit
) {
    if (!isVisible) return

    if (settings.accessToken.isBlank()) {
        // No dedicated settings tab anymore — connecting happens right here, on demand.
        PlanningCenterConnectWindow(theme, onDismiss, onConnected)
        return
    }

    val viewModel = remember(isVisible) {
        PlanningCenterImportViewModel(
            initialAccessToken = settings.accessToken,
            initialRefreshToken = settings.refreshToken,
            initialExpiresAtEpochMs = settings.tokenExpiresAtEpochMs,
            initialServiceTypeId = settings.defaultServiceTypeId,
            importSongbookName = settings.importSongbookName,
            onTokensRefreshed = onTokensRefreshed
        )
    }
    LaunchedEffect(isVisible) {
        if (isVisible) viewModel.loadServiceTypes()
    }

    var addSongForItem by remember { mutableStateOf<PlanningCenterClient.PlanItem?>(null) }
    var addSongPrefill by remember { mutableStateOf<SongItem?>(null) }

    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 900.dp, 750.dp),
            width = 900.dp,
            height = 750.dp
        ),
        title = stringResource(Res.string.planning_center_import_title),
        resizable = true
    ) {
        AppWindowRoot(theme = theme) {
            PlanningCenterImportDialogContent(
                viewModel = viewModel,
                settings = settings,
                onDismiss = onDismiss,
                onDisconnect = onDisconnect,
                onAddSong = onAddSong,
                onAddLabel = onAddLabel,
                onAddPresentation = onAddPresentation,
                onAddPicture = onAddPicture,
                onAddMedia = onAddMedia,
                onAddAnnouncement = onAddAnnouncement,
                onAddBibleVerse = onAddBibleVerse,
                onAddSongRequested = { pco, prefill ->
                    addSongForItem = pco
                    addSongPrefill = prefill
                }
            )
        }
    }

    val prefill = addSongPrefill
    val targetItem = addSongForItem
    EditSongDialog(
        isVisible = targetItem != null && prefill != null,
        song = prefill,
        songbooks = listOf(viewModel.defaultSongbookForNewSongs()),
        isNewSong = true,
        theme = theme,
        onDismiss = {
            addSongForItem = null
            addSongPrefill = null
        },
        // Tempo and capo are not offered here (showTuningFields defaults off), so they come back unset.
        onSave = { savedSong, _ ->
            val saved = viewModel.createLocalSong(savedSong)
            if (saved != null && targetItem != null) {
                viewModel.markItemResolved(targetItem.id, saved.songId)
            }
            addSongForItem = null
            addSongPrefill = null
        }
    )
}

/**
 * Runs the Planning Center OAuth round trip: opens the consent page, waits for the local
 * loopback callback, and exchanges the code for tokens. [browse] stands in for
 * [UrlOpener.open], which falls back to the OS's own open command where AWT declines the BROWSE
 * action — a Linux desktop without a freedesktop.org helper, or a headless JVM — so this can run
 * headless in tests.
 */
internal suspend fun connectToPlanningCenter(
    browse: (java.net.URI) -> Unit = { UrlOpener.open(it.toString()) },
    onConnected: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long, personName: String) -> Unit,
    onError: (String) -> Unit
) {
    val authUrl = PlanningCenterClient.buildAuthorizationUrl(BuildConfig.PLANNING_CENTER_CLIENT_ID)
    browse(java.net.URI(authUrl))
    when (val callback = PlanningCenterAuthServer.awaitAuthorizationCode()) {
        is PlanningCenterAuthServer.CallbackResult.Success -> {
            when (
                val tokenOutcome = PlanningCenterClient.exchangeCodeForToken(
                    BuildConfig.PLANNING_CENTER_CLIENT_ID,
                    BuildConfig.PLANNING_CENTER_CLIENT_SECRET,
                    callback.code
                )
            ) {
                is PlanningCenterClient.TokenOutcome.Success -> {
                    val tokens = tokenOutcome.tokens
                    val personOutcome = PlanningCenterClient.getCurrentPerson(tokens.accessToken)
                    val name = (personOutcome as? PlanningCenterClient.PersonOutcome.Success)
                        ?.person?.displayName ?: ""
                    onConnected(tokens.accessToken, tokens.refreshToken, tokens.expiresAtEpochMs, name)
                }
                PlanningCenterClient.TokenOutcome.InvalidCredentials ->
                    onError("Invalid client ID or secret")
                PlanningCenterClient.TokenOutcome.NetworkError ->
                    onError("Network error — check your connection")
                PlanningCenterClient.TokenOutcome.Failure ->
                    onError("Connection failed")
            }
        }
        is PlanningCenterAuthServer.CallbackResult.Error -> onError(callback.message)
        PlanningCenterAuthServer.CallbackResult.Timeout -> onError("Timed out waiting for browser sign-in")
    }
}

@Composable
internal fun PlanningCenterConnectDialogContent(
    isConnecting: Boolean,
    connectionError: String?,
    onDismiss: () -> Unit,
    onConnectClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    stringResource(Res.string.planning_center_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                connectionError?.let {
                    Text(
                        stringResource(Res.string.atem_status_error, it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                GhostButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                    Text(stringResource(Res.string.cancel))
                }
                RaisedButton(
                    shape = AppShape(6.dp),
                    enabled = !isConnecting,
                    onClick = onConnectClick
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (isConnecting) stringResource(Res.string.planning_center_status_connecting)
                        else stringResource(Res.string.planning_center_connect)
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlanningCenterImportDialogContent(
    viewModel: PlanningCenterImportViewModel,
    settings: PlanningCenterSettings,
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit,
    onAddSong: (songNumber: Int, title: String, songbook: String, songId: String) -> Unit,
    onAddLabel: (text: String, textColor: String, backgroundColor: String) -> Unit,
    onAddPresentation: (filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit,
    onAddPicture: (folderPath: String, folderName: String, imageCount: Int) -> Unit,
    onAddMedia: (mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit,
    onAddAnnouncement: (text: String) -> Unit,
    onAddBibleVerse: (bookName: String, chapter: Int, verseNumber: Int, verseText: String,
        verseRange: String, bookId: Int) -> Unit,
    onAddSongRequested: (pco: PlanningCenterClient.PlanItem, prefill: SongItem) -> Unit
) {
    var isFetchingArrangement by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Header rows import with no color-picker step, so they need a default — sourced from the
    // current theme (matching AddLabelDialog's own picker) instead of a hardcoded hex pair.
    val defaultHeaderTextColor = cpColorToHex(MaterialTheme.colorScheme.onPrimary)
    val defaultHeaderBackgroundColor = cpColorToHex(MaterialTheme.colorScheme.primary)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            PcoImportHeader(viewModel, settings, onDisconnect)

            Spacer(Modifier.height(12.dp))

            viewModel.errorMessage?.let { err ->
                Text(
                    stringResource(err),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (viewModel.isLoadingServiceTypes || viewModel.isLoadingPlans) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (viewModel.plans.isEmpty()) {
                Text(
                    stringResource(Res.string.planning_center_import_no_plans),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (viewModel.selectedPlanId != null) {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                PcoItemsHeader(viewModel)
                Spacer(Modifier.height(4.dp))

                if (viewModel.isLoadingItems) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    val itemsListState = rememberLazyListState()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = itemsListState,
                        modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                    ) {
                        items(viewModel.planItems) { entry ->
                            PcoPlanItemRow(entry, viewModel, isFetchingArrangement) { pco ->
                                isFetchingArrangement = pco.id
                                scope.launch {
                                    onAddSongRequested(pco, newSongPrefill(viewModel, pco))
                                    isFetchingArrangement = null
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState = itemsListState)
                    )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f))
            }

            var isImporting by remember { mutableStateOf(false) }
            val planId = viewModel.selectedPlanId
            PcoImportFooter(
                isImporting = isImporting,
                canImport = planId != null && canImportSelection(viewModel),
                onDismiss = onDismiss,
            ) {
                if (planId == null) return@PcoImportFooter
                UsageEvents.record(UsageEvent.PLANNING_CENTER_IMPORT)
                isImporting = true
                scope.launch {
                    importSelection(
                        viewModel, planId,
                        PcoImportActions(
                            onAddSong, onAddLabel, onAddPresentation, onAddPicture, onAddMedia,
                            onAddAnnouncement, onAddBibleVerse,
                            headerTextColor = defaultHeaderTextColor,
                            headerBackgroundColor = defaultHeaderBackgroundColor,
                        ),
                    )
                    isImporting = false
                    onDismiss()
                }
            }
        }
    }
}
