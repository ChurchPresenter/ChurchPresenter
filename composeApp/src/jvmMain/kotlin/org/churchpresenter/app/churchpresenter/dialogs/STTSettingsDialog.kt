package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_engine_detect
import churchpresenter.composeapp.generated.resources.bible_engine_host
import churchpresenter.composeapp.generated.resources.bible_engine_port
import churchpresenter.composeapp.generated.resources.bible_engine_run_local
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.qa_pos_bc
import churchpresenter.composeapp.generated.resources.qa_pos_bl
import churchpresenter.composeapp.generated.resources.qa_pos_br
import churchpresenter.composeapp.generated.resources.qa_pos_c
import churchpresenter.composeapp.generated.resources.qa_pos_cl
import churchpresenter.composeapp.generated.resources.qa_pos_cr
import churchpresenter.composeapp.generated.resources.qa_pos_tc
import churchpresenter.composeapp.generated.resources.qa_pos_tl
import churchpresenter.composeapp.generated.resources.qa_pos_tr
import churchpresenter.composeapp.generated.resources.stt_background_color
import churchpresenter.composeapp.generated.resources.stt_display_mode
import churchpresenter.composeapp.generated.resources.stt_display_styling
import churchpresenter.composeapp.generated.resources.stt_drip_feed
import churchpresenter.composeapp.generated.resources.stt_drip_feed_speed
import churchpresenter.composeapp.generated.resources.stt_font
import churchpresenter.composeapp.generated.resources.stt_help_dev_mode
import churchpresenter.composeapp.generated.resources.stt_in_progress_text
import churchpresenter.composeapp.generated.resources.stt_layout
import churchpresenter.composeapp.generated.resources.stt_layout_side_by_side
import churchpresenter.composeapp.generated.resources.stt_layout_side_by_side_inverse
import churchpresenter.composeapp.generated.resources.stt_layout_stacked
import churchpresenter.composeapp.generated.resources.stt_layout_stacked_inverse
import churchpresenter.composeapp.generated.resources.stt_line_spacing
import churchpresenter.composeapp.generated.resources.stt_max_lines
import churchpresenter.composeapp.generated.resources.stt_max_segments
import churchpresenter.composeapp.generated.resources.stt_mode_both
import churchpresenter.composeapp.generated.resources.stt_mode_transcribe
import churchpresenter.composeapp.generated.resources.stt_mode_translate
import churchpresenter.composeapp.generated.resources.stt_opacity
import churchpresenter.composeapp.generated.resources.stt_position
import churchpresenter.composeapp.generated.resources.stt_settings_dialog_title
import churchpresenter.composeapp.generated.resources.stt_size
import churchpresenter.composeapp.generated.resources.stt_text_color
import churchpresenter.composeapp.generated.resources.stt_translation_color
import churchpresenter.composeapp.generated.resources.stt_translation_in_progress
import churchpresenter.composeapp.generated.resources.stt_word_highlighting
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.StyledTextField
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun STTSettingsDialog(
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sttSettings = appSettings.sttSettings
    val engine = appSettings.bibleEngineSettings

    val availableFonts = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }

    val mainWindowState = LocalMainWindowState.current
    val dialogWidth = 560.dp
    val dialogHeight = 640.dp
    val maxDialogHeight = 900.dp
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, dialogWidth, dialogHeight),
        width = dialogWidth,
        height = dialogHeight
    )

    // Grow the window (never shrink) when content overflows the current viewport, instead of
    // relying on a single guessed-at fixed height — the scroll stays as a fallback beyond the cap.
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val overflow = with(density) { scrollState.maxValue.toDp() }
            val grown = (dialogState.size.height + overflow).coerceAtMost(maxDialogHeight)
            if (grown > dialogState.size.height) {
                dialogState.size = DpSize(dialogState.size.width, grown)
            }
        }
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(Res.string.stt_settings_dialog_title),
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scripture detection (Bible Lookup Engine) — the engine starts with the STT connection.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(
                        checked = engine.enabled,
                        onCheckedChange = { onSettingsChange { s -> s.copy(bibleEngineSettings = s.bibleEngineSettings.copy(enabled = it)) } }
                    )
                    Text(stringResource(Res.string.bible_engine_detect), color = MaterialTheme.colorScheme.onSurface)
                    // Toggle hidden for now — engine always runs locally (runLocal defaults true).
                    // Flip to true to expose the local/remote choice again.
                    @Suppress("KotlinConstantConditions")
                    val showRunEngineLocallyToggle = false
                    if (showRunEngineLocallyToggle) {
                        Spacer(Modifier.weight(1f))
                        Checkbox(
                            checked = engine.runLocal,
                            enabled = engine.enabled,
                            onCheckedChange = { onSettingsChange { s -> s.copy(bibleEngineSettings = s.bibleEngineSettings.copy(runLocal = it)) } }
                        )
                        Text(stringResource(Res.string.bible_engine_run_local), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                AnimatedVisibility(visible = engine.enabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = engine.helpDevMode,
                            onCheckedChange = { onSettingsChange { s -> s.copy(bibleEngineSettings = s.bibleEngineSettings.copy(helpDevMode = it)) } }
                        )
                        Text(stringResource(Res.string.stt_help_dev_mode), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                AnimatedVisibility(visible = engine.enabled && !engine.runLocal) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StyledTextField(
                            value = engine.host,
                            onValueChange = { onSettingsChange { s -> s.copy(bibleEngineSettings = s.bibleEngineSettings.copy(host = it)) } },
                            label = stringResource(Res.string.bible_engine_host),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        StyledTextField(
                            value = engine.port.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { p -> onSettingsChange { s -> s.copy(bibleEngineSettings = s.bibleEngineSettings.copy(port = p)) } } },
                            label = stringResource(Res.string.bible_engine_port),
                            singleLine = true,
                            modifier = Modifier.width(120.dp)
                        )
                    }
                }

                // Display mode + Layout + numeric fields — FlowRow so each control wraps onto a
                // new line as a whole unit when narrow instead of squeezing internally and growing
                // tall (DropdownSelector's value text is now capped to 1 line as a second guard).
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DropdownSelector(
                        label = stringResource(Res.string.stt_display_mode),
                        value = sttSettings.displayMode,
                        options = listOf(
                            "transcribe" to stringResource(Res.string.stt_mode_transcribe),
                            "translate" to stringResource(Res.string.stt_mode_translate),
                            "both" to stringResource(Res.string.stt_mode_both)
                        ),
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(displayMode = it)) } },
                        modifier = Modifier.widthIn(min = 120.dp)
                    )
                    AnimatedVisibility(visible = sttSettings.displayMode == "both") {
                        DropdownSelector(
                            label = stringResource(Res.string.stt_layout),
                            value = sttSettings.layout,
                            options = listOf(
                                "stacked" to stringResource(Res.string.stt_layout_stacked),
                                "stacked_inverse" to stringResource(Res.string.stt_layout_stacked_inverse),
                                "side_by_side" to stringResource(Res.string.stt_layout_side_by_side),
                                "side_by_side_inverse" to stringResource(Res.string.stt_layout_side_by_side_inverse)
                            ),
                            onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(layout = it)) } },
                            modifier = Modifier.widthIn(min = 120.dp)
                        )
                    }
                    NumberSettingsTextField(
                        label = stringResource(Res.string.stt_max_segments),
                        initialText = sttSettings.maxSegments,
                        range = 0..100,
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(maxSegments = it)) } }
                    )
                    NumberSettingsTextField(
                        label = stringResource(Res.string.stt_max_lines),
                        initialText = sttSettings.maxLines,
                        range = 0..50,
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(maxLines = it)) } }
                    )
                    NumberSettingsTextField(
                        label = stringResource(Res.string.stt_line_spacing),
                        initialText = sttSettings.lineSpacing,
                        range = 80..300,
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(lineSpacing = it)) } }
                    )
                }

                // Toggles row — FlowRow wraps each checkbox+label pair as a whole unit onto a new
                // line when narrow, instead of a plain Row letting the labels wrap internally and
                // grow the row unboundedly tall.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = sttSettings.showWordHighlighting,
                            onCheckedChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(showWordHighlighting = it)) } }
                        )
                        Text(
                            stringResource(Res.string.stt_word_highlighting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = sttSettings.showInProgress,
                            onCheckedChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(showInProgress = it)) } }
                        )
                        Text(
                            stringResource(Res.string.stt_in_progress_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = sttSettings.showTranslationInProgress,
                            onCheckedChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(showTranslationInProgress = it)) } }
                        )
                        Text(
                            stringResource(Res.string.stt_translation_in_progress),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Drip feed settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = sttSettings.dripFeedEnabled,
                            onCheckedChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(dripFeedEnabled = it)) } }
                        )
                        Text(stringResource(Res.string.stt_drip_feed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    NumberSettingsTextField(
                        label = stringResource(Res.string.stt_drip_feed_speed),
                        initialText = sttSettings.dripFeedSpeed,
                        range = 1..1000,
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(dripFeedSpeed = it)) } },
                        modifier = Modifier.width(130.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ── Display Styling ───────────────────────────────────────
                // Two columns side-by-side (text/font/background styling | position + opacity)
                // instead of one long stacked column — keeps the dialog compact.
                Text(stringResource(Res.string.stt_display_styling), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Left: Text, font & background styling ────────────
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ColorPickerField(label = stringResource(Res.string.stt_text_color), color = sttSettings.textColor, onColorChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(textColor = it)) } }, modifier = Modifier.fillMaxWidth())
                        TextStyleButtons(
                            bold = sttSettings.bold, italic = sttSettings.italic, underline = sttSettings.underline, shadow = sttSettings.shadow,
                            onBoldChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(bold = it)) } },
                            onItalicChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(italic = it)) } },
                            onUnderlineChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(underline = it)) } },
                            onShadowChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(shadow = it)) } }
                        )
                        AnimatedVisibility(visible = sttSettings.shadow) {
                            ShadowDetailRow(
                                shadowColor = sttSettings.shadowColor, shadowSize = sttSettings.shadowSize, shadowOpacity = sttSettings.shadowOpacity,
                                onColorChange = { c -> onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(shadowColor = c)) } },
                                onSizeChange = { v -> onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(shadowSize = v)) } },
                                onOpacityChange = { v -> onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(shadowOpacity = v)) } },
                            )
                        }
                        ColorPickerField(label = stringResource(Res.string.stt_translation_color), color = sttSettings.translationTextColor, onColorChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(translationTextColor = it)) } }, modifier = Modifier.fillMaxWidth())
                        ColorPickerField(label = stringResource(Res.string.stt_background_color), color = sttSettings.backgroundColor, onColorChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(backgroundColor = it)) } }, modifier = Modifier.fillMaxWidth())
                    }

                    // ── Right: Font/Size + Position + Opacity ─────────────
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FontSettingsDropdown(label = stringResource(Res.string.stt_font), value = sttSettings.fontType, fonts = availableFonts, onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(fontType = it)) } }, modifier = Modifier.weight(1f))
                            NumberSettingsTextField(label = stringResource(Res.string.stt_size), initialText = sttSettings.fontSize, range = 8..200, onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(fontSize = it)) } })
                        }
                        Text(stringResource(Res.string.stt_position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        val positions = listOf(
                            Constants.TOP_LEFT to stringResource(Res.string.qa_pos_tl),
                            Constants.TOP_CENTER to stringResource(Res.string.qa_pos_tc),
                            Constants.TOP_RIGHT to stringResource(Res.string.qa_pos_tr),
                            Constants.CENTER_LEFT to stringResource(Res.string.qa_pos_cl),
                            Constants.CENTER to stringResource(Res.string.qa_pos_c),
                            Constants.CENTER_RIGHT to stringResource(Res.string.qa_pos_cr),
                            Constants.BOTTOM_LEFT to stringResource(Res.string.qa_pos_bl),
                            Constants.BOTTOM_CENTER to stringResource(Res.string.qa_pos_bc),
                            Constants.BOTTOM_RIGHT to stringResource(Res.string.qa_pos_br)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                            positions.chunked(3).forEach { rowItems ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    rowItems.forEach { (posConst, posLabel) ->
                                        val isSelected = sttSettings.position == posConst
                                        Box(
                                            modifier = Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(3.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(position = posConst)) } },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(posLabel, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.stt_opacity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(4.dp))
                            SlimSlider(
                                value = sttSettings.backgroundOpacity / 100f,
                                onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(backgroundOpacity = (it * 100).toInt())) } },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f),
                                trailingLabel = "${sttSettings.backgroundOpacity}%"
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(6.dp),
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(Res.string.close))
                }
            }
        }
    }
}
