package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.extensions.errorShake

@Composable
fun NumberSettingsTextField(
    modifier: Modifier = Modifier,
    initialText: Int = 8,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initialText) }
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
    BasicTextField(
        interactionSource = interactionSource,
        maxLines = 1,
        textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .width(80.dp)
            .height(32.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(2.dp))
            .errorShake(
                trigger = shakeTrigger,
                onAnimationFinish = {
                    // Reset the trigger after animation so it can be re-triggered
                    shakeTrigger = false
                }
            )
        ,
        singleLine = true,
        value = value.toString(),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
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
}