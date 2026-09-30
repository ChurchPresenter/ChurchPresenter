package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.middle
import churchpresenter.composeapp.generated.resources.percent_suffix
import churchpresenter.composeapp.generated.resources.profile_box
import churchpresenter.composeapp.generated.resources.profile_box_area
import churchpresenter.composeapp.generated.resources.profile_box_area_margins
import churchpresenter.composeapp.generated.resources.profile_box_area_screen
import churchpresenter.composeapp.generated.resources.profile_box_band
import churchpresenter.composeapp.generated.resources.profile_box_band_band
import churchpresenter.composeapp.generated.resources.profile_box_band_screen
import churchpresenter.composeapp.generated.resources.profile_box_height
import churchpresenter.composeapp.generated.resources.profile_box_keep_clear
import churchpresenter.composeapp.generated.resources.profile_box_keep_clear_sub
import churchpresenter.composeapp.generated.resources.profile_box_languages
import churchpresenter.composeapp.generated.resources.profile_box_languages_own
import churchpresenter.composeapp.generated.resources.profile_box_languages_shared
import churchpresenter.composeapp.generated.resources.profile_box_overflow
import churchpresenter.composeapp.generated.resources.profile_box_overflow_cut
import churchpresenter.composeapp.generated.resources.profile_box_overflow_shrink
import churchpresenter.composeapp.generated.resources.profile_box_overflow_spill
import churchpresenter.composeapp.generated.resources.profile_box_place
import churchpresenter.composeapp.generated.resources.customize_theme_text_size
import churchpresenter.composeapp.generated.resources.profile_box_size_fill
import churchpresenter.composeapp.generated.resources.profile_box_size_up_to
import churchpresenter.composeapp.generated.resources.profile_box_snap
import churchpresenter.composeapp.generated.resources.profile_box_sub
import churchpresenter.composeapp.generated.resources.profile_box_vertical
import churchpresenter.composeapp.generated.resources.profile_box_width
import churchpresenter.composeapp.generated.resources.profile_box_x
import churchpresenter.composeapp.generated.resources.profile_box_y
import churchpresenter.composeapp.generated.resources.top
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.TextBoxOptions
import org.churchpresenter.settings.TextBoxOverflow
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val BOX_FIELD = 52.dp
private val BOX_PERCENT_RANGE = 0..100
private val SIZE_RANGE = TextBox.MIN_SIZE_PERCENT.toInt()..100

/**
 * The rows that give one item a text box and set it: the switch, where short text sits and what
 * happens to text too big for it in Basic; the exact rectangle and the fill choice in Advanced, and
 * after them how every box on the page behaves -- [options], shared by all of the page's boxes.
 *
 * [startBox] is where the box begins the first time it is turned on. A box turned off keeps its
 * rectangle, so turning it back on puts it where it was. [perLanguage] offers the own-box-each /
 * share-one choice, for a page whose items are drawn once per language; [lowerThird] offers what a
 * lower third's boxes are measured against.
 */
@Composable
internal fun TextBoxRows(
    box: TextBox,
    onBox: (TextBox) -> Unit,
    startBox: TextBox,
    options: TextBoxOptions,
    onOptions: (TextBoxOptions) -> Unit,
    perLanguage: Boolean,
    lowerThird: Boolean,
    boxPaths: List<String> = emptyList(),
    optionPaths: List<String> = emptyList(),
    /** Whether the page has margins for its boxes to be measured inside -- the single-form pages do not. */
    offerArea: Boolean = true,
) {
    SettingsSwitchRow(
        stringResource(Res.string.profile_box),
        box.enabled,
        { on -> onBox(if (on && box == TextBox()) startBox.copy(enabled = true) else box.copy(enabled = on)) },
        sub = stringResource(Res.string.profile_box_sub),
        paths = boxPaths,
    )
    if (!box.enabled) return
    SettingsRow(stringResource(Res.string.profile_box_vertical), paths = boxPaths) {
        RowSegmented(
            options = listOf(
                RowOption(Constants.TOP, stringResource(Res.string.top)),
                RowOption(Constants.MIDDLE, stringResource(Res.string.middle)),
                RowOption(Constants.BOTTOM, stringResource(Res.string.bottom)),
            ),
            selected = box.vertical,
            onSelect = { onBox(box.copy(vertical = it)) },
        )
    }
    SettingsRow(stringResource(Res.string.profile_box_overflow), paths = boxPaths) {
        RowSegmented(
            options = listOf(
                RowOption(TextBoxOverflow.SHRINK, stringResource(Res.string.profile_box_overflow_shrink)),
                RowOption(TextBoxOverflow.CUT, stringResource(Res.string.profile_box_overflow_cut)),
                RowOption(TextBoxOverflow.SPILL, stringResource(Res.string.profile_box_overflow_spill)),
            ),
            selected = box.overflow,
            onSelect = { onBox(box.copy(overflow = it)) },
        )
    }
    SettingsRow(stringResource(Res.string.profile_box_place), advanced = true, paths = boxPaths) {
        BoxRectFields(box, onBox)
    }
    if (box.overflow == TextBoxOverflow.SHRINK) {
        SettingsRow(stringResource(Res.string.customize_theme_text_size), advanced = true, paths = boxPaths) {
            RowSegmented(
                options = listOf(
                    RowOption(false, stringResource(Res.string.profile_box_size_up_to)),
                    RowOption(true, stringResource(Res.string.profile_box_size_fill)),
                ),
                selected = box.fill,
                onSelect = { onBox(box.copy(fill = it)) },
            )
        }
    }
    TextBoxOptionRows(options, onOptions, BoxOptionOffers(perLanguage, lowerThird, offerArea), optionPaths)
}

