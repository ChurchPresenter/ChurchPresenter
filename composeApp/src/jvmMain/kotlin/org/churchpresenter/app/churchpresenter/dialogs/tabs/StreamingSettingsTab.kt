package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.lottie_no_presets
import churchpresenter.composeapp.generated.resources.lottie_preset_add_pair
import churchpresenter.composeapp.generated.resources.lottie_preset_delete
import churchpresenter.composeapp.generated.resources.lottie_preset_import_file
import churchpresenter.composeapp.generated.resources.lottie_preset_label
import churchpresenter.composeapp.generated.resources.lottie_preset_label_hint
import churchpresenter.composeapp.generated.resources.lottie_preset_no_file
import churchpresenter.composeapp.generated.resources.lottie_preset_save
import churchpresenter.composeapp.generated.resources.lottie_presets
import churchpresenter.composeapp.generated.resources.lottie_presets_list
import churchpresenter.composeapp.generated.resources.lottie_select_preset
import churchpresenter.composeapp.generated.resources.replace
import churchpresenter.composeapp.generated.resources.search
import churchpresenter.composeapp.generated.resources.streaming_settings
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.composables.ImageIconButton
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.LottiePreset
import org.churchpresenter.app.churchpresenter.data.LottieSearchReplacePair
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.utils.createFileChooser
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.SwingUtilities

