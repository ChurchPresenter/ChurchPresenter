package org.churchpresenter.theme.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.HOVER_TINT_ALPHA
import org.churchpresenter.theme.elevationPalette

/**
 * `OutlinedTextField` in the elevated look: the well's color inside and its rim at rest, so it
 * reads as the same sunken field as every other input; the accent rim while focused. Keeps
 * Material's floating label. A caller passing [colors] keeps them.
 */
@Composable
fun SunkenOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = RoundedCornerShape(8.dp),
    colors: TextFieldColors? = null,
) {
    val palette = elevationPalette()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    // The container is drawn over anything behind the field, so the hover tint goes into its color.
    val well = if (hovered) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_TINT_ALPHA).compositeOver(palette.wellBottom)
    } else {
        palette.wellBottom
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.hoverable(hover),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        colors = colors ?: OutlinedTextFieldDefaults.colors(
            focusedContainerColor = well,
            unfocusedContainerColor = well,
            disabledContainerColor = palette.wellBottom,
            unfocusedBorderColor = palette.wellBorder,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
