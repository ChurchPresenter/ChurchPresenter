package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.band.BandEntrance
import org.churchpresenter.lottiegen.band.BandStyle
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.band.ReferencePlacement
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens
import org.churchpresenter.lottiegen.ui.components.AccentButton
import org.churchpresenter.lottiegen.ui.components.CollapsibleSection
import org.churchpresenter.lottiegen.ui.components.ColorPickerRow
import org.churchpresenter.lottiegen.ui.components.LottieCheckbox
import org.churchpresenter.lottiegen.ui.components.LottieDropdown
import org.churchpresenter.lottiegen.ui.components.LottieTextField
import org.churchpresenter.lottiegen.ui.components.SectionCard
import org.churchpresenter.lottiegen.ui.components.SliderWithLabel

private const val MAX_BORDER_PX = 12f
private const val MAX_CORNER_PX = 120f
private const val MAX_INSET_PX = 120f
private const val MAX_PADDING_PX = 160f
private const val MIN_REFERENCE_FRACTION = 0.1f
private const val MAX_REFERENCE_FRACTION = 0.5f
private const val MIN_SECONDS = 0.1f
private const val MAX_SECONDS = 4f
private const val MAX_HOLD_SECONDS = 10f
private const val MIN_TICKER_SPEED = 30f
private const val MAX_TICKER_SPEED = 600f
private const val MIN_PREVIEW_SIZE = 12f
private const val MAX_PREVIEW_SIZE = 200f

/** The band generator's left pane: every knob the template has, top to bottom. */
@Composable
internal fun BandControlPanel(viewModel: BibleLottieGenViewModel, panelWidth: Dp) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxHeight().width(panelWidth).background(Tokens.PanelBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Strings.bandAppTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Tokens.TitleText)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.CardBorder))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            BandSection(viewModel)
            LayoutSection(viewModel)
            AnimationSection(viewModel)
            PreviewTextSection(viewModel)
            SaveSection(viewModel)
        }
    }
}

@Composable
private fun BandSection(viewModel: BibleLottieGenViewModel) {
    val cfg = viewModel.config
    SectionCard(Strings.bandSectionBand) {
        EnumDropdown(Strings.bandStyle, "style", cfg.bandStyle, BandStyle.entries) { v ->
            viewModel.updateConfig { it.copy(bandStyle = v) }
        }
        ColorPickerRow(
            Strings.bandColorBackground, cfg.bgColor, cfg.bgAlpha,
            onColorChange = { c -> viewModel.updateConfig { it.copy(bgColor = c) } },
            onAlphaChange = { a -> viewModel.updateConfig { it.copy(bgAlpha = a) } },
        )
        ColorPickerRow(
            Strings.bandColorAccent, cfg.accentColor, cfg.accentAlpha,
            onColorChange = { c -> viewModel.updateConfig { it.copy(accentColor = c) } },
            onAlphaChange = { a -> viewModel.updateConfig { it.copy(accentAlpha = a) } },
        )
        if (cfg.bandStyle == BandStyle.GRADIENT_BAR) {
            ColorPickerRow(
                Strings.bandColorGradient, cfg.gradientColor, cfg.bgAlpha,
                onColorChange = { c -> viewModel.updateConfig { it.copy(gradientColor = c) } },
                onAlphaChange = { a -> viewModel.updateConfig { it.copy(bgAlpha = a) } },
            )
        }
        ColorPickerRow(
            Strings.bandColorBorder, cfg.borderColor, cfg.borderAlpha,
            onColorChange = { c -> viewModel.updateConfig { it.copy(borderColor = c) } },
            onAlphaChange = { a -> viewModel.updateConfig { it.copy(borderAlpha = a) } },
        )
        PxSlider(Strings.bandBorderThickness, cfg.borderThickness, MAX_BORDER_PX) { v ->
            viewModel.updateConfig { it.copy(borderThickness = v) }
        }
        PxSlider(Strings.bandCornerRadius, cfg.cornerRadiusPx, MAX_CORNER_PX) { v ->
            viewModel.updateConfig { it.copy(cornerRadiusPx = v) }
        }
        PxSlider(Strings.bandInset, cfg.insetPx, MAX_INSET_PX) { v ->
            viewModel.updateConfig { it.copy(insetPx = v) }
        }
        PxSlider(Strings.bandPadding, cfg.paddingPx, MAX_PADDING_PX) { v ->
            viewModel.updateConfig { it.copy(paddingPx = v) }
        }
    }
}

