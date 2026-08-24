package org.churchpresenter.dictionary.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.color
import org.churchpresenter.resources.generated.resources.dictionary_settings_card_background
import org.churchpresenter.resources.generated.resources.dictionary_settings_definition_text
import org.churchpresenter.resources.generated.resources.dictionary_settings_kjv_usage
import org.churchpresenter.resources.generated.resources.dictionary_settings_opacity
import org.churchpresenter.resources.generated.resources.dictionary_settings_reference_text
import org.churchpresenter.resources.generated.resources.dictionary_settings_transitions
import org.churchpresenter.resources.generated.resources.dictionary_settings_word_text
import org.churchpresenter.resources.generated.resources.show
import org.churchpresenter.resources.generated.resources.fade_in
import org.churchpresenter.resources.generated.resources.fade_out
import org.churchpresenter.resources.generated.resources.font_size
import org.churchpresenter.resources.generated.resources.font_type
import org.churchpresenter.resources.generated.resources.milliseconds_suffix
import org.churchpresenter.resources.generated.resources.transition_duration
import org.churchpresenter.ui.ColorPickerField
import org.churchpresenter.ui.FontSettingsDropdown
import org.churchpresenter.ui.NumberSettingsTextField
import org.churchpresenter.ui.SettingRow
import org.churchpresenter.ui.SettingsScrollbar
import org.churchpresenter.ui.SettingsScrollbarGutter
import org.churchpresenter.ui.SettingsSection
import org.churchpresenter.ui.ShadowDetailRow
import org.churchpresenter.ui.SlimSlider
import org.churchpresenter.ui.TextStyleButtons
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.ui.rememberSystemFonts
import org.jetbrains.compose.resources.stringResource

private const val COLUMN_WEIGHT = 0.48f

