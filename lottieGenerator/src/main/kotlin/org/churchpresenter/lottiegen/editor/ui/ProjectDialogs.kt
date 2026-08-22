package org.churchpresenter.lottiegen.editor.ui

import org.churchpresenter.lottiegen.ui.EditorStrings
import androidx.compose.foundation.clickable
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
import org.churchpresenter.lottiegen.editor.BuildRegistrar
import org.churchpresenter.lottiegen.editor.EditorState
import org.churchpresenter.lottiegen.editor.EditorViewModel
import org.churchpresenter.lottiegen.editor.ExportIssue
import org.churchpresenter.lottiegen.editor.RegisterResult
import org.churchpresenter.lottiegen.model.StyleCatalog
import org.churchpresenter.lottiegen.persistence.StyleSpecStorage
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.components.LottieTextField
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Toolbar project actions (New/Open/Save/Save As/Export) plus the dialogs they open —
 * dirty-state confirmation, project picker, save-as naming, and the export flow that
 * ends with the manual registration checklist.
 */
/** The seven pieces of dialog state the toolbar drives, held together rather than threaded. */
private class ProjectDialogState {
    var confirmDiscardFor by mutableStateOf<PendingAction?>(null)
    var showNewDialog by mutableStateOf(false)
    var showOpenDialog by mutableStateOf(false)
    var showSaveAsDialog by mutableStateOf(false)
    var showExportDialog by mutableStateOf(false)
    var exportedFileName by mutableStateOf<String?>(null)
    var registered by mutableStateOf<RegisterResult?>(null)
}

@Composable
fun ProjectToolbarActions(state: EditorState) {
    val d = remember { ProjectDialogState() }
    ToolbarRow(state, d)
    ProjectDialogHost(state, d)
}

/** New / Open / Save / Save As / Export. */
@Composable
private fun ToolbarRow(state: EditorState, d: ProjectDialogState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EditorTooltip(Strings.editorTipNew) {
            TextButton(onClick = {
                if (state.dirty) d.confirmDiscardFor = PendingAction.NEW else d.showNewDialog = true
            }) { Text(Strings.editorNew) }
        }
        EditorTooltip(Strings.editorTipOpen) {
            TextButton(onClick = {
                if (state.dirty) d.confirmDiscardFor = PendingAction.OPEN else d.showOpenDialog = true
            }) { Text(Strings.editorOpen) }
        }
        EditorTooltip(Strings.editorTipSave) {
            TextButton(onClick = {
                if (!state.saveProject()) d.showSaveAsDialog = true
            }) { Text(Strings.editorSave) }
        }
        EditorTooltip(Strings.editorTipSaveAs) {
            TextButton(onClick = { d.showSaveAsDialog = true }) { Text(Strings.editorSaveAs) }
        }
        EditorTooltip(Strings.editorTipExport) {
            TextButton(onClick = { d.showExportDialog = true }) { Text(Strings.editorExport) }
        }
    }
}

/** Every dialog the toolbar can raise, shown only while its own flag is set. */
@Composable
private fun ProjectDialogHost(state: EditorState, d: ProjectDialogState) {
    DiscardConfirmDialog(d)
    NewProjectDialog(state, d)
    if (d.showOpenDialog) {
        OpenProjectDialog(state, onClose = { d.showOpenDialog = false })
    }

    if (d.showSaveAsDialog) {
        SaveAsDialog(state, onClose = { d.showSaveAsDialog = false })
    }

    if (d.showExportDialog) {
        ExportDialog(
            state,
            onClose = { d.showExportDialog = false },
            onExported = { fileName ->
                d.showExportDialog = false
                d.exportedFileName = fileName
            },
            onRegistered = { result ->
                d.showExportDialog = false
                d.registered = result
            }
        )
    }

    d.registered?.let { result ->
        AlertDialog(
            onDismissRequest = { d.registered = null },
            title = { Text(Strings.editorRegisterDoneTitle) },
            text = {
                Text(
                    EditorStrings.registerDoneBody(
                        result.specFile.absolutePath,
                        result.registryFile.absolutePath,
                        state.spec.id
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { d.registered = null }) { Text(Strings.ok) }
            }
        )
    }

    d.exportedFileName?.let { fileName ->
        AlertDialog(
            onDismissRequest = { d.exportedFileName = null },
            title = { Text(Strings.editorExportDoneTitle) },
            text = { Text(EditorStrings.exportDoneSteps(state.spec.id, fileName, state.spec.name)) },
            confirmButton = {
                TextButton(onClick = { d.exportedFileName = null }) { Text(Strings.ok) }
            }
        )
    }
}

/** "You have unsaved changes" -- shown before New or Open when the project is dirty. */
@Composable
private fun DiscardConfirmDialog(d: ProjectDialogState) {
    d.confirmDiscardFor?.let { pending ->
        AlertDialog(
            onDismissRequest = { d.confirmDiscardFor = null },
            title = { Text(Strings.editorUnsavedTitle) },
            text = { Text(Strings.editorUnsavedMessage) },
            confirmButton = {
                TextButton(onClick = {
                    d.confirmDiscardFor = null
                    when (pending) {
                        PendingAction.NEW -> d.showNewDialog = true
                        PendingAction.OPEN -> d.showOpenDialog = true
                    }
                }) { Text(Strings.editorDiscard) }
            },
            dismissButton = {
                TextButton(onClick = { d.confirmDiscardFor = null }) { Text(Strings.cancelBtn) }
            }
        )
    }

}

/** The template picker New opens. */
@Composable
private fun NewProjectDialog(state: EditorState, d: ProjectDialogState) {
    if (!d.showNewDialog) return
        AlertDialog(
            onDismissRequest = { d.showNewDialog = false },
            title = { Text(Strings.editorNewTitle) },
            text = {
                ScrollableDialogColumn {
                    TextButton(onClick = {
                        d.showNewDialog = false
                        state.newProject(null)
                    }) { Text(Strings.editorNewBlank) }
                    val bundled = EditorViewModel.BUNDLED_TEMPLATES.map { template ->
                        val label = template.styleId?.let { StyleCatalog.labelFor(it) }
                            ?: template.labelKey?.let { Strings.byKey(it) }
                            ?: template.resource
                        label to template.resource
                    }
                    // Registry-registered styles are immediately re-editable as templates.
                    val fromRegistry = StyleCatalog.entries
                        .mapNotNull { info -> info.specResource?.let { info.label to it } }
                        .filterNot { (_, resource) -> bundled.any { it.second == resource } }
                    for ((label, resource) in bundled + fromRegistry) {
                        TextButton(onClick = {
                            d.showNewDialog = false
                            state.newProject(resource)
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { d.showNewDialog = false }) { Text(Strings.cancelBtn) }
            }
        )
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
                        EditorStrings.exportLabelPreview(
                            EditorStrings.styleLabelFormat(state.spec.id.trim(), state.spec.name.trim())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                StyleCatalog.entries.firstOrNull { it.id == state.spec.id.trim() }?.let { taken ->
                    Text(
                        EditorStrings.exportReplaces(taken.label),
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
