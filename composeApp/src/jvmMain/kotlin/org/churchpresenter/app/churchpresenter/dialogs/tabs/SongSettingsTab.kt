package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.*
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager
import org.jetbrains.compose.resources.stringResource
import java.awt.Window
import javax.swing.SwingUtilities


@Composable
fun SongSettingsTab(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit = {}
) {
    val availableFonts = remember { Utils.getAvailableSystemFonts() }
    val fileManager = remember { FileManager() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Left Column
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .widthIn(min = 400.dp, max = 450.dp)
                    .heightIn(min = 600.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp)
            ) {
                LeftColumn(settings, onSettingsChange, availableFonts, fileManager)
            }

            // Right Column
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .widthIn(min = 400.dp, max = 450.dp)
                    .heightIn(min = 600.dp)
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
    onSettingsChange: (AppSettings) -> Unit,
    availableFonts: List<String>,
    fileManager: FileManager
) {
    // State to trigger refresh of file list
    var refreshTrigger by remember { mutableStateOf(0) }

    // State for selected file
    var selectedFile by remember { mutableStateOf<String?>(null) }

    // Store string resources to avoid calling stringResource in callbacks
    val noneStr = stringResource(Res.string.none)
    val firstPageStr = stringResource(Res.string.first_page)
    val everyPageStr = stringResource(Res.string.every_page)
    val aboveVerseStr = stringResource(Res.string.above_verse)
    val belowVerseStr = stringResource(Res.string.below_verse)
    val leftStr = stringResource(Res.string.left)
    val centerStr = stringResource(Res.string.center)
    val rightStr = stringResource(Res.string.right)

    // Dialog message strings
    val pleaseSelectDirectoryStr = stringResource(Res.string.please_select_directory_first)
    val noDirectorySelectedStr = stringResource(Res.string.no_directory_selected)
    val pleaseSelectFileStr = stringResource(Res.string.please_select_file_first)
    val noFileSelectedStr = stringResource(Res.string.no_file_selected_title)
    val confirmDeleteStr = stringResource(Res.string.confirm_delete)
    val confirmDeleteFileTemplate = stringResource(Res.string.confirm_delete_file)
    val importErrorStr = stringResource(Res.string.import_error)
    val deleteErrorStr = stringResource(Res.string.delete_error)

    // Storage Directory Section
    SectionHeader(stringResource(Res.string.storage_directory))

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = if (settings.songSettings.storageDirectory.isNotEmpty()) settings.songSettings.storageDirectory
            else stringResource(Res.string.no_directory_selected),
            modifier = Modifier
                .weight(1f)
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
                        currentDirectory = settings.songSettings.storageDirectory,
                        parentWindow = parentWindow
                    )
                    selectedDir?.let { dir ->
                        onSettingsChange.invoke(
                            settings.copy(songSettings = settings.songSettings.copy(storageDirectory = dir))
                        )
                    }
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(15.dp))

    // Song Files Section
    SectionHeader(stringResource(Res.string.song_files))

    Spacer(modifier = Modifier.height(8.dp))

    // Use FileManager to get song files
    val songFilesInDirectory = remember(settings.songSettings.storageDirectory, refreshTrigger) {
        fileManager.getSongFilesInDirectory(settings.songSettings.storageDirectory)
    }

    Spacer(modifier = Modifier.height(8.dp))


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            if (songFilesInDirectory.isEmpty()) {
                Text(
                    text = if (settings.songSettings.storageDirectory.isEmpty()) {
                        stringResource(Res.string.no_directory_selected)
                    } else {
                        stringResource(Res.string.no_song_files)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                songFilesInDirectory.forEach { fileName ->
                    val isSelected = fileName == selectedFile
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { selectedFile = fileName }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModernButton(
            text = stringResource(Res.string.import_song_file),
            backgroundColor = MaterialTheme.colorScheme.inverseSurface,
            onClick = {
                SwingUtilities.invokeLater {
                    val parentWindow = Window.getWindows().firstOrNull { it.isActive }

                    // Check if directory is selected
                    if (settings.songSettings.storageDirectory.isEmpty()) {
                        fileManager.showWarning(
                            message = pleaseSelectDirectoryStr,
                            title = noDirectorySelectedStr,
                            parentWindow = parentWindow
                        )
                        return@invokeLater
                    }

                    // Choose files to import
                    val selectedFiles = fileManager.chooseSongFiles(parentWindow)
                    selectedFiles?.let { files ->
                        // Import files
                        val errors = fileManager.importFiles(
                            sourceFiles = files,
                            targetDirectory = settings.songSettings.storageDirectory
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
                        refreshTrigger++
                    }
                }
            }
        )

        ModernButton(
            text = stringResource(Res.string.remove_song_file),
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
                            directory = settings.songSettings.storageDirectory,
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
                            selectedFile = null
                            refreshTrigger++
                        }
                    }
                }
            }
        )
    }


    Spacer(modifier = Modifier.height(20.dp))

    // Title Section
    SectionHeader(stringResource(Res.string.title))

    Spacer(modifier = Modifier.height(8.dp))

    SettingRow(stringResource(Res.string.show_title)) {
        DropdownSettingsField(
            value = when (settings.songSettings.titleDisplay) {
                Constants.NONE -> noneStr
                Constants.FIRST_PAGE -> firstPageStr
                Constants.EVERY_PAGE -> everyPageStr
                else -> firstPageStr
            },
            options = listOf(noneStr, firstPageStr, everyPageStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    noneStr -> Constants.NONE
                    firstPageStr -> Constants.FIRST_PAGE
                    everyPageStr -> Constants.EVERY_PAGE
                    else -> Constants.FIRST_PAGE
                }
                onSettingsChange.invoke(settings.copy(songSettings = settings.songSettings.copy(titleDisplay = storedValue)))
            }
        )
    }

    SettingRow(stringResource(Res.string.font_size)) {
        NumberSettingsTextField(
            initialText = settings.songSettings.titleFontSize,
            onValueChange = {
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(titleFontSize = it))
                )
            },
            range = 8..72
        )
    }

    SettingRow(stringResource(Res.string.font_type)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FontSettingsDropdown(
                modifier = Modifier.width(200.dp),
                value = settings.songSettings.titleFontType,
                fonts = availableFonts,
                onValueChange = {
                    onSettingsChange.invoke(
                        settings.copy(songSettings = settings.songSettings.copy(titleFontType = it))
                    )
                }
            )
            val previewFontFamily = remember(settings.songSettings.titleFontType) {
                systemFontFamilyOrDefault(settings.songSettings.titleFontType)
            }
            Text(
                text = "ABCDabcd1234",
                fontFamily = previewFontFamily,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp)
            )
        }
    }

