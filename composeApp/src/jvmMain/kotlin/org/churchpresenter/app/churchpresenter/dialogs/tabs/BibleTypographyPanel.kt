package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.auto_fit
import churchpresenter.composeapp.generated.resources.bible_letter_spacing
import churchpresenter.composeapp.generated.resources.bible_alignment
import churchpresenter.composeapp.generated.resources.bible_font
import churchpresenter.composeapp.generated.resources.bible_reference_position
import churchpresenter.composeapp.generated.resources.bible_size
import churchpresenter.composeapp.generated.resources.bible_text_transform
import churchpresenter.composeapp.generated.resources.bible_text_transform_capitalize
import churchpresenter.composeapp.generated.resources.bible_text_transform_lowercase
import churchpresenter.composeapp.generated.resources.bible_text_transform_uppercase
import churchpresenter.composeapp.generated.resources.bible_word_spacing
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.shadow_settings
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.PositionButtons
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRowHeight
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

private const val FONT_SIZE_MIN = 8
private const val FONT_SIZE_MAX = 150
private const val LETTER_SPACING_MIN = -10f
private const val LETTER_SPACING_MAX = 30f
private const val WORD_SPACING_MIN = 0f
private const val WORD_SPACING_MAX = 40f



private val CONTROL_GAP = 12.dp

/** Wide enough for the longest face name that reads at this size, and no wider. */
private val FONT_FIELD_WIDTH = 190.dp

/** The colour swatch, sized so the four face buttons beside it always fit the cell. */
private val COLOR_SWATCH_WIDTH = 104.dp
private val FACE_BUTTON_SIZE = 26.dp

/** The two spacing sliders share the second row evenly, whatever the reference position leaves. */
private const val SPACING_WEIGHT = 1f

private val TRANSFORM_BUTTON_WIDTH = 96.dp

/**
 * The panel under the preview: everything about how the selected translation draws the selected
 * element on the selected output.
 *
 * One set of controls rather than the four parallel copies this replaced. The element tabs and the
 * Full Screen / Lower Third switch above choose which of the stored profiles the controls are
 * pointed at; [style] is that profile, read through [elementStyle], and [onStyleChange] writes the
 * edited copy back through [withElementStyle].
 */
