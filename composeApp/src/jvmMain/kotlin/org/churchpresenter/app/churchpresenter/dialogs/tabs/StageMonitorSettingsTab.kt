package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_color
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.style
import churchpresenter.composeapp.generated.resources.horizontal_alignment
import churchpresenter.composeapp.generated.resources.vertical_alignment
import churchpresenter.composeapp.generated.resources.stage_monitor_clock_24h
import churchpresenter.composeapp.generated.resources.stage_monitor_clock_show_seconds
import churchpresenter.composeapp.generated.resources.stage_monitor_label_style_section
import churchpresenter.composeapp.generated.resources.stage_monitor_notes_from_presentation
import churchpresenter.composeapp.generated.resources.stage_monitor_quadrant_clock
import churchpresenter.composeapp.generated.resources.stage_monitor_quadrant_current
import churchpresenter.composeapp.generated.resources.stage_monitor_quadrant_next
import churchpresenter.composeapp.generated.resources.stage_monitor_quadrant_notes
import churchpresenter.composeapp.generated.resources.stage_monitor_quadrant_timer
import churchpresenter.composeapp.generated.resources.stage_monitor_show_clock
import churchpresenter.composeapp.generated.resources.stage_monitor_show_label
import churchpresenter.composeapp.generated.resources.shadow_settings
import churchpresenter.composeapp.generated.resources.stage_monitor_show_timer
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingRow
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorSettings
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment

