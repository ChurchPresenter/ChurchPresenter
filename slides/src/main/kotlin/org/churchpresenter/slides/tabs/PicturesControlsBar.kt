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
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_refresh
import org.churchpresenter.strings.generated.resources.animation_crossfade
import org.churchpresenter.strings.generated.resources.animation_fade
import org.churchpresenter.strings.generated.resources.animation_none
import org.churchpresenter.strings.generated.resources.animation_slide_left
import org.churchpresenter.strings.generated.resources.animation_slide_right
import org.churchpresenter.strings.generated.resources.animation_type
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
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.settings.OutputScaleMode
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.icon
import org.churchpresenter.sharedui.utils.label
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.hosanna.composables.button.IconToggleButton
import org.churchpresenter.hosanna.composables.button.CircularButton
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.field.Dropdown
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.model.MenuEntry
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.hosanna.composables.button.CircularButtonGroup
import org.churchpresenter.hosanna.composables.field.NumberField
import org.churchpresenter.hosanna.composables.model.NumberRange
import org.churchpresenter.hosanna.composables.model.NumberFieldActions
import org.churchpresenter.strings.generated.resources.number_increase
import org.churchpresenter.strings.generated.resources.number_decrease
import org.churchpresenter.strings.generated.resources.number_out_of_range
import org.churchpresenter.strings.generated.resources.pictures_interval_label
import org.churchpresenter.strings.generated.resources.pictures_transition_label
import androidx.compose.foundation.layout.IntrinsicSize

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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PicturesTransport(viewModel)

        PicturesImageCounter(viewModel)

        PicturesLoopButton(viewModel)

        PicturesScaleButton()

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

/** Previous and next either side of the biggest key, Play, in one pill. */
@Composable
private fun PicturesTabScope.PicturesTransport(viewModel: PicturesViewModel) {
    // One pill, Play in the middle, as the transport is drawn in the design.
    CircularButtonGroup {
        val previousStr = stringResource(Res.string.previous_image)
        Tooltip(previousStr) {
            CircularButton(
                painterResource(IconRes.drawable.ic_skip_previous),
                previousStr,
                { viewModel.previousImage(onInstanceLinkSendPreviousPicture) },
                hierarchy = ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
                enabled = viewModel.images.isNotEmpty() || onInstanceLinkSendPreviousPicture != null,
            )
        }
        PicturesPlayKey(viewModel)
        val nextStr = stringResource(Res.string.next_image)
        Tooltip(nextStr) {
            CircularButton(
                painterResource(IconRes.drawable.ic_skip_next),
                nextStr,
                { viewModel.nextImage(onInstanceLinkSendNextPicture) },
                hierarchy = ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
                enabled = viewModel.images.isNotEmpty() || onInstanceLinkSendNextPicture != null,
            )
        }
    }
}

