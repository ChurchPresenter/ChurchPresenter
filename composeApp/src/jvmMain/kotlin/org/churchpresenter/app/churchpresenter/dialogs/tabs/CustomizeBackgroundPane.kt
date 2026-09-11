package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_above_band_caption
import churchpresenter.composeapp.generated.resources.background_above_band_fill
import churchpresenter.composeapp.generated.resources.background_above_band_opacity
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Background pane: what this screen shows behind whatever is live.
 *
 * Carries the same controls the global Background tab does — type, colour, the image and video
 * pickers with the stock-photo browser behind them, the gradient ends, opacity, dim and blur — so a
 * screen can be given a background of its own rather than only a different treatment of the shared
 * one. It writes through the same [BackgroundConfig] that tab writes, so the two never disagree
 * about what a surface means.
 *
 * What it does *not* offer is a choice of surface: [BackgroundSurfaceRows] is handed the one this
 * output actually draws, for the reason given at that call.
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
    // Every lower-third surface, whatever its band is set to: the wash falls through on its own
    // field rather than on the band's type, so a surface drawing a picture still has a say above it.
    if (scope.lowerThird) {
        AboveBandRows(scope, config, onConfig)
    }
}

/**
 * The wash over the two thirds this output's band does not cover.
 *
 * The band's own rows one rectangle up: the same type control, off the same labels, and the same
 * color field under it. `Default` means here what it means in the row above — follow the Default
 * Lower Third — and the Default Lower Third itself is not offered one, being the top of this chain.
 * See `aboveBandTypeOptions` for the list and why it is the short one.
 *
 * Named "Fill Color" and "Fill Opacity", not the plain words: the band's own color and opacity rows
 * are in the same column, and a second "Color" there names nothing anyone can tell apart.
 */
@Composable
private fun AboveBandRows(
    scope: BackgroundScope,
    config: BackgroundConfig,
    onConfig: (BackgroundConfig) -> Unit,
) {
    CustomizeRow(stringResource(Res.string.background_above_band_caption)) {
        ChoiceControl(
            options = scope.aboveBandTypeOptions().map { it to stringResource(customizeTypeLabel(it)) },
            selected = config.aboveBandType,
            onSelect = { v -> onConfig(config.copy(aboveBandType = v)) },
        )
    }
    if (config.aboveBandType != Constants.BACKGROUND_COLOR) return
    val fill = stringResource(Res.string.background_above_band_fill)
    CustomizeRow(fill, labelInsideControl = true) {
        ColorControl(fill, config.aboveBandColor) { v -> onConfig(config.copy(aboveBandColor = v)) }
    }
    CustomizeRow(stringResource(Res.string.background_above_band_opacity)) {
        SliderControl(
            value = (config.aboveBandOpacity * PERCENT).toInt(),
            onValueChange = { v -> onConfig(config.copy(aboveBandOpacity = v / PERCENT)) },
            range = PERCENT_RANGE,
            suffix = stringResource(Res.string.percent_suffix),
        )
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
    scope.inheritType?.let { add(it to stringResource(customizeTypeLabel(it))) }
    add(Constants.BACKGROUND_COLOR to stringResource(customizeTypeLabel(Constants.BACKGROUND_COLOR)))
    add(Constants.BACKGROUND_IMAGE to stringResource(customizeTypeLabel(Constants.BACKGROUND_IMAGE)))
    add(Constants.BACKGROUND_VIDEO to stringResource(customizeTypeLabel(Constants.BACKGROUND_VIDEO)))
    add(
        Constants.BACKGROUND_TRANSPARENT
            to stringResource(customizeTypeLabel(Constants.BACKGROUND_TRANSPARENT)),
    )
    if (scope.offersGradient) {
        add(
            Constants.BACKGROUND_GRADIENT
                to stringResource(customizeTypeLabel(Constants.BACKGROUND_GRADIENT)),
        )
    }
}

/**
 * What this pane calls a background type — the band's row and the wash's read off one list.
 *
 * Its own rather than the Background tab's [backgroundTypeLabel]: this dialog's segments are
 * narrower, so `Follow Default` and `Video Loop` are shortened here, and the wash has to be
 * labelled the same way the band beside it is.
 */
private fun customizeTypeLabel(type: String): StringResource = when (type) {
    Constants.BACKGROUND_IMAGE -> Res.string.customize_type_image
    Constants.BACKGROUND_VIDEO -> Res.string.customize_type_video
    Constants.BACKGROUND_TRANSPARENT -> Res.string.customize_type_transparent
    Constants.BACKGROUND_GRADIENT -> Res.string.customize_type_gradient
    Constants.BACKGROUND_COLOR -> Res.string.customize_type_color
    else -> Res.string.customize_type_default
}

private const val PERCENT = 100f
