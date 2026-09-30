package org.churchpresenter.converter.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Transform
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import org.churchpresenter.theme.components.RaisedFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.churchpresenter.converter.song.DocumentTextExtractor
import org.churchpresenter.converter.song.findFormatInputs
import org.churchpresenter.converter.song.inputSize
import org.churchpresenter.converter.song.MarkdownToSongConverter
import org.churchpresenter.converter.song.ParsedSong
import org.churchpresenter.converter.song.DocumentFormat
import org.churchpresenter.converter.song.SoftProjectorFormat
import org.churchpresenter.converter.song.SongFormatConverter
import org.churchpresenter.converter.song.SongFormatConverters
import org.churchpresenter.converter.song.SpsToSongConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// =============================================================================
// Songs Tab — "convert from" rail plus a stepped conversion panel
// =============================================================================

@Composable
fun SongsTab(onConverted: (sourceId: String) -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf(SongSources.default.id) }
    val source = SongSources.byId(selectedId)

    Row(modifier = Modifier.fillMaxSize()) {
        SourceRail(
            query = query,
            onQueryChange = { query = it },
            selectedId = selectedId,
            onSelect = { selectedId = it },
            modifier = Modifier.width(274.dp).fillMaxHeight()
        )
        VerticalDivider()
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SourceHeader(source)
            when (source.id) {
                SongSources.SOFTPROJECTOR -> SoftProjectorPanel(onConverted)
                SongSources.DOCUMENTS -> DocumentsPanel(source, onConverted)
                else -> BatchFilePanel(source, SongFormatConverters.byId(source.id), onConverted)
            }
        }
    }
}

/** The searchable "Convert from" list of every format people migrate from. */
@Composable
private fun SourceRail(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val matches = SongSources.matching(query)
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 9.dp)) {
            Text(
                Strings.convertFrom.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ConverterTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = Strings.searchFormats,
                leadingIcon = Icons.Default.Search,
                height = 32.dp
            )
        }
        if (matches.isEmpty()) {
            Text(
                Strings.noFormatMatches(query),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 30.dp),
                textAlign = TextAlign.Center
            )
            return@Column
        }
        val railState = rememberLazyListState()
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = railState,
                modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
            ) {
                for (group in SourceGroup.entries) {
                    val items = matches.filter { it.group == group }
                    if (items.isEmpty()) continue
                    item(key = "group_${group.name}") { RailGroupLabel(SongSources.groupLabel(group)) }
                    items(items.size, key = { items[it].id }) { index ->
                        val entry = items[index]
                        SourceRailRow(entry, entry.id == selectedId) { onSelect(entry.id) }
                        Spacer(Modifier.height(2.dp))
                    }
                    item(key = "gap_${group.name}") { Spacer(Modifier.height(10.dp)) }
                }
                item(key = "request_format") {
                    Spacer(Modifier.height(2.dp))
                    RequestFormatNote()
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(railState)
            )
        }
    }
}

/** Identity strip above the conversion steps: which format, and what it turns into. */
@Composable
private fun SourceHeader(source: SongSource) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SourceInitialsTile(source.initials, selected = true, size = 38.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        source.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    ExtensionBadge(source.ext)
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExtensionBadge(".song", emphasized = true)
                }
                Text(
                    source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        HorizontalDivider()
    }
}

/**
 * Steps 1–2 plus the action bar for any format that converts a list of input files into one
 * `.song` file each — SongBeamer and Free Worship today.
 */