/** The biggest key, Play/Pause. */
@Composable
private fun PicturesTabScope.PicturesPlayKey(viewModel: PicturesViewModel) {
    val playStr = stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play)
    Tooltip(playStr, Modifier.guideTarget(GuideTargets.PICTURES_PLAY)) {
        CircularButton(
            painterResource(if (viewModel.isPlaying) IconRes.drawable.ic_pause else IconRes.drawable.ic_play),
            playStr,
            { viewModel.togglePlayPause() },
            size = IconButtonSize.Large,
            hierarchy = ButtonHierarchy.Primary,
            intent = ButtonIntent.Default,
            enabled = viewModel.images.isNotEmpty(),
        )
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
private fun PicturesTabScope.PicturesLoopButton(viewModel: PicturesViewModel) {
    val loopStr = stringResource(if (viewModel.isLooping) Res.string.loop_on else Res.string.loop_off)
    Tooltip(loopStr) {
        IconToggleButton(
            painterResource(IconRes.drawable.ic_refresh),
            loopStr,
            viewModel.isLooping,
            { looping ->
                viewModel.isLooping = looping
                onSettingsChange { s -> s.copy(pictureSettings = s.pictureSettings.copy(isLooping = looping)) }
            },
            hierarchy = ButtonHierarchy.Tertiary,
        )
    }
}

/**
 * Each click moves Fit → Fill → Stretch on every profile at once -- the scaling is per profile, and
 * this is the shortcut over all of them. Lit whenever it is not Fit, or while the profiles disagree,
 * which it says rather than naming one of them.
 */
@Composable
private fun PicturesTabScope.PicturesScaleButton() {
    val shared = appSettings?.let { s ->
        sharedScaleMode(s.projectionSettings.outputProfiles) { it.pictureScaleMode }
    }
    val scaleMode = shared ?: OutputScaleMode.FIT
    val scaleLabel = scaleButtonLabel(shared, scaleMode, ScaleButtonContent.PICTURES)
    Tooltip(scaleLabel) {
        IconToggleButton(
            rememberVectorPainter(scaleMode.icon),
            scaleLabel,
            shared != OutputScaleMode.FIT,
            {
                val next = if (shared == null) scaleMode else scaleMode.next()
                onSettingsChange { s -> s.withPictureScaleEverywhere(next) }
            },
            hierarchy = ButtonHierarchy.Tertiary,
        )
    }
}

/** Seconds between pictures while the slideshow plays, typed or stepped. */
@Composable
private fun PicturesTabScope.PicturesIntervalSetting(viewModel: PicturesViewModel, appSettings: AppSettings) {
    PicturesWholeNumberField(
        label = stringResource(Res.string.pictures_interval_label),
        value = appSettings.pictureSettings.autoScrollInterval.toInt(),
        range = 1..PICTURES_MAX_AUTO_SCROLL_SECONDS,
        step = 1,
    ) { v ->
        viewModel.autoScrollInterval = v.toFloat()
        onSettingsChange { s -> s.copy(pictureSettings = s.pictureSettings.copy(autoScrollInterval = v.toFloat())) }
    }
}

/** How long one picture takes to give way to the next, typed or stepped. */
@Composable
private fun PicturesTabScope.PicturesTransitionSetting(viewModel: PicturesViewModel, appSettings: AppSettings) {
    PicturesWholeNumberField(
        label = stringResource(Res.string.pictures_transition_label),
        value = appSettings.pictureSettings.transitionDuration.toInt(),
        range = PICTURES_MIN_TRANSITION_MS..PICTURES_MAX_TRANSITION_MS,
        step = PICTURES_TRANSITION_STEP_MS,
    ) { v ->
        viewModel.transitionDuration = v.toFloat()
        onSettingsChange { s -> s.copy(pictureSettings = s.pictureSettings.copy(transitionDuration = v.toFloat())) }
    }
}

/** A labelled field of whole numbers within [range]; anything outside it is refused and says so. */
@Composable
private fun PicturesWholeNumberField(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
) {
    NumberField(
        value = value.toDouble(),
        onValueChange = { onValueChange(it.toInt().coerceIn(range)) },
        label = label,
        range = NumberRange(range.first.toDouble(), range.last.toDouble(), step.toDouble()),
        actions = NumberFieldActions(
            stringResource(Res.string.number_increase),
            stringResource(Res.string.number_decrease),
        ),
        // As wide as its one-line label, so a longer translation does not wrap it.
        modifier = Modifier.width(IntrinsicSize.Max),
        rangeError = stringResource(Res.string.number_out_of_range, range.first, range.last),
    )
}

private const val PICTURES_TRANSITION_STEP_MS = 100

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
    Dropdown(
        entries = listOf(crossfadeText, fadeText, slideLeftText, slideRightText, noneText)
            .map { MenuEntry.Item(it, it) },
        selectedKey = currentAnimationLabel,
        onSelect = { selected ->
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
        },
        modifier = Modifier.widthIn(min = PICTURES_SETTING_BOX_WIDTH),
        label = stringResource(Res.string.animation_type).removeSuffix(":"),
    )
}
