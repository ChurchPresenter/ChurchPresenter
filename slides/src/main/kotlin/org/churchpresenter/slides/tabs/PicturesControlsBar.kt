@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.churchpresenter.slides.tabs

import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.utils.sharedScaleMode
import org.churchpresenter.sharedui.utils.ScaleButtonContent
import org.churchpresenter.sharedui.utils.scaleButtonLabel
import org.churchpresenter.sharedui.utils.withPictureScaleEverywhere
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import org.churchpresenter.theme.components.RaisedIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_refresh
import org.churchpresenter.strings.generated.resources.animation_crossfade
import org.churchpresenter.strings.generated.resources.animation_fade
import org.churchpresenter.strings.generated.resources.animation_none
import org.churchpresenter.strings.generated.resources.animation_slide_left
import org.churchpresenter.strings.generated.resources.animation_slide_right
import org.churchpresenter.strings.generated.resources.animation_type
import org.churchpresenter.strings.generated.resources.auto_scroll_interval
import org.churchpresenter.icons.generated.resources.ic_pause
import org.churchpresenter.icons.generated.resources.ic_play
import org.churchpresenter.icons.generated.resources.ic_skip_next
import org.churchpresenter.icons.generated.resources.ic_skip_previous
import org.churchpresenter.strings.generated.resources.image_counter
import org.churchpresenter.strings.generated.resources.image_counter_with_hidden
import org.churchpresenter.strings.generated.resources.loop_off
import org.churchpresenter.strings.generated.resources.loop_on
import org.churchpresenter.strings.generated.resources.next_image
import org.churchpresenter.strings.generated.resources.pause
import org.churchpresenter.strings.generated.resources.play
import org.churchpresenter.strings.generated.resources.previous_image
import org.churchpresenter.strings.generated.resources.transition_duration
import org.churchpresenter.strings.generated.resources.unit_s
import org.churchpresenter.strings.generated.resources.unit_ms
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.settings.OutputScaleMode
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.icon
import org.churchpresenter.sharedui.utils.label
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.sunken
import androidx.compose.material3.IconButtonColors

/** Transport, the counter, loop, scale, and the interval, transition and animation settings. */
@Composable
internal fun PicturesTabScope.PicturesControlsBar(viewModel: PicturesViewModel) {
    // ── Playback controls bar ─────────────────────────────────────
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val neutralKeyColors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        val accentKeyColors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
        PicturesTransport(viewModel, neutralKeyColors)

        PicturesImageCounter(viewModel)

        PicturesLoopButton(viewModel, accentKeyColors, neutralKeyColors)

        PicturesScaleButton(accentKeyColors, neutralKeyColors)

        // Divider
        Box(modifier = Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outlineVariant))

        // Settings display boxes
        if (appSettings != null) {
            PicturesIntervalSetting(viewModel, appSettings)

            PicturesTransitionSetting(viewModel, appSettings)

            PicturesAnimationDropdown(viewModel, appSettings)
        }
    }
}

