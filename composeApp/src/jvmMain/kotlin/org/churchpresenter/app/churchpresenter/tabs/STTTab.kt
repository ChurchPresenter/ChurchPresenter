package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import churchpresenter.composeapp.generated.resources.stt_background_color
import churchpresenter.composeapp.generated.resources.stt_connect
import churchpresenter.composeapp.generated.resources.stt_disconnect
import churchpresenter.composeapp.generated.resources.stt_display_mode
import churchpresenter.composeapp.generated.resources.stt_display_styling
import churchpresenter.composeapp.generated.resources.stt_drip_feed
import churchpresenter.composeapp.generated.resources.stt_drip_feed_speed
import churchpresenter.composeapp.generated.resources.stt_font
import churchpresenter.composeapp.generated.resources.stt_go_live
import churchpresenter.composeapp.generated.resources.stt_in_progress_text
import churchpresenter.composeapp.generated.resources.stt_layout
import churchpresenter.composeapp.generated.resources.stt_layout_side_by_side
import churchpresenter.composeapp.generated.resources.stt_layout_side_by_side_inverse
import churchpresenter.composeapp.generated.resources.stt_layout_stacked
import churchpresenter.composeapp.generated.resources.stt_layout_stacked_inverse
import churchpresenter.composeapp.generated.resources.stt_line_spacing
import churchpresenter.composeapp.generated.resources.stt_live_preview
import churchpresenter.composeapp.generated.resources.stt_max_lines
import churchpresenter.composeapp.generated.resources.stt_max_segments
import churchpresenter.composeapp.generated.resources.stt_mode_both
import churchpresenter.composeapp.generated.resources.stt_mode_transcribe
import churchpresenter.composeapp.generated.resources.stt_mode_translate
import churchpresenter.composeapp.generated.resources.stt_not_connected
import churchpresenter.composeapp.generated.resources.stt_opacity
import churchpresenter.composeapp.generated.resources.stt_position
import churchpresenter.composeapp.generated.resources.stt_server_url
import churchpresenter.composeapp.generated.resources.stt_size
import churchpresenter.composeapp.generated.resources.stt_text_color
import churchpresenter.composeapp.generated.resources.stt_transcription_label
import churchpresenter.composeapp.generated.resources.stt_translation_color
import churchpresenter.composeapp.generated.resources.stt_translation_in_progress
import churchpresenter.composeapp.generated.resources.stt_translation_label
import churchpresenter.composeapp.generated.resources.stt_waiting_for_transcription
import churchpresenter.composeapp.generated.resources.stt_word_highlighting
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun STTTab(
    modifier: Modifier = Modifier,
    sttManager: STTManager,
    presenterManager: org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager,
    presenting: (Presenting) -> Unit,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val sttSettings = appSettings.sttSettings
    val connected by sttManager.connected
    val connecting by sttManager.connecting
    val presentingMode by presenterManager.presentingMode
    val isLive = presentingMode == Presenting.STT
    val segments = sttManager.segments
    val inProgressText by sttManager.inProgressText
    val translationSegments = sttManager.translationSegments
    val inProgressTranslation by sttManager.inProgressTranslation

    val availableFonts = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }

    var urlInput by remember(sttSettings.serverUrl) { mutableStateOf(sttSettings.serverUrl.ifEmpty { "http://" }) }

    Row(modifier = modifier.fillMaxSize()) {
        // ── Left Panel: Connection + Live Preview ─────────────────────
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Connection row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text(stringResource(Res.string.stt_server_url)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    enabled = !connected && !connecting
                )

                // Status indicator
                Box(
                    modifier = Modifier.size(12.dp).clip(CircleShape).background(
                        when {
                            connected -> Color(0xFF43A047)
                            connecting -> Color(0xFFFFA726)
                            else -> Color(0xFFE53935)
                        }
                    )
                )

                if (connected) {
                    IconButton(
                        onClick = { sttManager.disconnect() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = stringResource(Res.string.stt_disconnect), modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(
                        onClick = {
                            val url = if (urlInput.isNotBlank() && !urlInput.startsWith("http://") && !urlInput.startsWith("https://")) "http://$urlInput" else urlInput
                            urlInput = url
                            onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(serverUrl = url)) }
                            sttManager.connect(url)
                        },
                        enabled = !connecting && urlInput.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF43A047),
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(Res.string.stt_connect), modifier = Modifier.size(20.dp))
                    }
                }

                // Go Live
                IconButton(
                    onClick = {
                        presenting(Presenting.STT)
                    },
                    enabled = connected && !isLive,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(Icons.Default.Tv, contentDescription = stringResource(Res.string.stt_go_live), modifier = Modifier.size(20.dp))
                }
            }

            // Display mode + Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Display mode dropdown
                DropdownSelector(
                    label = stringResource(Res.string.stt_display_mode),
                    value = sttSettings.displayMode,
                    options = listOf(
                        "transcribe" to stringResource(Res.string.stt_mode_transcribe),
                        "translate" to stringResource(Res.string.stt_mode_translate),
                        "both" to stringResource(Res.string.stt_mode_both)
                    ),
                    onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(displayMode = it)) } },
                    modifier = Modifier.weight(1f)
                )

                // Layout dropdown (only when both)
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
                        modifier = Modifier.width(200.dp)
                    )
                }
            }

            // Max segments, max lines, line spacing row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.stt_max_segments), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    NumberSettingsTextField(
                        initialText = sttSettings.maxSegments,
                        range = 0..100,
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(maxSegments = it)) } }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.stt_max_lines), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    NumberSettingsTextField(
                        initialText = sttSettings.maxLines,
                        range = 0..50,
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(maxLines = it)) } }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.stt_line_spacing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    NumberSettingsTextField(
                        initialText = sttSettings.lineSpacing,
                        range = 80..300,
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(lineSpacing = it)) } }
                    )
                }
            }

            // Toggles row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sttSettings.showWordHighlighting,
                        onCheckedChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(showWordHighlighting = it)) } }
                    )
                    Text(stringResource(Res.string.stt_word_highlighting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sttSettings.showInProgress,
                        onCheckedChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(showInProgress = it)) } }
                    )
                    Text(stringResource(Res.string.stt_in_progress_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sttSettings.showTranslationInProgress,
                        onCheckedChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(showTranslationInProgress = it)) } }
                    )
                    Text(stringResource(Res.string.stt_translation_in_progress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.stt_drip_feed_speed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    NumberSettingsTextField(
                        initialText = sttSettings.dripFeedSpeed,
                        range = 10..500,
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(dripFeedSpeed = it)) } }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Live preview area
            Text(stringResource(Res.string.stt_live_preview), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            val maxSeg = sttSettings.maxSegments
            val displaySegments = if (maxSeg > 0) segments.takeLast(maxSeg) else segments
            val displayTranslation = if (maxSeg > 0) translationSegments.takeLast(maxSeg) else translationSegments

            val showTranscription = sttSettings.displayMode == "transcribe" || sttSettings.displayMode == "both"
            val showTranslation = sttSettings.displayMode == "translate" || sttSettings.displayMode == "both"

            val hasTranscriptionContent = displaySegments.isNotEmpty() || inProgressText.isNotBlank()
            val hasTranslationContent = displayTranslation.isNotEmpty() || inProgressTranslation.isNotBlank()
            val hasContent = (showTranscription && hasTranscriptionContent) || (showTranslation && hasTranslationContent)

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                if (!connected) {
                    Text(
                        stringResource(Res.string.stt_not_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else if (!hasContent) {
                    Text(
                        stringResource(Res.string.stt_waiting_for_transcription),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val highlightedWords = sttManager.highlightedWords
                    val highlightingEnabled = sttSettings.showWordHighlighting && sttManager.wordHighlightingEnabled.value

                    // Transcription column
                    if (showTranscription) {
                        val transcriptionScrollState = rememberScrollState()
                        LaunchedEffect(displaySegments.size, inProgressText) {
                            transcriptionScrollState.animateScrollTo(transcriptionScrollState.maxValue)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(transcriptionScrollState).padding(4.dp)
                            ) {
                                Text(stringResource(Res.string.stt_transcription_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(4.dp))
                                displaySegments.forEach { segment ->
                                    Text(
                                        text = applyHighlighting(segment.text, highlightedWords, highlightingEnabled, MaterialTheme.colorScheme.onSurface),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                                if (sttSettings.showInProgress && inProgressText.isNotBlank()) {
                                    Text(
                                        text = inProgressText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(transcriptionScrollState)
                            )
                        }
                    }

                    // Translation column
                    if (showTranslation) {
                        val translationScrollState = rememberScrollState()
                        LaunchedEffect(displayTranslation.size, inProgressTranslation) {
                            translationScrollState.animateScrollTo(translationScrollState.maxValue)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(translationScrollState).padding(4.dp)
                            ) {
                                Text(stringResource(Res.string.stt_translation_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                displayTranslation.forEach { segment ->
                                    Text(
                                        text = applyHighlighting(segment.text, highlightedWords, highlightingEnabled, MaterialTheme.colorScheme.primary),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                                if (sttSettings.showTranslationInProgress && inProgressTranslation.isNotBlank()) {
                                    Text(
                                        text = inProgressTranslation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(translationScrollState)
                            )
                        }
                    }
                }
            }
        }

        // ── Right Panel: Display Styling ──────────────────────────────
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(Res.string.stt_display_styling), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            // Text color
            Text(stringResource(Res.string.stt_text_color), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(color = sttSettings.textColor, onColorChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(textColor = it)) } })
                TextStyleButtons(
                    bold = sttSettings.bold, italic = sttSettings.italic, underline = sttSettings.underline, shadow = sttSettings.shadow,
                    onBoldChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(bold = it)) } },
                    onItalicChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(italic = it)) } },
                    onUnderlineChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(underline = it)) } },
                    onShadowChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(shadow = it)) } }
                )
            }

            AnimatedVisibility(visible = sttSettings.shadow) {
                ShadowDetailRow(
                    shadowColor = sttSettings.shadowColor, shadowSize = sttSettings.shadowSize, shadowOpacity = sttSettings.shadowOpacity,
                    onColorChange = { c -> onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(shadowColor = c)) } },
                    onSizeChange = { v -> onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(shadowSize = v)) } },
                    onOpacityChange = { v -> onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(shadowOpacity = v)) } },
                )
            }

            // Translation text color
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.stt_translation_color), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            ColorPickerField(color = sttSettings.translationTextColor, onColorChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(translationTextColor = it)) } })

            // Font type + size
            Spacer(Modifier.height(8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(modifier = Modifier.width(140.dp)) {
                    Text(stringResource(Res.string.stt_font), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    FontSettingsDropdown(value = sttSettings.fontType, fonts = availableFonts, onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(fontType = it)) } }, modifier = Modifier.fillMaxWidth())
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.stt_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    NumberSettingsTextField(initialText = sttSettings.fontSize, range = 8..200, onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(fontSize = it)) } })
                }
            }

            // Background color
            Spacer(Modifier.height(8.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(stringResource(Res.string.stt_background_color), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                ColorPickerField(color = if (sttSettings.backgroundColor == "transparent") "#1E1E2E" else sttSettings.backgroundColor, onColorChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(backgroundColor = it)) } })
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.stt_opacity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    androidx.compose.material3.Slider(
                        value = sttSettings.backgroundOpacity / 100f,
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(backgroundOpacity = (it * 100).toInt())) } },
                        modifier = Modifier.weight(1f)
                    )
                    Text("${sttSettings.backgroundOpacity}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                }
            }

            // Position on screen
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.stt_position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
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
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = options.firstOrNull { it.first == value }?.second ?: value

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = {
                        onValueChange(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun applyHighlighting(
    text: String,
    highlightedWords: List<org.churchpresenter.app.churchpresenter.viewmodel.HighlightedWord>,
    enabled: Boolean,
    baseColor: Color
): androidx.compose.ui.text.AnnotatedString {
    if (!enabled || highlightedWords.isEmpty()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = baseColor)) { append(text) }
        }
    }
    // Build per-character color array then construct contiguous runs (no overlapping spans)
    val colors = Array(text.length) { baseColor }
    for (hw in highlightedWords) {
        if (hw.word.isBlank()) continue
        try {
            val highlightColor = org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor(hw.color)
            val wb = "(?<![\\p{L}\\p{N}])"
            val we = "(?![\\p{L}\\p{N}])"
            val rawPattern = if (hw.isRegex) {
                "$wb(?:${hw.word})$we"
            } else {
                val escaped = Regex.escape(hw.word)
                "$wb$escaped$we"
            }
            var flags = java.util.regex.Pattern.UNICODE_CHARACTER_CLASS
            if (!hw.caseSensitive) flags = flags or java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CASE
            val regex = java.util.regex.Pattern.compile(rawPattern, flags).toRegex()
            regex.findAll(text).forEach { match ->
                for (j in match.range) colors[j] = highlightColor
            }
        } catch (_: Exception) {}
    }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val color = colors[i]
            val start = i
            while (i < text.length && colors[i] == color) i++
            withStyle(SpanStyle(color = color)) { append(text.substring(start, i)) }
        }
    }
}
