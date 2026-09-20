package org.churchpresenter.calendar.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private const val SKELETON_COUNT = 5
private const val SKELETON_ALPHA_LOW = 0.08f
private const val SKELETON_ALPHA_HIGH = 0.2f
private const val SKELETON_PULSE_MILLIS = 900
private val CAPTION_SKELETON_WIDTH = 140.dp
private val CAPTION_SKELETON_HEIGHT = 10.dp

/**
 * The placeholder a strip of thumbnails leaves while they load: [count] boxes the size of the
 * thumbnails they stand for, breathing. What the row will look like, before it does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkeletonStrip(count: Int = SKELETON_COUNT) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) {
            SkeletonBox(Modifier.width(THUMB_WIDTH).height(THUMB_HEIGHT))
        }
    }
    SkeletonBox(Modifier.width(CAPTION_SKELETON_WIDTH).height(CAPTION_SKELETON_HEIGHT))
}

/** One placeholder block, its tint breathing between two alphas until what it stands for arrives. */
@Composable
internal fun SkeletonBox(modifier: Modifier) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = SKELETON_ALPHA_LOW,
        targetValue = SKELETON_ALPHA_HIGH,
        animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MILLIS), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
    )
}
