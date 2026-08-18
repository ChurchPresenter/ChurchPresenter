package lottiegen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lottiegen.ui.Tokens

/**
 * A labelled slider row: name on the left, the current value (emphasised) and its unit on the
 * right, with the track underneath.
 */
@Composable
fun SliderWithLabel(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    unit: String = "",
    format: (Float) -> String = { "%.1f".format(it) }
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = Tokens.LabelText,
                maxLines = 1
            )
            Text(
                format(value),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tokens.PrimaryText
            )
            if (unit.isNotEmpty()) {
                Text(unit, fontSize = 10.5.sp, color = Tokens.UnitText)
            }
        }
        Spacer(Modifier.height(7.dp))
        LottieSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}
