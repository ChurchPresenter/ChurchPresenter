@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.foundation.Image
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.composables.AddToScheduleButton
import org.churchpresenter.sharedui.composables.GoLiveButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.components.RaisedIconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_pause
import org.churchpresenter.icons.generated.resources.ic_play
import org.churchpresenter.strings.generated.resources.add_to_schedule
import org.churchpresenter.strings.generated.resources.atem_clip_too_long
import org.churchpresenter.strings.generated.resources.atem_golive_key
import org.churchpresenter.strings.generated.resources.atem_unreachable
import org.churchpresenter.strings.generated.resources.atem_quick_clip_tooltip
import org.churchpresenter.strings.generated.resources.atem_quick_still_tooltip
import org.churchpresenter.strings.generated.resources.atem_send_to_atem
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.icons.generated.resources.ic_key
import org.churchpresenter.icons.generated.resources.ic_upload
import org.churchpresenter.strings.generated.resources.lottie_select_preset
import org.churchpresenter.strings.generated.resources.pause
import org.churchpresenter.strings.generated.resources.play
import kotlinx.coroutines.launch
import org.churchpresenter.lowerthird.render.LottieRenderCache
import org.churchpresenter.sharedui.utils.formatAspectRatio
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.strings.generated.resources.aspect_ratio_mismatch
import org.churchpresenter.sharedui.utils.PreviewOutput
import androidx.compose.material3.IconButtonColors
import org.churchpresenter.sharedui.composables.topBarCard

/** The one bar over the preview: the preset name, then ATEM, then Play, Add to Schedule and Go Live. */
@Composable
internal fun LowerThirdTabScope.LowerThirdTitleBar(previewOutput: PreviewOutput) {
    val comp = composition
    val arMismatch = if (comp != null && comp.width > 0 && comp.height > 0) {
        val outputWidth = previewOutput.size.width
        val outputHeight = previewOutput.size.height
        val screenAR = previewOutput.size.aspectRatio
        if (kotlin.math.abs(comp.width / comp.height - screenAR) > 0.05f)
            stringResource(
                Res.string.aspect_ratio_mismatch,
                comp.width.toInt(),
                comp.height.toInt(),
                formatAspectRatio(comp.width.toInt(), comp.height.toInt()),
                outputWidth,
                outputHeight,
                formatAspectRatio(outputWidth, outputHeight),
            )
        else null
    } else null
    // One bar: the preset name, then ATEM, then the Play · Add to Schedule · Go Live tail.
    // There is no second controls row — everything it held now sits here, which is the
    // shape the Pictures and Presentation headers use. FlowRow so the ATEM buttons wrap
    // rather than clip on a narrow panel; heightIn because the aspect-ratio warning adds
    // a second line beneath the name.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .topBarCard(start = 0.dp)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = selectedFile?.nameWithoutExtension ?: stringResource(Res.string.lottie_select_preset),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selectedFile != null) FontWeight.Medium else FontWeight.Normal,
                ),
                color = if (selectedFile != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (arMismatch != null) {
                Text(
                    text = arMismatch,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
        }
        }

        LowerThirdAtemButtons()

        LowerThirdPlayButton()

        LowerThirdScheduleAndLive()
    }
}

