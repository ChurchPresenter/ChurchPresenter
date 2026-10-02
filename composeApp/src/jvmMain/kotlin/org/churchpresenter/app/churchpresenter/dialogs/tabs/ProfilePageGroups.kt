package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_summary_margins
import org.churchpresenter.strings.generated.resources.profile_text_size_unit
import org.churchpresenter.strings.generated.resources.bilingual_grid_1x3
import org.churchpresenter.strings.generated.resources.bilingual_grid_1x4
import org.churchpresenter.strings.generated.resources.bilingual_grid_2x2
import org.churchpresenter.strings.generated.resources.bilingual_grid_3x1
import org.churchpresenter.strings.generated.resources.bilingual_grid_4x1
import org.churchpresenter.strings.generated.resources.bottom
import org.churchpresenter.strings.generated.resources.center
import org.churchpresenter.strings.generated.resources.left
import org.churchpresenter.strings.generated.resources.middle
import org.churchpresenter.strings.generated.resources.percent_suffix
import org.churchpresenter.strings.generated.resources.profile_applies_to
import org.churchpresenter.strings.generated.resources.profile_band_height
import org.churchpresenter.strings.generated.resources.profile_band_height_sub
import org.churchpresenter.strings.generated.resources.profile_band_source
import org.churchpresenter.strings.generated.resources.profile_bg_app_default
import org.churchpresenter.strings.generated.resources.profile_bg_own
import org.churchpresenter.strings.generated.resources.profile_content_align
import org.churchpresenter.strings.generated.resources.profile_content_width
import org.churchpresenter.strings.generated.resources.profile_crossfade
import org.churchpresenter.strings.generated.resources.profile_duration
import org.churchpresenter.strings.generated.resources.profile_fade_in
import org.churchpresenter.strings.generated.resources.profile_fade_out
import org.churchpresenter.strings.generated.resources.profile_group_band
import org.churchpresenter.strings.generated.resources.profile_group_position
import org.churchpresenter.strings.generated.resources.profile_group_transition
import org.churchpresenter.strings.generated.resources.profile_layout_side_by_side
import org.churchpresenter.strings.generated.resources.profile_layout_stacked
import org.churchpresenter.strings.generated.resources.profile_margins
import org.churchpresenter.strings.generated.resources.profile_ms
import org.churchpresenter.strings.generated.resources.profile_place_freely
import org.churchpresenter.strings.generated.resources.profile_place_freely_sub
import org.churchpresenter.strings.generated.resources.profile_region_moves_background
import org.churchpresenter.strings.generated.resources.profile_region_moves_background_sub
import org.churchpresenter.strings.generated.resources.customize_theme_reset
import org.churchpresenter.strings.generated.resources.profile_vertical_alignment
import org.churchpresenter.strings.generated.resources.profile_x_offset
import org.churchpresenter.strings.generated.resources.profile_y_offset
import org.churchpresenter.strings.generated.resources.right
import org.churchpresenter.strings.generated.resources.top
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.ElementOffset
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * "Reset to defaults" at the end of a group's caption, drawn only while the group differs from them.
 * Linked profiles replace it with "Revert to {master}".
 */
@Composable
internal fun ResetAction(changed: Boolean, onReset: () -> Unit): (@Composable RowScope.() -> Unit)? =
    if (!changed) null else {
        { GroupCaptionAction(stringResource(Res.string.customize_theme_reset), onReset) }
    }

/** The four margins, the way a page's margin fields are read: top, bottom, left, right. */
internal data class Margins(val top: Int, val bottom: Int, val left: Int, val right: Int)

/** Which stored settings the Position group's rows write, for a linked profile to mark them. */
internal data class PositionPaths(
    val vertical: List<String> = emptyList(),
    val margins: List<String> = emptyList(),
    val region: List<String> = emptyList(),
) {
    val all: List<String> get() = vertical + margins + region
}

