package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.bible_catalog_attribution
import org.churchpresenter.resources.generated.resources.bible_catalog_done
import org.churchpresenter.resources.generated.resources.bible_catalog_empty
import org.churchpresenter.resources.generated.resources.bible_catalog_empty_hint
import org.churchpresenter.resources.generated.resources.bible_catalog_installed
import org.churchpresenter.resources.generated.resources.bible_catalog_installed_summary
import org.churchpresenter.resources.generated.resources.bible_catalog_loading
import org.churchpresenter.resources.generated.resources.bible_catalog_retry
import org.churchpresenter.resources.generated.resources.bible_catalog_rights
import org.churchpresenter.resources.generated.resources.bible_catalog_source_ebible
import org.churchpresenter.resources.generated.resources.bible_catalog_source_zefania
import org.churchpresenter.resources.generated.resources.bible_catalog_title
import org.churchpresenter.resources.generated.resources.bible_catalog_source_beblia
import org.churchpresenter.resources.generated.resources.ok
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.bibleformats.catalog.BebliaSource
import org.churchpresenter.bibleformats.catalog.BibleModule
import org.churchpresenter.bibleformats.catalog.BibleSource
import org.churchpresenter.bibleformats.catalog.EBibleSource
import org.churchpresenter.bibleformats.catalog.ZefaniaSource
import org.churchpresenter.app.churchpresenter.viewmodel.BibleCatalogViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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

    LaunchedEffect(viewModels) {
        viewModels.forEach { it.refreshInstalled() }
    }

    LaunchedEffect(viewModel) {
        viewModel.refreshInstalled()
        viewModel.load()
    }

    var pendingInstall by remember { mutableStateOf<BibleModule?>(null) }

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
