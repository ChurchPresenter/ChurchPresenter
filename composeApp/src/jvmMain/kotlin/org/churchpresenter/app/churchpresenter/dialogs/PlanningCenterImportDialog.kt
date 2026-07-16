package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.planning_center_connect
import churchpresenter.composeapp.generated.resources.planning_center_description
import churchpresenter.composeapp.generated.resources.planning_center_disconnect
import churchpresenter.composeapp.generated.resources.planning_center_import_add_song
import churchpresenter.composeapp.generated.resources.planning_center_import_button
import churchpresenter.composeapp.generated.resources.planning_center_import_deselect_all
import churchpresenter.composeapp.generated.resources.planning_center_import_file_count
import churchpresenter.composeapp.generated.resources.planning_center_import_items
import churchpresenter.composeapp.generated.resources.planning_center_import_matched
import churchpresenter.composeapp.generated.resources.planning_center_import_select_all
import churchpresenter.composeapp.generated.resources.planning_center_import_no_plans
import churchpresenter.composeapp.generated.resources.planning_center_import_select_plan
import churchpresenter.composeapp.generated.resources.planning_center_import_service_type
import churchpresenter.composeapp.generated.resources.planning_center_import_title
import churchpresenter.composeapp.generated.resources.planning_center_status_connected
import churchpresenter.composeapp.generated.resources.planning_center_status_connecting
import churchpresenter.composeapp.generated.resources.planning_center_status_error
import churchpresenter.composeapp.generated.resources.cancel
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.data.PlanningCenterClient
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.settings.PlanningCenterSettings
import org.churchpresenter.app.churchpresenter.server.PlanningCenterAuthServer
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.PlanningCenterImportViewModel
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop

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
    onAddBibleVerse: (bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String, bookId: Int) -> Unit,
    onConnected: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long, personName: String) -> Unit,
    onDisconnect: () -> Unit
) {
    if (!isVisible) return

    if (settings.accessToken.isBlank()) {
        // No dedicated settings tab anymore — connecting happens right here, on demand.
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
            AppThemeWrapper(theme = theme) {
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
                                    stringResource(Res.string.planning_center_status_error, it),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                                Text(stringResource(Res.string.cancel))
                            }
                            Button(
                                shape = RoundedCornerShape(6.dp),
                                enabled = !isConnecting,
                                onClick = {
                                    isConnecting = true
                                    connectionError = null
                                    connectScope.launch {
                                        try {
                                            val authUrl = PlanningCenterClient.buildAuthorizationUrl(BuildConfig.PLANNING_CENTER_CLIENT_ID)
                                            Desktop.getDesktop().browse(java.net.URI(authUrl))
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
                                                            connectionError = "Invalid client ID or secret"
                                                        PlanningCenterClient.TokenOutcome.NetworkError ->
                                                            connectionError = "Network error — check your connection"
                                                        PlanningCenterClient.TokenOutcome.Failure ->
                                                            connectionError = "Connection failed"
                                                    }
                                                }
                                                is PlanningCenterAuthServer.CallbackResult.Error ->
                                                    connectionError = callback.message
                                                PlanningCenterAuthServer.CallbackResult.Timeout ->
                                                    connectionError = "Timed out waiting for browser sign-in"
                                            }
                                        } finally {
                                            isConnecting = false
                                        }
                                    }
                                }
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
        }
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
    var isFetchingArrangement by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
        AppThemeWrapper(theme = theme) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    val selectedServiceTypeName = viewModel.serviceTypes
                        .firstOrNull { it.id == viewModel.selectedServiceTypeId }?.name ?: ""

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DropdownSelector(
                            label = stringResource(Res.string.planning_center_import_service_type),
                            items = viewModel.serviceTypes.map { it.name },
                            selected = selectedServiceTypeName,
                            onSelectedChange = { name ->
                                viewModel.serviceTypes.firstOrNull { it.name == name }?.let { viewModel.selectServiceType(it.id) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(Res.string.planning_center_status_connected, settings.connectedPersonName.ifBlank { "?" }),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                        TextButton(
                            onClick = onDisconnect,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(stringResource(Res.string.planning_center_disconnect), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    viewModel.errorMessage?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                    } else {
                        Text(
                            stringResource(Res.string.planning_center_import_select_plan),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                            viewModel.plans.forEach { plan ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        shape = RoundedCornerShape(6.dp),
                                        onClick = { viewModel.selectPlan(plan.id) }
                                    ) {
                                        Text("${plan.title}${if (plan.dates.isNotBlank()) " — ${plan.dates}" else ""}")
                                    }
                                }
                            }
                        }
                    }

                    if (viewModel.selectedPlanId != null) {
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.planning_center_import_items), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = viewModel.allSelected,
                                onCheckedChange = { viewModel.setAllSelected(it) },
                                enabled = viewModel.planItems.isNotEmpty()
                            )
                            Text(
                                if (viewModel.allSelected) {
                                    stringResource(Res.string.planning_center_import_deselect_all)
                                } else {
                                    stringResource(Res.string.planning_center_import_select_all)
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                                    val pco = entry.pco
                                    // Scoped to the whole row (not just the button's branch) so the
                                    // attachments list below can also check it — a real accordion.
                                    var expanded by remember(pco.id) { mutableStateOf(false) }
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            when (pco.itemType) {
                                                "song" -> {
                                                    Checkbox(
                                                        checked = entry.selected && entry.matchedSongId != null,
                                                        enabled = entry.matchedSongId != null,
                                                        onCheckedChange = { viewModel.toggleItemSelected(pco.id) }
                                                    )
                                                    Text(pco.songTitle ?: pco.title, modifier = Modifier.weight(1f))
                                                    if (entry.matchedSongId != null) {
                                                        Text(
                                                            stringResource(Res.string.planning_center_import_matched),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    } else if (isFetchingArrangement == pco.id) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    } else {
                                                        OutlinedButton(
                                                            shape = RoundedCornerShape(6.dp),
                                                            onClick = {
                                                                isFetchingArrangement = pco.id
                                                                scope.launch {
                                                                    val detail = viewModel.fetchArrangementForAddSong(pco)
                                                                    addSongPrefill = SongItem(
                                                                        number = "",
                                                                        title = pco.songTitle ?: pco.title,
                                                                        songbook = viewModel.defaultSongbookForNewSongs(),
                                                                        author = pco.songAuthor ?: "",
                                                                        lyrics = (detail?.lyrics ?: "").split("\n"),
                                                                        ccliNumber = pco.songCcliNumber ?: ""
                                                                    )
                                                                    addSongForItem = pco
                                                                    isFetchingArrangement = null
                                                                }
                                                            }
                                                        ) {
                                                            Text(stringResource(Res.string.planning_center_import_add_song))
                                                        }
                                                    }
                                                }
                                                "header" -> {
                                                    Checkbox(
                                                        checked = entry.selected,
                                                        onCheckedChange = { viewModel.toggleItemSelected(pco.id) }
                                                    )
                                                    Text(
                                                        pco.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                else -> {
                                                    // Generic "item" rows: if scripture references were
                                                    // detected (see below), that accordion IS the selection
                                                    // mechanism — no separate announcement checkbox needed.
                                                    // Otherwise fall back to a plain checkbox (import the
                                                    // title as an announcement). "media" rows never get a
                                                    // top-level checkbox — attachments are their own picker.
                                                    val hasScripture = viewModel.detectedScripturesByItemId[pco.id]?.isNotEmpty() == true
                                                    if (pco.itemType == "item" && !hasScripture) {
                                                        Checkbox(
                                                            checked = entry.selected,
                                                            onCheckedChange = { viewModel.toggleItemSelected(pco.id) }
                                                        )
                                                    } else {
                                                        Spacer(Modifier.width(40.dp))
                                                    }
                                                    Text(
                                                        pco.title,
                                                        modifier = Modifier.weight(1f),
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                    )
                                                    // Attachments are now loaded eagerly (see selectPlan),
                                                    // so the button only appears once we actually know
                                                    // there's something to show — never a dead-end click.
                                                    val attachmentsLoaded = viewModel.attachmentsByItemId.containsKey(pco.id)
                                                    val fileCount = viewModel.attachmentsByItemId[pco.id]?.size ?: 0
                                                    if (!attachmentsLoaded) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    } else if (fileCount > 0) {
                                                        OutlinedButton(
                                                            shape = RoundedCornerShape(6.dp),
                                                            onClick = { expanded = !expanded }
                                                        ) {
                                                            Text(stringResource(Res.string.planning_center_import_file_count, fileCount))
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        val scriptures = viewModel.detectedScripturesByItemId[pco.id].orEmpty()
                                        if (scriptures.isNotEmpty()) {
                                            val selectedScriptureIdx = viewModel.selectedScriptureIndices[pco.id].orEmpty()
                                            scriptures.forEachIndexed { index, verse ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Checkbox(
                                                        checked = index in selectedScriptureIdx,
                                                        onCheckedChange = { viewModel.toggleScriptureSelected(pco.id, index) }
                                                    )
                                                    Text(verse.displayReference, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }

                                        if (expanded && pco.itemType != "song" && pco.itemType != "header") {
                                            val attachments = viewModel.attachmentsByItemId[pco.id].orEmpty()
                                            val selectedIds = viewModel.selectedAttachmentIds[pco.id].orEmpty()
                                            attachments.forEach { att ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Checkbox(
                                                        checked = att.id in selectedIds,
                                                        onCheckedChange = { viewModel.toggleAttachmentSelected(pco.id, att.id) }
                                                    )
                                                    val thumbUrl = att.thumbnailUrl
                                                    if (thumbUrl != null && att.filename.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS) {
                                                        AttachmentThumbnail(thumbUrl, viewModel)
                                                    }
                                                    Text(att.filename, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
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

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                            Text(stringResource(Res.string.cancel))
                        }
                        var isImporting by remember { mutableStateOf(false) }
                        val planId = viewModel.selectedPlanId
                        Button(
                            shape = RoundedCornerShape(6.dp),
                            enabled = !isImporting && planId != null && viewModel.planItems.any { entry ->
                                val pco = entry.pco
                                val hasScripture = viewModel.detectedScripturesByItemId[pco.id]?.isNotEmpty() == true
                                when (pco.itemType) {
                                    "header" -> entry.selected
                                    "song" -> entry.matchedSongId != null
                                    "item" -> if (hasScripture) {
                                        viewModel.selectedScriptureIndices[pco.id]?.isNotEmpty() == true
                                    } else {
                                        entry.selected || viewModel.selectedAttachmentIds[pco.id]?.isNotEmpty() == true
                                    }
                                    "media" -> viewModel.selectedAttachmentIds[pco.id]?.isNotEmpty() == true
                                    else -> false
                                }
                            },
                            onClick = {
                                if (planId == null) return@Button
                                isImporting = true
                                scope.launch {
                                    for (entry in viewModel.planItems) {
                                        if (!entry.selected) continue
                                        val pco = entry.pco
                                        when (pco.itemType) {
                                            "song" -> {
                                                val songId = entry.matchedSongId ?: continue
                                                val parts = songId.split("::", limit = 2)
                                                val songbook = parts.getOrNull(0) ?: ""
                                                val songNumber = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                                onAddSong(songNumber, pco.songTitle ?: pco.title, songbook, songId)
                                            }
                                            "header" -> onAddLabel(pco.title, "#FFFFFF", "#2196F3")
                                            "item" -> {
                                                val scriptures = viewModel.detectedScripturesByItemId[pco.id].orEmpty()
                                                val hasSelectedAttachments = viewModel.selectedAttachmentIds[pco.id]?.isNotEmpty() == true
                                                if (scriptures.isNotEmpty()) {
                                                    val selectedIdx = viewModel.selectedScriptureIndices[pco.id].orEmpty()
                                                    scriptures.forEachIndexed { index, verse ->
                                                        if (index !in selectedIdx) return@forEachIndexed
                                                        onAddBibleVerse(
                                                            verse.bookName,
                                                            verse.chapter,
                                                            verse.verseNumber,
                                                            verse.verseText,
                                                            verse.verseRange,
                                                            verse.bookId
                                                        )
                                                    }
                                                } else if (!hasSelectedAttachments) {
                                                    // Only fall back to a text announcement when there's
                                                    // no attached file — a file import already becomes its
                                                    // own Presentation/Picture/Media schedule entry below,
                                                    // so adding an announcement too would just duplicate it.
                                                    onAddAnnouncement(pco.description.ifBlank { pco.title })
                                                }
                                            }
                                        }
                                        // Attachments are their own per-file checkboxes, independent of
                                        // the row's main checkbox — applies to both "media" and "item".
                                        if (pco.itemType == "media" || pco.itemType == "item") {
                                            val attachments = viewModel.attachmentsByItemId[pco.id].orEmpty()
                                            val selectedIds = viewModel.selectedAttachmentIds[pco.id].orEmpty()
                                            // All selected images for this item share one cache folder
                                            // (keyed by item id) — collect them into a single Picture
                                            // schedule entry (one slideshow) instead of one per image.
                                            var pictureFolderPath: String? = null
                                            var pictureFolderName: String? = null
                                            var pictureCount = 0
                                            for (att in attachments) {
                                                if (att.id !in selectedIds) continue
                                                // The schedule item's title should read as the plan item's
                                                // own title (e.g. "Guest Speaker Presentation"), not the
                                                // raw uploaded filename — fall back to the filename only
                                                // when the plan item has no title.
                                                when (val imported = viewModel.importAttachment(planId, pco.id, att)) {
                                                    is PlanningCenterImportViewModel.ImportedMedia.Presentation ->
                                                        onAddPresentation(
                                                            imported.filePath,
                                                            pco.title.ifBlank { imported.fileName },
                                                            imported.slideCount,
                                                            imported.fileType
                                                        )
                                                    is PlanningCenterImportViewModel.ImportedMedia.Picture -> {
                                                        pictureFolderPath = imported.folderPath
                                                        pictureFolderName = pco.title.ifBlank { imported.folderName }
                                                        pictureCount++
                                                    }
                                                    is PlanningCenterImportViewModel.ImportedMedia.Media ->
                                                        onAddMedia(imported.mediaUrl, pco.title.ifBlank { imported.mediaTitle }, "local")
                                                    null -> {}
                                                }
                                            }
                                            if (pictureFolderPath != null && pictureFolderName != null) {
                                                onAddPicture(pictureFolderPath, pictureFolderName, pictureCount)
                                            }
                                        }
                                    }
                                    isImporting = false
                                    onDismiss()
                                }
                            }
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(Res.string.planning_center_import_button))
                        }
                    }
                }
            }
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
        onSave = { savedSong ->
            val saved = viewModel.createLocalSong(savedSong)
            if (saved != null && targetItem != null) {
                viewModel.markItemResolved(targetItem.id, saved.songId)
            }
            addSongForItem = null
            addSongPrefill = null
        }
    )
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

/** Small thumbnail preview for an image attachment in the import picker, fetched on demand. */
@Composable
private fun AttachmentThumbnail(thumbnailUrl: String, viewModel: PlanningCenterImportViewModel) {
    var bitmap by remember(thumbnailUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(thumbnailUrl) {
        val bytes = viewModel.fetchThumbnailBytes(thumbnailUrl)
        bitmap = bytes?.let {
            try {
                SkiaImage.makeFromEncoded(it).toComposeImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}
