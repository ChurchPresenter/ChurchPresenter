package org.churchpresenter.app.churchpresenter.tabs

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
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

    var urlInput by remember(sttSettings.serverUrl) { mutableStateOf(sttSettings.serverUrl) }

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
                    label = { Text("STT Server URL") },
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
                        Icon(Icons.Default.Stop, contentDescription = "Disconnect", modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(
                        onClick = {
                            onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(serverUrl = urlInput)) }
                            sttManager.connect(urlInput)
                        },
                        enabled = !connecting && urlInput.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF43A047),
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Connect", modifier = Modifier.size(20.dp))
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
                    Icon(Icons.Default.Tv, contentDescription = "Go Live", modifier = Modifier.size(20.dp))
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
                    label = "Display Mode",
                    value = sttSettings.displayMode,
                    options = listOf("transcribe" to "Transcription Only", "translate" to "Translation Only", "both" to "Both"),
                    onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(displayMode = it)) } },
                    modifier = Modifier.weight(1f)
                )

                // Layout dropdown (only when both)
                AnimatedVisibility(visible = sttSettings.displayMode == "both") {
                    DropdownSelector(
                        label = "Layout",
                        value = sttSettings.layout,
                        options = listOf(
                            "stacked" to "Stacked",
                            "stacked_inverse" to "Stacked (Inverse)",
                            "side_by_side" to "Side by Side",
                            "side_by_side_inverse" to "Side by Side (Inverse)"
                        ),
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(layout = it)) } },
                        modifier = Modifier.width(200.dp)
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
                    Text("Word Highlighting", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sttSettings.showInProgress,
                        onCheckedChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(showInProgress = it)) } }
                    )
                    Text("In-Progress Text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sttSettings.showTranslationInProgress,
                        onCheckedChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(showTranslationInProgress = it)) } }
                    )
                    Text("Translation In-Progress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Max Segments:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    NumberSettingsTextField(
                        initialText = sttSettings.maxSegments,
                        range = 0..100,
                        onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(maxSegments = it)) } }
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
                    Text("Drip Feed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speed (ms/word):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
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
            Text("Live Preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

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
                        "Not connected. Enter the STT server URL and click Connect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else if (!hasContent) {
                    Text(
                        "Waiting for transcription...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val highlightedWords = sttManager.highlightedWords
                    val highlightingEnabled = sttSettings.showWordHighlighting && sttManager.wordHighlightingEnabled.value

                    // Transcription column
                    if (showTranscription) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(4.dp)
                        ) {
                            Text("Transcription", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                    }

                    // Translation column
                    if (showTranslation) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(4.dp)
                        ) {
                            Text("Translation", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
            Text("Display Styling", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            // Text color
            Text("Text Color", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
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
            Text("Translation Color", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            ColorPickerField(color = sttSettings.translationTextColor, onColorChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(translationTextColor = it)) } })

            // Font type + size
            Spacer(Modifier.height(8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(modifier = Modifier.width(140.dp)) {
                    Text("Font", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    FontSettingsDropdown(value = sttSettings.fontType, fonts = availableFonts, onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(fontType = it)) } }, modifier = Modifier.fillMaxWidth())
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    NumberSettingsTextField(initialText = sttSettings.fontSize, range = 8..200, onValueChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(fontSize = it)) } })
                }
            }

            // Background color
            Spacer(Modifier.height(8.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text("Background Color", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                if (sttSettings.backgroundColor == "transparent") {
                    Button(onClick = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(backgroundColor = "#1E1E2E")) } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) { Text("Transparent", style = MaterialTheme.typography.labelMedium) }
                } else {
                    ColorPickerField(color = sttSettings.backgroundColor, onColorChange = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(backgroundColor = it)) } })
                    Button(onClick = { onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(backgroundColor = "transparent")) } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) { Text("Transparent", style = MaterialTheme.typography.labelMedium) }
                }
            }

            // Position on screen
            Spacer(Modifier.height(8.dp))
            Text("Position", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            val positions = listOf(
                Constants.TOP_LEFT to "TL", Constants.TOP_CENTER to "TC", Constants.TOP_RIGHT to "TR",
                Constants.CENTER_LEFT to "CL", Constants.CENTER to "C", Constants.CENTER_RIGHT to "CR",
                Constants.BOTTOM_LEFT to "BL", Constants.BOTTOM_CENTER to "BC", Constants.BOTTOM_RIGHT to "BR"
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