/**
 * POSITION ON SCREEN: where the block sits top to bottom and how far in from each edge, then --
 * Advanced, and on a full screen only -- the narrower region the content can be confined to.
 *
 * [region] is null on a lower third, whose band already is its region. [extraAdvanced] is the
 * element's own positioning, which the page adds.
 */
@Composable
internal fun PositionGroup(
    verticalAlignment: String?,
    onVerticalAlignment: (String) -> Unit,
    margins: Margins,
    onMargins: (Margins) -> Unit,
    region: ContentRegion?,
    onRegion: (ContentRegion) -> Unit,
    reset: (@Composable RowScope.() -> Unit)?,
    extraAdvanced: @Composable () -> Unit = {},
    paths: PositionPaths = PositionPaths(),
    /** What the margins are taken from -- the screen, or the band on a lower third. */
    room: MarginRoom = MarginRoom.FULL_SCREEN,
) {
    SettingsGroup(
        stringResource(Res.string.profile_group_position),
        key = "position",
        action = reset,
        paths = paths.all,
        summary = { positionSummary(verticalAlignment, margins) },
    ) {
        if (verticalAlignment != null) {
            SettingsRow(stringResource(Res.string.profile_vertical_alignment), paths = paths.vertical) {
                RowSegmented(
                    options = listOf(
                        RowOption(Constants.TOP, stringResource(Res.string.top)),
                        RowOption(Constants.MIDDLE, stringResource(Res.string.middle)),
                        RowOption(Constants.BOTTOM, stringResource(Res.string.bottom)),
                    ),
                    selected = verticalAlignment,
                    onSelect = onVerticalAlignment,
                )
            }
        }
        SettingsRow(stringResource(Res.string.profile_margins), paths = paths.margins) {
            MarginFields(margins, onMargins, room)
        }
        if (region != null) {
            val percent = stringResource(Res.string.percent_suffix)
            SettingsRow(stringResource(Res.string.profile_content_width), advanced = true, paths = paths.region) {
                RowStepper(
                    region.widthPercent,
                    { onRegion(region.copy(widthPercent = it)) },
                    ContentRegion.WIDTH_RANGE,
                    step = 5,
                    unit = percent,
                )
            }
            SettingsRow(stringResource(Res.string.profile_content_align), advanced = true, paths = paths.region) {
                RowSegmented(
                    options = listOf(
                        RowOption(ContentRegion.OFFSET_RANGE.first, stringResource(Res.string.left)),
                        RowOption(0, stringResource(Res.string.center)),
                        RowOption(ContentRegion.OFFSET_RANGE.last, stringResource(Res.string.right)),
                    ),
                    selected = region.xOffsetPercent,
                    onSelect = { onRegion(region.copy(xOffsetPercent = it)) },
                )
            }
            SettingsRow(stringResource(Res.string.profile_x_offset), advanced = true, paths = paths.region) {
                RowStepper(
                    region.xOffsetPercent,
                    { onRegion(region.copy(xOffsetPercent = it)) },
                    ContentRegion.OFFSET_RANGE,
                    unit = percent,
                )
            }
            SettingsRow(stringResource(Res.string.profile_y_offset), advanced = true, paths = paths.region) {
                RowStepper(
                    region.yOffsetPercent,
                    { onRegion(region.copy(yOffsetPercent = it)) },
                    ContentRegion.OFFSET_RANGE,
                    unit = percent,
                )
            }
            SettingsSwitchRow(
                stringResource(Res.string.profile_region_moves_background),
                region.movesBackground,
                { onRegion(region.copy(movesBackground = it)) },
                sub = stringResource(Res.string.profile_region_moves_background_sub),
                advanced = true,
                paths = paths.region,
            )
        }
        extraAdvanced()
    }
}

