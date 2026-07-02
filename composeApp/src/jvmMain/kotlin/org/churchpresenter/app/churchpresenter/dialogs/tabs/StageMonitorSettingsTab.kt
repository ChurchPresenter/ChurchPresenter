package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_color
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.content_announcements
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.media
import churchpresenter.composeapp.generated.resources.obs_mode_lower_third
import churchpresenter.composeapp.generated.resources.pictures
import churchpresenter.composeapp.generated.resources.presentation
import churchpresenter.composeapp.generated.resources.songs
import churchpresenter.composeapp.generated.resources.horizontal_alignment
import churchpresenter.composeapp.generated.resources.vertical_alignment
import churchpresenter.composeapp.generated.resources.stage_monitor_content_section
import churchpresenter.composeapp.generated.resources.stage_monitor_quadrant_clock
import churchpresenter.composeapp.generated.resources.stage_monitor_quadrant_next
import churchpresenter.composeapp.generated.resources.stage_monitor_quadrant_notes
import churchpresenter.composeapp.generated.resources.shadow_settings
import churchpresenter.composeapp.generated.resources.stage_monitor_layout_section
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_bottom_left
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_bottom_center
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_bottom_right
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_full_screen
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_none
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_top_left
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_top_right
import churchpresenter.composeapp.generated.resources.tab_canvas
import churchpresenter.composeapp.generated.resources.tab_dictionary
import churchpresenter.composeapp.generated.resources.tab_qa
import churchpresenter.composeapp.generated.resources.tab_stt
import churchpresenter.composeapp.generated.resources.tab_web
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingRow
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.composables.TvScreenBox
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorContentType
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorSettings
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorStyleZone
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorZone
import org.churchpresenter.app.churchpresenter.data.settings.StageMonitorZoneStyle
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
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── Left column: content assignment + Top-Left/Top-Right style ──────
                Column(
                    modifier = Modifier.weight(1f).widthIn(min = 320.dp, max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StageMonitorContentSection(sm = sm, update = ::update)

                    ZoneStyleSection(
                        title = styleZoneLabel(StageMonitorStyleZone.FULL_SCREEN),
                        style = sm.styleFor(StageMonitorStyleZone.FULL_SCREEN),
                        availableFonts = availableFonts,
                        onStyleChange = { block ->
                            update {
                                copy(zoneStyles = zoneStyles + (StageMonitorStyleZone.FULL_SCREEN to styleFor(StageMonitorStyleZone.FULL_SCREEN).block()))
                            }
                        }
                    )

                    listOf(StageMonitorStyleZone.TOP_LEFT, StageMonitorStyleZone.TOP_RIGHT).forEach { zone ->
                        ZoneStyleSection(
                            title = styleZoneLabel(zone),
                            style = sm.styleFor(zone),
                            availableFonts = availableFonts,
                            onStyleChange = { block ->
                                update { copy(zoneStyles = zoneStyles + (zone to styleFor(zone).block())) }
                            }
                        )
                    }
                }

                // ── Right column: layout preview + remaining zone styles + clock ────
                Column(
                    modifier = Modifier.weight(1f).widthIn(min = 320.dp, max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StageMonitorLayoutPreviewSection(sm = sm)

                    listOf(
                        StageMonitorStyleZone.BOTTOM_LEFT,
                        StageMonitorStyleZone.BOTTOM_MIDDLE,
                        StageMonitorStyleZone.BOTTOM_RIGHT
                    ).forEach { zone ->
                        ZoneStyleSection(
                            title = styleZoneLabel(zone),
                            style = sm.styleFor(zone),
                            availableFonts = availableFonts,
                            onStyleChange = { block ->
                                update { copy(zoneStyles = zoneStyles + (zone to styleFor(zone).block())) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun contentTypeLabel(type: StageMonitorContentType): String = when (type) {
    StageMonitorContentType.BIBLE -> stringResource(Res.string.bible)
    StageMonitorContentType.SONGS -> stringResource(Res.string.songs)
    StageMonitorContentType.PRESENTATION -> stringResource(Res.string.presentation)
    StageMonitorContentType.PRESENTATION_NOTES -> stringResource(Res.string.stage_monitor_quadrant_notes)
    StageMonitorContentType.PICTURES -> stringResource(Res.string.pictures)
    StageMonitorContentType.MEDIA -> stringResource(Res.string.media)
    StageMonitorContentType.LOWER_THIRD -> stringResource(Res.string.obs_mode_lower_third)
    StageMonitorContentType.WEB -> stringResource(Res.string.tab_web)
    StageMonitorContentType.STT -> stringResource(Res.string.tab_stt)
    StageMonitorContentType.CANVAS -> stringResource(Res.string.tab_canvas)
    StageMonitorContentType.QA -> stringResource(Res.string.tab_qa)
    StageMonitorContentType.DICTIONARY -> stringResource(Res.string.tab_dictionary)
    StageMonitorContentType.CLOCK -> stringResource(Res.string.stage_monitor_quadrant_clock)
    StageMonitorContentType.ANNOUNCEMENT_TEXT -> stringResource(Res.string.content_announcements)
    StageMonitorContentType.NEXT -> stringResource(Res.string.stage_monitor_quadrant_next)
}

@Composable
private fun zoneLabel(zone: StageMonitorZone): String = when (zone) {
    StageMonitorZone.TOP_LEFT -> stringResource(Res.string.stage_monitor_zone_top_left)
    StageMonitorZone.TOP_RIGHT -> stringResource(Res.string.stage_monitor_zone_top_right)
    StageMonitorZone.BOTTOM_LEFT -> stringResource(Res.string.stage_monitor_zone_bottom_left)
    StageMonitorZone.BOTTOM_MIDDLE -> stringResource(Res.string.stage_monitor_zone_bottom_center)
    StageMonitorZone.BOTTOM_RIGHT -> stringResource(Res.string.stage_monitor_zone_bottom_right)
    StageMonitorZone.FULL_SCREEN -> stringResource(Res.string.stage_monitor_zone_full_screen)
    StageMonitorZone.NONE -> stringResource(Res.string.stage_monitor_zone_none)
}

@Composable
private fun styleZoneLabel(zone: StageMonitorStyleZone): String = when (zone) {
    StageMonitorStyleZone.TOP_LEFT -> stringResource(Res.string.stage_monitor_zone_top_left)
    StageMonitorStyleZone.TOP_RIGHT -> stringResource(Res.string.stage_monitor_zone_top_right)
    StageMonitorStyleZone.BOTTOM_LEFT -> stringResource(Res.string.stage_monitor_zone_bottom_left)
    StageMonitorStyleZone.BOTTOM_MIDDLE -> stringResource(Res.string.stage_monitor_zone_bottom_center)
    StageMonitorStyleZone.BOTTOM_RIGHT -> stringResource(Res.string.stage_monitor_zone_bottom_right)
    StageMonitorStyleZone.FULL_SCREEN -> stringResource(Res.string.stage_monitor_zone_full_screen)
}

/** For every content type, a dropdown choosing which zone (or none) it is routed to. */
@Composable
private fun StageMonitorContentSection(
    sm: StageMonitorSettings,
    update: (StageMonitorSettings.() -> StageMonitorSettings) -> Unit
) {
    // Bible/Songs/Next are always meant to share the screen with other zones, never take it over.
    val noFullScreenTypes = setOf(StageMonitorContentType.BIBLE, StageMonitorContentType.SONGS, StageMonitorContentType.NEXT)
    val allZones = StageMonitorZone.entries.map { zoneLabel(it) }
    val zonesWithoutFullScreen = StageMonitorZone.entries.filter { it != StageMonitorZone.FULL_SCREEN }.map { zoneLabel(it) }
    val zoneByLabel = StageMonitorZone.entries.associateBy { zoneLabel(it) }
    val types = StageMonitorContentType.entries
    val columns = types.chunked((types.size + 3) / 4)

    SettingsSection(title = stringResource(Res.string.stage_monitor_content_section)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            columns.forEach { column ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    column.forEach { type ->
                        DropdownSettingsField(
                            label = contentTypeLabel(type),
                            value = zoneLabel(sm.zoneFor(type)),
                            options = if (type in noFullScreenTypes) zonesWithoutFullScreen else allZones,
                            onValueChange = { picked ->
                                zoneByLabel[picked]?.let { zone -> update { copy(contentZones = contentZones + (type to zone)) } }
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Visual preview of the screen: shows which content types land in each zone, as labels. */
@Composable
private fun StageMonitorLayoutPreviewSection(sm: StageMonitorSettings) {
    val labeledTypes = StageMonitorContentType.entries.map { it to contentTypeLabel(it) }
    val byZone: Map<StageMonitorZone, List<String>> = labeledTypes
        .groupBy({ sm.zoneFor(it.first) }, { it.second })
    fun labelsFor(zone: StageMonitorZone): String = byZone[zone].orEmpty().joinToString(", ")

    SettingsSection(title = stringResource(Res.string.stage_monitor_layout_section)) {
        TvScreenBox(
            modifier = Modifier.fillMaxWidth(0.9f).height(200.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    ZoneLabelCell(text = labelsFor(StageMonitorZone.TOP_LEFT), modifier = Modifier.weight(1f))
                    ZoneLabelCell(text = labelsFor(StageMonitorZone.TOP_RIGHT), modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    ZoneLabelCell(text = labelsFor(StageMonitorZone.BOTTOM_LEFT), modifier = Modifier.weight(1f))
                    ZoneLabelCell(text = labelsFor(StageMonitorZone.BOTTOM_MIDDLE), modifier = Modifier.weight(0.8f))
                    ZoneLabelCell(text = labelsFor(StageMonitorZone.BOTTOM_RIGHT), modifier = Modifier.weight(1f))
                }
            }
        }
        SettingRow(label = stringResource(Res.string.stage_monitor_zone_full_screen)) {
            Text(
                text = labelsFor(StageMonitorZone.FULL_SCREEN).ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingRow(label = stringResource(Res.string.stage_monitor_zone_none)) {
            Text(
                text = labelsFor(StageMonitorZone.NONE).ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ZoneLabelCell(
    text: String,
    modifier: Modifier = Modifier,
    cellColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .background(cellColor, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.ifBlank { "—" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Full font/color/style/alignment editor for one drawable zone. */
@Composable
private fun ZoneStyleSection(
    title: String,
    style: StageMonitorZoneStyle,
    availableFonts: List<String>,
    onStyleChange: (StageMonitorZoneStyle.() -> StageMonitorZoneStyle) -> Unit
) {
    SettingsSection(title = title) {
        QuadrantFontSettings(
            fontType = style.fontType, fontSize = style.fontSize,
            color = style.color, bgColor = style.bgColor,
            bold = style.bold, italic = style.italic,
            underline = style.underline, shadow = style.shadow,
            shadowColor = style.shadowColor, shadowSize = style.shadowSize, shadowOpacity = style.shadowOpacity,
            availableFonts = availableFonts,
            onFontTypeChange = { v -> onStyleChange { copy(fontType = v) } },
            onFontSizeChange = { v -> onStyleChange { copy(fontSize = v) } },
            onColorChange = { v -> onStyleChange { copy(color = v) } },
            onBgColorChange = { v -> onStyleChange { copy(bgColor = v) } },
            onBoldChange = { v -> onStyleChange { copy(bold = v) } },
            onItalicChange = { v -> onStyleChange { copy(italic = v) } },
            onUnderlineChange = { v -> onStyleChange { copy(underline = v) } },
            onShadowChange = { v -> onStyleChange { copy(shadow = v) } },
            onShadowColorChange = { v -> onStyleChange { copy(shadowColor = v) } },
            onShadowSizeChange = { v -> onStyleChange { copy(shadowSize = v) } },
            onShadowOpacityChange = { v -> onStyleChange { copy(shadowOpacity = v) } }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.vertical_alignment), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                VerticalAlignmentButtons(
                    selectedAlignment = style.verticalAlignment,
                    onAlignmentChange = { v -> onStyleChange { copy(verticalAlignment = v) } },
                    topValue = Constants.TOP, middleValue = Constants.MIDDLE, bottomValue = Constants.BOTTOM
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.horizontal_alignment), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                HorizontalAlignmentButtons(
                    selectedAlignment = style.horizontalAlignment,
                    onAlignmentChange = { v -> onStyleChange { copy(horizontalAlignment = v) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
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
        TextStyleButtons(
            bold = bold, italic = italic, underline = underline, shadow = shadow,
            onBoldChange = onBoldChange, onItalicChange = onItalicChange,
            onUnderlineChange = onUnderlineChange, onShadowChange = onShadowChange
        )
    }
    SettingRow(stringResource(Res.string.shadow_settings)) {
        ShadowDetailRow(
            shadowColor = shadowColor, shadowSize = shadowSize, shadowOpacity = shadowOpacity,
            onColorChange = onShadowColorChange, onSizeChange = onShadowSizeChange, onOpacityChange = onShadowOpacityChange
        )
    }
}
