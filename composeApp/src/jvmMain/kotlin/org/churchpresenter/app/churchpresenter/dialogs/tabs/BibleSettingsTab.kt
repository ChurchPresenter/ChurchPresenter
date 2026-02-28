package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.animation_fade
import churchpresenter.composeapp.generated.resources.animation_none
import churchpresenter.composeapp.generated.resources.animation_slide_left
import churchpresenter.composeapp.generated.resources.animation_slide_right
import churchpresenter.composeapp.generated.resources.animation_type
import churchpresenter.composeapp.generated.resources.bible_files
import churchpresenter.composeapp.generated.resources.bible_selection
import churchpresenter.composeapp.generated.resources.bible_transition_settings
import churchpresenter.composeapp.generated.resources.browse_directory
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.confirm_delete
import churchpresenter.composeapp.generated.resources.confirm_delete_file
import churchpresenter.composeapp.generated.resources.delete_error
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.full_screen
import churchpresenter.composeapp.generated.resources.horizontal_alignment
import churchpresenter.composeapp.generated.resources.import_bible_file
import churchpresenter.composeapp.generated.resources.import_error
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.no_bible_files
import churchpresenter.composeapp.generated.resources.no_directory_selected
import churchpresenter.composeapp.generated.resources.no_file_selected_title
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.please_select_directory_first
import churchpresenter.composeapp.generated.resources.please_select_file_first
import churchpresenter.composeapp.generated.resources.position
import churchpresenter.composeapp.generated.resources.primary_bible
import churchpresenter.composeapp.generated.resources.primary_bible_reference
import churchpresenter.composeapp.generated.resources.primary_bible_text
import churchpresenter.composeapp.generated.resources.remove_bible_file
import churchpresenter.composeapp.generated.resources.secondary_bible
import churchpresenter.composeapp.generated.resources.secondary_bible_reference
import churchpresenter.composeapp.generated.resources.secondary_bible_text
import churchpresenter.composeapp.generated.resources.show_abbreviation
import churchpresenter.composeapp.generated.resources.show_in_lower_third
import churchpresenter.composeapp.generated.resources.storage_directory
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.vertical_alignment
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.PositionButtons
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.viewmodel.BibleSettingsViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment
import java.awt.Window
import javax.swing.SwingUtilities
import kotlinx.coroutines.launch


