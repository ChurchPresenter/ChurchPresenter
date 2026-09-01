package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.dictionary_settings_transitions
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.fade_out
import churchpresenter.composeapp.generated.resources.fade_in
import churchpresenter.composeapp.generated.resources.shadow_opacity
import churchpresenter.composeapp.generated.resources.shadow_size
import churchpresenter.composeapp.generated.resources.shadow_settings
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.customize_group_margins
import churchpresenter.composeapp.generated.resources.customize_group_typography
import churchpresenter.composeapp.generated.resources.customize_letter_spacing
import churchpresenter.composeapp.generated.resources.customize_text_transform
import churchpresenter.composeapp.generated.resources.customize_transform_capitalize
import churchpresenter.composeapp.generated.resources.customize_transform_lowercase
import churchpresenter.composeapp.generated.resources.customize_transform_none
import churchpresenter.composeapp.generated.resources.customize_transform_uppercase
import churchpresenter.composeapp.generated.resources.customize_word_spacing
import churchpresenter.composeapp.generated.resources.every_page
import churchpresenter.composeapp.generated.resources.first_page
import churchpresenter.composeapp.generated.resources.none
import org.churchpresenter.settings.textTransformOptions
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The shared parts of the Customize dialog's per-output panes, built from [CustomizeForm]'s
 * controls. Each pane lives in its own file beside this one.
 *
 * The panes carry the same appearance settings their tabs do — typography, colour, shadow,
 * alignment, look-ahead, transitions and backgrounds — so an output can be given any look the
 * global tab can give, without going to the global tab to do it. What stays out is what has no
 * per-output meaning: the stock-photo API keys, the quick-backgrounds tray, and the library and
 * browse options. Those remain on their own tabs, where one value serves every output.
 *
 * Each pane reads the profile the output actually draws with — full-screen or lower-third — from
 * [LocalOutputStyleScope], so one set of controls edits whichever half applies.
 */

internal val BAND_RANGE = 5..100
internal val BLUR_RANGE = 0..100
internal val SOURCE_FIELD_WIDTH = 260.dp
internal val SHADOW_RANGE = 0..400
internal val PERCENT_RANGE = 0..100
internal val DURATION_RANGE = 0..5000
internal const val SPACING_RANGE_MIN = -20
internal const val SPACING_RANGE_MAX = 100
internal val PANE_PADDING = 16.dp

@Composable
internal fun PaneScaffold(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PANE_PADDING, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** The transform picker, labelled by example — AA, aa, Aa — rather than by name. */
@Composable
internal fun transformOptions(): List<Pair<String, String>> {
    val labels = listOf(
        stringResource(Res.string.customize_transform_none),
        stringResource(Res.string.customize_transform_uppercase),
        stringResource(Res.string.customize_transform_lowercase),
        stringResource(Res.string.customize_transform_capitalize),
    )
    return textTransformOptions().zip(labels)
}

/**
 * Shadow, and the three settings that only mean anything while it is on.
 *
 * Folded away when it is off rather than dimmed: three rows reading nothing is worse than three
 * rows absent, and the checkbox above them says why they are gone.
 */
@Composable
internal fun ShadowRows(
    shadow: Boolean,
    shadowColor: String,
    shadowSize: Int,
    shadowOpacity: Int,
    onShadow: (Boolean) -> Unit,
    onShadowColor: (String) -> Unit,
    onShadowSize: (Int) -> Unit,
    onShadowOpacity: (Int) -> Unit,
) {
    CustomizeRow(stringResource(Res.string.shadow_settings), labelInsideControl = true) {
        ToggleControl(stringResource(Res.string.shadow_settings), shadow, onShadow)

    }
    if (shadow) {
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(stringResource(Res.string.color), shadowColor, onShadowColor)
        }
        CustomizeRow(stringResource(Res.string.shadow_size), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.shadow_size),
                value = shadowSize,
                onValueChange = onShadowSize,
                range = SHADOW_RANGE,
            )
        }
        CustomizeRow(stringResource(Res.string.shadow_opacity), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.shadow_opacity),
                value = shadowOpacity,
                onValueChange = onShadowOpacity,
                range = PERCENT_RANGE,
            )
        }
    }
}

