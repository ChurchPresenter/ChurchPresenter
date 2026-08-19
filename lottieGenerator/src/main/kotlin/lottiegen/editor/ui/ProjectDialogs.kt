package lottiegen.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lottiegen.editor.BuildRegistrar
import lottiegen.editor.EditorState
import lottiegen.editor.EditorViewModel
import lottiegen.editor.ExportIssue
import lottiegen.editor.RegisterResult
import lottiegen.model.StyleCatalog
import lottiegen.persistence.StyleSpecStorage
import lottiegen.ui.Strings
import lottiegen.ui.components.LottieTextField
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Toolbar project actions (New/Open/Save/Save As/Export) plus the dialogs they open —
 * dirty-state confirmation, project picker, save-as naming, and the export flow that
 * ends with the manual registration checklist.
 */
@Composable
fun ProjectToolbarActions(state: EditorState) {
    var confirmDiscardFor by remember { mutableStateOf<PendingAction?>(null) }
    var showNewDialog by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedFileName by remember { mutableStateOf<String?>(null) }
    var registered by remember { mutableStateOf<RegisterResult?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        EditorTooltip(Strings.editorTipNew) {
            TextButton(onClick = {
                if (state.dirty) confirmDiscardFor = PendingAction.NEW else showNewDialog = true
            }) { Text(Strings.editorNew) }
        }
        EditorTooltip(Strings.editorTipOpen) {
            TextButton(onClick = {
                if (state.dirty) confirmDiscardFor = PendingAction.OPEN else showOpenDialog = true
            }) { Text(Strings.editorOpen) }
        }
        EditorTooltip(Strings.editorTipSave) {
            TextButton(onClick = {
                if (!state.saveProject()) showSaveAsDialog = true
            }) { Text(Strings.editorSave) }
        }
        EditorTooltip(Strings.editorTipSaveAs) {
            TextButton(onClick = { showSaveAsDialog = true }) { Text(Strings.editorSaveAs) }
        }
        EditorTooltip(Strings.editorTipExport) {
            TextButton(onClick = { showExportDialog = true }) { Text(Strings.editorExport) }
        }
    }

    confirmDiscardFor?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirmDiscardFor = null },
            title = { Text(Strings.editorUnsavedTitle) },
            text = { Text(Strings.editorUnsavedMessage) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscardFor = null
                    when (pending) {
                        PendingAction.NEW -> showNewDialog = true
                        PendingAction.OPEN -> showOpenDialog = true
                    }
                }) { Text(Strings.editorDiscard) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardFor = null }) { Text(Strings.cancelBtn) }
            }
        )
    }

    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text(Strings.editorNewTitle) },
            text = {
                ScrollableDialogColumn {
                    TextButton(onClick = {
                        showNewDialog = false
                        state.newProject(null)
                    }) { Text(Strings.editorNewBlank) }
                    val bundled = EditorViewModel.BUNDLED_TEMPLATES.map { template ->
                        val label = template.styleId?.let { StyleCatalog.labelFor(it) }
                            ?: template.labelKey?.let { Strings.byKey(it) }
                            ?: template.resource
                        label to template.resource
                    }
                    // Registry-registered styles are immediately re-editable as templates.
                    val registered = StyleCatalog.entries
                        .mapNotNull { info -> info.specResource?.let { info.label to it } }
                        .filterNot { (_, resource) -> bundled.any { it.second == resource } }
                    for ((label, resource) in bundled + registered) {
                        TextButton(onClick = {
                            showNewDialog = false
                            state.newProject(resource)
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) { Text(Strings.cancelBtn) }
            }
        )
    }

    if (showOpenDialog) {
        OpenProjectDialog(state, onClose = { showOpenDialog = false })
    }

    if (showSaveAsDialog) {
        SaveAsDialog(state, onClose = { showSaveAsDialog = false })
    }

    if (showExportDialog) {
        ExportDialog(
            state,
            onClose = { showExportDialog = false },
            onExported = { fileName ->
                showExportDialog = false
                exportedFileName = fileName
            },
            onRegistered = { result ->
                showExportDialog = false
                registered = result
            }
        )
    }

    registered?.let { result ->
        AlertDialog(
            onDismissRequest = { registered = null },
            title = { Text(Strings.editorRegisterDoneTitle) },
            text = {
                Text(
                    Strings.editorRegisterDoneBody(
                        result.specFile.absolutePath,
                        result.registryFile.absolutePath,
                        state.spec.id
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { registered = null }) { Text(Strings.ok) }
            }
        )
    }

    exportedFileName?.let { fileName ->
        AlertDialog(
            onDismissRequest = { exportedFileName = null },
            title = { Text(Strings.editorExportDoneTitle) },
            text = { Text(Strings.editorExportDoneSteps(state.spec.id, fileName, state.spec.name)) },
            confirmButton = {
                TextButton(onClick = { exportedFileName = null }) { Text(Strings.ok) }
            }
        )
    }
}

private enum class PendingAction { NEW, OPEN }

@Composable
private fun OpenProjectDialog(state: EditorState, onClose: () -> Unit) {
    var files by remember { mutableStateOf(StyleSpecStorage.list()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(Strings.editorOpenTitle) },
        text = {
            ScrollableDialogColumn {
                if (files.isEmpty()) {
                    Text(
                        Strings.editorNoProjects,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                for (file in files) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (state.openProject(file)) onClose()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            file.nameWithoutExtension,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            StyleSpecStorage.delete(file)
                            files = StyleSpecStorage.list()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = Strings.editorDelete,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onClose) { Text(Strings.cancelBtn) }
        }
    )
}

@Composable
private fun SaveAsDialog(state: EditorState, onClose: () -> Unit) {
    var name by remember { mutableStateOf(state.projectName) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(Strings.editorSaveAs) },
        text = {
            LottieTextField(
                value = name,
                onValueChange = { name = it },
                label = Strings.editorProjectName,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.saveProjectAs(name.trim().ifEmpty { "Untitled" })
                    onClose()
                }
            ) { Text(Strings.editorSave) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(Strings.cancelBtn) }
        }
    )
}

@Composable
private fun ExportDialog(
    state: EditorState,
    onClose: () -> Unit,
    onExported: (String) -> Unit,
    onRegistered: (RegisterResult) -> Unit
) {
    val issues = state.validateForExport()
    val canRegister = remember { BuildRegistrar.locateStylesDir() != null }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(Strings.editorExportTitle) },
        text = {
            ScrollableDialogColumn(maxHeight = 440.dp) {
                LottieTextField(
                    value = state.spec.id,
                    onValueChange = { new -> state.updateSpec { it.copy(id = new.trim()) } },
                    label = Strings.editorStyleId,
                    modifier = Modifier.fillMaxWidth()
                )
                LottieTextField(
                    value = state.spec.name,
                    onValueChange = { new -> state.updateSpec { it.copy(name = new) } },
                    label = Strings.editorStyleName,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.spec.id.isNotBlank() && state.spec.name.isNotBlank()) {
                    Text(
                        Strings.editorExportLabelPreview(
                            Strings.editorStyleLabelFormat(state.spec.id.trim(), state.spec.name.trim())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                StyleCatalog.entries.firstOrNull { it.id == state.spec.id.trim() }?.let { taken ->
                    Text(
                        Strings.editorExportReplaces(taken.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Text(
                    Strings.editorExportRegistered,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    StyleCatalog.entries.joinToString("\n") { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                for (issue in issues) {
                    Text(
                        text = when (issue) {
                            ExportIssue.BAD_ID -> Strings.editorExportErrId
                            ExportIssue.NO_ELEMENTS -> Strings.editorExportErrNoElements
                            ExportIssue.BAD_KEYFRAMES -> Strings.editorExportErrKeyframes
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canRegister) {
                    EditorTooltip(Strings.editorTipRegister) {
                        TextButton(
                            enabled = issues.isEmpty(),
                            onClick = {
                                state.registerIntoBuild()?.let(onRegistered)
                            }
                        ) { Text(Strings.editorRegisterBuild) }
                    }
                }
                TextButton(
                    enabled = issues.isEmpty(),
                    onClick = {
                        val suggested = "style${state.spec.id}_${StyleSpecStorage.slugify(state.spec.name)}.json"
                        val dialog = FileDialog(null as Frame?, Strings.editorExportTitle, FileDialog.SAVE)
                        dialog.file = suggested
                        dialog.isVisible = true
                        val dir = dialog.directory
                        val fileName = dialog.file
                        if (dir != null && fileName != null) {
                            val target = File(dir, fileName)
                            if (state.exportTo(target)) onExported(target.name)
                        }
                    }
                ) { Text(Strings.editorExportChoose) }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(Strings.cancelBtn) }
        }
    )
}
