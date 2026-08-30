/*
 * The Background tab's editor column: what the selected surface is, and how it looks.
 *
 * One column for all six surfaces — the tab maps the two Defaults onto a [BackgroundConfig] just
 * like the four content surfaces already are, so the type, the picture, the color and the three
 * look sliders are written once instead of six times.
 */
package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_color_caption
import churchpresenter.composeapp.generated.resources.background_copy_look_to
import churchpresenter.composeapp.generated.resources.background_image_file
import churchpresenter.composeapp.generated.resources.background_opacity_caption
import churchpresenter.composeapp.generated.resources.background_type_caption
import churchpresenter.composeapp.generated.resources.background_video_file
import churchpresenter.composeapp.generated.resources.gradient_bottom_color
import churchpresenter.composeapp.generated.resources.gradient_bottom_opacity
import churchpresenter.composeapp.generated.resources.gradient_position
import churchpresenter.composeapp.generated.resources.gradient_top_color
import churchpresenter.composeapp.generated.resources.gradient_top_opacity
import churchpresenter.composeapp.generated.resources.media_vlc_required
import churchpresenter.composeapp.generated.resources.song_background_blur
import churchpresenter.composeapp.generated.resources.song_background_dim
import churchpresenter.composeapp.generated.resources.song_background_look
import churchpresenter.composeapp.generated.resources.unit_px
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.dialogs.PanelCaption
import org.churchpresenter.app.churchpresenter.dialogs.PresetButton
import org.churchpresenter.app.churchpresenter.dialogs.SONG_BACKGROUND_LOOKS
import org.churchpresenter.app.churchpresenter.dialogs.SONG_BACKGROUND_SUGGESTIONS
import org.churchpresenter.app.churchpresenter.dialogs.Segment
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.core.models.songs.SONG_BACKGROUND_MAX_BLUR
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The one editor, for whichever surface the rail has open. */
@Composable
internal fun BackgroundControlsColumn(
    scope: BackgroundScope,
    settings: AppSettings,
    onConfigChange: (BackgroundConfig) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val config = settings.backgroundSettings.configFor(scope)
    val scrollState = rememberScrollState()
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 13.dp, top = 12.dp, bottom = 14.dp)
                .padding(end = SettingsScrollbarGutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BackgroundTypeSegments(scope = scope, config = config, onConfigChange = onConfigChange)
            BackgroundSourceSection(
                settings = settings,
                config = config,
                onConfigChange = onConfigChange,
                onSettingsChange = onSettingsChange
            )
            if (config.backgroundType in ADJUSTABLE_TYPES) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                BackgroundLookSliders(config = config, onConfigChange = onConfigChange)
            }
            val targets = scope.copyTargets()
            if (targets.isNotEmpty() && config.backgroundType != scope.inheritType) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CopyLookSection(targets = targets, config = config, onSettingsChange = onSettingsChange)
            }
        }
        SettingsScrollbar(scrollState)
    }
}