/** Fade in, fade out and how long they take — the transition block every content type carries. */
@Composable
internal fun TransitionsGroup(
    fadeIn: Boolean,
    fadeOut: Boolean,
    durationMs: Float,
    onFadeIn: (Boolean) -> Unit,
    onFadeOut: (Boolean) -> Unit,
    onDuration: (Float) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.dictionary_settings_transitions)) {
        CustomizeRow(stringResource(Res.string.fade_in), labelInsideControl = true) {
            ToggleControl(stringResource(Res.string.fade_in), fadeIn, onFadeIn)
        }
        CustomizeRow(stringResource(Res.string.fade_out), labelInsideControl = true) {
            ToggleControl(stringResource(Res.string.fade_out), fadeOut, onFadeOut)
        }
        CustomizeRow(stringResource(Res.string.transition_duration), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.transition_duration),
                value = durationMs.toInt(),
                onValueChange = { v -> onDuration(v.toFloat()) },
                range = DURATION_RANGE,
            )
        }
    }
}

/** Letter spacing, word spacing and case — the three rows every text pane carries. */
@Composable
internal fun TypographyGroup(
    letterSpacing: Int,
    wordSpacing: Int,
    transform: String,
    onLetterSpacing: (Int) -> Unit,
    onWordSpacing: (Int) -> Unit,
    onTransform: (String) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.customize_group_typography)) {
        val pixels = stringResource(Res.string.pixels_short)
        CustomizeRow(stringResource(Res.string.customize_letter_spacing)) {
            SliderControl(letterSpacing, onLetterSpacing, SPACING_RANGE_MIN..SPACING_RANGE_MAX, pixels)
        }
        CustomizeRow(stringResource(Res.string.customize_word_spacing)) {
            SliderControl(wordSpacing, onWordSpacing, SPACING_RANGE_MIN..SPACING_RANGE_MAX, pixels)
        }
        CustomizeRow(stringResource(Res.string.customize_text_transform)) {
            ChoiceControl(transformOptions(), transform, onTransform)
        }
    }
}

// ── Bible ───────────────────────────────────────────────────────────────────────────────────────

// ── Song ────────────────────────────────────────────────────────────────────────────────────────

// ── Dictionary ──────────────────────────────────────────────────────────────────────────────────

// ── Background ──────────────────────────────────────────────────────────────────────────────────

// ── Shared bits ─────────────────────────────────────────────────────────────────────────────────

/**
 * The four margins, one field each — the same four the Bible and Song tabs offer.
 *
 * They were a Top/bottom and a Left/right pair, each writing the same number into both sides, so
 * an output could not be nudged down off a bezel or in from one edge without moving the opposite
 * margin with it. The settings have always held four values; only this dialog collapsed them.
 */
@Composable
internal fun MarginsGroup(
    top: Int,
    bottom: Int,
    left: Int,
    right: Int,
    onTop: (Int) -> Unit,
    onBottom: (Int) -> Unit,
    onLeft: (Int) -> Unit,
    onRight: (Int) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.customize_group_margins)) {
        CustomizeRow(stringResource(Res.string.top), labelInsideControl = true) {
            NumberControl(stringResource(Res.string.top), top, onTop, MARGIN_RANGE)
        }
        CustomizeRow(stringResource(Res.string.bottom), labelInsideControl = true) {
            NumberControl(stringResource(Res.string.bottom), bottom, onBottom, MARGIN_RANGE)
        }
        CustomizeRow(stringResource(Res.string.left), labelInsideControl = true) {
            NumberControl(stringResource(Res.string.left), left, onLeft, MARGIN_RANGE)
        }
        CustomizeRow(stringResource(Res.string.right), labelInsideControl = true) {
            NumberControl(stringResource(Res.string.right), right, onRight, MARGIN_RANGE)
        }
    }
}

/** None / first page / every page, the vocabulary the title and number already use. */
@Composable
internal fun showOptions(): List<Pair<String, String>> = listOf(
    Constants.NONE to stringResource(Res.string.none),
    Constants.FIRST_PAGE to stringResource(Res.string.first_page),
    Constants.EVERY_PAGE to stringResource(Res.string.every_page),
)

internal val FONT_SIZE_RANGE = 8..150
internal val MARGIN_RANGE = 0..500
internal const val REFERENCE_ABOVE = "Above"
internal const val REFERENCE_BELOW = "Below"
internal const val SHADOW_GLYPH = "S"
