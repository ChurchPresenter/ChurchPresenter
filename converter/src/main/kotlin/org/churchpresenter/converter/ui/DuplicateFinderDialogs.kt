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
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import org.churchpresenter.theme.components.RaisedCheckbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.converter.library.DuplicateFinder
import org.churchpresenter.converter.library.DuplicateGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import org.churchpresenter.converter.library.SongInfo

/** Confirms deleting the marked files. */
@Composable
internal fun DuplicateFinderState.DuplicateDeleteConfirmDialog() {
    if (!showDeleteConfirm) return
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
            RaisedButton(shape = ButtonShape, onClick = {
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
        dismissButton = { KeyButton(shape = ButtonShape, onClick = { showDeleteConfirm = false }) { Text(Strings.cancel) } }
    )
}

/** Offers to fix look-alike characters before a scan. */
@Composable
internal fun DuplicateFinderState.HomoglyphPromptDialog() {
    if (!showHomoglyphPrompt) return
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
            RaisedButton(shape = ButtonShape, onClick = {
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
            KeyButton(shape = ButtonShape, onClick = {
                showHomoglyphPrompt = false
                startScan()
            }) { Text(Strings.skipAndScan) }
        }
    )
}

/** Two songs of a group side by side: which files, their sections, and a line diff. */
@Composable
internal fun DuplicateFinderState.DuplicateCompareWindow() {
    if (compareGroup == null) return
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
                        CompareFileColumn(cg, Strings.left, compareLeft, compareRight, { compareLeft = it }, Modifier.weight(1f))
                        CompareFileColumn(cg, Strings.right, compareRight, compareLeft, { compareRight = it }, Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(8.dp))

                    // Section summary
                    val leftSong = cg.songs.getOrNull(compareLeft)
                    val rightSong = cg.songs.getOrNull(compareRight)
                    if (leftSong != null && rightSong != null) {
                        CompareSectionSummary(leftSong, rightSong)

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        CompareDiff(leftSong, rightSong, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** One side of the compare window: the file, whether it is marked for deletion, and Open. */
@Composable
private fun DuplicateFinderState.CompareFileColumn(
    cg: DuplicateGroup,
    label: String,
    selected: Int,
    other: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var expanded by remember { mutableStateOf(false) }
        Box {
            KeyButton(shape = ButtonShape, onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                val f = cg.songs.getOrNull(selected)?.file
                Text(if (f != null) "${f.parentFile.name}/${f.name}" else "Select",
                    maxLines = 1, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                cg.songs.forEachIndexed { idx, song ->
                    DropdownMenuItem(
                        text = { Text("${song.file.parentFile.name}/${song.file.name}", style = MaterialTheme.typography.bodySmall) },
                        onClick = { onSelect(idx); expanded = false },
                        enabled = idx != other,
                        leadingIcon = {
                            if (idx == selected) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        }
                    )
                }
            }
        }
        // Delete checkbox + open
        val file = cg.songs.getOrNull(selected)?.file
        val path = file?.canonicalPath
        if (path != null) {
            val marked = path in markedForDelete
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                RaisedCheckbox(
                    checked = marked,
                    onCheckedChange = {
                        markedForDelete = if (marked) markedForDelete - path else markedForDelete + path
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(Strings.markForDeletion, style = MaterialTheme.typography.labelSmall,
                    color = if (marked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                GhostButton(shape = ButtonShape, onClick = { Desktop.getDesktop().open(file) },
                    modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                    Text(Strings.open, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CompareSectionSummary(leftSong: SongInfo, rightSong: SongInfo) {
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
}

@Composable
private fun CompareDiff(leftSong: SongInfo, rightSong: SongInfo, modifier: Modifier) {
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
        modifier = modifier.fillMaxWidth()
            .clip(AppShape(6.dp))
            .border(1.dp, dividerColor, AppShape(6.dp))
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