/** What kind of background this surface is — the one control that is never dimmed or hidden. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundTypeSegments(
    scope: BackgroundScope,
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit
) {
    val vlcMissingHint = stringResource(Res.string.media_vlc_required)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        PanelCaption(stringResource(Res.string.background_type_caption))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            scope.typeOptions().forEach { type ->
                val videoWithoutVlc = type == Constants.BACKGROUND_VIDEO && !isVlcAvailable
                val segment = @Composable {
                    Segment(
                        label = stringResource(backgroundTypeLabel(type)),
                        selected = config.backgroundType == type,
                        enabled = !videoWithoutVlc,
                        onClick = {
                            onConfigChange(
                                config.copy(
                                    backgroundType = type,
                                    gradientEnabled = type == Constants.BACKGROUND_GRADIENT
                                )
                            )
                        }
                    )
                }
                // A machine without VLC still shows the Video segment; hovering it says why it is
                // dead, which "it does nothing when clicked" does not.
                if (videoWithoutVlc) HintTooltip(vlcMissingHint, segment) else segment()
            }
        }
    }
}

/** Whatever the chosen type needs said about it: a color, a file, or a gradient's two ends. */
@Composable
private fun BackgroundSourceSection(
    settings: AppSettings,
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val onPexelsKey: (String) -> Unit = { key ->
        onSettingsChange { s -> s.copy(stockPhotoSettings = s.stockPhotoSettings.copy(pexelsApiKey = key)) }
    }
    val onPixabayKey: (String) -> Unit = { key ->
        onSettingsChange { s -> s.copy(stockPhotoSettings = s.stockPhotoSettings.copy(pixabayApiKey = key)) }
    }
    when (config.backgroundType) {
        Constants.BACKGROUND_COLOR -> BackgroundColorSection(config, onConfigChange)
        Constants.BACKGROUND_IMAGE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelCaption(stringResource(Res.string.background_image_file))
            ImagePickerRow(
                imagePath = config.backgroundImage,
                onImagePathChange = { onConfigChange(config.copy(backgroundImage = it)) },
                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                onPexelsApiKeyChange = onPexelsKey,
                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                onPixabayApiKeyChange = onPixabayKey,
                atemSettings = settings.atemSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Constants.BACKGROUND_VIDEO -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelCaption(stringResource(Res.string.background_video_file))
            VideoPickerRow(
                videoPath = config.backgroundVideo,
                onVideoPathChange = { onConfigChange(config.copy(backgroundVideo = it)) },
                pexelsApiKey = settings.stockPhotoSettings.pexelsApiKey,
                onPexelsApiKeyChange = onPexelsKey,
                pixabayApiKey = settings.stockPhotoSettings.pixabayApiKey,
                onPixabayApiKeyChange = onPixabayKey,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Constants.BACKGROUND_GRADIENT -> BackgroundGradientSection(config, onConfigChange)
        else -> Unit
    }
}

/** The color itself, and the six one-click solids under it the song panel offers too. */
@Composable
private fun BackgroundColorSection(config: BackgroundConfig, onConfigChange: (BackgroundConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelCaption(stringResource(Res.string.background_color_caption))
        ColorPickerField(
            color = config.backgroundColor,
            onColorChange = { onConfigChange(config.copy(backgroundColor = it)) },
            modifier = Modifier.width(COLOR_FIELD_WIDTH)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            SONG_BACKGROUND_SUGGESTIONS.forEach { hex ->
                val selected = config.backgroundColor.equals(hex, ignoreCase = true)
                Box(
                    Modifier
                        .weight(1f)
                        .height(SWATCH_HEIGHT)
                        .clip(RoundedCornerShape(6.dp))
                        .background(parseHexColor(hex))
                        .border(
                            2.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onConfigChange(config.copy(backgroundColor = hex)) }
                )
            }
        }
    }
}

/** A lower third's fade: two colors, their two opacities, and where the transition sits. */
@Composable
private fun BackgroundGradientSection(config: BackgroundConfig, onConfigChange: (BackgroundConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPickerField(
                label = stringResource(Res.string.gradient_top_color),
                color = config.gradientTopColor,
                onColorChange = { onConfigChange(config.copy(gradientTopColor = it)) },
                modifier = Modifier.weight(1f)
            )
            ColorPickerField(
                label = stringResource(Res.string.gradient_bottom_color),
                color = config.gradientBottomColor,
                onColorChange = { onConfigChange(config.copy(gradientBottomColor = it)) },
                modifier = Modifier.weight(1f)
            )
        }
        CaptionedSlider(
            caption = stringResource(Res.string.gradient_top_opacity),
            readout = percentReadout(config.gradientTopOpacity),
            value = config.gradientTopOpacity,
            onValueChange = { onConfigChange(config.copy(gradientTopOpacity = it)) }
        )
        CaptionedSlider(
            caption = stringResource(Res.string.gradient_bottom_opacity),
            readout = percentReadout(config.gradientBottomOpacity),
            value = config.gradientBottomOpacity,
            onValueChange = { onConfigChange(config.copy(gradientBottomOpacity = it)) }
        )
        CaptionedSlider(
            caption = stringResource(Res.string.gradient_position),
            readout = percentReadout(config.gradientPosition),
            value = config.gradientPosition,
            onValueChange = { onConfigChange(config.copy(gradientPosition = it)) }
        )
    }
}

/**
 * Opacity, dim and blur — the three every background in the app now carries, so one configured
 * here and one a song brings with it can be made to look alike.
 *
 * The four Look presets are the same four the per-song background panel offers, off the same
 * list: a dim and a blur that read well together, so the usual case is one click rather than two
 * sliders. They stay in step with the sliders below because they *are* the sliders' two values.
 */
@Composable
private fun BackgroundLookSliders(config: BackgroundConfig, onConfigChange: (BackgroundConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            PanelCaption(stringResource(Res.string.song_background_look))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                SONG_BACKGROUND_LOOKS.forEach { preset ->
                    PresetButton(
                        label = stringResource(preset.label),
                        selected = config.dim == preset.dim && config.blur == preset.blur,
                        modifier = Modifier.weight(1f),
                    ) { onConfigChange(config.copy(dim = preset.dim, blur = preset.blur)) }
                }
            }
        }
        CaptionedSlider(
            caption = stringResource(Res.string.background_opacity_caption),
            readout = percentReadout(config.backgroundOpacity),
            value = config.backgroundOpacity,
            onValueChange = { onConfigChange(config.copy(backgroundOpacity = it)) }
        )
        CaptionedSlider(
            caption = stringResource(Res.string.song_background_dim),
            readout = "${config.dim}%",
            value = config.dim.toFloat(),
            valueRange = 0f..BACKGROUND_DIM_MAX.toFloat(),
            onValueChange = { onConfigChange(config.copy(dim = it.roundToInt())) }
        )
        CaptionedSlider(
            caption = stringResource(Res.string.song_background_blur),
            readout = "${config.blur}${stringResource(Res.string.unit_px)}",
            value = config.blur.toFloat(),
            valueRange = 0f..SONG_BACKGROUND_MAX_BLUR.toFloat(),
            onValueChange = { onConfigChange(config.copy(blur = it.roundToInt())) }
        )
    }
}