//    MinMaxRow(
//        minValue = settings.songSettings.titleMinFontSize,
//        maxValue = settings.songSettings.titleMaxFontSize,
//        onMinChange = {
//            onSettingsChange.invoke(
//                settings.copy(songSettings = settings.songSettings.copy(titleMinFontSize = it))
//            )
//        },
//        onMaxChange = {
//            onSettingsChange.invoke(
//                settings.copy(songSettings = settings.songSettings.copy(titleMaxFontSize = it))
//            )
//        }
//    )

    SettingRow(stringResource(Res.string.color)) {
        ColorPickerField(
            color = settings.songSettings.titleColor,
            onColorChange = {
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(titleColor = it))
                )
            }
        )
    }

    SettingRow(stringResource(Res.string.vertical_alignment), width = 200.dp) {
        DropdownSettingsField(
            value = when (settings.songSettings.titlePosition) {
                Constants.BELOW_VERSE -> belowVerseStr
                else -> aboveVerseStr
            },
            options = listOf(belowVerseStr, aboveVerseStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    belowVerseStr -> Constants.BELOW_VERSE
                    else -> Constants.ABOVE_VERSE
                }
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(titlePosition = storedValue))
                )
            }
        )
    }

    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        DropdownSettingsField(
            value = when (settings.songSettings.titleHorizontalAlignment) {
                Constants.LEFT -> leftStr
                Constants.CENTER -> centerStr
                Constants.RIGHT -> rightStr
                else -> centerStr
            },
            options = listOf(leftStr, centerStr, rightStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    leftStr -> Constants.LEFT
                    centerStr -> Constants.CENTER
                    rightStr -> Constants.RIGHT
                    else -> Constants.CENTER
                }
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(titleHorizontalAlignment = storedValue))
                )
            }
        )
    }
}

