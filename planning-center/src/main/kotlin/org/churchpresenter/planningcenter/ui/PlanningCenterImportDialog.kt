package org.churchpresenter.planningcenter.ui

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.planning_center_connect
import org.churchpresenter.strings.generated.resources.planning_center_description
import org.churchpresenter.strings.generated.resources.planning_center_import_no_plans
import org.churchpresenter.strings.generated.resources.planning_center_import_title
import org.churchpresenter.strings.generated.resources.planning_center_status_connecting
import org.churchpresenter.strings.generated.resources.atem_status_error
import kotlinx.coroutines.launch
import org.churchpresenter.sharedui.composables.cpColorToHex
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.planningcenter.PlanningCenterAuthServer
import org.churchpresenter.planningcenter.PlanningCenterClient
import org.churchpresenter.settings.PlanningCenterSettings
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.utils.UrlOpener

/** The small window that connects to Planning Center before anything can be imported. */
@Composable
private fun PlanningCenterConnectWindow(
    services: PlanningCenterImportServices,
    window: PlanningCenterWindow,
    onDismiss: () -> Unit,
    onConnected: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long, personName: String) -> Unit,
) {
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val connectScope = rememberCoroutineScope()
    val spec = PlanningCenterWindowSpec(
        title = stringResource(Res.string.planning_center_import_title),
        width = 460.dp,
        height = 260.dp,
        resizable = false,
        onClose = onDismiss,
    )
    window(spec) {
        PlanningCenterConnectDialogContent(
            isConnecting = isConnecting,
            connectionError = connectionError,
            onDismiss = onDismiss,
            onConnectClick = {
                isConnecting = true
                connectionError = null
                connectScope.launch {
                    try {
                        connectToPlanningCenter(services, onConnected = onConnected, onError = { connectionError = it })
                    } finally {
                        isConnecting = false
                    }
                }
            }
        )
    }
}

/** What a Planning Center window asks its host for: its title, its size, and what closing it does. */
class PlanningCenterWindowSpec(
    val title: String,
    val width: Dp,
    val height: Dp,
    val resizable: Boolean,
    val onClose: () -> Unit,
)

/**
 * Opens a window for [content] as the [spec] says. The host places it, and wraps the content in its
 * theme and whatever else its windows install.
 */
typealias PlanningCenterWindow = @Composable (spec: PlanningCenterWindowSpec, content: @Composable () -> Unit) -> Unit

/**
 * The host's song editor, for a plan song the library does not have yet: [song] is the prefill, or
 * null while no song is being added; [onSave] hands back the song the operator confirmed.
 */
typealias PlanningCenterSongEditor = @Composable (
    song: SongItem?,
    songbook: String,
    onDismiss: () -> Unit,
    onSave: (SongItem) -> Unit,
) -> Unit

/**
 * Lets the operator pick a Planning Center Services plan and import its songs (matched against
 * the local library, or added on the spot via [editSong]) and section headers (as schedule
 * labels) into the Schedule. Owns its own [PlanningCenterImportViewModel] (created here, never
 * passed elsewhere) and talks back to the host purely through typed callbacks — it never touches
 * `ScheduleViewModel`/`SongsViewModel` directly.
 */
@Composable
fun PlanningCenterImportDialog(
    isVisible: Boolean,
    settings: PlanningCenterSettings,
    services: PlanningCenterImportServices,
    window: PlanningCenterWindow,
    editSong: PlanningCenterSongEditor,
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
        PlanningCenterConnectWindow(services, window, onDismiss, onConnected)
        return
    }

    val viewModel = remember(isVisible) {
        PlanningCenterImportViewModel(
            initialAccessToken = settings.accessToken,
            initialRefreshToken = settings.refreshToken,
            initialExpiresAtEpochMs = settings.tokenExpiresAtEpochMs,
            initialServiceTypeId = settings.defaultServiceTypeId,
            importSongbookName = settings.importSongbookName,
            onTokensRefreshed = onTokensRefreshed,
            services = services,
        )
    }
    LaunchedEffect(isVisible) {
        if (isVisible) viewModel.loadServiceTypes()
    }

    var addSongForItem by remember { mutableStateOf<PlanningCenterClient.PlanItem?>(null) }
    var addSongPrefill by remember { mutableStateOf<SongItem?>(null) }

    val spec = PlanningCenterWindowSpec(
        title = stringResource(Res.string.planning_center_import_title),
        width = 900.dp,
        height = 750.dp,
        resizable = true,
        onClose = onDismiss,
    )
    window(spec) {
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

    val prefill = addSongPrefill
    val targetItem = addSongForItem
    editSong(
        if (targetItem != null) prefill else null,
        viewModel.defaultSongbookForNewSongs(),
        {
            addSongForItem = null
            addSongPrefill = null
        },
    ) { savedSong ->
        val saved = createLocalSong(savedSong)
        if (saved != null && targetItem != null) {
            viewModel.markItemResolved(targetItem.id, saved.songId)
        }
        addSongForItem = null
        addSongPrefill = null
    }
}

/**
 * Runs the Planning Center OAuth round trip: opens the consent page, waits for the local
 * loopback callback, and exchanges the code for tokens. [browse] stands in for
 * [UrlOpener.open], which falls back to the OS's own open command where AWT declines the BROWSE
 * action — a Linux desktop without a freedesktop.org helper, or a headless JVM — so this can run
 * headless in tests.
 */
internal suspend fun connectToPlanningCenter(
    services: PlanningCenterImportServices,
    browse: (java.net.URI) -> Unit = { UrlOpener.open(it.toString()) },
    onConnected: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long, personName: String) -> Unit,
    onError: (String) -> Unit
) {
    val authUrl = PlanningCenterClient.buildAuthorizationUrl(services.clientId)
    browse(java.net.URI(authUrl))
    when (val callback = PlanningCenterAuthServer.awaitAuthorizationCode()) {
        is PlanningCenterAuthServer.CallbackResult.Success -> {
            when (
                val tokenOutcome = PlanningCenterClient.exchangeCodeForToken(
                    services.clientId,
                    services.clientSecret,
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
