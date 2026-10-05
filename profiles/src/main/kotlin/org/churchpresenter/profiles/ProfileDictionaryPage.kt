package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.percent_suffix
import org.churchpresenter.strings.generated.resources.profile_card_color
import org.churchpresenter.strings.generated.resources.profile_card_opacity
import org.churchpresenter.strings.generated.resources.dictionary_definition
import org.churchpresenter.strings.generated.resources.profile_dict_kjv
import org.churchpresenter.strings.generated.resources.profile_reference
import org.churchpresenter.strings.generated.resources.profile_dict_word
import org.churchpresenter.strings.generated.resources.profile_group_card
import org.churchpresenter.strings.generated.resources.profile_group_show
import org.churchpresenter.strings.generated.resources.profile_group_text
import org.churchpresenter.strings.generated.resources.profile_text_color
import org.churchpresenter.strings.generated.resources.profile_text_font
import org.churchpresenter.strings.generated.resources.profile_text_shadow
import org.churchpresenter.strings.generated.resources.profile_text_size
import org.churchpresenter.strings.generated.resources.profile_text_size_unit
import org.churchpresenter.strings.generated.resources.profile_text_style
import org.churchpresenter.sharedui.composables.ShadowDetailRow
import org.churchpresenter.sharedui.composables.TextBackdropButton
import org.churchpresenter.sharedui.composables.TextOutlineButton
import org.churchpresenter.sharedui.composables.TextStyleButtons
import org.churchpresenter.sharedui.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.TextBox
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val DICT = "dictionarySettings"
private val DICT_FONT_SIZES = 8..200
private const val DICT_SIZE_STEP = 2
private val DICT_STYLE_BUTTON = 26.dp
private const val OPACITY_STEP = 5

/**
 * The Strong's dictionary card on this output: which of its four parts it shows, how each part
 * looks -- picked on the strip, since the parts are styled apart -- the card behind them, and the
 * fades.
 */
@Composable
internal fun ProfileDictionaryPage(draft: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    val ds = draft.dictionarySettings
    val update: ((DictionarySettings) -> DictionarySettings) -> Unit = { t ->
        onSettingsChange { s -> s.copy(dictionarySettings = t(s.dictionarySettings)) }
    }
    var part by remember { mutableStateOf(DictionaryPart.WORD) }
    SettingsGroup(stringResource(Res.string.profile_group_show), key = "show") {
        DictionaryPart.entries.forEach { p ->
            SettingsSwitchRow(
                p.label(),
                p.shown(ds),
                { v -> update { p.withShown(it, v) } },
                paths = listOf("$DICT.${p.showField}"),
            )
        }
    }
    SettingsGroup(
        stringResource(Res.string.profile_group_text),
        key = "text",
        paths = part.fields.map { "$DICT.$it" },
        header = {
            AppliesToStrip(
                targets = DictionaryPart.entries.map { RowOption(it, it.label(), dictionaryPartTag(it)) },
                target = part,
                onTarget = { part = it },
                elements = emptyList<RowOption<Unit>>(),
                element = null,
                onElement = {},
            )
        },
    ) {
        key(part) { DictionaryPartRows(part.look(ds)) { look -> update { part.withLook(it, look) } } }
    }
    SettingsGroup(
        stringResource(Res.string.profile_group_card),
        key = "card",
        paths = listOf("$DICT.cardBackgroundColor", "$DICT.cardBackgroundOpacity"),
    ) {
        SettingsRow(stringResource(Res.string.profile_card_color), paths = listOf("$DICT.cardBackgroundColor")) {
            RowColor(ds.cardBackgroundColor, { v -> update { it.copy(cardBackgroundColor = v) } })
        }
        SettingsRow(stringResource(Res.string.profile_card_opacity), paths = listOf("$DICT.cardBackgroundOpacity")) {
            RowStepper(
                (ds.cardBackgroundOpacity * FULL_PERCENT).roundToInt(),
                { v -> update { it.copy(cardBackgroundOpacity = v / FULL_PERCENT) } },
                0..FULL_PERCENT.toInt(),
                step = OPACITY_STEP,
                unit = stringResource(Res.string.percent_suffix),
            )
        }
    }
    TransitionGroup(
        prefix = DICT,
        fadeIn = ds.fadeIn,
        fadeOut = ds.fadeOut,
        crossfade = null,
        durationMs = ds.transitionDuration,
        onFadeIn = { v -> update { it.copy(fadeIn = v) } },
        onFadeOut = { v -> update { it.copy(fadeOut = v) } },
        onCrossfade = {},
        onDuration = { v -> update { it.copy(transitionDuration = v) } },
        reset = null,
    )
    ItemBoxGroup(
        items = DictionaryPart.entries.mapIndexed { row, p ->
            BoxItem(
                p.name,
                p.label(),
                TextBox(
                    xPercent = 10f,
                    yPercent = DICT_BOX_TOP + row * DICT_BOX_STEP,
                    widthPercent = 80f,
                    heightPercent = DICT_BOX_HEIGHT,
                ),
            )
        },
        boxes = ds.textBoxes,
        options = ds.textBoxOptions,
        onBoxes = { boxes -> update { it.copy(textBoxes = boxes) } },
        onOptions = { options -> update { it.copy(textBoxOptions = options) } },
        paths = listOf("$DICT.textBoxes", "$DICT.textBoxOptions"),
    )
}

