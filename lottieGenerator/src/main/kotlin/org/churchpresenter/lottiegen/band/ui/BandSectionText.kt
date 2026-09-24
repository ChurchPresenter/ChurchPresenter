package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.band.BandFontPicker
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.lottie.rememberSystemFonts
import org.churchpresenter.lottiegen.model.LottieFont
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.theme.components.TextStyleToggleButton
import androidx.compose.material3.LocalContentColor
import org.churchpresenter.theme.components.SegmentTrackItem
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette

private const val MAX_ALPHA_PCT = 100f
private const val MIN_PREVIEW_SIZE = 12f
private const val MAX_PREVIEW_SIZE = 200f

/** The Text pane: the sample's face, its two colours and sizes, and the words themselves. */
@Composable
internal fun TextSection(viewModel: BibleLottieGenViewModel, fontPicker: BandFontPicker?) {
    val cfg = viewModel.config
    val kind = cfg.kind.name.lowercase()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val onFont: (String) -> Unit = { v -> viewModel.updateConfig { it.copy(previewFontFamily = v) } }
        if (fontPicker != null) {
            // The host's own picker, listing the machine's fonts each in its own face.
            fontPicker(cfg.previewFontFamily, onFont, Modifier.weight(1f))
        } else {
            val bundledFontNames = LottieFont.entries.map { it.familyName }
            val systemFontNames = rememberSystemFonts().filterNot { it in bundledFontNames }
            val fonts = bundledFontNames + systemFontNames
            DropdownSelector(
                label = Strings.bandPreviewFont,
                value = cfg.previewFontFamily,
                options = fonts.map { it to it },
                onValueChange = onFont,
                modifier = Modifier.weight(1f),
            )
        }
        // The app's style row, less what Lottie text cannot carry: no underline, no strikethrough.
        TextStyleToggleButton(
            label = Strings.bandStyleBold,
            tooltip = Strings.bandPreviewBold,
            isActive = cfg.previewBold,
            fontWeight = FontWeight.Bold,
            onClick = { viewModel.updateConfig { it.copy(previewBold = !it.previewBold) } },
        )
        TextStyleToggleButton(
            label = Strings.bandStyleItalic,
            tooltip = Strings.bandPreviewItalic,
            isActive = cfg.previewItalic,
            fontStyle = FontStyle.Italic,
            onClick = { viewModel.updateConfig { it.copy(previewItalic = !it.previewItalic) } },
        )
        TextStyleToggleButton(
            label = Strings.bandStyleShadow,
            tooltip = Strings.bandPreviewShadow,
            isActive = cfg.previewShadow,
            onClick = { viewModel.updateConfig { it.copy(previewShadow = !it.previewShadow) } },
        )
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
    TextOpacityRow(viewModel, kind)
    Hairline()
    var lang by remember { mutableStateOf(0) }
    // As many tabs as the layout has cells for -- one for [SlotLayout.SINGLE], up to four for a
    // grid -- so a language whose tab has scrolled out of reach by a layout change is never left
    // silently selected.
    val slotCount = cfg.layout.cellCount
    if (lang >= slotCount) lang = 0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption(Strings.bandSampleText, Modifier.weight(1f))
        Row(
            Modifier.sunken(FIELD_SHAPE, elevationPalette()).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (i in 0 until slotCount) {
                LangTab(Strings.bandLabel("preview_text_${i + 1}", kind), selected = lang == i) { lang = i }
            }
        }
    }
    val text = previewTextOf(cfg, lang)
    val reference = previewReferenceOf(cfg, lang)
    SettingsTextField(
        value = text,
        onValueChange = { v -> viewModel.updateConfig { it.withPreviewText(lang, v) } },
        label = Strings.bandLabel("preview_text_1", kind).substringBefore(' '),
        singleLine = false,
        fillWidth = true,
    )
    SettingsTextField(
        value = reference,
        onValueChange = { v -> viewModel.updateConfig { it.withPreviewReference(lang, v) } },
        label = Strings.bandLabel("reference", kind),
        fillWidth = true,
    )
}

/** [cfg]'s preview text for language [index], 0 to 3. */
private fun previewTextOf(cfg: BibleLottieGenConfig, index: Int): String =
    when (index) {
        0 -> cfg.previewText1
        1 -> cfg.previewText2
        2 -> cfg.previewText3
        else -> cfg.previewText4
    }

/** [cfg]'s preview reference for language [index], 0 to 3. */
private fun previewReferenceOf(cfg: BibleLottieGenConfig, index: Int): String =
    when (index) {
        0 -> cfg.previewReference1
        1 -> cfg.previewReference2
        2 -> cfg.previewReference3
        else -> cfg.previewReference4
    }

/** [BibleLottieGenConfig] with language [index]'s preview text replaced by [value]. */
private fun BibleLottieGenConfig.withPreviewText(
    index: Int,
    value: String,
): BibleLottieGenConfig = when (index) {
    0 -> copy(previewText1 = value)
    1 -> copy(previewText2 = value)
    2 -> copy(previewText3 = value)
    else -> copy(previewText4 = value)
}

/** [BibleLottieGenConfig] with language [index]'s preview reference replaced by [value]. */
private fun BibleLottieGenConfig.withPreviewReference(
    index: Int,
    value: String,
): BibleLottieGenConfig = when (index) {
    0 -> copy(previewReference1 = value)
    1 -> copy(previewReference2 = value)
    2 -> copy(previewReference3 = value)
    else -> copy(previewReference4 = value)
}

/**
 * How opaque the two text slots are.
 *
 * Opacity rather than a colour with an alpha channel: the band's text colour comes from the app's
 * own settings at run time, and only the layer's opacity is the template's to set.
 */
@Composable
private fun TextOpacityRow(viewModel: BibleLottieGenViewModel, kind: String) {
    val cfg = viewModel.config
    SliderGrid(
        listOf(
            GridSlider(
                Strings.bandLabel("text_opacity", kind), cfg.textAlpha, MAX_ALPHA_PCT,
                unit = Strings.bandUnitPercent,
            ) { v -> viewModel.updateConfig { it.copy(textAlpha = v) } },
            GridSlider(
                Strings.bandLabel("reference_opacity", kind), cfg.referenceAlpha, MAX_ALPHA_PCT,
                unit = Strings.bandUnitPercent,
            ) { v -> viewModel.updateConfig { it.copy(referenceAlpha = v) } },
        ),
    )
}

/** A colour and a size on one line: the verse's, then the reference's. */
@Composable
private fun TextStyleRow(label: String, color: String, onColor: (String) -> Unit, size: Int, onSize: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HexField(label, color, onColor)
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
private fun LangTab(label: String, selected: Boolean, onClick: () -> Unit) {
    SegmentTrackItem(selected = selected, onClick = onClick, modifier = Modifier.height(22.dp)) {
        Text(
            label, fontSize = 10.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = LocalContentColor.current,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}
