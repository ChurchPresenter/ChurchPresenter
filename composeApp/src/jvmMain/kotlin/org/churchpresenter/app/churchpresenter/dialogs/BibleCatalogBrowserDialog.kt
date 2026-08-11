package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_catalog_attribution
import churchpresenter.composeapp.generated.resources.bible_catalog_done
import churchpresenter.composeapp.generated.resources.bible_catalog_download
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_archive
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_convert
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_generic
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_incomplete
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_network
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_stalled
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_write
import churchpresenter.composeapp.generated.resources.bible_catalog_empty
import churchpresenter.composeapp.generated.resources.bible_catalog_empty_hint
import churchpresenter.composeapp.generated.resources.bible_catalog_error_generic
import churchpresenter.composeapp.generated.resources.bible_catalog_error_network
import churchpresenter.composeapp.generated.resources.bible_catalog_error_rate_limited
import churchpresenter.composeapp.generated.resources.bible_catalog_installed
import churchpresenter.composeapp.generated.resources.bible_catalog_installed_count
import churchpresenter.composeapp.generated.resources.bible_catalog_installed_summary
import churchpresenter.composeapp.generated.resources.bible_catalog_language_all
import churchpresenter.composeapp.generated.resources.bible_catalog_language_count
import churchpresenter.composeapp.generated.resources.bible_catalog_language_named
import churchpresenter.composeapp.generated.resources.bible_catalog_language_named_native
import churchpresenter.composeapp.generated.resources.bible_catalog_license_accept
import churchpresenter.composeapp.generated.resources.bible_catalog_license_badge_redistributable
import churchpresenter.composeapp.generated.resources.bible_catalog_license_badge_unverified
import churchpresenter.composeapp.generated.resources.bible_catalog_license_body
import churchpresenter.composeapp.generated.resources.bible_catalog_license_field_copyright
import churchpresenter.composeapp.generated.resources.bible_catalog_license_field_identifier
import churchpresenter.composeapp.generated.resources.bible_catalog_license_field_source
import churchpresenter.composeapp.generated.resources.bible_catalog_license_source_ebible
import churchpresenter.composeapp.generated.resources.bible_catalog_license_source_zefania
import churchpresenter.composeapp.generated.resources.bible_catalog_license_subtitle
import churchpresenter.composeapp.generated.resources.bible_catalog_license_title
import churchpresenter.composeapp.generated.resources.bible_catalog_license_unknown
import churchpresenter.composeapp.generated.resources.bible_catalog_loading
import churchpresenter.composeapp.generated.resources.bible_catalog_no_directory
import churchpresenter.composeapp.generated.resources.bible_catalog_overwrite_confirm
import churchpresenter.composeapp.generated.resources.bible_catalog_phase_converting
import churchpresenter.composeapp.generated.resources.bible_catalog_phase_downloading
import churchpresenter.composeapp.generated.resources.bible_catalog_phase_extracting
import churchpresenter.composeapp.generated.resources.bible_catalog_phase_installing
import churchpresenter.composeapp.generated.resources.bible_catalog_redownload
import churchpresenter.composeapp.generated.resources.bible_catalog_retry
import churchpresenter.composeapp.generated.resources.bible_catalog_rights
import churchpresenter.composeapp.generated.resources.bible_catalog_search_placeholder
import churchpresenter.composeapp.generated.resources.bible_catalog_size_mb
import churchpresenter.composeapp.generated.resources.bible_catalog_source_ebible
import churchpresenter.composeapp.generated.resources.bible_catalog_source_zefania
import churchpresenter.composeapp.generated.resources.bible_catalog_stale_notice
import churchpresenter.composeapp.generated.resources.bible_catalog_subtitle
import churchpresenter.composeapp.generated.resources.bible_catalog_testament_full
import churchpresenter.composeapp.generated.resources.bible_catalog_testament_new
import churchpresenter.composeapp.generated.resources.bible_catalog_testament_old
import churchpresenter.composeapp.generated.resources.bible_catalog_title
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.bible_catalog_book_names_english
import churchpresenter.composeapp.generated.resources.bible_catalog_license_source_beblia
import churchpresenter.composeapp.generated.resources.bible_catalog_source_beblia
import churchpresenter.composeapp.generated.resources.ok
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.SearchableDropdownField
import org.churchpresenter.app.churchpresenter.data.BebliaSource
import org.churchpresenter.app.churchpresenter.data.BibleModule
import org.churchpresenter.app.churchpresenter.data.BibleSource
import org.churchpresenter.app.churchpresenter.data.BibleSourceId
import org.churchpresenter.app.churchpresenter.data.EBibleSource
import org.churchpresenter.app.churchpresenter.data.InstallPhase
import org.churchpresenter.app.churchpresenter.data.Testament
import org.churchpresenter.app.churchpresenter.data.ZefaniaSource
import org.churchpresenter.app.churchpresenter.viewmodel.BibleCatalogError
import org.churchpresenter.app.churchpresenter.viewmodel.BibleCatalogViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.BibleDownloadError
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Browses the available Bible archives and installs the chosen translations into [storageDirectory].
 *
 * One tab per archive, each with its own view model so switching tabs keeps each list's search and
 * scroll position. eBible.org comes first: it is far larger, and it states each translation's
 * copyright in the list rather than only after the file has been downloaded and opened.
 *
 * Each install downloads a module and converts it on this machine, so the row reports which stage
 * it is at. The dialog stays open afterwards — people normally collect two or three translations in
 * one sitting — and reports each one through [onBibleInstalled] so the settings tab behind it can
 * refresh its Primary/Secondary dropdowns straight away.
 */
