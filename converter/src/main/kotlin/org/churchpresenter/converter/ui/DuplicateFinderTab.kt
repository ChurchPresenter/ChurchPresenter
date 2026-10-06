package org.churchpresenter.converter.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TextFormat
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import org.churchpresenter.theme.components.RaisedFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.converter.library.DuplicateFinder
import org.churchpresenter.converter.library.TextUtils
import org.churchpresenter.converter.library.DuplicateGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope

// =============================================================================
// Duplicate Finder Tab
// =============================================================================

enum class ScanState { IDLE, SCANNING, DONE }

@Composable
fun DuplicateFinderTab() {
    val scope = rememberCoroutineScope()
    val state = remember { DuplicateFinderState(scope) }
    with(state) {
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

        Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Left panel — controls
            DuplicateControlsPanel()

            DuplicateDeleteConfirmDialog()

            HomoglyphPromptDialog()

            DuplicateCompareWindow()

            // Right panel — results
            DuplicateResultsPanel(Modifier.weight(1f))
        }
    }
}

/** What the Duplicate Finder remembers while it is open, and the scan it runs. */
@Stable
internal class DuplicateFinderState(val scope: CoroutineScope) {
    var directory by mutableStateOf<File?>(null)
    var scanState by mutableStateOf(ScanState.IDLE)
    var duplicateGroups by mutableStateOf<List<DuplicateGroup>>(emptyList())
    var totalScanned by mutableStateOf(0)
    var expandedGroups by mutableStateOf<Set<Int>>(emptySet())
    var songFolders by mutableStateOf<List<File>>(emptyList())
    var keepFolder by mutableStateOf<File?>(null)
    var keepDropdownExpanded by mutableStateOf(false)
    var markedForDelete by mutableStateOf<Set<String>>(emptySet()) // canonical paths
    var deleteLog by mutableStateOf<List<String>>(emptyList())
    var showDeleteConfirm by mutableStateOf(false)
    var compareGroup by mutableStateOf<DuplicateGroup?>(null)
    var compareLeft by mutableStateOf(0)
    var compareRight by mutableStateOf(1)
    var showHomoglyphPrompt by mutableStateOf(false)
    var pendingHomoglyphFiles by mutableStateOf<List<File>>(emptyList())
    var matchByNumber by mutableStateOf(false)
    var matchByTitle by mutableStateOf(true)
    var threshold by mutableStateOf(0.9f)
    var filterMinSimilarity by mutableStateOf(0f)
    var filterMinFiles by mutableStateOf(2)
    var filterMaxFiles by mutableStateOf(10)
    var filterCategories by mutableStateOf(setOf("Same song number", "Same title", "Similar lyrics"))

    val filteredGroups by derivedStateOf {
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

    val filesToDelete by derivedStateOf {
        val allSongFiles = filteredGroups.flatMap { it.songs }.map { it.file }
        allSongFiles.filter { it.canonicalPath in markedForDelete }
    }

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
}

@Composable
private fun DuplicateFinderState.DuplicateControlsPanel() {
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
        DuplicateScanControls()

        if (scanState == ScanState.DONE) {
            DuplicateScanSummary()

            DuplicateHomoglyphSection()

            DuplicateSanitizeSection()

            DuplicateFilters()
        }
    }
}

/** The folder, the match options, the threshold and the scan button. */
@Composable
private fun DuplicateFinderState.DuplicateScanControls() {
    // Folder picker
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RaisedButton(shape = ButtonShape, onClick = {
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
            RaisedButton(shape = ButtonShape, onClick = {
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
            RaisedButton(shape = ButtonShape, enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp)); Text(Strings.scanning)
            }
        }
        ScanState.DONE -> {
            KeyButton(shape = ButtonShape, onClick = {
                scanState = ScanState.IDLE; duplicateGroups = emptyList(); expandedGroups = emptySet()
                keepFolder = null; deleteLog = emptyList(); songFolders = emptyList(); markedForDelete = emptySet()
            }, modifier = Modifier.fillMaxWidth()) { Text(Strings.scanAgain) }
        }
    }
}

/** What the scan found, the folder to keep, and deleting the rest. */
@Composable
private fun DuplicateFinderState.DuplicateScanSummary() {
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
                KeyButton(shape = ButtonShape, onClick = { keepDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
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
            RaisedButton(
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
            RaisedButton(shape = ButtonShape, onClick = {
                deleteLog = emptyList(); markedForDelete = emptySet()
                expandedGroups = emptySet(); keepFolder = null
                startScan()
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(Strings.rescan)
            }
        }
    }
}

@Composable
private fun DuplicateFinderState.DuplicateHomoglyphSection() {
    // Homoglyph fix
    HorizontalDivider()
    var homoglyphFiles by remember { mutableStateOf<List<File>?>(null) }
    var homoglyphLog by remember { mutableStateOf<List<String>>(emptyList()) }
    KeyButton(shape = ButtonShape, onClick = {
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
        RaisedButton(shape = ButtonShape, onClick = {
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
}

@Composable
private fun DuplicateFinderState.DuplicateSanitizeSection() {
    // Sanitize control characters
    HorizontalDivider()
    var sanitizeFiles by remember { mutableStateOf<List<File>?>(null) }
    var sanitizeLog by remember { mutableStateOf<List<String>>(emptyList()) }
    KeyButton(shape = ButtonShape, onClick = {
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
        RaisedButton(shape = ButtonShape, onClick = {
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
}

@Composable
private fun DuplicateFinderState.DuplicateFilters() {
    // Filters
    HorizontalDivider()
    Text(Strings.filters, style = MaterialTheme.typography.labelMedium)

    val allCategories = listOf("Same song number", "Same title", "Similar lyrics")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        allCategories.forEach { cat ->
            RaisedFilterChip(
                selected = cat in filterCategories,
                onClick = {
                    filterCategories = if (cat in filterCategories)
                        filterCategories - cat else filterCategories + cat
                },
                label = { Text(Strings.duplicateReason(cat), style = MaterialTheme.typography.labelSmall) }
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
        GhostButton(shape = ButtonShape, onClick = {
            filterMinSimilarity = 0f; filterMinFiles = 2; filterMaxFiles = 10
            filterCategories = setOf("Same song number", "Same title", "Similar lyrics")
        }) {
            Icon(Icons.Default.Clear, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
            Text(Strings.clearFilters)
        }
    }
}
