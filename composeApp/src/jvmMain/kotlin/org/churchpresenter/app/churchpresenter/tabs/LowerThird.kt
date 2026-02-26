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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.lottie_no_presets
import churchpresenter.composeapp.generated.resources.lottie_select_preset
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.composables.ImageIconButton
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LowerThirdTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    selectedLowerThirdItem: ScheduleItem.LowerThirdItem? = null,
    onAddToSchedule: (presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) -> Unit = { _, _, _, _ -> },
    onGoLive: (jsonContent: String, pauseAtFrame: Boolean, pauseFrame: Float, pauseDurationMs: Long) -> Unit = { _, _, _, _ -> }
) {
    val lottieFolder = appSettings.streamingSettings.lowerThirdFolder

    // Build file list from user-chosen folder
    val lottieFiles = remember(lottieFolder) {
        if (lottieFolder.isEmpty()) emptyList()
        else File(lottieFolder).takeIf { it.exists() && it.isDirectory }
            ?.listFiles { f -> f.extension.lowercase() == "json" }
            ?.sortedBy { it.nameWithoutExtension.lowercase() } ?: emptyList()
    }

    val scope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<Job?>(null) }

    var selectedFile by remember { mutableStateOf<File?>(null) }
    val animatedProgress = remember { Animatable(0f) }
    var isPlaying by remember { mutableStateOf(false) }

    // When a schedule item is clicked, find the matching file by name
    LaunchedEffect(selectedLowerThirdItem) {
        val item = selectedLowerThirdItem ?: return@LaunchedEffect
        val file = lottieFiles.find { it.nameWithoutExtension == item.presetLabel || it.name == item.presetLabel }
            ?: lottieFiles.find { it.nameWithoutExtension == item.presetId }
        if (file != null) {
            selectedFile = file
            animJob?.cancel()
            animJob = null
            animatedProgress.snapTo(0f)
            isPlaying = false
        }
    }

    val jsonContent = remember(selectedFile) {
        val f = selectedFile ?: return@remember ""
        if (!f.exists()) return@remember ""
        f.readText()
    }

    val composition by rememberLottieComposition(key = jsonContent) {
        LottieCompositionSpec.JsonString(jsonContent.ifBlank { "{}" })
    }

    // Reset when file changes
    LaunchedEffect(composition) {
        animJob?.cancel()
        animJob = null
        animatedProgress.snapTo(0f)
        isPlaying = false
    }

    fun totalDurationMs(): Long =
        ((composition?.durationFrames ?: 0f) / (composition?.frameRate ?: 30f) * 1000f)
            .toLong().coerceAtLeast(1L)

    fun startPlaying() {
        val oldJob = animJob
        animJob = null
        isPlaying = true
        animJob = scope.launch {
            oldJob?.cancel()
            oldJob?.join()
            val durMs = totalDurationMs()
            val start = animatedProgress.value
            val segDur = (durMs * (1f - start)).toInt().coerceAtLeast(1)
            animatedProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = segDur, easing = LinearEasing)
            )
            isPlaying = false
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
        // Left column — file list
        LazyColumn(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            if (lottieFiles.isEmpty()) {
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
                items(lottieFiles) { file ->
                    val isSelected = selectedFile?.absolutePath == file.absolutePath
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedFile = file
                            isPlaying = false
                        },
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = file.nameWithoutExtension,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
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
                text = selectedFile?.nameWithoutExtension ?: stringResource(Res.string.lottie_select_preset),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedFile != null) MaterialTheme.colorScheme.onSurface
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
                } else if (selectedFile != null) {
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
                                    val job = animJob
                                    animJob = null
                                    isPlaying = false
                                    job?.cancel()
                                } else if (animatedProgress.value >= 1f) {
                                    scope.launch {
                                        animatedProgress.snapTo(0f)
                                        startPlaying()
                                    }
                                } else {
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


                // Add to Schedule button
                if (selectedFile != null) {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        onClick = {
                            onAddToSchedule(
                                selectedFile!!.nameWithoutExtension,
                                selectedFile!!.nameWithoutExtension,
                                false,
                                0L
                            )
                        }
                    ) {
                        Text(text = stringResource(Res.string.add_to_schedule), style = MaterialTheme.typography.labelSmall)
                    }

                    // Go Live button
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        onClick = {
                            onGoLive(jsonContent, false, -1f, 0L)
                        },
                        enabled = canPlay
                    ) {
                        Text(text = stringResource(Res.string.go_live), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

