package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.media_subtitle_background_color
import churchpresenter.composeapp.generated.resources.media_subtitle_font
import churchpresenter.composeapp.generated.resources.media_subtitle_max_lines
import churchpresenter.composeapp.generated.resources.media_subtitle_opacity
import churchpresenter.composeapp.generated.resources.media_subtitle_position
import churchpresenter.composeapp.generated.resources.media_subtitle_size
import churchpresenter.composeapp.generated.resources.media_subtitle_text_color
import churchpresenter.composeapp.generated.resources.qa_pos_bc
import churchpresenter.composeapp.generated.resources.qa_pos_bl
import churchpresenter.composeapp.generated.resources.qa_pos_br
import churchpresenter.composeapp.generated.resources.qa_pos_c
import churchpresenter.composeapp.generated.resources.qa_pos_cl
import churchpresenter.composeapp.generated.resources.qa_pos_cr
import churchpresenter.composeapp.generated.resources.qa_pos_tc
import churchpresenter.composeapp.generated.resources.qa_pos_tl
import churchpresenter.composeapp.generated.resources.qa_pos_tr
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * Font, colour, size, backdrop, outline and position for the app-rendered subtitle overlay
 * (`SubtitleOverlay`, driven by `MediaSettings`). Shared between the Media tab's own settings
 * dialog and `MediaSettingsTab` so there is one control surface, not two.
 */
