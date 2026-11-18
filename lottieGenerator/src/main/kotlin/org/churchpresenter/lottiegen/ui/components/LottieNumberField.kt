package org.churchpresenter.lottiegen.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction

/**
 * A number field that keeps what is typed in it -- empty, a lone "1", "2," -- and commits to
 * [onCommit] only a complete number inside [min]..[max] as it goes, then clamps on Enter or when it
 * loses focus, reverting to [value] if the text is not a number. A comma is a decimal mark.
 */
@Composable
fun LottieNumberField(
    label: String,
    value: Double,
    onCommit: (Double) -> Unit,
    min: Double,
    max: Double,
    modifier: Modifier = Modifier,
    whole: Boolean = false,
    fillWidth: Boolean = false,
) {
    val current by rememberUpdatedState(value)
    val commit by rememberUpdatedState(onCommit)
    var text by remember { mutableStateOf(NumberInput.format(value)) }
    var focused by remember { mutableStateOf(false) }

    // The config changed from outside (a preset, a canvas size button): show it, unless the typist is mid-edit.
    LaunchedEffect(value, focused) {
        if (!focused) text = NumberInput.format(value)
    }

    fun finish() {
        val done = NumberInput.finish(text, min, max, whole)
        if (done != null && done != current) commit(done)
        text = NumberInput.format(done ?: current)
    }

    LottieTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            val live = NumberInput.live(typed, min, max, whole)
            if (live != null && live != current) commit(live)
        },
        label = label,
        modifier = modifier.onFocusChanged {
            val left = focused && !it.hasFocus
            focused = it.hasFocus
            if (left) finish()
        },
        fillWidth = fillWidth,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { finish() }),
    )
}

/** [LottieNumberField] over an [Int] setting. */
@Composable
fun LottieIntField(
    label: String,
    value: Int,
    onCommit: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    LottieNumberField(
        label = label,
        value = value.toDouble(),
        onCommit = { onCommit(it.toInt()) },
        min = range.first.toDouble(),
        max = range.last.toDouble(),
        modifier = modifier,
        whole = true,
        fillWidth = fillWidth,
    )
}

/** [LottieNumberField] over a [Float] setting; the float's own digits, not its widened double's. */
@Composable
fun LottieFloatField(
    label: String,
    value: Float,
    onCommit: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    LottieNumberField(
        label = label,
        value = value.toString().toDouble(),
        onCommit = { onCommit(it.toFloat()) },
        min = range.start.toString().toDouble(),
        max = range.endInclusive.toString().toDouble(),
        modifier = modifier,
        fillWidth = fillWidth,
    )
}
