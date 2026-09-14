package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.model.LottieFont
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens

private const val MIN_PREVIEW_SIZE = 12f
private const val MAX_PREVIEW_SIZE = 200f
private const val VERSE_MIN_LINES = 3

/** The Text pane: the sample's face, its two colours and sizes, and the words themselves. */
@Composable
internal fun TextSection(viewModel: BibleLottieGenViewModel) {
    val cfg = viewModel.config
    val kind = cfg.kind.name.lowercase()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val fonts = LottieFont.entries.map { it.familyName }
        PickerField(
            Strings.bandPreviewFont, cfg.previewFontFamily, fonts, cfg.previewFontFamily,
            labelOf = { it },
            onPick = { v -> viewModel.updateConfig { it.copy(previewFontFamily = v) } },
            modifier = Modifier.weight(1f),
        )
        BoldToggle(cfg.previewBold) { v -> viewModel.updateConfig { it.copy(previewBold = v) } }
    }
    TextStyleRow(
        label = Strings.bandLabel("preview_text_1", kind).substringBefore(' '),
        color = cfg.previewTextColor,
        onColor = { c -> viewModel.updateConfig { it.copy(previewTextColor = c) } },
        size = cfg.previewTextSizePx,
        onSize = { v -> viewModel.updateConfig { it.copy(previewTextSizePx = v) } },
    )
    TextStyleRow(
        label = Strings.bandLabel("reference", kind),
        color = cfg.previewReferenceColor,
        onColor = { c -> viewModel.updateConfig { it.copy(previewReferenceColor = c) } },
        size = cfg.previewReferenceSizePx,
        onSize = { v -> viewModel.updateConfig { it.copy(previewReferenceSizePx = v) } },
    )
    Hairline()
    var lang by remember { mutableStateOf(0) }
    val twoLanguages = cfg.layout != SlotLayout.SINGLE
    if (!twoLanguages) lang = 0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption(Strings.bandSampleText, Modifier.weight(1f))
        Row(
            Modifier.clip(FIELD_SHAPE).background(Tokens.FieldBg).border(1.dp, Tokens.FieldBorder, FIELD_SHAPE)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (i in 0 until if (twoLanguages) 2 else 1) {
                LangTab(Strings.bandLabel("preview_text_${i + 1}", kind), selected = lang == i) { lang = i }
            }
        }
    }
    val text = if (lang == 0) cfg.previewText1 else cfg.previewText2
    val reference = if (lang == 0) cfg.previewReference1 else cfg.previewReference2
    CaptionedInput(
        Strings.bandLabel("preview_text_1", kind).substringBefore(' '), text,
        onValueChange = { v ->
            viewModel.updateConfig { if (lang == 0) it.copy(previewText1 = v) else it.copy(previewText2 = v) }
        },
        minLines = VERSE_MIN_LINES,
    )
    CaptionedInput(
        Strings.bandLabel("reference", kind), reference,
        onValueChange = { v ->
            viewModel.updateConfig { if (lang == 0) it.copy(previewReference1 = v) else it.copy(previewReference2 = v) }
        },
    )
}

/** A colour and a size on one line: the verse's, then the reference's. */
@Composable
private fun TextStyleRow(label: String, color: String, onColor: (String) -> Unit, size: Int, onSize: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 11.5.sp, color = Tokens.LabelText, maxLines = 1, modifier = Modifier.width(52.dp))
        HexField(color, onColor, Modifier.width(100.dp))
        Box(Modifier.weight(1f)) {
            InlineSlider(
                label = "",
                value = size.toFloat(),
                onValueChange = { onSize(it.toInt()) },
                valueRange = MIN_PREVIEW_SIZE..MAX_PREVIEW_SIZE,
                format = { it.toInt().toString() },
                unit = Strings.bandUnitPx,
                labelWidth = 0.dp,
            )
        }
    }
}

@Composable
private fun BoldToggle(bold: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CARD_SHAPE)
            .background(if (bold) Tokens.Accent else Tokens.FieldBg)
            .border(1.dp, if (bold) Tokens.Accent else Tokens.FieldBorder, CARD_SHAPE)
            .clickable { onChange(!bold) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.FormatBold, contentDescription = Strings.bandPreviewBold,
            tint = if (bold) Tokens.OnAccent else Tokens.LabelText, modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LangTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(FIELD_SHAPE)
            .background(if (selected) Tokens.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 10.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Tokens.OnAccent else Tokens.LabelText,
        )
    }
}
