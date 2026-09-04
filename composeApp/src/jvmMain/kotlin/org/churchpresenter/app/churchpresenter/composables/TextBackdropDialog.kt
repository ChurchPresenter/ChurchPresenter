package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.backdrop_border_color
import churchpresenter.composeapp.generated.resources.backdrop_border_padding
import churchpresenter.composeapp.generated.resources.backdrop_border_radius
import churchpresenter.composeapp.generated.resources.backdrop_border_width
import churchpresenter.composeapp.generated.resources.backdrop_fill_color
import churchpresenter.composeapp.generated.resources.backdrop_height_offset
import churchpresenter.composeapp.generated.resources.backdrop_mode_hint
import churchpresenter.composeapp.generated.resources.backdrop_opacity
import churchpresenter.composeapp.generated.resources.backdrop_preset_saved_already
import churchpresenter.composeapp.generated.resources.backdrop_presets
import churchpresenter.composeapp.generated.resources.backdrop_save_preset
import churchpresenter.composeapp.generated.resources.backdrop_saved_look
import churchpresenter.composeapp.generated.resources.backdrop_style
import churchpresenter.composeapp.generated.resources.backdrop_vertical_offset
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.ic_close
import org.churchpresenter.core.models.text.TextBackdrop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val DIALOG_WIDTH = 320.dp
private val OPACITY_FIELD_WIDTH = 104.dp
private val MODE_BUTTON_HEIGHT = 44.dp

/** Breathing room above the swatch and below the caption, inside the button's own border. */
private val MODE_BUTTON_PADDING = 6.dp
private val PRESET_HEIGHT = 32.dp

/** Four across, which is the width the dialog was built for; the rest wrap under them. */
private const val PRESETS_PER_ROW = 4
private val SECTION_PADDING = 12.dp

/** The close button, at the size the rest of the app draws a close icon at rather than the
 *  10dp glyph in a 20dp target this opened with -- which was smaller than anything else in
 *  the dialog and hard to hit. */
private val CLOSE_BUTTON_SIZE = 28.dp
private val CLOSE_ICON_SIZE = 14.dp
private const val SELECTED_FILL_ALPHA = 0.18f

/**
 * Everything that goes behind and around a piece of text, in one dialog.
 *
 * It used to be two — a border button with a border dialog, a line-background button with its own —
 * which made the common case, a plate with an outline on it, two round trips before the result
 * could be seen. Both halves answer one question, so the dialog opens with that question: the Style
 * row picks Off, Fill, Border or Both, and only the chosen halves' fields appear underneath.
 *
 * There is no separate enabled switch. Off is the first option in that row, which is also what the
 * toolbar button toggles, so turning the feature off is the same gesture as choosing a look rather
 * than a switch to find once the dialog is already open.
 *
 * A dialog rather than fields inline: these settings belong to nine different panels, several of
 * which are a 48dp toolbar or an already-full properties column, and none of them has room for two
 * colour pickers and seven numbers.
 */
