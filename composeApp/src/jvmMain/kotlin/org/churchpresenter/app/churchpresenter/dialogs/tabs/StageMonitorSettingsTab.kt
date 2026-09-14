package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_color
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.fade_in
import churchpresenter.composeapp.generated.resources.fade_out
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.stage_monitor_content_section
import churchpresenter.composeapp.generated.resources.stage_monitor_layout_stranded
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_shows
import churchpresenter.composeapp.generated.resources.stage_monitor_metronome_position
import churchpresenter.composeapp.generated.resources.shadow_settings
import churchpresenter.composeapp.generated.resources.song_chord_color
import churchpresenter.composeapp.generated.resources.stage_monitor_layout_section
import churchpresenter.composeapp.generated.resources.stage_monitor_text_color
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.transition_settings
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingRow
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.settings.StageMonitorZoneStyle
import org.churchpresenter.settings.toZone
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.stage_monitor_size_reset
import churchpresenter.composeapp.generated.resources.stage_monitor_size_hint
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.aspectRatio
import org.churchpresenter.settings.hasCustomZoneSizes
import org.churchpresenter.settings.withDefaultZoneSizes
import org.churchpresenter.settings.withEvenZoneSizes
import org.churchpresenter.settings.withEvenRowWidths
import org.churchpresenter.settings.withZoneHeight
import org.churchpresenter.settings.withZoneWidth
import org.churchpresenter.settings.zoneHeightPercent
import org.churchpresenter.settings.zoneWidthPercent
import org.churchpresenter.settings.layoutSizes


/**
 * How tall the monitor mockup may get, stand included.
 *
 * A cap rather than a size: the mockup is the shape of the real stage monitor, and a portrait or
 * rotated confidence display -- which churches do run -- would otherwise draw a preview taller than
 * the dialog. Deliberately not [SETTINGS_PREVIEW_MAX_HEIGHT]: an ordinary 16:9 output already lands
 * near 250dp, so sharing that 260dp number would leave the common case one dialog-resize away from
 * silently clamping.
 */
private const val SHOWS_COLUMNS = 4
private const val TRANSITION_LABEL_WIDTH = 120
private const val TRANSITION_STEP_MS = 50f
private const val TRANSITION_MIN_MS = 100f
private const val TRANSITION_MAX_MS = 2000f
internal const val ZONE_LINE_HEIGHT = 1.2f

@Composable
fun StageMonitorSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val availableFonts = rememberSystemFonts()

    val sm = settings.stageMonitorSettings

    // The shape of the monitor this layout actually goes out on -- the stage monitor's own output,
    // not the first assigned one, which is normally the congregation projector.
    val previewAspect = stageMonitorPreviewOutputSize(settings).aspectRatio
    fun update(block: StageMonitorSettings.() -> StageMonitorSettings) {
        onSettingsChange { s -> s.copy(stageMonitorSettings = s.stageMonitorSettings.block()) }
    }

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = SettingsScrollbarGutter),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).widthIn(min = 320.dp, max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsSection(title = stringResource(Res.string.stage_monitor_layout_section)) {
                        StageMonitorLayoutPicker(
                            layout = sm.layout,
                            screenAspect = previewAspect,
                            onPick = { picked -> update { withLayout(picked) } },
                        )
                    }
                    StageMonitorContentSection(sm = sm, update = ::update)
                    StageMonitorTransitionSection(sm = sm, update = ::update)
                }

                Column(
                    modifier = Modifier.weight(1f).widthIn(min = 320.dp, max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StageMonitorPreviewSection(sm = sm, screenAspect = previewAspect, update = ::update)

                    // Every zone the layout draws gets its own editor, plus the full-screen
                    // override, so nothing has to be selected to be styled.
                    val styleZones = listOf(StageMonitorStyleZone.FULL_SCREEN) + sm.layout.slots
                    styleZones.forEach { styleZone ->
                        ZoneStyleSection(
                            title = zoneLabel(styleZone.toZone()),
                            style = sm.styleFor(styleZone),
                            availableFonts = availableFonts,
                            // Songs cannot be routed full-screen, so only the layout's own zones
                            // can ever draw a chart.
                            showChordColor = styleZone != StageMonitorStyleZone.FULL_SCREEN,
                            onStyleChange = { block ->
                                update { copy(zoneStyles = zoneStyles + (styleZone to styleFor(styleZone).block())) }
                            }
                        )
                    }
                }
            }
        }
        SettingsScrollbar(scrollState)
    }
}

