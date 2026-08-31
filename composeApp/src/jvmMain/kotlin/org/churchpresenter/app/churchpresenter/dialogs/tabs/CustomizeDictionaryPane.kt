package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.show
import churchpresenter.composeapp.generated.resources.dictionary_settings_kjv_usage
import churchpresenter.composeapp.generated.resources.dictionary_settings_reference_text
import churchpresenter.composeapp.generated.resources.customize_background_opacity
import churchpresenter.composeapp.generated.resources.customize_card_background
import churchpresenter.composeapp.generated.resources.customize_group_card
import churchpresenter.composeapp.generated.resources.customize_group_definition
import churchpresenter.composeapp.generated.resources.customize_group_word
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.DictionarySettings
import org.jetbrains.compose.resources.stringResource

/**
 * The Dictionary pane, showing whichever part of the card the chips above it have selected.
 *
 * The fades have moved under the preview into [CustomizeCategoryStrip]; they belong to the card as
 * a whole rather than to the word or the definition inside it.
 */
@Composable
internal fun DictionaryCustomizePane(
    element: CustomizeElement,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val fonts = rememberSystemFonts()
    val ds = settings.dictionarySettings

    fun update(transform: (DictionarySettings) -> DictionarySettings) {
        onSettingsChange { s -> s.copy(dictionarySettings = transform(s.dictionarySettings)) }
    }

    PaneScaffold {
        when (element) {
            CustomizeElement.DICTIONARY_REFERENCE -> DictionaryReferenceGroup(ds, fonts, ::update)
            CustomizeElement.DICTIONARY_DEFINITION -> DictionaryDefinitionGroup(ds, ::update)
            CustomizeElement.DICTIONARY_KJV -> DictionaryKjvUsageGroup(ds, ::update)
            CustomizeElement.DICTIONARY_CARD -> DictionaryCardGroup(ds, ::update)
            else -> DictionaryWordGroup(ds, fonts, ::update)
        }
    }
}

private const val PERCENT = 100f


@Composable
private fun DictionaryWordGroup(
    ds: DictionarySettings,
    fonts: List<String>,
    update: ((DictionarySettings) -> DictionarySettings) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.customize_group_word)) {
        CustomizeRow(stringResource(Res.string.show), labelInsideControl = true) {
            ToggleControl(stringResource(Res.string.show), ds.showWord) { v -> update { it.copy(showWord = v) } }

        }
        CustomizeRow(stringResource(Res.string.font_type), labelInsideControl = true) {
            FontControl(stringResource(Res.string.font_type), ds.wordFontType, fonts) { v ->
                update { it.copy(wordFontType = v) }
            }
        }
        CustomizeRow(stringResource(Res.string.font_size), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.font_size),
                value = ds.wordFontSize,
                onValueChange = { v -> update { it.copy(wordFontSize = v) } },
                range = FONT_SIZE_RANGE,
            )
        }
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(stringResource(Res.string.color), ds.wordColor) { v ->
                update { it.copy(wordColor = v) }
            }
        }
        ShadowRows(
            shadow = ds.wordShadow,
            shadowColor = ds.wordShadowColor,
            shadowSize = ds.wordShadowSize,
            shadowOpacity = ds.wordShadowOpacity,
            onShadow = { v -> update { it.copy(wordShadow = v) } },
            onShadowColor = { v -> update { it.copy(wordShadowColor = v) } },
            onShadowSize = { v -> update { it.copy(wordShadowSize = v) } },
            onShadowOpacity = { v -> update { it.copy(wordShadowOpacity = v) } },
        )
    }
}

@Composable
private fun DictionaryReferenceGroup(
    ds: DictionarySettings,
    fonts: List<String>,
    update: ((DictionarySettings) -> DictionarySettings) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.dictionary_settings_reference_text)) {
        CustomizeRow(stringResource(Res.string.show), labelInsideControl = true) {
            ToggleControl(stringResource(Res.string.show), ds.showReference) { v ->
                update { it.copy(showReference = v) }
            }

        }
        CustomizeRow(stringResource(Res.string.font_type), labelInsideControl = true) {
            FontControl(stringResource(Res.string.font_type), ds.referenceFontType, fonts) { v ->
                update { it.copy(referenceFontType = v) }
            }
        }
        CustomizeRow(stringResource(Res.string.font_size), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.font_size),
                value = ds.referenceFontSize,
                onValueChange = { v -> update { it.copy(referenceFontSize = v) } },
                range = FONT_SIZE_RANGE,
            )
        }
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(stringResource(Res.string.color), ds.referenceColor) { v ->
                update { it.copy(referenceColor = v) }
            }
        }
        ShadowRows(
            shadow = ds.referenceShadow,
            shadowColor = ds.referenceShadowColor,
            shadowSize = ds.referenceShadowSize,
            shadowOpacity = ds.referenceShadowOpacity,
            onShadow = { v -> update { it.copy(referenceShadow = v) } },
            onShadowColor = { v -> update { it.copy(referenceShadowColor = v) } },
            onShadowSize = { v -> update { it.copy(referenceShadowSize = v) } },
            onShadowOpacity = { v -> update { it.copy(referenceShadowOpacity = v) } },
        )
    }
}

@Composable
private fun DictionaryDefinitionGroup(
    ds: DictionarySettings,
    update: ((DictionarySettings) -> DictionarySettings) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.customize_group_definition)) {
        CustomizeRow(stringResource(Res.string.show), labelInsideControl = true) {
            ToggleControl(stringResource(Res.string.show), ds.showDefinition) { v ->
                update { it.copy(showDefinition = v) }
            }

        }
        CustomizeRow(stringResource(Res.string.font_size), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.font_size),
                value = ds.definitionFontSize,
                onValueChange = { v -> update { it.copy(definitionFontSize = v) } },
                range = FONT_SIZE_RANGE,
            )
        }
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(stringResource(Res.string.color), ds.definitionColor) { v ->
                update { it.copy(definitionColor = v) }
            }
        }
    }
}

@Composable
private fun DictionaryKjvUsageGroup(
    ds: DictionarySettings,
    update: ((DictionarySettings) -> DictionarySettings) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.dictionary_settings_kjv_usage)) {
        CustomizeRow(stringResource(Res.string.show), labelInsideControl = true) {
            ToggleControl(stringResource(Res.string.show), ds.showKjvUsage) { v ->
                update { it.copy(showKjvUsage = v) }
            }

        }
        CustomizeRow(stringResource(Res.string.font_size), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.font_size),
                value = ds.kjvUsageFontSize,
                onValueChange = { v -> update { it.copy(kjvUsageFontSize = v) } },
                range = FONT_SIZE_RANGE,
            )
        }
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(stringResource(Res.string.color), ds.kjvUsageColor) { v ->
                update { it.copy(kjvUsageColor = v) }
            }
        }
    }
}

@Composable
private fun DictionaryCardGroup(
    ds: DictionarySettings,
    update: ((DictionarySettings) -> DictionarySettings) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.customize_group_card)) {
        CustomizeRow(stringResource(Res.string.customize_card_background), labelInsideControl = true) {
            ColorControl(
                label = stringResource(Res.string.customize_card_background),
                color = ds.cardBackgroundColor,
                onColorChange = { v -> update { it.copy(cardBackgroundColor = v) } },
            )
        }
        CustomizeRow(stringResource(Res.string.customize_background_opacity), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.customize_background_opacity),
                value = (ds.cardBackgroundOpacity * PERCENT).toInt(),
                onValueChange = { v -> update { it.copy(cardBackgroundOpacity = v / PERCENT) } },
                range = 0..PERCENT.toInt(),
            )
        }
    }
}

