package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_above_band_caption
import churchpresenter.composeapp.generated.resources.background_above_band_fill
import churchpresenter.composeapp.generated.resources.background_above_band_fills_behind_band
import churchpresenter.composeapp.generated.resources.background_above_band_opacity
import churchpresenter.composeapp.generated.resources.background_camera_option
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.gradient_bottom_opacity
import churchpresenter.composeapp.generated.resources.gradient_top_opacity
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.percent_suffix
import churchpresenter.composeapp.generated.resources.customize_type_gradient
import churchpresenter.composeapp.generated.resources.customize_type_lottie
import churchpresenter.composeapp.generated.resources.lower_third_animation_file
import churchpresenter.composeapp.generated.resources.customize_type_default
import churchpresenter.composeapp.generated.resources.song_background_blur
import churchpresenter.composeapp.generated.resources.song_background_dim
import churchpresenter.composeapp.generated.resources.background_video_file
import churchpresenter.composeapp.generated.resources.background_image_file
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.customize_background_follow
import churchpresenter.composeapp.generated.resources.customize_background_opacity
import churchpresenter.composeapp.generated.resources.customize_background_own
import churchpresenter.composeapp.generated.resources.customize_background_source
import churchpresenter.composeapp.generated.resources.customize_background_type
import churchpresenter.composeapp.generated.resources.customize_type_color
import churchpresenter.composeapp.generated.resources.customize_type_image
import churchpresenter.composeapp.generated.resources.customize_type_transparent
import churchpresenter.composeapp.generated.resources.customize_type_video
import churchpresenter.composeapp.generated.resources.position
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SettingsManager
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
    profile: OutputProfile,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileFieldChange: (OutputProfile) -> Unit,
) {
    // Only the surface this output actually draws. The display mode already says whether it is a
    // full screen or a lower third, so the chip names the surface -- "Default", "Bible", "Songs" --
    // and the output's own shape picks which of the stored pair it writes. Listing all six would
    // offer an operator three surfaces that cannot reach the screen they are customizing.
    val lowerThird = LocalOutputStyleScope.current == OutputStyleScope.LOWER_THIRD
    val scope = element.backgroundScope(lowerThird)
    val overridden = scope.name in profile.backgroundOverrides
    PaneScaffold {
        CustomizeGroup(backgroundScopeTitle(scope)) {
            BackgroundFollowRow(
                overridden = overridden,
                onOverriddenChange = { own ->
                    onProfileFieldChange(
                        if (own) {
                            // Seeded from what it was inheriting, so switching to "own" starts from
                            // the picture already on screen rather than dropping to a black default
                            // and making the operator rebuild a background they could already see.
                            profile.copy(
                                backgroundOverrides = profile.backgroundOverrides + scope.name,
                                backgroundSettings = settings.backgroundSettings,
                            )
                        } else {
                            profile.copy(backgroundOverrides = profile.backgroundOverrides - scope.name)
                        },
                    )
                },
            )
            // Shown either way: while following, these are the Background tab's values, so the
            // operator can see what is being inherited before deciding to take it over. Not
            // editable, though -- a control that silently wrote to a surface nobody is reading
            // would be worse than one that is plainly out of reach.
            FollowingOverlay(following = !overridden) {
                BackgroundSurfaceRows(scope, settings, onSettingsChange)
            }
        }
    }
}

/** The one choice that decides whether the rows under it belong to this profile or to the tab. */
@Composable
private fun BackgroundFollowRow(overridden: Boolean, onOverriddenChange: (Boolean) -> Unit) {
    CustomizeRow(stringResource(Res.string.customize_background_source)) {
        ChoiceControl(
            options = listOf(
                false.toString() to stringResource(Res.string.customize_background_follow),
                true.toString() to stringResource(Res.string.customize_background_own),
            ),
            selected = overridden.toString(),
            onSelect = { picked -> onOverriddenChange(picked.toBoolean()) },
        )
    }
}

/**
 * Draws [content] dimmed and untouchable while [following].
 *
 * A blanket over the top rather than an `enabled` flag threaded through two dozen controls: the
 * point is only that these values belong to the Background tab right now, and every one of them
 * says so at once.
 */
@Composable
private fun FollowingOverlay(following: Boolean, content: @Composable () -> Unit) {
    Box {
        Column(modifier = Modifier.alpha(if (following) FOLLOWING_ALPHA else 1f)) { content() }
        if (following) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // Swallows the clicks the dimmed controls would otherwise still take. No
                    // indication and no ripple: it is not itself a control.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )
        }
    }
}

