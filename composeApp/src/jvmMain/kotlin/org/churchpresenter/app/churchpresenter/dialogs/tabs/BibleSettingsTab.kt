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
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment
import java.awt.Window
import javax.swing.SwingUtilities

@Composable
fun BibleSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val availableFonts = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }
    val fileManager = remember { FileManager() }

    var refreshTrigger by remember { mutableStateOf(0) }
    var selectedFile by remember { mutableStateOf<String?>(null) }

    // Use FileManager to load Bible files
    val bibleFilesInDirectory = remember(settings.bibleSettings.storageDirectory, refreshTrigger) {
        fileManager.getBibleFilesInDirectory(settings.bibleSettings.storageDirectory)
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
                LeftColumn(settings, onSettingsChange, availableFonts, bibleFilesInDirectory, selectedFile, { selectedFile = it }, { refreshTrigger++ }, fileManager)
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
    onRefresh: () -> Unit,
    fileManager: FileManager
) {
    val noneStr = stringResource(Res.string.none)
    val pleaseSelectDirectoryStr = stringResource(Res.string.please_select_directory_first)
    val noDirectorySelectedStr = stringResource(Res.string.no_directory_selected)
    val pleaseSelectFileStr = stringResource(Res.string.please_select_file_first)
    val noFileSelectedStr = stringResource(Res.string.no_file_selected_title)
    val confirmDeleteStr = stringResource(Res.string.confirm_delete)
    val confirmDeleteFileTemplate = stringResource(Res.string.confirm_delete_file)
    val importErrorStr = stringResource(Res.string.import_error)
    val deleteErrorStr = stringResource(Res.string.delete_error)

    // Background type options
    val backgroundDefaultStr = stringResource(Res.string.background_default)
    val backgroundColorStr = stringResource(Res.string.background_color_option)
    val backgroundImageStr = stringResource(Res.string.background_image_option)

    // Position options
    val positionAboveStr = stringResource(Res.string.position_above)
    val positionBelowStr = stringResource(Res.string.position_below)

    // Language options
    val languageInterfaceStr = stringResource(Res.string.language_interface)
    val languageDatabaseStr = stringResource(Res.string.language_database)

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
                    val selectedDir = fileManager.chooseDirectory(
                        currentDirectory = settings.bibleSettings.storageDirectory,
                        parentWindow = parentWindow
                    )
                    selectedDir?.let { dir ->
                        onSettingsChange { it.copy(bibleSettings = it.bibleSettings.copy(storageDirectory = dir)) }
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

                    // Check if directory is selected
                    if (settings.bibleSettings.storageDirectory.isEmpty()) {
                        fileManager.showWarning(
                            message = pleaseSelectDirectoryStr,
                            title = noDirectorySelectedStr,
                            parentWindow = parentWindow
                        )
                        return@invokeLater
                    }

                    // Choose files to import
                    val selectedFiles = fileManager.chooseBibleFile(parentWindow)
                    selectedFiles?.let { file ->
                        // Import files
                        val errors = fileManager.importFiles(
                            sourceFiles = listOf(file),
                            targetDirectory = settings.bibleSettings.storageDirectory
                        )

                        // Show errors if any
                        if (errors.isNotEmpty()) {
                            fileManager.showError(
                                message = errors.joinToString("\n"),
                                title = importErrorStr,
                                parentWindow = parentWindow
                            )
                        }

                        // Trigger refresh
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

                    // Check if file is selected
                    if (selectedFile == null) {
                        fileManager.showWarning(
                            message = pleaseSelectFileStr,
                            title = noFileSelectedStr,
                            parentWindow = parentWindow
                        )
                        return@invokeLater
                    }

                    // Confirm deletion
                    val confirmed = fileManager.showConfirmDialog(
                        message = String.format(confirmDeleteFileTemplate, selectedFile),
                        title = confirmDeleteStr,
                        parentWindow = parentWindow
                    )

                    if (confirmed) {
                        // Delete file
                        val error = fileManager.deleteFile(
                            directory = settings.bibleSettings.storageDirectory,
                            fileName = selectedFile!!
                        )

                        if (error != null) {
                            fileManager.showError(
                                message = error,
                                title = deleteErrorStr,
                                parentWindow = parentWindow
                            )
                        } else {
                            // Success - clear selection and refresh
                            onFileSelected(null)
                            onRefresh()
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
            value = when (settings.bibleSettings.backgroundType) {
                Constants.BACKGROUND_DEFAULT -> backgroundDefaultStr
                Constants.BACKGROUND_COLOR -> backgroundColorStr
                Constants.BACKGROUND_IMAGE -> backgroundImageStr
                else -> backgroundDefaultStr
            },
            options = listOf(backgroundDefaultStr, backgroundColorStr, backgroundImageStr),
            onValueChange = { displayValue ->
                val value = when (displayValue) {
                    backgroundDefaultStr -> Constants.BACKGROUND_DEFAULT
                    backgroundColorStr -> Constants.BACKGROUND_COLOR
                    backgroundImageStr -> Constants.BACKGROUND_IMAGE
                    else -> Constants.BACKGROUND_DEFAULT
                }
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(backgroundType = value)) }
            }
        )
    }

    if (settings.bibleSettings.backgroundType == Constants.BACKGROUND_COLOR) {
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
    // Position options
    val positionAboveStr = stringResource(Res.string.position_above)
    val positionBelowStr = stringResource(Res.string.position_below)

    // Language options
    val languageInterfaceStr = stringResource(Res.string.language_interface)
    val languageDatabaseStr = stringResource(Res.string.language_database)

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
            value = when (settings.bibleSettings.primaryReferencePosition) {
                Constants.POSITION_ABOVE -> positionAboveStr
                Constants.POSITION_BELOW -> positionBelowStr
                else -> positionAboveStr
            },
            options = listOf(positionAboveStr, positionBelowStr),
            onValueChange = { displayValue ->
                val value = when (displayValue) {
                    positionAboveStr -> Constants.POSITION_ABOVE
                    positionBelowStr -> Constants.POSITION_BELOW
                    else -> Constants.POSITION_ABOVE
                }
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferencePosition = value)) }
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
            value = when (settings.bibleSettings.secondaryReferencePosition) {
                Constants.POSITION_ABOVE -> positionAboveStr
                Constants.POSITION_BELOW -> positionBelowStr
                else -> positionAboveStr
            },
            options = listOf(positionAboveStr, positionBelowStr),
            onValueChange = { displayValue ->
                val value = when (displayValue) {
                    positionAboveStr -> Constants.POSITION_ABOVE
                    positionBelowStr -> Constants.POSITION_BELOW
                    else -> Constants.POSITION_ABOVE
                }
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferencePosition = value)) }
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
            value = when (settings.bibleSettings.captionLanguage) {
                Constants.LANGUAGE_INTERFACE -> languageInterfaceStr
                Constants.LANGUAGE_DATABASE -> languageDatabaseStr
                else -> languageInterfaceStr
            },
            options = listOf(languageInterfaceStr, languageDatabaseStr),
            onValueChange = { displayValue ->
                val value = when (displayValue) {
                    languageInterfaceStr -> Constants.LANGUAGE_INTERFACE
                    languageDatabaseStr -> Constants.LANGUAGE_DATABASE
                    else -> Constants.LANGUAGE_INTERFACE
                }
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(captionLanguage = value)) }
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