@Composable
private fun BatchFilePanel(
    source: SongSource,
    format: SongFormatConverter,
    onConverted: (sourceId: String) -> Unit,
) {
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var summary by remember { mutableStateOf("") }
    var outputDir by remember { mutableStateOf<File?>(null) }
    var previewItems by remember { mutableStateOf<List<PreviewItem>>(emptyList()) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var state by remember { mutableStateOf(ConvertState.SELECT) }
    var completed by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun clearResults() {
        previewItems = emptyList(); log = emptyList(); state = ConvertState.SELECT; completed = 0
    }

    fun choose(picked: List<File>, label: String) {
        if (picked.isEmpty()) return
        files = picked
        summary = label
        clearResults()
    }

    fun runConvert() {
        state = ConvertState.CONVERTING
        completed = 0
        scope.launch {
            val messages = mutableListOf<String>()
            for (file in files) {
                messages += withContext(Dispatchers.IO) {
                    try {
                        val result = format.convert(file, outputDir)
                        result.outputFiles.map { "OK: ${file.name} -> ${it.name}" } +
                            result.errors.map { "ERROR: ${file.name} - $it" }
                    } catch (e: Exception) {
                        listOf("ERROR: ${file.name} - ${e.message}")
                    }
                }
                completed++
            }
            log = messages
            if (messages.anyConverted()) onConverted(source.id)
            state = ConvertState.DONE
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                StepHeader(
                    index = 1,
                    complete = files.isNotEmpty(),
                    label = Strings.stepSourceFiles,
                    hint = if (files.isEmpty()) source.ext else ""
                )
                if (files.isEmpty()) {
                    FileDropZone(Strings.dropFilesHere(source.ext), source.accepts) {
                        RaisedButton(shape = ButtonShape, onClick = {
                            val picked = pickSourceFiles(source, format)
                            choose(picked, Strings.filesSelected(picked.size))
                        }) {
                            Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.selectFiles)
                        }
                        KeyButton(shape = ButtonShape, onClick = {
                            val dir = pickDirectory() ?: return@KeyButton
                            val picked = findFormatInputs(dir, format)
                            choose(picked, Strings.folderSelected(dir.absolutePath, picked.size))
                        }) {
                            Icon(Icons.Default.Folder, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.selectFolder)
                        }
                    }
                } else {
                    SelectedFilesCard(
                        summary = summary,
                        entries = files.take(FILE_LIST_LIMIT).map { SelectedEntry(it.name, formatFileSize(inputSize(it))) },
                        onChange = {
                            val picked = pickSourceFiles(source, format)
                            choose(picked, Strings.filesSelected(picked.size))
                        },
                        onClear = { files = emptyList(); summary = ""; clearResults() }
                    )
                }
            }
        }

        item {
            Column {
                StepHeader(index = 2, complete = outputDir != null, label = Strings.stepDestination)
                val needsFolder = format.needsOutputFolder
                DestinationRow(
                    path = outputDir?.absolutePath
                        ?: if (needsFolder) Strings.chooseOutputFolder else Strings.sameAsInput,
                    chosen = outputDir != null,
                    warning = if (needsFolder && outputDir == null) Strings.outputManyFilesWarning else null,
                    onBrowse = { pickDirectory()?.let { outputDir = it } }
                )
            }
        }

        item {
            ConversionActionBar(
                state = state,
                canPreview = files.isNotEmpty(),
                canConvert = files.isNotEmpty() && (outputDir != null || !format.needsOutputFolder),
                convertLabel = if (state == ConvertState.PREVIEW) Strings.convertNFiles(files.size) else Strings.convert,
                doneLabel = if (state == ConvertState.DONE) Strings.nConverted(log.count { it.startsWith("OK") }) else null,
                onPreview = { previewItems = buildFormatPreview(format, files, outputDir); state = ConvertState.PREVIEW },
                onConvert = { runConvert() },
                onStartOver = { files = emptyList(); summary = ""; clearResults() }
            )
        }

        if (state == ConvertState.CONVERTING) {
            item {
                ConversionProgressRow(
                    Strings.convertingFiles(files.size),
                    if (files.isEmpty()) null else completed.toFloat() / files.size
                )
            }
        }

        if (state == ConvertState.PREVIEW && previewItems.isNotEmpty()) {
            item { Text(Strings.previewLabel, style = MaterialTheme.typography.titleSmall) }
            items(previewItems) { item -> PreviewRow(item) }
        }
        if (state == ConvertState.DONE && log.isNotEmpty()) {
            item {
                val ok = log.count { it.startsWith("OK") }
                val err = log.count { it.startsWith("ERROR") }
                Text(
                    Strings.doneConverted(ok, err),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (err > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            items(log) { msg -> LogLine(msg) }
        }
    }
}

/**
 * SoftProjector: every chosen `.sps` song book explodes into a folder of `.song` files.
 *
 * Kept apart from the generic panel because a song book previews as a list of the songs inside it
 * rather than as one output file — but it takes several books at once, since a church migrating off
 * SoftProjector has one file per songbook and converting them one at a time is the whole job done
 * by hand.
 */
@Composable
private fun SoftProjectorPanel(onConverted: (sourceId: String) -> Unit) {
    val spsSource = SongSources.byId(SongSources.SOFTPROJECTOR)
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var summary by remember { mutableStateOf("") }
    var outputDir by remember { mutableStateOf<File?>(null) }
    var previews by remember { mutableStateOf<List<SpsPreviewData>>(emptyList()) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var state by remember { mutableStateOf(ConvertState.SELECT) }
    var completed by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun clearResults() {
        previews = emptyList(); log = emptyList(); state = ConvertState.SELECT; completed = 0
    }

    fun choose(picked: List<File>, describedAs: String) {
        if (picked.isEmpty()) return
        files = picked
        summary = describedAs
        clearResults()
    }

    fun runConvert() {
        state = ConvertState.CONVERTING
        completed = 0
        val destination = outputDir ?: return
        scope.launch {
            val messages = mutableListOf<String>()
            for (file in files) {
                messages += withContext(Dispatchers.IO) { convertSongBook(file, destination) }
                completed++
            }
            log = messages
            if (messages.anyConverted()) onConverted(spsSource.id)
            state = ConvertState.DONE
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                StepHeader(
                    index = 1,
                    complete = files.isNotEmpty(),
                    label = Strings.stepSourceFiles,
                    hint = if (files.isEmpty()) spsSource.ext else ""
                )
                if (files.isEmpty()) {
                    FileDropZone(Strings.dropFilesHere(spsSource.ext), spsSource.accepts) {
                        RaisedButton(shape = ButtonShape, onClick = {
                            val picked = pickSourceFiles(spsSource, SoftProjectorFormat)
                            choose(picked, Strings.filesSelected(picked.size))
                        }) {
                            Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.selectFiles)
                        }
                        KeyButton(shape = ButtonShape, onClick = {
                            val dir = pickDirectory() ?: return@KeyButton
                            val picked = findFormatInputs(dir, SoftProjectorFormat)
                            choose(picked, Strings.folderSelected(dir.absolutePath, picked.size))
                        }) {
                            Icon(Icons.Default.Folder, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.selectFolder)
                        }
                    }
                } else {
                    SelectedFilesCard(
                        summary = summary,
                        entries = files.take(FILE_LIST_LIMIT).map { SelectedEntry(it.name, formatFileSize(inputSize(it))) },
                        onChange = {
                            val picked = pickSourceFiles(spsSource, SoftProjectorFormat)
                            choose(picked, Strings.filesSelected(picked.size))
                        },
                        onClear = { files = emptyList(); summary = ""; clearResults() }
                    )
                }
            }
        }

        item {
            Column {
                StepHeader(index = 2, complete = outputDir != null, label = Strings.stepDestination)
                DestinationRow(
                    path = outputDir?.absolutePath ?: Strings.chooseOutputFolder,
                    chosen = outputDir != null,
                    warning = if (outputDir == null) Strings.outputManyFilesWarning else null,
                    onBrowse = { pickDirectory()?.let { outputDir = it } }
                )
            }
        }

        item {
            val ready = files.isNotEmpty() && outputDir != null
            ConversionActionBar(
                state = state,
                canPreview = ready,
                canConvert = ready && previews.none { it.error != null },
                convertLabel = if (state == ConvertState.PREVIEW) {
                    Strings.convertNSongs(previews.sumOf { it.songCount })
                } else Strings.convert,
                doneLabel = if (state == ConvertState.DONE && log.none { it.startsWith("ERROR") }) Strings.doneLabel else null,
                onPreview = {
                    previews = files.map { buildSpsPreview(it, outputDir!!) }
                    state = ConvertState.PREVIEW
                },
                onConvert = { runConvert() },
                onStartOver = { files = emptyList(); summary = ""; clearResults() }
            )
        }

        if (state == ConvertState.CONVERTING) {
            item {
                ConversionProgressRow(
                    Strings.convertingFiles(files.size),
                    if (files.isEmpty()) null else completed.toFloat() / files.size
                )
            }
        }

        if (state == ConvertState.PREVIEW) {
            items(previews) { preview -> SpsPreviewCard(preview, showTitles = previews.size == 1) }
        }
        if (state == ConvertState.DONE && log.isNotEmpty()) {
            item {
                val hasErr = log.any { it.startsWith("ERROR") }
                Text(
                    if (hasErr) Strings.completedWithErrors else Strings.doneLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (hasErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            items(log) { msg -> LogLine(msg) }
        }
    }
}

/** One song book's preview: what it holds, where it lands, and whether that folder is already there. */
@Composable
private fun SpsPreviewCard(preview: SpsPreviewData, showTitles: Boolean) {
    if (preview.error != null) {
        Text(Strings.errorPrefix(preview.error), color = MaterialTheme.colorScheme.error)
        return
    }
    Column {
        Text(Strings.songbookPrefix(preview.songbookName), style = MaterialTheme.typography.bodyMedium)
        Text(Strings.songsFound(preview.songCount), style = MaterialTheme.typography.bodyMedium)
        Text(
            Strings.outputFolderPrefix(preview.outputFolder),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (preview.folderExists) {
            Text(
                Strings.outputFolderOverwrite,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (showTitles && preview.sampleTitles.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(Strings.songsLabel, style = MaterialTheme.typography.titleSmall)
            preview.sampleTitles.forEach { title ->
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** One song book converted, reported in the same OK/ERROR lines every other format's log uses. */
private fun convertSongBook(file: File, outputDir: File): List<String> = runCatching {
    val result = SpsToSongConverter.convert(file, outputDir)
    val folder = result.songbookFolder.substringAfterLast('/').substringAfterLast('\\')
    listOf("OK: ${file.name} -> $folder, ${Strings.songsConverted(result.songsConverted)}") +
        result.errors.map { "ERROR: ${file.name} - $it" }
}.getOrElse { failure -> listOf("ERROR: ${file.name} - ${failure.message}") }

/** PDF / PPTX / DOCX: text is extracted, split into songs, then written out. */
@Composable
private fun DocumentsPanel(source: SongSource, onConverted: (sourceId: String) -> Unit) {
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var outputDir by remember { mutableStateOf<File?>(null) }
    var parsedSongs by remember { mutableStateOf<List<ParsedSong>>(emptyList()) }
    var markdown by remember { mutableStateOf("") }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var state by remember { mutableStateOf(ConvertState.SELECT) }
    var showMarkdown by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun clearResults() {
        parsedSongs = emptyList(); markdown = ""; log = emptyList(); state = ConvertState.SELECT; completed = 0
    }

    fun pick(): List<File> = pickSourceFiles(source, DocumentFormat)

    fun runConvert() {
        state = ConvertState.CONVERTING
        completed = 0
        scope.launch {
            val msgs = mutableListOf<String>()
            for (file in files) {
                msgs += withContext(Dispatchers.IO) {
                    try {
                        val result = DocumentTextExtractor.extract(file)
                        if (!result.success) {
                            listOf("ERROR: ${file.name} - ${result.errorMessage}")
                        } else {
                            val convResult = MarkdownToSongConverter.convert(result.text, file.name, outputDir!!)
                            convResult.outputFiles.map { "OK: ${file.name} -> ${it.name}" } +
                                convResult.errors.map { "ERROR: ${file.name} - $it" }
                        }
                    } catch (e: Exception) {
                        listOf("ERROR: ${file.name} - ${e.message}")
                    }
                }
                completed++
            }
            log = msgs
            if (msgs.anyConverted()) onConverted(source.id)
            state = ConvertState.DONE
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                StepHeader(
                    index = 1,
                    complete = files.isNotEmpty(),
                    label = Strings.stepSourceFiles,
                    hint = if (files.isEmpty()) source.ext else ""
                )
                if (files.isEmpty()) {
                    FileDropZone(Strings.dropFilesHere(source.ext), source.accepts) {
                        RaisedButton(shape = ButtonShape, onClick = {
                            val picked = pick()
                            if (picked.isNotEmpty()) { files = picked; clearResults() }
                        }) {
                            Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.selectFiles)
                        }
                        KeyButton(shape = ButtonShape, onClick = {
                            val dir = pickDirectory() ?: return@KeyButton
                            val picked = findFormatInputs(dir, DocumentFormat)
                            if (picked.isNotEmpty()) { files = picked; clearResults() }
                        }) {
                            Icon(Icons.Default.Folder, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.selectFolder)
                        }
                    }
                } else {
                    SelectedFilesCard(
                        summary = Strings.filesSelected(files.size),
                        entries = files.take(FILE_LIST_LIMIT).map { SelectedEntry(it.name, formatFileSize(inputSize(it))) },
                        onChange = {
                            val picked = pick()
                            if (picked.isNotEmpty()) { files = picked; clearResults() }
                        },
                        onClear = { files = emptyList(); clearResults() }
                    )
                }
            }
        }

        item {
            Column {
                StepHeader(index = 2, complete = outputDir != null, label = Strings.stepDestination)
                DestinationRow(
                    path = outputDir?.absolutePath ?: Strings.chooseOutputFolder,
                    chosen = outputDir != null,
                    warning = if (outputDir == null) Strings.outputManyFilesWarning else null,
                    onBrowse = { pickDirectory()?.let { outputDir = it } }
                )
            }
        }

        item {
            ConversionActionBar(
                state = state,
                canPreview = files.isNotEmpty(),
                canConvert = files.isNotEmpty() && outputDir != null,
                convertLabel = if (state == ConvertState.PREVIEW) {
                    Strings.convertNSongs(parsedSongs.size)
                } else Strings.convert,
                doneLabel = if (state == ConvertState.DONE) Strings.nConverted(log.count { it.startsWith("OK") }) else null,
                onPreview = {
                    state = ConvertState.CONVERTING
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val allSongs = mutableListOf<ParsedSong>()
                            val textParts = mutableListOf<String>()
                            for (file in files) {
                                val (text, songs) = MarkdownToSongConverter.preview(file)
                                textParts.add("── ${file.name} ──\n$text")
                                allSongs.addAll(songs)
                            }
                            markdown = textParts.joinToString("\n\n")
                            parsedSongs = allSongs
                        }
                        state = ConvertState.PREVIEW
                    }
                },
                onConvert = { runConvert() },
                onStartOver = { files = emptyList(); clearResults() }
            )
        }

        if (state == ConvertState.CONVERTING) {
            item {
                ConversionProgressRow(
                    Strings.convertingFiles(files.size),
                    if (files.isEmpty()) null else completed.toFloat() / files.size
                )
            }
        }

        if (state == ConvertState.PREVIEW && parsedSongs.isNotEmpty()) {
            item {
                Column {
                    Text(Strings.songsExtracted(parsedSongs.size), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RaisedFilterChip(
                            selected = !showMarkdown,
                            onClick = { showMarkdown = false },
                            label = { Text(Strings.docPreviewSong) }
                        )
                        RaisedFilterChip(
                            selected = showMarkdown,
                            onClick = { showMarkdown = true },
                            label = { Text(Strings.docPreviewMarkdown) }
                        )
                    }
                }
            }
            if (showMarkdown) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Text(
                            markdown,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(10.dp).horizontalScroll(rememberScrollState())
                        )
                    }
                }
            } else {
                items(parsedSongs) { song ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(song.title, style = MaterialTheme.typography.bodyMedium)
                            val details = mutableListOf<String>()
                            if (song.author.isNotBlank()) details.add(song.author)
                            details.add(Strings.sectionsLines(song.sections.size, song.sections.sumOf { it.lines.size }))
                            Text(
                                details.joinToString(" | "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (song.sections.isNotEmpty()) {
                                Text(
                                    song.sections.joinToString(", ") { it.label },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state == ConvertState.DONE && log.isNotEmpty()) {
            item {
                val ok = log.count { it.startsWith("OK") }
                val err = log.count { it.startsWith("ERROR") }
                Text(
                    Strings.doneConverted(ok, err),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (err > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            items(log) { msg -> LogLine(msg) }
        }
    }
}

/** Preview / Convert / Start over, plus the "n converted" pill once a run finishes. */
@Composable
private fun ConversionActionBar(
    state: ConvertState,
    canPreview: Boolean,
    canConvert: Boolean,
    convertLabel: String,
    doneLabel: String?,
    onPreview: () -> Unit,
    onConvert: () -> Unit,
    onStartOver: () -> Unit
) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(bottom = 14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            when (state) {
                ConvertState.SELECT, ConvertState.PREVIEW -> {
                    KeyButton(shape = ButtonShape, onClick = onPreview, enabled = canPreview) {
                        Icon(Icons.Default.Preview, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.preview)
                    }
                    RaisedButton(shape = ButtonShape, onClick = onConvert, enabled = canConvert) {
                        Icon(Icons.Default.Transform, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(convertLabel)
                    }
                }
                ConvertState.CONVERTING -> {
                    RaisedButton(shape = ButtonShape, enabled = false, onClick = {}) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text(Strings.converting)
                    }
                }
                ConvertState.DONE -> {
                    KeyButton(shape = ButtonShape, onClick = onStartOver) { Text(Strings.startOver) }
                }
            }
            Spacer(Modifier.weight(1f))
            if (state == ConvertState.DONE && doneLabel != null) {
                DoneChip(doneLabel)
            }
        }
    }
}
