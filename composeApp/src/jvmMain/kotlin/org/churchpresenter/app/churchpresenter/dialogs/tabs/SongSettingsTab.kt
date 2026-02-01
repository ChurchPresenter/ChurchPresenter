package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.jetbrains.compose.resources.stringResource
import java.awt.*
import java.io.File
import javax.swing.*

// Constants for dropdown values that can't be localized
private object DropdownValues {
    const val NONE = "None"
    const val FIRST_PAGE = "First Page"
    const val EVERY_PAGE = "Every Page"
    const val TOP = "Top"
    const val MIDDLE = "Middle"
    const val BOTTOM = "Bottom"
    const val LEFT = "Left"
    const val CENTER = "Center"
    const val RIGHT = "Right"
    const val TOP_LEFT = "Top Left"
    const val TOP_RIGHT = "Top Right"
    const val BOTTOM_LEFT = "Bottom Left"
    const val BOTTOM_RIGHT = "Bottom Right"
}

@Composable
fun SongSettingsTab(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit = {}
) {
    val availableFonts =
        remember { GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList() }

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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                LeftColumn(settings, onSettingsChange, availableFonts)
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
    availableFonts: List<String>
) {
    // Store string resources to avoid calling stringResource in callbacks
    val noneStr = stringResource(Res.string.none)
    val firstPageStr = stringResource(Res.string.first_page)
    val everyPageStr = stringResource(Res.string.every_page)
    val topStr = stringResource(Res.string.top)
    val middleStr = stringResource(Res.string.middle)
    val bottomStr = stringResource(Res.string.bottom)
    val leftStr = stringResource(Res.string.left)
    val centerStr = stringResource(Res.string.center)
    val rightStr = stringResource(Res.string.right)

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
            backgroundColor = Color(0xFF3498DB),
            onClick = {
                SwingUtilities.invokeLater {
                    val dirChooser = JFileChooser().apply {
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        if (settings.songSettings.storageDirectory.isNotEmpty()) {
                            currentDirectory = File(settings.songSettings.storageDirectory)
                        }
                    }
                    val result = dirChooser.showOpenDialog(null)
                    if (result == JFileChooser.APPROVE_OPTION) {
                        onSettingsChange.invoke(
                            settings.copy(songSettings = settings.songSettings.copy(storageDirectory = dirChooser.selectedFile.absolutePath))
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
            if (settings.songSettings.songFiles.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_song_files),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                settings.songSettings.songFiles.forEach { file ->
                    Text(
                        text = file,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 8.dp)
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
            backgroundColor = Color(0xFF2ECC71),
            onClick = {
                SwingUtilities.invokeLater {
                    val fileChooser = JFileChooser().apply {
                        fileSelectionMode = JFileChooser.FILES_ONLY
                        isMultiSelectionEnabled = true
                        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                            "Song Files (*.spb, *.sps)", "spb", "sps"
                        )
                    }
                    val result = fileChooser.showOpenDialog(null)
                    if (result == JFileChooser.APPROVE_OPTION) {
                        val newFiles = fileChooser.selectedFiles.map { it.absolutePath }
                        val updatedFiles = (settings.songSettings.songFiles + newFiles).distinct()
                        onSettingsChange.invoke(
                            settings.copy(
                                songSettings = settings.songSettings.copy(songFiles = updatedFiles)
                            )
                        )
                    }
                }
            }
        )

        ModernButton(
            text = stringResource(Res.string.remove_song_file),
            backgroundColor = Color(0xFFE74C3C),
            onClick = {
                if (settings.songSettings.songFiles.isNotEmpty()) {
                    val updatedFiles = settings.songSettings.songFiles.drop(1)
                    onSettingsChange.invoke(
                        settings.copy(songSettings = settings.songSettings.copy(songFiles = updatedFiles))
                    )
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
                DropdownValues.NONE -> noneStr
                DropdownValues.FIRST_PAGE -> firstPageStr
                DropdownValues.EVERY_PAGE -> everyPageStr
                else -> firstPageStr
            },
            options = listOf(noneStr, firstPageStr, everyPageStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    noneStr -> DropdownValues.NONE
                    firstPageStr -> DropdownValues.FIRST_PAGE
                    everyPageStr -> DropdownValues.EVERY_PAGE
                    else -> DropdownValues.FIRST_PAGE
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
        FontSettingsDropdown(
            value = settings.songSettings.titleFontType,
            fonts = availableFonts,
            onValueChange = {
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(titleFontType = it))
                )
            }
        )
    }

    MinMaxRow(
        minValue = settings.songSettings.titleMinFontSize,
        maxValue = settings.songSettings.titleMaxFontSize,
        onMinChange = {
            onSettingsChange.invoke(
                settings.copy(songSettings = settings.songSettings.copy(titleMinFontSize = it))
            )
        },
        onMaxChange = {
            onSettingsChange.invoke(
                settings.copy(songSettings = settings.songSettings.copy(titleMaxFontSize = it))
            )
        }
    )

    AlphaSlider(
        value = settings.songSettings.titleAlpha,
        onValueChange = {
            onSettingsChange.invoke(
                settings.copy(songSettings = settings.songSettings.copy(titleAlpha = it))
            )
        }
    )

    SettingRow(stringResource(Res.string.vertical_alignment), width = 200.dp) {
        DropdownSettingsField(
            value = when (settings.songSettings.titleAlignment) {
                DropdownValues.TOP -> topStr
                DropdownValues.MIDDLE -> middleStr
                DropdownValues.BOTTOM -> bottomStr
                else -> middleStr
            },
            options = listOf(topStr, middleStr, bottomStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    topStr -> DropdownValues.TOP
                    middleStr -> DropdownValues.MIDDLE
                    bottomStr -> DropdownValues.BOTTOM
                    else -> DropdownValues.MIDDLE
                }
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(titleAlignment = storedValue))
                )
            }
        )
    }

    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        DropdownSettingsField(
            value = when (settings.songSettings.titleHorizontalAlignment) {
                DropdownValues.LEFT -> leftStr
                DropdownValues.CENTER -> centerStr
                DropdownValues.RIGHT -> rightStr
                else -> centerStr
            },
            options = listOf(leftStr, centerStr, rightStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    leftStr -> DropdownValues.LEFT
                    centerStr -> DropdownValues.CENTER
                    rightStr -> DropdownValues.RIGHT
                    else -> DropdownValues.CENTER
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
        FontSettingsDropdown(
            value = settings.songSettings.lyricsFontType,
            fonts = availableFonts,
            onValueChange = {
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(lyricsFontType = it))
                )
            }
        )
    }

    MinMaxRow(
        minValue = settings.songSettings.lyricsMinFontSize,
        maxValue = settings.songSettings.lyricsMaxFontSize,
        onMinChange = {
            onSettingsChange.invoke(
                settings.copy(songSettings = settings.songSettings.copy(lyricsMinFontSize = it))
            )
        },
        onMaxChange = {
            onSettingsChange.invoke(
                settings.copy(songSettings = settings.songSettings.copy(lyricsMaxFontSize = it))
            )
        }
    )

    AlphaSlider(
        value = settings.songSettings.lyricsAlpha,
        onValueChange = {
            onSettingsChange.invoke(
                settings.copy(songSettings = settings.songSettings.copy(lyricsAlpha = it))
            )
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = settings.songSettings.wordWrap,
            onCheckedChange = {
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(wordWrap = it))
                )
            }
        )
        Text(
            text = stringResource(Res.string.word_wrap),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(5.dp))

    SettingRow(stringResource(Res.string.vertical_alignment), width = 200.dp) {
        DropdownSettingsField(
            value = when (settings.songSettings.lyricsAlignment) {
                DropdownValues.TOP -> topStr
                DropdownValues.MIDDLE -> middleStr
                DropdownValues.BOTTOM -> bottomStr
                else -> middleStr
            },
            options = listOf(topStr, middleStr, bottomStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    topStr -> DropdownValues.TOP
                    middleStr -> DropdownValues.MIDDLE
                    bottomStr -> DropdownValues.BOTTOM
                    else -> DropdownValues.MIDDLE
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
                DropdownValues.LEFT -> leftStr
                DropdownValues.CENTER -> centerStr
                DropdownValues.RIGHT -> rightStr
                else -> centerStr
            },
            options = listOf(leftStr, centerStr, rightStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    leftStr -> DropdownValues.LEFT
                    centerStr -> DropdownValues.CENTER
                    rightStr -> DropdownValues.RIGHT
                    else -> DropdownValues.CENTER
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

    println("Andrei: ${settings.songSettings.songNumberFontSize}")
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = settings.songSettings.songNumberFirstPageOnly,
            onCheckedChange = {
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(songNumberFirstPageOnly = it))
                )
            }
        )
        Text(
            text = stringResource(Res.string.show_on_first_page_only),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(5.dp))

    val pos = when (settings.songSettings.songNumberPosition) {
        DropdownValues.TOP_LEFT -> topLeftStr
        DropdownValues.TOP_RIGHT -> topRightStr
        DropdownValues.BOTTOM_LEFT -> bottomLeftStr
        DropdownValues.BOTTOM_RIGHT -> bottomRightStr
        else -> bottomRightStr
    }

    var initialPosition by remember { mutableStateOf(pos) }
    SettingRow(stringResource(Res.string.position_on_screen), width = 200.dp) {
        DropdownSettingsField(
            value = initialPosition,
            options = listOf(topLeftStr, topRightStr, bottomLeftStr, bottomRightStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    topLeftStr -> DropdownValues.TOP_LEFT
                    topRightStr -> DropdownValues.TOP_RIGHT
                    bottomLeftStr -> DropdownValues.BOTTOM_LEFT
                    bottomRightStr -> DropdownValues.BOTTOM_RIGHT
                    else -> DropdownValues.BOTTOM_RIGHT
                }
                initialPosition = storedValue
                onSettingsChange.invoke(
                    settings.copy(songSettings = settings.songSettings.copy(songNumberPosition = storedValue))
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

@Composable
private fun AlphaSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    var mutableValue by remember { mutableStateOf(value) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.alpha),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(120.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = mutableValue,
                onValueChange = {
                    mutableValue = it
                    onValueChange.invoke(it)
                },
                steps = 20,
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(0, 25, 50, 75, 100).forEach { tick ->
                    Text(
                        text = tick.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
