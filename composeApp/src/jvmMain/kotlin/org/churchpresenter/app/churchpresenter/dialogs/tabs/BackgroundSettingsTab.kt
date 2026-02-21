package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_color
import churchpresenter.composeapp.generated.resources.background_color_option
import churchpresenter.composeapp.generated.resources.background_default
import churchpresenter.composeapp.generated.resources.background_image
import churchpresenter.composeapp.generated.resources.background_image_option
import churchpresenter.composeapp.generated.resources.background_type
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.default_background_color
import churchpresenter.composeapp.generated.resources.default_background_color_help
import churchpresenter.composeapp.generated.resources.songs
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FileImagePicker
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource

@Composable
fun BackgroundSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Default Background Color Section
        Column {
            Text(
                text = stringResource(Res.string.default_background_color),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 2.dp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.default_background_color_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingRow(stringResource(Res.string.color)) {
                ColorPickerField(
                    color = settings.backgroundSettings.defaultBackgroundColor,
                    onColorChange = {
                        onSettingsChange { s ->
                            s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundColor = it))
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Two Column Layout for Bible and Songs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Column - Bible Background
            Column(
                modifier = Modifier.weight(1f)
            ) {
                BibleBackgroundColumn(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )
            }

            // Vertical Divider
            HorizontalDivider(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Right Column - Song Background
            Column(
                modifier = Modifier.weight(1f)
            ) {
                SongBackgroundColumn(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )
            }
        }
    }
}

@Composable
private fun BibleBackgroundColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    // String resources
    val backgroundDefaultStr = stringResource(Res.string.background_default)
    val backgroundColorStr = stringResource(Res.string.background_color_option)
    val backgroundImageStr = stringResource(Res.string.background_image_option)

    SectionHeader(stringResource(Res.string.bible))
    Spacer(modifier = Modifier.height(16.dp))

    // Background Type Selection
    Text(
        text = stringResource(Res.string.background_type),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Radio buttons for background type
    BackgroundTypeRadioGroup(
        selectedType = settings.backgroundSettings.bibleBackground.backgroundType,
        onTypeSelected = { type ->
            onSettingsChange { s ->
                s.copy(backgroundSettings = s.backgroundSettings.copy(
                    bibleBackground = s.backgroundSettings.bibleBackground.copy(backgroundType = type)
                ))
            }
        },
        defaultLabel = backgroundDefaultStr,
        colorLabel = backgroundColorStr,
        imageLabel = backgroundImageStr
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Background Color
    if (settings.backgroundSettings.bibleBackground.backgroundType == Constants.BACKGROUND_COLOR) {
        SettingRow(stringResource(Res.string.background_color)) {
            ColorPickerField(
                color = settings.backgroundSettings.bibleBackground.backgroundColor,
                onColorChange = {
                    onSettingsChange { s ->
                        s.copy(backgroundSettings = s.backgroundSettings.copy(
                            bibleBackground = s.backgroundSettings.bibleBackground.copy(backgroundColor = it)
                        ))
                    }
                }
            )
        }
    }

    // Background Image
    if (settings.backgroundSettings.bibleBackground.backgroundType == Constants.BACKGROUND_IMAGE) {
        SettingRow(stringResource(Res.string.background_image)) {
            FileImagePicker(
                imagePath = settings.backgroundSettings.bibleBackground.backgroundImage,
                onImagePathChange = {
                    onSettingsChange { s ->
                        s.copy(backgroundSettings = s.backgroundSettings.copy(
                            bibleBackground = s.backgroundSettings.bibleBackground.copy(backgroundImage = it)
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SongBackgroundColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    // String resources
    val backgroundDefaultStr = stringResource(Res.string.background_default)
    val backgroundColorStr = stringResource(Res.string.background_color_option)
    val backgroundImageStr = stringResource(Res.string.background_image_option)

    SectionHeader(stringResource(Res.string.songs))
    Spacer(modifier = Modifier.height(16.dp))

    // Background Type Selection
    Text(
        text = stringResource(Res.string.background_type),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Radio buttons for background type
    BackgroundTypeRadioGroup(
        selectedType = settings.backgroundSettings.songBackground.backgroundType,
        onTypeSelected = { type ->
            onSettingsChange { s ->
                s.copy(backgroundSettings = s.backgroundSettings.copy(
                    songBackground = s.backgroundSettings.songBackground.copy(backgroundType = type)
                ))
            }
        },
        defaultLabel = backgroundDefaultStr,
        colorLabel = backgroundColorStr,
        imageLabel = backgroundImageStr
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Background Color
    if (settings.backgroundSettings.songBackground.backgroundType == Constants.BACKGROUND_COLOR) {
        SettingRow(stringResource(Res.string.background_color)) {
            ColorPickerField(
                color = settings.backgroundSettings.songBackground.backgroundColor,
                onColorChange = {
                    onSettingsChange { s ->
                        s.copy(backgroundSettings = s.backgroundSettings.copy(
                            songBackground = s.backgroundSettings.songBackground.copy(backgroundColor = it)
                        ))
                    }
                }
            )
        }
    }

    // Background Image
    if (settings.backgroundSettings.songBackground.backgroundType == Constants.BACKGROUND_IMAGE) {
        SettingRow(stringResource(Res.string.background_image)) {
            FileImagePicker(
                imagePath = settings.backgroundSettings.songBackground.backgroundImage,
                onImagePathChange = {
                    onSettingsChange { s ->
                        s.copy(backgroundSettings = s.backgroundSettings.copy(
                            songBackground = s.backgroundSettings.songBackground.copy(backgroundImage = it)
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BackgroundTypeRadioGroup(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    defaultLabel: String,
    colorLabel: String,
    imageLabel: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Default option
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = selectedType == Constants.BACKGROUND_DEFAULT,
                onClick = { onTypeSelected(Constants.BACKGROUND_DEFAULT) }
            )
            Text(
                text = defaultLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Color option
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = selectedType == Constants.BACKGROUND_COLOR,
                onClick = { onTypeSelected(Constants.BACKGROUND_COLOR) }
            )
            Text(
                text = colorLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Image option
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = selectedType == Constants.BACKGROUND_IMAGE,
                onClick = { onTypeSelected(Constants.BACKGROUND_IMAGE) }
            )
            Text(
                text = imageLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 2.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    width: Dp = 140.dp,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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