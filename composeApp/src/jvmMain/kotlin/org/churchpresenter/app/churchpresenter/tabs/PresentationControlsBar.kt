package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Surface
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import org.churchpresenter.theme.components.RaisedIconButton
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.SunkenOutlinedTextField
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res as AppRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.animation_crossfade
import org.churchpresenter.strings.generated.resources.animation_fade
import org.churchpresenter.strings.generated.resources.animation_none
import org.churchpresenter.strings.generated.resources.animation_slide_left
import org.churchpresenter.strings.generated.resources.animation_slide_right
import org.churchpresenter.strings.generated.resources.animation_type
import org.churchpresenter.strings.generated.resources.auto_scroll_interval
import org.churchpresenter.strings.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.ic_refresh
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_skip_next
import churchpresenter.composeapp.generated.resources.ic_skip_previous
import org.churchpresenter.strings.generated.resources.loop_off
import org.churchpresenter.strings.generated.resources.loop_on
import org.churchpresenter.strings.generated.resources.next_image
import org.churchpresenter.strings.generated.resources.ok
import org.churchpresenter.strings.generated.resources.pause
import org.churchpresenter.strings.generated.resources.presentation_arrow_key_hint
import org.churchpresenter.strings.generated.resources.play
import org.churchpresenter.strings.generated.resources.previous_image
import org.churchpresenter.strings.generated.resources.presentation_builds_counter
import org.churchpresenter.strings.generated.resources.slide_counter
import org.churchpresenter.strings.generated.resources.slide_counter_with_hidden
import org.churchpresenter.strings.generated.resources.transition_duration
import org.churchpresenter.strings.generated.resources.unit_ms
import org.churchpresenter.strings.generated.resources.unit_s
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.label
import org.churchpresenter.app.churchpresenter.utils.pairLabel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.sunken

