package ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import converter.library.DuplicateFinder
import converter.library.TextUtils
import converter.library.DuplicateGroup
import converter.song.DocumentTextExtractor
import converter.song.MarkdownToSongConverter
import converter.song.ParsedSong
import converter.song.DocumentFormat
import converter.song.SoftProjectorFormat
import converter.song.SongFormatConverter
import converter.song.SongFormatConverters
import converter.song.SongPreviewInfo
import converter.song.SpsToSongConverter
import converter.bible.SpbVersePatcher
import converter.bible.XmlToSpbConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val ButtonShape = RoundedCornerShape(6.dp)

/** Cards and forms stop widening past this — on a wide window a full-bleed row reads as empty band. */
private val CONTENT_MAX_WIDTH = 820.dp

@Composable
fun App() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(Strings.tabBibles, Strings.tabSongs, Strings.tabDuplicates, Strings.tabRename)

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ConverterTabRow(tabs, selectedTab) { selectedTab = it }

            when (selectedTab) {
                0 -> BibleConverterTab()
                1 -> SongsTab()
                2 -> DuplicateFinderTab()
                3 -> BulkRenameTab()
            }
        }
    }
}

enum class ConvertState { SELECT, PREVIEW, CONVERTING, DONE }

// =============================================================================
// Songs Tab — "convert from" rail plus a stepped conversion panel
// =============================================================================

