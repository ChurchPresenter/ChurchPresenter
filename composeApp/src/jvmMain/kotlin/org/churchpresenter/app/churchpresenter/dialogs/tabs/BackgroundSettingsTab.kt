package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.full_screen
import churchpresenter.composeapp.generated.resources.songs
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FileImagePicker
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.BackgroundConfig
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.BackgroundSettingsViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun BackgroundSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val viewModel = remember { BackgroundSettingsViewModel() }

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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 2.dp)
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
                    onColorChange = { viewModel.updateDefaultColor(it, onSettingsChange) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2-group layout: Bible | Songs, each with Full Screen + Lower Third sub-columns
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Bible group ──────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                GroupHeader(stringResource(Res.string.bible))
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        BackgroundColumn(
                            subtitle = stringResource(Res.string.full_screen),
                            config = settings.backgroundSettings.bibleBackground,
                            onConfigChange = { viewModel.updateBibleBackground(it, onSettingsChange) }
                        )
                    }
                    ColumnDivider()
                    Column(modifier = Modifier.weight(1f)) {
                        BackgroundColumn(
                            subtitle = stringResource(Res.string.display_lower_third),
                            config = settings.backgroundSettings.bibleLowerThirdBackground,
                            onConfigChange = { viewModel.updateBibleLowerThirdBackground(it, onSettingsChange) }
                        )
                    }
                }
            }

            ColumnDivider()

            // ── Songs group ──────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                GroupHeader(stringResource(Res.string.songs))
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        BackgroundColumn(
                            subtitle = stringResource(Res.string.full_screen),
                            config = settings.backgroundSettings.songBackground,
                            onConfigChange = { viewModel.updateSongBackground(it, onSettingsChange) }
                        )
                    }
                    ColumnDivider()
                    Column(modifier = Modifier.weight(1f)) {
                        BackgroundColumn(
                            subtitle = stringResource(Res.string.display_lower_third),
                            config = settings.backgroundSettings.songLowerThirdBackground,
                            onConfigChange = { viewModel.updateSongLowerThirdBackground(it, onSettingsChange) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 2.dp)
    }
}

@Composable
private fun BackgroundColumn(
    subtitle: String,
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit
) {
    val backgroundDefaultStr = stringResource(Res.string.background_default)
    val backgroundColorStr   = stringResource(Res.string.background_color_option)
    val backgroundImageStr   = stringResource(Res.string.background_image_option)

    SectionHeader(subtitle)
    Spacer(modifier = Modifier.height(10.dp))

    Text(
        text = stringResource(Res.string.background_type),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))

    BackgroundTypeRadioGroup(
        selectedType = config.backgroundType,
        onTypeSelected = { onConfigChange(config.copy(backgroundType = it)) },
        defaultLabel = backgroundDefaultStr,
        colorLabel   = backgroundColorStr,
        imageLabel   = backgroundImageStr
    )

    Spacer(modifier = Modifier.height(10.dp))

    when (config.backgroundType) {
        Constants.BACKGROUND_COLOR -> {
            SettingRow(stringResource(Res.string.background_color)) {
                ColorPickerField(
                    color = config.backgroundColor,
                    onColorChange = { onConfigChange(config.copy(backgroundColor = it)) }
                )
            }
        }
        Constants.BACKGROUND_IMAGE -> {
            SettingRow(stringResource(Res.string.background_image)) {
                FileImagePicker(
                    imagePath = config.backgroundImage,
                    onImagePathChange = { onConfigChange(config.copy(backgroundImage = it)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        else -> {
            // Default — nothing extra to show
        }
    }
}

@Composable
private fun ColumnDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(400.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun BackgroundTypeRadioGroup(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    defaultLabel: String,
    colorLabel: String,
    imageLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(
            Constants.BACKGROUND_DEFAULT to defaultLabel,
            Constants.BACKGROUND_COLOR   to colorLabel,
            Constants.BACKGROUND_IMAGE   to imageLabel
        ).forEach { (type, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = selectedType == type,
                    onClick = { onTypeSelected(type) },
                    modifier = Modifier.size(28.dp),
                    colors = RadioButtonDefaults.colors()
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

@Composable
private fun SettingRow(
    label: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}