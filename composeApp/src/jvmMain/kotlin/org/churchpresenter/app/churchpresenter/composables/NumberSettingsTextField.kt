package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.arrow_down
import churchpresenter.composeapp.generated.resources.arrow_up
import org.churchpresenter.app.churchpresenter.extensions.errorShake
import churchpresenter.composeapp.generated.resources.decrement
import churchpresenter.composeapp.generated.resources.increment
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun NumberSettingsTextField(
    modifier: Modifier = Modifier,
    initialText: Int = 8,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    var value by rememberSaveable(initialText) { mutableStateOf(initialText) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var isError by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableStateOf(false) }
    val borderColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val borderWidth = if (isFocused || isError) {
        2.dp
    } else {
        1.dp
    }

    Row(
        modifier = modifier
            .width(100.dp)
            .height(32.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(2.dp))
            .errorShake(
                trigger = shakeTrigger,
                onAnimationFinish = {
                    shakeTrigger = false
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            interactionSource = interactionSource,
            maxLines = 1,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.weight(1f),
            singleLine = true,
            value = value.toString(),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) { innerTextField() }
            },
            onValueChange = { newValue ->
                val intValue = newValue.toIntOrNull() ?: 0
                value = intValue
                if (intValue in range) {
                    onValueChange.invoke(intValue)
                    isError = false
                } else {
                    shakeTrigger = true
                    isError = true
                }
            },
        )

        // Spinner arrows
        Column(
            modifier = Modifier.width(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Up arrow button
            IconButton(
                onClick = {
                    val newValue = value + 1
                    if (newValue in range) {
                        value = newValue
                        onValueChange.invoke(newValue)
                        isError = false
                    } else {
                        shakeTrigger = true
                        isError = true
                    }
                },
                modifier = Modifier.size(20.dp, 16.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.arrow_up),
                    contentDescription = stringResource(Res.string.increment),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.size(12.dp)
                )
            }

            // Down arrow button
            IconButton(
                onClick = {
                    val newValue = value - 1
                    if (newValue in range) {
                        value = newValue
                        onValueChange.invoke(newValue)
                        isError = false
                    } else {
                        shakeTrigger = true
                        isError = true
                    }
                },
                modifier = Modifier.size(20.dp, 16.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.arrow_down),
                    contentDescription = stringResource(Res.string.decrement),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}