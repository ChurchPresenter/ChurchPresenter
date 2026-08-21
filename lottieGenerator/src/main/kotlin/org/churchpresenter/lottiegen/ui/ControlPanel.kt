package org.churchpresenter.lottiegen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.LottieGenState
import org.churchpresenter.lottiegen.model.CANVAS_PRESETS
import org.churchpresenter.lottiegen.model.LottieFont
import org.churchpresenter.lottiegen.model.StyleCatalog
import org.churchpresenter.lottiegen.model.TIMING_PRESETS
import org.churchpresenter.lottiegen.ui.components.AccentButton
import org.churchpresenter.lottiegen.ui.components.CollapsibleSection
import org.churchpresenter.lottiegen.ui.components.ColorPickerRow
import org.churchpresenter.lottiegen.ui.components.DeleteIconButton
import org.churchpresenter.lottiegen.ui.components.LottieCheckbox
import org.churchpresenter.lottiegen.ui.components.LottieDropdown
import org.churchpresenter.lottiegen.ui.components.LottieTextField
import org.churchpresenter.lottiegen.ui.components.SectionCard
import org.churchpresenter.lottiegen.ui.components.SegmentedButtons
import org.churchpresenter.lottiegen.ui.components.SliderWithLabel
import org.churchpresenter.lottiegen.ui.components.SubtleButton
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/** Two equal columns, the layout every field pair in the panel uses. */
@Composable
private fun FieldRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        content()
    }
}

