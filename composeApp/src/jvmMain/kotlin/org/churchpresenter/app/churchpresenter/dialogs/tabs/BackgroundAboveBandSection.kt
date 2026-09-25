/*
 * The wash above a lower-third band: the Background tab's editor column, one rectangle up.
 *
 * Its own file because `BackgroundSettingsControls.kt` sits at detekt's function ceiling, and
 * because this is a subject of its own — the band and what is above it are two decisions an
 * operator makes separately, even though they are made with the same controls.
 */
package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_above_band_caption
import churchpresenter.composeapp.generated.resources.background_above_band_fill
import churchpresenter.composeapp.generated.resources.background_above_band_fills_behind_band
import churchpresenter.composeapp.generated.resources.background_above_band_opacity
import churchpresenter.composeapp.generated.resources.background_camera_option
import churchpresenter.composeapp.generated.resources.background_image_file
import churchpresenter.composeapp.generated.resources.background_video_file
import org.churchpresenter.app.churchpresenter.dialogs.PanelCaption
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What covers the two thirds a band does not: a wash, a picture, a clip, a camera, or nothing.
 *
 * Built out of the band's own parts on purpose: the same segmented type row, drawn by
 * [BackgroundTypeRow] and labelled from [backgroundTypeLabel], the same [BackgroundColorPicker]
 * under it for a colour, and the band's own picture, clip and camera pickers through
 * [AboveBandMediaPicker]. Choosing what goes above the band is the same act as choosing what goes
 * in it, so it is the same control, and `Default` there means what `Default` means one row up.
 *
 * See [aboveBandTypeOptions] for which surface offers what, and why the Default Lower Third has no
 * `Default` of its own.
 *
 * Captioned "Fill Color" and "Fill Opacity" rather than the plain words the band's controls use:
 * both sets are in one scrolling column, and a second "COLOR" or "OPACITY" there names nothing an
 * operator could tell from the first.
 */
@Composable
internal fun AboveBandSection(
    scope: BackgroundScope,
    settings: AppSettings,
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        BackgroundTypeRow(
            caption = stringResource(Res.string.background_above_band_caption),
            options = scope.aboveBandTypeOptions(),
            selected = config.aboveBandType,
            onSelect = { onConfigChange(config.copy(aboveBandType = it)) },
        )
        if (config.aboveBandType == Constants.BACKGROUND_COLOR) {
            BackgroundColorPicker(
                caption = stringResource(Res.string.background_above_band_fill),
                color = config.aboveBandColor,
                onColorChange = { onConfigChange(config.copy(aboveBandColor = it)) },
            )
        }
        aboveBandMediaCaption(config.aboveBandType)?.let { caption ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelCaption(stringResource(caption))
                AboveBandMediaPicker(settings, config, onConfigChange, onSettingsChange, Modifier.fillMaxWidth())
            }
        }
        if (config.aboveBandType.drawsAboveBand()) {
            CaptionedSlider(
                caption = stringResource(Res.string.background_above_band_opacity),
                readout = percentReadout(config.aboveBandOpacity),
                value = config.aboveBandOpacity,
                onValueChange = { onConfigChange(config.copy(aboveBandOpacity = it)) }
            )
            ToggleControl(
                label = stringResource(Res.string.background_above_band_fills_behind_band),
                checked = config.aboveBandFillsBehindBand,
                onCheckedChange = { onConfigChange(config.copy(aboveBandFillsBehindBand = it)) },
            )
        }
    }
}

/** Whether an above-band [this] type paints anything, and so has an opacity and a behind-band choice. */
internal fun String.drawsAboveBand(): Boolean = this == Constants.BACKGROUND_COLOR || this in ABOVE_BAND_MEDIA

private val ABOVE_BAND_MEDIA =
    setOf(Constants.BACKGROUND_IMAGE, Constants.BACKGROUND_VIDEO, Constants.BACKGROUND_CAMERA)

/** The caption over [type]'s picker above the band, or null for a type that has no file or device. */
internal fun aboveBandMediaCaption(type: String): StringResource? = when (type) {
    Constants.BACKGROUND_IMAGE -> Res.string.background_image_file
    Constants.BACKGROUND_VIDEO -> Res.string.background_video_file
    Constants.BACKGROUND_CAMERA -> Res.string.background_camera_option
    else -> null
}

/**
 * The picture, clip or camera above the band — the band's own pickers, pointed at the
 * `aboveBand*` fields instead. The camera picker edits a config's `camera`, so it is handed a copy
 * carrying the above-band device and its answer is moved back where it belongs.
 */
@Composable
internal fun AboveBandMediaPicker(
    settings: AppSettings,
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onPexelsKey: (String) -> Unit = { key ->
        onSettingsChange { s -> s.copy(stockPhotoSettings = s.stockPhotoSettings.copy(pexelsApiKey = key)) }
    }
    val onPixabayKey: (String) -> Unit = { key ->
        onSettingsChange { s -> s.copy(stockPhotoSettings = s.stockPhotoSettings.copy(pixabayApiKey = key)) }
    }
    when (config.aboveBandType) {
        Constants.BACKGROUND_IMAGE -> ImagePickerRow(
            imagePath = config.aboveBandImage,
            onImagePathChange = { onConfigChange(config.copy(aboveBandImage = it)) },
            pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
            onPexelsApiKeyChange = onPexelsKey,
            pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
            onPixabayApiKeyChange = onPixabayKey,
            atemSettings = settings.atemSettings,
            modifier = modifier,
        )
        Constants.BACKGROUND_VIDEO -> VideoPickerRow(
            videoPath = config.aboveBandVideo,
            onVideoPathChange = { onConfigChange(config.copy(aboveBandVideo = it)) },
            pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
            onPexelsApiKeyChange = onPexelsKey,
            pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
            onPixabayApiKeyChange = onPixabayKey,
            modifier = modifier,
        )
        Constants.BACKGROUND_CAMERA -> CameraPickerRow(config.copy(camera = config.aboveBandCamera)) { picked ->
            onConfigChange(config.copy(aboveBandCamera = picked.camera))
        }
        else -> Unit
    }
}