// Where the parts' boxes start: a column of them down the screen, in the card's own order.
private const val DICT_BOX_TOP = 10f
private const val DICT_BOX_STEP = 21f
private const val DICT_BOX_HEIGHT = 19f

/**
 * One part's Style row: bold and italic, the outline and the highlight -- whichever of them the part
 * has a setting for, and no row at all when it has none.
 */
@Composable
private fun DictionaryStyleRow(
    look: DictionaryLook,
    onChange: (DictionaryLook) -> Unit,
    p: (String?) -> List<String>,
) {
    val hasWeight = look.bold != null && look.italic != null
    if (!hasWeight && look.outline == null && look.backdrop == null) return
    val names = look.names
    SettingsRow(
        stringResource(Res.string.profile_text_style),
        searchTerms = styleSearchTerms(),
        paths = p(names.bold) + p(names.italic) + p(names.outline) + p(names.backdrop),
    ) {
        if (look.bold != null && look.italic != null) {
            TextStyleButtons(
                bold = look.bold,
                italic = look.italic,
                underline = false,
                shadow = false,
                onBoldChange = { onChange(look.copy(bold = it)) },
                onItalicChange = { onChange(look.copy(italic = it)) },
                onUnderlineChange = {},
                onShadowChange = {},
                showShadow = false,
                showUnderline = false,
                buttonSize = DICT_STYLE_BUTTON,
                outline = look.outline,
                onOutlineChange = { onChange(look.copy(outline = it)) },
                backdrop = look.backdrop,
                onBackdropChange = { onChange(look.copy(backdrop = it)) },
            )
        } else {
            // No weight to set: only the outline and highlight, spaced as the style buttons are.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                look.outline?.let {
                    TextOutlineButton(it, { v -> onChange(look.copy(outline = v)) }, DICT_STYLE_BUTTON)
                }
                look.backdrop?.let {
                    TextBackdropButton(it, { v -> onChange(look.copy(backdrop = v)) }, DICT_STYLE_BUTTON)
                }
            }
        }
    }
}

/** One part's rows: only the ones that part has a setting for. */
@Composable
private fun DictionaryPartRows(look: DictionaryLook, onChange: (DictionaryLook) -> Unit) {
    val p = { field: String? -> listOfNotNull(field?.let { "$DICT.$it" }) }
    look.fontType?.let { font ->
        SettingsRow(stringResource(Res.string.profile_text_font), paths = p(look.names.font)) {
            RowFont(font, rememberSystemFonts()) { onChange(look.copy(fontType = it)) }
        }
    }
    SettingsRow(stringResource(Res.string.profile_text_size), paths = p(look.names.size)) {
        RowStepper(
            look.fontSize,
            { onChange(look.copy(fontSize = it)) },
            DICT_FONT_SIZES,
            step = DICT_SIZE_STEP,
            unit = stringResource(Res.string.profile_text_size_unit),
        )
    }
    SettingsRow(stringResource(Res.string.profile_text_color), paths = p(look.names.color)) {
        RowColor(look.color, { onChange(look.copy(color = it)) })
    }
    DictionaryStyleRow(look, onChange, p)
    look.shadow?.let { shadow ->
        SettingsSwitchRow(
            stringResource(Res.string.profile_text_shadow),
            shadow,
            { onChange(look.copy(shadow = it)) },
            paths = p(look.names.shadow),
        )
        if (shadow) {
            SettingsWideRow(advanced = true, searchTerms = stringResource(Res.string.profile_text_shadow)) {
                ShadowDetailRow(
                    shadowColor = look.shadowColor,
                    shadowSize = look.shadowSize,
                    shadowOpacity = look.shadowOpacity,
                    onColorChange = { onChange(look.copy(shadowColor = it)) },
                    onSizeChange = { onChange(look.copy(shadowSize = it)) },
                    onOpacityChange = { onChange(look.copy(shadowOpacity = it)) },
                )
            }
        }
    }
}

/** The strip's label for [this] part. */
@Composable
private fun DictionaryPart.label(): String = stringResource(
    when (this) {
        DictionaryPart.WORD -> Res.string.profile_dict_word
        DictionaryPart.REFERENCE -> Res.string.profile_reference
        DictionaryPart.DEFINITION -> Res.string.dictionary_definition
        DictionaryPart.KJV_USAGE -> Res.string.profile_dict_kjv
    },
)

/** Test handle for one part on the Dictionary strip. */
internal fun dictionaryPartTag(part: DictionaryPart): String = "profile_dictionary_part_${part.name}"