/** A folded Position group's summary: where the block sits, and its margins -- "Bottom · margins 40". */
@Composable
private fun positionSummary(verticalAlignment: String?, margins: Margins): String {
    val where = when (verticalAlignment) {
        Constants.TOP -> stringResource(Res.string.top)
        Constants.MIDDLE -> stringResource(Res.string.middle)
        Constants.BOTTOM -> stringResource(Res.string.bottom)
        else -> null
    }
    val all = listOf(margins.top, margins.bottom, margins.left, margins.right)
    val numbers = if (all.distinct().size == 1) "${margins.top}" else all.joinToString(" / ")
    return listOfNotNull(where, stringResource(Res.string.profile_summary_margins, numbers)).joinToString(" · ")
}

/** A folded Text group's summary: the font and its size -- "Arial · 48 pt". */
@Composable
internal fun textSummary(font: String, size: Int): String =
    listOf(font, "$size ${stringResource(Res.string.profile_text_size_unit)}").filter { it.isNotBlank() }
        .joinToString(" · ")

private val MARGIN_FIELD = 56.dp

/** Top, bottom, left and right, four small fields captioned above. */
@Composable
internal fun MarginFields(margins: Margins, onMargins: (Margins) -> Unit, room: MarginRoom = MarginRoom.FULL_SCREEN) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RowNumberField(
            margins.top,
            { onMargins(margins.copy(top = it)) },
            0..room.maxFor(MarginSide.TOP, margins),
            caption = stringResource(Res.string.top),
            width = MARGIN_FIELD,
            testTag = MARGIN_TOP_TAG,
        )
        RowNumberField(
            margins.bottom,
            { onMargins(margins.copy(bottom = it)) },
            0..room.maxFor(MarginSide.BOTTOM, margins),
            caption = stringResource(Res.string.bottom),
            width = MARGIN_FIELD,
            testTag = MARGIN_BOTTOM_TAG,
        )
        RowNumberField(
            margins.left,
            { onMargins(margins.copy(left = it)) },
            0..room.maxFor(MarginSide.LEFT, margins),
            caption = stringResource(Res.string.left),
            width = MARGIN_FIELD,
            testTag = MARGIN_LEFT_TAG,
        )
        RowNumberField(
            margins.right,
            { onMargins(margins.copy(right = it)) },
            0..room.maxFor(MarginSide.RIGHT, margins),
            caption = stringResource(Res.string.right),
            width = MARGIN_FIELD,
            testTag = MARGIN_RIGHT_TAG,
        )
    }
}

/**
 * One element taken out of the flow and placed anywhere in the frame -- Advanced. Off is the
 * default and means "laid out as it always was".
 */
@Composable
internal fun ElementPlacementRows(
    offset: ElementOffset?,
    onChange: (ElementOffset?) -> Unit,
    verticalOnly: Boolean = false,
    tagPrefix: String,
    paths: List<String> = emptyList(),
) {
    SettingsRow(
        stringResource(Res.string.profile_place_freely),
        sub = stringResource(Res.string.profile_place_freely_sub),
        advanced = true,
        paths = paths,
    ) {
        if (offset != null) {
            val percent = stringResource(Res.string.percent_suffix)
            if (!verticalOnly) {
                RowNumberField(
                    offset.xPercent,
                    { onChange(offset.copy(xPercent = it)) },
                    ElementOffset.PERCENT_RANGE,
                    unit = "X$percent",
                    width = 64.dp,
                )
            }
            RowNumberField(
                offset.yPercent,
                { onChange(offset.copy(yPercent = it)) },
                ElementOffset.PERCENT_RANGE,
                unit = "Y$percent",
                width = 64.dp,
            )
        }
        RowSwitch(
            checked = offset != null,
            onCheckedChange = { on -> onChange(if (on) ElementOffset() else null) },
            modifier = Modifier.testTag("${tagPrefix}_enabled"),
        )
    }
}

private val DURATION_RANGE_MS = 0..5000
private const val DURATION_STEP = 100