@Composable
private fun MetronomeRow(
    sm: StageMonitorSettings,
    update: (StageMonitorSettings.() -> StageMonitorSettings) -> Unit
) {
    val options = MetronomePosition.entries.map { metronomePositionLabel(it) }
    val byLabel = MetronomePosition.entries.associateBy { metronomePositionLabel(it) }
    DropdownSettingsField(
        label = stringResource(Res.string.stage_monitor_metronome_position),
        value = metronomePositionLabel(sm.metronomePosition),
        options = options,
        onValueChange = { picked ->
            byLabel[picked]?.let { position -> update { copy(metronomePosition = position) } }
        },
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/**
 * The monitor as it will be divided: what each zone shows, how big it is, and the controls for both.
 *
 * One picture of the screen rather than two. The zones are the same zones whichever question is
 * being asked — what is routed here, and how much room does it get — so a second diagram of the
 * same grid beside the first was a picture the reader had to reconcile rather than read.
 *
 * Sizing is done on the shared edges: height belongs to the row (a cell taller than the cell beside
 * it is not a grid), and a lone slider that silently changes its neighbor reads as a fault, whereas
 * a divider obviously moves both sides. The numbers underneath are for what a drag cannot do —
 * an exact 30/70.
 *
 * Full Screen and None sit under the grid rather than in it: neither is a position, and both have
 * to be shown to be understood.
 */
@Composable
private fun StageMonitorPreviewSection(
    sm: StageMonitorSettings,
    screenAspect: Float,
    update: (StageMonitorSettings.() -> StageMonitorSettings) -> Unit
) {
    // Keyed on the layout: its slots are what can be selected, and a smaller layout would otherwise
    // leave the selection pointing at a zone that is no longer drawn.
    var selected by remember(sm.layout) { mutableStateOf(sm.layout.slots.first()) }
    val sizes = sm.layoutSizes()

    SettingsSection(
        title = stringResource(Res.string.stage_monitor_content_section),
        headerTrailing = {
            TextButton(
                onClick = { update { withDefaultZoneSizes() } },
                enabled = sm.hasCustomZoneSizes(),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(24.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(stringResource(Res.string.stage_monitor_size_reset), style = MaterialTheme.typography.labelSmall)
            }
        }
    ) {
        Text(
            text = stringResource(Res.string.stage_monitor_size_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.labelSmall.fontSize * ZONE_LINE_HEIGHT,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        ZoneGrid(
            layout = sm.layout,
            sizes = sizes,
            screenAspect = screenAspect,
            selected = selected,
            metronomePosition = sm.metronomePosition,
            contentsOf = { zone -> sm.typesIn(zone).map { contentTypeLabel(it) }.joinToString(", ") },
            onSelect = { zone -> selected = zone },
            onWidthChange = { zone, percent -> update { withZoneWidth(zone, percent) } },
            onHeightChange = { zone, percent -> update { withZoneHeight(zone, percent) } },
        )
        // Straight under the diagram, as the design has it: these fields describe the zone that
        // was just clicked in it, and the two off-grid chips below are a different question.
        ZoneSizeControls(
            selectedLabel = zoneLabel(selected.toZone()),
            widthPercent = sm.zoneWidthPercent(selected),
            heightPercent = sm.zoneHeightPercent(selected),
            onWidthChange = { percent -> update { withZoneWidth(selected, percent) } },
            onHeightChange = { percent -> update { withZoneHeight(selected, percent) } },
            onEvenRow = { update { withEvenRowWidths(selected) } },
            onEvenAll = { update { withEvenZoneSizes() } },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(StageMonitorZone.FULL_SCREEN, StageMonitorZone.NONE).forEach { zone ->
                OffGridZoneChip(
                    label = zoneLabel(zone),
                    contents = sm.typesIn(zone).map { contentTypeLabel(it) }.joinToString(", "),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        val stranded = sm.strandedTypes()
        if (stranded.isNotEmpty()) {
            Text(
                text = stringResource(
                    Res.string.stage_monitor_layout_stranded,
                    stranded.map { contentTypeLabel(it) }.joinToString(", "),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun StageMonitorTransitionSection(
    sm: StageMonitorSettings,
    update: (StageMonitorSettings.() -> StageMonitorSettings) -> Unit
) {
    SettingsSection(title = stringResource(Res.string.transition_settings)) {
        val msSuffix = stringResource(Res.string.milliseconds_suffix)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.transition_duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(TRANSITION_LABEL_WIDTH.dp)
            )
            SlimSlider(
                value = sm.transitionDuration,
                onValueChange = { raw ->
                    val snapped = (raw / TRANSITION_STEP_MS).toInt() * TRANSITION_STEP_MS
                    update { copy(transitionDuration = snapped) }
                },
                valueRange = TRANSITION_MIN_MS..TRANSITION_MAX_MS,
                modifier = Modifier.weight(1f),
                trailingLabel = "${sm.transitionDuration.toInt()}$msSuffix"
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledCheckbox(
                checked = sm.fadeIn,
                onCheckedChange = { on -> update { copy(fadeIn = on) } },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.fade_in),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LabeledCheckbox(
                checked = sm.fadeOut,
                onCheckedChange = { on -> update { copy(fadeOut = on) } },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.fade_out),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LabeledCheckbox(
                checked = sm.crossfade,
                onCheckedChange = { on -> update { copy(crossfade = on) } },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.animation_crossfade),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * One dropdown per content type, naming the zone it is routed to.
 *
 * The routing is content-first and stays that way — a zone can host several types, and whichever
 * is live is what it draws. Only the zones this layout actually draws are offered, plus Full
 * Screen and None, so a type cannot be sent somewhere nothing would put it on screen.
 */
@Composable
private fun StageMonitorContentSection(
    sm: StageMonitorSettings,
    update: (StageMonitorSettings.() -> StageMonitorSettings) -> Unit
) {
    // Bible/Songs/Next are always meant to share the screen with other zones, never take it over.
    val noFullScreenTypes = setOf(
        StageMonitorContentType.BIBLE, StageMonitorContentType.SONGS, StageMonitorContentType.NEXT
    )
    val drawn = sm.layout.slots.map { it.toZone() } + listOf(StageMonitorZone.FULL_SCREEN, StageMonitorZone.NONE)
    val allZones = drawn.map { zoneLabel(it) }
    val zonesWithoutFullScreen = drawn.filter { it != StageMonitorZone.FULL_SCREEN }.map { zoneLabel(it) }
    val zoneByLabel = mutableMapOf<String, StageMonitorZone>()
    drawn.forEach { zoneByLabel[zoneLabel(it)] = it }
    val types = StageMonitorContentType.entries
    val columns = types.chunked((types.size + SHOWS_COLUMNS - 1) / SHOWS_COLUMNS)

    SettingsSection(title = stringResource(Res.string.stage_monitor_zone_shows)) {
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
                                zoneByLabel[picked]?.let { zone ->
                                    update { copy(contentZones = contentZones + (type to zone)) }
                                }
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
        MetronomeRow(sm = sm, update = update)
    }
}

@Composable
private fun ZoneStyleSection(
    title: String,
    style: StageMonitorZoneStyle,
    availableFonts: List<String>,
    // Only the zones a song's chart can land in are asked for a chord colour; on a clock or a
    // notes zone the setting would never do anything.
    showChordColor: Boolean = false,
    onStyleChange: (StageMonitorZoneStyle.() -> StageMonitorZoneStyle) -> Unit
) {
    SettingsSection(title = title) {
        QuadrantFontSettings(
            fontType = style.fontType, fontSize = style.fontSize,
            color = style.color, bgColor = style.bgColor,
            chordColor = if (showChordColor) style.chordColor else null,
            onChordColorChange = { v -> onStyleChange { copy(chordColor = v) } },
            shadowColor = style.shadowColor, shadowSize = style.shadowSize, shadowOpacity = style.shadowOpacity,
            availableFonts = availableFonts,
            onFontTypeChange = { v -> onStyleChange { copy(fontType = v) } },
            onFontSizeChange = { v -> onStyleChange { copy(fontSize = v) } },
            onColorChange = { v -> onStyleChange { copy(color = v) } },
            onBgColorChange = { v -> onStyleChange { copy(bgColor = v) } },
            onShadowColorChange = { v -> onStyleChange { copy(shadowColor = v) } },
            onShadowSizeChange = { v -> onStyleChange { copy(shadowSize = v) } },
            onShadowOpacityChange = { v -> onStyleChange { copy(shadowOpacity = v) } }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bold/Italic/Underline/Shadow moved here from the font settings row above, which was
            // getting squeezed for room with the font/size/color pickers already in it. Alignment
            // labels dropped too — each button already has its own tooltip.
            TextStyleButtons(
                bold = style.bold, italic = style.italic, underline = style.underline, shadow = style.shadow,
                onBoldChange = { v -> onStyleChange { copy(bold = v) } },
                onItalicChange = { v -> onStyleChange { copy(italic = v) } },
                onUnderlineChange = { v -> onStyleChange { copy(underline = v) } },
                onShadowChange = { v -> onStyleChange { copy(shadow = v) } },
                backdrop = style.backdrop,
                onBackdropChange = { v -> onStyleChange { copy(backdrop = v) } },
            )
            VerticalAlignmentButtons(
                selectedAlignment = style.verticalAlignment,
                onAlignmentChange = { v -> onStyleChange { copy(verticalAlignment = v) } },
                topValue = Constants.TOP, middleValue = Constants.MIDDLE, bottomValue = Constants.BOTTOM
            )
            HorizontalAlignmentButtons(
                selectedAlignment = style.horizontalAlignment,
                onAlignmentChange = { v -> onStyleChange { copy(horizontalAlignment = v) } },
                leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
            )
        }
    }
}

@Composable
private fun QuadrantFontSettings(
    fontType: String, fontSize: Int,
    color: String, bgColor: String,
    shadowColor: String, shadowSize: Int, shadowOpacity: Int,
    // Non-null only for a zone a song's chart can land in.
    chordColor: String? = null,
    onChordColorChange: (String) -> Unit = {},
    availableFonts: List<String>,
    onFontTypeChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onColorChange: (String) -> Unit,
    onBgColorChange: (String) -> Unit,
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
            label = stringResource(Res.string.stage_monitor_text_color).removeSuffix(":"),
            modifier = Modifier.widthIn(max = 150.dp)
        )
        if (chordColor != null) {
            ColorPickerField(
                color = chordColor,
                onColorChange = onChordColorChange,
                label = stringResource(Res.string.song_chord_color),
                modifier = Modifier.widthIn(max = 150.dp)
            )
        }
        ColorPickerField(
            color = bgColor,
            onColorChange = onBgColorChange,
            label = stringResource(Res.string.background_color).removeSuffix(":"),
            modifier = Modifier.widthIn(max = 150.dp)
        )
    }
    SettingRow(stringResource(Res.string.shadow_settings)) {
        ShadowDetailRow(
            shadowColor = shadowColor, shadowSize = shadowSize, shadowOpacity = shadowOpacity,
            onColorChange = onShadowColorChange, onSizeChange = onShadowSizeChange, onOpacityChange = onShadowOpacityChange
        )
    }
}