@Composable
fun BibleSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val availableFonts = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }
    val viewModel = remember {
        BibleSettingsViewModel().also { vm ->
            val dir = settings.bibleSettings.storageDirectory
            if (dir.isNotEmpty()) vm.setDirectory(dir)
        }
    }

    // Keep viewModel directory in sync if settings change after initial load
    LaunchedEffect(settings.bibleSettings.storageDirectory) {
        val dir = settings.bibleSettings.storageDirectory
        if (viewModel.storageDirectory != dir) viewModel.setDirectory(dir)
    }

    val bibleFilesInDirectory = remember(viewModel.storageDirectory, viewModel.refreshTrigger) {
        viewModel.filesInDirectory()
    }
    val bibleFileDisplayNames = remember(viewModel.storageDirectory, bibleFilesInDirectory) {
        viewModel.fileDisplayNames(bibleFilesInDirectory)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(0.48f).widthIn(min = 400.dp, max = 450.dp).heightIn(min = 600.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp)
            ) {
                LeftColumn(
                    settings,
                    onSettingsChange,
                    bibleFilesInDirectory,
                    bibleFileDisplayNames,
                    viewModel.selectedFile,
                    { viewModel.selectFile(it) },
                    { viewModel.refresh() },
                    viewModel.fileManager
                )
            }
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
    bibleFilesInDirectory: List<String>,
    bibleFileDisplayNames: Map<String, String>,
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

    val scope = rememberCoroutineScope()

    // Storage Directory
    SectionHeader(stringResource(Res.string.storage_directory))
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = settings.bibleSettings.storageDirectory.ifEmpty { stringResource(Res.string.no_directory_selected) },
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
                scope.launch {
                    val parentWindow = Window.getWindows().firstOrNull { it.isActive }
                    val selectedDir = fileManager.chooseDirectory(
                        currentDirectory = settings.bibleSettings.storageDirectory
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
                    text = if (settings.bibleSettings.storageDirectory.isEmpty()) noDirectorySelectedStr
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
                scope.launch {
                    val parentWindow = Window.getWindows().firstOrNull { it.isActive }
                    if (settings.bibleSettings.storageDirectory.isEmpty()) {
                        fileManager.showWarning(pleaseSelectDirectoryStr, noDirectorySelectedStr, parentWindow)
                        return@launch
                    }
                    fileManager.chooseBibleFile()?.let { file ->
                        val errors = fileManager.importFiles(listOf(file), settings.bibleSettings.storageDirectory)
                        if (errors.isNotEmpty()) fileManager.showError(errors.joinToString("\n"), importErrorStr, parentWindow)
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
                        fileManager.showWarning(pleaseSelectFileStr, noFileSelectedStr, parentWindow)
                        return@invokeLater
                    }
                    if (fileManager.showConfirmDialog(String.format(confirmDeleteFileTemplate, selectedFile), confirmDeleteStr, parentWindow)) {
                        val error = fileManager.deleteFile(settings.bibleSettings.storageDirectory, selectedFile)
                        if (error != null) fileManager.showError(error, deleteErrorStr, parentWindow)
                        else { onFileSelected(null); onRefresh() }
                    }
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Bible Selection
    SectionHeader(stringResource(Res.string.bible_selection))
    Spacer(modifier = Modifier.height(8.dp))

    // Create list of display names for dropdown
    val bibleDisplayOptions = listOf(noneStr) + bibleFilesInDirectory.map { fileName ->
        bibleFileDisplayNames[fileName] ?: fileName
    }

    SettingRow(stringResource(Res.string.primary_bible)) {
        DropdownSettingsField(
            value = if (settings.bibleSettings.primaryBible.isEmpty()) {
                noneStr
            } else {
                bibleFileDisplayNames[settings.bibleSettings.primaryBible] ?: settings.bibleSettings.primaryBible
            },
            options = bibleDisplayOptions,
            onValueChange = { displayName ->
                // Find the filename that matches this display name
                val fileName = if (displayName == noneStr) {
                    ""
                } else {
                    bibleFileDisplayNames.entries.find { it.value == displayName }?.key ?: displayName
                }
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBible = fileName)) }
            }
        )
    }
    SettingRow(stringResource(Res.string.secondary_bible)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DropdownSettingsField(
                value = if (settings.bibleSettings.secondaryBible.isEmpty()) {
                    noneStr
                } else {
                    bibleFileDisplayNames[settings.bibleSettings.secondaryBible] ?: settings.bibleSettings.secondaryBible
                },
                options = bibleDisplayOptions,
                onValueChange = { displayName ->
                    val fileName = if (displayName == noneStr) {
                        ""
                    } else {
                        bibleFileDisplayNames.entries.find { it.value == displayName }?.key ?: displayName
                    }
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBible = fileName)) }
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = settings.bibleSettings.secondaryBibleLowerThirdEnabled,
                    onCheckedChange = { checked ->
                        onSettingsChange { s ->
                            s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleLowerThirdEnabled = checked))
                        }
                    }
                )
                Text(
                    text = stringResource(Res.string.show_in_lower_third),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Global Vertical Alignment
    SectionHeader(stringResource(Res.string.vertical_alignment))
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerticalAlignmentButtons(
            selectedAlignment = settings.bibleSettings.verticalAlignment,
            onAlignmentChange = { value ->
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(verticalAlignment = value)) }
            },
            topValue = Constants.TOP,
            middleValue = Constants.MIDDLE,
            bottomValue = Constants.BOTTOM
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Transition Section
    SectionHeader(stringResource(Res.string.bible_transition_settings))

    Spacer(modifier = Modifier.height(8.dp))

    val durationLabel = stringResource(Res.string.transition_duration)
    val msSuffix = stringResource(Res.string.milliseconds_suffix)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = durationLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(120.dp)
        )
        Slider(
            value = settings.bibleSettings.transitionDuration,
            onValueChange = { rawValue ->
                val snapped = (rawValue / 50f).toInt() * 50f
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(transitionDuration = snapped)) }
            },
            valueRange = 100f..2000f,
            steps = 37,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${settings.bibleSettings.transitionDuration.toInt()}$msSuffix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(60.dp)
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    SettingRow(stringResource(Res.string.animation_type)) {
        val crossfadeText = stringResource(Res.string.animation_crossfade)
        val fadeText = stringResource(Res.string.animation_fade)
        val slideLeftText = stringResource(Res.string.animation_slide_left)
        val slideRightText = stringResource(Res.string.animation_slide_right)
        val noneText = stringResource(Res.string.animation_none)

        val currentType = when (settings.bibleSettings.animationType) {
            Constants.ANIMATION_FADE -> AnimationType.FADE
            Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
            Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
            Constants.ANIMATION_NONE -> AnimationType.NONE
            else -> AnimationType.CROSSFADE
        }

        DropdownSelector(
            modifier = Modifier.width(200.dp),
            label = "",
            items = listOf(crossfadeText, fadeText, slideLeftText, slideRightText, noneText),
            selected = when (currentType) {
                AnimationType.CROSSFADE -> crossfadeText
                AnimationType.FADE -> fadeText
                AnimationType.SLIDE_LEFT -> slideLeftText
                AnimationType.SLIDE_RIGHT -> slideRightText
                AnimationType.NONE -> noneText
                else -> crossfadeText
            },
            onSelectedChange = { selected ->
                val newType = when (selected) {
                    fadeText -> Constants.ANIMATION_FADE
                    slideLeftText -> Constants.ANIMATION_SLIDE_LEFT
                    slideRightText -> Constants.ANIMATION_SLIDE_RIGHT
                    noneText -> Constants.ANIMATION_NONE
                    else -> Constants.ANIMATION_CROSSFADE
                }
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(animationType = newType)) }
            }
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPickerField(
                color = settings.bibleSettings.primaryBibleColor,
                onColorChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleColor = it)) }
                }
            )
            TextStyleButtons(
                bold = settings.bibleSettings.primaryBibleBold,
                italic = settings.bibleSettings.primaryBibleItalic,
                underline = settings.bibleSettings.primaryBibleUnderline,
                shadow = settings.bibleSettings.primaryBibleShadow,
                onBoldChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleBold = it)) } },
                onItalicChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleItalic = it)) } },
                onUnderlineChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleUnderline = it)) } },
                onShadowChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleShadow = it)) } }
            )
        }
    }
    SettingRow(stringResource(Res.string.font_type)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FontSettingsDropdown(
                modifier = Modifier.width(200.dp),
                value = settings.bibleSettings.primaryBibleFontType,
                fonts = availableFonts,
                onValueChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleFontType = it)) }
                }
            )
            val previewFontFamily = remember(settings.bibleSettings.primaryBibleFontType) {
                systemFontFamilyOrDefault(settings.bibleSettings.primaryBibleFontType)
            }
            Text(
                text = "ABCDabcd1234",
                fontFamily = previewFontFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp)
            )
        }
    }
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.bibleSettings.primaryBibleFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleFontSize = it)) } },
                    range = 8..72
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.bibleSettings.primaryBibleLowerThirdFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleLowerThirdFontSize = it)) } },
                    range = 8..72
                )
            }
        }
    }
    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.bibleSettings.primaryBibleHorizontalAlignment,
                    onAlignmentChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleHorizontalAlignment = value)) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.bibleSettings.primaryBibleLowerThirdHorizontalAlignment,
                    onAlignmentChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryBibleLowerThirdHorizontalAlignment = value)) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Primary Bible Reference
    SectionHeader(stringResource(Res.string.primary_bible_reference))
    Spacer(modifier = Modifier.height(8.dp))
    SettingRow(stringResource(Res.string.color)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPickerField(
                color = settings.bibleSettings.primaryReferenceColor,
                onColorChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceColor = it)) }
                }
            )
            TextStyleButtons(
                bold = settings.bibleSettings.primaryReferenceBold,
                italic = settings.bibleSettings.primaryReferenceItalic,
                underline = settings.bibleSettings.primaryReferenceUnderline,
                shadow = settings.bibleSettings.primaryReferenceShadow,
                onBoldChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceBold = it)) } },
                onItalicChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceItalic = it)) } },
                onUnderlineChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceUnderline = it)) } },
                onShadowChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceShadow = it)) } }
            )
        }
    }
    SettingRow(stringResource(Res.string.font_type)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FontSettingsDropdown(
                modifier = Modifier.width(200.dp),
                value = settings.bibleSettings.primaryReferenceFontType,
                fonts = availableFonts,
                onValueChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceFontType = it)) }
                }
            )
            val previewFontFamily = remember(settings.bibleSettings.primaryReferenceFontType) {
                systemFontFamilyOrDefault(settings.bibleSettings.primaryReferenceFontType)
            }
            Text(
                text = "ABCDabcd1234",
                fontFamily = previewFontFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp)
            )
        }
    }
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.bibleSettings.primaryReferenceFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceFontSize = it)) } },
                    range = 8..72
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.bibleSettings.primaryReferenceLowerThirdFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceLowerThirdFontSize = it)) } },
                    range = 8..72
                )
            }
        }
    }
    SettingRow(stringResource(Res.string.position)) {
        PositionButtons(
            selectedPosition = settings.bibleSettings.primaryReferencePosition,
            onPositionChange = { value ->
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferencePosition = value)) }
            },
            aboveValue = Constants.POSITION_ABOVE,
            belowValue = Constants.POSITION_BELOW
        )
    }
    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.bibleSettings.primaryReferenceHorizontalAlignment,
                    onAlignmentChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceHorizontalAlignment = value)) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.bibleSettings.primaryReferenceLowerThirdHorizontalAlignment,
                    onAlignmentChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(primaryReferenceLowerThirdHorizontalAlignment = value)) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
        }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPickerField(
                color = settings.bibleSettings.secondaryBibleColor,
                onColorChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleColor = it)) }
                }
            )
            TextStyleButtons(
                bold = settings.bibleSettings.secondaryBibleBold,
                italic = settings.bibleSettings.secondaryBibleItalic,
                underline = settings.bibleSettings.secondaryBibleUnderline,
                shadow = settings.bibleSettings.secondaryBibleShadow,
                onBoldChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleBold = it)) } },
                onItalicChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleItalic = it)) } },
                onUnderlineChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleUnderline = it)) } },
                onShadowChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleShadow = it)) } }
            )
        }
    }
    SettingRow(stringResource(Res.string.font_type)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FontSettingsDropdown(
                modifier = Modifier.width(200.dp),
                value = settings.bibleSettings.secondaryBibleFontType,
                fonts = availableFonts,
                onValueChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleFontType = it)) }
                }
            )
            val previewFontFamily = remember(settings.bibleSettings.secondaryBibleFontType) {
                systemFontFamilyOrDefault(settings.bibleSettings.secondaryBibleFontType)
            }
            Text(
                text = "ABCDabcd1234",
                fontFamily = previewFontFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp)
            )
        }
    }
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.bibleSettings.secondaryBibleFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleFontSize = it)) } },
                    range = 8..72
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.bibleSettings.secondaryBibleLowerThirdFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleLowerThirdFontSize = it)) } },
                    range = 8..72
                )
            }
        }
    }
    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.bibleSettings.secondaryBibleHorizontalAlignment,
                    onAlignmentChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleHorizontalAlignment = value)) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.bibleSettings.secondaryBibleLowerThirdHorizontalAlignment,
                    onAlignmentChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryBibleLowerThirdHorizontalAlignment = value)) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Secondary Bible Reference
    SectionHeader(stringResource(Res.string.secondary_bible_reference))
    Spacer(modifier = Modifier.height(8.dp))
    SettingRow(stringResource(Res.string.color)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPickerField(
                color = settings.bibleSettings.secondaryReferenceColor,
                onColorChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceColor = it)) }
                }
            )
            TextStyleButtons(
                bold = settings.bibleSettings.secondaryReferenceBold,
                italic = settings.bibleSettings.secondaryReferenceItalic,
                underline = settings.bibleSettings.secondaryReferenceUnderline,
                shadow = settings.bibleSettings.secondaryReferenceShadow,
                onBoldChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceBold = it)) } },
                onItalicChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceItalic = it)) } },
                onUnderlineChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceUnderline = it)) } },
                onShadowChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceShadow = it)) } }
            )
        }
    }
    SettingRow(stringResource(Res.string.font_type)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FontSettingsDropdown(
                modifier = Modifier.width(200.dp),
                value = settings.bibleSettings.secondaryReferenceFontType,
                fonts = availableFonts,
                onValueChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceFontType = it)) }
                }
            )
            val previewFontFamily = remember(settings.bibleSettings.secondaryReferenceFontType) {
                systemFontFamilyOrDefault(settings.bibleSettings.secondaryReferenceFontType)
            }
            Text(
                text = "ABCDabcd1234",
                fontFamily = previewFontFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp)
            )
        }
    }
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.bibleSettings.secondaryReferenceFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceFontSize = it)) } },
                    range = 8..72
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.bibleSettings.secondaryReferenceLowerThirdFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceLowerThirdFontSize = it)) } },
                    range = 8..72
                )
            }
        }
    }
    SettingRow(stringResource(Res.string.position)) {
        PositionButtons(
            selectedPosition = settings.bibleSettings.secondaryReferencePosition,
            onPositionChange = { value ->
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferencePosition = value)) }
            },
            aboveValue = Constants.POSITION_ABOVE,
            belowValue = Constants.POSITION_BELOW
        )
    }
    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.bibleSettings.secondaryReferenceHorizontalAlignment,
                    onAlignmentChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceHorizontalAlignment = value)) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.bibleSettings.secondaryReferenceLowerThirdHorizontalAlignment,
                    onAlignmentChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(secondaryReferenceLowerThirdHorizontalAlignment = value)) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
        }
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
//    SectionHeader(stringResource(Res.string.caption_language))
//    Spacer(modifier = Modifier.height(8.dp))
//    SettingRow(stringResource(Res.string.language_source)) {
//        DropdownSettingsField(
//            value = when (settings.bibleSettings.captionLanguage) {
//                Constants.LANGUAGE_INTERFACE -> languageInterfaceStr
//                Constants.LANGUAGE_DATABASE -> languageDatabaseStr
//                else -> languageInterfaceStr
//            },
//            options = listOf(languageInterfaceStr, languageDatabaseStr),
//            onValueChange = { displayValue ->
//                val value = when (displayValue) {
//                    languageInterfaceStr -> Constants.LANGUAGE_INTERFACE
//                    languageDatabaseStr -> Constants.LANGUAGE_DATABASE
//                    else -> Constants.LANGUAGE_INTERFACE
//                }
//                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(captionLanguage = value)) }
//            }
//        )
//    }
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
    width: Dp = 120.dp,
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
