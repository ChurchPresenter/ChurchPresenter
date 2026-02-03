package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.*
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

@Composable
fun BibleSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val availableFonts = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }

    var refreshTrigger by remember { mutableStateOf(0) }
    var selectedFile by remember { mutableStateOf<String?>(null) }

    // Automatically load Bible files
    val bibleFilesInDirectory = remember(settings.bibleSettings.storageDirectory, refreshTrigger) {
        if (settings.bibleSettings.storageDirectory.isNotEmpty()) {
            val dir = File(settings.bibleSettings.storageDirectory)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { file -> file.extension.lowercase() == "spb" }?.map { it.name }?.sorted() ?: emptyList()
            } else emptyList()
        } else emptyList()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Column
            Column(
                modifier = Modifier.weight(0.48f).widthIn(min = 400.dp, max = 450.dp).heightIn(min = 600.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp)
            ) {
                LeftColumn(settings, onSettingsChange, availableFonts, bibleFilesInDirectory, selectedFile, { selectedFile = it }, { refreshTrigger++ })
            }

            // Right Column
            Column(
                modifier = Modifier.weight(0.48f).widthIn(min = 400.dp, max = 450.dp).heightIn(min = 600.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp)
            ) {
                RightColumn(settings, onSettingsChange, availableFonts)
            }
        }
    }
}

