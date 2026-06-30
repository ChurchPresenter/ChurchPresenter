package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import churchpresenter.composeapp.generated.resources.show
import churchpresenter.composeapp.generated.resources.fade_in
import churchpresenter.composeapp.generated.resources.fade_out
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.transition_duration
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingRow
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
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
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left column: Word + Definition
            Column(
                modifier = Modifier.weight(0.48f).widthIn(min = 360.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // Word section
            SettingsSection(title = stringResource(Res.string.dictionary_settings_word_text)) {
                SettingRow(label = stringResource(Res.string.show)) {
                    Switch(
                        checked = ds.showWord,
                        onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(showWord = it)) } }
                    )
                }

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

            }

            // Definition section
            SettingsSection(title = stringResource(Res.string.dictionary_settings_definition_text)) {
                SettingRow(label = stringResource(Res.string.show)) {
                    Switch(
                        checked = ds.showDefinition,
                        onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(showDefinition = it)) } }
                    )
                }

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
            } // end Definition SettingsSection
            } // end left column

            // Right column: Reference + KJV + Card Background + Transitions
            Column(
                modifier = Modifier.weight(0.48f).widthIn(min = 360.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reference & Transliteration
                SettingsSection(title = stringResource(Res.string.dictionary_settings_reference_text)) {
                    SettingRow(label = stringResource(Res.string.show)) {
                        Switch(
                            checked = ds.showReference,
                            onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(showReference = it)) } }
                        )
                    }

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
                SettingsSection(title = stringResource(Res.string.dictionary_settings_kjv_usage)) {
                    SettingRow(label = stringResource(Res.string.show)) {
                        Switch(
                            checked = ds.showKjvUsage,
                            onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(showKjvUsage = it)) } }
                        )
                    }

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
                SettingsSection(title = stringResource(Res.string.dictionary_settings_card_background)) {
                    ColorPickerField(
                        color = ds.cardBackgroundColor,
                        onColorChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(cardBackgroundColor = it)) } },
                        label = stringResource(Res.string.color),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingRow(stringResource(Res.string.dictionary_settings_opacity)) {
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
                SettingsSection(title = stringResource(Res.string.dictionary_settings_transitions)) {
                    SettingRow(label = stringResource(Res.string.fade_in)) {
                        Switch(
                            checked = ds.fadeIn,
                            onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(fadeIn = it)) } }
                        )
                    }
                    SettingRow(label = stringResource(Res.string.fade_out)) {
                        Switch(
                            checked = ds.fadeOut,
                            onCheckedChange = { onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(fadeOut = it)) } }
                        )
                    }

                    SettingRow(stringResource(Res.string.transition_duration)) {
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

