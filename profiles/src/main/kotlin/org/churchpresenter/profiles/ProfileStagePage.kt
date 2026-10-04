package org.churchpresenter.profiles

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_layout
import org.churchpresenter.strings.generated.resources.profile_group_what_goes_where
import org.churchpresenter.strings.generated.resources.profile_stage_arrangement
import org.churchpresenter.strings.generated.resources.profile_stage_zones
import org.churchpresenter.strings.generated.resources.stage_monitor_layout_stranded
import org.churchpresenter.strings.generated.resources.stage_monitor_metronome_position
import org.churchpresenter.strings.generated.resources.stage_monitor_size_hint
import org.churchpresenter.strings.generated.resources.stage_monitor_size_reset
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorLayout
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.hasCustomZoneSizes
import org.churchpresenter.settings.layoutSizes
import org.churchpresenter.settings.toZone
import org.churchpresenter.settings.withDefaultZoneSizes
import org.churchpresenter.settings.withEvenRowWidths
import org.churchpresenter.settings.withEvenZoneSizes
import org.churchpresenter.settings.withZoneHeight
import org.churchpresenter.settings.withZoneWidth
import org.churchpresenter.settings.zoneHeightPercent
import org.churchpresenter.settings.zoneWidthPercent
import org.churchpresenter.theme.components.DropdownSelector
import org.jetbrains.compose.resources.stringResource

/** Where the stage monitor's settings live on a profile. */
internal const val STAGE_PATH = "stageMonitorSettings"

/** Shown on the page without Advanced: what a worship leader actually reads off the monitor. */
private val BASIC_CONTENT = listOf(
    StageMonitorContentType.BIBLE,
    StageMonitorContentType.SONGS,
    StageMonitorContentType.NEXT,
    StageMonitorContentType.CLOCK,
    StageMonitorContentType.ANNOUNCEMENT_TEXT,
    StageMonitorContentType.PRESENTATION_NOTES,
)

/** Always meant to share the screen with other zones, never to take it over. */
private val NO_FULL_SCREEN = setOf(
    StageMonitorContentType.BIBLE,
    StageMonitorContentType.SONGS,
    StageMonitorContentType.NEXT,
)

private val ZONE_PICKER_WIDTH = 170.dp

/**
 * The Stage layout page of a stage-monitor profile: how the monitor is divided, what goes in each
 * zone, how each zone's text looks, and the fades.
 *
 * The zone clicked in the diagram is the one the Text group edits, so the picture and the rows
 * below it always talk about the same zone.
 */
@Composable
internal fun ProfileStagePage(draft: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    val sm = draft.stageMonitorSettings
    val update: (StageMonitorSettings.() -> StageMonitorSettings) -> Unit = { block ->
        onSettingsChange { s -> s.copy(stageMonitorSettings = s.stageMonitorSettings.block()) }
    }
    // Keyed on the layout: a smaller one would leave the selection on a zone it no longer draws.
    var selected by remember(sm.layout) { mutableStateOf(sm.layout.slots.first()) }
    StageLayoutGroup(sm, stageMonitorPreviewOutputSize(draft).aspectRatio, selected, { selected = it }, update)
    WhatGoesWhereGroup(sm, update)
    StageTextGroup(sm, selected, { selected = it }, update)
    TransitionGroup(
        prefix = STAGE_PATH,
        fadeIn = sm.fadeIn,
        fadeOut = sm.fadeOut,
        crossfade = sm.crossfade,
        durationMs = sm.transitionDuration,
        onFadeIn = { v -> update { copy(fadeIn = v) } },
        onFadeOut = { v -> update { copy(fadeOut = v) } },
        onCrossfade = { v -> update { copy(crossfade = v) } },
        onDuration = { v -> update { copy(transitionDuration = v) } },
        reset = null,
    )
    // Only the zones this layout draws, and the full screen, each can be moved out into a box.
    val zones = sm.layout.slots + StageMonitorStyleZone.FULL_SCREEN
    ItemBoxGroup(
        items = zones.mapIndexed { index, zone ->
            BoxItem(
                zone.name,
                zoneLabel(zone.toZone()),
                TextBox(
                    xPercent = STAGE_BOX_INSET + (index % 2) * STAGE_BOX_HALF,
                    yPercent = STAGE_BOX_INSET + (index / 2 % 2) * STAGE_BOX_HALF,
                    widthPercent = STAGE_BOX_SIZE,
                    heightPercent = STAGE_BOX_SIZE,
                ),
            )
        },
        boxes = sm.textBoxes,
        options = sm.textBoxOptions,
        onBoxes = { boxes -> update { copy(textBoxes = boxes) } },
        onOptions = { options -> update { copy(textBoxOptions = options) } },
        paths = listOf("$STAGE_PATH.textBoxes", "$STAGE_PATH.textBoxOptions"),
    )
}

// Where a zone's box starts: a quarter of the screen, laid out two by two in the zones' order.
private const val STAGE_BOX_INSET = 2f
private const val STAGE_BOX_HALF = 50f
private const val STAGE_BOX_SIZE = 46f

/**
 * LAYOUT: how many zones and in which arrangement, then the monitor drawn to scale -- every zone
 * clickable, every shared edge draggable, exactly as the stage monitor's own tab sized them.
 */