@Composable
internal fun BibleTypographyPanel(
    translation: BibleTranslationSettings,
    moduleTitle: String,
    element: BibleStyleElement,
    onElementChange: (BibleStyleElement) -> Unit,
    style: BibleElementStyle,
    onStyleChange: (BibleElementStyle) -> Unit,
    onTranslationChange: ((BibleTranslationSettings) -> BibleTranslationSettings) -> Unit,
    onReset: () -> Unit,
    availableFonts: List<String>,
    autoFit: (() -> Unit)?,
    autoFitEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElementHeaderRow(
            translation = translation,
            moduleTitle = moduleTitle,
            element = element,
            onElementChange = onElementChange,
            onTranslationChange = onTranslationChange,
            onReset = onReset,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
            verticalAlignment = Alignment.Top,
        ) {
            // Every cell but the font dropdown holds fixed-size controls, so they take their own
            // width and the dropdown absorbs whatever is left. Sharing the row out by weight
            // instead gave the colour cell less than its swatch and four buttons needed, and
            // clipped the underline and strikethrough buttons clean off.
            ColorControl(style, onStyleChange)
            FontControl(style, onStyleChange, availableFonts, Modifier.width(FONT_FIELD_WIDTH))
            SizeControl(style, onStyleChange, autoFit, autoFitEnabled)
            // The slack goes here rather than into the font field, which at a weight grew to half
            // the panel to show a name no longer than "Times New Roman".
            Spacer(Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
            verticalAlignment = Alignment.Top,
        ) {
            // Alignment sits here rather than beside Size, matching the Song tab: with the Auto
            // button in that row as well, the four cells came to more than the pane.
            AlignmentControl(style, onStyleChange)
            // Only the reference has anywhere to sit relative to the verse, so the control is absent
            // rather than disabled while the verse is being edited.
            if (element == BibleStyleElement.REFERENCE) {
                PositionControl(style, onStyleChange)
            }
            // Explicitly keyed, because the control above them comes and goes with the element. A
            // composable's identity is its call-site *position*, so without a key of their own the
            // two sliders shift a slot as the reference position appears, and each one inherits the
            // composition state of whatever previously stood where it now stands -- which is what
            // left them looking frozen while the value behind them moved.
            key("letterSpacing") {
                SpacingControl(
                    label = stringResource(Res.string.bible_letter_spacing),
                    value = style.letterSpacing,
                    range = LETTER_SPACING_MIN..LETTER_SPACING_MAX,
                    onValueChange = { onStyleChange(style.copy(letterSpacing = it)) },
                    modifier = Modifier.weight(SPACING_WEIGHT),
                )
            }
            key("wordSpacing") {
                SpacingControl(
                    label = stringResource(Res.string.bible_word_spacing),
                    value = style.wordSpacing,
                    range = WORD_SPACING_MIN..WORD_SPACING_MAX,
                    onValueChange = { onStyleChange(style.copy(wordSpacing = it)) },
                    modifier = Modifier.weight(SPACING_WEIGHT),
                )
            }
        }
        // Bottom-aligned, not top: the transform cell carries a caption above its buttons and the
        // shadow controls do not, so aligning the tops left the shadow checkbox floating level with
        // that caption instead of with the buttons it sits beside.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
            verticalAlignment = Alignment.Bottom,
        ) {
            TransformControl(style, onStyleChange)
            ShadowControl(style, onStyleChange, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColorControl(
    style: BibleElementStyle,
    onStyleChange: (BibleElementStyle) -> Unit,
    modifier: Modifier = Modifier,
) = ControlColumn(stringResource(Res.string.color), modifier, labelInsideControl = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ColorPickerField(
            label = stringResource(Res.string.color),
            color = style.color,
            onColorChange = { onStyleChange(style.copy(color = it)) },
            modifier = Modifier.width(COLOR_SWATCH_WIDTH),
        )
        // The shadow button is off here: shadow has a labelled control of its own below, and two
        // buttons both reading "S" in one row would be indistinguishable.
        TextStyleButtons(
            bold = style.bold,
            italic = style.italic,
            underline = style.underline,
            shadow = style.shadow,
            onBoldChange = { onStyleChange(style.copy(bold = it)) },
            onItalicChange = { onStyleChange(style.copy(italic = it)) },
            onUnderlineChange = { onStyleChange(style.copy(underline = it)) },
            onShadowChange = { onStyleChange(style.copy(shadow = it)) },
            strikethrough = style.strikethrough,
            onStrikethroughChange = { onStyleChange(style.copy(strikethrough = it)) },
            showShadow = false,
            buttonSize = FACE_BUTTON_SIZE,
        )
    }
}

@Composable
private fun FontControl(
    style: BibleElementStyle,
    onStyleChange: (BibleElementStyle) -> Unit,
    availableFonts: List<String>,
    modifier: Modifier = Modifier,
) = ControlColumn(stringResource(Res.string.bible_font), modifier, labelInsideControl = true) {
    FontSettingsDropdown(
        label = stringResource(Res.string.bible_font),
        value = style.fontType,
        fonts = availableFonts,
        onValueChange = { onStyleChange(style.copy(fontType = it)) },
        modifier = Modifier.fillMaxWidth(),
        fillWidth = true,
    )
}

@Composable
private fun SizeControl(
    style: BibleElementStyle,
    onStyleChange: (BibleElementStyle) -> Unit,
    autoFit: (() -> Unit)?,
    autoFitEnabled: Boolean,
    modifier: Modifier = Modifier,
) = ControlColumn(stringResource(Res.string.bible_size), modifier, labelInsideControl = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberSettingsTextField(
            label = stringResource(Res.string.bible_size),
            initialText = style.fontSize,
            onValueChange = { onStyleChange(style.copy(fontSize = it)) },
            range = FONT_SIZE_MIN..FONT_SIZE_MAX,
        )
        // Still the one-shot measurement it has always been: it reads the verse that is live right
        // now and writes a size. Disabled rather than hidden when there is nothing live to measure.
        if (autoFit != null) {
            TextButton(
                onClick = autoFit,
                enabled = autoFitEnabled,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AlignmentControl(
    style: BibleElementStyle,
    onStyleChange: (BibleElementStyle) -> Unit,
    modifier: Modifier = Modifier,
) = ControlColumn(stringResource(Res.string.bible_alignment), modifier) {
    HorizontalAlignmentButtons(
        selectedAlignment = style.horizontalAlignment,
        onAlignmentChange = { onStyleChange(style.copy(horizontalAlignment = it)) },
        leftValue = Constants.LEFT,
        centerValue = Constants.CENTER,
        rightValue = Constants.RIGHT,
    )
}

@Composable
private fun PositionControl(
    style: BibleElementStyle,
    onStyleChange: (BibleElementStyle) -> Unit,
    modifier: Modifier = Modifier,
) = ControlColumn(stringResource(Res.string.bible_reference_position), modifier) {
    PositionButtons(
        selectedPosition = style.position,
        onPositionChange = { onStyleChange(style.copy(position = it)) },
        aboveValue = Constants.POSITION_ABOVE,
        belowValue = Constants.POSITION_BELOW,
    )
}

/** Letter and word spacing share a shape: a slider reading out whole pixels at the configured size. */
@Composable
private fun SpacingControl(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = ControlColumn(label, modifier) {
    val suffix = stringResource(Res.string.pixels_short)
    SlimSlider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = range,
        modifier = Modifier.fillMaxWidth(),
        trailingLabel = "$value$suffix",
    )
}

@Composable
private fun TransformControl(
    style: BibleElementStyle,
    onStyleChange: (BibleElementStyle) -> Unit,
    modifier: Modifier = Modifier,
) = ControlColumn(stringResource(Res.string.bible_text_transform), modifier) {
    SegmentedButton(
        items = listOf(
            SegmentedButtonItem(Constants.TEXT_TRANSFORM_NONE, stringResource(Res.string.none)),
            SegmentedButtonItem(
                Constants.TEXT_TRANSFORM_UPPERCASE,
                stringResource(Res.string.bible_text_transform_uppercase),
            ),
            SegmentedButtonItem(
                Constants.TEXT_TRANSFORM_LOWERCASE,
                stringResource(Res.string.bible_text_transform_lowercase),
            ),
            SegmentedButtonItem(
                Constants.TEXT_TRANSFORM_CAPITALIZE,
                stringResource(Res.string.bible_text_transform_capitalize),
            ),
        ),
        selectedValue = style.transform,
        onValueChange = { onStyleChange(style.copy(transform = it)) },
        buttonWidth = TRANSFORM_BUTTON_WIDTH,
        buttonHeight = 32.dp,
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
    )
}

/**
 * Shadow: its own block rather than a fifth face button, and one row rather than two.
 *
 * It is the only one of the face toggles carrying three settings behind it, and they sit beside the
 * checkbox on the same line as the text transform above them -- stacked underneath they opened a
 * band of their own that was empty whenever shadow was off.
 *
 * No caption, unlike the rest of the grid: the checkbox already carries the word "Shadow", and a
 * caption above it would put the same label on screen twice.
 */
@Composable
private fun ShadowControl(
    style: BibleElementStyle,
    onStyleChange: (BibleElementStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        // The detail controls fold out beside the checkbox rather than under it, so the row is held
        // at their height whether they are showing or not -- see [ShadowDetailRowHeight].
        modifier = modifier.heightIn(min = ShadowDetailRowHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Bottom, not centre. The controls this row sits beside are bottom-aligned, and the row is
        // held taller than the checkbox by the reservation above -- so centring it left the box
        // floating nine pixels above the buttons next to it whenever the details were folded away.
        verticalAlignment = Alignment.Bottom,
    ) {
        LabeledCheckbox(
            checked = style.shadow,
            onCheckedChange = { onStyleChange(style.copy(shadow = it)) },
            label = stringResource(Res.string.shadow_settings),
            style = MaterialTheme.typography.bodySmall,
        )
        AnimatedVisibility(visible = style.shadow) {
            ShadowDetailRow(
                shadowColor = style.shadowColor,
                shadowSize = style.shadowSize,
                shadowOpacity = style.shadowOpacity,
                onColorChange = { onStyleChange(style.copy(shadowColor = it)) },
                onSizeChange = { onStyleChange(style.copy(shadowSize = it)) },
                onOpacityChange = { onStyleChange(style.copy(shadowOpacity = it)) },
            )
        }
    }
}