@Composable
fun SongsTab() {
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
                SongSources.SOFTPROJECTOR -> SoftProjectorPanel()
                SongSources.DOCUMENTS -> DocumentsPanel(source)
                else -> BatchFilePanel(source, SongFormatConverters.byId(source.id))
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
private fun BatchFilePanel(source: SongSource, format: SongFormatConverter) {
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
                        Button(shape = ButtonShape, onClick = {
                            val picked = pickFiles(pickerLabel(source), *format.extensions.toTypedArray(), multiSelection = format.allowsMultipleFiles)
                            choose(picked, Strings.filesSelected(picked.size))
                        }) {
                            Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.selectFiles)
                        }
                        OutlinedButton(shape = ButtonShape, onClick = {
                            val dir = pickDirectory() ?: return@OutlinedButton
                            val picked = findFilesRecursive(dir, format.extensions.first())
                            choose(picked, Strings.folderSelected(dir.absolutePath, picked.size))
                        }) {
                            Icon(Icons.Default.Folder, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.selectFolder)
                        }
                    }
                } else {
                    SelectedFilesCard(
                        summary = summary,
                        entries = files.take(FILE_LIST_LIMIT).map { SelectedEntry(it.name, formatFileSize(it.length())) },
                        onChange = {
                            val picked = pickFiles(pickerLabel(source), *format.extensions.toTypedArray(), multiSelection = format.allowsMultipleFiles)
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

/** SoftProjector: one .sps song book explodes into a folder of .song files. */
@Composable
private fun SoftProjectorPanel() {
    val spsSource = SongSources.byId(SongSources.SOFTPROJECTOR)
    var spsFile by remember { mutableStateOf<File?>(null) }
    var outputDir by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf<SpsPreviewData?>(null) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var state by remember { mutableStateOf(ConvertState.SELECT) }
    val scope = rememberCoroutineScope()

    fun runConvert() {
        state = ConvertState.CONVERTING
        scope.launch {
            log = withContext(Dispatchers.IO) {
                try {
                    val result = SpsToSongConverter.convert(spsFile!!, outputDir!!)
                    val msgs = mutableListOf(
                        Strings.songbookPrefix(result.songbookFolder.substringAfterLast('/').substringAfterLast('\\')),
                        Strings.songsConverted(result.songsConverted),
                        Strings.outputPrefix(result.songbookFolder)
                    )
                    result.errors.forEach { msgs.add("ERROR: $it") }
                    if (result.errors.isEmpty()) msgs.add("OK: ${Strings.allSongsConverted}")
                    msgs
                } catch (e: Exception) {
                    listOf("ERROR: ${e.message}")
                }
            }
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
                    complete = spsFile != null,
                    label = Strings.stepSourceFiles,
                    hint = if (spsFile == null) ".sps" else ""
                )
                val chosen = spsFile
                if (chosen == null) {
                    FileDropZone(Strings.dropFilesHere(".sps"), SongSources.byId(SongSources.SOFTPROJECTOR).accepts) {
                        Button(shape = ButtonShape, onClick = {
                            pickFiles(pickerLabel(spsSource), *SoftProjectorFormat.extensions.toTypedArray(), multiSelection = false).firstOrNull()?.let {
                                spsFile = it; preview = null; log = emptyList(); state = ConvertState.SELECT
                            }
                        }) {
                            Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.selectFiles)
                        }
                    }
                } else {
                    SelectedFilesCard(
                        summary = chosen.name,
                        entries = listOf(SelectedEntry(chosen.name, formatFileSize(chosen.length()))),
                        onChange = {
                            pickFiles(pickerLabel(spsSource), *SoftProjectorFormat.extensions.toTypedArray(), multiSelection = false).firstOrNull()?.let {
                                spsFile = it; preview = null; log = emptyList(); state = ConvertState.SELECT
                            }
                        },
                        onClear = { spsFile = null; preview = null; log = emptyList(); state = ConvertState.SELECT }
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
            val ready = spsFile != null && outputDir != null
            ConversionActionBar(
                state = state,
                canPreview = ready,
                canConvert = ready && preview?.error == null,
                convertLabel = if (state == ConvertState.PREVIEW) {
                    Strings.convertNSongs(preview?.songCount ?: 0)
                } else Strings.convert,
                doneLabel = if (state == ConvertState.DONE && log.none { it.startsWith("ERROR") }) Strings.doneLabel else null,
                onPreview = { preview = buildSpsPreview(spsFile!!, outputDir!!); state = ConvertState.PREVIEW },
                onConvert = { runConvert() },
                onStartOver = { spsFile = null; preview = null; log = emptyList(); state = ConvertState.SELECT }
            )
        }

        if (state == ConvertState.CONVERTING) {
            item { ConversionProgressRow(Strings.converting, null) }
        }

        if (state == ConvertState.PREVIEW) {
            preview?.let { p ->
                if (p.error != null) {
                    item { Text(Strings.errorPrefix(p.error), color = MaterialTheme.colorScheme.error) }
                } else {
                    item {
                        Column {
                            Text(Strings.songbookPrefix(p.songbookName), style = MaterialTheme.typography.bodyMedium)
                            Text(Strings.songsFound(p.songCount), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                Strings.outputFolderPrefix(p.outputFolder),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (p.folderExists) {
                                Text(
                                    Strings.outputFolderOverwrite,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    if (p.sampleTitles.isNotEmpty()) {
                        item { Text(Strings.songsLabel, style = MaterialTheme.typography.titleSmall) }
                        items(p.sampleTitles) { title ->
                            Text(
                                title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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

/** PDF / PPTX / DOCX: text is extracted, split into songs, then written out. */
@Composable
private fun DocumentsPanel(source: SongSource) {
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

    fun pick(): List<File> = pickFiles(pickerLabel(source), *DocumentFormat.extensions.toTypedArray(), multiSelection = true)

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
                        Button(shape = ButtonShape, onClick = {
                            val picked = pick()
                            if (picked.isNotEmpty()) { files = picked; clearResults() }
                        }) {
                            Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.selectFiles)
                        }
                    }
                } else {
                    SelectedFilesCard(
                        summary = Strings.filesSelected(files.size),
                        entries = files.take(FILE_LIST_LIMIT).map { SelectedEntry(it.name, formatFileSize(it.length())) },
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
                        FilterChip(
                            selected = !showMarkdown,
                            onClick = { showMarkdown = false },
                            label = { Text(Strings.docPreviewSong) }
                        )
                        FilterChip(
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
                    OutlinedButton(shape = ButtonShape, onClick = onPreview, enabled = canPreview) {
                        Icon(Icons.Default.Preview, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.preview)
                    }
                    Button(shape = ButtonShape, onClick = onConvert, enabled = canConvert) {
                        Icon(Icons.Default.Transform, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(convertLabel)
                    }
                }
                ConvertState.CONVERTING -> {
                    Button(shape = ButtonShape, enabled = false, onClick = {}) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text(Strings.converting)
                    }
                }
                ConvertState.DONE -> {
                    OutlinedButton(shape = ButtonShape, onClick = onStartOver) { Text(Strings.startOver) }
                }
            }
            Spacer(Modifier.weight(1f))
            if (state == ConvertState.DONE && doneLabel != null) {
                DoneChip(doneLabel)
            }
        }
    }
}

// =============================================================================
// Bible Tab
// =============================================================================

@Composable
fun BibleConverterTab() {
    var inputFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var outputDir by remember { mutableStateOf<File?>(null) }
    var logMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    var state by remember { mutableStateOf(ConvertState.SELECT) }
    var previewItems by remember { mutableStateOf<List<PreviewItem>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var spbFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var fixState by remember { mutableStateOf(ConvertState.SELECT) }
    var fixLog by remember { mutableStateOf<List<String>>(emptyList()) }

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        // fillMaxHeight, not fillMaxSize: the latter pins min width to the parent too, which would
        // override the max below and leave the cards spanning the whole window.
        modifier = Modifier.fillMaxHeight().widthIn(max = CONTENT_MAX_WIDTH)
            .verticalScroll(scrollState).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      SectionCard(Strings.bibleTitle, Strings.bibleDesc) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shape = ButtonShape, onClick = {
                val files = pickFiles(Strings.xmlBibleFiles, "xml", multiSelection = true)
                if (files.isNotEmpty()) {
                    inputFiles = files; state = ConvertState.SELECT; previewItems = emptyList(); logMessages = emptyList()
                }
            }) {
                Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(Strings.selectXmlFiles)
            }
            OutlinedButton(shape = ButtonShape, onClick = {
                val dir = pickDirectory()
                if (dir != null) {
                    val files = findXmlFilesRecursive(dir)
                    inputFiles = files; state = ConvertState.SELECT; previewItems = emptyList(); logMessages = emptyList()
                }
            }) {
                Icon(Icons.Default.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(Strings.selectFolder)
            }
            if (inputFiles.isNotEmpty()) {
                Text(Strings.fileCount(inputFiles.size), style = MaterialTheme.typography.bodySmall)
            }
        }

        DestinationRow(
            path = outputDir?.absolutePath ?: Strings.sameAsInput,
            chosen = outputDir != null,
            warning = null,
            onBrowse = { pickDirectory()?.let { outputDir = it } }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                ConvertState.SELECT -> {
                    OutlinedButton(shape = ButtonShape, onClick = {
                        previewItems = buildBiblePreview(inputFiles, outputDir); state = ConvertState.PREVIEW
                    }, enabled = inputFiles.isNotEmpty()) {
                        Icon(Icons.Default.Preview, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(Strings.preview)
                    }
                    Button(shape = ButtonShape, onClick = {
                        state = ConvertState.CONVERTING
                        scope.launch {
                            logMessages = withContext(Dispatchers.IO) {
                                inputFiles.map { file ->
                                    try {
                                        val outDir = outputDir ?: file.parentFile
                                        val outFile = File(outDir, file.nameWithoutExtension + ".spb")
                                        XmlToSpbConverter.convert(file, outFile)
                                        "OK: ${file.name} -> ${outFile.name}"
                                    } catch (e: Exception) { "ERROR: ${file.name} - ${e.message}" }
                                }
                            }
                            state = ConvertState.DONE
                        }
                    }, enabled = inputFiles.isNotEmpty()) {
                        Icon(Icons.Default.Transform, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.convert)
                    }
                }
                ConvertState.PREVIEW -> {
                    Button(shape = ButtonShape, onClick = {
                        state = ConvertState.CONVERTING
                        scope.launch {
                            logMessages = withContext(Dispatchers.IO) {
                                inputFiles.map { file ->
                                    try {
                                        val outDir = outputDir ?: file.parentFile
                                        val outFile = File(outDir, file.nameWithoutExtension + ".spb")
                                        XmlToSpbConverter.convert(file, outFile)
                                        "OK: ${file.name} -> ${outFile.name}"
                                    } catch (e: Exception) { "ERROR: ${file.name} - ${e.message}" }
                                }
                            }
                            state = ConvertState.DONE
                        }
                    }) {
                        Icon(Icons.Default.Transform, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.convertNFiles(inputFiles.size))
                    }
                    OutlinedButton(shape = ButtonShape, onClick = { state = ConvertState.SELECT; previewItems = emptyList() }) { Text(Strings.back) }
                }
                ConvertState.CONVERTING -> {
                    Button(shape = ButtonShape, enabled = false, onClick = {}) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text(Strings.converting)
                    }
                }
                ConvertState.DONE -> {
                    OutlinedButton(shape = ButtonShape, onClick = {
                        state = ConvertState.SELECT; inputFiles = emptyList(); previewItems = emptyList(); logMessages = emptyList()
                    }) { Text(Strings.startOver) }
                }
            }
        }

        when (state) {
            ConvertState.PREVIEW -> {
                Text(Strings.previewLabel, style = MaterialTheme.typography.titleSmall)
                previewItems.forEach { item -> PreviewRow(item) }
            }
            ConvertState.DONE -> {
                val ok = logMessages.count { it.startsWith("OK") }; val err = logMessages.count { it.startsWith("ERROR") }
                Text(Strings.doneConverted(ok, err), style = MaterialTheme.typography.titleSmall,
                    color = if (err > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                logMessages.forEach { msg -> LogLine(msg) }
            }
            else -> {}
        }
      }

      SectionCard(Strings.fixVersesTitle, Strings.fixVersesDesc) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shape = ButtonShape, onClick = {
                val files = pickFiles(Strings.spbBibleFiles, "spb", multiSelection = true)
                if (files.isNotEmpty()) {
                    spbFiles = files; fixState = ConvertState.SELECT; fixLog = emptyList()
                }
            }) {
                Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(Strings.selectSpbFiles)
            }

            when (fixState) {
                ConvertState.SELECT -> {
                    Button(shape = ButtonShape, onClick = {
                        fixState = ConvertState.CONVERTING
                        scope.launch {
                            fixLog = withContext(Dispatchers.IO) {
                                spbFiles.map { file ->
                                    try {
                                        val count = SpbVersePatcher.applyPatches(file)
                                        if (count > 0) "OK: ${file.name} \u2014 $count verse(s) patched"
                                        else "OK: ${file.name} \u2014 no patches needed"
                                    } catch (e: Exception) { "ERROR: ${file.name} \u2014 ${e.message}" }
                                }
                            }
                            fixState = ConvertState.DONE
                        }
                    }, enabled = spbFiles.isNotEmpty()) {
                        Icon(Icons.Default.Build, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.fixVerses)
                    }
                }
                ConvertState.CONVERTING -> {
                    Button(shape = ButtonShape, enabled = false, onClick = {}) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text(Strings.fixingVerses)
                    }
                }
                ConvertState.DONE -> {
                    OutlinedButton(shape = ButtonShape, onClick = {
                        fixState = ConvertState.SELECT; spbFiles = emptyList(); fixLog = emptyList()
                    }) { Text(Strings.startOver) }
                }
                ConvertState.PREVIEW -> {}
            }

            if (spbFiles.isNotEmpty()) {
                Text(Strings.fileCount(spbFiles.size), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (fixState == ConvertState.DONE && fixLog.isNotEmpty()) {
            val fixedCount = fixLog.count { it.startsWith("OK") && it.contains("patched") }
            val errCount = fixLog.count { it.startsWith("ERROR") }
            Text(
                Strings.doneFixed(fixedCount, errCount),
                style = MaterialTheme.typography.titleSmall,
                color = if (errCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            fixLog.forEach { msg -> LogLine(msg) }
        }
      }
    }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}

// =============================================================================
// Duplicate Finder Tab
// =============================================================================

enum class ScanState { IDLE, SCANNING, DONE }

@Composable
fun DuplicateFinderTab() {
    var directory by remember { mutableStateOf<File?>(null) }
    var scanState by remember { mutableStateOf(ScanState.IDLE) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var totalScanned by remember { mutableStateOf(0) }
    var expandedGroups by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var songFolders by remember { mutableStateOf<List<File>>(emptyList()) }
    var keepFolder by remember { mutableStateOf<File?>(null) }
    var keepDropdownExpanded by remember { mutableStateOf(false) }
    var markedForDelete by remember { mutableStateOf<Set<String>>(emptySet()) } // canonical paths
    var deleteLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var compareGroup by remember { mutableStateOf<DuplicateGroup?>(null) }
    var compareLeft by remember { mutableStateOf(0) }
    var compareRight by remember { mutableStateOf(1) }
    var showHomoglyphPrompt by remember { mutableStateOf(false) }
    var pendingHomoglyphFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var matchByNumber by remember { mutableStateOf(false) }
    var matchByTitle by remember { mutableStateOf(true) }
    var threshold by remember { mutableStateOf(0.9f) }
    var filterMinSimilarity by remember { mutableStateOf(0f) }
    var filterMinFiles by remember { mutableStateOf(2) }
    var filterMaxFiles by remember { mutableStateOf(10) }
    var filterCategories by remember { mutableStateOf(setOf("Same song number", "Same title", "Similar lyrics")) }
    val scope = rememberCoroutineScope()

    fun startScan() {
        scanState = ScanState.SCANNING
        val useNumber = matchByNumber
        val useTitle = matchByTitle
        val useThreshold = threshold.toDouble()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val songs = DuplicateFinder.scanSongs(directory!!)
                val groups = DuplicateFinder.findDuplicates(directory!!, threshold = useThreshold, matchByNumber = useNumber, matchByTitle = useTitle)
                val folders = songs.map { it.file.parentFile }.distinct().sortedBy { it.absolutePath }
                Triple(songs.size, groups, folders)
            }
            totalScanned = result.first
            duplicateGroups = result.second
            songFolders = result.third
            scanState = ScanState.DONE
        }
    }

    val filteredGroups by remember(duplicateGroups, filterCategories, filterMinFiles, filterMaxFiles, filterMinSimilarity) {
        derivedStateOf {
            duplicateGroups.filter { group ->
                group.reason in filterCategories &&
                group.songs.size >= filterMinFiles &&
                group.songs.size <= filterMaxFiles &&
                (group.similarities.isEmpty() || run {
                    val avgSim = if (group.similarities.size > 1)
                        group.similarities.drop(1).average() else 1.0
                    avgSim >= filterMinSimilarity
                })
            }
        }
    }

    // When keep folder changes, auto-mark files outside it for deletion
    LaunchedEffect(keepFolder, filteredGroups) {
        if (keepFolder != null && filteredGroups.isNotEmpty()) {
            val autoMarked = DuplicateFinder.resolveDeletes(filteredGroups, keepFolder!!)
                .map { it.canonicalPath }.toSet()
            markedForDelete = autoMarked
        } else {
            markedForDelete = emptySet()
        }
    }

    val filesToDelete by remember(markedForDelete, filteredGroups) {
        derivedStateOf {
            val allSongFiles = filteredGroups.flatMap { it.songs }.map { it.file }
            allSongFiles.filter { it.canonicalPath in markedForDelete }
        }
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Left panel — controls
        val leftScrollState = rememberScrollState()
        Column(
            modifier = Modifier.width(360.dp).fillMaxHeight().verticalScroll(leftScrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                Strings.dupesTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                Strings.dupesDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Folder picker
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shape = ButtonShape, onClick = {
                    val dir = pickDirectory()
                    if (dir != null) {
                        directory = dir; scanState = ScanState.IDLE; duplicateGroups = emptyList()
                        expandedGroups = emptySet(); keepFolder = null; deleteLog = emptyList(); markedForDelete = emptySet()
                        songFolders = emptyList()
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(Strings.selectFolder)
                }
            }
            if (directory != null) {
                Text(directory!!.absolutePath, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Match options
            OptionToggleRow(
                checked = matchByNumber,
                label = Strings.matchByNumber,
                enabled = scanState != ScanState.SCANNING
            ) { matchByNumber = !matchByNumber }
            OptionToggleRow(
                checked = matchByTitle,
                label = Strings.matchByTitle,
                enabled = scanState != ScanState.SCANNING
            ) { matchByTitle = !matchByTitle }

            Column {
                Text(
                    Strings.similarityThreshold,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SlimSlider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    valueRange = 0.3f..1.0f,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = scanState != ScanState.SCANNING,
                    trailingLabel = "${(threshold * 100).toInt()}%"
                )
            }

            // Scan button
            when (scanState) {
                ScanState.IDLE -> {
                    Button(shape = ButtonShape, onClick = {
                        scanState = ScanState.SCANNING
                        // Check for homoglyphs first
                        scope.launch {
                            val hFiles = withContext(Dispatchers.IO) {
                                DuplicateFinder.findHomoglyphFiles(directory!!)
                            }
                            if (hFiles.isNotEmpty()) {
                                scanState = ScanState.IDLE
                                pendingHomoglyphFiles = hFiles
                                showHomoglyphPrompt = true
                            } else {
                                startScan()
                            }
                        }
                    }, enabled = directory != null, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.scanForDuplicates)
                    }
                }
                ScanState.SCANNING -> {
                    Button(shape = ButtonShape, enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text(Strings.scanning)
                    }
                }
                ScanState.DONE -> {
                    OutlinedButton(shape = ButtonShape, onClick = {
                        scanState = ScanState.IDLE; duplicateGroups = emptyList(); expandedGroups = emptySet()
                        keepFolder = null; deleteLog = emptyList(); songFolders = emptyList(); markedForDelete = emptySet()
                    }, modifier = Modifier.fillMaxWidth()) { Text(Strings.scanAgain) }
                }
            }

            if (scanState == ScanState.DONE) {
                HorizontalDivider()

                val dupeCount = duplicateGroups.sumOf { it.songs.size }
                if (duplicateGroups.isEmpty()) {
                    Text(Strings.noDupesFound(totalScanned),
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(Strings.groupSummary(duplicateGroups.size, dupeCount, totalScanned),
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)

                    // Keep folder
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            OutlinedButton(shape = ButtonShape, onClick = { keepDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Shield, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                Text(if (keepFolder != null) keepFolder!!.name else Strings.keepFolder)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = keepDropdownExpanded, onDismissRequest = { keepDropdownExpanded = false }) {
                                songFolders.forEach { folder ->
                                    val relativePath = directory?.let {
                                        folder.toRelativeString(it).ifEmpty { "." }
                                    } ?: folder.name
                                    DropdownMenuItem(
                                        text = { Text(relativePath) },
                                        onClick = { keepFolder = folder; deleteLog = emptyList(); markedForDelete = emptySet(); keepDropdownExpanded = false },
                                        leadingIcon = {
                                            Icon(if (folder == keepFolder) Icons.Default.CheckCircle else Icons.Default.Folder,
                                                null, Modifier.size(18.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (filesToDelete.isNotEmpty() && deleteLog.isEmpty()) {
                        Button(
                            shape = ButtonShape,
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.deleteNSelected(filesToDelete.size))
                        }
                    }
                    if (markedForDelete.isEmpty() && deleteLog.isEmpty()) {
                        Text(Strings.selectHint,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (deleteLog.isNotEmpty()) {
                        val deleted = deleteLog.count { it.startsWith("Deleted") }
                        val errors = deleteLog.count { it.startsWith("ERROR") }
                        Text(Strings.doneDeleted(deleted, errors),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Button(shape = ButtonShape, onClick = {
                            deleteLog = emptyList(); markedForDelete = emptySet()
                            expandedGroups = emptySet(); keepFolder = null
                            startScan()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(Strings.rescan)
                        }
                    }
                }

                // Homoglyph fix
                HorizontalDivider()
                var homoglyphFiles by remember { mutableStateOf<List<File>?>(null) }
                var homoglyphLog by remember { mutableStateOf<List<String>>(emptyList()) }
                OutlinedButton(shape = ButtonShape, onClick = {
                    scope.launch {
                        homoglyphFiles = withContext(Dispatchers.IO) {
                            DuplicateFinder.findHomoglyphFiles(directory!!)
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = directory != null) {
                    Icon(Icons.Default.TextFormat, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(Strings.findHomoglyphs)
                }
                if (homoglyphFiles != null && homoglyphFiles!!.isEmpty()) {
                    Text(Strings.noHomoglyphs,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (homoglyphFiles != null && homoglyphFiles!!.isNotEmpty() && homoglyphLog.isEmpty()) {
                    Text(Strings.filesWithHomoglyphs(homoglyphFiles!!.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    homoglyphFiles!!.take(5).forEach { f ->
                        Text(f.name, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                    if (homoglyphFiles!!.size > 5) {
                        Text(Strings.andNMore(homoglyphFiles!!.size - 5),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(shape = ButtonShape, onClick = {
                        scope.launch {
                            homoglyphLog = withContext(Dispatchers.IO) {
                                homoglyphFiles!!.map { f ->
                                    try {
                                        val count = DuplicateFinder.fixHomoglyphs(f)
                                        "Fixed $count chars: ${f.name}"
                                    } catch (e: Exception) { "ERROR: ${f.name} - ${e.message}" }
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Build, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.fixNFiles(homoglyphFiles!!.size))
                    }
                    Text(Strings.homoglyphFixNote,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (homoglyphLog.isNotEmpty()) {
                    val fixed = homoglyphLog.count { it.startsWith("Fixed") }
                    val errors = homoglyphLog.count { it.startsWith("ERROR") }
                    Text(Strings.doneFixed(fixed, errors),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }

                // Sanitize control characters
                HorizontalDivider()
                var sanitizeFiles by remember { mutableStateOf<List<File>?>(null) }
                var sanitizeLog by remember { mutableStateOf<List<String>>(emptyList()) }
                OutlinedButton(shape = ButtonShape, onClick = {
                    scope.launch {
                        sanitizeFiles = withContext(Dispatchers.IO) {
                            TextUtils.findFilesWithControlChars(directory!!)
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = directory != null) {
                    Icon(Icons.Default.CleaningServices, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(Strings.findControlChars)
                }
                if (sanitizeFiles != null && sanitizeFiles!!.isEmpty()) {
                    Text(Strings.noControlChars,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (sanitizeFiles != null && sanitizeFiles!!.isNotEmpty() && sanitizeLog.isEmpty()) {
                    Text(Strings.filesWithControlChars(sanitizeFiles!!.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    sanitizeFiles!!.take(5).forEach { f ->
                        Text(f.name, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                    if (sanitizeFiles!!.size > 5) {
                        Text(Strings.andNMore(sanitizeFiles!!.size - 5),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(shape = ButtonShape, onClick = {
                        scope.launch {
                            sanitizeLog = withContext(Dispatchers.IO) {
                                sanitizeFiles!!.map { f ->
                                    try {
                                        val changed = TextUtils.sanitizeFile(f)
                                        if (changed) "Fixed: ${f.name}" else "Unchanged: ${f.name}"
                                    } catch (e: Exception) { "ERROR: ${f.name} - ${e.message}" }
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Build, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.fixNFiles(sanitizeFiles!!.size))
                    }
                }
                if (sanitizeLog.isNotEmpty()) {
                    val fixed = sanitizeLog.count { it.startsWith("Fixed") }
                    val errors = sanitizeLog.count { it.startsWith("ERROR") }
                    Text(Strings.doneFixed(fixed, errors),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }

                // Filters
                HorizontalDivider()
                Text(Strings.filters, style = MaterialTheme.typography.labelMedium)

                val allCategories = listOf("Same song number", "Same title", "Similar lyrics")
                val categoryLabels = mapOf("Same song number" to Strings.catSameNumber, "Same title" to Strings.catSameTitle, "Similar lyrics" to Strings.catSimilarLyrics)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    allCategories.forEach { cat ->
                        FilterChip(
                            selected = cat in filterCategories,
                            onClick = {
                                filterCategories = if (cat in filterCategories)
                                    filterCategories - cat else filterCategories + cat
                            },
                            label = { Text(categoryLabels[cat] ?: cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Strings.minSim, style = MaterialTheme.typography.bodySmall)
                    SlimSlider(
                        value = filterMinSimilarity,
                        onValueChange = { filterMinSimilarity = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        trailingLabel = "${(filterMinSimilarity * 100).toInt()}%"
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Strings.filesPerGroup, style = MaterialTheme.typography.bodySmall)
                    ConverterTextField(value = filterMinFiles.toString(), onValueChange = { v -> v.filter { it.isDigit() }.toIntOrNull()?.let { if (it >= 2) filterMinFiles = it } }, modifier = Modifier.width(55.dp))
                    Text("-", style = MaterialTheme.typography.bodySmall)
                    ConverterTextField(value = filterMaxFiles.toString(), onValueChange = { v -> v.filter { it.isDigit() }.toIntOrNull()?.let { if (it >= 2) filterMaxFiles = it } }, modifier = Modifier.width(55.dp))
                }

                if (filterMinSimilarity > 0f || filterMinFiles > 2 || filterMaxFiles < 10 || filterCategories.size < 3) {
                    TextButton(shape = ButtonShape, onClick = {
                        filterMinSimilarity = 0f; filterMinFiles = 2; filterMaxFiles = 10
                        filterCategories = setOf("Same song number", "Same title", "Similar lyrics")
                    }) {
                        Icon(Icons.Default.Clear, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(Strings.clearFilters)
                    }
                }
            }
        }

        // Confirmation dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(Strings.deleteDupesTitle) },
                text = {
                    Column {
                        Text(Strings.permanentlyDelete(filesToDelete.size))
                        if (keepFolder != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(Strings.keepFolderPrefix(keepFolder!!.absolutePath),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.filesToDeleteLabel, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        filesToDelete.take(10).forEach { f ->
                            Text(f.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (filesToDelete.size > 10) {
                            Text(Strings.andNMore(filesToDelete.size - 10),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                confirmButton = {
                    Button(shape = ButtonShape, onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            deleteLog = withContext(Dispatchers.IO) {
                                filesToDelete.map { file ->
                                    try { file.delete(); "Deleted: ${file.absolutePath}" }
                                    catch (e: Exception) { "ERROR: ${file.name} - ${e.message}" }
                                }
                            }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(Strings.delete) }
                },
                dismissButton = { OutlinedButton(shape = ButtonShape, onClick = { showDeleteConfirm = false }) { Text(Strings.cancel) } }
            )
        }

        // Homoglyph prompt
        if (showHomoglyphPrompt) {
            AlertDialog(
                onDismissRequest = { showHomoglyphPrompt = false },
                title = { Text(Strings.homoglyphDialogTitle) },
                text = {
                    Column {
                        Text("${pendingHomoglyphFiles.size} ${Strings.homoglyphDialogDescSuffix}")
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.homoglyphDialogQuestion,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(Strings.homoglyphDialogNote,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(shape = ButtonShape, onClick = {
                        showHomoglyphPrompt = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                pendingHomoglyphFiles.forEach { DuplicateFinder.fixHomoglyphs(it) }
                            }
                            startScan()
                        }
                    }) { Text(Strings.fixAndScan) }
                },
                dismissButton = {
                    OutlinedButton(shape = ButtonShape, onClick = {
                        showHomoglyphPrompt = false
                        startScan()
                    }) { Text(Strings.skipAndScan) }
                }
            )
        }

        // Comparison window
        if (compareGroup != null) {
            val cg = compareGroup!!
            DialogWindow(
                onCloseRequest = { compareGroup = null },
                title = Strings.compareTitle(cg.songs.first().title),
                resizable = true,
                state = rememberDialogState(size = DpSize(900.dp, 700.dp))
            ) {
                ConverterTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            // Top bar: file selectors + delete buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left file selector
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(Strings.left, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    var leftExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(shape = ButtonShape, onClick = { leftExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                            val lf = cg.songs.getOrNull(compareLeft)?.file
                                            Text(if (lf != null) "${lf.parentFile.name}/${lf.name}" else "Select",
                                                maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                                        }
                                        DropdownMenu(expanded = leftExpanded, onDismissRequest = { leftExpanded = false }) {
                                            cg.songs.forEachIndexed { idx, song ->
                                                DropdownMenuItem(
                                                    text = { Text("${song.file.parentFile.name}/${song.file.name}", style = MaterialTheme.typography.bodySmall) },
                                                    onClick = { compareLeft = idx; leftExpanded = false },
                                                    enabled = idx != compareRight,
                                                    leadingIcon = {
                                                        if (idx == compareLeft) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    // Delete checkbox + open for left
                                    val leftFile = cg.songs.getOrNull(compareLeft)?.file
                                    val leftPath = leftFile?.canonicalPath
                                    if (leftPath != null) {
                                        val leftMarked = leftPath in markedForDelete
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                            Checkbox(
                                                checked = leftMarked,
                                                onCheckedChange = {
                                                    markedForDelete = if (leftMarked) markedForDelete - leftPath else markedForDelete + leftPath
                                                },
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(Strings.markForDeletion, style = MaterialTheme.typography.labelSmall,
                                                color = if (leftMarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.weight(1f))
                                            TextButton(shape = ButtonShape, onClick = { Desktop.getDesktop().open(leftFile) },
                                                modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                                                Text(Strings.open, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                                // Right file selector
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(Strings.right, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    var rightExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(shape = ButtonShape, onClick = { rightExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                            val rf = cg.songs.getOrNull(compareRight)?.file
                                            Text(if (rf != null) "${rf.parentFile.name}/${rf.name}" else "Select",
                                                maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                                        }
                                        DropdownMenu(expanded = rightExpanded, onDismissRequest = { rightExpanded = false }) {
                                            cg.songs.forEachIndexed { idx, song ->
                                                DropdownMenuItem(
                                                    text = { Text("${song.file.parentFile.name}/${song.file.name}", style = MaterialTheme.typography.bodySmall) },
                                                    onClick = { compareRight = idx; rightExpanded = false },
                                                    enabled = idx != compareLeft,
                                                    leadingIcon = {
                                                        if (idx == compareRight) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    // Delete checkbox + open for right
                                    val rightFile = cg.songs.getOrNull(compareRight)?.file
                                    val rightPath = rightFile?.canonicalPath
                                    if (rightPath != null) {
                                        val rightMarked = rightPath in markedForDelete
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                            Checkbox(
                                                checked = rightMarked,
                                                onCheckedChange = {
                                                    markedForDelete = if (rightMarked) markedForDelete - rightPath else markedForDelete + rightPath
                                                },
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(Strings.markForDeletion, style = MaterialTheme.typography.labelSmall,
                                                color = if (rightMarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.weight(1f))
                                            TextButton(shape = ButtonShape, onClick = { Desktop.getDesktop().open(rightFile) },
                                                modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                                                Text(Strings.open, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Section summary
                            val leftSong = cg.songs.getOrNull(compareLeft)
                            val rightSong = cg.songs.getOrNull(compareRight)
                            if (leftSong != null && rightSong != null) {
                                val allSections = (leftSong.sections + rightSong.sections).distinct()
                                val leftMissing = allSections - leftSong.sections.toSet()
                                val rightMissing = allSections - rightSong.sections.toSet()
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(Strings.sectionsLines(leftSong.sections.size, leftSong.lyricsText.lines().size),
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (leftMissing.isNotEmpty()) {
                                            Text(Strings.missingPrefix(leftMissing.joinToString(", ")),
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(Strings.sectionsLines(rightSong.sections.size, rightSong.lyricsText.lines().size),
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (rightMissing.isNotEmpty()) {
                                            Text(Strings.missingPrefix(rightMissing.joinToString(", ")),
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(4.dp))

                                // Side-by-side diff
                                val diffRows = computeSideBySide(leftSong.lyricsText.lines(), rightSong.lyricsText.lines())
                                val diffScrollV = rememberScrollState()
                                val monoStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                val addBg = Color(0xFF1B3A2A)
                                val delBg = Color(0xFF3A1B1B)
                                val emptyBg = MaterialTheme.colorScheme.surfaceContainerLow
                                val gutterColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                val dividerColor = MaterialTheme.colorScheme.outlineVariant

                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, dividerColor, RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                ) {
                                    Column(modifier = Modifier.verticalScroll(diffScrollV)) {
                                        diffRows.forEach { row ->
                                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                                // Left side
                                                val leftBg = when {
                                                    row.leftText == null -> emptyBg
                                                    row.leftType == DiffType.DEL -> delBg
                                                    else -> Color.Transparent
                                                }
                                                val leftColor = when (row.leftType) {
                                                    DiffType.DEL -> Color(0xFFE27E7E)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                                Row(
                                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                                        .background(leftBg).padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        row.leftNum?.toString()?.padStart(4) ?: "    ",
                                                        style = monoStyle, color = gutterColor,
                                                        modifier = Modifier.width(36.dp)
                                                    )
                                                    Text(
                                                        row.leftText ?: "",
                                                        style = monoStyle, color = leftColor,
                                                        softWrap = false
                                                    )
                                                }
                                                // Divider
                                                Box(Modifier.width(1.dp).fillMaxHeight().background(dividerColor))
                                                // Right side
                                                val rightBg = when {
                                                    row.rightText == null -> emptyBg
                                                    row.rightType == DiffType.ADD -> addBg
                                                    else -> Color.Transparent
                                                }
                                                val rightColor = when (row.rightType) {
                                                    DiffType.ADD -> Color(0xFF7EE2A8)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                                Row(
                                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                                        .background(rightBg).padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        row.rightNum?.toString()?.padStart(4) ?: "    ",
                                                        style = monoStyle, color = gutterColor,
                                                        modifier = Modifier.width(36.dp)
                                                    )
                                                    Text(
                                                        row.rightText ?: "",
                                                        style = monoStyle, color = rightColor,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right panel — results
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            if (scanState != ScanState.DONE) {
                EmptyStatePanel(Icons.Default.ContentCopy, Strings.dupesEmptyState)
            } else {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            Strings.showingGroups(filteredGroups.size, duplicateGroups.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        // Select duplicates in same folder
                        TextButton(shape = ButtonShape, onClick = {
                            val toMark = mutableSetOf<String>()
                            for (group in filteredGroups) {
                                // Group songs by folder, mark all but one per folder
                                val byFolder = group.songs.groupBy { it.file.parentFile.canonicalPath }
                                for ((_, songsInFolder) in byFolder) {
                                    if (songsInFolder.size > 1) {
                                        songsInFolder.drop(1).forEach { toMark.add(it.file.canonicalPath) }
                                    }
                                }
                            }
                            markedForDelete = markedForDelete + toMark
                        }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text(Strings.selectSameFolder, style = MaterialTheme.typography.labelSmall)
                        }
                        // Expand/Collapse all
                        TextButton(shape = ButtonShape, onClick = {
                            expandedGroups = if (expandedGroups.size >= filteredGroups.size)
                                emptySet() else filteredGroups.indices.toSet()
                        }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text(
                                if (expandedGroups.size >= filteredGroups.size) Strings.collapseAll else Strings.expandAll,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    val listState = rememberLazyListState()
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(start = 8.dp, top = 0.dp, bottom = 8.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (deleteLog.isNotEmpty()) {
                                items(deleteLog.size, key = { "log_$it" }) { idx -> LogLine(deleteLog[idx]) }
                            }

                            filteredGroups.forEachIndexed { groupIdx, group ->
                                item(key = "header_$groupIdx") {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        onClick = {
                                            expandedGroups = if (groupIdx in expandedGroups)
                                                expandedGroups - groupIdx else expandedGroups + groupIdx
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                if (groupIdx in expandedGroups) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                null, Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    Strings.groupHeader(groupIdx + 1, group.songs.first().title),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                val avgSim = if (group.similarities.size > 1)
                                                    group.similarities.drop(1).average() else 1.0
                                                Text(
                                                    Strings.groupDetail(group.songs.size, group.reason, (avgSim * 100).toInt()),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(shape = ButtonShape, onClick = { compareLeft = 0; compareRight = minOf(1, group.songs.size - 1); compareGroup = group }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.AutoMirrored.Filled.CompareArrows, "Compare", Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                if (groupIdx in expandedGroups) {
                                    items(group.songs.size, key = { "song_${groupIdx}_$it" }) { songIdx ->
                                        val song = group.songs[songIdx]
                                        val canonPath = song.file.canonicalPath
                                        val isMarked = canonPath in markedForDelete
                                        val isKept = keepFolder != null &&
                                                canonPath.startsWith(keepFolder!!.canonicalPath)
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(start = 28.dp).clickable {
                                                markedForDelete = if (isMarked) markedForDelete - canonPath
                                                    else markedForDelete + canonPath
                                            },
                                            colors = CardDefaults.cardColors(
                                                containerColor = when {
                                                    isMarked -> MaterialTheme.colorScheme.errorContainer
                                                    isKept -> MaterialTheme.colorScheme.primaryContainer
                                                    else -> MaterialTheme.colorScheme.surface
                                                }
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = isMarked,
                                                        onCheckedChange = {
                                                            markedForDelete = if (isMarked) markedForDelete - canonPath
                                                                else markedForDelete + canonPath
                                                        },
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(song.file.name, style = MaterialTheme.typography.bodyMedium)
                                                    if (isKept && !isMarked) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(Strings.labelKeep, style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    if (isMarked) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(Strings.labelDelete, style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                                val simPercent = if (group.similarities.size > songIdx)
                                                    "${(group.similarities[songIdx] * 100).toInt()}%" else ""
                                                Text(
                                                    Strings.titlePrefix(song.title) + if (simPercent.isNotEmpty()) " \u2022 $simPercent" else "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start = 26.dp, top = 2.dp)
                                                )
                                                // Show sections and missing verses
                                                if (song.sections.isNotEmpty()) {
                                                    val allSections = group.songs.flatMap { it.sections }.distinct()
                                                    val missing = allSections - song.sections.toSet()
                                                    Text(
                                                        Strings.sectionsPrefix(song.sections.joinToString(", ")),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(start = 26.dp, top = 2.dp)
                                                    )
                                                    if (missing.isNotEmpty()) {
                                                        Text(
                                                            Strings.missingPrefix(missing.joinToString(", ")),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.padding(start = 26.dp, top = 1.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    song.file.absolutePath,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start = 26.dp, top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(listState)
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Bulk Rename Tab
// =============================================================================

data class RenameEntry(val file: File, val newName: String, val conflict: Boolean)

@Composable
fun BulkRenameTab() {
    var directory by remember { mutableStateOf<File?>(null) }
    var stripNumbers by remember { mutableStateOf(true) }
    var renameToFirstVerse by remember { mutableStateOf(false) }
    var caseOption by remember { mutableStateOf("None") } // None, Title Case, lowercase, UPPERCASE
    var preview by remember { mutableStateOf<List<RenameEntry>>(emptyList()) }
    var logMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    var state by remember { mutableStateOf(ConvertState.SELECT) }
    var renameCompareFiles by remember { mutableStateOf<List<File>?>(null) }
    var renameCompareLeft by remember { mutableStateOf(0) }
    var renameCompareRight by remember { mutableStateOf(1) }
    var renameMarkedForDelete by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxHeight().widthIn(max = CONTENT_MAX_WIDTH).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            Strings.renameTitle,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            Strings.renameDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shape = ButtonShape, onClick = {
                val dir = pickDirectory()
                if (dir != null) {
                    directory = dir; state = ConvertState.SELECT; preview = emptyList(); logMessages = emptyList()
                }
            }) {
                Icon(Icons.Default.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(Strings.selectFolder)
            }
            if (directory != null) {
                Text(directory!!.absolutePath, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        OptionToggleRow(
            checked = stripNumbers,
            label = Strings.stripNumbers,
            enabled = state != ConvertState.CONVERTING
        ) { stripNumbers = !stripNumbers }
        OptionToggleRow(
            checked = renameToFirstVerse,
            label = Strings.renameFirstVerse,
            enabled = state != ConvertState.CONVERTING
        ) { renameToFirstVerse = !renameToFirstVerse }

        Column {
            Text(
                Strings.letterCase,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val caseOptions = listOf(
                    "None" to Strings.caseNone,
                    "Sentence case" to Strings.caseSentence,
                    "Title Case" to Strings.caseTitle,
                    "lowercase" to Strings.caseLower,
                    "UPPERCASE" to Strings.caseUpper
                )
                caseOptions.forEach { (id, label) ->
                    FilterChip(
                        selected = caseOption == id,
                        onClick = { caseOption = id },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        enabled = state != ConvertState.CONVERTING
                    )
                }
            }
        }

        RenameExampleCard(
            before = Strings.renameExampleBefore,
            after = renameExample(stripNumbers, renameToFirstVerse, caseOption)
        )

        // Live-update preview when options change
        LaunchedEffect(stripNumbers, renameToFirstVerse, caseOption) {
            if (state == ConvertState.PREVIEW && directory != null) {
                preview = withContext(Dispatchers.IO) {
                    buildRenamePreview(directory!!, stripNumbers, renameToFirstVerse, caseOption)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                ConvertState.SELECT -> {
                    OutlinedButton(shape = ButtonShape, onClick = {
                        preview = buildRenamePreview(directory!!, stripNumbers, renameToFirstVerse, caseOption)
                        state = ConvertState.PREVIEW
                    }, enabled = directory != null) {
                        Icon(Icons.Default.Preview, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(Strings.preview)
                    }
                }
                ConvertState.PREVIEW -> {
                    val renameCount = preview.count { it.file.name != it.newName }
                    Button(shape = ButtonShape, onClick = {
                        state = ConvertState.CONVERTING
                        scope.launch {
                            logMessages = withContext(Dispatchers.IO) {
                                preview.filter { it.file.name != it.newName }.map { entry ->
                                    try {
                                        val target = File(entry.file.parentFile, entry.newName)
                                        val isCaseOnly = entry.newName.equals(entry.file.name, ignoreCase = true)
                                        if (isCaseOnly) {
                                            // Windows: case-only rename needs a temp intermediate
                                            val temp = File(entry.file.parentFile, entry.file.name + ".tmp_rename")
                                            entry.file.renameTo(temp)
                                            temp.renameTo(target)
                                            "OK: ${entry.file.name} → ${entry.newName}"
                                        } else if (target.exists()) {
                                            "SKIP: ${entry.file.name} → ${entry.newName} (target exists)"
                                        } else {
                                            entry.file.renameTo(target)
                                            "OK: ${entry.file.name} → ${entry.newName}"
                                        }
                                    } catch (e: Exception) { "ERROR: ${entry.file.name} - ${e.message}" }
                                }
                            }
                            state = ConvertState.DONE
                        }
                    }, enabled = renameCount > 0) {
                        Icon(Icons.Default.DriveFileRenameOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.renameNFiles(renameCount))
                    }
                    OutlinedButton(shape = ButtonShape, onClick = { state = ConvertState.SELECT; preview = emptyList() }) { Text(Strings.back) }
                }
                ConvertState.CONVERTING -> {
                    Button(shape = ButtonShape, enabled = false, onClick = {}) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text(Strings.renaming)
                    }
                }
                ConvertState.DONE -> {
                    OutlinedButton(shape = ButtonShape, onClick = {
                        state = ConvertState.SELECT; preview = emptyList(); logMessages = emptyList()
                    }) { Text(Strings.startOver) }
                }
            }
        }

        when (state) {
            ConvertState.PREVIEW -> {
                val renameCount = preview.count { it.file.name != it.newName }
                val conflicts = preview.count { it.conflict }
                Text(Strings.renameSummary(renameCount, preview.size - renameCount) +
                    if (conflicts > 0) ", ${Strings.conflictsSummary(conflicts)}" else "",
                    style = MaterialTheme.typography.titleSmall)
                // Delete marked files button
                if (renameMarkedForDelete.isNotEmpty()) {
                    var showDeleteConfirm by remember { mutableStateOf(false) }
                    Button(
                        shape = ButtonShape,
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.deleteNMarked(renameMarkedForDelete.size))
                    }
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text(Strings.deleteFilesTitle) },
                            text = { Text(Strings.permanentlyDeleteShort(renameMarkedForDelete.size)) },
                            confirmButton = {
                                Button(shape = ButtonShape, onClick = {
                                    showDeleteConfirm = false
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            renameMarkedForDelete.forEach { path -> File(path).delete() }
                                        }
                                        renameMarkedForDelete = emptySet()
                                        preview = buildRenamePreview(directory!!, stripNumbers, renameToFirstVerse, caseOption)
                                    }
                                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(Strings.delete) }
                            },
                            dismissButton = { OutlinedButton(shape = ButtonShape, onClick = { showDeleteConfirm = false }) { Text(Strings.cancel) } }
                        )
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(preview.filter { it.file.name != it.newName }) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        entry.file.canonicalPath in renameMarkedForDelete -> MaterialTheme.colorScheme.errorContainer
                                        entry.conflict -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.surfaceContainer
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(entry.file.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp).padding(horizontal = 4.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Text(entry.newName, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                    // Find all files that rename to the same name in the same folder
                                    val sameNameFiles = preview.filter { other ->
                                        other.file !== entry.file &&
                                        other.newName.equals(entry.newName, ignoreCase = true) &&
                                        other.file.parentFile.canonicalPath == entry.file.parentFile.canonicalPath
                                    }
                                    if (sameNameFiles.isNotEmpty()) {
                                        IconButton(shape = ButtonShape, onClick = {
                                            val allFiles = listOf(entry.file) + sameNameFiles.map { it.file }
                                            renameCompareFiles = allFiles
                                            renameCompareLeft = 0
                                            renameCompareRight = 1
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.CompareArrows, "Compare", Modifier.size(16.dp))
                                        }
                                    }
                                    if (entry.conflict) {
                                        Text(Strings.conflict, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                }
            }
            ConvertState.DONE -> {
                val ok = logMessages.count { it.startsWith("OK") }
                val skipped = logMessages.count { it.startsWith("SKIP") }
                val err = logMessages.count { it.startsWith("ERROR") }
                Text(Strings.doneRenamed(ok, skipped, err), style = MaterialTheme.typography.titleSmall,
                    color = if (err > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(logMessages) { msg -> LogLine(msg) }
                    }
                }
            }
            else -> {}
        }
    }

    // Compare dialog for rename conflicts
    if (renameCompareFiles != null && renameCompareFiles!!.size >= 2) {
        val cFiles = renameCompareFiles!!
        val cSongs = remember(cFiles) {
            cFiles.map { f ->
                try { DuplicateFinder.readFileWithFallback(f) } catch (_: Exception) { "" }
            }
        }
        DialogWindow(
            onCloseRequest = { renameCompareFiles = null },
            title = Strings.compareTitle(cFiles.first().nameWithoutExtension),
            resizable = true,
            state = rememberDialogState(size = DpSize(900.dp, 700.dp))
        ) {
            ConverterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        // File selectors + delete checkboxes
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Left
                            Column(modifier = Modifier.weight(1f)) {
                                Text(Strings.left, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                var leftExp by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(shape = ButtonShape, onClick = { leftExp = true }, modifier = Modifier.fillMaxWidth()) {
                                        val lf = cFiles.getOrNull(renameCompareLeft)
                                        Text(if (lf != null) "${lf.parentFile.name}/${lf.name}" else "", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                                    }
                                    DropdownMenu(expanded = leftExp, onDismissRequest = { leftExp = false }) {
                                        cFiles.forEachIndexed { idx, f ->
                                            DropdownMenuItem(text = { Text("${f.parentFile.name}/${f.name}", style = MaterialTheme.typography.bodySmall) },
                                                onClick = { renameCompareLeft = idx; leftExp = false }, enabled = idx != renameCompareRight)
                                        }
                                    }
                                }
                                val leftFileR = cFiles.getOrNull(renameCompareLeft)
                                val leftPath = leftFileR?.canonicalPath
                                if (leftPath != null) {
                                    val leftMarked = leftPath in renameMarkedForDelete
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                        Checkbox(checked = leftMarked, onCheckedChange = {
                                            renameMarkedForDelete = if (leftMarked) renameMarkedForDelete - leftPath else renameMarkedForDelete + leftPath
                                        }, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(Strings.markForDeletion, style = MaterialTheme.typography.labelSmall,
                                            color = if (leftMarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.weight(1f))
                                        TextButton(shape = ButtonShape, onClick = { Desktop.getDesktop().open(leftFileR) },
                                            modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                                            Text(Strings.open, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            // Right
                            Column(modifier = Modifier.weight(1f)) {
                                Text(Strings.right, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                var rightExp by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(shape = ButtonShape, onClick = { rightExp = true }, modifier = Modifier.fillMaxWidth()) {
                                        val rf = cFiles.getOrNull(renameCompareRight)
                                        Text(if (rf != null) "${rf.parentFile.name}/${rf.name}" else "", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                                    }
                                    DropdownMenu(expanded = rightExp, onDismissRequest = { rightExp = false }) {
                                        cFiles.forEachIndexed { idx, f ->
                                            DropdownMenuItem(text = { Text("${f.parentFile.name}/${f.name}", style = MaterialTheme.typography.bodySmall) },
                                                onClick = { renameCompareRight = idx; rightExp = false }, enabled = idx != renameCompareLeft)
                                        }
                                    }
                                }
                                val rightFileR = cFiles.getOrNull(renameCompareRight)
                                val rightPath = rightFileR?.canonicalPath
                                if (rightPath != null) {
                                    val rightMarked = rightPath in renameMarkedForDelete
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                        Checkbox(checked = rightMarked, onCheckedChange = {
                                            renameMarkedForDelete = if (rightMarked) renameMarkedForDelete - rightPath else renameMarkedForDelete + rightPath
                                        }, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(Strings.markForDeletion, style = MaterialTheme.typography.labelSmall,
                                            color = if (rightMarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.weight(1f))
                                        TextButton(shape = ButtonShape, onClick = { Desktop.getDesktop().open(rightFileR) },
                                            modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                                            Text(Strings.open, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))

                        // Side-by-side diff
                        val leftContent = cSongs.getOrNull(renameCompareLeft) ?: ""
                        val rightContent = cSongs.getOrNull(renameCompareRight) ?: ""
                        val diffRows = computeSideBySide(leftContent.lines(), rightContent.lines())
                        val diffScrollV = rememberScrollState()
                        val monoStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        val addBg = Color(0xFF1B3A2A)
                        val delBg = Color(0xFF3A1B1B)
                        val emptyBg = MaterialTheme.colorScheme.surfaceContainerLow
                        val gutterColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        val dividerColor = MaterialTheme.colorScheme.outlineVariant

                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, dividerColor, RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        ) {
                            Column(modifier = Modifier.verticalScroll(diffScrollV)) {
                                diffRows.forEach { row ->
                                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                        val leftBgC = when {
                                            row.leftText == null -> emptyBg
                                            row.leftType == DiffType.DEL -> delBg
                                            else -> Color.Transparent
                                        }
                                        val leftColor = if (row.leftType == DiffType.DEL) Color(0xFFE27E7E) else MaterialTheme.colorScheme.onSurface
                                        Row(modifier = Modifier.weight(1f).fillMaxHeight().background(leftBgC).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                            Text(row.leftNum?.toString()?.padStart(4) ?: "    ", style = monoStyle, color = gutterColor, modifier = Modifier.width(36.dp))
                                            Text(row.leftText ?: "", style = monoStyle, color = leftColor, softWrap = false)
                                        }
                                        Box(Modifier.width(1.dp).fillMaxHeight().background(dividerColor))
                                        val rightBgC = when {
                                            row.rightText == null -> emptyBg
                                            row.rightType == DiffType.ADD -> addBg
                                            else -> Color.Transparent
                                        }
                                        val rightColor = if (row.rightType == DiffType.ADD) Color(0xFF7EE2A8) else MaterialTheme.colorScheme.onSurface
                                        Row(modifier = Modifier.weight(1f).fillMaxHeight().background(rightBgC).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                            Text(row.rightNum?.toString()?.padStart(4) ?: "    ", style = monoStyle, color = gutterColor, modifier = Modifier.width(36.dp))
                                            Text(row.rightText ?: "", style = monoStyle, color = rightColor, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val leadingNumberRegex = Regex("""^\d+\s*-\s*""")
private val verseHeaderRegex = Regex("""^\[.+\d.*\]$""", RegexOption.IGNORE_CASE)
private val invalidFilenameChars = Regex("""[\\/:*?"<>|]""")

/** The worked example on the Rename tab, run through the options currently ticked. */
private fun renameExample(stripNumbers: Boolean, renameToFirstVerse: Boolean, caseOption: String): String {
    var base = Strings.renameExampleBefore.removeSuffix(".song")
    if (stripNumbers) base = leadingNumberRegex.replace(base, "")
    if (renameToFirstVerse) base = Strings.renameExampleFirstLine
    if (caseOption != "None") base = applyCase(base, caseOption)
    return "$base.song"
}

private fun applyCase(name: String, caseOption: String): String = when (caseOption) {
    "Sentence case" -> name.lowercase().replaceFirstChar { it.titlecase() }
    "Title Case" -> name.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    "lowercase" -> name.lowercase()
    "UPPERCASE" -> name.uppercase()
    else -> name
}

private fun buildRenamePreview(directory: File, stripNumbers: Boolean, renameToFirstVerse: Boolean, caseOption: String = "None"): List<RenameEntry> {
    val files = directory.walkTopDown()
        .filter { it.isFile && it.extension.equals("song", ignoreCase = true) }
        .sortedBy { it.absolutePath }
        .toList()
    if (files.isEmpty()) return emptyList()

    // Track used names per parent folder to detect conflicts within each directory
    val usedNamesPerFolder = mutableMapOf<File, MutableSet<String>>()
    files.forEach { f ->
        usedNamesPerFolder.getOrPut(f.parentFile) { mutableSetOf() }.add(f.name.lowercase())
    }

    return files.map { file ->
        var newBase = file.nameWithoutExtension

        if (stripNumbers) {
            newBase = leadingNumberRegex.replace(newBase, "")
        }
        if (renameToFirstVerse) {
            val firstLine = extractFirstVerseLine(file)
            if (firstLine != null) {
                newBase = sanitizeFilename(firstLine)
            }
        }
        if (caseOption != "None") {
            newBase = applyCase(newBase, caseOption)
        }

        val newName = "$newBase.song"
        val folderNames = usedNamesPerFolder.getOrPut(file.parentFile) { mutableSetOf() }
        val isCaseOnlyChange = newName.equals(file.name, ignoreCase = true) && newName != file.name
        val conflict = newName != file.name && !isCaseOnlyChange && (File(file.parentFile, newName).exists() ||
            newName.lowercase() in folderNames && newName.lowercase() != file.name.lowercase())
        folderNames.add(newName.lowercase())
        RenameEntry(file, newName, conflict)
    }
}

private fun extractFirstVerseLine(file: File): String? {
    val content = DuplicateFinder.readFileWithFallback(file)
    val lines = content.lines()
    var frontmatterDone = false
    var inFrontmatter = false
    var foundPrimary = false
    var foundVerse = false

    for (line in lines) {
        val trimmed = line.trim()
        if (!frontmatterDone) {
            if (trimmed == "---") {
                inFrontmatter = !inFrontmatter
                if (!inFrontmatter) frontmatterDone = true
            }
            continue
        }
        if (trimmed.equals("[Primary]", ignoreCase = true)) { foundPrimary = true; continue }
        if (trimmed.equals("[Secondary]", ignoreCase = true)) break
        if (foundPrimary && trimmed.startsWith("[") && trimmed.endsWith("]")) {
            if (verseHeaderRegex.matches(trimmed)) { foundVerse = true; continue }
            if (foundVerse) break // hit next non-verse section after finding a verse
            continue
        }
        if (foundVerse && trimmed.isNotEmpty()) {
            return trimmed
        }
    }
    return null
}

private fun sanitizeFilename(text: String): String {
    return invalidFilenameChars.replace(text, "").trim().take(100)
}

// =============================================================================
// Shared UI components
// =============================================================================

data class PreviewItem(
    val inputName: String,
    val inputPath: String,
    val outputName: String,
    val outputPath: String,
    val details: String,
    val willOverwrite: Boolean
)

data class SpsPreviewData(
    val songbookName: String,
    val songCount: Int,
    val outputFolder: String,
    val folderExists: Boolean,
    val sampleTitles: List<String>,
    val error: String? = null
)

@Composable
private fun PreviewRow(item: PreviewItem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(item.inputName, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(item.outputName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (item.details.isNotBlank()) {
                Text(item.details, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
            }
            if (item.willOverwrite) {
                Text(Strings.outputOverwrite, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
            }
            Text(item.outputPath, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
        }
    }
}

@Composable
private fun LogLine(msg: String) {
    val color = if (msg.startsWith("ERROR")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Text(msg, style = MaterialTheme.typography.bodySmall, color = color)
}

// =============================================================================
// Preview builders
// =============================================================================


/** Words the converter layer's structured preview data, which carries no strings of its own. */
private fun describePreview(info: SongPreviewInfo): String {
    val parts = mutableListOf<String>()
    if (info.title.isNotBlank()) parts.add("\"${info.title}\"")
    if (info.sectionCount > 0) parts.add(Strings.sectionCount(info.sectionCount))
    if (info.songCount > 0) parts.add(Strings.songCount(info.songCount))
    if (info.verseOrder.isNotEmpty()) parts.add(Strings.verseOrderPrefix(info.verseOrder.joinToString(", ")))
    return parts.joinToString(" | ")
}

private fun buildFormatPreview(
    format: SongFormatConverter,
    files: List<File>,
    outputDir: File?
): List<PreviewItem> {
    return files.map { file ->
        val outDir = outputDir ?: file.parentFile
        val outFile = File(outDir, format.outputNameFor(file))
        val details = try {
            describePreview(format.describe(file))
        } catch (e: Exception) {
            Strings.parseError(e.message.orEmpty())
        }
        PreviewItem(file.name, file.absolutePath, outFile.name, outFile.absolutePath, details, outFile.exists())
    }
}

private fun buildSpsPreview(spsFile: File, outputDir: File): SpsPreviewData {
    return try {
        val result = SpsToSongConverter.parse(spsFile)
        val folderName = SpsToSongConverter.getTargetFolderName(spsFile)
        val targetFolder = File(outputDir, folderName)
        val titles = result.songs.map { "${it.number.padStart(4, '0')} - ${it.title}" }
        SpsPreviewData(result.songbookName, result.songs.size, targetFolder.absolutePath, targetFolder.exists(), titles)
    } catch (e: Exception) {
        SpsPreviewData("", 0, "", false, emptyList(), error = e.message)
    }
}


private fun buildBiblePreview(files: List<File>, outputDir: File?): List<PreviewItem> {
    return files.map { file ->
        val outDir = outputDir ?: file.parentFile
        val outFile = File(outDir, file.nameWithoutExtension + ".spb")
        val details = try {
            val bible = XmlToSpbConverter.parse(file)
            val parts = mutableListOf<String>()
            parts.add("\"${bible.name}\"")
            parts.add("${bible.books.size} book(s)")
            val totalVerses = bible.books.sumOf { b -> b.chapters.sumOf { c -> c.verses.size } }
            parts.add("$totalVerses verses")
            if (bible.language != null) parts.add("lang: ${bible.language}")
            parts.joinToString(" | ")
        } catch (e: Exception) { "Parse error: ${e.message}" }
        PreviewItem(file.name, file.absolutePath, outFile.name, outFile.absolutePath, details, outFile.exists())
    }
}

// =============================================================================
// File pickers
// =============================================================================

private val defaultDir: File = File(System.getProperty("user.home"), "Downloads")

/** File-chooser filter label: the product name and its extension, both untranslated by nature. */
private fun pickerLabel(source: SongSource): String = "${source.name} (${source.ext})"

private fun pickFiles(description: String, vararg extensions: String, multiSelection: Boolean): List<File> {
    val chooser = JFileChooser(defaultDir).apply {
        fileFilter = FileNameExtensionFilter(description, *extensions)
        isMultiSelectionEnabled = multiSelection
        dialogTitle = Strings.selectDialog(description)
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        if (multiSelection) chooser.selectedFiles.toList() else listOfNotNull(chooser.selectedFile)
    } else emptyList()
}

private fun pickDirectory(): File? {
    val chooser = JFileChooser(defaultDir).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = Strings.selectFolder
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun findXmlFilesRecursive(dir: File): List<File> = findFilesRecursive(dir, "xml")

private fun findFilesRecursive(dir: File, extension: String): List<File> =
    dir.walkTopDown()
        .filter { it.isFile && it.extension.equals(extension, ignoreCase = true) }
        .sortedBy { it.absolutePath }
        .toList()

/** How many of the chosen files the "source files" card lists before it stops. */
private const val FILE_LIST_LIMIT = 200

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.ROOT, "%d KB", bytes / 1024)
    else -> String.format(Locale.ROOT, "%d B", bytes)
}

// =============================================================================
// Diff engine
// =============================================================================

enum class DiffType { SAME, ADD, DEL }

data class SideBySideRow(
    val leftNum: Int? = null,
    val leftText: String? = null,
    val leftType: DiffType = DiffType.SAME,
    val rightNum: Int? = null,
    val rightText: String? = null,
    val rightType: DiffType = DiffType.SAME
)

/** LCS-based side-by-side diff with aligned matching lines. */
private fun computeSideBySide(leftLines: List<String>, rightLines: List<String>): List<SideBySideRow> {
    val n = leftLines.size
    val m = rightLines.size

    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in 1..n) {
        for (j in 1..m) {
            dp[i][j] = if (leftLines[i - 1] == rightLines[j - 1]) dp[i - 1][j - 1] + 1
            else maxOf(dp[i - 1][j], dp[i][j - 1])
        }
    }

    // Backtrack to get edit operations
    data class Op(val type: DiffType, val text: String, val li: Int, val ri: Int)
    val ops = mutableListOf<Op>()
    var i = n; var j = m
    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 && leftLines[i - 1] == rightLines[j - 1] -> {
                ops.add(Op(DiffType.SAME, leftLines[i - 1], i, j)); i--; j--
            }
            j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                ops.add(Op(DiffType.ADD, rightLines[j - 1], 0, j)); j--
            }
            else -> {
                ops.add(Op(DiffType.DEL, leftLines[i - 1], i, 0)); i--
            }
        }
    }
    ops.reverse()

    // Convert to side-by-side rows, pairing adjacent DEL+ADD as modifications
    val rows = mutableListOf<SideBySideRow>()
    var idx = 0
    while (idx < ops.size) {
        val op = ops[idx]
        when (op.type) {
            DiffType.SAME -> {
                rows.add(SideBySideRow(op.li, op.text, DiffType.SAME, op.ri, op.text, DiffType.SAME))
                idx++
            }
            DiffType.DEL -> {
                // Collect consecutive DELs and ADDs to pair them
                val dels = mutableListOf<Op>()
                while (idx < ops.size && ops[idx].type == DiffType.DEL) { dels.add(ops[idx]); idx++ }
                val adds = mutableListOf<Op>()
                while (idx < ops.size && ops[idx].type == DiffType.ADD) { adds.add(ops[idx]); idx++ }
                val maxLen = maxOf(dels.size, adds.size)
                for (k in 0 until maxLen) {
                    val d = dels.getOrNull(k)
                    val a = adds.getOrNull(k)
                    rows.add(SideBySideRow(
                        leftNum = d?.li, leftText = d?.text, leftType = if (d != null) DiffType.DEL else DiffType.SAME,
                        rightNum = a?.ri, rightText = a?.text, rightType = if (a != null) DiffType.ADD else DiffType.SAME
                    ))
                }
            }
            DiffType.ADD -> {
                rows.add(SideBySideRow(rightNum = op.ri, rightText = op.text, rightType = DiffType.ADD))
                idx++
            }
        }
    }
    return rows
}