@Composable
private fun StageLayoutGroup(
    sm: StageMonitorSettings,
    aspect: Float,
    selected: StageMonitorStyleZone,
    onSelect: (StageMonitorStyleZone) -> Unit,
    update: (StageMonitorSettings.() -> StageMonitorSettings) -> Unit,
) {
    val count = sm.layout.slots.size
    val resetLabel = stringResource(Res.string.stage_monitor_size_reset)
    val contents = StageMonitorZone.entries.associateWith { zone -> sm.typesIn(zone).map { contentTypeLabel(it) } }
    SettingsGroup(
        stringResource(Res.string.profile_layout),
        key = "layout",
        action = if (sm.hasCustomZoneSizes()) {
            { GroupCaptionAction(resetLabel, { update { withDefaultZoneSizes() } }) }
        } else {
            null
        },
        paths = listOf("$STAGE_PATH.layout", "$STAGE_PATH.zoneSizes"),
    ) {
        SettingsRow(stringResource(Res.string.profile_stage_zones), paths = listOf("$STAGE_PATH.layout")) {
            RowSegmented(
                options = StageMonitorLayout.zoneCounts().map { RowOption(it, it.toString(), stageZoneCountTag(it)) },
                selected = count,
                onSelect = { n -> if (n != count) update { withLayout(StageMonitorLayout.withZoneCount(n).first()) } },
            )
        }
        val arrangements = StageMonitorLayout.withZoneCount(count)
        if (arrangements.size > 1) {
            SettingsRow(stringResource(Res.string.profile_stage_arrangement), paths = listOf("$STAGE_PATH.layout")) {
                RowSegmented(
                    options = arrangements.map { RowOption(it, layoutLabel(it)) },
                    selected = sm.layout,
                    onSelect = { picked -> update { withLayout(picked) } },
                )
            }
        }
        SettingsWideRow(searchTerms = stringResource(Res.string.profile_layout)) {
            Text(
                stringResource(Res.string.stage_monitor_size_hint),
                fontSize = 11.sp,
                color = profilesPalette().faintText,
            )
            ZoneGrid(
                layout = sm.layout,
                sizes = sm.layoutSizes(),
                screenAspect = aspect,
                selected = selected,
                metronomePosition = sm.metronomePosition,
                contentsOf = { zone -> contents[zone].orEmpty().joinToString(", ") },
                onSelect = onSelect,
                onWidthChange = { zone, percent -> update { withZoneWidth(zone, percent) } },
                onHeightChange = { zone, percent -> update { withZoneHeight(zone, percent) } },
            )
        }
        SettingsWideRow(advanced = true, searchTerms = stringResource(Res.string.profile_layout)) {
            ZoneSizeControls(
                selectedLabel = zoneLabel(selected.toZone()),
                widthPercent = sm.zoneWidthPercent(selected),
                heightPercent = sm.zoneHeightPercent(selected),
                onWidthChange = { percent -> update { withZoneWidth(selected, percent) } },
                onHeightChange = { percent -> update { withZoneHeight(selected, percent) } },
                onEvenRow = { update { withEvenRowWidths(selected) } },
                onEvenAll = { update { withEvenZoneSizes() } },
            )
        }
    }
}

/**
 * WHAT GOES WHERE: each kind of content set to one of the layout's zones, the whole screen, or
 * hidden. The six a stage is usually set up around are Basic; the rest are Advanced.
 */
@Composable
private fun WhatGoesWhereGroup(
    sm: StageMonitorSettings,
    update: (StageMonitorSettings.() -> StageMonitorSettings) -> Unit,
) {
    val drawn = sm.layout.slots.map { it.toZone() } + listOf(StageMonitorZone.FULL_SCREEN, StageMonitorZone.NONE)
    SettingsGroup(
        stringResource(Res.string.profile_group_what_goes_where),
        key = "what_goes_where",
        paths = listOf("$STAGE_PATH.contentZones", "$STAGE_PATH.metronomePosition"),
    ) {
        val ordered = BASIC_CONTENT + StageMonitorContentType.entries.filterNot { it in BASIC_CONTENT }
        ordered.forEach { type ->
            val zones = if (type in NO_FULL_SCREEN) drawn.filter { it != StageMonitorZone.FULL_SCREEN } else drawn
            SettingsRow(
                contentTypeLabel(type),
                advanced = type !in BASIC_CONTENT,
                paths = listOf("$STAGE_PATH.contentZones.${type.name}"),
            ) {
                DropdownSelector(
                    label = "",
                    value = sm.zoneFor(type).name,
                    options = zones.map { it.name to zoneLabel(it) },
                    onValueChange = { picked ->
                        val zone = StageMonitorZone.valueOf(picked)
                        update { copy(contentZones = contentZones + (type to zone)) }
                    },
                    modifier = Modifier.width(ZONE_PICKER_WIDTH).testTag(stageContentTag(type)),
                    compact = true,
                )
            }
        }
        SettingsRow(
            stringResource(Res.string.stage_monitor_metronome_position),
            advanced = true,
            paths = listOf("$STAGE_PATH.metronomePosition"),
        ) {
            DropdownSelector(
                label = "",
                value = sm.metronomePosition.name,
                options = MetronomePosition.entries.map { it.name to metronomePositionLabel(it) },
                onValueChange = { picked -> update { copy(metronomePosition = MetronomePosition.valueOf(picked)) } },
                modifier = Modifier.width(ZONE_PICKER_WIDTH),
                compact = true,
            )
        }
        val stranded = sm.strandedTypes()
        if (stranded.isNotEmpty()) {
            val names = stranded.map { contentTypeLabel(it) }.joinToString(", ")
            SettingsWideRow {
                Text(
                    stringResource(Res.string.stage_monitor_layout_stranded, names),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Test handles for the zone count and one content type's zone. */
internal fun stageZoneCountTag(count: Int): String = "profile_stage_zones_$count"
internal fun stageContentTag(type: StageMonitorContentType): String = "profile_stage_content_${type.name}"
