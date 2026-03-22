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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.confirm_delete
import churchpresenter.composeapp.generated.resources.confirm_delete_file
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_cast
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.lottie_no_presets
import churchpresenter.composeapp.generated.resources.lottie_select_preset
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import churchpresenter.composeapp.generated.resources.tooltip_remove
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.swing.JOptionPane
import org.churchpresenter.app.churchpresenter.composables.ImageIconButton
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.presenterAspectRatio
import org.churchpresenter.app.churchpresenter.utils.formatAspectRatio
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.generate_lower_third
import churchpresenter.composeapp.generated.resources.aspect_ratio_mismatch
import churchpresenter.composeapp.generated.resources.lower_third_generator
import churchpresenter.composeapp.generated.resources.loading_generator
import churchpresenter.composeapp.generated.resources.server_not_running_title
import churchpresenter.composeapp.generated.resources.server_not_running_message
import churchpresenter.composeapp.generated.resources.no_folder_title
import churchpresenter.composeapp.generated.resources.no_folder_message
import churchpresenter.composeapp.generated.resources.choose_logo_image
import churchpresenter.composeapp.generated.resources.images_filter
import org.churchpresenter.app.churchpresenter.dialogs.tabs.openLottieGeneratorDialog
import java.awt.Window
import org.churchpresenter.app.churchpresenter.viewmodel.isLottieFile
import java.io.File
import javax.swing.SwingUtilities

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LowerThirdTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    selectedLowerThirdItem: ScheduleItem.LowerThirdItem? = null,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: (presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) -> Unit = { _, _, _, _ -> },
    onGoLive: (jsonContent: String, pauseAtFrame: Boolean, pauseFrame: Float, pauseDurationMs: Long) -> Unit = { _, _, _, _ -> },
    isDarkTheme: Boolean = true,
    serverUrl: String = ""
) {
    val lottieFolder = appSettings.streamingSettings.lowerThirdFolder
    var refreshKey by remember { mutableStateOf(0) }

    // Build file list from user-chosen folder
    val lottieFiles = remember(lottieFolder, refreshKey) {
        if (lottieFolder.isEmpty()) emptyList()
        else File(lottieFolder).takeIf { it.exists() && it.isDirectory }
            ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
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

    val density = LocalDensity.current
    var listWidthPx by remember(appSettings.streamingSettings.lowerThirdListWidthDp) {
        mutableStateOf(with(density) { appSettings.streamingSettings.lowerThirdListWidthDp.dp.toPx() })
    }
    val listWidthDp = with(density) { listWidthPx.toDp() }
    val onSettingsChangeState = rememberUpdatedState(onSettingsChange)

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
        // Left column — file list (resizable) + generate button
        Column(
            modifier = Modifier
                .width(listWidthDp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                        val confirmTitle = stringResource(Res.string.confirm_delete)
                        val confirmMsg = stringResource(Res.string.confirm_delete_file, file.name)
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedFile = file
                                isPlaying = false
                            },
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = file.nameWithoutExtension,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                                )
                                Icon(
                                    painter = painterResource(Res.drawable.ic_close),
                                    contentDescription = stringResource(Res.string.tooltip_remove),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .offset(y = 1.dp)
                                        .clickable {
                                            SwingUtilities.invokeLater {
                                                val result = JOptionPane.showConfirmDialog(
                                                    Window.getWindows().firstOrNull { it.isActive },
                                                    confirmMsg,
                                                    confirmTitle,
                                                    JOptionPane.YES_NO_OPTION,
                                                    JOptionPane.WARNING_MESSAGE
                                                )
                                                if (result == JOptionPane.YES_OPTION) {
                                                    file.delete()
                                                    if (selectedFile?.absolutePath == file.absolutePath) {
                                                        selectedFile = null
                                                    }
                                                    refreshKey++
                                                }
                                            }
                                        },
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            val genTitle = stringResource(Res.string.lower_third_generator)
            val genLoading = stringResource(Res.string.loading_generator)
            val srvTitle = stringResource(Res.string.server_not_running_title)
            val srvMsg = stringResource(Res.string.server_not_running_message)
            val nfTitle = stringResource(Res.string.no_folder_title)
            val nfMsg = stringResource(Res.string.no_folder_message)
            val logoTitle = stringResource(Res.string.choose_logo_image)
            val imgFilter = stringResource(Res.string.images_filter)
            Button(
                onClick = {
                    SwingUtilities.invokeLater {
                        openLottieGeneratorDialog(
                            parentWindow = Window.getWindows().firstOrNull { it.isActive },
                            onFileSaved = { scope.launch { refreshKey++ } },
                            serverUrl = serverUrl,
                            isDarkTheme = isDarkTheme,
                            lowerThirdFolder = appSettings.streamingSettings.lowerThirdFolder,
                            generatorDialogTitle = genTitle,
                            loadingText = genLoading,
                            serverNotRunningTitle = srvTitle,
                            serverNotRunningMessage = srvMsg,
                            noFolderTitle = nfTitle,
                            noFolderMessage = nfMsg,
                            chooseLogoTitle = logoTitle,
                            imagesFilterText = imgFilter,
                            parentScope = scope
                        )
                    }
                },
                modifier = Modifier.width(200.dp).padding(8.dp).align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text(stringResource(Res.string.generate_lower_third), style = MaterialTheme.typography.labelMedium)
            }
        }

        HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

        // Drag handle — resize the list
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val newWidthDp = with(density) { listWidthPx.toDp().value.toInt() }
                            onSettingsChangeState.value { s ->
                                s.copy(
                                    streamingSettings = s.streamingSettings.copy(
                                        lowerThirdListWidthDp = newWidthDp
                                    )
                                )
                            }
                        }
                    ) { _, dragAmount ->
                        listWidthPx = (listWidthPx + dragAmount)
                            .coerceIn(
                                with(density) { 100.dp.toPx() },
                                with(density) { 600.dp.toPx() }
                            )
                    }
                }
        )

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

            // Aspect ratio mismatch warning
            val comp = composition
            if (comp != null && comp.width > 0 && comp.height > 0) {
                val lottieAR = comp.width / comp.height
                val screenBounds = presenterScreenBounds()
                val screenAR = screenBounds.width.toFloat() / screenBounds.height.toFloat()
                if (kotlin.math.abs(lottieAR - screenAR) > 0.05f) {
                    Text(
                        text = stringResource(
                            Res.string.aspect_ratio_mismatch,
                            comp.width.toInt(), comp.height.toInt(), formatAspectRatio(comp.width.toInt(), comp.height.toInt()),
                            screenBounds.width, screenBounds.height, formatAspectRatio(screenBounds.width, screenBounds.height)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Controls row — moved to top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause
                Tooltip(stringResource(if (isPlaying) Res.string.pause else Res.string.play)) {
                    IconButton(
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
                        enabled = canPlay,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Add to Schedule
                val hasFile = selectedFile != null
                Tooltip(stringResource(Res.string.add_to_schedule)) {
                    IconButton(
                        onClick = {
                            val file = selectedFile ?: return@IconButton
                            onAddToSchedule(
                                file.nameWithoutExtension,
                                file.nameWithoutExtension,
                                false,
                                0L
                            )
                        },
                        enabled = hasFile,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_playlist_add), contentDescription = stringResource(Res.string.add_to_schedule), modifier = Modifier.size(20.dp))
                    }
                }

                // Go Live
                Tooltip(stringResource(Res.string.go_live)) {
                    IconButton(
                        onClick = { onGoLive(jsonContent, false, -1f, 0L) },
                        enabled = canPlay,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(painter = painterResource(Res.drawable.ic_cast), contentDescription = stringResource(Res.string.go_live), modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Lottie preview — weight(1f) fills remaining space, aspectRatio inside
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(presenterAspectRatio())
                        .fillMaxSize()
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
            }
        }
    }
}
