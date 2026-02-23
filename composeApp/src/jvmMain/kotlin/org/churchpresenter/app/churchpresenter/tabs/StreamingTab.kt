package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_skip_next
import churchpresenter.composeapp.generated.resources.lottie_no_presets
import churchpresenter.composeapp.generated.resources.lottie_pause_at_frame
import churchpresenter.composeapp.generated.resources.lottie_pause_duration
import churchpresenter.composeapp.generated.resources.lottie_play_next
import churchpresenter.composeapp.generated.resources.lottie_run_to_end
import churchpresenter.composeapp.generated.resources.lottie_select_preset
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.composables.ImageIconButton
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.LottiePreset
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
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
    val scope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<Job?>(null) }

    var selectedPreset by remember { mutableStateOf<LottiePreset?>(null) }
    val animatedProgress = remember { Animatable(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var pauseAtFrame by remember { mutableStateOf(false) }
    var pauseDurationMs by remember { mutableStateOf("2000") }

    val pauseFrame = selectedPreset?.pauseFrame ?: -1f
    val hasPauseFrame = pauseFrame in 0f..1f

    val jsonContent = remember(selectedPreset) {
        val preset = selectedPreset ?: return@remember ""
        val presetFile = File(lottiePresetsDir, preset.savedFileName)
        if (!presetFile.exists()) return@remember ""
        var json = presetFile.readText()
        for (pair in preset.searchReplacePairs) {
            if (pair.search.isNotBlank()) json = replaceLottieText(json, pair.search, pair.replace)
        }
        json
    }

    val composition by rememberLottieComposition(key = jsonContent) {
        LottieCompositionSpec.JsonString(jsonContent.ifBlank { "{}" })
    }

    val totalFrames = composition?.durationFrames?.toInt() ?: 0

    // Reset when preset changes
    LaunchedEffect(composition) {
        animJob?.cancel()
        animJob = null
        animatedProgress.snapTo(0f)
        isPlaying = false
    }

    fun totalDurationMs(): Long =
        ((composition?.durationFrames ?: 0f) / (composition?.frameRate ?: 30f) * 1000f)
            .toLong().coerceAtLeast(1L)

    // Start playing from current progress position, respecting pause-at-frame
    fun startPlaying() {
        val oldJob = animJob
        animJob = null
        isPlaying = true
        animJob = scope.launch {
            oldJob?.cancel()
            oldJob?.join()
            val durMs = totalDurationMs()
            val start = animatedProgress.value

            if (pauseAtFrame && hasPauseFrame && start < pauseFrame) {
                // Animate to pause frame
                val toPauseDur = (durMs * (pauseFrame - start)).toInt().coerceAtLeast(1)
                animatedProgress.animateTo(
                    targetValue = pauseFrame,
                    animationSpec = tween(durationMillis = toPauseDur, easing = LinearEasing)
                )
                isPlaying = false
                // Auto-resume after pause duration (0 = wait for manual Play Next)
                val pauseMs = pauseDurationMs.toLongOrNull() ?: 2000L
                if (pauseMs > 0) {
                    delay(pauseMs)
                    // Continue from pause frame to end
                    isPlaying = true
                    val remainDur = (durMs * (1f - pauseFrame)).toInt().coerceAtLeast(1)
                    animatedProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = remainDur, easing = LinearEasing)
                    )
                    isPlaying = false
                }
            } else {
                // Play from current position to end
                val segDur = (durMs * (1f - start)).toInt().coerceAtLeast(1)
                animatedProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = segDur, easing = LinearEasing)
                )
                isPlaying = false
            }
        }
    }

    val displayProgress = animatedProgress.value
    val canPlay = composition != null && jsonContent.isNotBlank()

    @Composable
    fun Tooltip(text: String, content: @Composable () -> Unit) {
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            },
            tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
            content = content
        )
    }

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

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = selectedPreset?.label ?: stringResource(Res.string.lottie_select_preset),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedPreset != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            // Lottie preview
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(16f / 9f)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (canPlay) {
                    Image(
                        painter = rememberLottiePainter(composition = composition, progress = { displayProgress }),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (selectedPreset != null) {
                    Text("⚠", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                }
            }

            // Controls row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause
                Tooltip(stringResource(if (isPlaying) Res.string.pause else Res.string.play)) {
                    ImageIconButton(
                        onClick = {
                            if (canPlay) {
                                if (isPlaying) {
                                    // Pause — cancel coroutine, freeze at current frame
                                    val job = animJob
                                    animJob = null
                                    isPlaying = false
                                    job?.cancel()
                                } else if (animatedProgress.value >= 1f) {
                                    // Restart from beginning
                                    scope.launch {
                                        animatedProgress.snapTo(0f)
                                        startPlaying()
                                    }
                                } else {
                                    // Resume from current position
                                    startPlaying()
                                }
                            }
                        },
                        size = 36.dp,
                        modifier = Modifier.background(
                            color = if (canPlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (canPlay) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }

                // Play Next — resume from current paused position
                val canResume = canPlay && !isPlaying && animatedProgress.value < 1f
                Tooltip(stringResource(Res.string.lottie_play_next)) {
                    ImageIconButton(
                        onClick = { if (canResume) startPlaying() },
                        size = 36.dp,
                        modifier = Modifier.background(
                            color = if (canResume) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_skip_next),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (canResume) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }

                // Run to End — play from first frame to last frame without stopping
                Tooltip(stringResource(Res.string.lottie_run_to_end)) {
                    ImageIconButton(
                        onClick = {
                            if (canPlay) {
                                val oldJob = animJob
                                animJob = null
                                isPlaying = false
                                animJob = scope.launch {
                                    oldJob?.cancel()
                                    oldJob?.join()
                                    animatedProgress.snapTo(0f)
                                    isPlaying = true
                                    val durMs = totalDurationMs()
                                    animatedProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = durMs.toInt(), easing = LinearEasing)
                                    )
                                    isPlaying = false
                                }
                            }
                        },
                        size = 36.dp,
                        modifier = Modifier.background(
                            color = if (canPlay) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_skip_next),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (canPlay) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }

            // Pause at frame checkbox + duration
            if (hasPauseFrame) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(checked = pauseAtFrame, onCheckedChange = { pauseAtFrame = it })
                    val frameLabel = if (totalFrames > 0)
                        "${stringResource(Res.string.lottie_pause_at_frame)} ${(pauseFrame * totalFrames).toInt()}"
                    else
                        stringResource(Res.string.lottie_pause_at_frame)
                    Text(text = frameLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    if (pauseAtFrame) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = pauseDurationMs,
                            onValueChange = { pauseDurationMs = it.filter { c -> c.isDigit() } },
                            singleLine = true,
                            modifier = Modifier.width(120.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                            label = { Text(stringResource(Res.string.lottie_pause_duration), style = MaterialTheme.typography.labelSmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }
        }
    }
}

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