/** TRANSITION: fade in, fade out, the crossfade between items, and how long each takes. */
@Composable
internal fun TransitionGroup(
    fadeIn: Boolean,
    fadeOut: Boolean,
    crossfade: Boolean?,
    durationMs: Float,
    onFadeIn: (Boolean) -> Unit,
    onFadeOut: (Boolean) -> Unit,
    onCrossfade: (Boolean) -> Unit,
    onDuration: (Float) -> Unit,
    reset: (@Composable RowScope.() -> Unit)?,
    /** The settings object the four live in -- `bibleSettings`, `songSettings`. */
    prefix: String? = null,
) {
    val path = { field: String -> listOfNotNull(prefix?.let { "$it.$field" }) }
    SettingsGroup(
        stringResource(Res.string.profile_group_transition),
        key = "transition",
        action = reset,
        paths = listOf("fadeIn", "fadeOut", "crossfade", "transitionDuration").flatMap(path),
    ) {
        SettingsSwitchRow(stringResource(Res.string.profile_fade_in), fadeIn, onFadeIn, paths = path("fadeIn"))
        SettingsSwitchRow(stringResource(Res.string.profile_fade_out), fadeOut, onFadeOut, paths = path("fadeOut"))
        SettingsRow(stringResource(Res.string.profile_duration), paths = path("transitionDuration")) {
            RowStepper(
                durationMs.toInt(),
                { onDuration(it.toFloat()) },
                DURATION_RANGE_MS,
                step = DURATION_STEP,
                unit = stringResource(Res.string.profile_ms),
                fieldWidth = 76.dp,
            )
        }
        if (crossfade != null) {
            SettingsSwitchRow(
                stringResource(Res.string.profile_crossfade),
                crossfade,
                onCrossfade,
                paths = path("crossfade"),
            )
        }
    }
}

/**
 * LOWER THIRD BAND: how tall the band is, a shortcut back to whether it follows the app-wide
 * background for this content or has its own, and -- once that content is a Lottie animation --
 * the template picker itself.
 *
 * The number that sat beside the fades on the old strip, captioned "Lower Third", was exactly this
 * height. *Which* type the band uses -- a plain background or a Lottie animation -- is still picked
 * on the BACKGROUND group above it, alongside every other type -- see [ContentBackgroundGroup] --
 * but once that choice is Lottie, editing it happens here instead: the template picker, next to the
 * height and the ownership shortcut it already shares state with, rather than spread across two
 * cards. [profile_band_source]'s row writes the same ownership flag
 * ([OutputProfile.backgroundOverrides]) [ContentBackgroundGroup]'s own Own/App default row does, so
 * the two never diverge; the template picker claims it the same way on its first edit, through the
 * same [ownershipEdit].
 */
@Composable
internal fun BandGroup(
    scope: BackgroundScope,
    heightPercent: Int,
    onHeight: (Int) -> Unit,
    draft: AppSettings,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    reset: (@Composable RowScope.() -> Unit)?,
    /** The settings object the band's height lives in -- `bibleSettings`, `songSettings`. */
    prefix: String? = null,
) {
    val heightPaths = listOfNotNull(prefix?.let { "$it.lowerThirdHeightPercent" })
    val surfacePaths = scope.surfacePaths()
    val owned = scope.name in profile.backgroundOverrides
    val config = draft.backgroundSettings.configFor(scope)
    SettingsGroup(
        stringResource(Res.string.profile_group_band),
        key = "band",
        action = reset,
        paths = heightPaths + surfacePaths,
    ) {
        SettingsRow(
            stringResource(Res.string.profile_band_height),
            sub = stringResource(Res.string.profile_band_height_sub),
            paths = heightPaths,
        ) {
            RowStepper(
                heightPercent,
                onHeight,
                BAND_RANGE,
                unit = stringResource(Res.string.percent_suffix),
                testTag = BAND_HEIGHT_TAG,
            )
        }
        SettingsRow(stringResource(Res.string.profile_band_source), paths = surfacePaths) {
            RowSegmented(
                options = listOf(
                    RowOption(false, stringResource(Res.string.profile_bg_app_default), BAND_SOURCE_APP_DEFAULT_TAG),
                    RowOption(true, stringResource(Res.string.profile_bg_own), BAND_SOURCE_OWN_TAG),
                ),
                selected = owned,
                onSelect = { own ->
                    if (own == owned) return@RowSegmented
                    val backgrounds = draft.backgroundSettings
                    onProfileChange(
                        if (own) {
                            profile.withOwnSurface(scope, backgrounds, backgrounds.configFor(scope))
                        } else {
                            profile.copy(backgroundOverrides = profile.backgroundOverrides - scope.name)
                        },
                    )
                },
            )
        }
        if (config.backgroundType == Constants.BACKGROUND_LOTTIE) {
            val edit = ownershipEdit(scope, draft, profile, onProfileChange, onSettingsChange)
            val onConfig: (BackgroundConfig) -> Unit = { updated ->
                edit { s -> s.copy(backgroundSettings = s.backgroundSettings.withConfigFor(scope, updated)) }
            }
            LottieRows(scope, draft, config, onConfig = onConfig)
        }
    }
}