@Composable
fun StreamingSettingsTab(
    settings: AppSettings,
    lottiePresetsDir: File,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    // --- Preset importer state ---
    var importSourcePath by remember { mutableStateOf("") }
    var presetLabel by remember { mutableStateOf("") }
    var importPairs by remember { mutableStateOf(listOf(LottieSearchReplacePair())) }

    // --- Preview state ---
    var selectedPreset by remember { mutableStateOf<LottiePreset?>(null) }
    var previewIsPlaying by remember { mutableStateOf(false) }

    val searchLabel = stringResource(Res.string.search)
    val replaceLabel = stringResource(Res.string.replace)

    // Build preview JSON: from selected preset, or from current import form if file picked
    var debouncedImportPath by remember { mutableStateOf(importSourcePath) }
    var debouncedPairs by remember { mutableStateOf(importPairs) }
    LaunchedEffect(importSourcePath, importPairs) {
        delay(400)
        debouncedImportPath = importSourcePath
        debouncedPairs = importPairs
    }

    val previewJsonContent = remember(selectedPreset, debouncedImportPath, debouncedPairs) {
        when {
            selectedPreset != null -> {
                val f = File(lottiePresetsDir, selectedPreset!!.savedFileName)
                if (!f.exists()) return@remember ""
                var json = f.readText()
                for (p in selectedPreset!!.searchReplacePairs) {
                    if (p.search.isNotBlank()) json = replaceLottieTextInSettings(json, p.search, p.replace)
                }
                json
            }
            debouncedImportPath.isNotBlank() -> {
                val f = File(debouncedImportPath)
                if (!f.exists()) return@remember ""
                var json = f.readText()
                for (p in debouncedPairs) {
                    if (p.search.isNotBlank()) json = replaceLottieTextInSettings(json, p.search, p.replace)
                }
                json
            }
            else -> ""
        }
    }

    val composition by rememberLottieComposition(key = previewJsonContent) {
        LottieCompositionSpec.JsonString(previewJsonContent.ifBlank { "{}" })
    }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = previewIsPlaying,
        iterations = Int.MAX_VALUE
    )
    LaunchedEffect(previewJsonContent) {
        previewIsPlaying = previewJsonContent.isNotBlank()
    }
    val displayProgress = progress

    // Main layout: left = editor, right = preview
    Row(modifier = Modifier.fillMaxSize()) {

        // ── Left panel ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.streaming_settings),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider()

            // --- Preset importer ---
            Text(
                text = stringResource(Res.string.lottie_presets),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // File picker row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = if (importSourcePath.isBlank()) stringResource(Res.string.lottie_preset_no_file)
                            else File(importSourcePath).name,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text(stringResource(Res.string.lottie_preset_import_file), style = MaterialTheme.typography.labelSmall) }
                )
                Button(
                    onClick = {
                        SwingUtilities.invokeLater {
                            val chooser = createFileChooser {
                                fileSelectionMode = JFileChooser.FILES_ONLY
                                dialogTitle = "Select Lottie JSON File"
                                fileFilter = FileNameExtensionFilter("Lottie JSON (*.json)", "json")
                            }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                importSourcePath = chooser.selectedFile.absolutePath
                                selectedPreset = null // switch preview to import form
                                if (presetLabel.isBlank()) {
                                    presetLabel = chooser.selectedFile.nameWithoutExtension
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(Res.string.lottie_preset_import_file), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Label field
            OutlinedTextField(
                value = presetLabel,
                onValueChange = { presetLabel = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                label = { Text(stringResource(Res.string.lottie_preset_label), style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text(stringResource(Res.string.lottie_preset_label_hint), style = MaterialTheme.typography.bodySmall) }
            )

            // Search/replace pairs
            importPairs.forEachIndexed { index, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pair.search,
                        onValueChange = { v ->
                            importPairs = importPairs.toMutableList().also { it[index] = it[index].copy(search = v) }
                            selectedPreset = null
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        label = { Text(searchLabel, style = MaterialTheme.typography.labelSmall) }
                    )
                    Text("→", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    OutlinedTextField(
                        value = pair.replace,
                        onValueChange = { v ->
                            importPairs = importPairs.toMutableList().also { it[index] = it[index].copy(replace = v) }
                            selectedPreset = null
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        label = { Text(replaceLabel, style = MaterialTheme.typography.labelSmall) }
                    )
                    if (importPairs.size > 1) {
                        ImageIconButton(
                            onClick = { importPairs = importPairs.toMutableList().also { it.removeAt(index) } },
                            size = 36.dp
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = stringResource(Res.string.lottie_preset_delete),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Add pair + Save buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { importPairs = importPairs + LottieSearchReplacePair() }) {
                    Text(stringResource(Res.string.lottie_preset_add_pair), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (importSourcePath.isNotBlank() && presetLabel.isNotBlank()) {
                            val id = UUID.randomUUID().toString()
                            val savedFileName = "$id.json"
                            val destFile = File(lottiePresetsDir, savedFileName)
                            lottiePresetsDir.mkdirs()
                            File(importSourcePath).copyTo(destFile, overwrite = true)
                            val preset = LottiePreset(
                                id = id,
                                label = presetLabel,
                                savedFileName = savedFileName,
                                searchReplacePairs = importPairs.filter { it.search.isNotBlank() }
                            )
                            onSettingsChange { s ->
                                s.copy(streamingSettings = s.streamingSettings.copy(
                                    lottiePresets = s.streamingSettings.lottiePresets + preset
                                ))
                            }
                            // Reset form, select new preset for preview
                            selectedPreset = preset
                            importSourcePath = ""
                            presetLabel = ""
                            importPairs = listOf(LottieSearchReplacePair())
                        }
                    },
                    enabled = importSourcePath.isNotBlank() && presetLabel.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(Res.string.lottie_preset_save), style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider()

            // --- Saved presets list ---
            Text(
                text = stringResource(Res.string.lottie_presets_list),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            val presets = settings.streamingSettings.lottiePresets
            if (presets.isEmpty()) {
                Text(
                    text = stringResource(Res.string.lottie_no_presets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                presets.sortedBy { it.label.lowercase() }.forEach { preset ->
                    val isSelected = selectedPreset?.id == preset.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPreset = preset
                                importSourcePath = ""
                            },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    preset.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                if (preset.searchReplacePairs.isNotEmpty()) {
                                    Text(
                                        text = preset.searchReplacePairs.joinToString(", ") { "${it.search} → ${it.replace}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            ImageIconButton(
                                onClick = {
                                    if (selectedPreset?.id == preset.id) selectedPreset = null
                                    File(lottiePresetsDir, preset.savedFileName).delete()
                                    onSettingsChange { s ->
                                        s.copy(streamingSettings = s.streamingSettings.copy(
                                            lottiePresets = s.streamingSettings.lottiePresets.filter { it.id != preset.id }
                                        ))
                                    }
                                },
                                size = 36.dp
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_close),
                                    contentDescription = stringResource(Res.string.lottie_preset_delete),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Right panel — live preview ───────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = selectedPreset?.label
                    ?: if (importSourcePath.isNotBlank()) File(importSourcePath).nameWithoutExtension
                       else stringResource(Res.string.lottie_select_preset),
                style = MaterialTheme.typography.bodyMedium,
                color = if (previewJsonContent.isNotBlank()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            )

            // Lottie preview box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (previewJsonContent.isNotBlank()) {
                    Image(
                        painter = rememberLottiePainter(
                            composition = composition,
                            progress = { displayProgress }
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.lottie_select_preset),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

private fun replaceLottieTextInSettings(json: String, search: String, replacement: String): String {
    val escaped = Regex.escape(search)
    val result = json.replace(Regex(""""t"\s*:\s*"$escaped"""")) { """"t":"$replacement"""" }
    val charsStart = Regex(""""chars"\s*:\s*\[""").find(result) ?: return result
    var depth = 1
    var pos = charsStart.range.last + 1
    while (pos < result.length && depth > 0) {
        when (result[pos]) { '[' -> depth++; ']' -> depth-- }
        if (depth > 0) pos++
    }
    return """${result.substring(0, charsStart.range.first)}"chars":[]${result.substring(pos + 1)}"""
}
