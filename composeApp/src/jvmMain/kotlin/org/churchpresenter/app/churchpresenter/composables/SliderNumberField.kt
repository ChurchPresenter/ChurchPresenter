package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val DEFAULT_FIELD_WIDTH = 70.dp

/**
 * A [SlimSlider] paired with a [NumberSettingsTextField] reading and writing the same value --
 * for a range (like a reference-pixel offset) where a plain text field alone hides that negative
 * values are allowed and how far the range actually reaches. The slider makes both visible at a
 * glance; the field stays for anyone who wants to type an exact number, including a negative one.
 */
@Composable
fun SliderNumberField(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fieldWidth: Dp = DEFAULT_FIELD_WIDTH,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SlimSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f),
        )
        NumberSettingsTextField(
            modifier = Modifier.width(fieldWidth),
            initialText = value,
            onValueChange = onValueChange,
            range = range,
        )
    }
}
