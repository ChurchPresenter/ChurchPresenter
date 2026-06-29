package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.dictionary_settings_card_background
import churchpresenter.composeapp.generated.resources.dictionary_settings_definition_text
import churchpresenter.composeapp.generated.resources.dictionary_settings_kjv_usage
import churchpresenter.composeapp.generated.resources.dictionary_settings_opacity
import churchpresenter.composeapp.generated.resources.dictionary_settings_reference_text
import churchpresenter.composeapp.generated.resources.dictionary_settings_transitions
import churchpresenter.composeapp.generated.resources.dictionary_settings_word_text
import churchpresenter.composeapp.generated.resources.fade_in
import churchpresenter.composeapp.generated.resources.fade_out
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.transition_duration
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment

@Composable
fun DictionarySettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val availableFonts = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }
    val ds = settings.dictionarySettings

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left column: Word + Definition
            Column(
                modifier = Modifier.weight(0.48f).widthIn(min = 360.dp, max = 450.dp).heightIn(min = 500.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Word section
                DictSectionHeader(
                    text = stringResource(Res.string.dictionary_settings_word_text),
                    checked = ds.showWord,
                    onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(showWord = it)) } },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorPickerField(
                        color = ds.wordColor,
                        onColorChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(wordColor = it)) } },
                        label = stringResource(Res.string.color),
                        modifier = Modifier.weight(1f)
                    )
                    TextStyleButtons(
                        bold = ds.wordBold,
                        italic = ds.wordItalic,
                        underline = false,
                        shadow = ds.wordShadow,
                        onBoldChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(wordBold = it)) } },
                        onItalicChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(wordItalic = it)) } },
                        onUnderlineChange = { },
                        onShadowChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(wordShadow = it)) } }
                    )
                }
                AnimatedVisibility(visible = ds.wordShadow) {
                    ShadowDetailRow(
                        shadowColor = ds.wordShadowColor,
                        shadowSize = ds.wordShadowSize,
                        shadowOpacity = ds.wordShadowOpacity,
                        onColorChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(wordShadowColor = it)) } },
                        onSizeChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(wordShadowSize = it)) } },
                        onOpacityChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(wordShadowOpacity = it)) } }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FontSettingsDropdown(
                        modifier = Modifier.weight(1f),
                        label = stringResource(Res.string.font_type),
                        value = ds.wordFontType,
                        fonts = availableFonts,
                        onValueChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(wordFontType = it)) } }
                    )
                    NumberSettingsTextField(
                        label = stringResource(Res.string.font_size),
                        initialText = ds.wordFontSize,
                        range = 8..200,
                        onValueChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(wordFontSize = it)) } }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Definition section
                DictSectionHeader(
                    text = stringResource(Res.string.dictionary_settings_definition_text),
                    checked = ds.showDefinition,
                    onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(showDefinition = it)) } },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorPickerField(
                        color = ds.definitionColor,
                        onColorChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(definitionColor = it)) } },
                        label = stringResource(Res.string.color),
                        modifier = Modifier.weight(1f)
                    )
                    NumberSettingsTextField(
                        label = stringResource(Res.string.font_size),
                        initialText = ds.definitionFontSize,
                        range = 8..120,
                        onValueChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(definitionFontSize = it)) } }
                    )
                }
            }

            // Right column: Reference + KJV + Card Background + Transitions
            Column(
                modifier = Modifier.weight(0.48f).widthIn(min = 360.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val cardMod = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp)

                // Reference & Transliteration
                Column(modifier = cardMod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DictSectionHeader(
                        text = stringResource(Res.string.dictionary_settings_reference_text),
                        checked = ds.showReference,
                        onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(showReference = it)) } },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorPickerField(
                            color = ds.referenceColor,
                            onColorChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(referenceColor = it)) } },
                            label = stringResource(Res.string.color),
                            modifier = Modifier.weight(1f)
                        )
                        TextStyleButtons(
                            bold = false,
                            italic = false,
                            underline = false,
                            shadow = ds.referenceShadow,
                            onBoldChange = { },
                            onItalicChange = { },
                            onUnderlineChange = { },
                            onShadowChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(referenceShadow = it)) } }
                        )
                    }
                    AnimatedVisibility(visible = ds.referenceShadow) {
                        ShadowDetailRow(
                            shadowColor = ds.referenceShadowColor,
                            shadowSize = ds.referenceShadowSize,
                            shadowOpacity = ds.referenceShadowOpacity,
                            onColorChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(referenceShadowColor = it)) } },
                            onSizeChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(referenceShadowSize = it)) } },
                            onOpacityChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(referenceShadowOpacity = it)) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FontSettingsDropdown(
                            modifier = Modifier.weight(1f),
                            label = stringResource(Res.string.font_type),
                            value = ds.referenceFontType,
                            fonts = availableFonts,
                            onValueChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(referenceFontType = it)) } }
                        )
                        NumberSettingsTextField(
                            label = stringResource(Res.string.font_size),
                            initialText = ds.referenceFontSize,
                            range = 8..120,
                            onValueChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(referenceFontSize = it)) } }
                        )
                    }
                }

                // KJV Usage
                Column(modifier = cardMod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DictSectionHeader(
                        text = stringResource(Res.string.dictionary_settings_kjv_usage),
                        checked = ds.showKjvUsage,
                        onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(showKjvUsage = it)) } },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorPickerField(
                            color = ds.kjvUsageColor,
                            onColorChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(kjvUsageColor = it)) } },
                            label = stringResource(Res.string.color),
                            modifier = Modifier.weight(1f)
                        )
                        NumberSettingsTextField(
                            label = stringResource(Res.string.font_size),
                            initialText = ds.kjvUsageFontSize,
                            range = 8..80,
                            onValueChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(kjvUsageFontSize = it)) } }
                        )
                    }
                }

                // Card Background
                Column(modifier = cardMod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DictSectionHeader(stringResource(Res.string.dictionary_settings_card_background))

                    ColorPickerField(
                        color = ds.cardBackgroundColor,
                        onColorChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(cardBackgroundColor = it)) } },
                        label = stringResource(Res.string.color),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DictSettingRow(stringResource(Res.string.dictionary_settings_opacity)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Slider(
                                value = ds.cardBackgroundOpacity,
                                onValueChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(cardBackgroundOpacity = it)) } },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(ds.cardBackgroundOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                    }
                }

                // Transitions
                Column(modifier = cardMod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DictSectionHeader(stringResource(Res.string.dictionary_settings_transitions))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = ds.fadeIn,
                            onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(fadeIn = it)) } }
                        )
                        Text(
                            text = stringResource(Res.string.fade_in),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Checkbox(
                            checked = ds.fadeOut,
                            onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(fadeOut = it)) } }
                        )
                        Text(
                            text = stringResource(Res.string.fade_out),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    DictSettingRow(stringResource(Res.string.transition_duration)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Slider(
                                value = ds.transitionDuration,
                                onValueChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(transitionDuration = it)) } },
                                valueRange = 100f..2000f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${ds.transitionDuration.toInt()} ${stringResource(Res.string.milliseconds_suffix)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(60.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictSectionHeader(
    text: String,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (checked != null && onCheckedChange != null) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DictSettingRow(
    label: String,
    width: Dp = 120.dp,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(width)
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
