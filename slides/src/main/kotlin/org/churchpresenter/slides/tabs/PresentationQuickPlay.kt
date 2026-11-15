package org.churchpresenter.slides.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.churchpresenter.slides.viewmodel.HymnQuickPlay
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.hymn_quick_query
import org.churchpresenter.strings.generated.resources.hymn_quick_missing
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.settings.ResourceDirectory
import org.churchpresenter.settings.resourceDirectories
import java.io.File

@Composable
internal fun PresentationTabScope.PresentationQuickPlay(viewModel: PresentationViewModel, documents: List<File>,
    libraryControls: @Composable () -> Unit,
) {
    val settings = appSettings.presentationSettings
    val root = File(appSettings.presentationStorageDirectory)
    val roots = appSettings.resourceDirectories(ResourceDirectory.PRESENTATIONS).map(::File)
    var query by remember { mutableStateOf("") }
    val results = remember(documents, query, settings.quickPlayBook, settings.quickPlayFormat, roots) {
        HymnQuickPlay.find(documents, root, query, settings.quickPlayBook, settings.quickPlayFormat, roots)
    }
    val resultScroll = rememberLazyListState()
    LaunchedEffect(query, settings.quickPlayBook, settings.quickPlayFormat, roots) { resultScroll.scrollToItem(0) }
    var pending by remember { mutableStateOf<Pair<File, Int>?>(null) }
    val start: (File) -> Unit = { file ->
        pending = file to viewModel.loadGeneration
        onSettingsChange {
            it.copy(projectionSettings = it.projectionSettings.copy(
                windowedProjection = it.projectionSettings.quickPlayDisplay == null,
            ))
        }
        viewModel.previewPresentation(file)
    }
    val search = {
        if (pending == null) {
            val selected = viewModel.selectedPresentation?.takeIf { query.isBlank() }
            val file = selected ?: results.firstOrNull()
            file?.let(start)
        }
    }
    LaunchedEffect(viewModel.loadGeneration, viewModel.loadError, viewModel.isLoading, pending,
        viewModel.selectedPresentation) {
        val request = pending ?: return@LaunchedEffect
        if (viewModel.selectedPresentation != request.first || viewModel.loadError != null) {
            pending = null
            return@LaunchedEffect
        }
        if (viewModel.loadGeneration > request.second && !viewModel.isLoading) {
            pending = null
            if (viewModel.loadError == null && viewModel.slideFiles.isNotEmpty()) {
                presenterManager?.let { goLive(viewModel, it) }
            }
        }
    }
    Column {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(Res.string.hymn_quick_query)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { search() }),
            )
        }
        libraryControls()
        if (results.isEmpty()) Text(stringResource(Res.string.hymn_quick_missing))
        QuickPlayResults(results, root, roots, pending == null, resultScroll) { file ->
            pending = null
            viewModel.previewPresentation(file)
        }
    }
}

@Composable
private fun QuickPlayResults(
    results: List<File>, root: File, roots: List<File>, enabled: Boolean,
    resultScroll: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (File) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
            LazyColumn(Modifier.fillMaxWidth().padding(end = 14.dp), state = resultScroll) {
                items(results, key = { it.absolutePath }) { file ->
                    Column(Modifier.fillMaxWidth().clickable(enabled = enabled) {
                        onSelect(file)
                    }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(file.name, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(HymnQuickPlay.book(file, root, roots), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(resultScroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp),
            )
        }
    }
}