/** Dim enough to read as "not yours", solid enough to read at all. */
private const val FOLLOWING_ALPHA = 0.45f

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
        ChoiceControl(backgroundTypeOptions(scope), config.backgroundType, maxPerRow = TYPES_PER_ROW) { v ->
            // `gradientEnabled` rides along with the type, exactly as the Background tab does it
            // (`BackgroundSettingsControls.kt`). The presenters gate the gradient on that flag
            // rather than on the type -- BiblePresenter/SongPresenter both test `gradientEnabled`
            // -- so a type change that left it alone gave a profile a gradient it could configure,
            // and preview, and never draw.
            onConfig(config.copy(backgroundType = v, gradientEnabled = v == Constants.BACKGROUND_GRADIENT))
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
        Constants.BACKGROUND_CAMERA -> CustomizeRow(stringResource(Res.string.background_camera_option)) {
            CameraPickerRow(config, onConfig)
        }
        Constants.BACKGROUND_GRADIENT -> GradientRows(config, onConfig)
        Constants.BACKGROUND_LOTTIE -> LottieRows(scope, settings, config, onConfig)
        else -> Unit
    }
    val hasLook = config.backgroundType != Constants.BACKGROUND_TRANSPARENT &&
        config.backgroundType != Constants.BACKGROUND_LOTTIE
    if (hasLook) {
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

/** The template picker with the generator behind it. The band draws its own look, so there are no sliders. */
@Composable
private fun LottieRows(
    scope: BackgroundScope,
    settings: AppSettings,
    config: BackgroundConfig,
    onConfig: (BackgroundConfig) -> Unit,
) {
    var showGenerator by remember { mutableStateOf(false) }
    val templatesDir = remember { SettingsManager.bibleLowerThirdsDir() }
    CustomizeRow(stringResource(Res.string.lower_third_animation_file)) {
        LottieBandPickerRow(
            path = config.backgroundLottie,
            onPathChange = { onConfig(config.copy(backgroundLottie = it)) },
            templatesDir = templatesDir,
            onGenerate = { showGenerator = true },
            modifier = Modifier.width(SOURCE_FIELD_WIDTH),
        )
    }
    if (showGenerator) {
        BibleLottieGeneratorWindow(
            outputDir = templatesDir,
            seed = lottieBandSeed(settings, scope),
            onSaved = { file ->
                onConfig(config.copy(backgroundLottie = file.absolutePath))
                showGenerator = false
            },
            onClose = { showGenerator = false },
        )
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
    // Whether the wash paints behind the band as well as above it. It decides what a downstream
    // keyer sees, so a band meant to be keyed transparent for OBS or NDI needs it off.
    CustomizeRow(stringResource(Res.string.background_above_band_fills_behind_band)) {
        ToggleControl(
            label = stringResource(Res.string.background_above_band_fills_behind_band),
            checked = config.aboveBandFillsBehindBand,
            onCheckedChange = { v -> onConfig(config.copy(aboveBandFillsBehindBand = v)) },
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
    CustomizeRow(stringResource(Res.string.gradient_top_opacity)) {
        SliderControl(
            value = (config.gradientTopOpacity * PERCENT).toInt(),
            onValueChange = { v -> onConfig(config.copy(gradientTopOpacity = v / PERCENT)) },
            range = PERCENT_RANGE,
            suffix = stringResource(Res.string.percent_suffix),
        )
    }
    CustomizeRow(stringResource(Res.string.gradient_bottom_opacity)) {
        SliderControl(
            value = (config.gradientBottomOpacity * PERCENT).toInt(),
            onValueChange = { v -> onConfig(config.copy(gradientBottomOpacity = v / PERCENT)) },
            range = PERCENT_RANGE,
            suffix = stringResource(Res.string.percent_suffix),
        )
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
    add(Constants.BACKGROUND_CAMERA to stringResource(customizeTypeLabel(Constants.BACKGROUND_CAMERA)))
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
    if (scope.offersLottie) {
        add(Constants.BACKGROUND_LOTTIE to stringResource(customizeTypeLabel(Constants.BACKGROUND_LOTTIE)))
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
    Constants.BACKGROUND_LOTTIE -> Res.string.customize_type_lottie
    Constants.BACKGROUND_COLOR -> Res.string.customize_type_color
    // The Background tab's own word for it — short enough for a segment as it stands.
    Constants.BACKGROUND_CAMERA -> Res.string.background_camera_option
    else -> Res.string.customize_type_default
}

private const val PERCENT = 100f

/** Four types to a row: the surfaces that offer seven would otherwise run off the column. */
private const val TYPES_PER_ROW = 4