@Composable
fun DictionarySettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val availableFonts = rememberSystemFonts()
    val ds = settings.dictionarySettings

    // Every control on this tab changes one field of DictionarySettings, so they all go through one
    // updater rather than restating the two nested copies at each of the thirty call sites below.
    val updateDict: (DictionarySettings.() -> DictionarySettings) -> Unit = { change ->
        onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.change()) }
    }

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = SettingsScrollbarGutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left column: Word + Definition
            Column(
                modifier = Modifier.weight(COLUMN_WEIGHT).widthIn(min = 360.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // Word section
            SettingsSection(title = stringResource(Res.string.dictionary_settings_word_text)) {
                SettingRow(label = stringResource(Res.string.show)) {
                    Switch(
                        checked = ds.showWord,
                        onCheckedChange = { updateDict { copy(showWord = it) } }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorPickerField(
                        color = ds.wordColor,
                        onColorChange = { updateDict { copy(wordColor = it) } },
                        label = stringResource(Res.string.color).removeSuffix(":"),
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                    TextStyleButtons(
                        bold = ds.wordBold,
                        italic = ds.wordItalic,
                        underline = false,
                        shadow = ds.wordShadow,
                        onBoldChange = { updateDict { copy(wordBold = it) } },
                        onItalicChange = { updateDict { copy(wordItalic = it) } },
                        onUnderlineChange = { },
                        onShadowChange = { updateDict { copy(wordShadow = it) } }
                    )
                }
                AnimatedVisibility(visible = ds.wordShadow) {
                    ShadowDetailRow(
                        shadowColor = ds.wordShadowColor,
                        shadowSize = ds.wordShadowSize,
                        shadowOpacity = ds.wordShadowOpacity,
                        onColorChange = { updateDict { copy(wordShadowColor = it) } },
                        onSizeChange = { updateDict { copy(wordShadowSize = it) } },
                        onOpacityChange = { updateDict { copy(wordShadowOpacity = it) } }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FontSettingsDropdown(
                        modifier = Modifier,
                        label = stringResource(Res.string.font_type).removeSuffix(":"),
                        value = ds.wordFontType,
                        fonts = availableFonts,
                        onValueChange = { updateDict { copy(wordFontType = it) } }
                    )
                    NumberSettingsTextField(
                        label = stringResource(Res.string.font_size).removeSuffix(":"),
                        initialText = ds.wordFontSize,
                        range = 8..200,
                        onValueChange = { updateDict { copy(wordFontSize = it) } }
                    )
                }

            }

            // Definition section
            SettingsSection(title = stringResource(Res.string.dictionary_settings_definition_text)) {
                SettingRow(label = stringResource(Res.string.show)) {
                    Switch(
                        checked = ds.showDefinition,
                        onCheckedChange = { updateDict { copy(showDefinition = it) } }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorPickerField(
                        color = ds.definitionColor,
                        onColorChange = { updateDict { copy(definitionColor = it) } },
                        label = stringResource(Res.string.color).removeSuffix(":"),
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                    NumberSettingsTextField(
                        label = stringResource(Res.string.font_size).removeSuffix(":"),
                        initialText = ds.definitionFontSize,
                        range = 8..120,
                        onValueChange = { updateDict { copy(definitionFontSize = it) } }
                    )
                }
            } // end Definition SettingsSection

            // Card Background
            SettingsSection(title = stringResource(Res.string.dictionary_settings_card_background)) {
                ColorPickerField(
                    color = ds.cardBackgroundColor,
                    onColorChange = { updateDict { copy(cardBackgroundColor = it) } },
                    label = stringResource(Res.string.color).removeSuffix(":"),
                    modifier = Modifier.widthIn(max = 150.dp)
                )
                SettingRow(stringResource(Res.string.dictionary_settings_opacity)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SlimSlider(
                            value = ds.cardBackgroundOpacity,
                            onValueChange = { updateDict { copy(cardBackgroundOpacity = it) } },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                            trailingLabel = "${(ds.cardBackgroundOpacity * 100).toInt()}%"
                        )
                    }
                }
            }
            } // end left column

            // Right column: Reference + KJV + Card Background + Transitions
            Column(
                modifier = Modifier.weight(COLUMN_WEIGHT).widthIn(min = 360.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reference & Transliteration
                SettingsSection(title = stringResource(Res.string.dictionary_settings_reference_text)) {
                    SettingRow(label = stringResource(Res.string.show)) {
                        Switch(
                            checked = ds.showReference,
                            onCheckedChange = { updateDict { copy(showReference = it) } }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorPickerField(
                            color = ds.referenceColor,
                            onColorChange = { updateDict { copy(referenceColor = it) } },
                            label = stringResource(Res.string.color).removeSuffix(":"),
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                        TextStyleButtons(
                            bold = false,
                            italic = false,
                            underline = false,
                            shadow = ds.referenceShadow,
                            onBoldChange = { },
                            onItalicChange = { },
                            onUnderlineChange = { },
                            onShadowChange = { updateDict { copy(referenceShadow = it) } }
                        )
                    }
                    AnimatedVisibility(visible = ds.referenceShadow) {
                        ShadowDetailRow(
                            shadowColor = ds.referenceShadowColor,
                            shadowSize = ds.referenceShadowSize,
                            shadowOpacity = ds.referenceShadowOpacity,
                            onColorChange = { updateDict { copy(referenceShadowColor = it) } },
                            onSizeChange = { updateDict { copy(referenceShadowSize = it) } },
                            onOpacityChange = { updateDict { copy(referenceShadowOpacity = it) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FontSettingsDropdown(
                            modifier = Modifier,
                            label = stringResource(Res.string.font_type).removeSuffix(":"),
                            value = ds.referenceFontType,
                            fonts = availableFonts,
                            onValueChange = { updateDict { copy(referenceFontType = it) } }
                        )
                        NumberSettingsTextField(
                            label = stringResource(Res.string.font_size).removeSuffix(":"),
                            initialText = ds.referenceFontSize,
                            range = 8..120,
                            onValueChange = { updateDict { copy(referenceFontSize = it) } }
                        )
                    }
                }

                // KJV Usage
                SettingsSection(title = stringResource(Res.string.dictionary_settings_kjv_usage)) {
                    SettingRow(label = stringResource(Res.string.show)) {
                        Switch(
                            checked = ds.showKjvUsage,
                            onCheckedChange = { updateDict { copy(showKjvUsage = it) } }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorPickerField(
                            color = ds.kjvUsageColor,
                            onColorChange = { updateDict { copy(kjvUsageColor = it) } },
                            label = stringResource(Res.string.color).removeSuffix(":"),
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                        NumberSettingsTextField(
                            label = stringResource(Res.string.font_size).removeSuffix(":"),
                            initialText = ds.kjvUsageFontSize,
                            range = 8..80,
                            onValueChange = { updateDict { copy(kjvUsageFontSize = it) } }
                        )
                    }
                }

                // Transitions
                SettingsSection(title = stringResource(Res.string.dictionary_settings_transitions)) {
                    SettingRow(label = stringResource(Res.string.fade_in)) {
                        Switch(
                            checked = ds.fadeIn,
                            onCheckedChange = { updateDict { copy(fadeIn = it) } }
                        )
                    }
                    SettingRow(label = stringResource(Res.string.fade_out)) {
                        Switch(
                            checked = ds.fadeOut,
                            onCheckedChange = { updateDict { copy(fadeOut = it) } }
                        )
                    }

                    SettingRow(stringResource(Res.string.transition_duration).removeSuffix(":")) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val ms = stringResource(Res.string.milliseconds_suffix)
                            SlimSlider(
                                value = ds.transitionDuration,
                                onValueChange = { updateDict { copy(transitionDuration = it) } },
                                valueRange = 100f..2000f,
                                modifier = Modifier.weight(1f),
                                trailingLabel = "${ds.transitionDuration.toInt()} $ms"
                            )
                        }
                    }
                }
            }
        }
        SettingsScrollbar(scrollState)
    }
}

