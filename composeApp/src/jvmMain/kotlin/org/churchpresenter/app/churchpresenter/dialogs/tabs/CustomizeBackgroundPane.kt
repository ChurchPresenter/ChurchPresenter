package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.percent_suffix
import churchpresenter.composeapp.generated.resources.customize_type_gradient
import churchpresenter.composeapp.generated.resources.customize_type_default
import churchpresenter.composeapp.generated.resources.song_background_blur
import churchpresenter.composeapp.generated.resources.song_background_dim
import churchpresenter.composeapp.generated.resources.background_video_file
import churchpresenter.composeapp.generated.resources.background_image_file
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.customize_background_opacity
import churchpresenter.composeapp.generated.resources.customize_background_type
import churchpresenter.composeapp.generated.resources.customize_type_color
import churchpresenter.composeapp.generated.resources.customize_type_image
import churchpresenter.composeapp.generated.resources.customize_type_transparent
import churchpresenter.composeapp.generated.resources.customize_type_video
import churchpresenter.composeapp.generated.resources.position
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The Background pane: what this screen shows behind whatever is live.
 *
 * Type, colour and opacity for the full-screen background and for the lower-third band. The image
 * and video pickers, the stock-photo browser and the gradient controls stay on the global
 * Background tab — those choose a *file*, which is a library decision, where this pane is about how
 * one screen uses what the library already holds. An output set to Image or Video here keeps
 * showing the globally chosen file.
 */
@Composable
internal fun BackgroundCustomizePane(
    element: CustomizeElement,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    // Only the surface this output actually draws. The display mode already says whether it is a
    // full screen or a lower third, so the chip names the surface -- "Default", "Bible", "Songs" --
    // and the output's own shape picks which of the stored pair it writes. Listing all six would
    // offer an operator three surfaces that cannot reach the screen they are customizing.
    val lowerThird = LocalOutputStyleScope.current == OutputStyleScope.LOWER_THIRD
    val scope = element.backgroundScope(lowerThird)
    PaneScaffold {
        CustomizeGroup(backgroundScopeTitle(scope)) {
            BackgroundSurfaceRows(scope, settings, onSettingsChange)
        }
    }
}

/**
 * One background surface, edited through the same [BackgroundConfig] the Background tab edits.
 *
 * Going through `configFor`/`withConfigFor` rather than the flat `default*` fields is what lets all
 * six surfaces — the two Defaults, and Bible and Songs in both shapes — be one block of rows rather
 * than six, and keeps this pane and that tab writing the same settings the same way.
 */
