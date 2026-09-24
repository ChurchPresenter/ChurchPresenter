package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.apply
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.customize_theme_accent
import churchpresenter.composeapp.generated.resources.customize_theme_auto
import churchpresenter.composeapp.generated.resources.customize_theme_background
import churchpresenter.composeapp.generated.resources.customize_theme_base
import churchpresenter.composeapp.generated.resources.customize_theme_base_dark
import churchpresenter.composeapp.generated.resources.customize_theme_base_light
import churchpresenter.composeapp.generated.resources.customize_theme_colors
import churchpresenter.composeapp.generated.resources.customize_theme_error
import churchpresenter.composeapp.generated.resources.customize_theme_font
import churchpresenter.composeapp.generated.resources.customize_theme_font_default
import churchpresenter.composeapp.generated.resources.customize_theme_more_colors_hint
import churchpresenter.composeapp.generated.resources.customize_theme_reset
import churchpresenter.composeapp.generated.resources.customize_theme_reset_color
import churchpresenter.composeapp.generated.resources.customize_theme_secondary
import churchpresenter.composeapp.generated.resources.customize_theme_selection
import churchpresenter.composeapp.generated.resources.customize_theme_success
import churchpresenter.composeapp.generated.resources.customize_theme_text
import churchpresenter.composeapp.generated.resources.customize_theme_text_size
import churchpresenter.composeapp.generated.resources.customize_theme_text_size_default
import churchpresenter.composeapp.generated.resources.customize_theme_text_size_extra_large
import churchpresenter.composeapp.generated.resources.customize_theme_text_size_hint
import churchpresenter.composeapp.generated.resources.customize_theme_text_size_large
import churchpresenter.composeapp.generated.resources.customize_theme_text_size_small
import churchpresenter.composeapp.generated.resources.customize_theme_title
import churchpresenter.composeapp.generated.resources.customize_theme_use_custom_colors
import churchpresenter.composeapp.generated.resources.customize_theme_use_custom_colors_hint
import churchpresenter.composeapp.generated.resources.customize_theme_use_default_font
import churchpresenter.composeapp.generated.resources.customize_theme_warning
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.preview
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.cpColorToHex
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.CustomThemeColors
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.UI_FONT_SCALES
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.components.SegmentTrack
import org.churchpresenter.theme.components.SegmentTrackItem
import org.churchpresenter.theme.customColorScheme
import org.churchpresenter.theme.customSemanticColorsFor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val PREVIEW_COLUMN_WIDTH = 320.dp
private val SECTION_GAP = 8.dp
private val SECTION_PADDING = 10.dp
private val ROW_GAP = 6.dp
private val SEGMENT_TRACK_HEIGHT = 36.dp
private val BASE_TRACK_WIDTH = 150.dp
private const val COLOR_GRID_COLUMNS = 4
private const val DISABLED_ALPHA = 0.45f

/** The labels of [UI_FONT_SCALES], in the same order. */
private val FONT_SCALE_LABELS: List<StringResource> = listOf(
    Res.string.customize_theme_text_size_small,
    Res.string.customize_theme_text_size_default,
    Res.string.customize_theme_text_size_large,
    Res.string.customize_theme_text_size_extra_large,
)

/**
 * The Customize Theme window, opened from View → Customize Theme… in the native menu bar.
 *
 * Deliberately not a Settings tab: a theme is chosen once, not every time Settings is opened. Nothing
 * changes until Apply or OK — the preview is drawn from the draft, while the window around it keeps
 * the theme that is actually in effect.
 */
