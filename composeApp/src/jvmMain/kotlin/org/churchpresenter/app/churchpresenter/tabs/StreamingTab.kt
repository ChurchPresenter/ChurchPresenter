package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.lottie_no_presets
import churchpresenter.composeapp.generated.resources.lottie_select_preset
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.app.churchpresenter.composables.ImageIconButton
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.LottiePreset
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
fun StreamingTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {}
) {
    val lottiePresetsDir = remember {
        File(System.getProperty("user.home"), ".churchpresenter/lottie_presets")
    }

    val presets = appSettings.streamingSettings.lottiePresets

    var selectedPreset by remember { mutableStateOf<LottiePreset?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var frozenProgress by remember { mutableStateOf(0f) }
    var isFrozen by remember { mutableStateOf(false) }

    // Build processed JSON for selected preset
    val jsonContent = remember(selectedPreset) {
        val preset = selectedPreset ?: return@remember ""
        val presetFile = File(lottiePresetsDir, preset.savedFileName)
        if (!presetFile.exists()) return@remember ""
        var json = presetFile.readText()
        for (pair in preset.searchReplacePairs) {
            if (pair.search.isNotBlank()) {
                json = replaceLottieText(json, pair.search, pair.replace)
            }
        }
        json
    }

    val composition by rememberLottieComposition(key = jsonContent) {
        LottieCompositionSpec.JsonString(jsonContent.ifBlank { "{}" })
    }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isPlaying,
        iterations = 1
    )

    // Freeze on last frame when animation finishes
    LaunchedEffect(progress) {
        if (progress >= 1f && isPlaying) {
            isPlaying = false
            frozenProgress = 1f
            isFrozen = true
        }
    }

    // Reset when composition changes (new preset selected)
    LaunchedEffect(composition) {
        frozenProgress = 0f
        isFrozen = false
        isPlaying = false
    }

    val displayProgress = if (isFrozen) frozenProgress else progress

    Row(modifier = modifier.fillMaxSize()) {
        // Left column — preset list
        LazyColumn(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            if (presets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.lottie_no_presets),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                items(presets.sortedBy { it.label.lowercase() }) { preset ->
                    val isSelected = selectedPreset?.id == preset.id
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedPreset = preset
                            isPlaying = false
                            isFrozen = false
                            frozenProgress = 0f
                        },
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            if (preset.searchReplacePairs.isNotEmpty()) {
                                Text(
                                    text = preset.searchReplacePairs
                                        .filter { it.search.isNotBlank() }
                                        .joinToString(", ") { it.replace.ifBlank { it.search } },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

        // Right column — preview + controls
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Preset name + play/pause button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = selectedPreset?.label ?: stringResource(Res.string.lottie_select_preset),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedPreset != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                ImageIconButton(
                    onClick = {
                        if (composition != null && jsonContent.isNotBlank()) {
                            if (isPlaying) {
                                isPlaying = false
                            } else {
                                // Replay from start if frozen at end
                                if (isFrozen) {
                                    isFrozen = false
                                    frozenProgress = 0f
                                }
                                isPlaying = true
                            }
                        }
                    },
                    size = 36.dp,
                    modifier = Modifier.background(
                        color = if (jsonContent.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                        contentDescription = stringResource(if (isPlaying) Res.string.pause else Res.string.play),
                        modifier = Modifier.size(18.dp),
                        tint = if (jsonContent.isNotBlank()) Color.White
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }

            // Lottie preview
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(16f / 9f)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (composition != null && jsonContent.isNotBlank()) {
                    Image(
                        painter = rememberLottiePainter(composition = composition, progress = { displayProgress }),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (selectedPreset != null) {
                    Text(
                        text = "⚠",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Replaces [search] with [replacement] in a Lottie JSON string.
 */
private fun replaceLottieText(json: String, search: String, replacement: String): String {
    val escaped = Regex.escape(search)
    var result = json.replace(Regex(""""t"\s*:\s*"$escaped"""")) { """"t":"$replacement"""" }
    result = clearCharsAtlas(result)
    return result
}

private fun clearCharsAtlas(json: String): String {
    val charsStart = Regex(""""chars"\s*:\s*\[""").find(json) ?: return json
    var depth = 1
    var pos = charsStart.range.last + 1
    while (pos < json.length && depth > 0) {
        when (json[pos]) { '[' -> depth++; ']' -> depth-- }
        if (depth > 0) pos++
    }
    val before = json.substring(0, charsStart.range.first)
    val after = json.substring(pos + 1)
    return """${before}"chars":[]${after}"""
}
