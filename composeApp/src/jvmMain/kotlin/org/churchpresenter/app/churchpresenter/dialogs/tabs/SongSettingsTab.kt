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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bilingual_layout
import churchpresenter.composeapp.generated.resources.bilingual_left_right
import churchpresenter.composeapp.generated.resources.bilingual_top_bottom
import churchpresenter.composeapp.generated.resources.bottom_left
import churchpresenter.composeapp.generated.resources.bottom_right
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.display_mode_label
import churchpresenter.composeapp.generated.resources.display_mode_one_line
import churchpresenter.composeapp.generated.resources.display_mode_one_verse
import churchpresenter.composeapp.generated.resources.every_page
import churchpresenter.composeapp.generated.resources.first_page
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.full_screen
import churchpresenter.composeapp.generated.resources.fullscreen_display
import churchpresenter.composeapp.generated.resources.look_ahead_fullscreen
import churchpresenter.composeapp.generated.resources.look_ahead_lower_third
import churchpresenter.composeapp.generated.resources.look_ahead_next_fullscreen
import churchpresenter.composeapp.generated.resources.look_ahead_next_lower_third
import churchpresenter.composeapp.generated.resources.lower_third_display
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.horizontal_alignment
import churchpresenter.composeapp.generated.resources.lyrics
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.number_before_title
import churchpresenter.composeapp.generated.resources.position_on_screen
import churchpresenter.composeapp.generated.resources.show_number
import churchpresenter.composeapp.generated.resources.show_title
import churchpresenter.composeapp.generated.resources.song_language_both
import churchpresenter.composeapp.generated.resources.song_language_primary
import churchpresenter.composeapp.generated.resources.song_language_secondary
import churchpresenter.composeapp.generated.resources.song_number
import churchpresenter.composeapp.generated.resources.title
import churchpresenter.composeapp.generated.resources.top_left
import churchpresenter.composeapp.generated.resources.top_right
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
import churchpresenter.composeapp.generated.resources.fade_in
import churchpresenter.composeapp.generated.resources.fade_out
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.screen
import churchpresenter.composeapp.generated.resources.text_margins
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.song_transition_settings
import churchpresenter.composeapp.generated.resources.transition_duration
import androidx.compose.material3.Slider
import androidx.compose.runtime.rememberCoroutineScope
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.composables.PositionButtons
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitFontSize
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource
import java.awt.Window
import javax.swing.SwingUtilities
import kotlinx.coroutines.launch


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    presenterManager: PresenterManager? = null
) {
    val availableFonts = remember { Utils.getAvailableSystemFonts() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Left Column
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .widthIn(min = 400.dp, max = 450.dp)
                    .heightIn(min = 600.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp)
            ) {
                LeftColumn(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    availableFonts = availableFonts
                )
            }

            // Right Column + Look Ahead Column stacked vertically
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .widthIn(min = 400.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp)
                ) {
                    RightColumn(settings, onSettingsChange, availableFonts, presenterManager)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp)
                ) {
                    LookAheadColumn(settings, onSettingsChange, availableFonts)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LeftColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>
) {

    // Store string resources to avoid calling stringResource in callbacks
    val noneStr = stringResource(Res.string.none)
    val firstPageStr = stringResource(Res.string.first_page)
    val everyPageStr = stringResource(Res.string.every_page)
    val topLeftStr = stringResource(Res.string.top_left)
    val topRightStr = stringResource(Res.string.top_right)
    val bottomLeftStr = stringResource(Res.string.bottom_left)
    val bottomRightStr = stringResource(Res.string.bottom_right)

    // Song Number Section
    SectionHeader(stringResource(Res.string.song_number))

    Spacer(modifier = Modifier.height(8.dp))

    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.songSettings.songNumberFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberFontSize = it)) } },
                    range = 8..150
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.songSettings.songNumberLowerThirdFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberLowerThirdFontSize = it)) } },
                    range = 8..150
                )
            }
        }
    }

    SettingRow(stringResource(Res.string.show_number)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                DropdownSettingsField(
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
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                DropdownSettingsField(
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

    // Show "Number before title" checkbox only when both vertical AND horizontal alignments match
    val sameFullscreen = settings.songSettings.songNumberPosition == settings.songSettings.titlePosition &&
            settings.songSettings.songNumberHorizontalAlignment == settings.songSettings.titleHorizontalAlignment
    val sameLowerThird = settings.songSettings.songNumberLowerThirdPosition == settings.songSettings.titleLowerThirdPosition &&
            settings.songSettings.songNumberLowerThirdHorizontalAlignment == settings.songSettings.titleLowerThirdHorizontalAlignment
    AnimatedVisibility(visible = sameFullscreen || sameLowerThird) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = settings.songSettings.songNumberBeforeTitle,
                onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberBeforeTitle = it)) } }
            )
            Text(stringResource(Res.string.number_before_title), style = MaterialTheme.typography.bodyMedium)
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Title Section
    SectionHeader(stringResource(Res.string.title))

    Spacer(modifier = Modifier.height(8.dp))

    SettingRow(stringResource(Res.string.show_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                DropdownSettingsField(
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
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                DropdownSettingsField(
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
    }

    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.songSettings.titleFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleFontSize = it)) } },
                    range = 8..150
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.songSettings.titleLowerThirdFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdFontSize = it)) } },
                    range = 8..150
                )
            }
        }
    }

    SettingRow(stringResource(Res.string.font_type)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                FontSettingsDropdown(
                    modifier = Modifier.width(150.dp),
                    value = settings.songSettings.titleFontType,
                    fonts = availableFonts,
                    onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleFontType = it)) } }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                FontSettingsDropdown(
                    modifier = Modifier.width(150.dp),
                    value = settings.songSettings.titleLowerThirdFontType,
                    fonts = availableFonts,
                    onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleLowerThirdFontType = it)) } }
                )
            }
        }
    }

    SettingRow(stringResource(Res.string.color)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Fullscreen
            Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(
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
                    shadowColor = settings.songSettings.shadowColor,
                    shadowSize = settings.songSettings.shadowSize,
                    shadowOpacity = settings.songSettings.shadowOpacity,
                    onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(shadowColor = it)) } },
                    onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(shadowSize = it)) } },
                    onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(shadowOpacity = it)) } }
                )
            }
            // Lower Third
            Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(
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
                    shadowColor = settings.songSettings.lowerThirdShadowColor,
                    shadowSize = settings.songSettings.lowerThirdShadowSize,
                    shadowOpacity = settings.songSettings.lowerThirdShadowOpacity,
                    onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdShadowColor = it)) } },
                    onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdShadowSize = it)) } },
                    onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdShadowOpacity = it)) } }
                )
            }
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

    Spacer(modifier = Modifier.height(20.dp))

    // Transition Section
    SectionHeader(stringResource(Res.string.song_transition_settings))

    Spacer(modifier = Modifier.height(8.dp))

    // Transition duration slider
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
        Slider(
            value = settings.songSettings.transitionDuration,
            onValueChange = { rawValue ->
                val snapped = (rawValue / 50f).toInt() * 50f
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(transitionDuration = snapped)) }
            },
            valueRange = 100f..2000f,
            steps = 37,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${settings.songSettings.transitionDuration.toInt()}$msSuffix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(60.dp)
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = settings.songSettings.fadeIn,
                onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fadeIn = it)) } },
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(Res.string.fade_in),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = settings.songSettings.fadeOut,
                onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fadeOut = it)) } },
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(Res.string.fade_out),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }

    // ── Bilingual Layout ──
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.bilingual_layout),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(160.dp)
        )
        val isSideBySide = settings.songSettings.bilingualLayout == Constants.BILINGUAL_SIDE_BY_SIDE
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isSideBySide,
                onCheckedChange = {
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE))
                    }
                },
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(Res.string.bilingual_left_right),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = !isSideBySide,
                onCheckedChange = {
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM))
                    }
                },
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(Res.string.bilingual_top_bottom),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // ── Text Margins ──
    SectionHeader(stringResource(Res.string.text_margins))
    Spacer(modifier = Modifier.height(8.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(Res.string.top), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                NumberSettingsTextField(
                    modifier = Modifier.width(100.dp),
                    initialText = settings.songSettings.marginTop,
                    onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginTop = value)) } },
                    range = 0..500
                )
            }
            // Left | Screen | Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(Res.string.left), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    NumberSettingsTextField(
                        modifier = Modifier.width(100.dp),
                        initialText = settings.songSettings.marginLeft,
                        onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginLeft = value)) } },
                        range = 0..500
                    )
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(Res.string.screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(Res.string.right), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    NumberSettingsTextField(
                        modifier = Modifier.width(100.dp),
                        initialText = settings.songSettings.marginRight,
                        onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginRight = value)) } },
                        range = 0..500
                    )
                }
            }
            // Bottom
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(Res.string.bottom), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                NumberSettingsTextField(
                    modifier = Modifier.width(100.dp),
                    initialText = settings.songSettings.marginBottom,
                    onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginBottom = value)) } },
                    range = 0..500
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RightColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>,
    presenterManager: PresenterManager? = null
) {
    // Store string resources to avoid calling stringResource in callbacks
    val topLeftStr = stringResource(Res.string.top_left)
    val topRightStr = stringResource(Res.string.top_right)
    val bottomLeftStr = stringResource(Res.string.bottom_left)
    val bottomRightStr = stringResource(Res.string.bottom_right)
    val noneStr = stringResource(Res.string.none)
    val firstPageStr = stringResource(Res.string.first_page)
    val everyPageStr = stringResource(Res.string.every_page)

    // Lyrics Section (shared settings)
    SectionHeader(stringResource(Res.string.lyrics))

    Spacer(modifier = Modifier.height(8.dp))

    val textMeasurer = rememberTextMeasurer()
    val isPresentingLyrics = if (presenterManager != null) {
        remember { derivedStateOf {
            presenterManager.presentingMode.value == Presenting.LYRICS &&
            presenterManager.lyricSection.value.lines.any { it.isNotBlank() }
        } }.value
    } else false
    val activeScreens = settings.projectionSettings.screenAssignments
    val hasFullscreenScreen = activeScreens.any { it.displayMode == Constants.DISPLAY_MODE_FULLSCREEN }
    val hasLowerThirdScreen = activeScreens.any { it.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD }

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
            }
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

    Spacer(modifier = Modifier.height(20.dp))

    // ── Fullscreen Display ──
    SectionHeader(stringResource(Res.string.fullscreen_display))
    Spacer(modifier = Modifier.height(8.dp))
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.display_mode_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(120.dp))
            val fsDisplayMode = settings.songSettings.fullscreenDisplayMode
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = fsDisplayMode == Constants.SONG_DISPLAY_MODE_VERSE, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE)) } }, modifier = Modifier.size(24.dp))
                Text(text = stringResource(Res.string.display_mode_one_verse), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = fsDisplayMode == Constants.SONG_DISPLAY_MODE_LINE, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_LINE)) } }, modifier = Modifier.size(24.dp))
                Text(text = stringResource(Res.string.display_mode_one_line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            Spacer(modifier = Modifier.width(120.dp))
            listOf(Constants.SONG_LANG_BOTH to stringResource(Res.string.song_language_both), Constants.SONG_LANG_PRIMARY to stringResource(Res.string.song_language_primary), Constants.SONG_LANG_SECONDARY to stringResource(Res.string.song_language_secondary)).forEach { (mode, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = settings.songSettings.fullscreenLanguageDisplay == mode, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fullscreenLanguageDisplay = mode)) } }, modifier = Modifier.size(24.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
                }
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
                tooltip = { Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.CursorPoint()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.songSettings.lyricsFontSizeAutoFit,
                        onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsFontSizeAutoFit = it)) } },
                        modifier = Modifier.size(24.dp)
                    )
                    Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (presenterManager != null) {
                TooltipArea(
                    tooltip = { Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_button_tooltip), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.CursorPoint()
                ) {
                    TextButton(
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
            modifier = Modifier.width(200.dp),
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

    SettingRow(stringResource(Res.string.color)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(
                    color = settings.songSettings.lyricsColor,
                    onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsColor = it)) } }
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
                    shadowColor = settings.songSettings.shadowColor,
                    shadowSize = settings.songSettings.shadowSize,
                    shadowOpacity = settings.songSettings.shadowOpacity,
                    onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(shadowColor = it)) } },
                    onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(shadowSize = it)) } },
                    onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(shadowOpacity = it)) } }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // ── Lower Third Display ──
    SectionHeader(stringResource(Res.string.lower_third_display))
    Spacer(modifier = Modifier.height(8.dp))
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.display_mode_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(120.dp))
            val ltDisplayMode = settings.songSettings.lowerThirdDisplayMode
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ltDisplayMode == Constants.SONG_DISPLAY_MODE_VERSE, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE)) } }, modifier = Modifier.size(24.dp))
                Text(text = stringResource(Res.string.display_mode_one_verse), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ltDisplayMode == Constants.SONG_DISPLAY_MODE_LINE, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_LINE)) } }, modifier = Modifier.size(24.dp))
                Text(text = stringResource(Res.string.display_mode_one_line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            Spacer(modifier = Modifier.width(120.dp))
            listOf(Constants.SONG_LANG_BOTH to stringResource(Res.string.song_language_both), Constants.SONG_LANG_PRIMARY to stringResource(Res.string.song_language_primary), Constants.SONG_LANG_SECONDARY to stringResource(Res.string.song_language_secondary)).forEach { (mode, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = settings.songSettings.lowerThirdLanguageDisplay == mode, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLanguageDisplay = mode)) } }, modifier = Modifier.size(24.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
                }
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
                tooltip = { Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.CursorPoint()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.songSettings.lyricsLowerThirdFontSizeAutoFit,
                        onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdFontSizeAutoFit = it)) } },
                        modifier = Modifier.size(24.dp)
                    )
                    Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (presenterManager != null) {
                TooltipArea(
                    tooltip = { Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_button_tooltip), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.CursorPoint()
                ) {
                TextButton(
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
            modifier = Modifier.width(200.dp),
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

    SettingRow(stringResource(Res.string.color)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(
                    color = settings.songSettings.lyricsLowerThirdColor,
                    onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdColor = it)) } }
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
                    shadowColor = settings.songSettings.lowerThirdShadowColor,
                    shadowSize = settings.songSettings.lowerThirdShadowSize,
                    shadowOpacity = settings.songSettings.lowerThirdShadowOpacity,
                    onColorChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdShadowColor = it)) } },
                    onSizeChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdShadowSize = it)) } },
                    onOpacityChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdShadowOpacity = it)) } }
                )
            }
        }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LookAheadColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>
) {
    // ── Look Ahead — Fullscreen ──
    SectionHeader(stringResource(Res.string.look_ahead_fullscreen))
    Spacer(modifier = Modifier.height(8.dp))
    val laFsDisplayMode = settings.songSettings.lookAheadDisplayMode
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(modifier = Modifier.width(160.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = laFsDisplayMode == Constants.SONG_DISPLAY_MODE_VERSE, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE)) } }, modifier = Modifier.size(24.dp))
                Text(text = stringResource(Res.string.display_mode_one_verse), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = laFsDisplayMode == Constants.SONG_DISPLAY_MODE_LINE, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_LINE)) } }, modifier = Modifier.size(24.dp))
                Text(text = stringResource(Res.string.display_mode_one_line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            Spacer(modifier = Modifier.width(160.dp))
            listOf(Constants.SONG_LANG_BOTH to stringResource(Res.string.song_language_both), Constants.SONG_LANG_PRIMARY to stringResource(Res.string.song_language_primary), Constants.SONG_LANG_SECONDARY to stringResource(Res.string.song_language_secondary)).forEach { (mode, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = settings.songSettings.lookAheadLanguageDisplay == mode, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadLanguageDisplay = mode)) } }, modifier = Modifier.size(24.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
                }
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
                tooltip = { Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.CursorPoint()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.songSettings.lookAheadFontSizeAutoFit,
                        onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadFontSizeAutoFit = it)) } },
                        modifier = Modifier.size(24.dp)
                    )
                    Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
    SettingRow(stringResource(Res.string.font_type)) {
        FontSettingsDropdown(
            modifier = Modifier.width(200.dp),
            value = settings.songSettings.lookAheadFontType,
            fonts = availableFonts,
            onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadFontType = it)) } }
        )
    }
    SettingRow(stringResource(Res.string.color)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ColorPickerField(
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
    }

    // ── Look Ahead Next Section — Fullscreen ──
    Spacer(modifier = Modifier.height(20.dp))
    SectionHeader(stringResource(Res.string.look_ahead_next_fullscreen))
    Spacer(modifier = Modifier.height(8.dp))
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberSettingsTextField(
                initialText = settings.songSettings.lookAheadNextFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextFontSize = it)) } },
                range = 8..150
            )
            TooltipArea(
                tooltip = { Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.CursorPoint()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.songSettings.lookAheadNextFontSizeAutoFit,
                        onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextFontSizeAutoFit = it)) } },
                        modifier = Modifier.size(24.dp)
                    )
                    Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
    SettingRow(stringResource(Res.string.font_type)) {
        FontSettingsDropdown(
            modifier = Modifier.width(200.dp),
            value = settings.songSettings.lookAheadNextFontType,
            fonts = availableFonts,
            onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lookAheadNextFontType = it)) } }
        )
    }
    SettingRow(stringResource(Res.string.color)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ColorPickerField(
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
    }

    // ── Look Ahead — Lower Third ──
    Spacer(modifier = Modifier.height(20.dp))
    SectionHeader(stringResource(Res.string.look_ahead_lower_third))
    Spacer(modifier = Modifier.height(8.dp))
    val laLtDisplayMode = settings.songSettings.lowerThirdLookAheadDisplayMode
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(modifier = Modifier.width(160.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = laLtDisplayMode == Constants.SONG_DISPLAY_MODE_VERSE, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE)) } }, modifier = Modifier.size(24.dp))
                Text(text = stringResource(Res.string.display_mode_one_verse), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = laLtDisplayMode == Constants.SONG_DISPLAY_MODE_LINE, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_LINE)) } }, modifier = Modifier.size(24.dp))
                Text(text = stringResource(Res.string.display_mode_one_line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            Spacer(modifier = Modifier.width(160.dp))
            listOf(Constants.SONG_LANG_BOTH to stringResource(Res.string.song_language_both), Constants.SONG_LANG_PRIMARY to stringResource(Res.string.song_language_primary), Constants.SONG_LANG_SECONDARY to stringResource(Res.string.song_language_secondary)).forEach { (mode, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = settings.songSettings.lowerThirdLookAheadLanguageDisplay == mode, onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadLanguageDisplay = mode)) } }, modifier = Modifier.size(24.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
                }
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
                tooltip = { Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.CursorPoint()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.songSettings.lowerThirdLookAheadFontSizeAutoFit,
                        onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadFontSizeAutoFit = it)) } },
                        modifier = Modifier.size(24.dp)
                    )
                    Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
    SettingRow(stringResource(Res.string.font_type)) {
        FontSettingsDropdown(
            modifier = Modifier.width(200.dp),
            value = settings.songSettings.lowerThirdLookAheadFontType,
            fonts = availableFonts,
            onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadFontType = it)) } }
        )
    }
    SettingRow(stringResource(Res.string.color)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ColorPickerField(
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
    }

    // ── Look Ahead Next Section — Lower Third ──
    Spacer(modifier = Modifier.height(20.dp))
    SectionHeader(stringResource(Res.string.look_ahead_next_lower_third))
    Spacer(modifier = Modifier.height(8.dp))
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberSettingsTextField(
                initialText = settings.songSettings.lowerThirdLookAheadNextFontSize,
                onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextFontSize = it)) } },
                range = 8..150
            )
            TooltipArea(
                tooltip = { Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.auto_fit_checkbox_tooltip), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.CursorPoint()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.songSettings.lowerThirdLookAheadNextFontSizeAutoFit,
                        onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextFontSizeAutoFit = it)) } },
                        modifier = Modifier.size(24.dp)
                    )
                    Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
    SettingRow(stringResource(Res.string.font_type)) {
        FontSettingsDropdown(
            modifier = Modifier.width(200.dp),
            value = settings.songSettings.lowerThirdLookAheadNextFontType,
            fonts = availableFonts,
            onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lowerThirdLookAheadNextFontType = it)) } }
        )
    }
    SettingRow(stringResource(Res.string.color)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ColorPickerField(
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
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    width: Dp = 120.dp,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(width)
        )
        content()
    }
}


