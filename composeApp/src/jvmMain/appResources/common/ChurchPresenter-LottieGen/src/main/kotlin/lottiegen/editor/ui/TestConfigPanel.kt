package lottiegen.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lottiegen.editor.EditorState
import lottiegen.model.CANVAS_PRESETS
import lottiegen.persistence.LogoStorage
import lottiegen.spec.ColorRole
import lottiegen.ui.Strings
import lottiegen.ui.components.CollapsibleSection
import lottiegen.ui.components.ColorPickerRow
import lottiegen.ui.components.LottieTextField
import lottiegen.ui.components.SliderWithLabel

/**
 * The sample operator configuration the draft style is previewed against — the
 * editor-side stand-in for the generator's ControlPanel, reusing its widgets.
 */
@Composable
fun TestConfigPanel(state: EditorState) {
    val cfg = state.testConfig

    CollapsibleSection(Strings.editorTestConfig) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionDropdown(
                label = Strings.editorTestAlignment,
                options = listOf("left", "center", "right"),
                selected = cfg.align,
                display = { EditorLabels.align(it) },
                onSelect = { new -> state.updateTestConfig { it.copy(align = new) } }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LottieTextField(
                    value = cfg.nameText,
                    onValueChange = { new -> state.updateTestConfig { it.copy(nameText = new) } },
                    label = Strings.editorTestName,
                    modifier = Modifier.weight(1f)
                )
                LottieTextField(
                    value = cfg.infoText,
                    onValueChange = { new -> state.updateTestConfig { it.copy(infoText = new) } },
                    label = Strings.editorTestInfo,
                    modifier = Modifier.weight(1f)
                )
            }
            CheckboxRow(
                label = Strings.editorRuleName,
                checked = !cfg.hideName,
                onCheckedChange = { new -> state.updateTestConfig { it.copy(hideName = !new) } }
            )
            CheckboxRow(
                label = Strings.editorRuleInfo,
                checked = !cfg.hideInfo,
                onCheckedChange = { new -> state.updateTestConfig { it.copy(hideInfo = !new) } }
            )
            CheckboxRow(
                label = Strings.editorRuleBg,
                checked = cfg.bgEnabled,
                onCheckedChange = { new -> state.updateTestConfig { it.copy(bgEnabled = new) } }
            )

            val logoOptions = listOf("") + LogoStorage.listLogos()
            OptionDropdown(
                label = Strings.editorTestLogo,
                options = logoOptions,
                selected = if (cfg.logoEnabled) cfg.logoSelect else "",
                display = { if (it.isEmpty()) Strings.editorNone else it },
                onSelect = { name ->
                    if (name.isEmpty()) {
                        state.updateTestConfig {
                            it.copy(logoEnabled = false, logoData = null, logoW = 0, logoH = 0, logoSelect = "")
                        }
                    } else {
                        LogoStorage.loadLogoData(LogoStorage.getLogoFile(name))?.let { data ->
                            state.updateTestConfig {
                                it.copy(
                                    logoEnabled = true, logoData = data.dataUrl,
                                    logoW = data.width, logoH = data.height, logoSelect = name
                                )
                            }
                        }
                    }
                }
            )

            SliderWithLabel(
                label = Strings.editorTestBorder,
                value = cfg.borderThickness,
                onValueChange = { new -> state.updateTestConfig { it.copy(borderThickness = new) } },
                valueRange = 0f..5f
            )

            for (role in ColorRole.entries) {
                val (color, alpha) = when (role) {
                    ColorRole.NAME -> cfg.nameColor to cfg.nameColorAlpha
                    ColorRole.INFO -> cfg.infoColor to cfg.infoColorAlpha
                    ColorRole.ACCENT -> cfg.accentColor to cfg.accentColorAlpha
                    ColorRole.BG -> cfg.bgColor to cfg.bgColorAlpha
                    ColorRole.BORDER -> cfg.borderColor to cfg.borderColorAlpha
                }
                ColorPickerRow(
                    label = EditorLabels.role(role),
                    color = color,
                    alpha = alpha,
                    onColorChange = { new ->
                        state.updateTestConfig {
                            when (role) {
                                ColorRole.NAME -> it.copy(nameColor = new)
                                ColorRole.INFO -> it.copy(infoColor = new)
                                ColorRole.ACCENT -> it.copy(accentColor = new)
                                ColorRole.BG -> it.copy(bgColor = new)
                                ColorRole.BORDER -> it.copy(borderColor = new)
                            }
                        }
                    },
                    onAlphaChange = { new ->
                        state.updateTestConfig {
                            when (role) {
                                ColorRole.NAME -> it.copy(nameColorAlpha = new)
                                ColorRole.INFO -> it.copy(infoColorAlpha = new)
                                ColorRole.ACCENT -> it.copy(accentColorAlpha = new)
                                ColorRole.BG -> it.copy(bgColorAlpha = new)
                                ColorRole.BORDER -> it.copy(borderColorAlpha = new)
                            }
                        }
                    }
                )
            }

            OptionDropdown(
                label = Strings.editorTestCanvas,
                options = CANVAS_PRESETS,
                selected = CANVAS_PRESETS.firstOrNull { it.width == cfg.canvasW && it.height == cfg.canvasH }
                    ?: CANVAS_PRESETS.first(),
                display = { "${it.label} (${it.width}×${it.height})" },
                onSelect = { preset ->
                    state.updateTestConfig { it.copy(canvasW = preset.width, canvasH = preset.height) }
                }
            )

            SliderWithLabel(
                label = Strings.editorTestAnimDuration,
                value = cfg.animDuration,
                onValueChange = { new -> state.updateTestConfig { it.copy(animDuration = new) } },
                valueRange = 0.5f..10f
            )
            SliderWithLabel(
                label = Strings.editorTestHoldDuration,
                value = cfg.holdDuration,
                onValueChange = { new -> state.updateTestConfig { it.copy(holdDuration = new) } },
                valueRange = 0f..10f
            )
        }
    }
}
