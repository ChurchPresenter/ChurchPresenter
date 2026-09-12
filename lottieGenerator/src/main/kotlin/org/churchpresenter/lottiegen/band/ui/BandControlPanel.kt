package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.band.BandColorRole
import org.churchpresenter.lottiegen.band.BandEntrance
import org.churchpresenter.lottiegen.band.BandStyle
import org.churchpresenter.lottiegen.band.BandTextAlign
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.band.ReferencePlacement
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens
import org.churchpresenter.lottiegen.ui.components.AccentButton
import org.churchpresenter.lottiegen.ui.components.CollapsibleSection
import org.churchpresenter.lottiegen.ui.components.DeleteIconButton
import org.churchpresenter.lottiegen.ui.components.SubtleButton
import org.churchpresenter.lottiegen.ui.components.ColorPickerRow
import org.churchpresenter.lottiegen.ui.components.LottieCheckbox
import org.churchpresenter.lottiegen.ui.components.LottieDropdown
import org.churchpresenter.lottiegen.ui.components.LottieTextField
import org.churchpresenter.lottiegen.ui.components.SectionCard
import org.churchpresenter.lottiegen.ui.components.SliderWithLabel
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

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
internal fun BandControlPanel(
    viewModel: BibleLottieGenViewModel,
    panelWidth: Dp,
    pickImage: (suspend () -> File?)? = null,
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxHeight().width(panelWidth).background(Tokens.PanelBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Strings.bandAppTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Tokens.TitleText)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.CardBorder))
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(start = 13.dp, end = 13.dp + SCROLLBAR_GUTTER, top = 10.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                BandSection(viewModel, pickImage)
                LayoutSection(viewModel)
                AnimationSection(viewModel)
                PreviewTextSection(viewModel)
                SaveSection(viewModel)
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp),
            )
        }
    }
}

private val SCROLLBAR_GUTTER = 10.dp
private val MENU_MAX_HEIGHT = 380.dp
private val MENU_ITEM_HEIGHT = 48.dp

@Composable
private fun BandSection(viewModel: BibleLottieGenViewModel, pickImage: (suspend () -> File?)?) {
    val cfg = viewModel.config
    SectionCard(Strings.bandSectionBand) {
        EnumDropdown(Strings.bandStyle, "style", cfg.bandStyle, BandStyle.entries) { v ->
            viewModel.updateConfig { it.copy(bandStyle = v) }
        }
        ColorPickerRow(
            if (cfg.hasBackgroundImage) Strings.bandColorTint else Strings.bandColorBackground,
            cfg.bgColor,
            cfg.bgAlpha,
            onColorChange = { c -> viewModel.updateConfig { it.copy(bgColor = c) } },
            onAlphaChange = { a -> viewModel.updateConfig { it.copy(bgAlpha = a) } },
        )
        RoleImageRow(viewModel, BandColorRole.BACKGROUND, pickImage)
        if (cfg.bandStyle.usesSecond) {
            ColorPickerRow(
                Strings.bandColorGradient, cfg.gradientColor, BibleLottieGenConfig.FULL_ALPHA,
                onColorChange = { c -> viewModel.updateConfig { it.copy(gradientColor = c) } },
                onAlphaChange = {},
            )
            RoleImageRow(viewModel, BandColorRole.SECOND, pickImage)
        }
        ColorPickerRow(
            Strings.bandColorAccent, cfg.accentColor, cfg.accentAlpha,
            onColorChange = { c -> viewModel.updateConfig { it.copy(accentColor = c) } },
            onAlphaChange = { a -> viewModel.updateConfig { it.copy(accentAlpha = a) } },
        )
        RoleImageRow(viewModel, BandColorRole.ACCENT, pickImage)
        if (cfg.bandStyle.usesTertiary) {
            ColorPickerRow(
                Strings.bandColorThird, cfg.tertiaryColor, cfg.tertiaryAlpha,
                onColorChange = { c -> viewModel.updateConfig { it.copy(tertiaryColor = c) } },
                onAlphaChange = { a -> viewModel.updateConfig { it.copy(tertiaryAlpha = a) } },
            )
            RoleImageRow(viewModel, BandColorRole.TERTIARY, pickImage)
        }
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

/** Under a colour row: the picture standing in for that colour, if any, a chooser, and a way to drop it. */
@Composable
private fun RoleImageRow(viewModel: BibleLottieGenViewModel, role: BandColorRole, pickImage: (suspend () -> File?)?) {
    val image = viewModel.config.images[role]
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = image?.name ?: Strings.bandImageNone,
            fontSize = 11.sp,
            color = Tokens.FieldLabel,
            modifier = Modifier.weight(1f),
        )
        SubtleButton(
            Strings.bandImageChoose,
            onClick = {
                if (pickImage != null) {
                    scope.launch { pickImage()?.let { viewModel.loadBandImage(role, it) } }
                } else {
                    SwingUtilities.invokeLater {
                        val chooser = JFileChooser()
                        chooser.fileFilter = FileNameExtensionFilter(Strings.bandImage, "png", "jpg", "jpeg", "webp")
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            viewModel.loadBandImage(role, chooser.selectedFile)
                        }
                    }
                }
            },
            compact = true,
        )
        if (image != null) {
            DeleteIconButton(onClick = { viewModel.clearBandImage(role) }, contentDescription = Strings.bandImageClear)
        }
    }
}

