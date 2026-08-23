package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.bilingual_layout
import org.churchpresenter.resources.generated.resources.bilingual_left_right
import org.churchpresenter.resources.generated.resources.bilingual_top_bottom
import org.churchpresenter.resources.generated.resources.color
import org.churchpresenter.resources.generated.resources.every_page
import org.churchpresenter.resources.generated.resources.first_page
import org.churchpresenter.resources.generated.resources.font_size
import org.churchpresenter.resources.generated.resources.font_type
import org.churchpresenter.resources.generated.resources.full_screen
import org.churchpresenter.resources.generated.resources.lower_third_size
import org.churchpresenter.resources.generated.resources.horizontal_alignment
import org.churchpresenter.resources.generated.resources.none
import org.churchpresenter.resources.generated.resources.number_before_title
import org.churchpresenter.resources.generated.resources.show_number
import org.churchpresenter.resources.generated.resources.show_title
import org.churchpresenter.resources.generated.resources.song_number
import org.churchpresenter.resources.generated.resources.title
import org.churchpresenter.resources.generated.resources.vertical_alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import org.churchpresenter.resources.generated.resources.animation_crossfade
import org.churchpresenter.resources.generated.resources.fade_in
import org.churchpresenter.resources.generated.resources.fade_out
import org.churchpresenter.resources.generated.resources.bottom
import org.churchpresenter.resources.generated.resources.left
import org.churchpresenter.resources.generated.resources.right
import org.churchpresenter.resources.generated.resources.screen
import org.churchpresenter.resources.generated.resources.end_of_song_spacing
import org.churchpresenter.resources.generated.resources.text_margins
import org.churchpresenter.resources.generated.resources.top
import org.churchpresenter.resources.generated.resources.milliseconds_suffix
import org.churchpresenter.resources.generated.resources.song_transition_settings
import org.churchpresenter.resources.generated.resources.transition_duration
import org.churchpresenter.ui.ColorPickerField
import org.churchpresenter.ui.DropdownSettingsField
import org.churchpresenter.ui.FontSettingsDropdown
import org.churchpresenter.ui.HorizontalAlignmentButtons
import org.churchpresenter.ui.NumberSettingsTextField
import org.churchpresenter.ui.PositionButtons
import org.churchpresenter.ui.SettingRow
import org.churchpresenter.ui.SettingsSection
import org.churchpresenter.ui.ShadowDetailRow
import org.churchpresenter.ui.SlimSlider
import org.churchpresenter.ui.TextStyleButtons
import org.churchpresenter.ui.TvScreenBox
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.ui.LabeledCheckbox

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun LeftColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>
) {

    val noneStr = stringResource(Res.string.none)
    val firstPageStr = stringResource(Res.string.first_page)
    val everyPageStr = stringResource(Res.string.every_page)

    SettingsSection(title = stringResource(Res.string.song_number)) {
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberSettingsTextField(
                label = stringResource(Res.string.full_screen),
                initialText = settings.songSettings.songNumberFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberFontSize = it)) } },
                range = 8..150
            )
            NumberSettingsTextField(
                label = stringResource(Res.string.lower_third_size),
                initialText = settings.songSettings.songNumberLowerThirdFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberLowerThirdFontSize = it)) } },
                range = 8..150
            )
        }
    }

    SettingRow(stringResource(Res.string.show_number)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            DropdownSettingsField(
                label = stringResource(Res.string.full_screen),
                value = when (settings.songSettings.showNumber) {
                    Constants.NONE -> noneStr
                    Constants.FIRST_PAGE -> firstPageStr
                    Constants.EVERY_PAGE -> everyPageStr
                    else -> firstPageStr
                },
                options = listOf(noneStr, firstPageStr, everyPageStr),
                onValueChange = { displayValue ->
                    val storedValue = when (displayValue) {
                        noneStr -> Constants.NONE
                        firstPageStr -> Constants.FIRST_PAGE
                        everyPageStr -> Constants.EVERY_PAGE
                        else -> Constants.FIRST_PAGE
                    }
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(showNumber = storedValue)) }
                }
            )
            DropdownSettingsField(
                label = stringResource(Res.string.lower_third_size),
                value = when (settings.songSettings.showNumberLowerThird) {
                    Constants.NONE -> noneStr
                    Constants.FIRST_PAGE -> firstPageStr
                    Constants.EVERY_PAGE -> everyPageStr
                    else -> firstPageStr
                },
                options = listOf(noneStr, firstPageStr, everyPageStr),
                onValueChange = { displayValue ->
                    val storedValue = when (displayValue) {
                        noneStr -> Constants.NONE
                        firstPageStr -> Constants.FIRST_PAGE
                        everyPageStr -> Constants.EVERY_PAGE
                        else -> Constants.FIRST_PAGE
                    }
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(showNumberLowerThird = storedValue)) }
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(5.dp))

    SettingRow(stringResource(Res.string.vertical_alignment), width = 200.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                PositionButtons(
                    selectedPosition = settings.songSettings.songNumberPosition,
                    onPositionChange = { storedValue -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberPosition = storedValue)) } },
                    aboveValue = Constants.ABOVE_VERSE,
                    belowValue = Constants.BELOW_VERSE
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                PositionButtons(
                    selectedPosition = settings.songSettings.songNumberLowerThirdPosition,
                    onPositionChange = { storedValue -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberLowerThirdPosition = storedValue)) } },
                    aboveValue = Constants.ABOVE_VERSE,
                    belowValue = Constants.BELOW_VERSE
                )
            }
        }
    }

    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.songSettings.songNumberHorizontalAlignment,
                    onAlignmentChange = { storedValue -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberHorizontalAlignment = storedValue)) } },
                    leftValue = Constants.LEFT,
                    centerValue = Constants.CENTER,
                    rightValue = Constants.RIGHT
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.songSettings.songNumberLowerThirdHorizontalAlignment,
                    onAlignmentChange = { storedValue -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberLowerThirdHorizontalAlignment = storedValue)) } },
                    leftValue = Constants.LEFT,
                    centerValue = Constants.CENTER,
                    rightValue = Constants.RIGHT
                )
            }
        }
    }

    val sameFullscreen = settings.songSettings.songNumberPosition == settings.songSettings.titlePosition &&
            settings.songSettings.songNumberHorizontalAlignment == settings.songSettings.titleHorizontalAlignment
    val sameLowerThird = settings.songSettings.songNumberLowerThirdPosition == settings.songSettings.titleLowerThirdPosition &&
            settings.songSettings.songNumberLowerThirdHorizontalAlignment == settings.songSettings.titleLowerThirdHorizontalAlignment
    AnimatedVisibility(visible = sameFullscreen || sameLowerThird) {
        LabeledCheckbox(
            checked = settings.songSettings.songNumberBeforeTitle,
            onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberBeforeTitle = it)) } },
            label = stringResource(Res.string.number_before_title),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("song_songNumberBeforeTitle"),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    }

    SettingsSection(title = stringResource(Res.string.title)) {
    SettingRow(stringResource(Res.string.show_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            DropdownSettingsField(
                label = stringResource(Res.string.full_screen),
                value = when (settings.songSettings.titleDisplay) {
                    Constants.NONE -> noneStr
                    Constants.FIRST_PAGE -> firstPageStr
                    Constants.EVERY_PAGE -> everyPageStr
                    else -> firstPageStr
                },
                options = listOf(noneStr, firstPageStr, everyPageStr),
                onValueChange = { displayValue ->
                    val storedValue = when (displayValue) {
                        noneStr -> Constants.NONE
                        firstPageStr -> Constants.FIRST_PAGE
                        everyPageStr -> Constants.EVERY_PAGE
                        else -> Constants.FIRST_PAGE
                    }
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleDisplay = storedValue)) }
                }
            )
            DropdownSettingsField(
                label = stringResource(Res.string.lower_third_size),
                value = when (settings.songSettings.titleLowerThirdDisplay) {
                    Constants.NONE -> noneStr
                    Constants.FIRST_PAGE -> firstPageStr
                    Constants.EVERY_PAGE -> everyPageStr
                    else -> firstPageStr
                },
                options = listOf(noneStr, firstPageStr, everyPageStr),
                onValueChange = { displayValue ->
                    val storedValue = when (displayValue) {
                        noneStr -> Constants.NONE
                        firstPageStr -> Constants.FIRST_PAGE
                        everyPageStr -> Constants.EVERY_PAGE
                        else -> Constants.FIRST_PAGE
                    }
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdDisplay = storedValue)) }
                }
            )
        }
    }

    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberSettingsTextField(
                label = stringResource(Res.string.full_screen),
                initialText = settings.songSettings.titleFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleFontSize = it)) } },
                range = 8..150
            )
            NumberSettingsTextField(
                label = stringResource(Res.string.lower_third_size),
                initialText = settings.songSettings.titleLowerThirdFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdFontSize = it)) } },
                range = 8..150
            )
        }
    }

    SettingRow(stringResource(Res.string.font_type)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontSettingsDropdown(
                label = stringResource(Res.string.full_screen),
                value = settings.songSettings.titleFontType,
                fonts = availableFonts,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleFontType = it)) } }
            )
            FontSettingsDropdown(
                label = stringResource(Res.string.lower_third_size),
                value = settings.songSettings.titleLowerThirdFontType,
                fonts = availableFonts,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdFontType = it)) } }
            )
        }
    }

    Spacer(Modifier.height(4.dp))

    Column(modifier = Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.color),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPickerField(
                label = stringResource(Res.string.full_screen),
                modifier = Modifier.width(120.dp),
                color = settings.songSettings.titleColor,
                onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleColor = it)) } }
            )
            TextStyleButtons(
                bold = settings.songSettings.titleBold,
                italic = settings.songSettings.titleItalic,
                underline = settings.songSettings.titleUnderline,
                shadow = settings.songSettings.titleShadow,
                onBoldChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleBold = it)) } },
                onItalicChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleItalic = it)) } },
                onUnderlineChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleUnderline = it)) } },
                onShadowChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleShadow = it)) } }
            )
        }
        AnimatedVisibility(visible = settings.songSettings.titleShadow) {
            ShadowDetailRow(
                shadowColor = settings.songSettings.titleShadowColor,
                shadowSize = settings.songSettings.titleShadowSize,
                shadowOpacity = settings.songSettings.titleShadowOpacity,
                onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleShadowColor = it)) } },
                onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleShadowSize = it)) } },
                onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleShadowOpacity = it)) } }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPickerField(
                label = stringResource(Res.string.lower_third_size),
                modifier = Modifier.width(120.dp),
                color = settings.songSettings.titleLowerThirdColor,
                onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdColor = it)) } }
            )
            TextStyleButtons(
                bold = settings.songSettings.titleLowerThirdBold,
                italic = settings.songSettings.titleLowerThirdItalic,
                underline = settings.songSettings.titleLowerThirdUnderline,
                shadow = settings.songSettings.titleLowerThirdShadow,
                onBoldChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdBold = it)) } },
                onItalicChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdItalic = it)) } },
                onUnderlineChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdUnderline = it)) } },
                onShadowChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdShadow = it)) } }
            )
        }
        AnimatedVisibility(visible = settings.songSettings.titleLowerThirdShadow) {
            ShadowDetailRow(
                shadowColor = settings.songSettings.titleLowerThirdShadowColor,
                shadowSize = settings.songSettings.titleLowerThirdShadowSize,
                shadowOpacity = settings.songSettings.titleLowerThirdShadowOpacity,
                onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdShadowColor = it)) } },
                onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdShadowSize = it)) } },
                onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdShadowOpacity = it)) } }
            )
        }
    }

    SettingRow(stringResource(Res.string.vertical_alignment), width = 200.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                PositionButtons(
                    selectedPosition = settings.songSettings.titlePosition,
                    onPositionChange = { storedValue -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titlePosition = storedValue)) } },
                    aboveValue = Constants.ABOVE_VERSE,
                    belowValue = Constants.BELOW_VERSE
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                PositionButtons(
                    selectedPosition = settings.songSettings.titleLowerThirdPosition,
                    onPositionChange = { storedValue -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdPosition = storedValue)) } },
                    aboveValue = Constants.ABOVE_VERSE,
                    belowValue = Constants.BELOW_VERSE
                )
            }
        }
    }

    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.songSettings.titleHorizontalAlignment,
                    onAlignmentChange = { storedValue -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleHorizontalAlignment = storedValue)) } },
                    leftValue = Constants.LEFT,
                    centerValue = Constants.CENTER,
                    rightValue = Constants.RIGHT
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.songSettings.titleLowerThirdHorizontalAlignment,
                    onAlignmentChange = { storedValue -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdHorizontalAlignment = storedValue)) } },
                    leftValue = Constants.LEFT,
                    centerValue = Constants.CENTER,
                    rightValue = Constants.RIGHT
                )
            }
        }
    }
    }

    SettingsSection(title = stringResource(Res.string.song_transition_settings)) {
    val durationLabel = stringResource(Res.string.transition_duration)
    val msSuffix = stringResource(Res.string.milliseconds_suffix)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = durationLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(120.dp)
        )
        SlimSlider(
            value = settings.songSettings.transitionDuration,
            onValueChange = { rawValue ->
                val snapped = (rawValue / 50f).toInt() * 50f
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(transitionDuration = snapped)) }
            },
            valueRange = 100f..2000f,
            modifier = Modifier.weight(1f),
            trailingLabel = "${settings.songSettings.transitionDuration.toInt()}$msSuffix"
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LabeledCheckbox(
            checked = settings.songSettings.fadeIn,
            onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fadeIn = it)) } },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.fade_in),
            modifier = Modifier.testTag("song_fadeIn"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LabeledCheckbox(
            checked = settings.songSettings.fadeOut,
            onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fadeOut = it)) } },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.fade_out),
            modifier = Modifier.testTag("song_fadeOut"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LabeledCheckbox(
            checked = settings.songSettings.crossfade,
            onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(crossfade = it)) } },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.animation_crossfade),
            modifier = Modifier.testTag("song_crossfade"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.bilingual_layout),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        val isSideBySide = settings.songSettings.bilingualLayout == Constants.BILINGUAL_SIDE_BY_SIDE
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            SegmentedButton(
                selected = isSideBySide,
                onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE)) } },
                shape = segmentedItemShape(index = 0, count = 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                icon = {}
            ) {
                Text(text = stringResource(Res.string.bilingual_left_right), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            SegmentedButton(
                selected = !isSideBySide,
                onClick = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM)) } },
                shape = segmentedItemShape(index = 1, count = 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                icon = {}
            ) {
                Text(text = stringResource(Res.string.bilingual_top_bottom), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SettingRow(stringResource(Res.string.end_of_song_spacing)) {
        NumberSettingsTextField(
            initialText = settings.songSettings.endOfSongIndicatorSpacing,
            range = 0..10,
            onValueChange = { value ->
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(endOfSongIndicatorSpacing = value))
                }
            }
        )
    }

    }

    SettingsSection(title = stringResource(Res.string.text_margins)) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        NumberSettingsTextField(
            modifier = Modifier.width(100.dp),
            label = stringResource(Res.string.top),
            initialText = settings.songSettings.marginTop,
            onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginTop = value)) } },
            range = 0..500
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumberSettingsTextField(
                modifier = Modifier.width(100.dp),
                label = stringResource(Res.string.left),
                initialText = settings.songSettings.marginLeft,
                onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginLeft = value)) } },
                range = 0..500
            )
            TvScreenBox(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(180.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(Res.string.screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            NumberSettingsTextField(
                modifier = Modifier.width(100.dp),
                label = stringResource(Res.string.right),
                initialText = settings.songSettings.marginRight,
                onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginRight = value)) } },
                range = 0..500
            )
        }

        NumberSettingsTextField(
            modifier = Modifier.width(100.dp),
            label = stringResource(Res.string.bottom),
            initialText = settings.songSettings.marginBottom,
            onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginBottom = value)) } },
            range = 0..500
        )
    }
    }
}
