package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.arrow_down
import churchpresenter.composeapp.generated.resources.arrow_up
import churchpresenter.composeapp.generated.resources.decrement
import churchpresenter.composeapp.generated.resources.increment
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun NumberSettingsTextField(
    modifier: Modifier = Modifier.width(100.dp),
    label: String = "",
    initialText: Int = 8,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    var value by rememberSaveable(initialText) { mutableStateOf(initialText) }
    var isError by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(42.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(8.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(start = 11.dp, top = 2.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (label.isNotEmpty()) {
                Text(
                    text = label.uppercase(),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                interactionSource = remember { MutableInteractionSource() },
                maxLines = 1,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                value = value.toString(),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth()) {
                        innerTextField()
                    }
                },
                onValueChange = { newValue ->
                    val intValue = newValue.toIntOrNull() ?: 0
                    value = intValue
                    if (intValue in range) {
                        onValueChange.invoke(intValue)
                        isError = false
                    } else {
                        isError = true
                    }
                },
            )
        }

        Column(
            modifier = Modifier.width(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = {
                    val newValue = value + 1
                    if (newValue in range) { value = newValue; onValueChange.invoke(newValue); isError = false }
                    else isError = true
                },
                modifier = Modifier.size(20.dp, 16.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.arrow_up),
                    contentDescription = stringResource(Res.string.increment),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(12.dp)
                )
            }
            IconButton(
                onClick = {
                    val newValue = value - 1
                    if (newValue in range) { value = newValue; onValueChange.invoke(newValue); isError = false }
                    else isError = true
                },
                modifier = Modifier.size(20.dp, 16.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.arrow_down),
                    contentDescription = stringResource(Res.string.decrement),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
