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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.song_chord_color
import churchpresenter.composeapp.generated.resources.display_mode_label
import churchpresenter.composeapp.generated.resources.display_mode_one_line
import churchpresenter.composeapp.generated.resources.display_mode_one_verse
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.fullscreen_display
import churchpresenter.composeapp.generated.resources.lower_third_display
import churchpresenter.composeapp.generated.resources.horizontal_alignment
import churchpresenter.composeapp.generated.resources.lyrics
import churchpresenter.composeapp.generated.resources.song_language_both
import churchpresenter.composeapp.generated.resources.song_language_primary
import churchpresenter.composeapp.generated.resources.song_language_secondary
import churchpresenter.composeapp.generated.resources.enabled
import churchpresenter.composeapp.generated.resources.title
import churchpresenter.composeapp.generated.resources.vertical_alignment
import churchpresenter.composeapp.generated.resources.word_wrap
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.material3.Surface
import churchpresenter.composeapp.generated.resources.auto_fit
import churchpresenter.composeapp.generated.resources.auto_fit_checkbox_tooltip
import churchpresenter.composeapp.generated.resources.auto_fit_button_tooltip
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingRow
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitFontSize
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RightColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>,
    presenterManager: PresenterManager? = null
) {

    val textMeasurer = rememberTextMeasurer()
    val isPresentingLyrics = if (presenterManager != null) {
        remember { derivedStateOf {
            presenterManager.presentingMode.value == Presenting.LYRICS &&
            presenterManager.lyricSection.value.lines.any { it.isNotBlank() }
        } }.value
    } else false
    // BOTH in the Options tab; one profile only when the Customize dialog is editing one output.
    val scope = LocalOutputStyleScope.current
    val activeScreens = settings.projectionSettings.screenAssignments
    val hasFullscreenScreen = activeScreens.any { it.displayMode == Constants.DISPLAY_MODE_FULLSCREEN }
    val hasLowerThirdScreen = activeScreens.any { it.isLowerThird }

    SettingsSection(title = stringResource(Res.string.lyrics)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var initialWordWrapValue by remember { mutableStateOf(settings.songSettings.wordWrap) }
        Checkbox(
            checked = initialWordWrapValue,
            onCheckedChange = {
                initialWordWrapValue = it
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(wordWrap = it))
                }
            },
            modifier = Modifier.testTag("song_wordWrap")
        )
        Text(
            text = stringResource(Res.string.word_wrap),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(5.dp))

    SettingRow(stringResource(Res.string.vertical_alignment), width = 200.dp) {
        VerticalAlignmentButtons(
            selectedAlignment = settings.songSettings.lyricsAlignment,
            onAlignmentChange = { storedValue ->
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(lyricsAlignment = storedValue))
                }
            },
            topValue = Constants.TOP,
            middleValue = Constants.MIDDLE,
            bottomValue = Constants.BOTTOM
        )
    }
    }

    if (scope.showsFullScreen) SettingsSection(title = stringResource(Res.string.fullscreen_display)) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val fsDisplayMode = settings.songSettings.fullscreenDisplayMode
        Text(text = stringResource(Res.string.display_mode_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            SegmentedButton(
                selected = fsDisplayMode == Constants.SONG_DISPLAY_MODE_VERSE,
                onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE)) } },
                shape = segmentedItemShape(index = 0, count = 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                icon = {}
            ) { Text(stringResource(Res.string.display_mode_one_verse), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            SegmentedButton(
                selected = fsDisplayMode == Constants.SONG_DISPLAY_MODE_LINE,
                onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_LINE)) } },
                shape = segmentedItemShape(index = 1, count = 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                icon = {}
            ) { Text(stringResource(Res.string.display_mode_one_line), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
        val fsModes = listOf(
            Constants.SONG_LANG_BOTH to stringResource(Res.string.song_language_both),
            Constants.SONG_LANG_PRIMARY to stringResource(Res.string.song_language_primary),
            Constants.SONG_LANG_SECONDARY to stringResource(Res.string.song_language_secondary)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            fsModes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = settings.songSettings.fullscreenLanguageDisplay == mode,
                    onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fullscreenLanguageDisplay = mode)) } },
                    shape = segmentedItemShape(index = index, count = fsModes.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    icon = {}
                ) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
    }

    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberSettingsTextField(
                initialText = settings.songSettings.lyricsFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsFontSize = it)) } },
                range = 8..150
            )
            TooltipArea(
                tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
            ) {
                LabeledCheckbox(
                    checked = settings.songSettings.lyricsFontSizeAutoFit,
                    onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsFontSizeAutoFit = it)) } },
                    controlModifier = Modifier.size(24.dp),
                    label = stringResource(Res.string.auto_fit),
                    modifier = Modifier.testTag("song_lyricsFontSizeAutoFit"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (presenterManager != null) {
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_button_tooltip), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    TextButton(
                        shape = RoundedCornerShape(6.dp),
                        enabled = isPresentingLyrics && hasFullscreenScreen,
                        onClick = {
                            val section = presenterManager.lyricSection.value
                            val lyricsText = section.lines.joinToString("\n")
                            if (lyricsText.isBlank()) return@TextButton
                            val ss = settings.songSettings
                            val proj = settings.projectionSettings
                            val baseStyle = TextStyle(
                                fontFamily = systemFontFamilyOrDefault(ss.lyricsFontType),
                                fontWeight = if (ss.lyricsBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (ss.lyricsItalic) FontStyle.Italic else FontStyle.Normal,
                                textDecoration = if (ss.lyricsUnderline) TextDecoration.Underline else TextDecoration.None
                            )
                            val availW = 1920 - proj.windowLeft - proj.windowRight - ss.marginLeft - ss.marginRight
                            val availH = 1080 - proj.windowTop - proj.windowBottom - ss.marginTop - ss.marginBottom
                            val shouldShowTitle = ss.titleDisplay != Constants.NONE && section.title.isNotBlank()
                            val titleH = if (shouldShowTitle) {
                                val titleStyle = TextStyle(
                                    fontFamily = systemFontFamilyOrDefault(ss.titleFontType),
                                    fontWeight = if (ss.titleBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (ss.titleItalic) FontStyle.Italic else FontStyle.Normal
                                )
                                val titleResult = textMeasurer.measure(section.title, titleStyle.copy(fontSize = ss.titleFontSize.sp), density = Density(1f))
                                titleResult.size.height
                            } else 0
                            val fullSize = calculateAutoFitFontSize(textMeasurer, lyricsText, baseStyle, availW, availH - titleH)
                            onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsFontSize = fullSize)) }
                        },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    SettingRow(stringResource(Res.string.font_type)) {
        FontSettingsDropdown(
            value = settings.songSettings.lyricsFontType,
            fonts = availableFonts,
            onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsFontType = it)) } }
        )
    }

    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        HorizontalAlignmentButtons(
            selectedAlignment = settings.songSettings.lyricsHorizontalAlignment,
            onAlignmentChange = { storedValue ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsHorizontalAlignment = storedValue)) }
            },
            leftValue = Constants.LEFT,
            centerValue = Constants.CENTER,
            rightValue = Constants.RIGHT
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorPickerField(
            label = stringResource(Res.string.color),
            modifier = Modifier.width(120.dp),
            color = settings.songSettings.lyricsColor,
            onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsColor = it)) } }
        )
        ColorPickerField(
            label = stringResource(Res.string.song_chord_color),
            modifier = Modifier.width(120.dp),
            color = settings.songSettings.lyricsChordColor,
            onColorChange = { picked ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsChordColor = picked)) }
            }
        )
        TextStyleButtons(
            bold = settings.songSettings.lyricsBold,
            italic = settings.songSettings.lyricsItalic,
            underline = settings.songSettings.lyricsUnderline,
            shadow = settings.songSettings.lyricsShadow,
            onBoldChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsBold = it)) } },
            onItalicChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsItalic = it)) } },
            onUnderlineChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsUnderline = it)) } },
            onShadowChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsShadow = it)) } }
        )
    }
    AnimatedVisibility(visible = settings.songSettings.lyricsShadow) {
        ShadowDetailRow(
            shadowColor = settings.songSettings.lyricsShadowColor,
            shadowSize = settings.songSettings.lyricsShadowSize,
            shadowOpacity = settings.songSettings.lyricsShadowOpacity,
            onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsShadowColor = it)) } },
            onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsShadowSize = it)) } },
            onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsShadowOpacity = it)) } }
        )
    }

    }

    if (scope.showsLowerThird) SettingsSection(title = stringResource(Res.string.lower_third_display)) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val ltDisplayMode = settings.songSettings.lowerThirdDisplayMode
        Text(text = stringResource(Res.string.display_mode_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            SegmentedButton(
                selected = ltDisplayMode == Constants.SONG_DISPLAY_MODE_VERSE,
                onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE)) } },
                shape = segmentedItemShape(index = 0, count = 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                icon = {}
            ) { Text(stringResource(Res.string.display_mode_one_verse), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            SegmentedButton(
                selected = ltDisplayMode == Constants.SONG_DISPLAY_MODE_LINE,
                onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_LINE)) } },
                shape = segmentedItemShape(index = 1, count = 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                icon = {}
            ) { Text(stringResource(Res.string.display_mode_one_line), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
        val ltModes = listOf(
            Constants.SONG_LANG_BOTH to stringResource(Res.string.song_language_both),
            Constants.SONG_LANG_PRIMARY to stringResource(Res.string.song_language_primary),
            Constants.SONG_LANG_SECONDARY to stringResource(Res.string.song_language_secondary)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            ltModes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = settings.songSettings.lowerThirdLanguageDisplay == mode,
                    onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLanguageDisplay = mode)) } },
                    shape = segmentedItemShape(index = index, count = ltModes.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = MaterialTheme.colorScheme.onPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    icon = {}
                ) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
    }

    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberSettingsTextField(
                initialText = settings.songSettings.lyricsLowerThirdFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdFontSize = it)) } },
                range = 8..150
            )
            TooltipArea(
                tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
            ) {
                LabeledCheckbox(
                    checked = settings.songSettings.lyricsLowerThirdFontSizeAutoFit,
                    onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdFontSizeAutoFit = it)) } },
                    controlModifier = Modifier.size(24.dp),
                    label = stringResource(Res.string.auto_fit),
                    modifier = Modifier.testTag("song_lyricsLowerThirdFontSizeAutoFit"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (presenterManager != null) {
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_button_tooltip), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                TextButton(
                    shape = RoundedCornerShape(6.dp),
                    enabled = isPresentingLyrics && hasLowerThirdScreen,
                    onClick = {
                        val section = presenterManager.lyricSection.value
                        val lyricsText = section.lines.joinToString("\n")
                        if (lyricsText.isBlank()) return@TextButton
                        val ss = settings.songSettings
                        val proj = settings.projectionSettings
                        val baseStyle = TextStyle(
                            fontFamily = systemFontFamilyOrDefault(ss.lyricsLowerThirdFontType),
                            fontWeight = if (ss.lyricsLowerThirdBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (ss.lyricsLowerThirdItalic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (ss.lyricsLowerThirdUnderline) TextDecoration.Underline else TextDecoration.None
                        )
                        val availW = 1920 - proj.windowLeft - proj.windowRight - ss.marginLeft - ss.marginRight
                        val availH = 1080 - proj.windowTop - proj.windowBottom - ss.marginTop - ss.marginBottom
                        val ltH = (availH * proj.lowerThirdHeightPercent / 100f).toInt()
                        val shouldShowTitle = ss.titleDisplay != Constants.NONE && section.title.isNotBlank()
                        val titleH = if (shouldShowTitle) {
                            val titleStyle = TextStyle(
                                fontFamily = systemFontFamilyOrDefault(ss.titleLowerThirdFontType),
                                fontWeight = if (ss.titleLowerThirdBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (ss.titleLowerThirdItalic) FontStyle.Italic else FontStyle.Normal
                            )
                            val titleResult = textMeasurer.measure(section.title, titleStyle.copy(fontSize = ss.titleLowerThirdFontSize.sp), density = Density(1f))
                            titleResult.size.height
                        } else 0
                        val ltSize = calculateAutoFitFontSize(textMeasurer, lyricsText, baseStyle, availW, ltH - titleH)
                        onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdFontSize = ltSize)) }
                    },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.labelSmall)
                }
                }
            }
        }
    }

    SettingRow(stringResource(Res.string.font_type)) {
        FontSettingsDropdown(
            value = settings.songSettings.lyricsLowerThirdFontType,
            fonts = availableFonts,
            onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdFontType = it)) } }
        )
    }

    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        HorizontalAlignmentButtons(
            selectedAlignment = settings.songSettings.lyricsLowerThirdHorizontalAlignment,
            onAlignmentChange = { storedValue ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdHorizontalAlignment = storedValue)) }
            },
            leftValue = Constants.LEFT,
            centerValue = Constants.CENTER,
            rightValue = Constants.RIGHT
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorPickerField(
            label = stringResource(Res.string.color),
            modifier = Modifier.width(120.dp),
            color = settings.songSettings.lyricsLowerThirdColor,
            onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdColor = it)) } }
        )
        ColorPickerField(
            label = stringResource(Res.string.song_chord_color),
            modifier = Modifier.width(120.dp),
            color = settings.songSettings.lyricsLowerThirdChordColor,
            onColorChange = { picked ->
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdChordColor = picked))
                }
            }
        )
        TextStyleButtons(
            bold = settings.songSettings.lyricsLowerThirdBold,
            italic = settings.songSettings.lyricsLowerThirdItalic,
            underline = settings.songSettings.lyricsLowerThirdUnderline,
            shadow = settings.songSettings.lyricsLowerThirdShadow,
            onBoldChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdBold = it)) } },
            onItalicChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdItalic = it)) } },
            onUnderlineChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdUnderline = it)) } },
            onShadowChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdShadow = it)) } }
        )
    }
    AnimatedVisibility(visible = settings.songSettings.lyricsLowerThirdShadow) {
        ShadowDetailRow(
            shadowColor = settings.songSettings.lyricsLowerThirdShadowColor,
            shadowSize = settings.songSettings.lyricsLowerThirdShadowSize,
            shadowOpacity = settings.songSettings.lyricsLowerThirdShadowOpacity,
            onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdShadowColor = it)) } },
            onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdShadowSize = it)) } },
            onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdShadowOpacity = it)) } }
        )
    }
    }
}