/** Raised keys either side of the biggest one, Play. */
@Composable
private fun PicturesTabScope.PicturesTransport(viewModel: PicturesViewModel, neutralKeyColors: IconButtonColors) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
            RaisedIconButton(
                onClick = { viewModel.previousImage(onInstanceLinkSendPreviousPicture) },
                enabled = viewModel.images.isNotEmpty() || onInstanceLinkSendPreviousPicture != null,
                modifier = Modifier.size(PICTURES_TRANSPORT_KEY_SIZE),
                colors = neutralKeyColors
            ) {
                Icon(
                    painter = painterResource(IconRes.drawable.ic_skip_previous),
                    contentDescription = stringResource(Res.string.previous_image),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        PicturesPlayKey(viewModel)
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
            RaisedIconButton(
                onClick = { viewModel.nextImage(onInstanceLinkSendNextPicture) },
                enabled = viewModel.images.isNotEmpty() || onInstanceLinkSendNextPicture != null,
                modifier = Modifier.size(PICTURES_TRANSPORT_KEY_SIZE),
                colors = neutralKeyColors
            ) {
                Icon(
                    painter = painterResource(IconRes.drawable.ic_skip_next),
                    contentDescription = stringResource(Res.string.next_image),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** The biggest key, Play/Pause. */
@Composable
private fun PicturesTabScope.PicturesPlayKey(viewModel: PicturesViewModel) {
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
            enabled = viewModel.images.isNotEmpty(),
            modifier = Modifier.size(PICTURES_PLAY_KEY_SIZE).guideTarget(GuideTargets.PICTURES_PLAY),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                painter = painterResource(
                    if (viewModel.isPlaying) IconRes.drawable.ic_pause else IconRes.drawable.ic_play,
                ),
                contentDescription = stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun PicturesTabScope.PicturesImageCounter(viewModel: PicturesViewModel) {
    if (viewModel.images.isNotEmpty()) {
        val hiddenCount = viewModel.imagesSnapshot().count { viewModel.isHidden(it) }
        Text(
            text = if (hiddenCount == 0) {
                stringResource(
                    Res.string.image_counter,
                    viewModel.selectedImageIndex + 1,
                    viewModel.images.size,
                )
            } else {
                stringResource(
                    Res.string.image_counter_with_hidden,
                    viewModel.selectedImageIndex + 1,
                    viewModel.images.size,
                    hiddenCount,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.widthIn(min = 60.dp)
        )
    }
}

@Composable
private fun PicturesTabScope.PicturesLoopButton(
    viewModel: PicturesViewModel,
    accentKeyColors: IconButtonColors,
    neutralKeyColors: IconButtonColors,
) {
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
        RaisedIconButton(
            onClick = {
                viewModel.isLooping = !viewModel.isLooping
                onSettingsChange { s -> s.copy(
                    pictureSettings = s.pictureSettings.copy(isLooping = viewModel.isLooping),
                ) }
            },
            modifier = Modifier.size(PICTURES_LOOP_KEY_SIZE),
            shape = CircleShape,
            colors = if (viewModel.isLooping) accentKeyColors else neutralKeyColors
        ) {
            // Same text the tooltip shows: TooltipArea is a hover popup and contributes no
            // semantics, so without this the button has no name at all.
            Icon(painterResource(IconRes.drawable.ic_refresh), contentDescription = stringResource(
                if (viewModel.isLooping) Res.string.loop_on else Res.string.loop_off,
            ), modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Each click moves Fit → Fill → Stretch on every profile at once -- the scaling is per profile, and
 * this is the shortcut over all of them. Lit whenever it is not Fit, or while the profiles disagree,
 * which it says rather than naming one of them.
 */
@Composable
private fun PicturesTabScope.PicturesScaleButton(
    accentKeyColors: IconButtonColors,
    neutralKeyColors: IconButtonColors,
) {
    val shared = appSettings?.let { s ->
        sharedScaleMode(s.projectionSettings.outputProfiles) { it.pictureScaleMode }
    }
    val scaleMode = shared ?: OutputScaleMode.FIT
    val scaled = shared != OutputScaleMode.FIT
    val scaleLabel = scaleButtonLabel(shared, scaleMode, ScaleButtonContent.PICTURES)
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp
            ) {
                Text(
                    scaleLabel,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        )
    ) {
        RaisedIconButton(
            onClick = {
                val next = if (shared == null) scaleMode else scaleMode.next()
                onSettingsChange { s -> s.withPictureScaleEverywhere(next) }
            },
            modifier = Modifier.size(PICTURES_LOOP_KEY_SIZE),
            colors = if (scaled) accentKeyColors else neutralKeyColors
        ) {
            Icon(
                scaleMode.icon,
                contentDescription = scaleLabel,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** The auto-scroll interval box and the dialog that edits it. */
@Composable
private fun PicturesTabScope.PicturesIntervalSetting(viewModel: PicturesViewModel, appSettings: AppSettings) {
    var editingInterval by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .height(42.dp)
            .width(PICTURES_SETTING_BOX_WIDTH)
            .sunken(AppShape(8.dp), elevationPalette())
            .clickable { editingInterval = true }
            .padding(start = 11.dp, end = 11.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.auto_scroll_interval).removeSuffix(":").uppercase(),
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(1.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${appSettings.pictureSettings.autoScrollInterval.toInt()} ${stringResource(Res.string.unit_s)}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }

    if (editingInterval) {
        AutoScrollIntervalDialog(
            initial = appSettings.pictureSettings.autoScrollInterval.toInt(),
            maxSeconds = PICTURES_MAX_AUTO_SCROLL_SECONDS,
            onConfirm = { v ->
                viewModel.autoScrollInterval = v.toFloat()
                onSettingsChange { s ->
                    s.copy(pictureSettings = s.pictureSettings.copy(autoScrollInterval = v.toFloat()))
                }
                editingInterval = false
            },
            onDismiss = { editingInterval = false },
        )
    }
}

/** The transition duration box and the dialog that edits it. */
@Composable
private fun PicturesTabScope.PicturesTransitionSetting(viewModel: PicturesViewModel, appSettings: AppSettings) {
    var editingTransition by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .height(42.dp)
            .width(PICTURES_SETTING_BOX_WIDTH)
            .sunken(AppShape(8.dp), elevationPalette())
            .clickable { editingTransition = true }
            .padding(start = 11.dp, end = 11.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.transition_duration).removeSuffix(":").uppercase(),
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(1.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${appSettings.pictureSettings.transitionDuration.toInt()} " +
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
    }

    if (editingTransition) {
        TransitionDurationDialog(
            initial = appSettings.pictureSettings.transitionDuration.toInt(),
            minMs = PICTURES_MIN_TRANSITION_MS,
            maxMs = PICTURES_MAX_TRANSITION_MS,
            onConfirm = { v ->
                viewModel.transitionDuration = v.toFloat()
                onSettingsChange { s ->
                    s.copy(pictureSettings = s.pictureSettings.copy(transitionDuration = v.toFloat()))
                }
                editingTransition = false
            },
            onDismiss = { editingTransition = false },
        )
    }
}

@Composable
private fun PicturesTabScope.PicturesAnimationDropdown(viewModel: PicturesViewModel, appSettings: AppSettings) {
    val crossfadeText = stringResource(Res.string.animation_crossfade)
    val fadeText = stringResource(Res.string.animation_fade)
    val slideLeftText = stringResource(Res.string.animation_slide_left)
    val slideRightText = stringResource(Res.string.animation_slide_right)
    val noneText = stringResource(Res.string.animation_none)
    val currentAnimationLabel = when (appSettings.pictureSettings.animationType) {
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
                val newType = when (selected) {
                    fadeText -> Constants.ANIMATION_FADE
                    slideLeftText -> Constants.ANIMATION_SLIDE_LEFT
                    slideRightText -> Constants.ANIMATION_SLIDE_RIGHT
                    noneText -> Constants.ANIMATION_NONE
                    else -> Constants.ANIMATION_CROSSFADE
                }
                s.copy(pictureSettings = s.pictureSettings.copy(animationType = newType))
            }
        }
    )
}