@Composable
private fun LeftColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>,
    bibleFilesInDirectory: List<String>,
    selectedFile: String?,
    onFileSelected: (String?) -> Unit,
    onRefresh: () -> Unit
) {
    val noneStr = stringResource(Res.string.none)
    // Storage Directory
    SectionHeader(stringResource(Res.string.storage_directory))
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (settings.bibleSettings.storageDirectory.isNotEmpty()) settings.bibleSettings.storageDirectory
            else stringResource(Res.string.no_directory_selected),
            modifier = Modifier.weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ModernButton(
            text = stringResource(Res.string.browse_directory),
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            onClick = {
                SwingUtilities.invokeLater {
                    val parentWindow = Window.getWindows().firstOrNull { it.isActive }
                    val dirChooser = JFileChooser().apply {
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        if (settings.bibleSettings.storageDirectory.isNotEmpty()) {
                            currentDirectory = File(settings.bibleSettings.storageDirectory)
                        }
                    }
                    if (dirChooser.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
                        onSettingsChange { it.copy(bibleSettings = it.bibleSettings.copy(storageDirectory = dirChooser.selectedFile.absolutePath)) }
                    }
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(15.dp))

    // Bible Files
    SectionHeader(stringResource(Res.string.bible_files))
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surface).padding(8.dp)
        ) {
            if (bibleFilesInDirectory.isEmpty()) {
                Text(
                    text = if (settings.bibleSettings.storageDirectory.isEmpty()) stringResource(Res.string.no_directory_selected)
                    else stringResource(Res.string.no_bible_files),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                bibleFilesInDirectory.forEach { fileName ->
                    val isSelected = fileName == selectedFile
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onFileSelected(fileName) }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModernButton(
            text = stringResource(Res.string.import_bible_file),
            backgroundColor = MaterialTheme.colorScheme.inverseSurface,
            onClick = {
                SwingUtilities.invokeLater {
                    val parentWindow = Window.getWindows().firstOrNull { it.isActive }
                    if (settings.bibleSettings.storageDirectory.isEmpty()) {
                        JOptionPane.showMessageDialog(parentWindow, "Please select a storage directory first.", "No Directory Selected", JOptionPane.WARNING_MESSAGE)
                        return@invokeLater
                    }
                    val fileChooser = JFileChooser().apply {
                        fileSelectionMode = JFileChooser.FILES_ONLY
                        isMultiSelectionEnabled = true
                        fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Bible Files (*.spb)", "spb")
                    }
                    if (fileChooser.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
                        val targetDir = File(settings.bibleSettings.storageDirectory)
                        fileChooser.selectedFiles.forEach { sourceFile ->
                            try {
                                sourceFile.copyTo(File(targetDir, sourceFile.name), overwrite = true)
                            } catch (e: Exception) {
                                JOptionPane.showMessageDialog(parentWindow, "Error copying ${sourceFile.name}: ${e.message}", "Copy Error", JOptionPane.ERROR_MESSAGE)
                            }
                        }
                        onRefresh()
                    }
                }
            }
        )

        ModernButton(
            text = stringResource(Res.string.remove_bible_file),
            backgroundColor = MaterialTheme.colorScheme.errorContainer,
            onClick = {
                SwingUtilities.invokeLater {
                    val parentWindow = Window.getWindows().firstOrNull { it.isActive }
                    if (selectedFile == null) {
                        JOptionPane.showMessageDialog(parentWindow, "Please select a file from the list first.", "No File Selected", JOptionPane.WARNING_MESSAGE)
                        return@invokeLater
                    }
                    val fileToDelete = File(settings.bibleSettings.storageDirectory, selectedFile!!)
                    if (JOptionPane.showConfirmDialog(parentWindow, "Are you sure you want to delete '$selectedFile'?", "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                        try {
                            fileToDelete.delete()
                            onFileSelected(null)
                            onRefresh()
                        } catch (e: Exception) {
                            JOptionPane.showMessageDialog(parentWindow, "Error deleting file: ${e.message}", "Delete Error", JOptionPane.ERROR_MESSAGE)
                        }
                    }
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Bible Selection
    SectionHeader(stringResource(Res.string.bible_selection))
    Spacer(modifier = Modifier.height(8.dp))
    SettingRow(stringResource(Res.string.primary_bible)) {

        DropdownSettingsField(
            value = settings.bibleSettings.primaryBible.ifEmpty { noneStr },
            options = listOf(stringResource(Res.string.none)) + bibleFilesInDirectory,
            onValueChange = {
                val value = if (it == noneStr) "" else it
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBible = value)) }
            }
        )
    }
    SettingRow(stringResource(Res.string.secondary_bible)) {
        DropdownSettingsField(
            value = settings.bibleSettings.secondaryBible.ifEmpty { noneStr },
            options = listOf(noneStr) + bibleFilesInDirectory,
            onValueChange = {
                val value = if (it == noneStr) "" else it
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBible = value)) }
            }
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Background
    SectionHeader(stringResource(Res.string.background))
    Spacer(modifier = Modifier.height(8.dp))
    SettingRow(stringResource(Res.string.background_type)) {
        DropdownSettingsField(
            value = settings.bibleSettings.backgroundType,
            options = listOf("Default", "Color", "Image"),
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(backgroundType = it)) }
            }
        )
    }

    if (settings.bibleSettings.backgroundType == "Color") {
        SettingRow(stringResource(Res.string.background_color)) {
            ColorPickerField(
                color = settings.bibleSettings.backgroundColor,
                onColorChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(backgroundColor = it)) }
                }
            )
        }
    }
}