/** Which of the page-wide box options a page offers. */
private data class BoxOptionOffers(val perLanguage: Boolean, val lowerThird: Boolean, val area: Boolean)

/** X, Y, width and height, four small fields in percent -- the box's exact rectangle. */
@Composable
private fun BoxRectFields(box: TextBox, onBox: (TextBox) -> Unit) {
    val percent = stringResource(Res.string.percent_suffix)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RowNumberField(
            box.xPercent.roundToInt(),
            { onBox(box.copy(xPercent = it.toFloat())) },
            BOX_PERCENT_RANGE,
            unit = percent,
            caption = stringResource(Res.string.profile_box_x),
            width = BOX_FIELD,
        )
        RowNumberField(
            box.yPercent.roundToInt(),
            { onBox(box.copy(yPercent = it.toFloat())) },
            BOX_PERCENT_RANGE,
            unit = percent,
            caption = stringResource(Res.string.profile_box_y),
            width = BOX_FIELD,
        )
        RowNumberField(
            box.widthPercent.roundToInt(),
            { onBox(box.copy(widthPercent = it.toFloat())) },
            SIZE_RANGE,
            unit = percent,
            caption = stringResource(Res.string.profile_box_width),
            width = BOX_FIELD,
        )
        RowNumberField(
            box.heightPercent.roundToInt(),
            { onBox(box.copy(heightPercent = it.toFloat())) },
            SIZE_RANGE,
            unit = percent,
            caption = stringResource(Res.string.profile_box_height),
            width = BOX_FIELD,
        )
    }
}

/** How every box on the page behaves -- Advanced, shown under whichever item is boxed. */
@Composable
private fun TextBoxOptionRows(
    options: TextBoxOptions,
    onOptions: (TextBoxOptions) -> Unit,
    offers: BoxOptionOffers,
    paths: List<String>,
) {
    if (offers.lowerThird) {
        SettingsRow(stringResource(Res.string.profile_box_band), advanced = true, paths = paths) {
            RowSegmented(
                options = listOf(
                    RowOption(false, stringResource(Res.string.profile_box_band_band)),
                    RowOption(true, stringResource(Res.string.profile_box_band_screen)),
                ),
                selected = options.lowerThirdWholeScreen,
                onSelect = { onOptions(options.copy(lowerThirdWholeScreen = it)) },
            )
        }
    }
    if (offers.area) SettingsRow(stringResource(Res.string.profile_box_area), advanced = true, paths = paths) {
        RowSegmented(
            options = listOf(
                RowOption(false, stringResource(Res.string.profile_box_area_screen)),
                RowOption(true, stringResource(Res.string.profile_box_area_margins)),
            ),
            selected = options.insideMargins,
            onSelect = { onOptions(options.copy(insideMargins = it)) },
        )
    }
    if (offers.perLanguage) {
        SettingsRow(stringResource(Res.string.profile_box_languages), advanced = true, paths = paths) {
            RowSegmented(
                options = listOf(
                    RowOption(false, stringResource(Res.string.profile_box_languages_own)),
                    RowOption(true, stringResource(Res.string.profile_box_languages_shared)),
                ),
                selected = options.sharedLanguageBox,
                onSelect = { onOptions(options.copy(sharedLanguageBox = it)) },
            )
        }
    }
    SettingsSwitchRow(
        stringResource(Res.string.profile_box_keep_clear),
        options.keepClear,
        { onOptions(options.copy(keepClear = it)) },
        sub = stringResource(Res.string.profile_box_keep_clear_sub),
        advanced = true,
        paths = paths,
    )
    SettingsSwitchRow(
        stringResource(Res.string.profile_box_snap),
        options.snap,
        { onOptions(options.copy(snap = it)) },
        advanced = true,
        paths = paths,
    )
}
