package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.qa_pos_bc
import churchpresenter.composeapp.generated.resources.qa_pos_bl
import churchpresenter.composeapp.generated.resources.qa_pos_br
import churchpresenter.composeapp.generated.resources.qa_pos_c
import churchpresenter.composeapp.generated.resources.qa_pos_cl
import churchpresenter.composeapp.generated.resources.qa_pos_cr
import churchpresenter.composeapp.generated.resources.qa_pos_tc
import churchpresenter.composeapp.generated.resources.qa_pos_tl
import churchpresenter.composeapp.generated.resources.qa_pos_tr
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/** The largest font size the caption and Q&A forms accept. */
private val DISPLAY_FONT_SIZE_RANGE = 8..200

/**
 * The text look captions and Q&A questions share, field for field -- the same twelve settings on
 * [org.churchpresenter.settings.STTSettings] and [org.churchpresenter.settings.QASettings] -- so the
 * two forms draw one set of controls rather than two copies of it.
 */
internal data class DisplayTextStyle(
    val textColor: String,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val shadow: Boolean,
    val shadowColor: String,
    val shadowSize: Int,
    val shadowOpacity: Int,
    val backdrop: TextBackdrop,
    val outline: TextOutline,
    val fontType: String,
    val fontSize: Int,
)

/** Captions for [DisplayTextStyleControls]' three fields, which the two forms word differently. */
internal data class DisplayTextStyleLabels(val color: String, val font: String, val size: String)

/** Text colour, the style keys with their shadow detail, and font and size. */
@Composable
internal fun DisplayTextStyleControls(
    style: DisplayTextStyle,
    labels: DisplayTextStyleLabels,
    availableFonts: List<String>,
    onChange: (DisplayTextStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorPickerField(
            label = labels.color,
            color = style.textColor,
            onColorChange = { onChange(style.copy(textColor = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextStyleButtons(
            bold = style.bold,
            italic = style.italic,
            underline = style.underline,
            shadow = style.shadow,
            onBoldChange = { onChange(style.copy(bold = it)) },
            onItalicChange = { onChange(style.copy(italic = it)) },
            onUnderlineChange = { onChange(style.copy(underline = it)) },
            onShadowChange = { onChange(style.copy(shadow = it)) },
            backdrop = style.backdrop,
            onBackdropChange = { onChange(style.copy(backdrop = it)) },
            outline = style.outline,
            onOutlineChange = { onChange(style.copy(outline = it)) },
        )
        AnimatedVisibility(visible = style.shadow) {
            ShadowDetailRow(
                shadowColor = style.shadowColor,
                shadowSize = style.shadowSize,
                shadowOpacity = style.shadowOpacity,
                onColorChange = { onChange(style.copy(shadowColor = it)) },
                onSizeChange = { onChange(style.copy(shadowSize = it)) },
                onOpacityChange = { onChange(style.copy(shadowOpacity = it)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FontSettingsDropdown(
                label = labels.font,
                value = style.fontType,
                fonts = availableFonts,
                onValueChange = { onChange(style.copy(fontType = it)) },
                modifier = Modifier.weight(1f),
            )
            NumberSettingsTextField(
                label = labels.size,
                initialText = style.fontSize,
                range = DISPLAY_FONT_SIZE_RANGE,
                onValueChange = { onChange(style.copy(fontSize = it)) },
            )
        }
    }
}

/** The nine places on screen a caption block or a question can sit, in reading order. */
@Composable
internal fun screenPositions(): List<Pair<String, String>> = listOf(
    Constants.TOP_LEFT to stringResource(Res.string.qa_pos_tl),
    Constants.TOP_CENTER to stringResource(Res.string.qa_pos_tc),
    Constants.TOP_RIGHT to stringResource(Res.string.qa_pos_tr),
    Constants.CENTER_LEFT to stringResource(Res.string.qa_pos_cl),
    Constants.CENTER to stringResource(Res.string.qa_pos_c),
    Constants.CENTER_RIGHT to stringResource(Res.string.qa_pos_cr),
    Constants.BOTTOM_LEFT to stringResource(Res.string.qa_pos_bl),
    Constants.BOTTOM_CENTER to stringResource(Res.string.qa_pos_bc),
    Constants.BOTTOM_RIGHT to stringResource(Res.string.qa_pos_br),
)

/** A labelled 0–100% slider, as both forms draw their background opacity. */
@Composable
internal fun OpacitySliderRow(label: String, percent: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(4.dp))
        SlimSlider(
            value = percent / PERCENT,
            onValueChange = { onChange((it * PERCENT).toInt()) },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            trailingLabel = "$percent%",
        )
    }
}

private const val PERCENT = 100f
