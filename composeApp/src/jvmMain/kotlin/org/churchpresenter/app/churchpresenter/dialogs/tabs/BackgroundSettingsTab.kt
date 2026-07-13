package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
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
import churchpresenter.composeapp.generated.resources.background_opacity
import churchpresenter.composeapp.generated.resources.gradient_bottom_opacity
import churchpresenter.composeapp.generated.resources.gradient_enabled
import churchpresenter.composeapp.generated.resources.gradient_position
import churchpresenter.composeapp.generated.resources.gradient_top_color
import churchpresenter.composeapp.generated.resources.gradient_top_opacity
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.songs
import churchpresenter.composeapp.generated.resources.stock_library_tooltip
import churchpresenter.composeapp.generated.resources.stock_photo_browse_tooltip
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FileImagePicker
import org.churchpresenter.app.churchpresenter.composables.FileVideoPicker
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.TvScreenBox
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.data.StockMediaClient
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundConfig
import org.churchpresenter.app.churchpresenter.dialogs.LocalLibraryDialog
import org.churchpresenter.app.churchpresenter.dialogs.StockMediaBrowserDialog
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.BackgroundSettingsViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun BackgroundSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val viewModel = remember { BackgroundSettingsViewModel() }

    val onPexelsApiKeyChange: (String) -> Unit = { key ->
        onSettingsChange { s -> s.copy(stockPhotoSettings = s.stockPhotoSettings.copy(pexelsApiKey = key)) }
    }
    val onPixabayApiKeyChange: (String) -> Unit = { key ->
        onSettingsChange { s -> s.copy(stockPhotoSettings = s.stockPhotoSettings.copy(pixabayApiKey = key)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
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
                Box(modifier = Modifier.weight(1f)) {
                SettingsSection(
                    title = stringResource(Res.string.default_background_color),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(Res.string.default_background_color_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BackgroundTypeDropdown(
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
                    Spacer(modifier = Modifier.height(10.dp))
                    when (settings.backgroundSettings.defaultBackgroundType) {
                        Constants.BACKGROUND_COLOR -> {
                            ColorPickerField(
                                label = stringResource(Res.string.color),
                                modifier = Modifier.width(140.dp),
                                color = settings.backgroundSettings.defaultBackgroundColor,
                                onColorChange = { viewModel.updateDefaultColor(it, onSettingsChange) }
                            )
                            OpacitySlider(settings.backgroundSettings.defaultBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundOpacity = opacity))
                                }
                            }
                        }
                        Constants.BACKGROUND_IMAGE -> {
                            Text(
                                text = stringResource(Res.string.background_image),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ImagePickerRow(
                                imagePath = settings.backgroundSettings.defaultBackgroundImage,
                                onImagePathChange = { path ->
                                    onSettingsChange { s ->
                                        s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundImage = path))
                                    }
                                },
                                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                                onPexelsApiKeyChange = onPexelsApiKeyChange,
                                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                                onPixabayApiKeyChange = onPixabayApiKeyChange,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OpacitySlider(settings.backgroundSettings.defaultBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundOpacity = opacity))
                                }
                            }
                        }
                        Constants.BACKGROUND_VIDEO -> {
                            Text(
                                text = stringResource(Res.string.background_video),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            VideoPickerRow(
                                videoPath = settings.backgroundSettings.defaultBackgroundVideo,
                                onVideoPathChange = { path ->
                                    onSettingsChange { s ->
                                        s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundVideo = path))
                                    }
                                },
                                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                                onPexelsApiKeyChange = onPexelsApiKeyChange,
                                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                                onPixabayApiKeyChange = onPixabayApiKeyChange,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OpacitySlider(settings.backgroundSettings.defaultBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultBackgroundOpacity = opacity))
                                }
                            }
                        }
                    }
                }
                    // Fully colored — this background fills the entire output screen.
                    FullScreenCoverageTv(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 38.dp, end = 6.dp)
                            .width(100.dp)
                            .height(75.dp)
                    )
                }

                // Card 2: Default Lower Third Background
                Box(modifier = Modifier.weight(1f)) {
                SettingsSection(
                    title = stringResource(Res.string.default_lower_third_background),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(Res.string.default_lower_third_background_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BackgroundTypeDropdown(
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
                    Spacer(modifier = Modifier.height(10.dp))
                    when (settings.backgroundSettings.defaultLowerThirdBackgroundType) {
                        Constants.BACKGROUND_COLOR -> {
                            ColorPickerField(
                                label = stringResource(Res.string.color),
                                modifier = Modifier.width(140.dp),
                                color = settings.backgroundSettings.defaultLowerThirdBackgroundColor,
                                onColorChange = { color ->
                                    onSettingsChange { s ->
                                        s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundColor = color))
                                    }
                                }
                            )
                            OpacitySlider(settings.backgroundSettings.defaultLowerThirdBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundOpacity = opacity))
                                }
                            }
                        }
                        Constants.BACKGROUND_IMAGE -> {
                            Text(
                                text = stringResource(Res.string.background_image),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ImagePickerRow(
                                imagePath = settings.backgroundSettings.defaultLowerThirdBackgroundImage,
                                onImagePathChange = { path ->
                                    onSettingsChange { s ->
                                        s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundImage = path))
                                    }
                                },
                                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                                onPexelsApiKeyChange = onPexelsApiKeyChange,
                                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                                onPixabayApiKeyChange = onPixabayApiKeyChange,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OpacitySlider(settings.backgroundSettings.defaultLowerThirdBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundOpacity = opacity))
                                }
                            }
                        }
                        Constants.BACKGROUND_VIDEO -> {
                            Text(
                                text = stringResource(Res.string.background_video),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            VideoPickerRow(
                                videoPath = settings.backgroundSettings.defaultLowerThirdBackgroundVideo,
                                onVideoPathChange = { path ->
                                    onSettingsChange { s ->
                                        s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundVideo = path))
                                    }
                                },
                                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                                onPexelsApiKeyChange = onPexelsApiKeyChange,
                                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                                onPixabayApiKeyChange = onPixabayApiKeyChange,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OpacitySlider(settings.backgroundSettings.defaultLowerThirdBackgroundOpacity) { opacity ->
                                onSettingsChange { s ->
                                    s.copy(backgroundSettings = s.backgroundSettings.copy(defaultLowerThirdBackgroundOpacity = opacity))
                                }
                            }
                        }
                    }
                }
                    // Top 2/3 highlighted — this background fills everything above the lower-third band.
                    LowerThirdCoverageTv(
                        highlightTop = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 38.dp, end = 6.dp)
                            .width(100.dp)
                            .height(75.dp)
                    )
                }
            }

            // Row 2: Bible + Songs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 3: Bible
                SettingsSection(
                    title = stringResource(Res.string.bible),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BackgroundColumn(
                                subtitle = stringResource(Res.string.full_screen),
                                config = settings.backgroundSettings.bibleBackground,
                                onConfigChange = { viewModel.updateBibleBackground(it, onSettingsChange) },
                                isLowerThird = false,
                                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                                onPexelsApiKeyChange = onPexelsApiKeyChange,
                                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                                onPixabayApiKeyChange = onPixabayApiKeyChange
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            BackgroundColumn(
                                subtitle = stringResource(Res.string.display_lower_third),
                                config = settings.backgroundSettings.bibleLowerThirdBackground,
                                onConfigChange = { viewModel.updateBibleLowerThirdBackground(it, onSettingsChange) },
                                isLowerThird = true,
                                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                                onPexelsApiKeyChange = onPexelsApiKeyChange,
                                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                                onPixabayApiKeyChange = onPixabayApiKeyChange
                            )
                        }
                    }
                }

                // Card 4: Songs
                SettingsSection(
                    title = stringResource(Res.string.songs),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BackgroundColumn(
                                subtitle = stringResource(Res.string.full_screen),
                                config = settings.backgroundSettings.songBackground,
                                onConfigChange = { viewModel.updateSongBackground(it, onSettingsChange) },
                                isLowerThird = false,
                                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                                onPexelsApiKeyChange = onPexelsApiKeyChange,
                                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                                onPixabayApiKeyChange = onPixabayApiKeyChange
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            BackgroundColumn(
                                subtitle = stringResource(Res.string.display_lower_third),
                                config = settings.backgroundSettings.songLowerThirdBackground,
                                onConfigChange = { viewModel.updateSongLowerThirdBackground(it, onSettingsChange) },
                                isLowerThird = true,
                                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                                onPexelsApiKeyChange = onPexelsApiKeyChange,
                                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                                onPixabayApiKeyChange = onPixabayApiKeyChange
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BackgroundColumn(
    subtitle: String,
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit,
    isLowerThird: Boolean = false,
    pexelsApiKey: String = "",
    onPexelsApiKeyChange: (String) -> Unit = {},
    pixabayApiKey: String = "",
    onPixabayApiKeyChange: (String) -> Unit = {}
) {
    val backgroundDefaultStr      = stringResource(Res.string.background_default)
    val backgroundColorStr        = stringResource(Res.string.background_color_option)
    val backgroundImageStr        = stringResource(Res.string.background_image_option)
    val backgroundVideoStr        = stringResource(Res.string.background_video_option)
    val backgroundTransparentStr  = stringResource(Res.string.background_transparent_option)
    val backgroundGradientStr     = stringResource(Res.string.gradient_enabled)

    // A Box overlay so the corner TV badge doesn't add to the column's measured height and
    // push the radio buttons (and everything below) down. The badge is declared last so it
    // draws on top of the column content it overlaps.
    Box(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = subtitle,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = stringResource(Res.string.background_type),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))

    BackgroundTypeDropdown(
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
            ColorPickerField(
                label = stringResource(Res.string.background_color),
                modifier = Modifier.width(140.dp),
                color = config.backgroundColor,
                onColorChange = { onConfigChange(config.copy(backgroundColor = it)) }
            )
            OpacitySlider(config.backgroundOpacity) { onConfigChange(config.copy(backgroundOpacity = it)) }
        }
        Constants.BACKGROUND_IMAGE -> {
            Text(
                text = stringResource(Res.string.background_image),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            ImagePickerRow(
                imagePath = config.backgroundImage,
                onImagePathChange = { onConfigChange(config.copy(backgroundImage = it)) },
                pexelsApiKey = pexelsApiKey,
                onPexelsApiKeyChange = onPexelsApiKeyChange,
                pixabayApiKey = pixabayApiKey,
                onPixabayApiKeyChange = onPixabayApiKeyChange,
                modifier = Modifier.fillMaxWidth()
            )
            OpacitySlider(config.backgroundOpacity) { onConfigChange(config.copy(backgroundOpacity = it)) }
        }
        Constants.BACKGROUND_VIDEO -> {
            Text(
                text = stringResource(Res.string.background_video),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            VideoPickerRow(
                videoPath = config.backgroundVideo,
                onVideoPathChange = { onConfigChange(config.copy(backgroundVideo = it)) },
                pexelsApiKey = pexelsApiKey,
                onPexelsApiKeyChange = onPexelsApiKeyChange,
                pixabayApiKey = pixabayApiKey,
                onPixabayApiKeyChange = onPixabayApiKeyChange,
                modifier = Modifier.fillMaxWidth()
            )
            OpacitySlider(config.backgroundOpacity) { onConfigChange(config.copy(backgroundOpacity = it)) }
        }
        else -> {
            // Default — nothing extra to show
        }
    }

    // Gradient controls — shown when Gradient type is selected (lower-third columns only)
    if (isLowerThird && config.backgroundType == Constants.BACKGROUND_GRADIENT) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPickerField(
                label = stringResource(Res.string.gradient_top_color),
                modifier = Modifier.width(140.dp),
                color = config.gradientTopColor,
                onColorChange = { onConfigChange(config.copy(gradientTopColor = it)) }
            )
            ColorPickerField(
                label = stringResource(Res.string.gradient_bottom_color),
                modifier = Modifier.width(140.dp),
                color = config.gradientBottomColor,
                onColorChange = { onConfigChange(config.copy(gradientBottomColor = it)) }
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                text = "${stringResource(Res.string.gradient_top_opacity)}: ${(config.gradientTopOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = config.gradientTopOpacity,
                onValueChange = { onConfigChange(config.copy(gradientTopOpacity = it)) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                text = "${stringResource(Res.string.gradient_bottom_opacity)}: ${(config.gradientBottomOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = config.gradientBottomOpacity,
                onValueChange = { onConfigChange(config.copy(gradientBottomOpacity = it)) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                text = "${stringResource(Res.string.gradient_position)}: ${(config.gradientPosition * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = config.gradientPosition,
                onValueChange = { onConfigChange(config.copy(gradientPosition = it)) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    } // Column
    if (isLowerThird) {
        // Bottom 1/3 highlighted — this is the color/image of the lower-third band itself.
        LowerThirdCoverageTv(
            highlightTop = false,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(100.dp)
                .height(75.dp)
        )
    } else {
        // Fully colored — this background fills the entire output screen.
        FullScreenCoverageTv(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(100.dp)
                .height(75.dp)
        )
    }
    } // Box
}

/**
 * A tiny [TvScreenBox] with either its top 2/3 or bottom 1/3 filled in the theme's primary
 * color, showing at a glance which portion of the output screen a background setting covers.
 */
@Composable
private fun LowerThirdCoverageTv(
    highlightTop: Boolean,
    modifier: Modifier = Modifier
) {
    TvScreenBox(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Match TvScreenBox's own screen corner radius, or the coverage fill's square
                // corners poke past the rounded border at the bottom of the screen.
                .clip(RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .background(if (highlightTop) MaterialTheme.colorScheme.primary else Color.Transparent)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (highlightTop) Color.Transparent else MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/**
 * A tiny [TvScreenBox] with its entire screen filled in the theme's primary color, showing at a
 * glance that this background setting covers the whole output screen (no lower-third split).
 */
@Composable
private fun FullScreenCoverageTv(
    modifier: Modifier = Modifier
) {
    TvScreenBox(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun BackgroundTypeDropdown(
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
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = entries.firstOrNull { it.first == selectedType }?.second ?: selectedType

    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = true }
            .padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_down),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            entries.forEach { (type, label) ->
                val isDisabled = type in disabledTypes
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isDisabled) "$label (Install VLC)" else label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    enabled = !isDisabled,
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}


@Composable
private fun OpacitySlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "${stringResource(Res.string.background_opacity)}: ${(value * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TooltipIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit
) {
    TooltipArea(
        tooltip = {
            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                Text(
                    text = tooltip,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = tooltip)
        }
    }
}

@Composable
private fun ImagePickerRow(
    imagePath: String,
    onImagePathChange: (String) -> Unit,
    pexelsApiKey: String,
    onPexelsApiKeyChange: (String) -> Unit,
    pixabayApiKey: String,
    onPixabayApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBrowser by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    val browseTooltip = stringResource(Res.string.stock_photo_browse_tooltip)
    val libraryTooltip = stringResource(Res.string.stock_library_tooltip)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FileImagePicker(
            imagePath = imagePath,
            onImagePathChange = onImagePathChange,
            modifier = Modifier.weight(1f)
        )
        TooltipIconButton(Icons.Default.PhotoLibrary, libraryTooltip) { showLibrary = true }
        TooltipIconButton(Icons.Default.Search, browseTooltip) { showBrowser = true }
    }
    if (showBrowser) {
        StockMediaBrowserDialog(
            mediaType = StockMediaClient.StockMediaType.PHOTO,
            pexelsApiKey = pexelsApiKey,
            onPexelsApiKeyChange = onPexelsApiKeyChange,
            pixabayApiKey = pixabayApiKey,
            onPixabayApiKeyChange = onPixabayApiKeyChange,
            onDismiss = { showBrowser = false },
            onMediaDownloaded = onImagePathChange
        )
    }
    if (showLibrary) {
        LocalLibraryDialog(
            mediaType = StockMediaClient.StockMediaType.PHOTO,
            onDismiss = { showLibrary = false },
            onMediaSelected = onImagePathChange
        )
    }
}

@Composable
private fun VideoPickerRow(
    videoPath: String,
    onVideoPathChange: (String) -> Unit,
    pexelsApiKey: String,
    onPexelsApiKeyChange: (String) -> Unit,
    pixabayApiKey: String,
    onPixabayApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBrowser by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    val browseTooltip = stringResource(Res.string.stock_photo_browse_tooltip)
    val libraryTooltip = stringResource(Res.string.stock_library_tooltip)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FileVideoPicker(
            videoPath = videoPath,
            onVideoPathChange = onVideoPathChange,
            modifier = Modifier.weight(1f)
        )
        TooltipIconButton(Icons.Default.PhotoLibrary, libraryTooltip) { showLibrary = true }
        TooltipIconButton(Icons.Default.Search, browseTooltip) { showBrowser = true }
    }
    if (showBrowser) {
        StockMediaBrowserDialog(
            mediaType = StockMediaClient.StockMediaType.VIDEO,
            pexelsApiKey = pexelsApiKey,
            onPexelsApiKeyChange = onPexelsApiKeyChange,
            pixabayApiKey = pixabayApiKey,
            onPixabayApiKeyChange = onPixabayApiKeyChange,
            onDismiss = { showBrowser = false },
            onMediaDownloaded = onVideoPathChange
        )
    }
    if (showLibrary) {
        LocalLibraryDialog(
            mediaType = StockMediaClient.StockMediaType.VIDEO,
            onDismiss = { showLibrary = false },
            onMediaSelected = onVideoPathChange
        )
    }
}