@Composable
fun BibleCatalogBrowserDialog(
    storageDirectory: String,
    onDismiss: () -> Unit,
    onBibleInstalled: (fileName: String) -> Unit
) {
    val sources: List<Pair<BibleSource, StringResource>> = remember {
        listOf(
            EBibleSource to Res.string.bible_catalog_source_ebible,
            ZefaniaSource to Res.string.bible_catalog_source_zefania,
            BebliaSource to Res.string.bible_catalog_source_beblia,
        )
    }
    val viewModels = remember(storageDirectory) {
        sources.map { (source, _) -> BibleCatalogViewModel(source, storageDirectory) }
    }
    val tabLabels = sources.map { (_, label) -> stringResource(label) }
    DisposableEffect(viewModels) {
        onDispose { viewModels.forEach { it.dispose() } }
    }

    val mainWindowState = LocalMainWindowState.current
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, 860.dp, 780.dp),
        width = 860.dp,
        height = 780.dp
    )

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(Res.string.bible_catalog_title),
        resizable = true
    ) {
        BibleCatalogBrowserDialogContent(
            viewModels = viewModels,
            tabLabels = tabLabels,
            onDismiss = onDismiss,
            onBibleInstalled = onBibleInstalled,
        )
    }
}

@Composable
internal fun BibleCatalogBrowserDialogContent(
    viewModels: List<BibleCatalogViewModel>,
    tabLabels: List<String>,
    onDismiss: () -> Unit,
    onBibleInstalled: (fileName: String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val viewModel = viewModels[selectedTab]

    // Only a directory listing, not a network fetch, so every tab's installed set is known up
    // front — otherwise the header's total-installed count would only reflect whichever tab
    // happens to be active.
    LaunchedEffect(viewModels) {
        viewModels.forEach { it.refreshInstalled() }
    }

    // Each tab's catalogue loads the first time it is opened, so the second archive costs nothing
    // unless asked for.
    LaunchedEffect(viewModel) {
        viewModel.refreshInstalled()
        viewModel.load()
    }

    // Every download is confirmed, deliberately with no "don't ask again": the licence differs per
    // translation, so an acknowledgement given for one says nothing about the next.
    var pendingInstall by remember { mutableStateOf<BibleModule?>(null) }

    // An install only marks the file installed on the tab that ran it, but every tab lists the same
    // Bible folder — so without this the header's count and the other tab's "Installed" badges stay
    // stale until the dialog is reopened.
    val markInstalledEverywhere: (String) -> Unit = { fileName ->
        viewModels.forEach { it.markInstalled(fileName) }
        onBibleInstalled(fileName)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Header(installedCount = viewModels.first().installedFiles.size)
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SourceSegmentedControl(
                    tabLabels = tabLabels,
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it }
                )
                Spacer(Modifier.weight(1f))
                LanguageDropdown(
                    languages = viewModel.languages,
                    selectedLanguage = viewModel.selectedLanguage,
                    onLanguageChange = { viewModel.selectedLanguage = it },
                )
            }
            Spacer(Modifier.height(12.dp))

            SearchField(viewModel)
            Spacer(Modifier.height(12.dp))

            Messages(viewModel, onRetryInstall = { viewModel.retryLastInstall(markInstalledEverywhere) })

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    viewModel.isLoading && viewModel.modules.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(Res.string.bible_catalog_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    viewModel.catalogError != null && viewModel.modules.isEmpty() -> {
                        TextButton(
                            onClick = { viewModel.load() },
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(stringResource(Res.string.bible_catalog_retry))
                        }
                    }
                    viewModel.visibleModules.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(Res.string.bible_catalog_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(Res.string.bible_catalog_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(viewModel.visibleModules, key = { it.key }) { module ->
                                ModuleRow(
                                    module = module,
                                    showDate = module.displayName.trim().lowercase() in viewModel.duplicateDisplayNames,
                                    isInstalled = viewModel.isInstalled(module),
                                    isInstalling = viewModel.installingKey == module.key,
                                    phase = viewModel.installPhase,
                                    progress = viewModel.installProgress,
                                    anyInstallRunning = viewModel.installingKey != null,
                                    onInstall = { pendingInstall = module }
                                )
                            }
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.bible_catalog_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                // Not "Cancel": each install has already happened by the time this is pressed,
                // so there is nothing here to call off.
                Button(onClick = onDismiss, shape = RoundedCornerShape(6.dp)) {
                    Text(stringResource(Res.string.bible_catalog_done))
                }
            }
        }
    }

    pendingInstall?.let { module ->
        LicenceConfirmation(
            module = module,
            isReinstall = viewModel.isInstalled(module),
            onConfirm = {
                pendingInstall = null
                viewModel.install(module, markInstalledEverywhere)
            },
            onDismiss = { pendingInstall = null }
        )
    }

    viewModel.lastInstalled?.let { installed ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissInstalledNotice() },
            title = { Text(stringResource(Res.string.bible_catalog_installed)) },
            text = {
                Column {
                    Text(stringResource(Res.string.bible_catalog_installed_summary, installed.title, installed.books))
                    if (installed.rights.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.bible_catalog_rights, installed.rights),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissInstalledNotice() }) {
                    Text(stringResource(Res.string.ok))
                }
            }
        )
    }
}

