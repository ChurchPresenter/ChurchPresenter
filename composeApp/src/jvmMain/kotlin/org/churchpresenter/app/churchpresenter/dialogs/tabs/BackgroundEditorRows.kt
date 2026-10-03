package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.app.churchpresenter.presenter.invalidateBibleLottieTemplates
import org.churchpresenter.strings.generated.resources.song_lottie_unsupported_note
import org.churchpresenter.strings.generated.resources.bible_lottie_unsupported_note
import org.churchpresenter.strings.generated.resources.background_above_band_caption
import org.churchpresenter.strings.generated.resources.background_above_band_fill
import org.churchpresenter.strings.generated.resources.background_above_band_fills_behind_band
import org.churchpresenter.strings.generated.resources.background_above_band_opacity
import org.churchpresenter.strings.generated.resources.background_camera_option
import org.churchpresenter.strings.generated.resources.background_image_file
import org.churchpresenter.strings.generated.resources.background_video_file
import org.churchpresenter.strings.generated.resources.bottom
import org.churchpresenter.strings.generated.resources.customize_background_opacity
import org.churchpresenter.strings.generated.resources.customize_background_type
import org.churchpresenter.strings.generated.resources.canvas_source_color
import org.churchpresenter.strings.generated.resources.background_default
import org.churchpresenter.strings.generated.resources.gradient_enabled
import org.churchpresenter.strings.generated.resources.customize_type_image
import org.churchpresenter.strings.generated.resources.background_lottie_option
import org.churchpresenter.strings.generated.resources.background_transparent_option
import org.churchpresenter.strings.generated.resources.canvas_source_video
import org.churchpresenter.strings.generated.resources.gradient_bottom_opacity
import org.churchpresenter.strings.generated.resources.gradient_top_opacity
import org.churchpresenter.strings.generated.resources.lower_third_animation_file
import org.churchpresenter.strings.generated.resources.percent_suffix
import org.churchpresenter.strings.generated.resources.pixels_short
import org.churchpresenter.strings.generated.resources.position
import org.churchpresenter.strings.generated.resources.profile_bg_lottie_picker_elsewhere
import org.churchpresenter.strings.generated.resources.song_background_blur
import org.churchpresenter.strings.generated.resources.song_background_dim
import org.churchpresenter.strings.generated.resources.top
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val PERCENT = 100f

/**
 * A surface's editor: its type, then whatever that type needs -- the colour, the picture, the clip,
 * the camera, the gradient's two ends -- then how it is dimmed, faded and blurred, and on a
 * lower-third surface the wash above the band.
 *
 * [includeLottie] is off everywhere but the band's own cards, which is where the animated band is
 * chosen; the type list here leaves it out elsewhere, so the animation is set in one place.
 *
 * [lottiePickerHere] separates *choosing* Lottie as the type, which stays here alongside every
 * other type, from *editing* it: [ContentBackgroundGroup] passes `false` so the template picker
 * itself -- the dropdown, Generate, and the note on what it ignores -- renders once, in the "Lower
 * third band" card next to the height and the ownership shortcut it already shares state with,
 * rather than spread across two cards.
 */