@Composable
private fun BackgroundSurfaceRows(
    scope: BackgroundScope,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val config = settings.backgroundSettings.configFor(scope)
    val onConfig: (BackgroundConfig) -> Unit = { updated ->
        onSettingsChange { s ->
            s.copy(backgroundSettings = s.backgroundSettings.withConfigFor(scope, updated))
        }
    }
    val onPexelsKey: (String) -> Unit = { key ->
        onSettingsChange { s -> s.copy(stockPhotoSettings = s.stockPhotoSettings.copy(pexelsApiKey = key)) }
    }
    val onPixabayKey: (String) -> Unit = { key ->
        onSettingsChange { s -> s.copy(stockPhotoSettings = s.stockPhotoSettings.copy(pixabayApiKey = key)) }
    }

    CustomizeRow(stringResource(Res.string.customize_background_type)) {
        ChoiceControl(backgroundTypeOptions(scope), config.backgroundType) { v ->
            onConfig(config.copy(backgroundType = v))
        }
    }
    when (config.backgroundType) {
        Constants.BACKGROUND_COLOR ->
            CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
                ColorControl(stringResource(Res.string.color), config.backgroundColor) { v ->
                    onConfig(config.copy(backgroundColor = v))
                }
            }
        Constants.BACKGROUND_IMAGE -> CustomizeRow(stringResource(Res.string.background_image_file)) {
            ImagePickerRow(
                imagePath = config.backgroundImage,
                onImagePathChange = { onConfig(config.copy(backgroundImage = it)) },
                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                onPexelsApiKeyChange = onPexelsKey,
                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                onPixabayApiKeyChange = onPixabayKey,
                atemSettings = settings.atemSettings,
                modifier = Modifier.width(SOURCE_FIELD_WIDTH),
            )
        }
        Constants.BACKGROUND_VIDEO -> CustomizeRow(stringResource(Res.string.background_video_file)) {
            VideoPickerRow(
                videoPath = config.backgroundVideo,
                onVideoPathChange = { onConfig(config.copy(backgroundVideo = it)) },
                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                onPexelsApiKeyChange = onPexelsKey,
                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                onPixabayApiKeyChange = onPixabayKey,
                modifier = Modifier.width(SOURCE_FIELD_WIDTH),
            )
        }
        Constants.BACKGROUND_GRADIENT -> GradientRows(config, onConfig)
        else -> Unit
    }
    if (config.backgroundType != Constants.BACKGROUND_TRANSPARENT) {
        // Sliders, as on the Background tab: these are nudged until the picture reads well behind
        // text, not typed to a number anyone knows in advance.
        val percent = stringResource(Res.string.percent_suffix)
        val pixels = stringResource(Res.string.pixels_short)
        CustomizeRow(stringResource(Res.string.customize_background_opacity)) {
            SliderControl(
                value = (config.backgroundOpacity * PERCENT).toInt(),
                onValueChange = { v -> onConfig(config.copy(backgroundOpacity = v / PERCENT)) },
                range = PERCENT_RANGE,
                suffix = percent,
            )
        }
        CustomizeRow(stringResource(Res.string.song_background_dim)) {
            SliderControl(config.dim, { v -> onConfig(config.copy(dim = v)) }, PERCENT_RANGE, percent)
        }
        CustomizeRow(stringResource(Res.string.song_background_blur)) {
            SliderControl(config.blur, { v -> onConfig(config.copy(blur = v)) }, BLUR_RANGE, pixels)
        }
    }
}

/** The two ends of a gradient, and where it turns over. */
@Composable
private fun GradientRows(config: BackgroundConfig, onConfig: (BackgroundConfig) -> Unit) {
    CustomizeRow(stringResource(Res.string.top), labelInsideControl = true) {
        ColorControl(stringResource(Res.string.top), config.gradientTopColor) { v ->
            onConfig(config.copy(gradientTopColor = v))
        }
    }
    CustomizeRow(stringResource(Res.string.bottom), labelInsideControl = true) {
        ColorControl(stringResource(Res.string.bottom), config.gradientBottomColor) { v ->
            onConfig(config.copy(gradientBottomColor = v))
        }
    }
    CustomizeRow(stringResource(Res.string.position), labelInsideControl = true) {
        NumberControl(
            label = stringResource(Res.string.position),
            value = (config.gradientPosition * PERCENT).toInt(),
            onValueChange = { v -> onConfig(config.copy(gradientPosition = v / PERCENT)) },
            range = PERCENT_RANGE,
        )
    }
}

@Composable
private fun backgroundTypeOptions(scope: BackgroundScope): List<Pair<String, String>> = buildList {
    // The same list the Background tab's segmented control offers, in the same order, so a surface
    // set there reads the same here — including the "Default" a content surface falls through by.
    scope.inheritType?.let { add(it to stringResource(Res.string.customize_type_default)) }
    add(Constants.BACKGROUND_COLOR to stringResource(Res.string.customize_type_color))
    add(Constants.BACKGROUND_IMAGE to stringResource(Res.string.customize_type_image))
    add(Constants.BACKGROUND_VIDEO to stringResource(Res.string.customize_type_video))
    add(Constants.BACKGROUND_TRANSPARENT to stringResource(Res.string.customize_type_transparent))
    if (scope.offersGradient) {
        add(Constants.BACKGROUND_GRADIENT to stringResource(Res.string.customize_type_gradient))
    }
}

private const val PERCENT = 100f