/** ATEM controls, deliberately left of the primary actions so they keep the canonical rightmost tail. */
@Composable
private fun LowerThirdTabScope.LowerThirdAtemButtons() {
    if (atemConfigured && atemEverConnected) {
        val atemButtonColors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        val unreachableTooltip = stringResource(Res.string.atem_unreachable, appSettings.atemSettings.host)
        val goLiveKey = appSettings.atemSettings.goLiveKey
        // One string for the tooltip and the button's name, so they cannot drift apart.
        val goLiveKeyLabel = stringResource(Res.string.atem_golive_key)
        Tooltip(goLiveKeyLabel) {
            RaisedIconButton(
                onClick = { onSettingsChangeState.value { s -> s.copy(
                    atemSettings = s.atemSettings.copy(goLiveKey = !s.atemSettings.goLiveKey),
                ) } },
                modifier = Modifier.size(34.dp),
                shape = AppShape(8.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (goLiveKey) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (goLiveKey) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                )
            ) {
                Icon(
                    painterResource(IconRes.drawable.ic_key),
                    contentDescription = goLiveKeyLabel,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (appSettings.atemSettings.quickUpload) {
            LowerThirdQuickUploadButtons(atemButtonColors, unreachableTooltip)
        } else {
            Tooltip(if (atemReachable) stringResource(Res.string.atem_send_to_atem) else unreachableTooltip) {
                RaisedIconButton(
                    onClick = {
                        atemSlot = if (atemIsClip) {
                            appSettings.atemSettings.defaultClipSlot
                        } else {
                            appSettings.atemSettings.defaultStillSlot
                        }
                        atemError = null
                        atemProgress = null
                        showAtemDialog = true
                    },
                    enabled = canPlay && !atemBusy && atemReachable,
                    modifier = Modifier.size(34.dp),
                    shape = AppShape(8.dp),
                    colors = atemButtonColors
                ) {
                    Icon(
                        painterResource(IconRes.drawable.ic_upload),
                        contentDescription = stringResource(Res.string.atem_send_to_atem),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LowerThirdTabScope.LowerThirdQuickUploadButtons(
    atemButtonColors: IconButtonColors,
    unreachableTooltip: String,
) {
    val stillSlot = appSettings.atemSettings.defaultStillSlot
    val clipSlot = appSettings.atemSettings.defaultClipSlot
    val quickEnabled = canPlay && !atemBusy && atemReachable
    val quickClipVariant = if (jsonContent.isNotBlank()) atemVariant(isClip = true, useDetectedFps = false) else null
    val quickClipCapacity = appSettings.atemSettings.detectedClipMaxFrames.getOrNull(clipSlot)
    val quickClipTooLong =
        quickClipVariant != null && quickClipCapacity != null && quickClipVariant.frameCount > quickClipCapacity

    val quickStillLabel =
        if (!atemReachable) unreachableTooltip else stringResource(Res.string.atem_quick_still_tooltip, stillSlot + 1)
    Tooltip(quickStillLabel) {
        RaisedIconButton(
            onClick = {
                startAtemUpload(
                    atemVariant(isClip = false, useDetectedFps = false),
                    stillSlot,
                    closeDialogOnSuccess = false
                )
            },
            enabled = quickEnabled,
            modifier = Modifier.size(34.dp),
            shape = AppShape(8.dp),
            colors = atemButtonColors
        ) {
            Icon(Icons.Filled.Image, contentDescription = quickStillLabel, modifier = Modifier.size(16.dp))
        }
    }
    val quickClipLabel = when {
        !atemReachable -> unreachableTooltip
        quickClipTooLong -> {
            val secs = String.format(java.util.Locale.US, "%.1f", quickClipCapacity / quickClipVariant.fps)
            stringResource(
                Res.string.atem_clip_too_long,
                quickClipVariant.frameCount,
                clipSlot + 1,
                quickClipCapacity,
                secs,
            )
        }
        else -> stringResource(Res.string.atem_quick_clip_tooltip, clipSlot + 1)
    }
    Tooltip(quickClipLabel) {
        RaisedIconButton(
            onClick = {
                quickClipVariant?.let { variant ->
                    startAtemUpload(variant, clipSlot, closeDialogOnSuccess = false)
                }
            },
            enabled = quickEnabled && !quickClipTooLong,
            modifier = Modifier.size(34.dp),
            shape = AppShape(8.dp),
            colors = atemButtonColors
        ) {
            Icon(Icons.Filled.Movie, contentDescription = quickClipLabel, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun LowerThirdTabScope.LowerThirdPlayButton() {
    Tooltip(stringResource(if (isPlaying) Res.string.pause else Res.string.play)) {
        RaisedIconButton(
            onClick = {
                if (canPlay) {
                    if (isPlaying) {
                        val job = animJob; animJob = null; isPlaying = false; job?.cancel()
                    } else if (animatedProgress.value >= 1f) {
                        scope.launch { animatedProgress.snapTo(0f); startPlaying() }
                    } else {
                        startPlaying()
                    }
                }
            },
            enabled = canPlay,
            modifier = Modifier.size(34.dp),
            shape = AppShape(8.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                painterResource(if (isPlaying) IconRes.drawable.ic_pause else IconRes.drawable.ic_play),
                contentDescription = stringResource(if (isPlaying) Res.string.pause else Res.string.play),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun LowerThirdTabScope.LowerThirdScheduleAndLive() {
    // Add to Schedule
    AddToScheduleButton(
        onClick = {
            val file = selectedFile ?: return@AddToScheduleButton
            onAddToSchedule(file.nameWithoutExtension, file.nameWithoutExtension, false, 0L)
        },
        enabled = selectedFile != null,
        tooltipText = stringResource(Res.string.add_to_schedule),
        modifier = Modifier.guideTarget(GuideTargets.LOWER_THIRD_ADD_TO_SCHEDULE),
    )

    // Go Live
    GoLiveButton(
        modifier = Modifier.guideTarget(GuideTargets.LOWER_THIRD_GO_LIVE),
        onClick = ::goLive,
        enabled = canPlay,
        tooltipText = stringResource(Res.string.go_live),
        showsShortcut = true,
    )
}

/** Puts the selected preset on air: keyed onto the ATEM when set up to, else onto the outputs. */
internal fun LowerThirdTabScope.goLive() {
    val atemSettings = appSettings.atemSettings
    if (atemSettings.goLiveKey && atemConfigured) {
        val durationMs = LottieRenderCache.lottieDurationMs(jsonContent) ?: totalDurationMs()
        val name = selectedFile?.nameWithoutExtension ?: ""
        val useDsk = atemSettings.useDownstreamKey
        scope.launch {
            val keyError = LowerThirdSequencer.run(
                clip = LowerThirdClip(
                    name = name,
                    json = jsonContent,
                    durationMs = durationMs,
                    pauseAtFrame = false,
                    pauseDurationMs = 0L,
                ),
                key = LowerThirdKey(
                    mixEffect = if (useDsk) 0 else atemSettings.keyMixEffect,
                    keyer = if (useDsk) atemSettings.dskIndex else atemSettings.keyIndex,
                    useDownstreamKey = useDsk,
                ),
                atem = atemSettings,
            )
            if (keyError != null) atemError = keyError
        }
    } else {
        onGoLive(jsonContent, false, -1f, 0L, selectedFile?.nameWithoutExtension ?: "")
    }
}
