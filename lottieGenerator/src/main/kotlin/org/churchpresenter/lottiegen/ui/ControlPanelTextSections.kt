package org.churchpresenter.lottiegen.ui
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.LottieGenState
import org.churchpresenter.lottiegen.model.LottieFont
import org.churchpresenter.lottiegen.ui.components.CollapsibleSection
import org.churchpresenter.lottiegen.ui.components.ColorPickerRow
import org.churchpresenter.lottiegen.ui.components.DeleteIconButton
import org.churchpresenter.lottiegen.ui.components.LottieDropdown
import org.churchpresenter.lottiegen.ui.components.LottieTextField
import org.churchpresenter.lottiegen.ui.components.SectionCard
import org.churchpresenter.lottiegen.ui.components.SubtleButton


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextSection(viewModel: LottieGenState) {
    val cfg = viewModel.config
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
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextStyleSection(viewModel: LottieGenState) {
    val cfg = viewModel.config
    CollapsibleSection(Strings.sectionTextStyle, hint = cfg.fontFamily) {
        FontAndSizeRows(viewModel)
        WeightRow(viewModel)
        CaseRow(viewModel)
    }
}


/** The family, the base size, and the two line sizes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontAndSizeRows(viewModel: LottieGenState) {
    val cfg = viewModel.config
    FieldRow {
        var fontExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(fontExpanded, { fontExpanded = it }, Modifier.weight(1f)) {
            LottieDropdown(
                label = Strings.font,
                value = cfg.fontFamily,
                expanded = fontExpanded,
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(fontExpanded, { fontExpanded = false }) {
                LottieFont.entries.forEach { font ->
                    DropdownMenuItem(
                        text = { Text(font.familyName) },
                        onClick = {
                            viewModel.updateConfig { it.copy(fontFamily = font.familyName) }
                            fontExpanded = false
                        }
                    )
                }
            }
        }
        LottieTextField(
            value = cfg.baseSize.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let {
                    viewModel.updateConfig { c ->
                        c.copy(baseSize = it.coerceIn(MIN_BASE_SIZE, MAX_BASE_SIZE))
                    }
                }
            },
            label = Strings.baseSize,
            modifier = Modifier.weight(1f), fillWidth = true, singleLine = true
        )
    }
    FieldRow {
        LottieTextField(
            value = cfg.nameSize.toString(),
            onValueChange = { v ->
                v.toFloatOrNull()?.let {
                    viewModel.updateConfig { c -> c.copy(nameSize = it.coerceIn(MIN_TEXT_EM, MAX_TEXT_EM)) }
                }
            },
            label = Strings.nameSize, modifier = Modifier.weight(1f), fillWidth = true, singleLine = true
        )
        LottieTextField(
            value = cfg.infoSize.toString(),
            onValueChange = { v ->
                v.toFloatOrNull()?.let {
                    viewModel.updateConfig { c -> c.copy(infoSize = it.coerceIn(MIN_TEXT_EM, MAX_TEXT_EM)) }
                }
            },
            label = Strings.infoSize, modifier = Modifier.weight(1f), fillWidth = true, singleLine = true
        )
    }
}


/** Bold or regular, per line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightRow(viewModel: LottieGenState) {
    val cfg = viewModel.config
    FieldRow {
        var nwExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(nwExpanded, { nwExpanded = it }, Modifier.weight(1f)) {
            LottieDropdown(
                label = Strings.nameWeight,
                value = if (cfg.nameWeight >= 700) Strings.bold else Strings.normal,
                expanded = nwExpanded,
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(nwExpanded, { nwExpanded = false }) {
                DropdownMenuItem(
                    { Text(Strings.bold) },
                    { viewModel.updateConfig { it.copy(nameWeight = 700) }; nwExpanded = false },
                )
                DropdownMenuItem(
                    { Text(Strings.normal) },
                    { viewModel.updateConfig { it.copy(nameWeight = 400) }; nwExpanded = false },
                )
            }
        }
        var iwExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(iwExpanded, { iwExpanded = it }, Modifier.weight(1f)) {
            LottieDropdown(
                label = Strings.infoWeight,
                value = if (cfg.infoWeight >= 700) Strings.bold else Strings.normal,
                expanded = iwExpanded,
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(iwExpanded, { iwExpanded = false }) {
                DropdownMenuItem(
                    { Text(Strings.normal) },
                    { viewModel.updateConfig { it.copy(infoWeight = 400) }; iwExpanded = false },
                )
                DropdownMenuItem(
                    { Text(Strings.bold) },
                    { viewModel.updateConfig { it.copy(infoWeight = 700) }; iwExpanded = false },
                )
            }
        }
    }
}


/** Uppercase or as typed, per line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaseRow(viewModel: LottieGenState) {
    val cfg = viewModel.config
    FieldRow {
        var ntExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(ntExpanded, { ntExpanded = it }, Modifier.weight(1f)) {
            LottieDropdown(
                label = Strings.nameTransform,
                value = if (cfg.nameTransform == "uppercase") Strings.uppercase else Strings.none,
                expanded = ntExpanded,
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(ntExpanded, { ntExpanded = false }) {
                DropdownMenuItem(
                    { Text(Strings.uppercase) },
                    { viewModel.updateConfig { it.copy(nameTransform = "uppercase") }; ntExpanded = false },
                )
                DropdownMenuItem(
                    { Text(Strings.none) },
                    { viewModel.updateConfig { it.copy(nameTransform = "none") }; ntExpanded = false },
                )
            }
        }
        var itExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(itExpanded, { itExpanded = it }, Modifier.weight(1f)) {
            LottieDropdown(
                label = Strings.infoTransform,
                value = if (cfg.infoTransform == "uppercase") Strings.uppercase else Strings.none,
                expanded = itExpanded,
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(itExpanded, { itExpanded = false }) {
                DropdownMenuItem(
                    { Text(Strings.none) },
                    { viewModel.updateConfig { it.copy(infoTransform = "none") }; itExpanded = false },
                )
                DropdownMenuItem(
                    { Text(Strings.uppercase) },
                    { viewModel.updateConfig { it.copy(infoTransform = "uppercase") }; itExpanded = false },
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorsSection(viewModel: LottieGenState) {
    val cfg = viewModel.config
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
                Text(
                    theme.name,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = Tokens.LabelText,
                    maxLines = 1,
                )
                DeleteIconButton({ viewModel.deleteColorTheme(i) })
            }
        }
    }
}
