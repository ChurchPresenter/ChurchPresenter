package org.churchpresenter.app.churchpresenter.extensions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring.StiffnessLow
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.FloatingToolbarDefaults.animationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

fun Modifier.errorShake(
    trigger: Boolean, // A boolean state that changes to trigger the animation
    onAnimationFinish: () -> Unit // Callback when the animation completes
): Modifier = composed {
    val shake = remember { Animatable(0f) }

    // LaunchedEffect runs the animation when the 'trigger' value changes
    LaunchedEffect(trigger) {
        if (trigger) {
            // Animate the offset back and forth several times
            for (i in 0..8) { // Number of shakes (4 left, 4 right)
                when (i % 2) {
                    0 -> shake.animateTo(5f, tween(50, easing = LinearEasing))
                    else -> shake.animateTo(-5f, tween(50, easing = LinearEasing))
                }
            }
            // Animate back to original position
            shake.animateTo(0f)
            // Notify when done
            onAnimationFinish()
        }
    }

    // Apply the animated offset to the composable
    this.offset {
        IntOffset(x = shake.value.roundToInt(), y = 0)
    }
}

@Composable
fun Modifier.conditional(condition: Boolean, modifier: @Composable Modifier.() -> Modifier) =
    then(if (condition) modifier.invoke(this) else this)