package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.auto_fit
import churchpresenter.composeapp.generated.resources.bible_alignment
import churchpresenter.composeapp.generated.resources.bible_font
import churchpresenter.composeapp.generated.resources.bible_letter_spacing
import churchpresenter.composeapp.generated.resources.bible_reset_element
import churchpresenter.composeapp.generated.resources.bible_size
import churchpresenter.composeapp.generated.resources.bible_text_transform
import churchpresenter.composeapp.generated.resources.bible_text_transform_capitalize
import churchpresenter.composeapp.generated.resources.bible_text_transform_lowercase
import churchpresenter.composeapp.generated.resources.bible_text_transform_uppercase
import churchpresenter.composeapp.generated.resources.bible_word_spacing
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.position
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
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

private const val FONT_SIZE_MIN = 8
private const val FONT_SIZE_MAX = 150
private const val LETTER_SPACING_MIN = -10f
private const val LETTER_SPACING_MAX = 30f
private const val WORD_SPACING_MIN = 0f
private const val WORD_SPACING_MAX = 40f
private const val SPACING_WEIGHT = 1f

private val COLOR_SWATCH_WIDTH = 104.dp
private val FACE_BUTTON_SIZE = 26.dp
private val FONT_FIELD_WIDTH = 190.dp
private val TRANSFORM_BUTTON_WIDTH = 96.dp
private val CONTROL_GAP = 12.dp

/** Enough for the checkbox and the word "Shadow" beside it, so the label never wraps to one letter a line. */
private val SHADOW_ROW_MIN_WIDTH = 96.dp

/** The size box while Auto is deciding it: readable, but plainly not what is in charge. */
private const val AUTO_FIT_DIMMED = 0.45f

private val SIZE_FIELD_WIDTH = 76.dp

/**
 * The panel under the preview: how the selected element is drawn on the selected output.
 *
 * One set of controls rather than the four scrolling columns this replaced. Which of the ten stored
 * profiles they point at is chosen by the element tabs and the Full Screen / Lower Third switch
 * above; [style] is that profile, read through [elementStyle], and [onStyleChange] writes the
 * edited copy back through [withElementStyle].
 *
 * The grid adapts to what the element actually stores: only the number and the title sit above or
 * below the lyrics, and only the lyrics, look-ahead and next-section lines have a size the presenter
 * can fit for them. A control for a profile with nowhere to keep the value is absent rather than
 * present and ineffective.
 *
 * No chord colour here. Chords are for the stage monitor, not for what the congregation reads, so
 * neither of the outputs this panel styles ever draws one.
 */