@Composable
fun SubtitleStyleSettings(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    // Told when the font picker inside this opens or closes -- see FontSettingsDropdown's own
    // `onExpandedChange` doc for why a small host dialog wants to know.
    onFontPickerExpandedChange: (Boolean) -> Unit = {},
) {
    val mediaSettings = settings.mediaSettings
    val update: ((MediaSettings) -> MediaSettings) -> Unit = { transform ->
        onSettingsChange { s -> s.copy(mediaSettings = transform(s.mediaSettings)) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SubtitleStyleTextColumn(
            mediaSettings = mediaSettings,
            update = update,
            modifier = Modifier.weight(1f),
        )
        SubtitleStylePositionColumn(
            mediaSettings = mediaSettings,
            update = update,
            modifier = Modifier.weight(1f),
            onFontPickerExpandedChange = onFontPickerExpandedChange,
        )
    }
}

/** Left column: text colour, bold/italic/underline/shadow, backdrop, outline, background colour. */
@Composable
private fun SubtitleStyleTextColumn(
    mediaSettings: MediaSettings,
    update: ((MediaSettings) -> MediaSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ColorPickerField(
            label = stringResource(Res.string.media_subtitle_text_color),
            color = mediaSettings.textColor,
            onColorChange = { c -> update { it.copy(textColor = c) } },
            modifier = Modifier.fillMaxWidth(),
        )
        TextStyleButtons(
            bold = mediaSettings.bold,
            italic = mediaSettings.italic,
            underline = mediaSettings.underline,
            shadow = mediaSettings.shadow,
            onBoldChange = { v -> update { it.copy(bold = v) } },
            onItalicChange = { v -> update { it.copy(italic = v) } },
            onUnderlineChange = { v -> update { it.copy(underline = v) } },
            onShadowChange = { v -> update { it.copy(shadow = v) } },
            backdrop = mediaSettings.backdrop,
            onBackdropChange = { updated -> update { it.copy(backdrop = updated) } },
            outline = mediaSettings.outline,
            onOutlineChange = { updated -> update { it.copy(outline = updated) } },
        )
        AnimatedVisibility(visible = mediaSettings.shadow) {
            ShadowDetailRow(
                shadowColor = mediaSettings.shadowColor,
                shadowSize = mediaSettings.shadowSize,
                shadowOpacity = mediaSettings.shadowOpacity,
                onColorChange = { c -> update { it.copy(shadowColor = c) } },
                onSizeChange = { v -> update { it.copy(shadowSize = v) } },
                onOpacityChange = { v -> update { it.copy(shadowOpacity = v) } },
            )
        }
        ColorPickerField(
            label = stringResource(Res.string.media_subtitle_background_color),
            color = mediaSettings.backgroundColor,
            onColorChange = { c -> update { it.copy(backgroundColor = c) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Right column: font/size, the position grid, background opacity, and max lines. */
@Composable
private fun SubtitleStylePositionColumn(
    mediaSettings: MediaSettings,
    update: ((MediaSettings) -> MediaSettings) -> Unit,
    modifier: Modifier = Modifier,
    onFontPickerExpandedChange: (Boolean) -> Unit = {},
) {
    val availableFonts = rememberSystemFonts()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FontSettingsDropdown(
                label = stringResource(Res.string.media_subtitle_font),
                value = mediaSettings.fontType,
                fonts = availableFonts,
                onValueChange = { v -> update { it.copy(fontType = v) } },
                onExpandedChange = onFontPickerExpandedChange,
                modifier = Modifier.weight(1f),
            )
            NumberSettingsTextField(
                label = stringResource(Res.string.media_subtitle_size),
                initialText = mediaSettings.fontSize,
                range = MIN_SUBTITLE_FONT_SIZE..MAX_SUBTITLE_FONT_SIZE,
                onValueChange = { v -> update { it.copy(fontSize = v) } },
            )
        }

        Text(
            stringResource(Res.string.media_subtitle_position),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SubtitlePositionGrid(
            position = mediaSettings.position,
            onPositionChange = { p -> update { it.copy(position = p) } },
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.media_subtitle_opacity),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(4.dp))
            SlimSlider(
                value = mediaSettings.backgroundOpacity / OPACITY_PERCENT_SCALE,
                onValueChange = { v -> update { it.copy(backgroundOpacity = (v * OPACITY_PERCENT_SCALE).toInt()) } },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                trailingLabel = "${mediaSettings.backgroundOpacity}%",
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.media_subtitle_max_lines),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            NumberSettingsTextField(
                label = "",
                initialText = mediaSettings.maxLines,
                range = MIN_SUBTITLE_MAX_LINES..MAX_SUBTITLE_MAX_LINES,
                onValueChange = { v -> update { it.copy(maxLines = v) } },
            )
        }
    }
}

/** The 3×3 grid of position choices, same layout STT's own position picker uses. */
@Composable
private fun SubtitlePositionGrid(position: String, onPositionChange: (String) -> Unit) {
    val positions = listOf(
        Constants.TOP_LEFT to stringResource(Res.string.qa_pos_tl),
        Constants.TOP_CENTER to stringResource(Res.string.qa_pos_tc),
        Constants.TOP_RIGHT to stringResource(Res.string.qa_pos_tr),
        Constants.CENTER_LEFT to stringResource(Res.string.qa_pos_cl),
        Constants.CENTER to stringResource(Res.string.qa_pos_c),
        Constants.CENTER_RIGHT to stringResource(Res.string.qa_pos_cr),
        Constants.BOTTOM_LEFT to stringResource(Res.string.qa_pos_bl),
        Constants.BOTTOM_CENTER to stringResource(Res.string.qa_pos_bc),
        Constants.BOTTOM_RIGHT to stringResource(Res.string.qa_pos_br),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        positions.chunked(SUBTITLE_POSITION_GRID_COLUMNS).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowItems.forEach { (posConst, posLabel) ->
                    val isSelected = position == posConst
                    Box(
                        modifier = Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onPositionChange(posConst) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            posLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Columns in the subtitle position grid — same layout STT's own position picker uses. */
private const val SUBTITLE_POSITION_GRID_COLUMNS = 3
private const val MIN_SUBTITLE_FONT_SIZE = 8
private const val MAX_SUBTITLE_FONT_SIZE = 200
private const val MIN_SUBTITLE_MAX_LINES = 1
private const val MAX_SUBTITLE_MAX_LINES = 10
private const val OPACITY_PERCENT_SCALE = 100f
