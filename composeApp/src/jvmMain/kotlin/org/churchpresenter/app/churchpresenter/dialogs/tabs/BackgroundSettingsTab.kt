package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
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
import churchpresenter.composeapp.generated.resources.background_transparent_option
import churchpresenter.composeapp.generated.resources.background_type
import churchpresenter.composeapp.generated.resources.background_video
import churchpresenter.composeapp.generated.resources.background_video_option
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.default_background_color
import churchpresenter.composeapp.generated.resources.default_background_color_help
import churchpresenter.composeapp.generated.resources.default_lower_third_background
import churchpresenter.composeapp.generated.resources.default_lower_third_background_help
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.full_screen
import churchpresenter.composeapp.generated.resources.gradient_bottom_color
import churchpresenter.composeapp.generated.resources.gradient_enabled
import churchpresenter.composeapp.generated.resources.gradient_opacity
import churchpresenter.composeapp.generated.resources.gradient_position
import churchpresenter.composeapp.generated.resources.gradient_top_color
import churchpresenter.composeapp.generated.resources.songs
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FileImagePicker
import org.churchpresenter.app.churchpresenter.composables.FileVideoPicker
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Default Full Screen + Default Lower Third
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 1: Default Full Screen Background
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GroupHeader(stringResource(Res.string.default_background_color))
                    Text(
                        text = stringResource(Res.string.default_background_color_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    BackgroundTypeRadioGroup(
                        selectedType = settings.backgroundSettings.defaultBackgroundType,
                        onTypeSelected = { type ->
                            onSettingsChange { s ->
                                s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundType = type))
                            }
                        },
                        defaultLabel = stringResource(Res.string.background_color_option),
                        colorLabel = stringResource(Res.string.background_image_option),
                        imageLabel = stringResource(Res.string.background_video_option),
                        videoLabel = stringResource(Res.string.background_transparent_option),
                        types = listOf(
                            Constants.BACKGROUND_COLOR,
                            Constants.BACKGROUND_IMAGE,
                            Constants.BACKGROUND_VIDEO,
                            Constants.BACKGROUND_TRANSPARENT
                        ),
                        disabledTypes = if (!isVlcAvailable) setOf(Constants.BACKGROUND_VIDEO) else emptySet()
                    )
                    when (settings.backgroundSettings.defaultBackgroundType) {
                        Constants.BACKGROUND_COLOR -> {
                            SettingRow(stringResource(Res.string.color)) {
                                ColorPickerField(
                                    color = settings.backgroundSettings.defaultBackgroundColor,
                                    onColorChange = { viewModel.updateDefaultColor(it, onSettingsChange) }
                                )
                            }
                            OpacitySlider(settings.backgroundSettings.defaultBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundOpacity = opacity))
                                }
                            }
                        }
                        Constants.BACKGROUND_IMAGE -> {
                            SettingRow(stringResource(Res.string.background_image)) {
                                FileImagePicker(
                                    imagePath = settings.backgroundSettings.defaultBackgroundImage,
                                    onImagePathChange = { path ->
                                        onSettingsChange { s ->
                                            s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundImage = path))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            OpacitySlider(settings.backgroundSettings.defaultBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundOpacity = opacity))
                                }
                            }
                        }
                        Constants.BACKGROUND_VIDEO -> {
                            SettingRow(stringResource(Res.string.background_video)) {
                                FileVideoPicker(
                                    videoPath = settings.backgroundSettings.defaultBackgroundVideo,
                                    onVideoPathChange = { path ->
                                        onSettingsChange { s ->
                                            s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundVideo = path))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            OpacitySlider(settings.backgroundSettings.defaultBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundOpacity = opacity))
                                }
                            }
                        }
                    }
                }

                // Card 2: Default Lower Third Background
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GroupHeader(stringResource(Res.string.default_lower_third_background))
                    Text(
                        text = stringResource(Res.string.default_lower_third_background_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    BackgroundTypeRadioGroup(
                        selectedType = settings.backgroundSettings.defaultLowerThirdBackgroundType,
                        onTypeSelected = { type ->
                            onSettingsChange { s ->
                                s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundType = type))
                            }
                        },
                        defaultLabel = "Follow Default",
                        colorLabel = stringResource(Res.string.background_color_option),
                        imageLabel = stringResource(Res.string.background_image_option),
                        videoLabel = stringResource(Res.string.background_video_option),
                        transparentLabel = stringResource(Res.string.background_transparent_option),
                        types = listOf(
                            Constants.BACKGROUND_FOLLOW_DEFAULT,
                            Constants.BACKGROUND_COLOR,
                            Constants.BACKGROUND_IMAGE,
                            Constants.BACKGROUND_VIDEO,
                            Constants.BACKGROUND_TRANSPARENT
                        ),
                        disabledTypes = if (!isVlcAvailable) setOf(Constants.BACKGROUND_VIDEO) else emptySet()
                    )
                    when (settings.backgroundSettings.defaultLowerThirdBackgroundType) {
                        Constants.BACKGROUND_COLOR -> {
                            SettingRow(stringResource(Res.string.color)) {
                                ColorPickerField(
                                    color = settings.backgroundSettings.defaultLowerThirdBackgroundColor,
                                    onColorChange = { color ->
                                        onSettingsChange { s ->
                                            s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundColor = color))
                                        }
                                    }
                                )
                            }
                            OpacitySlider(settings.backgroundSettings.defaultLowerThirdBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundOpacity = opacity))
                                }
                            }
                        }
                        Constants.BACKGROUND_IMAGE -> {
                            SettingRow(stringResource(Res.string.background_image)) {
                                FileImagePicker(
                                    imagePath = settings.backgroundSettings.defaultLowerThirdBackgroundImage,
                                    onImagePathChange = { path ->
                                        onSettingsChange { s ->
                                            s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundImage = path))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            OpacitySlider(settings.backgroundSettings.defaultLowerThirdBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundOpacity = opacity))
                                }
                            }
                        }
                        Constants.BACKGROUND_VIDEO -> {
                            SettingRow(stringResource(Res.string.background_video)) {
                                FileVideoPicker(
                                    videoPath = settings.backgroundSettings.defaultLowerThirdBackgroundVideo,
                                    onVideoPathChange = { path ->
                                        onSettingsChange { s ->
                                            s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundVideo = path))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            OpacitySlider(settings.backgroundSettings.defaultLowerThirdBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundOpacity = opacity))
                                }
                            }
                        }
                    }
                }
            }

            // Row 2: Bible + Songs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 3: Bible
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GroupHeader(stringResource(Res.string.bible))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BackgroundColumn(
                                subtitle = stringResource(Res.string.full_screen),
                                config = settings.backgroundSettings.bibleBackground,
                                onConfigChange = { viewModel.updateBibleBackground(it, onSettingsChange) },
                                isLowerThird = false
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            BackgroundColumn(
                                subtitle = stringResource(Res.string.display_lower_third),
                                config = settings.backgroundSettings.bibleLowerThirdBackground,
                                onConfigChange = { viewModel.updateBibleLowerThirdBackground(it, onSettingsChange) },
                                isLowerThird = true
                            )
                        }
                    }
                }

                // Card 4: Songs
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GroupHeader(stringResource(Res.string.songs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BackgroundColumn(
                                subtitle = stringResource(Res.string.full_screen),
                                config = settings.backgroundSettings.songBackground,
                                onConfigChange = { viewModel.updateSongBackground(it, onSettingsChange) },
                                isLowerThird = false
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            BackgroundColumn(
                                subtitle = stringResource(Res.string.display_lower_third),
                                config = settings.backgroundSettings.songLowerThirdBackground,
                                onConfigChange = { viewModel.updateSongLowerThirdBackground(it, onSettingsChange) },
                                isLowerThird = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BackgroundColumn(
    subtitle: String,
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit,
    isLowerThird: Boolean = false
) {
    val backgroundDefaultStr      = stringResource(Res.string.background_default)
    val backgroundColorStr        = stringResource(Res.string.background_color_option)
    val backgroundImageStr        = stringResource(Res.string.background_image_option)
    val backgroundVideoStr        = stringResource(Res.string.background_video_option)
    val backgroundTransparentStr  = stringResource(Res.string.background_transparent_option)
    val backgroundGradientStr     = stringResource(Res.string.gradient_enabled)

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
        onTypeSelected = { type ->
            if (type == Constants.BACKGROUND_GRADIENT) {
                onConfigChange(config.copy(backgroundType = type, gradientEnabled = true))
            } else {
                onConfigChange(config.copy(backgroundType = type, gradientEnabled = false))
            }
        },
        defaultLabel      = backgroundDefaultStr,
        colorLabel        = backgroundColorStr,
        imageLabel        = backgroundImageStr,
        videoLabel        = backgroundVideoStr,
        transparentLabel  = backgroundTransparentStr,
        gradientLabel     = if (isLowerThird) backgroundGradientStr else null,
        disabledTypes     = if (!isVlcAvailable) setOf(Constants.BACKGROUND_VIDEO) else emptySet()
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
            OpacitySlider(config.backgroundOpacity) { onConfigChange(config.copy(backgroundOpacity = it)) }
        }
        Constants.BACKGROUND_IMAGE -> {
            SettingRow(stringResource(Res.string.background_image)) {
                FileImagePicker(
                    imagePath = config.backgroundImage,
                    onImagePathChange = { onConfigChange(config.copy(backgroundImage = it)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OpacitySlider(config.backgroundOpacity) { onConfigChange(config.copy(backgroundOpacity = it)) }
        }
        Constants.BACKGROUND_VIDEO -> {
            SettingRow(stringResource(Res.string.background_video)) {
                FileVideoPicker(
                    videoPath = config.backgroundVideo,
                    onVideoPathChange = { onConfigChange(config.copy(backgroundVideo = it)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OpacitySlider(config.backgroundOpacity) { onConfigChange(config.copy(backgroundOpacity = it)) }
        }
        else -> {
            // Default — nothing extra to show
        }
    }

    // Gradient controls — shown when Gradient type is selected (lower-third columns only)
    if (isLowerThird && config.backgroundType == Constants.BACKGROUND_GRADIENT) {
        Spacer(modifier = Modifier.height(6.dp))
        SettingRow(stringResource(Res.string.gradient_top_color)) {
            ColorPickerField(
                color = config.gradientTopColor,
                onColorChange = { onConfigChange(config.copy(gradientTopColor = it)) }
            )
        }
        SettingRow("${stringResource(Res.string.gradient_opacity)}: ${(config.gradientTopOpacity * 100).toInt()}%") {
            Slider(
                value = config.gradientTopOpacity,
                onValueChange = { onConfigChange(config.copy(gradientTopOpacity = it)) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        SettingRow(stringResource(Res.string.gradient_bottom_color)) {
            ColorPickerField(
                color = config.gradientBottomColor,
                onColorChange = { onConfigChange(config.copy(gradientBottomColor = it)) }
            )
        }
        SettingRow("${stringResource(Res.string.gradient_opacity)}: ${(config.gradientBottomOpacity * 100).toInt()}%") {
            Slider(
                value = config.gradientBottomOpacity,
                onValueChange = { onConfigChange(config.copy(gradientBottomOpacity = it)) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        SettingRow("${stringResource(Res.string.gradient_position)}: ${(config.gradientPosition * 100).toInt()}%") {
            Slider(
                value = config.gradientPosition,
                onValueChange = { onConfigChange(config.copy(gradientPosition = it)) },
                valueRange = 0f..1f,
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
    imageLabel: String,
    videoLabel: String? = null,
    transparentLabel: String? = null,
    gradientLabel: String? = null,
    types: List<String>? = null,
    disabledTypes: Set<String> = emptySet()
) {
    val entries = if (types != null) {
        // Custom types list with labels mapped positionally
        val labels = listOfNotNull(defaultLabel, colorLabel, imageLabel, videoLabel, transparentLabel, gradientLabel)
        types.zip(labels)
    } else {
        // Standard: Default, Color, Image + optional Video + optional Transparent + optional Gradient
        buildList {
            add(Constants.BACKGROUND_DEFAULT to defaultLabel)
            add(Constants.BACKGROUND_COLOR to colorLabel)
            add(Constants.BACKGROUND_IMAGE to imageLabel)
            if (videoLabel != null) add(Constants.BACKGROUND_VIDEO to videoLabel)
            if (transparentLabel != null) add(Constants.BACKGROUND_TRANSPARENT to transparentLabel)
            if (gradientLabel != null) add(Constants.BACKGROUND_GRADIENT to gradientLabel)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        entries.forEach { (type, label) ->
            val isDisabled = type in disabledTypes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = selectedType == type,
                    onClick = { onTypeSelected(type) },
                    enabled = !isDisabled,
                    modifier = Modifier.size(28.dp),
                    colors = RadioButtonDefaults.colors()
                )
                Text(
                    text = if (isDisabled) "$label (Install VLC)" else label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OpacitySlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    SettingRow("${stringResource(Res.string.gradient_opacity)}: ${(value * 100).toInt()}%") {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
