package org.churchpresenter.converter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.AlertDialog
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import org.churchpresenter.theme.components.RaisedCheckbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import org.churchpresenter.theme.components.RaisedFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.converter.library.DuplicateFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

// =============================================================================
// Bulk Rename Tab
// =============================================================================

data class RenameEntry(val file: File, val newName: String, val conflict: Boolean)

@Composable
fun BulkRenameTab() {
    val pickers = LocalConverterPickers.current
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
            RaisedButton(shape = ButtonShape, onClick = {
                val dir = pickers.directory()
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
                    RaisedFilterChip(
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
                    KeyButton(shape = ButtonShape, onClick = {
                        preview = buildRenamePreview(directory!!, stripNumbers, renameToFirstVerse, caseOption)
                        state = ConvertState.PREVIEW
                    }, enabled = directory != null) {
                        Icon(Icons.Default.Preview, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(Strings.preview)
                    }
                }
                ConvertState.PREVIEW -> {
                    val renameCount = preview.count { it.file.name != it.newName }
                    RaisedButton(shape = ButtonShape, onClick = {
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
                    KeyButton(shape = ButtonShape, onClick = { state = ConvertState.SELECT; preview = emptyList() }) { Text(Strings.back) }
                }
                ConvertState.CONVERTING -> {
                    RaisedButton(shape = ButtonShape, enabled = false, onClick = {}) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text(Strings.renaming)
                    }
                }
                ConvertState.DONE -> {
                    KeyButton(shape = ButtonShape, onClick = {
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
                    RaisedButton(
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
                                RaisedButton(shape = ButtonShape, onClick = {
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
                            dismissButton = { KeyButton(shape = ButtonShape, onClick = { showDeleteConfirm = false }) { Text(Strings.cancel) } }
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
                                        KeyIconButton(shape = ButtonShape, onClick = {
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
                                    KeyButton(shape = ButtonShape, onClick = { leftExp = true }, modifier = Modifier.fillMaxWidth()) {
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
                                        RaisedCheckbox(checked = leftMarked, onCheckedChange = {
                                            renameMarkedForDelete = if (leftMarked) renameMarkedForDelete - leftPath else renameMarkedForDelete + leftPath
                                        }, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(Strings.markForDeletion, style = MaterialTheme.typography.labelSmall,
                                            color = if (leftMarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.weight(1f))
                                        GhostButton(shape = ButtonShape, onClick = { Desktop.getDesktop().open(leftFileR) },
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
                                    KeyButton(shape = ButtonShape, onClick = { rightExp = true }, modifier = Modifier.fillMaxWidth()) {
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
                                        RaisedCheckbox(checked = rightMarked, onCheckedChange = {
                                            renameMarkedForDelete = if (rightMarked) renameMarkedForDelete - rightPath else renameMarkedForDelete + rightPath
                                        }, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(Strings.markForDeletion, style = MaterialTheme.typography.labelSmall,
                                            color = if (rightMarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.weight(1f))
                                        GhostButton(shape = ButtonShape, onClick = { Desktop.getDesktop().open(rightFileR) },
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
                                .clip(AppShape(6.dp))
                                .border(1.dp, dividerColor, AppShape(6.dp))
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
internal fun renameExample(stripNumbers: Boolean, renameToFirstVerse: Boolean, caseOption: String): String {
    var base = Strings.renameExampleBefore.removeSuffix(".song")
    if (stripNumbers) base = leadingNumberRegex.replace(base, "")
    if (renameToFirstVerse) base = Strings.renameExampleFirstLine
    if (caseOption != "None") base = applyCase(base, caseOption)
    return "$base.song"
}

internal fun applyCase(name: String, caseOption: String): String = when (caseOption) {
    "Sentence case" -> name.lowercase().replaceFirstChar { it.titlecase() }
    "Title Case" -> name.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    "lowercase" -> name.lowercase()
    "UPPERCASE" -> name.uppercase()
    else -> name
}

internal fun buildRenamePreview(directory: File, stripNumbers: Boolean, renameToFirstVerse: Boolean, caseOption: String = "None"): List<RenameEntry> {
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

internal fun extractFirstVerseLine(file: File): String? {
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