/**
 * Putting this look on the other content surfaces of the same shape.
 *
 * Only ever full screen onto full screen, or band onto band: the two are cropped differently and a
 * picture chosen for one is rarely the picture for the other.
 */
@Composable
private fun CopyLookSection(
    targets: List<BackgroundScope>,
    config: BackgroundConfig,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelCaption(stringResource(Res.string.background_copy_look_to))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            targets.forEach { target ->
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable {
                            onSettingsChange { s ->
                                s.copy(
                                    backgroundSettings = s.backgroundSettings.withConfigFor(target, config)
                                )
                            }
                        }
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(backgroundGroupLabel(target.group)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** A caption, its readout, and the track between them — the shape every slider here takes. */
@Composable
private fun CaptionedSlider(
    caption: String,
    readout: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelCaption(caption, Modifier.weight(1f))
            Text(
                text = readout,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        SlimSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun percentReadout(fraction: Float): String = "${(fraction * PERCENT).toInt()}%"

/** The types that have something to fade, dim or blur. */
private val ADJUSTABLE_TYPES = setOf(
    Constants.BACKGROUND_COLOR,
    Constants.BACKGROUND_IMAGE,
    Constants.BACKGROUND_VIDEO
)

/** Dim is a percentage, like every other percentage in this tab. */
private const val BACKGROUND_DIM_MAX = 100
private const val PERCENT = 100f
private val COLOR_FIELD_WIDTH = 150.dp
private val SWATCH_HEIGHT = 22.dp