@Composable
fun TextBackdropDialog(
    backdrop: TextBackdrop,
    onChange: (TextBackdrop) -> Unit,
    onDismiss: () -> Unit,
) {
    val mode = backdrop.mode
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.width(DIALOG_WIDTH),
        ) {
            Column {
                Column(Modifier.padding(SECTION_PADDING)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(stringResource(Res.string.backdrop_style), Modifier.weight(1f))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(CLOSE_BUTTON_SIZE)) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = stringResource(Res.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(CLOSE_ICON_SIZE),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    BackdropModeRow(backdrop) { onChange(backdrop.withMode(it)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (mode == TextBackdropMode.OFF) {
                    Text(
                        text = stringResource(Res.string.backdrop_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(SECTION_PADDING),
                    )
                } else {
                    Column(Modifier.padding(SECTION_PADDING)) {
                        SectionLabel(stringResource(Res.string.backdrop_presets))
                        Spacer(Modifier.height(7.dp))
                        BackdropPresetRow(current = backdrop, onPick = onChange)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // No height cap: the dialog is as tall as the fields the chosen mode has, so
                    // Both shows its last row instead of clipping it. The scroll is what saves a
                    // short screen, and does nothing at all when the content already fits.
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(SECTION_PADDING),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (mode.drawsFill) FillFields(backdrop, onChange)
                        if (mode.drawsBorder) BorderFields(backdrop, onChange)
                    }
                }
            }
        }
    }
}

/** The four-way choice that replaced the pair of toggles, drawn as one joined strip. */
@Composable
private fun BackdropModeRow(backdrop: TextBackdrop, onModeChange: (TextBackdropMode) -> Unit) {
    val modes = TextBackdropMode.entries
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = OUTLINE_ALPHA)
    Row(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            val selected = backdrop.mode == mode
            val shape = segmentShape(index, modes.size)
            val ink = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    // Grows rather than clips, and keeps a gap of its own above the swatch. Fixed at
                    // 44dp the chip and its caption came to within a hair of the height, so the
                    // swatch's own border sat on the button's top edge and read as one line.
                    .heightIn(min = MODE_BUTTON_HEIGHT)
                    .background(
                        if (selected) {
                            accent.copy(alpha = SELECTED_FILL_ALPHA)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape,
                    )
                    .border(1.dp, if (selected) accent else outline, shape)
                    .clickable { onModeChange(mode) }
                    .padding(vertical = MODE_BUTTON_PADDING),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TextBackdropChip(
                    // Each choice previews itself with the colours already set, so switching
                    // between Fill and Both shows what the switch will actually produce.
                    backdrop = backdrop.withMode(mode),
                    emptyOutline = outline,
                    emptyInk = ink,
                    modifier = Modifier.width(26.dp).height(15.dp),
                    label = null,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(mode.label),
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The presets row: the operator's own saved looks first, then whichever built-ins still fit.
 *
 * Flowing rather than a fixed row of four. The list grows as looks are saved -- up to
 * [SavedTextBackdrops.MAX] -- and a `Row` would squeeze eight swatches into the width of four
 * rather than wrapping onto a second line.
 *
 * The Save button underneath is what puts the current look in the list. It is deliberately explicit
 * rather than a history the dialog collects on its own: half of what passes through this dialog is
 * a look being tuned or abandoned, and an automatic list would push the looks somebody meant to
 * keep off the end with the ones they were only trying. Saving one already stored would move it to
 * the front and nothing else, so the button says so and does nothing instead.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun BackdropPresetRow(current: TextBackdrop, onPick: (TextBackdrop) -> Unit) {
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = OUTLINE_ALPHA)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val saved = SavedTextBackdrops.looks
    val choices = backdropChoices(saved.toList())
    val savedName = stringResource(Res.string.backdrop_saved_look)
    Column {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            maxItemsInEachRow = PRESETS_PER_ROW,
        ) {
            choices.forEach { choice ->
                val name = choice.label?.let { stringResource(it) } ?: savedName
                TooltipArea(
                    tooltip = { BackdropTooltip(name) },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter),
                    // Weighted against a fixed row of four rather than filling the line it lands on,
                    // so a second row holding one swatch draws it the size of the four above it.
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PRESET_HEIGHT)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))
                            .border(1.dp, outline, RoundedCornerShape(7.dp))
                            .clickable { onPick(choice.apply(current)) }
                            .padding(3.dp),
                    ) {
                        TextBackdropChip(
                            backdrop = choice.preview,
                            emptyOutline = outline,
                            emptyInk = ink,
                            modifier = Modifier.fillMaxSize(),
                            label = "Aa",
                        )
                    }
                }
            }
            // Keeps the last line's swatches at the width of a full row instead of stretching them
            // across it -- `weight` divides what is on the line, not what the row can hold.
            repeat(emptyTrailingSlots(choices.size)) { Spacer(Modifier.weight(1f)) }
        }
        val alreadySaved = saved.firstOrNull() == current
        Spacer(Modifier.height(2.dp))
        TextButton(
            onClick = { SavedTextBackdrops.add(current) },
            enabled = !alreadySaved,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(28.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(
                text = stringResource(
                    if (alreadySaved) Res.string.backdrop_preset_saved_already else Res.string.backdrop_save_preset,
                ),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** How many slots the last line is short of a full row, so its swatches keep the row's width. */
private fun emptyTrailingSlots(count: Int): Int =
    (PRESETS_PER_ROW - count % PRESETS_PER_ROW) % PRESETS_PER_ROW

@Composable
private fun FillFields(backdrop: TextBackdrop, onChange: (TextBackdrop) -> Unit) {
    FieldGroup(stringResource(TextBackdropMode.FILL.label)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ColorPickerField(
                label = stringResource(Res.string.backdrop_fill_color),
                color = backdrop.lineBackgroundColor,
                onColorChange = { onChange(backdrop.copy(lineBackgroundColor = it)) },
                modifier = Modifier.weight(1f),
            )
            BackdropNumberField(
                label = stringResource(Res.string.backdrop_opacity),
                value = backdrop.lineBackgroundOpacity,
                range = TextBackdrop.OPACITY_RANGE,
                modifier = Modifier.width(OPACITY_FIELD_WIDTH),
            ) { onChange(backdrop.copy(lineBackgroundOpacity = it)) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BackdropNumberField(
                label = stringResource(Res.string.backdrop_height_offset),
                value = backdrop.lineBackgroundHeight,
                range = TextBackdrop.HEIGHT_RANGE,
                modifier = Modifier.weight(1f),
            ) { onChange(backdrop.copy(lineBackgroundHeight = it)) }
            BackdropNumberField(
                label = stringResource(Res.string.backdrop_vertical_offset),
                value = backdrop.lineBackgroundOffset,
                range = TextBackdrop.OFFSET_RANGE,
                modifier = Modifier.weight(1f),
            ) { onChange(backdrop.copy(lineBackgroundOffset = it)) }
        }
    }
}

@Composable
private fun BorderFields(backdrop: TextBackdrop, onChange: (TextBackdrop) -> Unit) {
    FieldGroup(stringResource(TextBackdropMode.BORDER.label)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ColorPickerField(
                label = stringResource(Res.string.backdrop_border_color),
                color = backdrop.borderColor,
                onColorChange = { onChange(backdrop.copy(borderColor = it)) },
                modifier = Modifier.weight(1f),
            )
            BackdropNumberField(
                label = stringResource(Res.string.backdrop_opacity),
                value = backdrop.borderOpacity,
                range = TextBackdrop.OPACITY_RANGE,
                modifier = Modifier.width(OPACITY_FIELD_WIDTH),
            ) { onChange(backdrop.copy(borderOpacity = it)) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BackdropNumberField(
                label = stringResource(Res.string.backdrop_border_width),
                value = backdrop.borderWidth,
                range = TextBackdrop.WIDTH_RANGE,
                modifier = Modifier.weight(1f),
            ) { onChange(backdrop.copy(borderWidth = it)) }
            BackdropNumberField(
                label = stringResource(Res.string.backdrop_border_padding),
                value = backdrop.borderPadding,
                range = TextBackdrop.PADDING_RANGE,
                modifier = Modifier.weight(1f),
            ) { onChange(backdrop.copy(borderPadding = it)) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BackdropNumberField(
                label = stringResource(Res.string.backdrop_border_radius),
                value = backdrop.borderRadius,
                range = TextBackdrop.RADIUS_RANGE,
                modifier = Modifier.weight(1f),
            ) { onChange(backdrop.copy(borderRadius = it)) }
            // The row is half full on purpose: the field keeps the width it has in the row above
            // rather than stretching to twice it.
            Spacer(Modifier.weight(1f))
        }
    }
}

/** A titled block of fields, marked with the same accent bar on each. */
@Composable
private fun FieldGroup(title: String, fields: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(11.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            SectionLabel(title)
        }
        fields()
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun BackdropNumberField(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit,
) {
    NumberSettingsTextField(
        modifier = modifier,
        label = label,
        initialText = value,
        range = range,
        onValueChange = onValueChange,
    )
}