@Composable
private fun LayoutSection(viewModel: BibleLottieGenViewModel) {
    val cfg = viewModel.config
    SectionCard(Strings.bandSectionLayout) {
        EnumDropdown(Strings.bandLayout, "layout", cfg.layout, SlotLayout.entries) { v ->
            viewModel.updateConfig { it.copy(layout = v) }
        }
        EnumDropdown(Strings.bandReference, "reference", cfg.referencePlacement, ReferencePlacement.entries) { v ->
            viewModel.updateConfig { it.copy(referencePlacement = v) }
        }
        SliderWithLabel(
            label = Strings.bandReferenceHeight,
            value = cfg.referenceHeightFraction,
            onValueChange = { v -> viewModel.updateConfig { it.copy(referenceHeightFraction = v) } },
            valueRange = MIN_REFERENCE_FRACTION..MAX_REFERENCE_FRACTION,
            format = { "${(it * PERCENT).toInt()}%" },
        )
    }
}

@Composable
private fun AnimationSection(viewModel: BibleLottieGenViewModel) {
    val cfg = viewModel.config
    SectionCard(Strings.bandSectionAnimation) {
        EnumDropdown(Strings.bandEntrance, "entrance", cfg.entrance, BandEntrance.entries) { v ->
            viewModel.updateConfig { it.copy(entrance = v) }
        }
        EnumDropdown(Strings.bandTextAnimation, "text", cfg.textAnimation, TextAnimation.entries) { v ->
            viewModel.updateConfig { it.copy(textAnimation = v) }
        }
        SecondsSlider(Strings.bandTimeBandIn, cfg.bgInSeconds, MAX_SECONDS) { v ->
            viewModel.updateConfig { it.copy(bgInSeconds = v) }
        }
        SecondsSlider(Strings.bandTimeTextIn, cfg.textInSeconds, MAX_SECONDS) { v ->
            viewModel.updateConfig { it.copy(textInSeconds = v) }
        }
        SecondsSlider(Strings.bandTimeHold, cfg.holdSeconds, MAX_HOLD_SECONDS) { v ->
            viewModel.updateConfig { it.copy(holdSeconds = v) }
        }
        SecondsSlider(Strings.bandTimeTextOut, cfg.textOutSeconds, MAX_SECONDS) { v ->
            viewModel.updateConfig { it.copy(textOutSeconds = v) }
        }
        SecondsSlider(Strings.bandTimeBandOut, cfg.bgOutSeconds, MAX_SECONDS) { v ->
            viewModel.updateConfig { it.copy(bgOutSeconds = v) }
        }
        if (cfg.textAnimation == TextAnimation.TICKER) {
            SliderWithLabel(
                label = Strings.bandTickerSpeed,
                value = cfg.tickerPxPerSecond.toFloat(),
                onValueChange = { v -> viewModel.updateConfig { it.copy(tickerPxPerSecond = v.toInt()) } },
                valueRange = MIN_TICKER_SPEED..MAX_TICKER_SPEED,
                unit = Strings.bandUnitPx + "/" + Strings.bandUnitSeconds,
                format = { it.toInt().toString() },
            )
        }
    }
}

