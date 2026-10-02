package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.center
import org.churchpresenter.strings.generated.resources.left
import org.churchpresenter.strings.generated.resources.percent_suffix
import org.churchpresenter.strings.generated.resources.profile_bg_row
import org.churchpresenter.strings.generated.resources.profile_box_opacity
import org.churchpresenter.strings.generated.resources.profile_group_box
import org.churchpresenter.strings.generated.resources.profile_place_on_screen
import org.churchpresenter.strings.generated.resources.profile_pos_bottom_center
import org.churchpresenter.strings.generated.resources.profile_pos_bottom_left
import org.churchpresenter.strings.generated.resources.profile_pos_bottom_right
import org.churchpresenter.strings.generated.resources.profile_pos_center
import org.churchpresenter.strings.generated.resources.profile_pos_center_left
import org.churchpresenter.strings.generated.resources.profile_pos_center_right
import org.churchpresenter.strings.generated.resources.profile_pos_top_center
import org.churchpresenter.strings.generated.resources.profile_pos_top_left
import org.churchpresenter.strings.generated.resources.profile_pos_top_right
import org.churchpresenter.strings.generated.resources.profile_text_alignment
import org.churchpresenter.strings.generated.resources.profile_text_color
import org.churchpresenter.strings.generated.resources.profile_text_font
import org.churchpresenter.strings.generated.resources.profile_text_shadow
import org.churchpresenter.strings.generated.resources.profile_text_size
import org.churchpresenter.strings.generated.resources.profile_text_size_unit
import org.churchpresenter.strings.generated.resources.profile_text_style
import org.churchpresenter.strings.generated.resources.right
import org.churchpresenter.sharedui.composables.ScreenPositionPicker
import org.churchpresenter.sharedui.composables.ShadowDetailRow
import org.churchpresenter.sharedui.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.dialogs.DisplayTextStyle
import org.churchpresenter.sharedui.utils.rememberSystemFonts
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

private val DISPLAY_FONT_SIZES = 8..200
private const val DISPLAY_SIZE_STEP = 2
private val DISPLAY_STYLE_BUTTON = 26.dp
private val OPACITY_RANGE = 0..100
private const val OPACITY_STEP = 5

/*
 * The rows the overlay pages -- live captions, subtitles, Q&A -- share. Their settings classes keep
 * the same look under the same field names, so one set of rows, handed the object's path, marks a
 * linked profile's values on every one of them.
 */

/**
 * The text's look: font, size, colour, style -- outline and highlight among it -- and shadow in
 * Basic; the shadow's own colour and size in Advanced.
 * [prefix] is where the look is stored -- `sttSettings`, `qaSettings`, `mediaSettings`.
 */
@Composable
internal fun DisplayTextRows(
    style: DisplayTextStyle,
    onChange: (DisplayTextStyle) -> Unit,
    prefix: String,
    extraBasic: @Composable () -> Unit = {},
) {
    val path = { field: String -> listOf("$prefix.$field") }
    SettingsRow(stringResource(Res.string.profile_text_font), paths = path("fontType")) {
        RowFont(style.fontType, rememberSystemFonts()) { onChange(style.copy(fontType = it)) }
    }
    SettingsRow(stringResource(Res.string.profile_text_size), paths = path("fontSize")) {
        RowStepper(
            style.fontSize,
            { onChange(style.copy(fontSize = it)) },
            DISPLAY_FONT_SIZES,
            step = DISPLAY_SIZE_STEP,
            unit = stringResource(Res.string.profile_text_size_unit),
        )
    }
    SettingsRow(stringResource(Res.string.profile_text_color), paths = path("textColor")) {
        RowColor(style.textColor, { onChange(style.copy(textColor = it)) })
    }
    extraBasic()
    SettingsRow(
        stringResource(Res.string.profile_text_style),
        searchTerms = styleSearchTerms(),
        paths = path("bold") + path("italic") + path("underline") + path("outline") + path("backdrop"),
    ) {
        TextStyleButtons(
            bold = style.bold,
            italic = style.italic,
            underline = style.underline,
            shadow = style.shadow,
            onBoldChange = { onChange(style.copy(bold = it)) },
            onItalicChange = { onChange(style.copy(italic = it)) },
            onUnderlineChange = { onChange(style.copy(underline = it)) },
            onShadowChange = { onChange(style.copy(shadow = it)) },
            showShadow = false,
            buttonSize = DISPLAY_STYLE_BUTTON,
            outline = style.outline,
            onOutlineChange = { onChange(style.copy(outline = it)) },
            backdrop = style.backdrop,
            onBackdropChange = { onChange(style.copy(backdrop = it)) },
        )
    }
    SettingsSwitchRow(
        stringResource(Res.string.profile_text_shadow),
        style.shadow,
        { onChange(style.copy(shadow = it)) },
        paths = path("shadow"),
    )
    if (style.shadow) {
        SettingsWideRow(advanced = true, searchTerms = stringResource(Res.string.profile_text_shadow)) {
            ShadowDetailRow(
                shadowColor = style.shadowColor,
                shadowSize = style.shadowSize,
                shadowOpacity = style.shadowOpacity,
                onColorChange = { onChange(style.copy(shadowColor = it)) },
                onSizeChange = { onChange(style.copy(shadowSize = it)) },
                onOpacityChange = { onChange(style.copy(shadowOpacity = it)) },
            )
        }
    }
}

