package org.churchpresenter.liveoutput.preview

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_pause
import org.churchpresenter.icons.generated.resources.ic_play
import org.churchpresenter.strings.generated.resources.pause
import org.churchpresenter.strings.generated.resources.play
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.SlimSlider

// The media transport under a preview showing a video: play, pause, seek and the audio level.

private const val AUDIO_LEVEL_COLOR = 0xFF4CAF50

@Composable
internal fun AnimatedEqualizer() {
    val transition = rememberInfiniteTransition()
    val barHeights = (0..3).map { index ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 300 + index * 100,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        barHeights.forEach { heightFraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightFraction.value)
                    .background(Color(AUDIO_LEVEL_COLOR), AppShape(1.dp))
            )
        }
    }
}

@Composable
internal fun MediaPreviewControls(
    isPlaying: Boolean,
    duration: Long,
    currentPosition: Long,
    formatTime: (Long) -> String,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        KeyIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isPlaying) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        ) {
            Icon(
                painter = painterResource(
                    if (isPlaying) IconRes.drawable.ic_pause else IconRes.drawable.ic_play
                ),
                contentDescription = stringResource(
                    if (isPlaying) Res.string.pause else Res.string.play
                ),
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
        }

        if (duration > 0) {
            SlimSlider(
                value = currentPosition.toFloat(),
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.weight(1f),
                trailingLabel = formatTime(currentPosition)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