@Composable
private fun PreviewTextSection(viewModel: BibleLottieGenViewModel) {
    val cfg = viewModel.config
    CollapsibleSection(Strings.bandSectionPreviewText, initiallyExpanded = true) {
        LottieTextField(
            value = cfg.previewFontFamily,
            onValueChange = { v -> viewModel.updateConfig { it.copy(previewFontFamily = v) } },
            label = Strings.bandPreviewFont,
            fillWidth = true,
        )
        ColorPickerRow(
            Strings.bandPreviewTextColor, cfg.previewTextColor, BibleLottieGenConfig.FULL_ALPHA,
            onColorChange = { c -> viewModel.updateConfig { it.copy(previewTextColor = c) } },
            onAlphaChange = {},
        )
        ColorPickerRow(
            Strings.bandPreviewReferenceColor, cfg.previewReferenceColor, BibleLottieGenConfig.FULL_ALPHA,
            onColorChange = { c -> viewModel.updateConfig { it.copy(previewReferenceColor = c) } },
            onAlphaChange = {},
        )
        LottieCheckbox(
            label = Strings.bandPreviewBold,
            checked = cfg.previewBold,
            onCheckedChange = { v -> viewModel.updateConfig { it.copy(previewBold = v) } },
        )
        PxSlider(Strings.bandPreviewTextSize, cfg.previewTextSizePx, MAX_PREVIEW_SIZE, MIN_PREVIEW_SIZE) { v ->
            viewModel.updateConfig { it.copy(previewTextSizePx = v) }
        }
        PxSlider(Strings.bandPreviewReferenceSize, cfg.previewReferenceSizePx, MAX_PREVIEW_SIZE, MIN_PREVIEW_SIZE) { v ->
            viewModel.updateConfig { it.copy(previewReferenceSizePx = v) }
        }
        LottieTextField(
            value = cfg.previewText1,
            onValueChange = { v -> viewModel.updateConfig { it.copy(previewText1 = v) } },
            label = Strings.bandPreviewText1,
            fillWidth = true,
            singleLine = false,
        )
        LottieTextField(
            value = cfg.previewReference1,
            onValueChange = { v -> viewModel.updateConfig { it.copy(previewReference1 = v) } },
            label = Strings.bandPreviewReference1,
            fillWidth = true,
        )
        if (cfg.layout != SlotLayout.SINGLE) {
            LottieTextField(
                value = cfg.previewText2,
                onValueChange = { v -> viewModel.updateConfig { it.copy(previewText2 = v) } },
                label = Strings.bandPreviewText2,
                fillWidth = true,
                singleLine = false,
            )
            LottieTextField(
                value = cfg.previewReference2,
                onValueChange = { v -> viewModel.updateConfig { it.copy(previewReference2 = v) } },
                label = Strings.bandPreviewReference2,
                fillWidth = true,
            )
        }
    }
}

@Composable
private fun SaveSection(viewModel: BibleLottieGenViewModel) {
    SectionCard(Strings.bandSectionSave) {
        LottieTextField(
            value = viewModel.fileName,
            onValueChange = viewModel::updateFileName,
            label = Strings.bandFileName,
            fillWidth = true,
        )
        AccentButton(Strings.bandSave, onClick = { viewModel.save() }, modifier = Modifier.fillMaxWidth())
        Text(Strings.bandSaveHint, fontSize = 11.sp, color = Tokens.FieldLabel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumDropdown(
    label: String,
    prefix: String,
    value: T,
    entries: List<T>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, Modifier.fillMaxWidth()) {
        LottieDropdown(
            label = label,
            value = Strings.bandEnumLabel(prefix, value.name),
            expanded = expanded,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(Strings.bandEnumLabel(prefix, entry.name)) },
                    onClick = {
                        onSelect(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PxSlider(label: String, value: Int, max: Float, min: Float = 0f, onChange: (Int) -> Unit) {
    SliderWithLabel(
        label = label,
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = min..max,
        unit = Strings.bandUnitPx,
        format = { it.toInt().toString() },
    )
}

@Composable
private fun SecondsSlider(label: String, value: Float, max: Float, onChange: (Float) -> Unit) {
    SliderWithLabel(
        label = label,
        value = value,
        onValueChange = onChange,
        valueRange = MIN_SECONDS..max,
        unit = Strings.bandUnitSeconds,
    )
}

private const val PERCENT = 100