@Composable
internal fun SongTypographyPanel(
    element: SongStyleElement,
    style: SongElementStyle,
    onStyleChange: (SongElementStyle) -> Unit,
    onReset: () -> Unit,
    availableFonts: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
            verticalAlignment = Alignment.Top,
        ) {
            SongColorControl(style, onStyleChange)
            SongFontControl(style, onStyleChange, availableFonts, Modifier.width(FONT_FIELD_WIDTH))
            SongSizeControl(element, style, onStyleChange)
            Spacer(Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
            verticalAlignment = Alignment.Top,
        ) {
            // Alignment sits here rather than beside Size: with the Auto box in that row as well,
            // the four cells came to more than the pane and it was clipped off the end.
            SongAlignmentControl(style, onStyleChange)
            if (element.hasPosition) {
                ControlColumn(stringResource(Res.string.position)) {
                    PositionButtons(
                        selectedPosition = style.position,
                        onPositionChange = { onStyleChange(style.copy(position = it)) },
                        aboveValue = Constants.ABOVE_VERSE,
                        belowValue = Constants.BELOW_VERSE,
                    )
                }
            }
            // Explicitly keyed, because the control above them comes and goes with the element. A
            // composable's identity is its call-site position, so without a key of their own the two
            // sliders shift a slot as the position control appears and each inherits the composition
            // state of whatever previously stood where it now stands.
            key("songLetterSpacing") {
                SongSpacingControl(
                    label = stringResource(Res.string.bible_letter_spacing),
                    value = style.letterSpacing,
                    range = LETTER_SPACING_MIN..LETTER_SPACING_MAX,
                    onValueChange = { onStyleChange(style.copy(letterSpacing = it)) },
                    modifier = Modifier.weight(SPACING_WEIGHT),
                )
            }
            key("songWordSpacing") {
                SongSpacingControl(
                    label = stringResource(Res.string.bible_word_spacing),
                    value = style.wordSpacing,
                    range = WORD_SPACING_MIN..WORD_SPACING_MAX,
                    onValueChange = { onStyleChange(style.copy(wordSpacing = it)) },
                    modifier = Modifier.weight(SPACING_WEIGHT),
                )
            }
        }
        // Bottom-aligned: the transform and chord cells carry a caption above their controls and the
        // shadow checkbox does not, so aligning the tops would leave it level with a caption.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
            verticalAlignment = Alignment.Bottom,
        ) {
            SongTransformControl(style, onStyleChange)
            // Weighted, and the Reset button unweighted beside it: ShadowDetailRow fills the width
            // it is given, and given the row's own constraints it took all of it -- Compose measures
            // unweighted children against the full width first -- which squeezed the Reset button to
            // zero the moment the shadow checkbox was ticked. The Bible panel already bounds its
            // copy this way; a Spacer cannot substitute, because the greedy child is measured first.
            SongShadowControl(style, onStyleChange, Modifier.weight(1f))
            TextButton(
                onClick = onReset,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Text(stringResource(Res.string.bible_reset_element), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SongColorControl(
    style: SongElementStyle,
    onStyleChange: (SongElementStyle) -> Unit,
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
            backdrop = style.backdrop,
            onBackdropChange = { onStyleChange(style.copy(backdrop = it)) },
            buttonSize = FACE_BUTTON_SIZE,
        )
    }
}

@Composable
private fun SongFontControl(
    style: SongElementStyle,
    onStyleChange: (SongElementStyle) -> Unit,
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
private fun SongSizeControl(
    element: SongStyleElement,
    style: SongElementStyle,
    onStyleChange: (SongElementStyle) -> Unit,
    modifier: Modifier = Modifier,
) = ControlColumn(stringResource(Res.string.bible_size), modifier, labelInsideControl = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        // Dimmed rather than hidden while Auto is on: the number is still the ceiling the fit works
        // down from, which is worth being able to read even when it is not what lands on screen.
        NumberSettingsTextField(
            // Fixed rather than intrinsic: without Auto beside it the field is the row's only child
            // and stretched to whatever the cell was given, pushing Alignment off the end of the row.
            modifier = Modifier
                .width(SIZE_FIELD_WIDTH)
                .alpha(if (element.hasAutoFit && style.autoFit) AUTO_FIT_DIMMED else 1f),
            label = stringResource(Res.string.bible_size),
            initialText = style.fontSize,
            onValueChange = { onStyleChange(style.copy(fontSize = it)) },
            range = FONT_SIZE_MIN..FONT_SIZE_MAX,
        )
        // A stored toggle, not the Bible tab's one-shot button: the presenter re-fits every slide
        // while it is on, so there is nothing to press and nothing that needs a live verse.
        if (element.hasAutoFit) {
            LabeledCheckbox(
                checked = style.autoFit,
                onCheckedChange = { onStyleChange(style.copy(autoFit = it)) },
                label = stringResource(Res.string.auto_fit),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SongAlignmentControl(
    style: SongElementStyle,
    onStyleChange: (SongElementStyle) -> Unit,
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

/** Letter and word spacing share a shape: a slider reading out whole pixels at the configured size. */
@Composable
private fun SongSpacingControl(
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
private fun SongTransformControl(
    style: SongElementStyle,
    onStyleChange: (SongElementStyle) -> Unit,
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

/** Shadow: a checkbox with its three settings folding out beside it, on the transform's own row. */
@Composable
private fun SongShadowControl(
    style: SongElementStyle,
    onStyleChange: (SongElementStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        // The detail controls fold out beside the checkbox rather than under it, so the row is held
        // at their height whether they are showing or not -- see [ShadowDetailRowHeight].
        modifier = modifier.widthIn(min = SHADOW_ROW_MIN_WIDTH).heightIn(min = ShadowDetailRowHeight),
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