@Composable
fun CustomizeThemeDialog(
    isVisible: Boolean,
    currentTheme: ThemeMode,
    initial: ThemeCustomizationChoice,
    onApply: (ThemeCustomizationChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, CUSTOMIZE_THEME_DIALOG_WIDTH, CUSTOMIZE_THEME_DIALOG_HEIGHT),
        width = CUSTOMIZE_THEME_DIALOG_WIDTH,
        height = CUSTOMIZE_THEME_DIALOG_HEIGHT,
    )

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(Res.string.customize_theme_title),
    ) {
        // A DialogWindow starts from the display's own density, so the applied text size is put back
        // here for the window's chrome; the raw density is kept for the preview, which scales itself.
        val windowDensity = LocalDensity.current
        ChurchPresenterTheme(themeMode = currentTheme) {
            CustomizeThemeContent(
                currentTheme = currentTheme,
                initial = initial,
                previewDensity = windowDensity,
                onApply = onApply,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
internal fun CustomizeThemeContent(
    currentTheme: ThemeMode,
    initial: ThemeCustomizationChoice,
    previewDensity: Density,
    onApply: (ThemeCustomizationChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val defaultFontLabel = stringResource(Res.string.customize_theme_font_default)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Scrolls only as a fallback — at the larger text sizes on a short screen.
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
                ) {
                    ColorsSection(draft = draft, onChange = { draft = it })
                    TextSection(
                        fontFamily = draft.fontFamily,
                        fontScale = draft.fontScale,
                        defaultLabel = defaultFontLabel,
                        onFontChange = { draft = draft.copy(fontFamily = if (it == defaultFontLabel) "" else it) },
                        onScaleChange = { draft = draft.copy(fontScale = it) },
                    )
                }
                SettingsSection(
                    title = stringResource(Res.string.preview),
                    modifier = Modifier.width(PREVIEW_COLUMN_WIDTH).fillMaxHeight(),
                ) {
                    CompositionLocalProvider(LocalDensity provides previewDensity) {
                        ChurchPresenterTheme(
                            themeMode = if (draft.useCustomColors) ThemeMode.CUSTOM else currentTheme,
                            customization = draft.toCustomization(),
                        ) {
                            ThemePreviewCard()
                        }
                    }
                }
            }
            HorizontalDivider()
            DialogButtons(
                onReset = { draft = defaultChoice(draft.useCustomColors) },
                onCancel = onDismiss,
                onApply = { onApply(draft) },
                onOk = {
                    onApply(draft)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun DialogButtons(onReset: () -> Unit, onCancel: () -> Unit, onApply: () -> Unit, onOk: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostButton(onClick = onReset) { Text(stringResource(Res.string.customize_theme_reset)) }
        Spacer(Modifier.weight(1f))
        // Apply keeps the window open to try another look; OK applies and closes.
        GhostButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
        Spacer(Modifier.size(8.dp))
        KeyButton(onClick = onApply) { Text(stringResource(Res.string.apply)) }
        Spacer(Modifier.size(8.dp))
        RaisedButton(onClick = onOk) { Text(stringResource(Res.string.ok)) }
    }
}

@Composable
private fun ColorsSection(draft: ThemeCustomizationChoice, onChange: (ThemeCustomizationChoice) -> Unit) {
    val enabled = draft.useCustomColors
    SettingsSection(title = stringResource(Res.string.customize_theme_colors)) {
        Column(modifier = Modifier.padding(SECTION_PADDING), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.customize_theme_use_custom_colors),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(Res.string.customize_theme_use_custom_colors_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RaisedSwitch(checked = enabled, onCheckedChange = { onChange(draft.copy(useCustomColors = it)) })
            }
            Column(
                modifier = if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
            ) {
                AccentAndBaseRow(draft = draft, enabled = enabled, onChange = onChange)
                MoreColorsGrid(draft = draft, enabled = enabled, onChange = onChange)
            }
        }
    }
}

@Composable
private fun AccentAndBaseRow(
    draft: ThemeCustomizationChoice,
    enabled: Boolean,
    onChange: (ThemeCustomizationChoice) -> Unit,
) {
    // Accent and base on one line: they are the two halves of one choice.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(Res.string.customize_theme_accent))
        ColorPickerField(
            color = draft.accentHex,
            onColorChange = { if (enabled) onChange(draft.copy(accentHex = it)) },
        )
        Spacer(Modifier.weight(1f))
        FieldLabel(stringResource(Res.string.customize_theme_base))
        val options = listOf(
            false to Res.string.customize_theme_base_light,
            true to Res.string.customize_theme_base_dark,
        )
        SegmentTrack(modifier = Modifier.width(BASE_TRACK_WIDTH).height(SEGMENT_TRACK_HEIGHT)) {
            options.forEach { (dark, label) ->
                SegmentTrackItem(
                    selected = draft.dark == dark,
                    // The track has no disabled state of its own; the row around it is dimmed instead.
                    onClick = { if (enabled) onChange(draft.copy(dark = dark)) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { Text(stringResource(label), color = LocalContentColor.current, maxLines = 1) }
            }
        }
    }
}

/** One optional colour: its label, the stored hex (blank for automatic), and how to change it. */
private class OptionalColor(
    val label: StringResource,
    val hex: String,
    val automatic: Color,
    val set: (CustomThemeColors, String) -> CustomThemeColors,
)

/**
 * The six optional colours, four to a row. Each shows the colour it will actually paint with — the
 * derived one while it is on Auto — so the grid reads as the palette, not as a form of blanks.
 */
@Composable
private fun MoreColorsGrid(
    draft: ThemeCustomizationChoice,
    enabled: Boolean,
    onChange: (ThemeCustomizationChoice) -> Unit,
) {
    val colors = draft.colors
    val derived = remember(draft) { draft.copy(colors = CustomThemeColors()).toCustomization() }
    val scheme = remember(derived) { customColorScheme(derived) }
    val status = remember(derived) { customSemanticColorsFor(derived) }
    val entries = listOf(
        OptionalColor(Res.string.customize_theme_background, colors.background, scheme.background) { c, v ->
            c.copy(background = v)
        },
        OptionalColor(Res.string.customize_theme_text, colors.text, scheme.onSurface) { c, v -> c.copy(text = v) },
        OptionalColor(Res.string.customize_theme_secondary, colors.secondary, scheme.secondary) { c, v ->
            c.copy(secondary = v)
        },
        OptionalColor(Res.string.customize_theme_selection, colors.selection, scheme.primaryContainer) { c, v ->
            c.copy(selection = v)
        },
        OptionalColor(Res.string.customize_theme_success, colors.success, status.success) { c, v ->
            c.copy(success = v)
        },
        OptionalColor(Res.string.customize_theme_warning, colors.warning, status.warning) { c, v ->
            c.copy(warning = v)
        },
        OptionalColor(Res.string.customize_theme_error, colors.error, scheme.error) { c, v -> c.copy(error = v) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        entries.chunked(COLOR_GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { entry ->
                    OptionalColorCell(
                        entry = entry,
                        modifier = Modifier.weight(1f),
                        onChange = { hex -> if (enabled) onChange(draft.copy(colors = entry.set(colors, hex))) },
                    )
                }
                repeat(COLOR_GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            stringResource(Res.string.customize_theme_more_colors_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OptionalColorCell(entry: OptionalColor, modifier: Modifier, onChange: (String) -> Unit) {
    val isAuto = entry.hex.isBlank()
    Column(modifier = modifier) {
        ColorPickerField(
            color = entry.hex.ifBlank { cpColorToHex(entry.automatic) },
            onColorChange = onChange,
            label = stringResource(entry.label),
            modifier = Modifier.fillMaxWidth(),
        )
        // Always one line tall, so a row of cells stays aligned whichever of them are set.
        Text(
            text = stringResource(
                if (isAuto) Res.string.customize_theme_auto else Res.string.customize_theme_reset_color,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (isAuto) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 4.dp, top = 2.dp)
                .then(if (isAuto) Modifier else Modifier.clickable { onChange("") }),
        )
    }
}

@Composable
private fun TextSection(
    fontFamily: String,
    fontScale: Float,
    defaultLabel: String,
    onFontChange: (String) -> Unit,
    onScaleChange: (Float) -> Unit,
) {
    val fonts = rememberSystemFonts()
    SettingsSection(title = stringResource(Res.string.customize_theme_font)) {
        Column(modifier = Modifier.padding(SECTION_PADDING), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FontSettingsDropdown(
                    modifier = Modifier.weight(1f),
                    value = fontFamily.ifBlank { defaultLabel },
                    fonts = fonts,
                    fillWidth = true,
                    onValueChange = onFontChange,
                )
                GhostButton(onClick = { onFontChange(defaultLabel) }, enabled = fontFamily.isNotBlank()) {
                    Text(stringResource(Res.string.customize_theme_use_default_font))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(stringResource(Res.string.customize_theme_text_size))
                SegmentTrack(modifier = Modifier.weight(1f).height(SEGMENT_TRACK_HEIGHT)) {
                    UI_FONT_SCALES.forEachIndexed { index, scale ->
                        SegmentTrackItem(
                            selected = fontScale == scale,
                            onClick = { onScaleChange(scale) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            Text(
                                stringResource(FONT_SCALE_LABELS[index]),
                                color = LocalContentColor.current,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(Res.string.customize_theme_text_size_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FieldLabel(label: String) {
    Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
}