/** BOX: the colour behind the text and how opaque it is, in percent. */
@Composable
internal fun DisplayBoxGroup(
    color: String,
    onColor: (String) -> Unit,
    opacity: Int,
    onOpacity: (Int) -> Unit,
    prefix: String,
    /** A page's own rows ahead of the colour, and the stored settings they write. */
    leading: @Composable () -> Unit = {},
    leadingPaths: List<String> = emptyList(),
) {
    SettingsGroup(
        stringResource(Res.string.profile_group_box),
        key = "box",
        paths = listOf("$prefix.backgroundColor", "$prefix.backgroundOpacity") + leadingPaths,
        summary = { "$color · $opacity${stringResource(Res.string.percent_suffix)}" },
    ) {
        leading()
        SettingsRow(stringResource(Res.string.profile_bg_row), paths = listOf("$prefix.backgroundColor")) {
            RowColor(color, onColor)
        }
        SettingsRow(stringResource(Res.string.profile_box_opacity), paths = listOf("$prefix.backgroundOpacity")) {
            RowStepper(
                opacity,
                onOpacity,
                OPACITY_RANGE,
                step = OPACITY_STEP,
                unit = stringResource(Res.string.percent_suffix),
            )
        }
    }
}

/** Where on the screen the block sits, as the nine-point picker the old forms used. */
@Composable
internal fun ScreenPlacementRow(position: String, onPosition: (String) -> Unit, prefix: String) {
    SettingsRow(stringResource(Res.string.profile_place_on_screen), paths = listOf("$prefix.position")) {
        ScreenPositionPicker(
            positions = namedScreenPositions(),
            selected = shownPosition(position),
            onSelect = onPosition,
        )
    }
}

/**
 * [position] as one of the nine spots. Captions used to be placed only top, middle or bottom, and a
 * file can still say so; those read as the centred spot on that line, which is where they draw.
 */
private fun shownPosition(position: String): String = when (position) {
    Constants.TOP -> Constants.TOP_CENTER
    Constants.MIDDLE -> Constants.CENTER
    Constants.BOTTOM -> Constants.BOTTOM_CENTER
    else -> position
}

/** The nine spots, named in full. */
@Composable
private fun namedScreenPositions(): List<Pair<String, String>> = listOf(
    Constants.TOP_LEFT to stringResource(Res.string.profile_pos_top_left),
    Constants.TOP_CENTER to stringResource(Res.string.profile_pos_top_center),
    Constants.TOP_RIGHT to stringResource(Res.string.profile_pos_top_right),
    Constants.CENTER_LEFT to stringResource(Res.string.profile_pos_center_left),
    Constants.CENTER to stringResource(Res.string.profile_pos_center),
    Constants.CENTER_RIGHT to stringResource(Res.string.profile_pos_center_right),
    Constants.BOTTOM_LEFT to stringResource(Res.string.profile_pos_bottom_left),
    Constants.BOTTOM_CENTER to stringResource(Res.string.profile_pos_bottom_center),
    Constants.BOTTOM_RIGHT to stringResource(Res.string.profile_pos_bottom_right),
)

/** Left / Center / Right for the text inside its block. */
@Composable
internal fun DisplayAlignmentRow(alignment: String, onAlignment: (String) -> Unit, prefix: String) {
    SettingsRow(stringResource(Res.string.profile_text_alignment), paths = listOf("$prefix.horizontalAlignment")) {
        RowSegmented(
            options = listOf(
                RowOption(Constants.LEFT, stringResource(Res.string.left)),
                RowOption(Constants.CENTER, stringResource(Res.string.center)),
                RowOption(Constants.RIGHT, stringResource(Res.string.right)),
            ),
            selected = alignment,
            onSelect = onAlignment,
        )
    }
}

/** A short explanation at the top of a page, in its own card. */
@Composable
internal fun PageNote(text: String) {
    SettingsGroup("", key = "note") {
        SettingsWideRow { Text(text, fontSize = 12.sp, color = profilesPalette().faintText) }
    }
}