/* The Presentation tab's playback controls: transport, counter, loop, timings, animation and the key hint. */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PresentationTabScope.PresentationControlsBar(viewModel: PresentationViewModel) {
    // ── Playback controls bar ─────────────────────────────────────
    // Adaptive shortcut hint: inline at the end of the controls bar when it fits on one
    // line there, otherwise on its own full-width row below the bar — never ellipsized.
    // Built from the live bindings so a rebind is reflected here. Empty when the user has
    // unbound all three, which both render sites below treat as "draw no hint at all" —
    // a hint whose keys do nothing is worse than none.
    val slideLabel = shortcuts.pairLabel(ShortcutAction.PRESENTATION_PREVIOUS, ShortcutAction.PRESENTATION_NEXT)
    val playLabel = shortcuts.label(ShortcutAction.PRESENTATION_PLAY_PAUSE)
    val blankLabel = shortcuts.label(ShortcutAction.PRESENTATION_BLANK)
    val hintText = if (slideLabel.isEmpty() && playLabel.isEmpty() && blankLabel.isEmpty()) {
        ""
    } else {
        stringResource(Res.string.presentation_arrow_key_hint, slideLabel, playLabel, blankLabel)
    }
    val hintStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
    val hintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    var hintOnOwnRow by remember { mutableStateOf(false) }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PresentationTransport(viewModel)
        PresentationSlideCounter(viewModel)
        PresentationLoopButton(viewModel)

        // Divider
        Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

        AutoScrollIntervalBox(viewModel)
        TransitionDurationBox(viewModel)
        PresentationAnimationDropdown(viewModel)

        // Measuring slot: takes the leftover width of the bar's last flow line and only
        // renders the hint here when the whole text fits it on a single line. The Box
        // stays in the flow either way, so the width measurement can't oscillate.
        // (Deliberately NOT BoxWithConstraints — FlowRow needs children's intrinsic
        // widths for line breaking, which SubcomposeLayout-based components can't give.)
        val textMeasurer = rememberTextMeasurer()
        var hintSlotWidthPx by remember { mutableStateOf(-1) }
        val fitsInline = remember(hintText, hintStyle, hintSlotWidthPx) {
            hintSlotWidthPx >= 0 && !textMeasurer.measure(
                text = hintText,
                style = hintStyle,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = hintSlotWidthPx)
            ).didOverflowWidth
        }
        LaunchedEffect(fitsInline, hintSlotWidthPx) {
            if (hintSlotWidthPx >= 0) hintOnOwnRow = !fitsInline
        }
        Box(modifier = Modifier.weight(1f).onSizeChanged { hintSlotWidthPx = it.width }) {
            if (fitsInline && hintText.isNotEmpty()) {
                Text(text = hintText, style = hintStyle, color = hintColor, maxLines = 1)
            }
        }

    }
    if (hintOnOwnRow && hintText.isNotEmpty()) {
        Text(
            text = hintText,
            style = hintStyle,
            color = hintColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresentationTabScope.PresentationTransport(viewModel: PresentationViewModel) {
    // Transport (inner gap: 4dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        stringResource(Res.string.previous_image),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 4.dp),
            )
        ) {
            KeyIconButton(onClick = { goPrevious(viewModel) }, modifier = Modifier.size(30.dp)) {
                Icon(
                    painterResource(AppRes.drawable.ic_skip_previous),
                    contentDescription = stringResource(Res.string.previous_image),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 4.dp),
            )
        ) {
            RaisedIconButton(
                onClick = { viewModel.togglePlayPause() },
                enabled = viewModel.slideFiles.isNotEmpty(),
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    painterResource(if (viewModel.isPlaying) AppRes.drawable.ic_pause else AppRes.drawable.ic_play),
                    contentDescription = stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        stringResource(Res.string.next_image),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 4.dp),
            )
        ) {
            KeyIconButton(onClick = { goNext(viewModel) }, modifier = Modifier.size(30.dp)) {
                Icon(
                    painterResource(AppRes.drawable.ic_skip_next),
                    contentDescription = stringResource(Res.string.next_image),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun PresentationTabScope.PresentationSlideCounter(viewModel: PresentationViewModel) {
    if (viewModel.slideFiles.isNotEmpty()) {
        val hiddenCount = viewModel.hiddenSlides.count { it in viewModel.slideFiles.indices }
        Text(
            text = if (hiddenCount == 0) {
                stringResource(
                    Res.string.slide_counter,
                    viewModel.selectedSlideIndex + 1,
                    viewModel.slideFiles.size,
                )
            } else {
                stringResource(
                    Res.string.slide_counter_with_hidden,
                    viewModel.selectedSlideIndex + 1,
                    viewModel.slideFiles.size,
                    hiddenCount,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.widthIn(min = 60.dp)
        )
        // Build progress of the live animated slide (only shown when it has builds).
        val liveFrame = presenterManager?.presentationFrame?.value
        if (liveFrame != null && liveFrame.stepCount > 0 && liveFrame.slideIndex == viewModel.selectedSlideIndex) {
            Text(
                text = stringResource(
                    Res.string.presentation_builds_counter,
                    liveFrame.completedSteps,
                    liveFrame.stepCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.widthIn(min = 60.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresentationTabScope.PresentationLoopButton(viewModel: PresentationViewModel) {
    // Loop button
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    stringResource(if (viewModel.isLooping) Res.string.loop_on else Res.string.loop_off),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        )
    ) {
        KeyIconButton(
            onClick = {
                viewModel.isLooping = !viewModel.isLooping
                onSettingsChange { s -> s.copy(
                    presentationSettings = s.presentationSettings.copy(isLooping = viewModel.isLooping),
                ) }
            },
            modifier = Modifier.size(28.dp),
            colors = if (viewModel.isLooping) IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) else IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        ) {
            Icon(
                painterResource(AppRes.drawable.ic_refresh),
                contentDescription = stringResource(
                    if (viewModel.isLooping) Res.string.loop_on else Res.string.loop_off,
                ),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun PresentationTabScope.AutoScrollIntervalBox(viewModel: PresentationViewModel) {
    var editingInterval by remember { mutableStateOf(false) }
    var intervalInput by remember(appSettings.presentationSettings.autoScrollInterval) {
        mutableStateOf(appSettings.presentationSettings.autoScrollInterval.toInt().toString())
    }

    Column(
        modifier = Modifier
            .height(42.dp)
            .width(170.dp)
            .sunken(AppShape(8.dp), elevationPalette())
            .clickable { editingInterval = true }
            .padding(start = 11.dp, end = 11.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(Res.string.auto_scroll_interval).uppercase(),
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(1.dp))
        Text(
            "${appSettings.presentationSettings.autoScrollInterval.toInt()} " +
                stringResource(Res.string.unit_s),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
    if (editingInterval) {
        AlertDialog(
            onDismissRequest = { editingInterval = false },
            title = { Text(stringResource(Res.string.auto_scroll_interval)) },
            text = {
                SunkenOutlinedTextField(
                    value = intervalInput,
                    onValueChange = { intervalInput = it },
                    suffix = { Text(stringResource(Res.string.unit_s)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                GhostButton(shape = AppShape(6.dp), onClick = {
                    intervalInput.toIntOrNull()?.coerceIn(1, PRESENTATION_MAX_AUTO_SCROLL_SECONDS)?.let { v ->
                        viewModel.autoScrollInterval = v.toFloat()
                        onSettingsChange { s ->
                            s.copy(
                                presentationSettings = s.presentationSettings.copy(
                                    autoScrollInterval = v.toFloat()
                                )
                            )
                        }
                    }
                    editingInterval = false
                }) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = {
                GhostButton(shape = AppShape(6.dp), onClick = { editingInterval = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PresentationTabScope.TransitionDurationBox(viewModel: PresentationViewModel) {
    var editingTransition by remember { mutableStateOf(false) }
    var transitionInput by remember(appSettings.presentationSettings.transitionDuration) {
        mutableStateOf(appSettings.presentationSettings.transitionDuration.toInt().toString())
    }

    Column(
        modifier = Modifier
            .height(42.dp)
            .width(170.dp)
            .sunken(AppShape(8.dp), elevationPalette())
            .clickable { editingTransition = true }
            .padding(start = 11.dp, end = 11.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(Res.string.transition_duration).uppercase(),
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(1.dp))
        Text(
            "${appSettings.presentationSettings.transitionDuration.toInt()} " +
                stringResource(Res.string.unit_ms),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
    if (editingTransition) {
        AlertDialog(
            onDismissRequest = { editingTransition = false },
            title = { Text(stringResource(Res.string.transition_duration)) },
            text = {
                SunkenOutlinedTextField(
                    value = transitionInput,
                    onValueChange = { transitionInput = it },
                    suffix = { Text(stringResource(Res.string.unit_ms)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                GhostButton(shape = AppShape(6.dp), onClick = {
                    transitionInput.toIntOrNull()
                        ?.coerceIn(PRESENTATION_MIN_TRANSITION_MS, PRESENTATION_MAX_TRANSITION_MS)
                        ?.let { v ->
                        viewModel.transitionDuration = v.toFloat()
                        onSettingsChange { s ->
                            s.copy(
                                presentationSettings = s.presentationSettings.copy(
                                    transitionDuration = v.toFloat()
                                )
                            )
                        }
                    }
                    editingTransition = false
                }) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = {
                GhostButton(shape = AppShape(6.dp), onClick = { editingTransition = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PresentationTabScope.PresentationAnimationDropdown(viewModel: PresentationViewModel) {
    val crossfadeText = stringResource(Res.string.animation_crossfade)
    val fadeText = stringResource(Res.string.animation_fade)
    val slideLeftText = stringResource(Res.string.animation_slide_left)
    val slideRightText = stringResource(Res.string.animation_slide_right)
    val noneText = stringResource(Res.string.animation_none)
    val currentAnimationLabel = when (appSettings.presentationSettings.animationType) {
        Constants.ANIMATION_FADE -> fadeText
        Constants.ANIMATION_SLIDE_LEFT -> slideLeftText
        Constants.ANIMATION_SLIDE_RIGHT -> slideRightText
        Constants.ANIMATION_NONE -> noneText
        else -> crossfadeText
    }
    DropdownSelector(
        label = stringResource(Res.string.animation_type),
        items = listOf(crossfadeText, fadeText, slideLeftText, slideRightText, noneText),
        selected = currentAnimationLabel,
        onSelectedChange = { selected ->
            viewModel.animationType = when (selected) {
                fadeText -> AnimationType.FADE
                slideLeftText -> AnimationType.SLIDE_LEFT
                slideRightText -> AnimationType.SLIDE_RIGHT
                noneText -> AnimationType.NONE
                else -> AnimationType.CROSSFADE
            }
            onSettingsChange { s ->
                s.copy(presentationSettings = s.presentationSettings.copy(animationType = when (selected) {
                    fadeText -> Constants.ANIMATION_FADE
                    slideLeftText -> Constants.ANIMATION_SLIDE_LEFT
                    slideRightText -> Constants.ANIMATION_SLIDE_RIGHT
                    noneText -> Constants.ANIMATION_NONE
                    else -> Constants.ANIMATION_CROSSFADE
                }))
            }
        }
    )
}