@Composable
private fun RightColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>
) {
    // Primary Bible Text
    SectionHeader(stringResource(Res.string.primary_bible_text))
    Spacer(modifier = Modifier.height(8.dp))
    SettingRow(stringResource(Res.string.color)) {
        ColorPickerField(
            color = settings.bibleSettings.primaryBibleColor,
            onColorChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleColor = it)) }
            }
        )
    }
    SettingRow(stringResource(Res.string.font_type)) {
        FontSettingsDropdown(
            value = settings.bibleSettings.primaryBibleFontType,
            fonts = availableFonts,
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleFontType = it)) }
            }
        )
    }
    SettingRow(stringResource(Res.string.font_size)) {
        NumberSettingsTextField(
            initialText = settings.bibleSettings.primaryBibleFontSize,
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleFontSize = it)) }
            },
            range = 8..72
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Primary Bible Reference
    SectionHeader(stringResource(Res.string.primary_bible_reference))
    Spacer(modifier = Modifier.height(8.dp))
    SettingRow(stringResource(Res.string.color)) {
        ColorPickerField(
            color = settings.bibleSettings.primaryReferenceColor,
            onColorChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceColor = it)) }
            }
        )
    }
    SettingRow(stringResource(Res.string.font_type)) {
        FontSettingsDropdown(
            value = settings.bibleSettings.primaryReferenceFontType,
            fonts = availableFonts,
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceFontType = it)) }
            }
        )
    }
    SettingRow(stringResource(Res.string.font_size)) {
        NumberSettingsTextField(
            initialText = settings.bibleSettings.primaryReferenceFontSize,
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceFontSize = it)) }
            },
            range = 8..72
        )
    }
    SettingRow(stringResource(Res.string.position)) {
        DropdownSettingsField(
            value = settings.bibleSettings.primaryReferencePosition,
            options = listOf("Above", "Below"),
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferencePosition = it)) }
            }
        )
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = settings.bibleSettings.primaryShowAbbreviation,
            onCheckedChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryShowAbbreviation = it)) }
            }
        )
        Text(
            text = stringResource(Res.string.show_abbreviation),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Secondary Bible Text
    SectionHeader(stringResource(Res.string.secondary_bible_text))
    Spacer(modifier = Modifier.height(8.dp))
    SettingRow(stringResource(Res.string.color)) {
        ColorPickerField(
            color = settings.bibleSettings.secondaryBibleColor,
            onColorChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleColor = it)) }
            }
        )
    }
    SettingRow(stringResource(Res.string.font_type)) {
        FontSettingsDropdown(
            value = settings.bibleSettings.secondaryBibleFontType,
            fonts = availableFonts,
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleFontType = it)) }
            }
        )
    }
    SettingRow(stringResource(Res.string.font_size)) {
        NumberSettingsTextField(
            initialText = settings.bibleSettings.secondaryBibleFontSize,
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleFontSize = it)) }
            },
            range = 8..72
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Secondary Bible Reference
    SectionHeader(stringResource(Res.string.secondary_bible_reference))
    Spacer(modifier = Modifier.height(8.dp))
    SettingRow(stringResource(Res.string.color)) {
        ColorPickerField(
            color = settings.bibleSettings.secondaryReferenceColor,
            onColorChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceColor = it)) }
            }
        )
    }
    SettingRow(stringResource(Res.string.font_type)) {
        FontSettingsDropdown(
            value = settings.bibleSettings.secondaryReferenceFontType,
            fonts = availableFonts,
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceFontType = it)) }
            }
        )
    }
    SettingRow(stringResource(Res.string.font_size)) {
        NumberSettingsTextField(
            initialText = settings.bibleSettings.secondaryReferenceFontSize,
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceFontSize = it)) }
            },
            range = 8..72
        )
    }
    SettingRow(stringResource(Res.string.position)) {
        DropdownSettingsField(
            value = settings.bibleSettings.secondaryReferencePosition,
            options = listOf("Above", "Below"),
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferencePosition = it)) }
            }
        )
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = settings.bibleSettings.secondaryShowAbbreviation,
            onCheckedChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryShowAbbreviation = it)) }
            }
        )
        Text(
            text = stringResource(Res.string.show_abbreviation),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Language
    SectionHeader(stringResource(Res.string.caption_language))
    Spacer(modifier = Modifier.height(8.dp))
    SettingRow(stringResource(Res.string.language_source)) {
        DropdownSettingsField(
            value = settings.bibleSettings.captionLanguage,
            options = listOf("Interface", "Database"),
            onValueChange = {
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(captionLanguage = it)) }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    width: androidx.compose.ui.unit.Dp = 120.dp,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(width)
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun ModernButton(
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}