/** Rounded-square icon swatch shared by the dialog header and the licence-confirmation header. */
@Composable
private fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp
) {
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
private fun Header(installedCount: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        IconBadge(icon = Icons.Filled.Book)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.bible_catalog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.bible_catalog_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.inverseOnSurface, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.bible_catalog_installed_count, installedCount),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SourceSegmentedControl(
    tabLabels: List<String>,
    selectedTab: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabLabels.forEachIndexed { index, label ->
            val selected = index == selectedTab
            val container = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                onClick = { onSelect(index) },
                shape = MaterialTheme.shapes.small,
                color = container,
                contentColor = content
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Shown before every download.
 *
 * The archives carry public-domain texts next to ones licensed for congregational use only, so this
 * names the licence of the translation actually being installed rather than a generic warning —
 * and it says so plainly when the translation declares none, which is the case that most warrants
 * a look before the text goes on a screen in front of a congregation.
 *
 * Re-downloading folds into the same dialog rather than stacking a second one on top. The
 * redistributable/unverified badge is chosen from which archive the module came from, not from
 * whether its copyright string happens to be blank — eBible always states redistribution rights,
 * Zefania never publishes licence details up front, and the Holy Bible XML archive republishes a
 * copyright statement it has not checked, so only eBible's rows are badged as redistributable.
 *
 * That archive also gets a second notice, because its files identify books by number alone: for a
 * language the app has no book-name table for, the installed Bible lists its books in English. That
 * is worth learning before the download rather than after it.
 */
@Composable
private fun LicenceConfirmation(
    module: BibleModule,
    isReinstall: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isRedistributable = module.sourceId == BibleSourceId.EBIBLE
    val showsEnglishBookNames = module.sourceId == BibleSourceId.BEBLIA &&
        !BebliaSource.hasLocalisedBookNames(module.language)
    val badgeContainer = if (isRedistributable) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.errorContainer
    val badgeContent = if (isRedistributable) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onErrorContainer
    val badgeLabel = stringResource(
        if (isRedistributable) Res.string.bible_catalog_license_badge_redistributable
        else Res.string.bible_catalog_license_badge_unverified
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconBadge(icon = Icons.Filled.Copyright) },
        title = {
            Column {
                Text(stringResource(Res.string.bible_catalog_license_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(Res.string.bible_catalog_license_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            // Some copyright statements run to several lines, and the notice sits below them, so
            // this can outgrow a short window.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = module.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = badgeContainer, contentColor = badgeContent) {
                            Text(
                                text = badgeLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    MetadataRow(
                        label = stringResource(Res.string.bible_catalog_license_field_source),
                        value = stringResource(sourceLabelStringRes(module.sourceId))
                    )
                    MetadataRow(
                        label = stringResource(Res.string.bible_catalog_license_field_identifier),
                        value = module.identifier
                    )
                    MetadataRow(
                        label = stringResource(Res.string.bible_catalog_license_field_copyright),
                        value = if (module.copyright.isNotBlank()) {
                            module.copyright
                        } else {
                            stringResource(Res.string.bible_catalog_license_unknown)
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        // What the archive itself vouches for differs sharply between the two, and
                        // that is the part someone deciding whether they may project this text
                        // actually needs.
                        Text(
                            text = stringResource(sourceLicenceStringRes(module.sourceId)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.bible_catalog_license_body),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (showsEnglishBookNames) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.bible_catalog_book_names_english),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isReinstall) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.bible_catalog_overwrite_confirm, module.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(6.dp)) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.bible_catalog_license_accept))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(6.dp)) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Language filter.
 *
 * eBible alone lists over a thousand languages, so this types-to-filter rather than asking anyone to
 * scroll it. The label leads with the English language name where the archive publishes one, but
 * keeps the code: a few distinct codes share a name (Hebrew is both `HBO` and `HEB`), and the label
 * is the key that maps a pick back to the code the filter runs on, so duplicates would silently drop
 * a language from the list. Matching is a plain substring over the whole label, which is what makes
 * both "english" and "eng" find the same row.
 */
@Composable
private fun LanguageDropdown(
    languages: List<BibleCatalogViewModel.LanguageOption>,
    selectedLanguage: String?,
    onLanguageChange: (String?) -> Unit,
) {
    val allLanguagesLabel = stringResource(Res.string.bible_catalog_language_all)
    // Display label -> language code. The label carries the name and count, so the map is what turns
    // the user's pick back into the code the filter works on.
    val languageLabels = languages.associate { option ->
        // The autonym only earns its place when it says something the English name doesn't — the two
        // agree for about three quarters of the list, and repeating a word helps nobody.
        val names = listOf(option.name, option.nativeName)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val label = when (names.size) {
            0 -> stringResource(Res.string.bible_catalog_language_count, option.code, option.count)
            1 -> stringResource(Res.string.bible_catalog_language_named, names[0], option.code, option.count)
            else -> stringResource(
                Res.string.bible_catalog_language_named_native,
                names[0], names[1], option.code, option.count
            )
        }
        label to option.code
    }
    val selectedLabel = languageLabels.entries
        .firstOrNull { it.value == selectedLanguage }?.key
        ?: allLanguagesLabel

    SearchableDropdownField(
        value = selectedLabel,
        options = listOf(allLanguagesLabel) + languageLabels.keys,
        onValueChange = { label -> onLanguageChange(languageLabels[label]) },
        leadingIcon = {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        // The field is sized to the header row, but a label carrying both spellings of a language
        // name runs long — most fit in this width, and the rest wrap rather than ellipsize, because
        // the truncated tail would take the code and count with it and those are what disambiguate
        // two languages sharing an English name.
        horizontalPadding = 11.dp,
        menuWidth = 340.dp,
        fillWidth = true,
        // Nothing to clear until a language has actually been picked — "All languages" is the
        // unfiltered state, so offering to clear it would undo nothing.
        onClear = { onLanguageChange(null) }.takeIf { selectedLanguage != null },
        itemContent = { option ->
            Text(
                text = option,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        clearOnFocus = true,
        modifier = Modifier.width(220.dp)
    )
}

private val SearchFieldShape = RoundedCornerShape(10.dp)

// A bespoke field rather than the shared SettingsTextField — this dialog's search box sits
// directly on the dialog surface and needs to read as barely-there (a faint tint + hairline
// border) rather than the settings-form contrast SettingsTextField is tuned for.
@Composable
private fun SearchField(viewModel: BibleCatalogViewModel) {
    BasicTextField(
        value = viewModel.query,
        onValueChange = { viewModel.query = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), SearchFieldShape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), SearchFieldShape),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (viewModel.query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.bible_catalog_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
private fun Messages(viewModel: BibleCatalogViewModel, onRetryInstall: () -> Unit) {
    if (viewModel.isStale) {
        Text(
            text = stringResource(Res.string.bible_catalog_stale_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(8.dp))
    }
    viewModel.catalogError?.let { error ->
        Text(
            text = stringResource(catalogErrorStringRes(error)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
    }
    viewModel.installError?.let { error ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(installErrorStringRes(error)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            // Only the stalled case: everything else has already been tried as many times as it is
            // worth trying, and a Retry beside "couldn't be converted" only invites a second wait.
            if (error == BibleDownloadError.DOWNLOAD_STALLED) {
                TextButton(onClick = onRetryInstall) {
                    Text(stringResource(Res.string.bible_catalog_retry))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Narrow enough that the button reads as the row's action rather than a column of its own, and wide
 * enough for the phase label and progress bar to sit in the same footprint as the buttons — so a
 * row doesn't reflow when an install starts.
 */
private val ACTION_COLUMN_WIDTH = 160.dp

@Composable
private fun ModuleRow(
    module: BibleModule,
    showDate: Boolean,
    isInstalled: Boolean,
    isInstalling: Boolean,
    phase: InstallPhase?,
    progress: Float,
    anyInstallRunning: Boolean,
    onInstall: () -> Unit
) {
    // A long list of near-identical rows is hard to track a cursor across, so the row under the
    // pointer is tinted — the same treatment the content tabs give their lists.
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val rowBackground by animateColorAsState(
        targetValue = if (hovered) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        } else {
            Color.Transparent
        },
        label = "bibleCatalogRowHover"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .background(rowBackground, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModuleAvatar(module)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Name is the headline; everything else sits beneath it as progressively fainter meta,
            // with copyright last and mutest — what someone may legally project matters, but the
            // name is what they're actually looking for in this list.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = module.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isInstalled) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = stringResource(Res.string.bible_catalog_installed),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
            Text(
                text = moduleSubtitle(module, showDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (module.copyright.isNotBlank()) {
                Text(
                    text = module.copyright,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.width(ACTION_COLUMN_WIDTH), contentAlignment = Alignment.CenterEnd) {
            when {
                isInstalling -> Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(phaseStringRes(phase)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                isInstalled -> OutlinedButton(
                    onClick = onInstall,
                    enabled = !anyInstallRunning,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.bible_catalog_redownload),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                else -> Button(
                    onClick = onInstall,
                    enabled = !anyInstallRunning,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.bible_catalog_download),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/**
 * Small rounded-square swatch leading each row: the module's identifier plus a colored testament
 * chip. Colors come straight from the theme's primary/secondary/tertiary roles (the same pattern
 * `SearchModeChip` in `BibleTab.kt` uses), which is what keeps them correct across all app themes
 * instead of copying the redesign mockup's hardcoded dark-theme palette.
 */
@Composable
private fun ModuleAvatar(module: BibleModule) {
    val (container, content) = when (module.testament) {
        Testament.NEW -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        Testament.OLD -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
        Testament.FULL -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
    val testamentLabel = stringResource(
        when (module.testament) {
            Testament.NEW -> Res.string.bible_catalog_testament_new
            Testament.OLD -> Res.string.bible_catalog_testament_old
            Testament.FULL -> Res.string.bible_catalog_testament_full
        }
    )
    Surface(
        modifier = Modifier.size(44.dp),
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = module.identifier.take(3).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = testamentLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun moduleSubtitle(module: BibleModule, showDate: Boolean): String {
    val parts = mutableListOf<String>()
    if (module.identifier.isNotBlank()) parts.add(module.identifier)
    if (module.language.isNotBlank()) parts.add(module.language)
    if (module.sizeBytes > 0) {
        val megabytes = "%.1f".format(module.sizeBytes / (1024.0 * 1024.0))
        parts.add(stringResource(Res.string.bible_catalog_size_mb, megabytes))
    }
    // Only where the name alone is ambiguous — see BibleCatalogViewModel.duplicateDisplayNames.
    if (showDate && module.releaseDate.isNotBlank()) parts.add(module.releaseDate)
    return parts.joinToString(" · ")
}

private fun phaseStringRes(phase: InstallPhase?): StringResource = when (phase) {
    InstallPhase.EXTRACTING -> Res.string.bible_catalog_phase_extracting
    InstallPhase.CONVERTING -> Res.string.bible_catalog_phase_converting
    InstallPhase.INSTALLING -> Res.string.bible_catalog_phase_installing
    else -> Res.string.bible_catalog_phase_downloading
}

private fun sourceLabelStringRes(sourceId: BibleSourceId): StringResource = when (sourceId) {
    BibleSourceId.EBIBLE -> Res.string.bible_catalog_source_ebible
    BibleSourceId.ZEFANIA -> Res.string.bible_catalog_source_zefania
    BibleSourceId.BEBLIA -> Res.string.bible_catalog_source_beblia
}

private fun sourceLicenceStringRes(sourceId: BibleSourceId): StringResource = when (sourceId) {
    BibleSourceId.EBIBLE -> Res.string.bible_catalog_license_source_ebible
    BibleSourceId.ZEFANIA -> Res.string.bible_catalog_license_source_zefania
    BibleSourceId.BEBLIA -> Res.string.bible_catalog_license_source_beblia
}

private fun catalogErrorStringRes(error: BibleCatalogError): StringResource = when (error) {
    BibleCatalogError.NETWORK_ERROR -> Res.string.bible_catalog_error_network
    BibleCatalogError.RATE_LIMITED -> Res.string.bible_catalog_error_rate_limited
    BibleCatalogError.FAILURE -> Res.string.bible_catalog_error_generic
}

private fun installErrorStringRes(error: BibleDownloadError): StringResource = when (error) {
    BibleDownloadError.NETWORK_ERROR -> Res.string.bible_catalog_download_error_network
    BibleDownloadError.DOWNLOAD_STALLED -> Res.string.bible_catalog_download_error_stalled
    BibleDownloadError.HTTP_ERROR -> Res.string.bible_catalog_download_error_generic
    BibleDownloadError.CHECKSUM_MISMATCH -> Res.string.bible_catalog_download_error_incomplete
    BibleDownloadError.CORRUPT_ARCHIVE -> Res.string.bible_catalog_download_error_archive
    BibleDownloadError.CONVERSION_FAILED -> Res.string.bible_catalog_download_error_convert
    BibleDownloadError.WRITE_FAILED -> Res.string.bible_catalog_download_error_write
    BibleDownloadError.NO_DIRECTORY -> Res.string.bible_catalog_no_directory
}
