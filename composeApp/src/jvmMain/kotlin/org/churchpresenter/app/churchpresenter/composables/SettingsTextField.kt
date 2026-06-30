package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FieldShape = RoundedCornerShape(6.dp)

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val borderColor = if (isError) MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (singleLine) Modifier.height(28.dp) else Modifier.padding(vertical = 5.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, FieldShape)
                .border(1.dp, borderColor, FieldShape),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                color = textColor
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (placeholder != null && value.isEmpty()) {
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            ) {
                                placeholder()
                            }
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        Box(modifier = Modifier.padding(start = 4.dp)) {
                            trailingIcon()
                        }
                    }
                }
            }
        )
        if (supportingText != null) {
            CompositionLocalProvider(
                LocalContentColor provides if (isError) MaterialTheme.colorScheme.error
                                          else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(modifier = Modifier.padding(start = 4.dp, top = 3.dp)) {
                    supportingText()
                }
            }
        }
    }
}
