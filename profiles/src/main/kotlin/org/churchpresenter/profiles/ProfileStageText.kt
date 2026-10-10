package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import org.churchpresenter.sharedui.guide.GuideTargets
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bottom
import org.churchpresenter.strings.generated.resources.center
import org.churchpresenter.strings.generated.resources.left
import org.churchpresenter.strings.generated.resources.middle
import org.churchpresenter.strings.generated.resources.profile_group_text
import org.churchpresenter.strings.generated.resources.profile_stage_zone_background
import org.churchpresenter.strings.generated.resources.profile_text_alignment
import org.churchpresenter.strings.generated.resources.profile_text_chord_color
import org.churchpresenter.strings.generated.resources.profile_text_color
import org.churchpresenter.strings.generated.resources.profile_text_font
import org.churchpresenter.strings.generated.resources.profile_text_shadow
import org.churchpresenter.strings.generated.resources.profile_text_size
import org.churchpresenter.strings.generated.resources.profile_text_size_unit
import org.churchpresenter.strings.generated.resources.profile_text_style
import org.churchpresenter.strings.generated.resources.profile_vertical_alignment
import org.churchpresenter.strings.generated.resources.right
import org.churchpresenter.strings.generated.resources.top
import org.churchpresenter.sharedui.composables.ShadowDetailRow
import org.churchpresenter.sharedui.composables.TextStyleButtons
import org.churchpresenter.sharedui.utils.rememberSystemFonts
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZoneStyle
import org.churchpresenter.settings.toZone
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

private val STAGE_FONT_SIZES = 8..200
private const val STAGE_SIZE_STEP = 2
private val STAGE_STYLE_BUTTON = 26.dp

/**
 * TEXT: how one zone's words look, picked on the strip above -- or by clicking the zone in the
 * diagram. Full screen is a zone of its own here, since whatever takes the screen over is styled
 * apart from the grid.
 */
@Composable
internal fun StageTextGroup(
    sm: StageMonitorSettings,
    selected: StageMonitorStyleZone,
    onSelect: (StageMonitorStyleZone) -> Unit,
    update: (StageMonitorSettings.() -> StageMonitorSettings) -> Unit,
) {
    val zones = sm.layout.slots + StageMonitorStyleZone.FULL_SCREEN
    val zone = selected.takeIf { it in zones } ?: zones.first()
    val style = sm.styleFor(zone)
    val base = "$STAGE_PATH.zoneStyles.${zone.name}"
    val path = { field: String -> listOf("$base.$field") }
    val write: (StageMonitorZoneStyle.() -> StageMonitorZoneStyle) -> Unit = { block ->
        update { copy(zoneStyles = zoneStyles + (zone to styleFor(zone).block())) }
    }
    SettingsGroup(
        stringResource(Res.string.profile_group_text),
        key = "text",
        paths = listOf(base),
        header = {
            AppliesToStrip(
                // The first zone is the one the helper's stage text tour points at.
                targets = zones.mapIndexed { index, zone ->
                    val guide = if (index == 0) GuideTargets.STAGE_TEXT_ZONE else null
                    RowOption(zone, zoneLabel(zone.toZone()), stageZoneTag(zone), guideTarget = guide)
                },
                target = zone,
                onTarget = onSelect,
                elements = emptyList<RowOption<Unit>>(),
                element = null,
                onElement = {},
            )
        },
    ) {
        key(zone) {
            SettingsRow(Res.string.profile_text_font, paths = path("fontType")) {
                RowFont(style.fontType, rememberSystemFonts()) { v -> write { copy(fontType = v) } }
            }
            SettingsRow(Res.string.profile_text_size, paths = path("fontSize")) {
                RowStepper(
                    style.fontSize,
                    { v -> write { copy(fontSize = v) } },
                    STAGE_FONT_SIZES,
                    step = STAGE_SIZE_STEP,
                    unit = stringResource(Res.string.profile_text_size_unit),
                )
            }
            SettingsRow(Res.string.profile_text_color, paths = path("color")) {
                RowColor(style.color, { v -> write { copy(color = v) } })
            }
            SettingsRow(Res.string.profile_stage_zone_background, paths = path("bgColor")) {
                RowColor(style.bgColor, { v -> write { copy(bgColor = v) } })
            }
            StageStyleRows(zone, style, path, write)
        }
    }
}