/** The app mark in the panel header. */
@Composable
private fun PanelHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Tokens.HeaderHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Tokens.LogoChipBg),
            contentAlignment = Alignment.Center
        ) {
            // A lower third in miniature: a frame with a caption bar across its lower edge.
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 11.dp)
                    .border(1.3.dp, Tokens.LogoIcon, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.BottomStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp, bottom = 1.5.dp)
                        .size(width = 8.dp, height = 2.6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Tokens.LogoIcon)
                )
            }
        }
        Text(
            Strings.appTitle,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.21).sp,
            color = Tokens.TitleText,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanel(viewModel: LottieGenState, panelWidth: Dp = 436.dp) {
    val cfg = viewModel.config
    val scrollState = rememberScrollState()
    var showBatchImport by remember { mutableStateOf(false) }
    var batchImportText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(panelWidth)
            .background(Tokens.PanelBg)
    ) {
        PanelHeader()
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.CardBorder))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {

            // ═══ Canvas ═══
            CollapsibleSection(Strings.sectionCanvas, hint = "${cfg.canvasW} × ${cfg.canvasH}") {
                val canvasIndex = CANVAS_PRESETS.indexOfFirst {
                    it.width == cfg.canvasW && it.height == cfg.canvasH
                }
                SegmentedButtons(
                    labels = CANVAS_PRESETS.map { it.label },
                    selectedIndex = canvasIndex,
                    onSelect = { i ->
                        val p = CANVAS_PRESETS[i]
                        viewModel.updateConfig { it.copy(canvasW = p.width, canvasH = p.height) }
                    }
                )
                FieldRow {
                    LottieTextField(
                        value = cfg.canvasW.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { viewModel.updateConfig { c -> c.copy(canvasW = it.coerceIn(100, 7680)) } } },
                        label = Strings.width,
                        modifier = Modifier.weight(1f), fillWidth = true, singleLine = true
                    )
                    LottieTextField(
                        value = cfg.canvasH.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { viewModel.updateConfig { c -> c.copy(canvasH = it.coerceIn(100, 4320)) } } },
                        label = Strings.height,
                        modifier = Modifier.weight(1f), fillWidth = true, singleLine = true
                    )
                }
                SliderWithLabel(
                    Strings.scale, cfg.scaleFactor,
                    { viewModel.updateConfig { c -> c.copy(scaleFactor = it) } },
                    0.5f..3f, unit = "×", format = { "%.2f".format(it) }
                )
            }

            // ═══ Style & Layout ═══
            SectionCard(Strings.sectionStyleLayout) {
                FieldRow {
                    var styleExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(styleExpanded, { styleExpanded = it }, Modifier.weight(1f)) {
                        LottieDropdown(
                            label = Strings.style,
                            value = StyleCatalog.labelFor(cfg.style),
                            expanded = styleExpanded,
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(styleExpanded, { styleExpanded = false }) {
                            StyleCatalog.entries.forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(style.label) },
                                    onClick = { viewModel.updateConfig { it.copy(style = style.id) }; styleExpanded = false }
                                )
                            }
                        }
                    }
                    var alignExpanded by remember { mutableStateOf(false) }
                    val alignmentLabels = mapOf(
                        "left" to Strings.alignLeft,
                        "center" to Strings.alignCenter,
                        "right" to Strings.alignRight
                    )
                    ExposedDropdownMenuBox(alignExpanded, { alignExpanded = it }, Modifier.weight(1f)) {
                        LottieDropdown(
                            label = Strings.alignment,
                            value = alignmentLabels[cfg.align] ?: cfg.align,
                            expanded = alignExpanded,
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(alignExpanded, { alignExpanded = false }) {
                            alignmentLabels.forEach { (id, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { viewModel.updateConfig { it.copy(align = id) }; alignExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            // ═══ Text ═══
            SectionCard(Strings.sectionText) {
                LottieTextField(
                    value = cfg.nameText,
                    onValueChange = { viewModel.updateConfig { c -> c.copy(nameText = it) } },
                    label = Strings.name,
                    modifier = Modifier.fillMaxWidth(), fillWidth = true, singleLine = true
                )
                LottieTextField(
                    value = cfg.infoText,
                    onValueChange = { viewModel.updateConfig { c -> c.copy(infoText = it) } },
                    label = Strings.info,
                    modifier = Modifier.fillMaxWidth(), fillWidth = true, singleLine = true
                )
            }

            // ═══ Text Style ═══
            CollapsibleSection(Strings.sectionTextStyle, hint = cfg.fontFamily) {
                FieldRow {
                    var fontExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(fontExpanded, { fontExpanded = it }, Modifier.weight(1f)) {
                        LottieDropdown(
                            label = Strings.font,
                            value = cfg.fontFamily,
                            expanded = fontExpanded,
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(fontExpanded, { fontExpanded = false }) {
                            LottieFont.entries.forEach { font ->
                                DropdownMenuItem(
                                    text = { Text(font.familyName) },
                                    onClick = { viewModel.updateConfig { it.copy(fontFamily = font.familyName) }; fontExpanded = false }
                                )
                            }
                        }
                    }
                    LottieTextField(
                        value = cfg.baseSize.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { viewModel.updateConfig { c -> c.copy(baseSize = it.coerceIn(10, 80)) } } },
                        label = Strings.baseSize,
                        modifier = Modifier.weight(1f), fillWidth = true, singleLine = true
                    )
                }
                FieldRow {
                    LottieTextField(
                        value = cfg.nameSize.toString(),
                        onValueChange = { v -> v.toFloatOrNull()?.let { viewModel.updateConfig { c -> c.copy(nameSize = it.coerceIn(0.5f, 4f)) } } },
                        label = Strings.nameSize, modifier = Modifier.weight(1f), fillWidth = true, singleLine = true
                    )
                    LottieTextField(
                        value = cfg.infoSize.toString(),
                        onValueChange = { v -> v.toFloatOrNull()?.let { viewModel.updateConfig { c -> c.copy(infoSize = it.coerceIn(0.5f, 4f)) } } },
                        label = Strings.infoSize, modifier = Modifier.weight(1f), fillWidth = true, singleLine = true
                    )
                }
                FieldRow {
                    var nwExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(nwExpanded, { nwExpanded = it }, Modifier.weight(1f)) {
                        LottieDropdown(
                            label = Strings.nameWeight,
                            value = if (cfg.nameWeight >= 700) Strings.bold else Strings.normal,
                            expanded = nwExpanded,
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(nwExpanded, { nwExpanded = false }) {
                            DropdownMenuItem({ Text(Strings.bold) }, { viewModel.updateConfig { it.copy(nameWeight = 700) }; nwExpanded = false })
                            DropdownMenuItem({ Text(Strings.normal) }, { viewModel.updateConfig { it.copy(nameWeight = 400) }; nwExpanded = false })
                        }
                    }
                    var iwExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(iwExpanded, { iwExpanded = it }, Modifier.weight(1f)) {
                        LottieDropdown(
                            label = Strings.infoWeight,
                            value = if (cfg.infoWeight >= 700) Strings.bold else Strings.normal,
                            expanded = iwExpanded,
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(iwExpanded, { iwExpanded = false }) {
                            DropdownMenuItem({ Text(Strings.normal) }, { viewModel.updateConfig { it.copy(infoWeight = 400) }; iwExpanded = false })
                            DropdownMenuItem({ Text(Strings.bold) }, { viewModel.updateConfig { it.copy(infoWeight = 700) }; iwExpanded = false })
                        }
                    }
                }
                FieldRow {
                    var ntExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(ntExpanded, { ntExpanded = it }, Modifier.weight(1f)) {
                        LottieDropdown(
                            label = Strings.nameTransform,
                            value = if (cfg.nameTransform == "uppercase") Strings.uppercase else Strings.none,
                            expanded = ntExpanded,
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(ntExpanded, { ntExpanded = false }) {
                            DropdownMenuItem({ Text(Strings.uppercase) }, { viewModel.updateConfig { it.copy(nameTransform = "uppercase") }; ntExpanded = false })
                            DropdownMenuItem({ Text(Strings.none) }, { viewModel.updateConfig { it.copy(nameTransform = "none") }; ntExpanded = false })
                        }
                    }
                    var itExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(itExpanded, { itExpanded = it }, Modifier.weight(1f)) {
                        LottieDropdown(
                            label = Strings.infoTransform,
                            value = if (cfg.infoTransform == "uppercase") Strings.uppercase else Strings.none,
                            expanded = itExpanded,
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(itExpanded, { itExpanded = false }) {
                            DropdownMenuItem({ Text(Strings.none) }, { viewModel.updateConfig { it.copy(infoTransform = "none") }; itExpanded = false })
                            DropdownMenuItem({ Text(Strings.uppercase) }, { viewModel.updateConfig { it.copy(infoTransform = "uppercase") }; itExpanded = false })
                        }
                    }
                }
            }

            // ═══ Colors ═══
            CollapsibleSection(Strings.sectionColors, initiallyExpanded = true) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ColorPickerRow(Strings.colorNameText, cfg.nameColor, cfg.nameColorAlpha,
                        { viewModel.updateConfig { c -> c.copy(nameColor = it) } },
                        { viewModel.updateConfig { c -> c.copy(nameColorAlpha = it) } })
                    ColorPickerRow(Strings.colorInfoText, cfg.infoColor, cfg.infoColorAlpha,
                        { viewModel.updateConfig { c -> c.copy(infoColor = it) } },
                        { viewModel.updateConfig { c -> c.copy(infoColorAlpha = it) } })
                    ColorPickerRow(Strings.colorAccent, cfg.accentColor, cfg.accentColorAlpha,
                        { viewModel.updateConfig { c -> c.copy(accentColor = it) } },
                        { viewModel.updateConfig { c -> c.copy(accentColorAlpha = it) } })
                    ColorPickerRow(Strings.colorBackground, cfg.bgColor, cfg.bgColorAlpha,
                        { viewModel.updateConfig { c -> c.copy(bgColor = it) } },
                        { viewModel.updateConfig { c -> c.copy(bgColorAlpha = it) } })
                    ColorPickerRow(Strings.colorBorder, cfg.borderColor, cfg.borderColorAlpha,
                        { viewModel.updateConfig { c -> c.copy(borderColor = it) } },
                        { viewModel.updateConfig { c -> c.copy(borderColorAlpha = it) } })
                }

                SubtleButton(Strings.saveColors, { viewModel.saveColorTheme() })

                viewModel.colorThemes.forEachIndexed { i, theme ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.loadColorTheme(i) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(theme.name, modifier = Modifier.weight(1f), fontSize = 12.sp, color = Tokens.LabelText, maxLines = 1)
                        DeleteIconButton({ viewModel.deleteColorTheme(i) })
                    }
                }
            }

            // ═══ Shape ═══
            CollapsibleSection(Strings.sectionShape) {
                SliderWithLabel(
                    Strings.corners, cfg.corners,
                    { viewModel.updateConfig { c -> c.copy(corners = it) } },
                    0f..2f, unit = "em", format = { "%.2f".format(it) }
                )
                SliderWithLabel(
                    Strings.borderThickness, cfg.borderThickness,
                    { viewModel.updateConfig { c -> c.copy(borderThickness = it) } },
                    0f..5f, format = { "%.2f".format(it) }
                )
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    FieldRow {
                        LottieCheckbox(Strings.showBackground, cfg.bgEnabled,
                            { viewModel.updateConfig { c -> c.copy(bgEnabled = it) } }, Modifier.weight(1f))
                        LottieCheckbox(Strings.shadow, cfg.shadowEnabled,
                            { viewModel.updateConfig { c -> c.copy(shadowEnabled = it) } }, Modifier.weight(1f))
                    }
                    FieldRow {
                        LottieCheckbox(Strings.hideName, cfg.hideName,
                            { viewModel.updateConfig { c -> c.copy(hideName = it) } }, Modifier.weight(1f))
                        LottieCheckbox(Strings.hideInfo, cfg.hideInfo,
                            { viewModel.updateConfig { c -> c.copy(hideInfo = it) } }, Modifier.weight(1f))
                    }
                }
            }

            // ═══ Logo ═══
            CollapsibleSection(
                Strings.sectionLogo,
                hint = if (cfg.logoEnabled) cfg.logoSelect.ifEmpty { Strings.logoNone } else Strings.logoNone
            ) {
                LottieCheckbox(Strings.showLogo, cfg.logoEnabled,
                    { viewModel.updateConfig { c -> c.copy(logoEnabled = it) } })

                var logoExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(logoExpanded, { logoExpanded = it }, Modifier.fillMaxWidth()) {
                    LottieDropdown(
                        label = Strings.logoLabel,
                        value = cfg.logoSelect.ifEmpty { Strings.logoNone },
                        expanded = logoExpanded,
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(logoExpanded, { logoExpanded = false }) {
                        DropdownMenuItem({ Text(Strings.logoNone) }, { viewModel.clearLogo(); logoExpanded = false })
                        viewModel.availableLogos.forEach { logo ->
                            DropdownMenuItem({ Text(logo) }, { viewModel.selectLogo(logo); logoExpanded = false })
                        }
                        DropdownMenuItem({ Text(Strings.logoImport) }, {
                            logoExpanded = false
                            SwingUtilities.invokeLater {
                                val chooser = JFileChooser()
                                chooser.fileFilter = FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "svg", "webp")
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    viewModel.importAndLoadLogo(chooser.selectedFile)
                                }
                            }
                        })
                    }
                }

                SliderWithLabel(
                    Strings.logoSize, cfg.logoSize,
                    { viewModel.updateConfig { c -> c.copy(logoSize = it) } },
                    2f..6f, unit = "em"
                )
            }

            // ═══ Timing ═══
            CollapsibleSection(
                Strings.sectionTiming,
                hint = "%.1fs".format(cfg.animDuration + cfg.holdDuration)
            ) {
                val timingIndex = TIMING_PRESETS.indexOfFirst {
                    it.animDuration == cfg.animDuration && it.holdDuration == cfg.holdDuration
                }
                SegmentedButtons(
                    labels = TIMING_PRESETS.map { it.label },
                    selectedIndex = timingIndex,
                    onSelect = { i ->
                        val p = TIMING_PRESETS[i]
                        viewModel.updateConfig { it.copy(animDuration = p.animDuration, holdDuration = p.holdDuration) }
                    }
                )
                FieldRow {
                    LottieTextField(
                        value = cfg.animDuration.toString(),
                        onValueChange = { v -> v.toFloatOrNull()?.let { viewModel.updateConfig { c -> c.copy(animDuration = it.coerceIn(0.5f, 20f)) } } },
                        label = Strings.animDuration, modifier = Modifier.weight(1f), fillWidth = true, singleLine = true
                    )
                    LottieTextField(
                        value = cfg.holdDuration.toString(),
                        onValueChange = { v -> v.toFloatOrNull()?.let { viewModel.updateConfig { c -> c.copy(holdDuration = it.coerceIn(0f, 30f)) } } },
                        label = Strings.holdDuration, modifier = Modifier.weight(1f), fillWidth = true, singleLine = true
                    )
                }
            }

            // ═══ Position ═══
            CollapsibleSection(Strings.sectionPosition) {
                SliderWithLabel(
                    Strings.marginH, cfg.marginH,
                    { viewModel.updateConfig { c -> c.copy(marginH = it) } },
                    0f..10f, unit = "rem"
                )
                SliderWithLabel(
                    Strings.marginV, cfg.marginV,
                    { viewModel.updateConfig { c -> c.copy(marginV = it) } },
                    0f..10f, unit = "rem"
                )
                SliderWithLabel(
                    Strings.lineSpacing, cfg.lineSpacing,
                    { viewModel.updateConfig { c -> c.copy(lineSpacing = it) } },
                    -0.5f..1f, unit = "em", format = { "%.2f".format(it) }
                )
            }

            // ═══ Actions ═══
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (viewModel.hasOutputDir) {
                    AccentButton(Strings.saveLowerThird, { viewModel.saveLowerThird() }, Modifier.weight(1f))
                } else {
                    AccentButton(
                        Strings.downloadJson,
                        {
                            SwingUtilities.invokeLater {
                                val chooser = JFileChooser()
                                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    viewModel.downloadJson(chooser.selectedFile)
                                }
                            }
                        },
                        Modifier.weight(1f)
                    )
                    SubtleButton(Strings.saveToLibrary, { viewModel.savePreset() }, Modifier.weight(1f))
                }
            }

            // ═══ Library ═══
            SectionCard(
                Strings.sectionLibrary,
                modifier = Modifier.padding(top = 3.dp),
                trailing = { SubtleButton(Strings.batchImport, { showBatchImport = true }, compact = true) }
            ) {
                if (viewModel.presets.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubtleButton(Strings.applyStyleToAll, { viewModel.applyStyleToAll() }, compact = true)
                        if (viewModel.hasOutputDir) {
                            SubtleButton(Strings.saveAllLowerThirds, { viewModel.batchDownloadAll(null) }, compact = true)
                        } else {
                            SubtleButton(Strings.saveAllLowerThirds, {
                                SwingUtilities.invokeLater {
                                    val chooser = JFileChooser()
                                    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        viewModel.batchDownloadAll(chooser.selectedFile)
                                    }
                                }
                            }, compact = true)
                        }
                    }
                }

                if (viewModel.presets.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                        Text(Strings.noPresets, fontSize = 12.sp, color = Tokens.UnitText)
                    }
                } else {
                    viewModel.presets.forEachIndexed { i, preset ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.loadPreset(i) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.name, fontSize = 12.sp, color = Tokens.PrimaryText, maxLines = 1)
                                Text(
                                    "${preset.config.canvasW}×${preset.config.canvasH} · ${StyleCatalog.labelFor(preset.config.style)}",
                                    fontSize = 10.sp,
                                    color = Tokens.UnitText,
                                    maxLines = 1
                                )
                            }
                            DeleteIconButton({ viewModel.deletePreset(i) })
                        }
                    }
                }
            }
        }
    }

    if (showBatchImport) {
        AlertDialog(
            onDismissRequest = { showBatchImport = false; batchImportText = "" },
            containerColor = Tokens.CardBg,
            title = { Text(Strings.batchImportTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tokens.TitleText) },
            text = {
                Column {
                    Text(Strings.batchImportHint, fontSize = 12.sp, color = Tokens.LabelText)
                    Spacer(Modifier.height(8.dp))
                    LottieTextField(
                        value = batchImportText,
                        onValueChange = { batchImportText = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        singleLine = false,
                        fillWidth = true,
                        placeholder = { Text(Strings.batchImportPlaceholder, fontSize = 12.sp) }
                    )
                }
            },
            confirmButton = {
                AccentButton(Strings.batchImportBtn, {
                    val (added, updated) = viewModel.batchImportPresets(batchImportText)
                    viewModel.updateStatusText(Strings.batchImportedStatus(added, updated))
                    showBatchImport = false
                    batchImportText = ""
                })
            },
            dismissButton = {
                SubtleButton(Strings.cancelBtn, { showBatchImport = false; batchImportText = "" })
            }
        )
    }
}