@Composable
internal fun BackgroundSurfaceRows(
    scope: BackgroundScope,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    includeLottie: Boolean = false,
    lottiePickerHere: Boolean = includeLottie,
) {
    val config = settings.backgroundSettings.configFor(scope)
    val onConfig: (BackgroundConfig) -> Unit = { updated ->
        onSettingsChange { s -> s.copy(backgroundSettings = s.backgroundSettings.withConfigFor(scope, updated)) }
    }
    val types = scope.typeOptions().filter {
        // "Default" is the row above this one ([ContentBackgroundGroup]'s Profile default); the
        // default lower third's "Follow default" is a real choice of its own and stays.
        it != Constants.BACKGROUND_DEFAULT && (includeLottie || it != Constants.BACKGROUND_LOTTIE)
    }
    SettingsRow(stringResource(Res.string.customize_background_type)) {
        RowSegmented(
            options = types.map { RowOption(it, stringResource(backgroundTypeWord(it)), backgroundTypeTag(it)) },
            selected = config.backgroundType,
            onSelect = { v ->
                // `gradientEnabled` rides along with the type, exactly as the Background tab does it:
                // the presenters gate the gradient on that flag rather than on the type.
                onConfig(config.copy(backgroundType = v, gradientEnabled = v == Constants.BACKGROUND_GRADIENT))
            },
            compact = true,
        )
    }
    SurfaceSourceRows(scope, settings, config, onConfig, onSettingsChange, lottiePickerHere)
    val hasLook = config.backgroundType != Constants.BACKGROUND_TRANSPARENT &&
        config.backgroundType != Constants.BACKGROUND_LOTTIE
    if (hasLook) {
        val percent = stringResource(Res.string.percent_suffix)
        SettingsRow(stringResource(Res.string.song_background_dim)) {
            RowStepper(config.dim, { onConfig(config.copy(dim = it)) }, PERCENT_RANGE, step = 5, unit = percent)
        }
        SettingsRow(stringResource(Res.string.customize_background_opacity)) {
            RowStepper(
                (config.backgroundOpacity * PERCENT).toInt(),
                { onConfig(config.copy(backgroundOpacity = it / PERCENT)) },
                PERCENT_RANGE,
                step = 5,
                unit = percent,
            )
        }
        SettingsRow(stringResource(Res.string.song_background_blur), advanced = true) {
            RowStepper(
                config.blur,
                { onConfig(config.copy(blur = it)) },
                BLUR_RANGE,
                unit = stringResource(Res.string.pixels_short),
            )
        }
    }
    if (scope.lowerThird) AboveBandRows(scope, settings, config, onConfig, onSettingsChange)
}