/** Test handle for the band height field. */
internal const val BAND_HEIGHT_TAG = "profile_band_height"

/**
 * Test handles for the band's own App default/Own shortcut -- distinct from
 * [BG_PROFILE_DEFAULT_TAG]/[BG_OWN_TAG], which the BACKGROUND group's row above it also draws on
 * the same page.
 */
internal const val BAND_SOURCE_APP_DEFAULT_TAG = "profile_band_source_app_default"
internal const val BAND_SOURCE_OWN_TAG = "profile_band_source_own"

/** Test handles for the four margin fields. */
internal const val MARGIN_TOP_TAG = "profile_margin_top"
internal const val MARGIN_BOTTOM_TAG = "profile_margin_bottom"
internal const val MARGIN_LEFT_TAG = "profile_margin_left"
internal const val MARGIN_RIGHT_TAG = "profile_margin_right"

/** The seven arrangements of parallel text, labelled as the Layout row names them. */
@Composable
internal fun bilingualLayoutRowOptions(): List<RowOption<String>> = listOf(
    RowOption(Constants.BILINGUAL_SIDE_BY_SIDE, stringResource(Res.string.profile_layout_side_by_side)),
    RowOption(Constants.BILINGUAL_TOP_BOTTOM, stringResource(Res.string.profile_layout_stacked)),
    RowOption(Constants.BILINGUAL_GRID_1X3, stringResource(Res.string.bilingual_grid_1x3)),
    RowOption(Constants.BILINGUAL_GRID_3X1, stringResource(Res.string.bilingual_grid_3x1)),
    RowOption(Constants.BILINGUAL_GRID_1X4, stringResource(Res.string.bilingual_grid_1x4)),
    RowOption(Constants.BILINGUAL_GRID_4X1, stringResource(Res.string.bilingual_grid_4x1)),
    RowOption(Constants.BILINGUAL_GRID_2X2, stringResource(Res.string.bilingual_grid_2x2)),
)

/**
 * The band across the top of a Text group: which translation or language the rows below edit, and
 * -- where there is more than one -- which element.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <A, E> AppliesToStrip(
    targets: List<RowOption<A>>,
    target: A,
    onTarget: (A) -> Unit,
    elements: List<RowOption<E>>,
    element: E?,
    onElement: (E) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (targets.size > 1) {
            Text(
                stringResource(Res.string.profile_applies_to),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RowSegmented(targets, target, onTarget, modifier = Modifier.testTag(CUSTOMIZE_TRANSLATION_ROW_TAG))
        }
        if (elements.size > 1 && element != null) {
            // Compact: the Songs page's five or six elements belong on one line.
            RowSegmented(
                elements,
                element,
                onElement,
                modifier = Modifier.testTag(CUSTOMIZE_ELEMENT_ROW_TAG),
                compact = true,
            )
        }
    }
}
