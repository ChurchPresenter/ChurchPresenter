package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.animation_fade
import churchpresenter.composeapp.generated.resources.animation_none
import churchpresenter.composeapp.generated.resources.animation_slide_left
import churchpresenter.composeapp.generated.resources.animation_slide_right
import churchpresenter.composeapp.generated.resources.animation_type
import churchpresenter.composeapp.generated.resources.auto_scroll_interval
import churchpresenter.composeapp.generated.resources.loop
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.seconds_suffix
import churchpresenter.composeapp.generated.resources.slideshow_settings
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.transition_settings
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource

@Composable
fun PictureSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(stringResource(Res.string.slideshow_settings))

        Spacer(modifier = Modifier.height(4.dp))

        // Auto-scroll interval with Loop checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.auto_scroll_interval),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(140.dp)
            )
            Slider(
                value = settings.pictureSettings.autoScrollInterval,
                onValueChange = { value ->
                    onSettingsChange { s ->
                        s.copy(pictureSettings = s.pictureSettings.copy(autoScrollInterval = value))
                    }
                },
                valueRange = 1f..30f,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${settings.pictureSettings.autoScrollInterval.toInt()}${stringResource(Res.string.seconds_suffix)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp)
            )
        }

        // Loop checkbox
        SettingRow(stringResource(Res.string.loop)) {
            Checkbox(
                checked = settings.pictureSettings.isLooping,
                onCheckedChange = { value ->
                    onSettingsChange { s ->
                        s.copy(pictureSettings = s.pictureSettings.copy(isLooping = value))
                    }
                },
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SectionHeader(stringResource(Res.string.transition_settings))

        Spacer(modifier = Modifier.height(4.dp))

        // Transition duration slider with 50ms snap
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.transition_duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(140.dp)
            )
            Slider(
                value = settings.pictureSettings.transitionDuration,
                onValueChange = { rawValue ->
                    // Snap to 50ms increments
                    val snappedValue = (rawValue / 50f).toInt() * 50f
                    onSettingsChange { s ->
                        s.copy(pictureSettings = s.pictureSettings.copy(transitionDuration = snappedValue))
                    }
                },
                valueRange = 100f..2000f,
                steps = 37, // (2000-100)/50 = 38 values, so 37 steps
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${settings.pictureSettings.transitionDuration.toInt()}${stringResource(Res.string.milliseconds_suffix)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Animation type DropdownSelector
        SettingRow(stringResource(Res.string.animation_type)) {
            val crossfadeText = stringResource(Res.string.animation_crossfade)
            val fadeText = stringResource(Res.string.animation_fade)
            val slideLeftText = stringResource(Res.string.animation_slide_left)
            val slideRightText = stringResource(Res.string.animation_slide_right)
            val noneText = stringResource(Res.string.animation_none)

            val animationTypeString = settings.pictureSettings.animationType
            val animationType = when (animationTypeString) {
                Constants.ANIMATION_FADE -> AnimationType.FADE
                Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
                Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
                Constants.ANIMATION_NONE -> AnimationType.NONE
                else -> AnimationType.CROSSFADE
            }

            DropdownSelector(
                modifier = Modifier.width(200.dp),
                label = "",
                items = listOf(
                    crossfadeText,
                    fadeText,
                    slideLeftText,
                    slideRightText,
                    noneText
                ),
                selected = when (animationType) {
                    AnimationType.CROSSFADE -> crossfadeText
                    AnimationType.FADE -> fadeText
                    AnimationType.SLIDE_LEFT -> slideLeftText
                    AnimationType.SLIDE_RIGHT -> slideRightText
                    AnimationType.NONE -> noneText
                },
                onSelectedChange = { selected ->
                    val newTypeString = when (selected) {
                        fadeText -> Constants.ANIMATION_FADE
                        slideLeftText -> Constants.ANIMATION_SLIDE_LEFT
                        slideRightText -> Constants.ANIMATION_SLIDE_RIGHT
                        noneText -> Constants.ANIMATION_NONE
                        else -> Constants.ANIMATION_CROSSFADE
                    }
                    onSettingsChange { s ->
                        s.copy(pictureSettings = s.pictureSettings.copy(animationType = newTypeString))
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(140.dp)
        )
        content()
    }
}