@Composable
private fun SurfaceSourceRows(
    scope: BackgroundScope,
    settings: AppSettings,
    config: BackgroundConfig,
    onConfig: (BackgroundConfig) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    lottiePickerHere: Boolean = true,
) {
    val onPexelsKey: (String) -> Unit = { key ->
        onSettingsChange { s -> s.copy(stockPhotoSettings = s.stockPhotoSettings.copy(pexelsApiKey = key)) }
    }
    val onPixabayKey: (String) -> Unit = { key ->
        onSettingsChange { s -> s.copy(stockPhotoSettings = s.stockPhotoSettings.copy(pixabayApiKey = key)) }
    }
    when (config.backgroundType) {
        Constants.BACKGROUND_COLOR -> SettingsRow(stringResource(Res.string.canvas_source_color)) {
            RowColor(config.backgroundColor, { onConfig(config.copy(backgroundColor = it)) })
        }
        Constants.BACKGROUND_IMAGE -> SettingsRow(stringResource(Res.string.background_image_file)) {
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
        Constants.BACKGROUND_VIDEO -> SettingsRow(stringResource(Res.string.background_video_file)) {
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
        Constants.BACKGROUND_CAMERA -> SettingsRow(stringResource(Res.string.background_camera_option)) {
            CameraPickerRow(config, onConfigChange = onConfig)
        }
        Constants.BACKGROUND_GRADIENT -> GradientRows(config, onConfig)
        Constants.BACKGROUND_LOTTIE ->
            if (lottiePickerHere) {
                LottieRows(scope, settings, config, onConfig = onConfig)
            } else {
                SettingsRow(
                    stringResource(Res.string.lower_third_animation_file),
                    sub = stringResource(Res.string.profile_bg_lottie_picker_elsewhere),
                ) {}
            }
        else -> Unit
    }
}

/** The template picker with the generator behind it. The band draws its own look, so there are no sliders. */
@Composable
internal fun LottieRows(
    scope: BackgroundScope,
    settings: AppSettings,
    config: BackgroundConfig,
    onConfig: (BackgroundConfig) -> Unit,
) {
    var showGenerator by remember { mutableStateOf(false) }
    val templatesDir = remember { SettingsManager.bibleLowerThirdsDir() }
    // Bumped whenever the generator saves, so a newly generated file appears in the dropdown at
    // once even when the picked template stays whatever it already was -- `path` alone does not
    // change then, and used to leave the list stale until the pane was left and reopened.
    var refreshToken by remember { mutableStateOf(0) }
    // What a band still takes from the Lower Third style and what it ignores -- the one place that
    // is said, since the template's own layout overrides most of the text rows beside it.
    val note = when (scope) {
        BackgroundScope.BIBLE_LOWER_THIRD -> stringResource(Res.string.bible_lottie_unsupported_note)
        BackgroundScope.SONG_LOWER_THIRD -> stringResource(Res.string.song_lottie_unsupported_note)
        else -> null
    }
    SettingsRow(stringResource(Res.string.lower_third_animation_file), sub = note) {
        LottieBandPickerRow(
            path = config.backgroundLottie,
            onPathChange = { onConfig(config.copy(backgroundLottie = it)) },
            templatesDir = templatesDir,
            onGenerate = { showGenerator = true },
            modifier = Modifier.width(SOURCE_FIELD_WIDTH),
            refreshToken = refreshToken,
        )
    }
    if (showGenerator) {
        BibleLottieGeneratorWindow(
            outputDir = templatesDir,
            seed = lottieBandSeed(settings, scope),
            onSaved = { file ->
                onConfig(config.copy(backgroundLottie = file.absolutePath))
                // The generator saves over the path it loaded from, so nothing drawing the band
                // would notice the file changed on its own.
                invalidateBibleLottieTemplates()
                refreshToken++
                showGenerator = false
            },
            onClose = { showGenerator = false },
        )
    }
}

/**
 * The wash over the part of the output this surface's band does not cover. Its own type, following
 * the Default Lower Third's wash when it says Default -- see `aboveBandTypeOptions`.
 */
@Composable
private fun AboveBandRows(
    scope: BackgroundScope,
    settings: AppSettings,
    config: BackgroundConfig,
    onConfig: (BackgroundConfig) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsRow(stringResource(Res.string.background_above_band_caption)) {
        RowSegmented(
            options = scope.aboveBandTypeOptions().map { RowOption(it, stringResource(backgroundTypeWord(it))) },
            selected = config.aboveBandType,
            onSelect = { v -> onConfig(config.copy(aboveBandType = v)) },
            compact = true,
        )
    }
    if (config.aboveBandType == Constants.BACKGROUND_COLOR) {
        SettingsRow(stringResource(Res.string.background_above_band_fill)) {
            RowColor(config.aboveBandColor, { onConfig(config.copy(aboveBandColor = it)) })
        }
    }
    aboveBandMediaCaption(config.aboveBandType)?.let { caption ->
        SettingsRow(stringResource(caption)) {
            AboveBandMediaPicker(settings, config, onConfig, onSettingsChange, Modifier.width(SOURCE_FIELD_WIDTH))
        }
    }
    if (!config.aboveBandType.drawsAboveBand()) return
    SettingsRow(stringResource(Res.string.background_above_band_opacity)) {
        RowStepper(
            (config.aboveBandOpacity * PERCENT).toInt(),
            { onConfig(config.copy(aboveBandOpacity = it / PERCENT)) },
            PERCENT_RANGE,
            step = 5,
            unit = stringResource(Res.string.percent_suffix),
        )
    }
    // Whether the wash paints behind the band as well as above it. It decides what a downstream
    // keyer sees, so a band meant to be keyed transparent for OBS or NDI needs it off.
    SettingsSwitchRow(
        stringResource(Res.string.background_above_band_fills_behind_band),
        config.aboveBandFillsBehindBand,
        { onConfig(config.copy(aboveBandFillsBehindBand = it)) },
    )
}

/** The two ends of a gradient, and where it turns over. */
@Composable
private fun GradientRows(config: BackgroundConfig, onConfig: (BackgroundConfig) -> Unit) {
    val percent = stringResource(Res.string.percent_suffix)
    SettingsRow(stringResource(Res.string.top)) {
        RowColor(config.gradientTopColor, { onConfig(config.copy(gradientTopColor = it)) })
    }
    SettingsRow(stringResource(Res.string.bottom)) {
        RowColor(config.gradientBottomColor, { onConfig(config.copy(gradientBottomColor = it)) })
    }
    SettingsRow(stringResource(Res.string.gradient_top_opacity)) {
        RowStepper(
            (config.gradientTopOpacity * PERCENT).toInt(),
            { onConfig(config.copy(gradientTopOpacity = it / PERCENT)) },
            PERCENT_RANGE,
            step = 5,
            unit = percent,
        )
    }
    SettingsRow(stringResource(Res.string.gradient_bottom_opacity)) {
        RowStepper(
            (config.gradientBottomOpacity * PERCENT).toInt(),
            { onConfig(config.copy(gradientBottomOpacity = it / PERCENT)) },
            PERCENT_RANGE,
            step = 5,
            unit = percent,
        )
    }
    SettingsRow(stringResource(Res.string.position), advanced = true) {
        RowStepper(
            (config.gradientPosition * PERCENT).toInt(),
            { onConfig(config.copy(gradientPosition = it / PERCENT)) },
            PERCENT_RANGE,
            step = 5,
            unit = percent,
        )
    }
}

/** What a background type is called in a segment. */
internal fun backgroundTypeWord(type: String): StringResource = when (type) {
    Constants.BACKGROUND_IMAGE -> Res.string.customize_type_image
    Constants.BACKGROUND_VIDEO -> Res.string.canvas_source_video
    Constants.BACKGROUND_TRANSPARENT -> Res.string.background_transparent_option
    Constants.BACKGROUND_GRADIENT -> Res.string.gradient_enabled
    Constants.BACKGROUND_LOTTIE -> Res.string.background_lottie_option
    Constants.BACKGROUND_COLOR -> Res.string.canvas_source_color
    Constants.BACKGROUND_CAMERA -> Res.string.background_camera_option
    else -> Res.string.background_default
}

/** Test handle for one background type segment. */
internal fun backgroundTypeTag(type: String): String = "profile_background_type_$type"

/**
 * A folded background group's summary: whose background it is -- [source] -- and, when it is the
 * profile's [own], what that is: "Own · Color #000000".
 */
@Composable
internal fun backgroundSummary(source: String, own: BackgroundConfig?): String {
    if (own == null) return source
    val kind = stringResource(backgroundTypeWord(own.backgroundType))
    val what = describeBackground(own)
    return "$source · " + if (what == kind) kind else "$kind $what"
}

/** A background in a few words: its colour, its file's name, or its kind. */
@Composable
internal fun describeBackground(config: BackgroundConfig): String = when (config.backgroundType) {
    Constants.BACKGROUND_COLOR -> config.backgroundColor
    Constants.BACKGROUND_IMAGE -> config.backgroundImage.substringAfterLast('/').substringAfterLast('\\')
        .ifBlank { stringResource(Res.string.customize_type_image) }
    Constants.BACKGROUND_VIDEO -> config.backgroundVideo.substringAfterLast('/').substringAfterLast('\\')
        .ifBlank { stringResource(Res.string.canvas_source_video) }
    Constants.BACKGROUND_LOTTIE -> config.backgroundLottie.substringAfterLast('/').substringBeforeLast('.')
        .ifBlank { stringResource(Res.string.background_lottie_option) }
    else -> stringResource(backgroundTypeWord(config.backgroundType))
}