@Composable
private fun LayoutSection(viewModel: BibleLottieGenViewModel) {
    val cfg = viewModel.config
    val kind = cfg.kind.name.lowercase()
    SectionCard(Strings.bandSectionLayout) {
        EnumDropdown(Strings.bandLayout, "layout", cfg.layout, SlotLayout.entries) { v ->
            viewModel.updateConfig { it.copy(layout = v) }
        }
        EnumDropdown(
            Strings.bandLabel("reference", kind), "reference", cfg.referencePlacement, ReferencePlacement.entries,
            labelOf = { Strings.bandLabel("reference_${it.name.lowercase()}", kind) },
        ) { v ->
            viewModel.updateConfig { it.copy(referencePlacement = v) }
        }
        // "Follow settings" names whose settings it follows; the three fixed alignments do not.
        val alignLabel: (BandTextAlign) -> String = {
            if (it == BandTextAlign.FOLLOW_SETTINGS) Strings.bandLabel("align_follow_settings", kind)
            else Strings.bandEnumLabel("align", it.name)
        }
        val aligns = BandTextAlign.entries
        EnumDropdown(Strings.bandLabel("text_align", kind), "align", cfg.textAlign, aligns, alignLabel) { v ->
            viewModel.updateConfig { it.copy(textAlign = v) }
        }
        EnumDropdown(Strings.bandLabel("reference_align", kind), "align", cfg.referenceAlign, aligns, alignLabel) { v ->
            viewModel.updateConfig { it.copy(referenceAlign = v) }
        }
        SliderWithLabel(
            label = Strings.bandLabel("reference_height", kind),
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
    val kind = cfg.kind.name.lowercase()
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
        val textSizeLabel = Strings.bandLabel("preview_text_size", kind)
        PxSlider(textSizeLabel, cfg.previewTextSizePx, MAX_PREVIEW_SIZE, MIN_PREVIEW_SIZE) { v ->
            viewModel.updateConfig { it.copy(previewTextSizePx = v) }
        }
        val referenceSizeLabel = Strings.bandLabel("preview_reference_size", kind)
        PxSlider(referenceSizeLabel, cfg.previewReferenceSizePx, MAX_PREVIEW_SIZE, MIN_PREVIEW_SIZE) { v ->
            viewModel.updateConfig { it.copy(previewReferenceSizePx = v) }
        }
        LottieTextField(
            value = cfg.previewText1,
            onValueChange = { v -> viewModel.updateConfig { it.copy(previewText1 = v) } },
            label = Strings.bandLabel("preview_text_1", kind),
            fillWidth = true,
            singleLine = false,
        )
        LottieTextField(
            value = cfg.previewReference1,
            onValueChange = { v -> viewModel.updateConfig { it.copy(previewReference1 = v) } },
            label = Strings.bandLabel("preview_reference_1", kind),
            fillWidth = true,
        )
        if (cfg.layout != SlotLayout.SINGLE) {
            LottieTextField(
                value = cfg.previewText2,
                onValueChange = { v -> viewModel.updateConfig { it.copy(previewText2 = v) } },
                label = Strings.bandLabel("preview_text_2", kind),
                fillWidth = true,
                singleLine = false,
            )
            LottieTextField(
                value = cfg.previewReference2,
                onValueChange = { v -> viewModel.updateConfig { it.copy(previewReference2 = v) } },
                label = Strings.bandLabel("preview_reference_2", kind),
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
    labelOf: (T) -> String = { Strings.bandEnumLabel(prefix, it.name) },
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, Modifier.fillMaxWidth()) {
        LottieDropdown(
            label = label,
            value = labelOf(value),
            expanded = expanded,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            // The list scrolls inside a capped box of its own, with a scrollbar beside it — the
            // menu's built-in scroll has no bar, and twenty-odd styles run off the screen without one.
            val listState = rememberScrollState()
            // An explicit height, not a cap: the menu measures its content's intrinsic size, and a
            // scrollbar filling an uncapped box reports an infinite height there and crashes.
            val listHeight = (MENU_ITEM_HEIGHT * entries.size).coerceAtMost(MENU_MAX_HEIGHT)
            Box(modifier = Modifier.height(listHeight)) {
                Column(modifier = Modifier.verticalScroll(listState).padding(end = SCROLLBAR_GUTTER)) {
                    entries.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(labelOf(entry)) },
                            onClick = {
                                onSelect(entry)
                                expanded = false
                            },
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp),
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