@Composable
private fun RightColumn(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    availableFonts: List<String>
) {
    // Store string resources to avoid calling stringResource in callbacks
    val topStr = stringResource(Res.string.top)
    val middleStr = stringResource(Res.string.middle)
    val bottomStr = stringResource(Res.string.bottom)
    val leftStr = stringResource(Res.string.left)
    val centerStr = stringResource(Res.string.center)
    val rightStr = stringResource(Res.string.right)
    val topLeftStr = stringResource(Res.string.top_left)
    val topRightStr = stringResource(Res.string.top_right)
    val bottomLeftStr = stringResource(Res.string.bottom_left)
    val bottomRightStr = stringResource(Res.string.bottom_right)
    val noneStr = stringResource(Res.string.none)
    val firstPageStr = stringResource(Res.string.first_page)
    val everyPageStr = stringResource(Res.string.every_page)

    // Lyrics Section
    SectionHeader(stringResource(Res.string.lyrics))

    Spacer(modifier = Modifier.height(8.dp))

    SettingRow(stringResource(Res.string.font_size)) {
        NumberSettingsTextField(
            initialText = settings.songSettings.lyricsFontSize,
            onValueChange = {
                onSettingsChange.invoke(settings.copy(songSettings = settings.songSettings.copy(lyricsFontSize = it)))
            },
            range = 8..72
        )
    }

    SettingRow(stringResource(Res.string.font_type)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FontSettingsDropdown(
                modifier = Modifier.width(200.dp),
                value = settings.songSettings.lyricsFontType,
                fonts = availableFonts,
                onValueChange = {
                    onSettingsChange.invoke(
                        settings.copy(songSettings = settings.songSettings.copy(lyricsFontType = it))
                    )
                }
            )
            val previewFontFamily = remember(settings.songSettings.lyricsFontType) {
                systemFontFamilyOrDefault(settings.songSettings.lyricsFontType)
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

//    MinMaxRow(
//        minValue = settings.songSettings.lyricsMinFontSize,
//        maxValue = settings.songSettings.lyricsMaxFontSize,
//        onMinChange = {
//            onSettingsChange.invoke(
//                settings.copy(songSettings = settings.songSettings.copy(lyricsMinFontSize = it))
//            )
//        },
//        onMaxChange = {
//            onSettingsChange.invoke(
//                settings.copy(songSettings = settings.songSettings.copy(lyricsMaxFontSize = it))
//            )
//        }
//    )

    SettingRow(stringResource(Res.string.color)) {
        ColorPickerField(
            color = settings.songSettings.lyricsColor,
            onColorChange = {
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(lyricsColor = it))
                )
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var initialWordWrapValue by remember { mutableStateOf(settings.songSettings.wordWrap) }
        Checkbox(
            checked = initialWordWrapValue,
            onCheckedChange = {
                initialWordWrapValue = it
                onSettingsChange.invoke(
                    settings.copy(
                        songSettings = settings.songSettings.copy(wordWrap = it)
                    )
                )
            }
        )
        Text(
            text = stringResource(Res.string.word_wrap),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(5.dp))

    SettingRow(stringResource(Res.string.vertical_alignment), width = 200.dp) {
        DropdownSettingsField(
            value = when (settings.songSettings.lyricsAlignment) {
                Constants.TOP -> topStr
                Constants.MIDDLE -> middleStr
                Constants.BOTTOM -> bottomStr
                else -> middleStr
            },
            options = listOf(topStr, middleStr, bottomStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    topStr -> Constants.TOP
                    middleStr -> Constants.MIDDLE
                    bottomStr -> Constants.BOTTOM
                    else -> Constants.MIDDLE
                }
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(lyricsAlignment = storedValue))
                )
            }
        )
    }

    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        DropdownSettingsField(
            value = when (settings.songSettings.lyricsHorizontalAlignment) {
                Constants.LEFT -> leftStr
                Constants.CENTER -> centerStr
                Constants.RIGHT -> rightStr
                else -> centerStr
            },
            options = listOf(leftStr, centerStr, rightStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    leftStr -> Constants.LEFT
                    centerStr -> Constants.CENTER
                    rightStr -> Constants.RIGHT
                    else -> Constants.CENTER
                }
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(lyricsHorizontalAlignment = storedValue))
                )
            }
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Song Number Section
    SectionHeader(stringResource(Res.string.song_number))

    Spacer(modifier = Modifier.height(8.dp))

    SettingRow(stringResource(Res.string.font_size)) {
        NumberSettingsTextField(
            initialText = settings.songSettings.songNumberFontSize,
            onValueChange = {
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(songNumberFontSize = it))
                )
            },
            range = 8..48
        )
    }

    SettingRow(stringResource(Res.string.show_number)) {
        DropdownSettingsField(
            value = when (settings.songSettings.titleDisplay) {
                Constants.NONE -> noneStr
                Constants.FIRST_PAGE -> firstPageStr
                Constants.EVERY_PAGE -> everyPageStr
                else -> firstPageStr
            },
            options = listOf(noneStr, firstPageStr, everyPageStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    noneStr -> Constants.NONE
                    firstPageStr -> Constants.FIRST_PAGE
                    everyPageStr -> Constants.EVERY_PAGE
                    else -> Constants.FIRST_PAGE
                }
                onSettingsChange.invoke(settings.copy(songSettings = settings.songSettings.copy(showNumber = storedValue)))
            }
        )
    }

    Spacer(modifier = Modifier.height(5.dp))

    val pos = when (settings.songSettings.songNumberPosition) {
        Constants.TOP_LEFT -> topLeftStr
        Constants.TOP_RIGHT -> topRightStr
        Constants.BOTTOM_LEFT -> bottomLeftStr
        Constants.BOTTOM_RIGHT -> bottomRightStr
        else -> bottomRightStr
    }

    var initialPosition by remember { mutableStateOf(pos) }
    SettingRow(stringResource(Res.string.position_on_screen), width = 200.dp) {
        DropdownSettingsField(
            value = initialPosition,
            options = listOf(topLeftStr, topRightStr, bottomLeftStr, bottomRightStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    topLeftStr -> Constants.TOP_LEFT
                    topRightStr -> Constants.TOP_RIGHT
                    bottomLeftStr -> Constants.BOTTOM_LEFT
                    bottomRightStr -> Constants.BOTTOM_RIGHT
                    else -> Constants.BOTTOM_RIGHT
                }
                initialPosition = storedValue
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(songNumberPosition = storedValue))
                )
            }
        )
    }

    SettingRow(stringResource(Res.string.color)) {
        ColorPickerField(
            color = settings.songSettings.songNumberColor,
            onColorChange = {
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(songNumberColor = it))
                )
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
    width: Dp = 120.dp,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(width)
        )
        content()
    }
}

@Composable
private fun ModernButton(
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isHovered) backgroundColor.copy(alpha = 0.8f) else backgroundColor,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun MinMaxRow(
    minValue: Int,
    maxValue: Int,
    onMinChange: (Int) -> Unit,
    onMaxChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.min),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(120.dp)
        )

        NumberSettingsTextField(
            initialText = minValue,
            onValueChange = onMinChange,
            range = 8..72
        )

        Spacer(modifier = Modifier.width(5.dp))

        Text(
            text = stringResource(Res.string.max),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        NumberSettingsTextField(
            initialText = maxValue,
            onValueChange = onMaxChange,
            range = 8..72
        )
    }
}