/**
 * Style -- outline and highlight among it -- alignment, vertical alignment and shadow in Basic; the
 * chord colour and the shadow's own colour and size in Advanced.
 */
@Composable
private fun StageStyleRows(
    zone: StageMonitorStyleZone,
    style: StageMonitorZoneStyle,
    path: (String) -> List<String>,
    write: (StageMonitorZoneStyle.() -> StageMonitorZoneStyle) -> Unit,
) {
    SettingsRow(
        Res.string.profile_text_style,
        searchTerms = styleSearchTerms(),
        paths = path("bold") + path("italic") + path("underline") + path("outline") + path("backdrop"),
    ) {
        TextStyleButtons(
            bold = style.bold,
            italic = style.italic,
            underline = style.underline,
            shadow = style.shadow,
            onBoldChange = { v -> write { copy(bold = v) } },
            onItalicChange = { v -> write { copy(italic = v) } },
            onUnderlineChange = { v -> write { copy(underline = v) } },
            onShadowChange = { v -> write { copy(shadow = v) } },
            showShadow = false,
            buttonSize = STAGE_STYLE_BUTTON,
            outline = style.outline,
            onOutlineChange = { v -> write { copy(outline = v) } },
            backdrop = style.backdrop,
            onBackdropChange = { v -> write { copy(backdrop = v) } },
        )
    }
    SettingsRow(Res.string.profile_text_alignment, paths = path("horizontalAlignment")) {
        RowSegmented(
            options = listOf(
                RowOption(Constants.LEFT, stringResource(Res.string.left)),
                RowOption(Constants.CENTER, stringResource(Res.string.center)),
                RowOption(Constants.RIGHT, stringResource(Res.string.right)),
            ),
            selected = style.horizontalAlignment,
            onSelect = { v -> write { copy(horizontalAlignment = v) } },
        )
    }
    SettingsRow(
        Res.string.profile_vertical_alignment,
        paths = path("verticalAlignment"),
    ) {
        RowSegmented(
            options = listOf(
                RowOption(Constants.TOP, stringResource(Res.string.top)),
                RowOption(Constants.MIDDLE, stringResource(Res.string.middle)),
                RowOption(Constants.BOTTOM, stringResource(Res.string.bottom)),
            ),
            selected = style.verticalAlignment,
            onSelect = { v -> write { copy(verticalAlignment = v) } },
        )
    }
    // Songs cannot be routed full screen, so only the layout's own zones can ever draw a chart.
    if (zone != StageMonitorStyleZone.FULL_SCREEN) {
        SettingsRow(Res.string.profile_text_chord_color, advanced = true, paths = path("chordColor")) {
            RowColor(style.chordColor, { v -> write { copy(chordColor = v) } })
        }
    }
    SettingsSwitchRow(
        Res.string.profile_text_shadow,
        style.shadow,
        { v -> write { copy(shadow = v) } },
        paths = path("shadow"),
    )
    if (style.shadow) {
        SettingsWideRow(advanced = true, searchTerms = stringResource(Res.string.profile_text_shadow)) {
            ShadowDetailRow(
                shadowColor = style.shadowColor,
                shadowSize = style.shadowSize,
                shadowOpacity = style.shadowOpacity,
                onColorChange = { v -> write { copy(shadowColor = v) } },
                onSizeChange = { v -> write { copy(shadowSize = v) } },
                onOpacityChange = { v -> write { copy(shadowOpacity = v) } },
            )
        }
    }
}

/** Test handle for one zone of the Text group's strip. */
internal fun stageZoneTag(zone: StageMonitorStyleZone): String = "profile_stage_zone_${zone.name}"