@Composable
fun StageMonitorSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val availableFonts = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }

    val sm = settings.stageMonitorSettings
    fun update(block: StageMonitorSettings.() -> StageMonitorSettings) {
        onSettingsChange { s -> s.copy(stageMonitorSettings = s.stageMonitorSettings.block()) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Left column: Current + Next ──────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f).widthIn(min = 320.dp, max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            SettingsSection(title = stringResource(Res.string.stage_monitor_quadrant_current)) {
                QuadrantFontSettings(
                    fontType = sm.currentFontType, fontSize = sm.currentFontSize,
                    color = sm.currentColor, bgColor = sm.currentBgColor,
                    bold = sm.currentBold, italic = sm.currentItalic,
                    underline = sm.currentUnderline, shadow = sm.currentShadow,
                    shadowColor = sm.currentShadowColor, shadowSize = sm.currentShadowSize, shadowOpacity = sm.currentShadowOpacity,
                    availableFonts = availableFonts,
                    onFontTypeChange = { update { copy(currentFontType = it) } },
                    onFontSizeChange = { update { copy(currentFontSize = it) } },
                    onColorChange = { update { copy(currentColor = it) } },
                    onBgColorChange = { update { copy(currentBgColor = it) } },
                    onBoldChange = { update { copy(currentBold = it) } },
                    onItalicChange = { update { copy(currentItalic = it) } },
                    onUnderlineChange = { update { copy(currentUnderline = it) } },
                    onShadowChange = { update { copy(currentShadow = it) } },
                    onShadowColorChange = { update { copy(currentShadowColor = it) } },
                    onShadowSizeChange = { update { copy(currentShadowSize = it) } },
                    onShadowOpacityChange = { update { copy(currentShadowOpacity = it) } }
                )
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.vertical_alignment), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    VerticalAlignmentButtons(selectedAlignment = sm.currentVerticalAlignment, onAlignmentChange = { update { copy(currentVerticalAlignment = it) } }, topValue = Constants.TOP, middleValue = Constants.MIDDLE, bottomValue = Constants.BOTTOM)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.horizontal_alignment), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    HorizontalAlignmentButtons(selectedAlignment = sm.currentHorizontalAlignment, onAlignmentChange = { update { copy(currentHorizontalAlignment = it) } }, leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT)
                }

            }

            SettingsSection(title = stringResource(Res.string.stage_monitor_quadrant_next)) {
                QuadrantFontSettings(
                    fontType = sm.nextFontType, fontSize = sm.nextFontSize,
                    color = sm.nextColor, bgColor = sm.nextBgColor,
                    bold = sm.nextBold, italic = sm.nextItalic,
                    underline = sm.nextUnderline, shadow = sm.nextShadow,
                    shadowColor = sm.nextShadowColor, shadowSize = sm.nextShadowSize, shadowOpacity = sm.nextShadowOpacity,
                    availableFonts = availableFonts,
                    onFontTypeChange = { update { copy(nextFontType = it) } },
                    onFontSizeChange = { update { copy(nextFontSize = it) } },
                    onColorChange = { update { copy(nextColor = it) } },
                    onBgColorChange = { update { copy(nextBgColor = it) } },
                    onBoldChange = { update { copy(nextBold = it) } },
                    onItalicChange = { update { copy(nextItalic = it) } },
                    onUnderlineChange = { update { copy(nextUnderline = it) } },
                    onShadowChange = { update { copy(nextShadow = it) } },
                    onShadowColorChange = { update { copy(nextShadowColor = it) } },
                    onShadowSizeChange = { update { copy(nextShadowSize = it) } },
                    onShadowOpacityChange = { update { copy(nextShadowOpacity = it) } }
                )
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.vertical_alignment), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    VerticalAlignmentButtons(selectedAlignment = sm.nextVerticalAlignment, onAlignmentChange = { update { copy(nextVerticalAlignment = it) } }, topValue = Constants.TOP, middleValue = Constants.MIDDLE, bottomValue = Constants.BOTTOM)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.horizontal_alignment), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    HorizontalAlignmentButtons(selectedAlignment = sm.nextHorizontalAlignment, onAlignmentChange = { update { copy(nextHorizontalAlignment = it) } }, leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT)
                }
            }
                // Song/Bible Label Style card
                SettingsSection(title = stringResource(Res.string.stage_monitor_label_style_section)) {
                    SettingRow(stringResource(Res.string.stage_monitor_show_label)) {
                        Switch(checked = sm.showSongBibleLabel, onCheckedChange = { update { copy(showSongBibleLabel = it) } })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FontSettingsDropdown(
                            label = stringResource(Res.string.font_type).removeSuffix(":"),
                            value = sm.labelFontType,
                            fonts = availableFonts,
                            onValueChange = { update { copy(labelFontType = it) } }
                        )
                        NumberSettingsTextField(
                            label = stringResource(Res.string.font_size).removeSuffix(":"),
                            initialText = sm.labelFontSize,
                            onValueChange = { update { copy(labelFontSize = it) } },
                            range = 8..100
                        )
                        ColorPickerField(
                            color = sm.labelColor,
                            onColorChange = { update { copy(labelColor = it) } },
                            label = stringResource(Res.string.color).removeSuffix(":"),
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                    }
                    SettingRow(stringResource(Res.string.style)) {
                        TextStyleButtons(
                            bold = sm.labelBold,
                            italic = sm.labelItalic,
                            underline = false,
                            shadow = false,
                            onBoldChange = { update { copy(labelBold = it) } },
                            onItalicChange = { update { copy(labelItalic = it) } },
                            onUnderlineChange = {},
                            onShadowChange = {}
                        )
                    }
                }
            } // end left column

            // ── Right column: Timer + Clock + Notes ──────────────────────────────
            Column(
                modifier = Modifier.weight(1f).widthIn(min = 320.dp, max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Timer card
                SettingsSection(title = stringResource(Res.string.stage_monitor_quadrant_timer)) {
                    SettingRow(stringResource(Res.string.stage_monitor_show_timer)) {
                        Switch(checked = sm.showTimer, onCheckedChange = { update { copy(showTimer = it) } })
                    }
                    QuadrantFontSettings(
                        fontType = sm.timerFontType, fontSize = sm.timerFontSize,
                        color = sm.timerColor, bgColor = sm.timerBgColor,
                        bold = sm.timerBold, italic = sm.timerItalic,
                        underline = sm.timerUnderline, shadow = sm.timerShadow,
                        shadowColor = sm.timerShadowColor, shadowSize = sm.timerShadowSize, shadowOpacity = sm.timerShadowOpacity,
                        availableFonts = availableFonts,
                        onFontTypeChange = { update { copy(timerFontType = it) } },
                        onFontSizeChange = { update { copy(timerFontSize = it) } },
                        onColorChange = { update { copy(timerColor = it) } },
                        onBgColorChange = { update { copy(timerBgColor = it) } },
                        onBoldChange = { update { copy(timerBold = it) } },
                        onItalicChange = { update { copy(timerItalic = it) } },
                        onUnderlineChange = { update { copy(timerUnderline = it) } },
                        onShadowChange = { update { copy(timerShadow = it) } },
                        onShadowColorChange = { update { copy(timerShadowColor = it) } },
                        onShadowSizeChange = { update { copy(timerShadowSize = it) } },
                        onShadowOpacityChange = { update { copy(timerShadowOpacity = it) } }
                    )
                }

                // Clock card
                SettingsSection(title = stringResource(Res.string.stage_monitor_quadrant_clock)) {
                    SettingRow(stringResource(Res.string.stage_monitor_show_clock)) {
                        Switch(checked = sm.showClock, onCheckedChange = { update { copy(showClock = it) } })
                    }
                    QuadrantFontSettings(
                        fontType = sm.clockFontType, fontSize = sm.clockFontSize,
                        color = sm.clockColor, bgColor = sm.clockBgColor,
                        bold = sm.clockBold, italic = sm.clockItalic,
                        underline = sm.clockUnderline, shadow = sm.clockShadow,
                        shadowColor = sm.clockShadowColor, shadowSize = sm.clockShadowSize, shadowOpacity = sm.clockShadowOpacity,
                        availableFonts = availableFonts,
                        onFontTypeChange = { update { copy(clockFontType = it) } },
                        onFontSizeChange = { update { copy(clockFontSize = it) } },
                        onColorChange = { update { copy(clockColor = it) } },
                        onBgColorChange = { update { copy(clockBgColor = it) } },
                        onBoldChange = { update { copy(clockBold = it) } },
                        onItalicChange = { update { copy(clockItalic = it) } },
                        onUnderlineChange = { update { copy(clockUnderline = it) } },
                        onShadowChange = { update { copy(clockShadow = it) } },
                        onShadowColorChange = { update { copy(clockShadowColor = it) } },
                        onShadowSizeChange = { update { copy(clockShadowSize = it) } },
                        onShadowOpacityChange = { update { copy(clockShadowOpacity = it) } }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = sm.clockShowSeconds,
                                onCheckedChange = { update { copy(clockShowSeconds = it) } },
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = stringResource(Res.string.stage_monitor_clock_show_seconds),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = sm.clockFormat24h,
                                onCheckedChange = { update { copy(clockFormat24h = it) } },
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = stringResource(Res.string.stage_monitor_clock_24h),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }

                // Presenter Notes card
                SettingsSection(title = stringResource(Res.string.stage_monitor_quadrant_notes)) {
                    Text(
                        text = stringResource(Res.string.stage_monitor_notes_from_presentation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    QuadrantFontSettings(
                        fontType = sm.notesFontType, fontSize = sm.notesFontSize,
                        color = sm.notesColor, bgColor = sm.notesBgColor,
                        bold = sm.notesBold, italic = sm.notesItalic,
                        underline = sm.notesUnderline, shadow = sm.notesShadow,
                        shadowColor = sm.notesShadowColor, shadowSize = sm.notesShadowSize, shadowOpacity = sm.notesShadowOpacity,
                        availableFonts = availableFonts,
                        onFontTypeChange = { update { copy(notesFontType = it) } },
                        onFontSizeChange = { update { copy(notesFontSize = it) } },
                        onColorChange = { update { copy(notesColor = it) } },
                        onBgColorChange = { update { copy(notesBgColor = it) } },
                        onBoldChange = { update { copy(notesBold = it) } },
                        onItalicChange = { update { copy(notesItalic = it) } },
                        onUnderlineChange = { update { copy(notesUnderline = it) } },
                        onShadowChange = { update { copy(notesShadow = it) } },
                        onShadowColorChange = { update { copy(notesShadowColor = it) } },
                        onShadowSizeChange = { update { copy(notesShadowSize = it) } },
                        onShadowOpacityChange = { update { copy(notesShadowOpacity = it) } }
                    )
                }

            }
        }
    }
}

@Composable
private fun QuadrantFontSettings(
    fontType: String, fontSize: Int,
    color: String, bgColor: String,
    bold: Boolean, italic: Boolean, underline: Boolean, shadow: Boolean,
    shadowColor: String, shadowSize: Int, shadowOpacity: Int,
    availableFonts: List<String>,
    onFontTypeChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onColorChange: (String) -> Unit,
    onBgColorChange: (String) -> Unit,
    onBoldChange: (Boolean) -> Unit,
    onItalicChange: (Boolean) -> Unit,
    onUnderlineChange: (Boolean) -> Unit,
    onShadowChange: (Boolean) -> Unit,
    onShadowColorChange: (String) -> Unit,
    onShadowSizeChange: (Int) -> Unit,
    onShadowOpacityChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FontSettingsDropdown(
            label = stringResource(Res.string.font_type).removeSuffix(":"),
            value = fontType,
            fonts = availableFonts,
            onValueChange = onFontTypeChange
        )
        NumberSettingsTextField(
            label = stringResource(Res.string.font_size).removeSuffix(":"),
            initialText = fontSize,
            onValueChange = onFontSizeChange,
            range = 8..300
        )
        ColorPickerField(
            color = color,
            onColorChange = onColorChange,
            label = stringResource(Res.string.color).removeSuffix(":"),
            modifier = Modifier.widthIn(max = 150.dp)
        )
        ColorPickerField(
            color = bgColor,
            onColorChange = onBgColorChange,
            label = stringResource(Res.string.background_color).removeSuffix(":"),
            modifier = Modifier.widthIn(max = 150.dp)
        )
    }
    SettingRow(stringResource(Res.string.style)) {
        TextStyleButtons(
            bold = bold, italic = italic, underline = underline, shadow = shadow,
            onBoldChange = onBoldChange, onItalicChange = onItalicChange,
            onUnderlineChange = onUnderlineChange, onShadowChange = onShadowChange
        )
    }
    if (shadow) {
        SettingRow(stringResource(Res.string.shadow_settings)) {
            ShadowDetailRow(
                shadowColor = shadowColor, shadowSize = shadowSize, shadowOpacity = shadowOpacity,
                onColorChange = onShadowColorChange, onSizeChange = onShadowSizeChange, onOpacityChange = onShadowOpacityChange
            )
        }
    }
}

