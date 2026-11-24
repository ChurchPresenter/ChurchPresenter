package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.churchpresenter.hosanna.composables.defaults.HoldRepeatDefaults

@Stable
internal class HoldRepeat {
    var repeated by mutableStateOf(false)

    fun click(step: (Boolean) -> Unit) {
        if (!repeated) step(false)
        repeated = false
    }
}

@Composable
internal fun rememberHoldRepeat(
    source: MutableInteractionSource,
    enabled: Boolean,
    onStep: (fast: Boolean) -> Unit,
): HoldRepeat {
    val hold = remember { HoldRepeat() }
    val pressed by source.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        hold.repeated = false
        delay(HoldRepeatDefaults.DELAY_MILLIS)
        var held = HoldRepeatDefaults.DELAY_MILLIS
        while (true) {
            hold.repeated = true
            step(held >= HoldRepeatDefaults.FAST_AFTER_MILLIS)
            delay(HoldRepeatDefaults.REPEAT_MILLIS)
            held += HoldRepeatDefaults.REPEAT_MILLIS
        }
    }
    return hold
}
