package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.color
import org.churchpresenter.resources.generated.resources.display_mode_label
import org.churchpresenter.resources.generated.resources.display_mode_one_line
import org.churchpresenter.resources.generated.resources.display_mode_one_verse
import org.churchpresenter.resources.generated.resources.font_size
import org.churchpresenter.resources.generated.resources.font_type
import org.churchpresenter.resources.generated.resources.look_ahead_fullscreen
import org.churchpresenter.resources.generated.resources.look_ahead_lower_third
import org.churchpresenter.resources.generated.resources.look_ahead_next_fullscreen
import org.churchpresenter.resources.generated.resources.look_ahead_next_lower_third
import org.churchpresenter.resources.generated.resources.horizontal_alignment
import org.churchpresenter.resources.generated.resources.song_language_both
import org.churchpresenter.resources.generated.resources.song_language_primary
import org.churchpresenter.resources.generated.resources.song_language_secondary
import org.churchpresenter.resources.generated.resources.title
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.material3.Surface
import org.churchpresenter.resources.generated.resources.auto_fit
import org.churchpresenter.resources.generated.resources.auto_fit_checkbox_tooltip
import org.churchpresenter.ui.ColorPickerField
import org.churchpresenter.ui.FontSettingsDropdown
import org.churchpresenter.ui.HorizontalAlignmentButtons
import org.churchpresenter.ui.NumberSettingsTextField
import org.churchpresenter.ui.SettingRow
import org.churchpresenter.ui.SettingsSection
import org.churchpresenter.ui.ShadowDetailRow
import org.churchpresenter.ui.TextStyleButtons
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.ui.LabeledCheckbox

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun LookAheadColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>
) {

    SettingsSection(title = stringResource(Res.string.look_ahead_fullscreen)) {
    val laFsDisplayMode = settings.songSettings.lookAheadDisplayMode
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(Res.string.display_mode_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            SegmentedButton(
                selected = laFsDisplayMode == Constants.SONG_DISPLAY_MODE_VERSE,
                onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE)) } },
                shape = segmentedItemShape(index = 0, count = 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                icon = {}
            ) { Text(stringResource(Res.string.display_mode_one_verse), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            SegmentedButton(
                selected = laFsDisplayMode == Constants.SONG_DISPLAY_MODE_LINE,
                onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_LINE)) } },
                shape = segmentedItemShape(index = 1, count = 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                icon = {}
            ) { Text(stringResource(Res.string.display_mode_one_line), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
        val laFsModes = listOf(
            Constants.SONG_LANG_BOTH to stringResource(Res.string.song_language_both),
            Constants.SONG_LANG_PRIMARY to stringResource(Res.string.song_language_primary),
            Constants.SONG_LANG_SECONDARY to stringResource(Res.string.song_language_secondary)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            laFsModes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = settings.songSettings.lookAheadLanguageDisplay == mode,
                    onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadLanguageDisplay = mode)) } },
                    shape = segmentedItemShape(index = index, count = laFsModes.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    icon = {}
                ) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
    }
    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        HorizontalAlignmentButtons(
            selectedAlignment = settings.songSettings.lookAheadHorizontalAlignment,
            onAlignmentChange = { storedValue ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadHorizontalAlignment = storedValue)) }
            },
            leftValue = Constants.LEFT,
            centerValue = Constants.CENTER,
            rightValue = Constants.RIGHT
        )
    }
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberSettingsTextField(
                initialText = settings.songSettings.lookAheadFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadFontSize = it)) } },
                range = 8..150
            )
            TooltipArea(
                tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
            ) {
                LabeledCheckbox(
                    checked = settings.songSettings.lookAheadFontSizeAutoFit,
                    onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadFontSizeAutoFit = it)) } },
                    controlModifier = Modifier.size(24.dp),
                    label = stringResource(Res.string.auto_fit),
                    modifier = Modifier.testTag("song_lookAheadFontSizeAutoFit"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    FontSettingsDropdown(
        label = stringResource(Res.string.font_type),
        value = settings.songSettings.lookAheadFontType,
        fonts = availableFonts,
        onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadFontType = it)) } }
    )
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ColorPickerField(
            label = stringResource(Res.string.color),
            modifier = Modifier.width(120.dp),
            color = settings.songSettings.lookAheadColor,
            onColorChange = { color -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadColor = color)) } }
        )
        TextStyleButtons(
            bold = settings.songSettings.lookAheadBold,
            italic = settings.songSettings.lookAheadItalic,
            underline = settings.songSettings.lookAheadUnderline,
            shadow = settings.songSettings.lookAheadShadow,
            onBoldChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadBold = it)) } },
            onItalicChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadItalic = it)) } },
            onUnderlineChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadUnderline = it)) } },
            onShadowChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadShadow = it)) } }
        )
    }
    AnimatedVisibility(visible = settings.songSettings.lookAheadShadow) {
        ShadowDetailRow(
            shadowColor = settings.songSettings.lookAheadShadowColor,
            shadowSize = settings.songSettings.lookAheadShadowSize,
            shadowOpacity = settings.songSettings.lookAheadShadowOpacity,
            onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadShadowColor = it)) } },
            onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadShadowSize = it)) } },
            onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadShadowOpacity = it)) } }
        )
    }

    }

    SettingsSection(title = stringResource(Res.string.look_ahead_next_fullscreen)) {
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberSettingsTextField(
                initialText = settings.songSettings.lookAheadNextFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextFontSize = it)) } },
                range = 8..150
            )
            TooltipArea(
                tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
            ) {
                LabeledCheckbox(
                    checked = settings.songSettings.lookAheadNextFontSizeAutoFit,
                    onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextFontSizeAutoFit = it)) } },
                    controlModifier = Modifier.size(24.dp),
                    label = stringResource(Res.string.auto_fit),
                    modifier = Modifier.testTag("song_lookAheadNextFontSizeAutoFit"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    FontSettingsDropdown(
        label = stringResource(Res.string.font_type),
        value = settings.songSettings.lookAheadNextFontType,
        fonts = availableFonts,
        onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextFontType = it)) } }
    )
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ColorPickerField(
            label = stringResource(Res.string.color),
            modifier = Modifier.width(120.dp),
            color = settings.songSettings.lookAheadNextColor,
            onColorChange = { color -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextColor = color)) } }
        )
        TextStyleButtons(
            bold = settings.songSettings.lookAheadNextBold,
            italic = settings.songSettings.lookAheadNextItalic,
            underline = settings.songSettings.lookAheadNextUnderline,
            shadow = settings.songSettings.lookAheadNextShadow,
            onBoldChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextBold = it)) } },
            onItalicChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextItalic = it)) } },
            onUnderlineChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextUnderline = it)) } },
            onShadowChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextShadow = it)) } }
        )
    }
    AnimatedVisibility(visible = settings.songSettings.lookAheadNextShadow) {
        ShadowDetailRow(
            shadowColor = settings.songSettings.lookAheadNextShadowColor,
            shadowSize = settings.songSettings.lookAheadNextShadowSize,
            shadowOpacity = settings.songSettings.lookAheadNextShadowOpacity,
            onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextShadowColor = it)) } },
            onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextShadowSize = it)) } },
            onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextShadowOpacity = it)) } }
        )
    }

    }

    SettingsSection(title = stringResource(Res.string.look_ahead_lower_third)) {
    val laLtDisplayMode = settings.songSettings.lowerThirdLookAheadDisplayMode
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(Res.string.display_mode_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            SegmentedButton(
                selected = laLtDisplayMode == Constants.SONG_DISPLAY_MODE_VERSE,
                onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE)) } },
                shape = segmentedItemShape(index = 0, count = 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                icon = {}
            ) { Text(stringResource(Res.string.display_mode_one_verse), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            SegmentedButton(
                selected = laLtDisplayMode == Constants.SONG_DISPLAY_MODE_LINE,
                onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_LINE)) } },
                shape = segmentedItemShape(index = 1, count = 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                icon = {}
            ) { Text(stringResource(Res.string.display_mode_one_line), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
        val laLtModes = listOf(
            Constants.SONG_LANG_BOTH to stringResource(Res.string.song_language_both),
            Constants.SONG_LANG_PRIMARY to stringResource(Res.string.song_language_primary),
            Constants.SONG_LANG_SECONDARY to stringResource(Res.string.song_language_secondary)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            laLtModes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = settings.songSettings.lowerThirdLookAheadLanguageDisplay == mode,
                    onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadLanguageDisplay = mode)) } },
                    shape = segmentedItemShape(index = index, count = laLtModes.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    icon = {}
                ) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
    }
    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        HorizontalAlignmentButtons(
            selectedAlignment = settings.songSettings.lowerThirdLookAheadHorizontalAlignment,
            onAlignmentChange = { storedValue ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadHorizontalAlignment = storedValue)) }
            },
            leftValue = Constants.LEFT,
            centerValue = Constants.CENTER,
            rightValue = Constants.RIGHT
        )
    }
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberSettingsTextField(
                initialText = settings.songSettings.lowerThirdLookAheadFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadFontSize = it)) } },
                range = 8..150
            )
            TooltipArea(
                tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
            ) {
                LabeledCheckbox(
                    checked = settings.songSettings.lowerThirdLookAheadFontSizeAutoFit,
                    onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadFontSizeAutoFit = it)) } },
                    controlModifier = Modifier.size(24.dp),
                    label = stringResource(Res.string.auto_fit),
                    modifier = Modifier.testTag("song_lowerThirdLookAheadFontSizeAutoFit"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    FontSettingsDropdown(
        label = stringResource(Res.string.font_type),
        value = settings.songSettings.lowerThirdLookAheadFontType,
        fonts = availableFonts,
        onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadFontType = it)) } }
    )
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ColorPickerField(
            label = stringResource(Res.string.color),
            modifier = Modifier.width(120.dp),
            color = settings.songSettings.lowerThirdLookAheadColor,
            onColorChange = { color -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadColor = color)) } }
        )
        TextStyleButtons(
            bold = settings.songSettings.lowerThirdLookAheadBold,
            italic = settings.songSettings.lowerThirdLookAheadItalic,
            underline = settings.songSettings.lowerThirdLookAheadUnderline,
            shadow = settings.songSettings.lowerThirdLookAheadShadow,
            onBoldChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadBold = it)) } },
            onItalicChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadItalic = it)) } },
            onUnderlineChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadUnderline = it)) } },
            onShadowChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadShadow = it)) } }
        )
    }
    AnimatedVisibility(visible = settings.songSettings.lowerThirdLookAheadShadow) {
        ShadowDetailRow(
            shadowColor = settings.songSettings.lowerThirdLookAheadShadowColor,
            shadowSize = settings.songSettings.lowerThirdLookAheadShadowSize,
            shadowOpacity = settings.songSettings.lowerThirdLookAheadShadowOpacity,
            onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadShadowColor = it)) } },
            onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadShadowSize = it)) } },
            onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadShadowOpacity = it)) } }
        )
    }

    }

    SettingsSection(title = stringResource(Res.string.look_ahead_next_lower_third)) {
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberSettingsTextField(
                initialText = settings.songSettings.lowerThirdLookAheadNextFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextFontSize = it)) } },
                range = 8..150
            )
            TooltipArea(
                tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
            ) {
                LabeledCheckbox(
                    checked = settings.songSettings.lowerThirdLookAheadNextFontSizeAutoFit,
                    onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextFontSizeAutoFit = it)) } },
                    controlModifier = Modifier.size(24.dp),
                    label = stringResource(Res.string.auto_fit),
                    modifier = Modifier.testTag("song_lowerThirdLookAheadNextFontSizeAutoFit"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    FontSettingsDropdown(
        label = stringResource(Res.string.font_type),
        value = settings.songSettings.lowerThirdLookAheadNextFontType,
        fonts = availableFonts,
        onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextFontType = it)) } }
    )
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ColorPickerField(
            label = stringResource(Res.string.color),
            modifier = Modifier.width(120.dp),
            color = settings.songSettings.lowerThirdLookAheadNextColor,
            onColorChange = { color -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextColor = color)) } }
        )
        TextStyleButtons(
            bold = settings.songSettings.lowerThirdLookAheadNextBold,
            italic = settings.songSettings.lowerThirdLookAheadNextItalic,
            underline = settings.songSettings.lowerThirdLookAheadNextUnderline,
            shadow = settings.songSettings.lowerThirdLookAheadNextShadow,
            onBoldChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextBold = it)) } },
            onItalicChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextItalic = it)) } },
            onUnderlineChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextUnderline = it)) } },
            onShadowChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextShadow = it)) } }
        )
    }
    AnimatedVisibility(visible = settings.songSettings.lowerThirdLookAheadNextShadow) {
        ShadowDetailRow(
            shadowColor = settings.songSettings.lowerThirdLookAheadNextShadowColor,
            shadowSize = settings.songSettings.lowerThirdLookAheadNextShadowSize,
            shadowOpacity = settings.songSettings.lowerThirdLookAheadNextShadowOpacity,
            onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextShadowColor = it)) } },
            onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextShadowSize = it)) } },
            onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextShadowOpacity = it)) } }
        )
    }
    }
}
